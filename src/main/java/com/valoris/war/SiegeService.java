package com.valoris.war;

import java.util.List;
import java.util.Optional;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.valoris.Config;
import com.valoris.city.CityService;
import com.valoris.city.Roles;
import com.valoris.data.Relation;
import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.data.ValorisData.Conquest;
import com.valoris.data.ValorisData.Kingdom;
import com.valoris.data.ValorisData.Siege;
import com.valoris.data.ValorisData.SiegePhase;
import com.valoris.kingdom.KingdomService;
import com.valoris.realm.Realms;
import com.valoris.realm.Realms.Realm;
import com.valoris.util.Errors;
import com.valoris.util.Msg;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Sieges. A king at war with another kingdom picks one of its cities. After the preparation time the siege
 * is active; attackers win by destroying the city banner, defenders win if it survives until the end.
 */
public final class SiegeService {
    private static final long TICKS_PER_MINUTE = 1200L;
    /** Countdown marks in seconds, ascending. */
    private static final int[] MARKS = {10, 30, 60, 120, 300, 600, 900, 1800, 2700};

    private SiegeService() {}

    public static boolean isUnderSiege(ValorisData data, java.util.UUID cityTeam) {
        return data.siegeOfCity(cityTeam).isPresent();
    }

    // ------------------------------------------------------------ starting

    public static void start(ServerPlayer player, String cityName) throws CommandSyntaxException {
        Realm own = Realms.requireActingLeader(player);
        MinecraftServer server = player.getServer();
        ValorisData data = ValorisData.get(server);
        long now = server.overworld().getGameTime();

        City city = data.cityByName(cityName).filter(c -> c.founded).orElseThrow(() -> Errors.of("No such city."));
        Realm defender = Realms.ofCity(data, city).orElseThrow(() -> Errors.of("No such city."));
        if (defender.id().equals(own.id())) throw Errors.of("You cannot besiege your own city.");
        com.valoris.coup.CoupService.checkNoCoup(own.id(), own.title());
        if (data.relation(own.id(), defender.id()).filter(r -> r == Relation.WAR).isEmpty()) {
            throw Errors.of("You are not at war with the " + defender.title() + ".");
        }
        Optional<Siege> running = data.siegeOfCity(city.teamId);
        if (running.isPresent()) {
            join(server, data, own, running.get());
            return;
        }
        if (data.siegeOfAttacker(own.id()).isPresent()) throw Errors.of("You are already taking part in a siege.");
        if (!city.hasBanner() || !CityService.bannerPresent(server, city)) {
            throw Errors.of("That city has no standing city banner, so it cannot be besieged.");
        }
        long until = data.cooldownUntil(city.teamId);
        if (until > now) {
            throw Errors.of("That city recently survived a siege and is protected for another "
                    + formatTime((until - now) / 20) + ".");
        }
        requireDefendersOnline(server, data, defender);

        Siege siege = new Siege(java.util.UUID.randomUUID(), own.id(), defender.id(), city.teamId, own.name(),
                defender.name(), city.name);
        long prep = Config.SIEGE_PREP_MINUTES.get() * TICKS_PER_MINUTE;
        siege.startTick = now + prep;
        siege.endTick = siege.startTick + Config.SIEGE_DURATION_MINUTES.get() * TICKS_PER_MINUTE;
        siege.lastMark = closestMark(prep / 20);
        data.addSiege(siege);
        com.valoris.coup.CoupService.siegeStarted(player.getServer(), data, own.id(), defender.id(), city.teamId);

        Msg.news(server, "🏰", "SIEGE! The " + own.title() + " will besiege the city of " + city.name
                + " (" + defender.title() + ") in " + formatTime(prep / 20) + ". The siege will last "
                + Config.SIEGE_DURATION_MINUTES.get() + " minutes. Other enemies of the " + defender.title()
                + " may join it.", ChatFormatting.DARK_RED);
        SiegeAnnouncer.declared(server, siege, own.title(), prep / 20);
        for (ValorisData.War w : warsOf(data, siege)) w.log("The " + own.title() + " began the siege of " + city.name + ".");
    }

    /**
     * No sieges on a sleeping realm: more than siegeMinOnlineFraction of the defending realm's residents (all its
     * cities) must be online.
     */
    private static void requireDefendersOnline(MinecraftServer server, ValorisData data, Realm defender)
            throws CommandSyntaxException {
        double needed = Config.SIEGE_MIN_ONLINE.get();
        if (needed <= 0) return;
        java.util.Set<java.util.UUID> residents = new java.util.HashSet<>();
        for (java.util.UUID cityTeam : Realms.citiesOf(defender)) {
            dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api().getManager().getTeamByID(cityTeam)
                    .ifPresent(t -> residents.addAll(t.getMembers()));
        }
        if (residents.isEmpty()) return;
        long online = residents.stream().filter(id -> server.getPlayerList().getPlayer(id) != null).count();
        if (online <= needed * residents.size()) {
            int percent = (int) Math.round(needed * 100);
            throw Errors.of("You can only besiege the " + defender.title() + " when more than " + percent
                    + "% of its residents are online (now " + online + " of " + residents.size() + ").");
        }
    }

    /**
     * Another realm at war with the defender joins a siege that is already under way. It fights alongside the
     * realm that started the siege, which keeps the lead (and the conquest if the city falls).
     */
    private static void join(MinecraftServer server, ValorisData data, Realm own, Siege s) throws CommandSyntaxException {
        if (attackersOf(data, s).contains(own.id())) {
            throw Errors.of("Your army already fights in the siege of " + s.cityName + " (you share the war with its leader).");
        }
        if (data.siegeOfAttacker(own.id()).isPresent()) throw Errors.of("You are already taking part in another siege.");
        s.coAttackers.add(own.id());
        s.coAttackerNames.put(own.id(), own.name());
        data.touch();
        String leader = Realms.title(data, s.attackerKingdom, s.attackerName);
        Msg.news(server, "🏰", "The " + own.title() + " joins the siege of " + s.cityName + " led by the "
                + leader + ".", ChatFormatting.DARK_RED);
        SiegeAnnouncer.joined(server, s, own, leader);
        for (ValorisData.War w : warsOf(data, s)) w.log("The " + own.title() + " joined the siege of " + s.cityName + ".");
        if (s.phase == SiegePhase.ACTIVE) {
            data.byTeam(s.cityTeam).ifPresent(city -> {
                net.minecraft.core.BlockPos banner = net.minecraft.core.BlockPos.of(city.bannerPos);
                Realms.message(own, Msg.text("Scouts report: the city banner of " + s.cityName + " stands at "
                        + banner.getX() + ", " + banner.getY() + ", " + banner.getZ() + ".", ChatFormatting.GOLD));
            });
        }
    }

    /** "the Kingdom of A" or "the Kingdom of A and 2 allies". */
    public static String attackersTitle(ValorisData data, Siege s) {
        String lead = "the " + Realms.title(data, s.attackerKingdom, s.attackerName);
        int n = attackersOf(data, s).size() - 1;
        return n == 0 ? lead : lead + " and " + n + (n == 1 ? " ally" : " allies");
    }

    /**
     * Drops attackers that no longer exist or made peace with the defender. If the leader drops out, the next
     * realm that joined takes the lead. Returns false when no attacker is left.
     */
    private static boolean pruneAttackers(MinecraftServer server, ValorisData data, Siege s) {
        for (java.util.UUID id : List.copyOf(s.coAttackers)) {
            boolean ok = Realms.byId(data, id).isPresent()
                    && data.relation(id, s.defenderKingdom).filter(r -> r == Relation.WAR).isPresent();
            if (!ok) {
                s.coAttackers.remove(id);
                String name = s.coAttackerNames.remove(id);
                data.touch();
                notifyBoth(data, s, Msg.text("The " + Realms.title(data, id, name) + " left the siege of " + s.cityName
                        + ".", ChatFormatting.YELLOW));
            }
        }
        boolean leaderOk = Realms.byId(data, s.attackerKingdom).isPresent()
                && data.relation(s.attackerKingdom, s.defenderKingdom).filter(r -> r == Relation.WAR).isPresent();
        if (leaderOk) return true;
        if (s.coAttackers.isEmpty()) return false;
        java.util.UUID next = s.coAttackers.remove(0);
        s.attackerKingdom = next;
        s.attackerName = s.coAttackerNames.remove(next);
        data.touch();
        Msg.broadcast(server, Msg.text("The " + Realms.title(data, next, s.attackerName) + " now leads the siege of "
                + s.cityName + ".", ChatFormatting.GOLD));
        return true;
    }

    // ------------------------------------------------------------ ticking (called every second)

    private static boolean bannersChecked;

    public static void tick(MinecraftServer server) {
        ValorisData data = ValorisData.get(server);
        long now = server.overworld().getGameTime();
        if (!bannersChecked) {
            // a looting does not outlive a restart: banners destroyed in a siege whose city is free again come back
            bannersChecked = true;
            for (City c : List.copyOf(data.all())) {
                if (c.founded && data.conquestOfCity(c.teamId).isEmpty() && data.siegeOfCity(c.teamId).isEmpty()) {
                    CityService.restoreBanner(server, c);
                }
            }
        }

        for (Siege s : List.copyOf(data.sieges())) {
            Optional<City> city = data.byTeam(s.cityTeam);
            if (city.isEmpty()) {
                cancel(server, data, s, "The siege of " + s.cityName + " was called off.");
                continue;
            }
            Optional<Realm> defenderNow = Realms.ofCity(data, city.get());
            if (defenderNow.isEmpty() || !defenderNow.get().id().equals(s.defenderKingdom)) {
                cancel(server, data, s, "The siege of " + s.cityName + " was called off: the city changed sides.");
                continue;
            }
            if (!pruneAttackers(server, data, s)) {
                cancel(server, data, s, "The siege of " + s.cityName + " was called off: the war is over.");
                continue;
            }

            SiegeAnnouncer.updateBar(server, s, now);
            if (s.phase == SiegePhase.PREPARATION) {
                if (now >= s.startTick) activate(server, data, s);
                else countdown(data, s, (s.startTick - now) / 20, "The siege of " + s.cityName + " by "
                        + attackersTitle(data, s) + " begins in ");
            } else {
                if (!CityService.bannerPresent(server, city.get())) {
                    captured(server, data, s, city.get());
                    continue;
                }
                if (now >= s.endTick) {
                    defended(server, data, s, now);
                    continue;
                }
                if (!s.prevProps.containsKey("ftbchunks:allow_explosions")) {
                    // a siege that was already running under an older version: open the rest of the protection
                    s.protectionApplied = false;
                    applyProtections(data, s);
                }
                fatigueInside(server, s);
                countdown(data, s, (s.endTick - now) / 20, "The siege of " + s.cityName
                        + " ends in ");
            }
        }
        SiegeAnnouncer.cleanBars(data);
        endSlowdowns(server, now);
        Looting.tick(server);
        ConquestService.tick(server, data, now);
    }

    private static final net.minecraft.resources.ResourceLocation SIEGE_MINING =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.valoris.valoris2.MODID, "siege_mining");
    /** players slowed down by a siege -> game time they were last seen on its land */
    private static final java.util.Map<java.util.UUID, Long> SLOWED = new java.util.HashMap<>();

    /**
     * Both sides standing on the besieged city's land mine slower while they are there: Mining Fatigue II and
     * Config.SIEGE_MINING_SPEED of that on top (a block break speed modifier, which the client knows too, so its
     * mining keeps in step with the server's).
     */
    private static void fatigueInside(MinecraftServer server, Siege s) {
        long now = server.overworld().getGameTime();
        double extra = Config.SIEGE_MINING_SPEED.get() - 1.0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!isParticipant(p, s)) continue;
            ClaimedChunk chunk = FTBChunksAPI.api().getManager().getChunk(new ChunkDimPos(p));
            if (chunk == null || !chunk.getTeamData().getTeam().getId().equals(s.cityTeam)) continue;
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 60, 1, false, true, true));
            var speed = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_BREAK_SPEED);
            if (speed != null) {
                speed.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SIEGE_MINING,
                        extra, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            SLOWED.put(p.getUUID(), now);
        }
    }

    /** The extra slowdown ends with the Mining Fatigue: once a player has not been on besieged land for 3 seconds. */
    private static void endSlowdowns(MinecraftServer server, long now) {
        SLOWED.entrySet().removeIf(e -> {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null && now - e.getValue() < 60) return false;
            if (p != null) {
                var speed = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_BREAK_SPEED);
                if (speed != null) speed.removeModifier(SIEGE_MINING);
            }
            return true;
        });
    }

    private static void countdown(ValorisData data, Siege s, long remainingSec, String prefix) {
        int mark = closestMark(remainingSec);
        if (mark < s.lastMark) {
            s.lastMark = mark;
            data.touch();
            String suffix = s.phase == SiegePhase.ACTIVE ? ". Defenders win if the banner survives." : ".";
            notifyBoth(data, s, Msg.text(prefix + formatTime(remainingSec) + suffix, ChatFormatting.GOLD));
        }
    }

    private static void activate(MinecraftServer server, ValorisData data, Siege s) {
        Optional<City> city = data.byTeam(s.cityTeam);
        if (city.isEmpty()) {
            cancel(server, data, s, "The siege of " + s.cityName + " was called off.");
            return;
        }
        if (!CityService.bannerPresent(server, city.get())) {
            // the banner cannot be moved or replaced during the preparation, so a banner gone by now was removed
            // on purpose: the city falls instead of escaping the siege
            captured(server, data, s, city.get());
            return;
        }
        s.phase = SiegePhase.ACTIVE;
        applyProtections(data, s);
        long duration = (s.endTick - s.startTick) / 20;
        s.lastMark = closestMark(duration);
        data.touch();
        Msg.news(server, "🔥", "The siege of " + s.cityName + " has begun! " + capital(attackersTitle(data, s))
                + " must destroy the city banner within " + formatTime(duration) + ".", ChatFormatting.DARK_RED);
        SiegeAnnouncer.began(server, s, duration);
        // the attackers learn where the banner stands
        net.minecraft.core.BlockPos banner = net.minecraft.core.BlockPos.of(city.get().bannerPos);
        for (ServerPlayer p : SiegeAnnouncer.participants(server, s)) {
            if (isAttacker(p, s)) p.sendSystemMessage(Msg.text("Scouts report: the city banner of " + s.cityName
                    + " stands at " + banner.getX() + ", " + banner.getY() + ", " + banner.getZ() + ".", ChatFormatting.GOLD));
        }
    }

    // ------------------------------------------------------------ outcomes

    /** The city a realm's leaderboard credits go to: the capital of a kingdom, or the independent city itself. */
    private static Optional<java.util.UUID> leaderCity(ValorisData data, java.util.UUID realm) {
        return Realms.byId(data, realm).map(r -> r.isKingdom() ? r.kingdom().capital : r.city().teamId);
    }

    /** The wars a siege belongs to: those in which one of its attackers fights the defender. */
    public static java.util.Set<ValorisData.War> warsOf(ValorisData data, Siege s) {
        java.util.Set<ValorisData.War> out = new java.util.LinkedHashSet<>();
        java.util.Set<java.util.UUID> explicit = new java.util.LinkedHashSet<>(s.coAttackers);
        explicit.add(s.attackerKingdom);
        for (java.util.UUID a : explicit) out.addAll(data.warsBetween(a, s.defenderKingdom));
        return out;
    }

    /** siege -> players downed by {attackers, defenders} (index 1, 2) during it; kept while the server runs */
    private static final java.util.Map<java.util.UUID, int[]> KILLS = new java.util.concurrent.ConcurrentHashMap<>();

    /** A player of one side downed a player of the other side during the active phase (SiegeEvents.onDeath). */
    public static void countKill(ServerPlayer killer, ServerPlayer victim) {
        ValorisData data = ValorisData.get(killer.getServer());
        for (Siege s : data.sieges()) {
            if (s.phase != SiegePhase.ACTIVE || !isParticipant(killer, s) || !isParticipant(victim, s)) continue;
            boolean atk = isAttacker(killer, s);
            if (atk == isAttacker(victim, s)) continue;
            KILLS.computeIfAbsent(s.id, k -> new int[3])[atk ? 1 : 2]++;
        }
    }

    /** The result of a siege for the Discord news channel: how it ended, how long it took and the numbers. */
    private static void summary(MinecraftServer server, ValorisData data, Siege s, String result) {
        java.util.function.Function<String, String> p = com.valoris.discord.DiscordLink::plain;
        int[] kills = KILLS.getOrDefault(s.id, new int[3]);
        KILLS.remove(s.id);
        long now = server.overworld().getGameTime();
        long seconds = Math.max(0, (now - s.startTick) / 20);
        java.util.List<String> attackers = new java.util.ArrayList<>();
        for (java.util.UUID id : attackersOf(data, s)) {
            attackers.add(Realms.title(data, id, id.equals(s.attackerKingdom) ? s.attackerName
                    : s.coAttackerNames.getOrDefault(id, "?")));
        }
        String text = "📊 **The siege of " + p.apply(s.cityName) + " is over**\n"
                + "Result: **" + p.apply(result) + "**\n"
                + "Lasted: " + formatTime(seconds) + "\n"
                + "City banner: " + s.bannerHits + " / " + Config.SIEGE_BANNER_HITS.get() + " hits\n\n"
                + "**Attackers**: " + p.apply(String.join(", ", attackers)) + " · " + kills[1] + " enemies downed\n"
                + "**Defenders**: " + p.apply(Realms.title(data, s.defenderKingdom, s.defenderName)) + " · "
                + kills[2] + " enemies downed";
        com.valoris.discord.DiscordLink.announceRaw(text);
    }

    private static void captured(MinecraftServer server, ValorisData data, Siege s, City city) {
        for (ValorisData.War w : warsOf(data, s)) {
            w.citiesTaken[w.sideOf(s.defenderKingdom) == 1 ? 2 : 1]++;
            w.log("The " + Realms.title(data, s.attackerKingdom, s.attackerName) + " conquered " + s.cityName + ".");
        }
        List<ServerPlayer> involved = SiegeAnnouncer.participants(server, s);
        java.util.Set<java.util.UUID> winners = attackersOf(data, s);
        com.valoris.data.LeaderboardData board = com.valoris.data.LeaderboardData.get(server);
        for (java.util.UUID realm : winners) leaderCity(data, realm).ifPresent(board::addSiegeWon);
        SiegeAnnouncer.removeBar(s.id);
        SiegeAnnouncer.captured(server, s, involved);
        Msg.news(server, "🏆", "VICTORY for " + attackersTitle(data, s) + "! The city banner of " + s.cityName
                + " has been destroyed and the city falls to the " + Realms.title(data, s.attackerKingdom, s.attackerName)
                + ".", ChatFormatting.DARK_RED);
        summary(server, data, s, "the attackers took the city");
        restoreProtections(data, s);
        data.removeSiege(s.id);

        Conquest conquest = new Conquest(city.teamId, s.attackerKingdom, city.name, s.attackerName);
        conquest.decideBy = server.overworld().getGameTime() + Config.CONQUEST_DECISION_MINUTES.get() * TICKS_PER_MINUTE;
        data.addConquest(conquest);
        // the city stays in its kingdom until the conqueror takes it (ConquestService.annex): spared or looted, it
        // goes on belonging to it, and a kingdom whose only city fell does not end
        // everyone who won the siege takes part if the conqueror chooses to loot the city
        Looting.rememberWinners(city.teamId, winners);
        ConquestService.openMenu(server, data, conquest, "");
    }

    private static void defended(MinecraftServer server, ValorisData data, Siege s, long now) {
        for (ValorisData.War w : warsOf(data, s)) {
            w.siegesRepelled[w.sideOf(s.defenderKingdom)]++;
            w.log("The defenders held " + s.cityName + " against " + attackersTitle(data, s) + ".");
        }
        leaderCity(data, s.defenderKingdom).ifPresent(com.valoris.data.LeaderboardData.get(server)::addSiegeRepelled);
        SiegeAnnouncer.removeBar(s.id);
        SiegeAnnouncer.defended(server, s);
        Msg.news(server, "🛡️", "The siege of " + s.cityName + " has failed! The defenders of the "
                + Realms.title(data, s.defenderKingdom, s.defenderName) + " hold the city against "
                + attackersTitle(data, s) + ".", ChatFormatting.GREEN);
        summary(server, data, s, "the defenders held the city");
        restoreProtections(data, s);
        data.removeSiege(s.id);
        data.setCooldown(s.cityTeam, now + Config.SIEGE_COOLDOWN_MINUTES.get() * TICKS_PER_MINUTE);
    }

    private static void cancel(MinecraftServer server, ValorisData data, Siege s, String reason) {
        for (ValorisData.War w : warsOf(data, s)) w.log(reason);
        SiegeAnnouncer.removeBar(s.id);
        SiegeAnnouncer.cancelled(server, s, reason);
        Msg.news(server, "🏳️", reason, ChatFormatting.YELLOW);
        KILLS.remove(s.id);
        restoreProtections(data, s);
        data.removeSiege(s.id);
    }

    // ------------------------------------------------------------ protections of the besieged city

    /**
     * During the active phase FTB Chunks stops protecting the besieged city: building, interacting, entities
     * (siege machines), explosions, mob griefing, fake players and PvP are all opened. SiegeEvents then applies the
     * siege's own rules (only the two sides act there, nobody builds, the banner falls only to the attackers).
     * The previous values are stored with the siege and restored when it ends, even after a restart.
     */
    private static void applyProtections(ValorisData data, Siege s) {
        if (s.protectionApplied) return;
        FTBTeamsAPI.api().getManager().getTeamByID(s.cityTeam).ifPresent(team -> {
            open(team, s, FTBChunksProperties.BLOCK_EDIT_MODE, PrivacyMode.PUBLIC);
            open(team, s, FTBChunksProperties.BLOCK_INTERACT_MODE, PrivacyMode.PUBLIC);
            open(team, s, FTBChunksProperties.ENTITY_INTERACT_MODE, PrivacyMode.PUBLIC);
            open(team, s, FTBChunksProperties.NONLIVING_ENTITY_ATTACK_MODE, PrivacyMode.PUBLIC);
            open(team, s, FTBChunksProperties.ALLOW_EXPLOSIONS, true);
            open(team, s, FTBChunksProperties.ALLOW_MOB_GRIEFING, true);
            open(team, s, FTBChunksProperties.ALLOW_ALL_FAKE_PLAYERS, true);
            open(team, s, FTBChunksProperties.ALLOW_PVP, true);
            team.markDirty();
            s.protectionApplied = true;
            data.touch();
        });
    }

    private static <T> void open(Team team, Siege s, TeamProperty<T> property, T value) {
        try {
            s.prevProps.putIfAbsent(property.getId().toString(), property.toString(team.getProperty(property)));
            team.setProperty(property, value);
        } catch (RuntimeException e) {
            com.valoris.valoris2.LOGGER.warn("Siege: could not open {} of team {}", property.getId(), team.getId(), e);
        }
    }

    private static <T> void restore(Team team, Siege s, TeamProperty<T> property) {
        String raw = s.prevProps.get(property.getId().toString());
        if (raw == null) return;
        try {
            property.fromString(raw).ifPresent(v -> team.setProperty(property, v));
        } catch (RuntimeException e) {
            com.valoris.valoris2.LOGGER.warn("Siege: could not restore {} of team {}", property.getId(), team.getId(), e);
        }
    }

    private static void restoreProtections(ValorisData data, Siege s) {
        if (!s.protectionApplied) return;
        Team team = FTBTeamsAPI.api().getManager().getTeamByID(s.cityTeam).orElse(null);
        if (team != null) {
            restore(team, s, FTBChunksProperties.BLOCK_EDIT_MODE);
            restore(team, s, FTBChunksProperties.BLOCK_INTERACT_MODE);
            restore(team, s, FTBChunksProperties.ENTITY_INTERACT_MODE);
            restore(team, s, FTBChunksProperties.NONLIVING_ENTITY_ATTACK_MODE);
            restore(team, s, FTBChunksProperties.ALLOW_EXPLOSIONS);
            restore(team, s, FTBChunksProperties.ALLOW_MOB_GRIEFING);
            restore(team, s, FTBChunksProperties.ALLOW_ALL_FAKE_PLAYERS);
            restore(team, s, FTBChunksProperties.ALLOW_PVP);
            team.markDirty();
        }
        s.prevProps.clear();
        s.protectionApplied = false;
        data.touch();
    }

    // ------------------------------------------------------------ queries used by events and the GUI

    /**
     * Every realm attacking in a siege: the one that started it, those that joined it, and all their allies on
     * the same side of their war with the defender.
     */
    public static java.util.Set<java.util.UUID> attackersOf(ValorisData data, Siege s) {
        java.util.Set<java.util.UUID> out = new java.util.LinkedHashSet<>();
        out.add(s.attackerKingdom);
        out.addAll(s.coAttackers);
        for (java.util.UUID a : List.copyOf(out)) {
            for (ValorisData.War w : data.warsBetween(a, s.defenderKingdom)) out.addAll(w.side(w.sideOf(a)));
        }
        return out;
    }

    /** Every realm defending in a siege: the city's realm and its allies on its side of those wars. */
    public static java.util.Set<java.util.UUID> defendersOf(ValorisData data, Siege s) {
        java.util.Set<java.util.UUID> out = new java.util.LinkedHashSet<>();
        out.add(s.defenderKingdom);
        java.util.Set<java.util.UUID> explicit = new java.util.LinkedHashSet<>(s.coAttackers);
        explicit.add(s.attackerKingdom);
        for (java.util.UUID a : explicit) {
            for (ValorisData.War w : data.warsBetween(a, s.defenderKingdom)) out.addAll(w.side(w.sideOf(s.defenderKingdom)));
        }
        return out;
    }

    /** Who may act inside a besieged city's land: both sides (allies included) and the city's own residents. */
    public static boolean isParticipant(ServerPlayer player, Siege s) {
        ValorisData data = ValorisData.get(player.getServer());
        if (Realms.of(player).map(r -> attackersOf(data, s).contains(r.id()) || defendersOf(data, s).contains(r.id()))
                .orElse(false)) {
            return true;
        }
        return Roles.cityOfMember(player).map(c -> c.teamId.equals(s.cityTeam)).orElse(false);
    }

    /** True if the player is on either side of a siege in its active phase. */
    public static boolean inActiveSiege(ServerPlayer player) {
        ValorisData data = ValorisData.get(player.getServer());
        for (Siege s : data.sieges()) {
            if (s.phase == SiegePhase.ACTIVE && isParticipant(player, s)) return true;
        }
        return false;
    }

    public static boolean isAttacker(ServerPlayer player, Siege s) {
        ValorisData data = ValorisData.get(player.getServer());
        return Realms.of(player).map(r -> attackersOf(data, s).contains(r.id())).orElse(false);
    }

    public static void status(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        ValorisData data = ValorisData.get(server);
        long now = server.overworld().getGameTime();
        Optional<Realm> own = Realms.of(player);
        StringBuilder sb = new StringBuilder("Sieges:");
        boolean any = false;
        for (Siege s : data.sieges()) {
            if (own.isEmpty() || !(s.isAttacking(own.get().id()) || own.get().id().equals(s.defenderKingdom))) continue;
            any = true;
            long remaining = (s.phase == SiegePhase.PREPARATION ? s.startTick : s.endTick) - now;
            sb.append("\n - ").append(s.attackerName).append(" vs ").append(s.cityName).append(" (")
                    .append(s.defenderName).append("): ")
                    .append(s.phase == SiegePhase.PREPARATION ? "begins in " : "ends in ")
                    .append(formatTime(Math.max(0, remaining / 20)));
        }
        if (!any) sb.append("\n - none involving your realm");
        Msg.info(player, sb.toString());
    }

    // ------------------------------------------------------------ helpers

    private static void notifyBoth(ValorisData data, Siege s, Component msg) {
        for (java.util.UUID a : attackersOf(data, s)) Realms.message(data, a, msg);
        // the defending side (or the city itself if its realm is gone)
        for (java.util.UUID d : defendersOf(data, s)) Realms.message(data, d, msg);
        if (Realms.byId(data, s.defenderKingdom).isEmpty()) Msg.toTeam(s.cityTeam, msg);
    }

    private static String capital(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static int closestMark(long remainingSec) {
        for (int mark : MARKS) {
            if (remainingSec <= mark) return mark;
        }
        return Integer.MAX_VALUE;
    }

    public static String formatTime(long seconds) {
        if (seconds >= 60) {
            long m = seconds / 60;
            long r = seconds % 60;
            String minutes = m + (m == 1 ? " minute" : " minutes");
            return r == 0 ? minutes : minutes + " " + r + " s";
        }
        return seconds + (seconds == 1 ? " second" : " seconds");
    }
}
