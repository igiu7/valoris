package com.valoris.net;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.valoris.Config;
import com.valoris.city.Roles;
import com.valoris.realm.Realms;
import com.valoris.realm.Realms.Realm;
import com.valoris.data.Relation;
import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.data.ValorisData.Kingdom;
import com.valoris.diplomacy.Requests;
import com.valoris.diplomacy.Requests.Type;
import com.valoris.city.CityService;
import com.valoris.data.ValorisData.Siege;
import com.valoris.data.ValorisData.SiegePhase;
import com.valoris.kingdom.KingdomService;
import com.valoris.skill.Skill;
import com.valoris.tech.TechConfig;
import com.valoris.tech.TechNode;
import com.valoris.tech.TechService;
import com.valoris.skill.SkillCurve;
import com.valoris.skill.SkillData;
import com.valoris.skill.SkillService;
import com.valoris.util.Msg;

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Builds the GUI snapshot for one player (server side). */
public final class StateBuilder {
    private StateBuilder() {}

    private static int relationCode(Optional<Relation> rel) {
        if (rel.isEmpty()) return 0;
        return switch (rel.get()) {
            case ALLIANCE -> 1;
            case WAR -> 3;
        };
    }

    public static GuiState build(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        ValorisData data = ValorisData.get(server);
        var teams = FTBTeamsAPI.api().getManager();
        GuiState s = new GuiState();

        Roles.Role role = Roles.of(p);
        s.role = role.name();
        s.roleTitle = role.title;
        s.claimLimit = Roles.cityOfMember(p).map(com.valoris.tech.TechService::claimLimit)
                .orElse(Config.CITY_CLAIM_LIMIT.get());
        s.siegePrepMinutes = Config.SIEGE_PREP_MINUTES.get();
        s.siegeMinutes = Config.SIEGE_DURATION_MINUTES.get();
        long now = server.overworld().getGameTime();

        SkillData skills = SkillService.data(p);
        int n = Skill.values().length;
        s.skillLevel = new int[n];
        s.skillXp = new int[n];
        s.skillNeed = new int[n];
        for (Skill skill : Skill.values()) {
            int i = skill.ordinal();
            int level = skills.level(skill);
            s.skillLevel[i] = level;
            s.skillXp[i] = (int) SkillCurve.progressInLevel(skills.xp(skill));
            s.skillNeed[i] = level >= SkillCurve.MAX_LEVEL ? 0 : (int) SkillCurve.needFor(level);
        }

        Optional<City> city = Roles.cityOfMember(p);
        Optional<Kingdom> kingdom = Roles.kingdomOf(p);

        city.filter(c -> c.founded).ifPresent(member -> {
            TechService.update(server, member);
            City c = TechService.holder(data, member); // the realm's research
            int[] levels = TechService.cityLevels(server, c);
            s.citySkills = levels;
            s.canResearch = TechService.canResearch(p);
            s.researching = c.researching;
            for (TechNode node : TechConfig.nodes().values()) {
                TechService.Status status = TechService.status(c, node, levels);
                int[] req = new int[Skill.values().length];
                node.skills().forEach((skill, lvl) -> req[skill.ordinal()] = lvl);
                s.techs.add(new com.valoris.net.TechRow(node.id(), node.era(),
                        TechConfig.eraNames().getOrDefault(node.era(), node.era()), node.name(), node.icon(),
                        node.description(), node.x(), node.y(), node.requires(), req, node.features(),
                        status.ordinal(), node.researchMinutes() * 60,
                        status == TechService.Status.RESEARCHING ? TechService.remainingSeconds(c) : 0));
            }
        });

        city.ifPresent(c -> {
            s.cityName = c.name;
            s.founded = c.founded;
            s.cityRuler = Msg.nameOf(server, c.ruler);
            teams.getTeamByID(c.teamId).ifPresent(team -> {
                s.residents = team.getMembers().size();
                for (ServerPlayer m : team.getOnlineMembers()) {
                    if (!m.getUUID().equals(p.getUUID())) s.onlineResidents.add(m.getGameProfile().getName());
                }
                s.claimed = FTBChunksAPI.api().getManager().getOrCreateData(team).getClaimedChunks().size();
            });
        });

        kingdom.ifPresent(k -> {
            s.kingdomName = k.name;
            s.king = KingdomService.kingOf(data, k).map(id -> Msg.nameOf(server, id)).orElse("?");
            city.ifPresent(c -> s.capital = k.capital.equals(c.teamId));
            for (UUID id : k.cities) {
                data.byTeam(id).ifPresent(c -> {
                    boolean online = server.getPlayerList().getPlayer(c.ruler) != null;
                    s.myCities.add(new Row(c.name, Msg.nameOf(server, c.ruler), "",
                            id.equals(k.capital) ? 1 : 0, online ? 1 : 0));
                });
            }
        });

        // all founded cities
        for (City c : data.all()) {
            if (!c.founded) continue;
            int residents = teams.getTeamByID(c.teamId).map(t -> t.getMembers().size()).orElse(0);
            String kn = data.kingdomOfCity(c.teamId).map(k -> k.name).orElse("");
            int flags = 0;
            if (c.hasBanner() && CityService.bannerPresent(server, c)) flags |= 1;
            if (data.siegeOfCity(c.teamId).isPresent()) flags |= 2;
            if (data.cooldownUntil(c.teamId) > now) flags |= 4;
            // claimed chunks in the upper bits (read with flags >> 8)
            int chunks = teams.getTeamByID(c.teamId)
                    .map(t -> FTBChunksAPI.api().getManager().getOrCreateData(t).getClaimedChunks().size()).orElse(0);
            flags |= Math.min(chunks, 0xFFFF) << 8;
            s.cities.add(new Row(c.name, Msg.nameOf(server, c.ruler), kn, residents, flags));
        }

        // notice board
        for (City c : com.valoris.city.NoticeBoard.notices(data)) {
            Optional<Kingdom> nk = data.kingdomOfCity(c.teamId);
            int color = nk.isPresent() ? com.valoris.realm.Heraldry.kingdomColor(nk.get())
                    : com.valoris.realm.Heraldry.teamColor(c.teamId);
            s.notices.add(new com.valoris.net.NoticeRow(c.name, nk.map(k -> k.name).orElse(""), Msg.nameOf(server, c.ruler),
                    c.noticeTitle, c.noticeBody, (int) com.valoris.city.NoticeBoard.secondsLeft(c),
                    c.ruler.equals(p.getUUID()), color));
        }

        // all kingdoms with the relation to mine
        for (Kingdom k : data.kingdoms()) {
            int rel;
            if (kingdom.isPresent() && kingdom.get().id.equals(k.id)) rel = -1;
            else if (kingdom.isEmpty()) rel = 0;
            else rel = relationCode(data.relation(kingdom.get().id, k.id));
            s.kingdoms.add(new Row(k.name, KingdomService.kingOf(data, k).map(id -> Msg.nameOf(server, id)).orElse("?"),
                    "", k.cities.size(), rel));
        }

        // invitable players
        for (ServerPlayer o : server.getPlayerList().getPlayers()) {
            if (o.getUUID().equals(p.getUUID())) continue;
            String name = o.getGameProfile().getName();
            boolean inParty = teams.getTeamForPlayer(o).map(Team::isPartyTeam).orElse(false);
            if (!inParty) s.candidates.add(name);
            data.byRuler(o.getUUID())
                    .filter(c -> c.founded && data.kingdomOfCity(c.teamId).isEmpty())
                    .ifPresent(c -> s.kingdomCandidates.add(new Row(name, c.name, "", 0, 0)));
        }

        // incoming requests
        for (Requests.Request r : Requests.listTo(server, Type.CITY_INVITE, p.getUUID())) {
            data.byTeam(r.from()).ifPresent(c -> s.cityInvites.add(c.name));
        }
        city.filter(c -> c.founded && c.ruler.equals(p.getUUID())).ifPresent(c -> {
            for (Requests.Request r : Requests.listTo(server, Type.CITY_JOIN, c.teamId)) {
                s.cityJoinRequests.add(Msg.nameOf(server, r.from()));
            }
            for (Requests.Request r : Requests.listTo(server, Type.KINGDOM_INVITE, c.teamId)) {
                data.kingdomById(r.from()).ifPresent(k -> s.kingdomInvites.add(k.name));
            }
        });
        if (role == Roles.Role.KING) {
            kingdom.ifPresent(k -> {
                for (Requests.Request r : Requests.listTo(server, Type.KINGDOM_JOIN, k.id)) {
                    data.byTeam(r.from()).ifPresent(c -> s.kingdomJoinRequests.add(c.name));
                }
            });
        }

        // my realm (kingdom or independent city): diplomacy, sieges and a pending conquest decision
        Optional<Realm> realm = Realms.of(p);
        boolean leader = Realms.isLeader(p);
        s.isLeader = leader;
        realm.ifPresent(r -> {
            s.realmName = r.name();
            s.realmIsKingdom = r.isKingdom();
            s.realmColor = r.isKingdom() ? com.valoris.realm.Heraldry.kingdomColor(r.kingdom())
                    : com.valoris.realm.Heraldry.teamColor(r.city().teamId);
            if (leader) {
                for (Requests.Request q : Requests.listTo(server, Type.DIPLOMACY, r.id())) {
                    Realms.byId(data, q.from()).ifPresent(f -> s.proposals.add(new Row(f.name(), q.proposal().name(), "", 0, 0)));
                }
                for (Requests.Request q : Requests.listFrom(server, Type.DIPLOMACY, r.id())) {
                    Realms.byId(data, q.to()).ifPresent(t -> s.sentProposals.add(new Row(t.name(), q.proposal().name(), "", 0, 0)));
                }
                data.firstConquestOf(r.id()).ifPresent(c -> s.conquestCity = c.cityName);
                for (Requests.Request q : Requests.listTo(server, Type.WAR_CALL, r.id())) {
                    data.warById(q.ref()).ifPresent(w -> {
                        int callerSide = w.sideOf(q.from());
                        if (callerSide == 0) return;
                        UUID enemy = w.opposite(callerSide).get(0);
                        String caller = Realms.byId(data, q.from()).map(Realm::name).orElse(w.names.getOrDefault(q.from(), "?"));
                        s.warCalls.add(new Row(caller, Realms.byId(data, enemy).map(Realm::name).orElse(w.names.getOrDefault(enemy, "?")),
                                w.id.toString(), 0, 0));
                    });
                }
                for (Requests.Request q : Requests.listFrom(server, Type.WAR_CALL, r.id())) {
                    data.warById(q.ref()).ifPresent(w -> {
                        int mySide = w.sideOf(r.id());
                        if (mySide == 0) return;
                        String ally = Realms.byId(data, q.to()).map(Realm::name).orElse("?");
                        UUID enemy = w.opposite(mySide).get(0);
                        s.sentCalls.add(new Row(ally, Realms.byId(data, enemy).map(Realm::name).orElse(w.names.getOrDefault(enemy, "?")),
                                w.id.toString(), 0, 0));
                    });
                }
            }
            for (ValorisData.War w : data.warsOf(r.id())) {
                int mySide = w.sideOf(r.id());
                UUID ourLeader = w.side(mySide).get(0), theirLeader = w.opposite(mySide).get(0);
                String nOurs = Realms.byId(data, ourLeader).map(Realm::name).orElse(w.names.getOrDefault(ourLeader, "?"));
                String nTheirs = Realms.byId(data, theirLeader).map(Realm::name).orElse(w.names.getOrDefault(theirLeader, "?"));
                int flags = (mySide == 1 ? 1 : 0) | (ourLeader.equals(r.id()) ? 2 : 0) | (w.rebellion ? 4 : 0);
                s.wars.add(new Row(w.id.toString(), nOurs, nTheirs, flags, 0));
                int theirSide = mySide == 1 ? 2 : 1;
                s.warInfos.add(new WarInfo(w.id.toString(), w.started, w.sideKills[mySide], w.sideKills[theirSide],
                        w.citiesTaken[mySide], w.citiesTaken[theirSide], w.siegesRepelled[mySide], w.siegesRepelled[theirSide],
                        List.copyOf(w.logTimes), List.copyOf(w.logTexts)));
                for (UUID m : w.attackers.size() + w.defenders.size() == 0 ? List.<UUID>of()
                        : java.util.stream.Stream.concat(w.attackers.stream(), w.defenders.stream()).toList()) {
                    String memberName = Realms.byId(data, m).map(Realm::name).orElse(w.names.getOrDefault(m, "?"));
                    int[] st = w.realmStats.getOrDefault(m, new int[2]);
                    s.warStats.add(new Row(w.id.toString(), memberName, w.joinReasons.getOrDefault(m, ""), st[0], st[1]));
                }
                for (int side = 1; side <= 2; side++) {
                    for (UUID m : w.side(side)) {
                        String memberName = Realms.byId(data, m).map(Realm::name).orElse(w.names.getOrDefault(m, "?"));
                        s.warMembers.add(new Row(w.id.toString(), memberName, side == mySide ? "OURS" : "THEIRS",
                                w.side(side).get(0).equals(m) ? 1 : 0, 0));
                    }
                }
            }
            for (Siege x : data.sieges()) {
                boolean attacking = com.valoris.war.SiegeService.attackersOf(data, x).contains(r.id());
                if (!attacking && !com.valoris.war.SiegeService.defendersOf(data, x).contains(r.id())) continue;
                long remaining = (x.phase == SiegePhase.PREPARATION ? x.startTick : x.endTick) - now;
                // a realm that joined someone else's siege sees itself as the attacker
                String attacker = attacking && !x.attackerKingdom.equals(r.id()) ? r.name()
                        : Realms.byId(data, x.attackerKingdom).map(Realm::name).orElse(x.attackerName);
                String defender = Realms.byId(data, x.defenderKingdom).map(Realm::name).orElse(x.defenderName);
                s.sieges.add(new Row(attacker, x.cityName, defender,
                        (int) Math.max(0, remaining / 20), x.phase == SiegePhase.PREPARATION ? 0 : 1));
            }
        });

        // every realm with its relation to mine. Row(name, leader, "KINGDOM"/"CITY", cities, relation)
        for (Realm r : Realms.all(data)) {
            int rel;
            if (realm.isPresent() && realm.get().id().equals(r.id())) rel = -1;
            else if (realm.isEmpty()) rel = 0;
            else rel = relationCode(data.relation(realm.get().id(), r.id()));
            String leaderName = Realms.leaderOf(data, r).map(id -> Msg.nameOf(server, id)).orElse("?");
            s.realms.add(new Row(r.name(), leaderName, r.isKingdom() ? "KINGDOM" : "CITY", Realms.citiesOf(r).size(), rel));
        }

        // truces of my realm
        realm.ifPresent(mine -> data.trucesOf(mine.id()).forEach((id, until) -> Realms.byId(data, id).ifPresent(other ->
                s.truces.add(new Row(other.name(), "", "", (int) Math.max(1, (until - System.currentTimeMillis()) / 1000), 0)))));

        s.leaderboard = leaderboard(server, data, realm.map(Realm::id).orElse(null));
        return s;
    }

    /**
     * Every kingdom and independent city with its statistics: kills and deaths of all its residents, its land,
     * residents, cities, technologies and sieges won / repelled (LeaderboardData).
     */
    private static List<LeaderRow> leaderboard(MinecraftServer server, ValorisData data, UUID mine) {
        com.valoris.data.LeaderboardData board = com.valoris.data.LeaderboardData.get(server);
        List<LeaderRow> out = new java.util.ArrayList<>();
        for (Realm r : Realms.all(data)) {
            int[] st = new int[LeaderRow.COUNT];
            java.util.Set<UUID> residents = new java.util.HashSet<>();
            for (UUID cityId : Realms.citiesOf(r)) {
                Team team = FTBTeamsAPI.api().getManager().getTeamByID(cityId).orElse(null);
                if (team != null) {
                    residents.addAll(team.getMembers());
                    st[LeaderRow.LAND] += FTBChunksAPI.api().getManager().getOrCreateData(team).getClaimedChunks().size();
                }
                data.byTeam(cityId).ifPresent(c -> st[LeaderRow.TECHS] = Math.max(st[LeaderRow.TECHS], c.techs.size()));
                st[LeaderRow.SIEGES_WON] += board.siegesWon(cityId);
                st[LeaderRow.SIEGES_REPELLED] += board.siegesRepelled(cityId);
            }
            for (UUID id : residents) {
                st[LeaderRow.KILLS] += board.kills(id);
                st[LeaderRow.DEATHS] += board.deaths(id);
            }
            st[LeaderRow.RESIDENTS] = residents.size();
            st[LeaderRow.CITIES] = Realms.citiesOf(r).size();
            String leaderName = Realms.leaderOf(data, r).map(id -> Msg.nameOf(server, id)).orElse("?");
            int color = r.isKingdom() ? com.valoris.realm.Heraldry.kingdomColor(r.kingdom())
                    : com.valoris.realm.Heraldry.teamColor(r.city().teamId);
            out.add(new LeaderRow(r.name(), leaderName, r.isKingdom(), color, r.id().equals(mine), st));
        }
        return out;
    }
}
