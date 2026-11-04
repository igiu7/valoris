package com.valoris.coup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.valoris.Config;
import com.valoris.city.CityService;
import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.data.ValorisData.Kingdom;
import com.valoris.net.CoupPayload;
import com.valoris.realm.Realms;
import com.valoris.realm.Realms.Realm;
import com.valoris.util.Errors;
import com.valoris.util.Msg;
import com.valoris.valoris2;
import com.valoris.war.SiegeEvents;

import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Coup d'etat. A subject of a kingdom or an independent city (anyone but its leader) starts one while he and the
 * leader stand on the land of the same city of the realm, and no siege touches the realm (none of its cities
 * besieged, none besieged by it, preparation included). Everyone of the realm online then has Config.COUP_CHOICE_SECONDS
 * to choose a side in a window they cannot close (client.CoupScreen); whoever does not choose stands with the leader.
 * Then the fight: PvP is open in that city, the two sides cannot hurt their own. The leader of the coup wins and
 * takes the realm if the ruler dies; the ruler wins if the leader dies, or when Config.COUP_MINUTES are over. Whoever of
 * the two leaves the city (or the game) loses unless back within Config.COUP_RETURN_SECONDS - everyone sees the
 * countdown. A won coup: in an independent city the leader becomes its ruler; a kingdom changes hands whole - the
 * leader moves into the capital (which stays where it is) and rules it, so he is king of every city of the kingdom. A
 * city he ruled himself passes to its next resident (only if it has none does it become the capital instead). The
 * fallen ruler stays a resident of the capital.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class CoupService {
    private CoupService() {}

    static final int RULER = 1, LEADER = 2;

    private static final class Coup {
        final UUID realm;
        final String realmTitle, rulerTitle;
        final UUID city;          // the city where it happens
        final UUID leader, ruler;
        final String leaderName, rulerName;
        final Map<UUID, Integer> sides = new LinkedHashMap<>();
        final long fightAt, endAt; // game ticks
        boolean fighting;
        final Map<UUID, Long> outsideSince = new HashMap<>();
        String prevPvp;           // the city's ALLOW_PVP before the fight (null: left as it was)
        Long previousCoup;        // the realm's last coup before this one (back if this one is called off)
        final long startedMs = System.currentTimeMillis();
        final int[] fallen = new int[3]; // players of each side (RULER, LEADER) who went down in the fight
        final ServerBossEvent bar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

        Coup(UUID realm, String realmTitle, String rulerTitle, UUID city, UUID leader, String leaderName, UUID ruler,
             String rulerName, long now) {
            this.realm = realm;
            this.realmTitle = realmTitle;
            this.rulerTitle = rulerTitle;
            this.city = city;
            this.leader = leader;
            this.leaderName = leaderName;
            this.ruler = ruler;
            this.rulerName = rulerName;
            this.fightAt = now + Config.COUP_CHOICE_SECONDS.get() * 20L;
            this.endAt = fightAt + Config.COUP_MINUTES.get() * 1200L;
        }
    }

    private static final Map<UUID, Coup> COUPS = new HashMap<>();      // realm -> coup

    // ------------------------------------------------------------------ starting

    private static Optional<City> cityAt(ValorisData data, ServerPlayer p) {
        return SiegeEvents.cityAt(data, p.serverLevel(), p.blockPosition());
    }

    private static List<UUID> members(Realm realm) {
        List<UUID> out = new ArrayList<>();
        for (UUID city : Realms.citiesOf(realm)) {
            FTBTeamsAPI.api().getManager().getTeamByID(city).ifPresent(t -> out.addAll(t.getMembers()));
        }
        return out;
    }

    public static void start(ServerPlayer player) throws CommandSyntaxException {
        MinecraftServer server = player.getServer();
        ValorisData data = ValorisData.get(server);
        Realm realm = Realms.of(player).orElseThrow(() -> Errors.of("You are not a subject of a kingdom or a city."));
        UUID rulerId = Realms.leaderOf(data, realm).orElseThrow(() -> Errors.of("Your realm has no ruler."));
        if (rulerId.equals(player.getUUID())) throw Errors.of("You already rule the " + realm.title() + ".");
        if (COUPS.containsKey(realm.id())) throw Errors.of("A coup is already under way in the " + realm.title() + ".");
        long now = server.overworld().getGameTime();
        long allowed = data.lastCoups.getOrDefault(realm.id(), 0L) + Config.COUP_HOURS_BETWEEN.get() * 3_600_000L;
        if (System.currentTimeMillis() < allowed) {
            throw Errors.of("The " + realm.title() + " already had a coup d'etat today. The next one is possible in "
                    + com.valoris.moderation.Durations.format(allowed - System.currentTimeMillis()) + ".");
        }
        List<UUID> cities = Realms.citiesOf(realm);
        boolean siege = data.sieges().stream().anyMatch(s -> s.isAttacking(realm.id()) || s.defenderKingdom.equals(realm.id())
                || cities.contains(s.cityTeam));
        if (siege) throw Errors.of("No coup while the " + realm.title() + " besieges or is besieged.");
        for (UUID c : cities) {
            if (data.conquestOfCity(c).isPresent()) throw Errors.of("No coup while a city of the " + realm.title() + " awaits its conqueror's decision.");
        }
        if (com.valoris.war.Looting.involves(realm.id()) || cities.stream().anyMatch(com.valoris.war.Looting::involves)) {
            throw Errors.of("No coup while the " + realm.title() + " loots or is looted.");
        }
        ServerPlayer ruler = server.getPlayerList().getPlayer(rulerId);
        String leaderTitle = realm.leaderTitle();
        if (ruler == null) throw Errors.of("The " + leaderTitle + " is not here. A coup needs him in the same city as you.");
        City here = cityAt(data, player).filter(c -> cities.contains(c.teamId))
                .orElseThrow(() -> Errors.of("Stand in a city of the " + realm.title() + ", where the " + leaderTitle + " is."));
        if (ruler.level() != player.level() || cityAt(data, ruler).map(c -> !c.teamId.equals(here.teamId)).orElse(true)) {
            throw Errors.of("The " + leaderTitle + " must be in " + here.name + " with you.");
        }

        Coup coup = new Coup(realm.id(), realm.title(), leaderTitle, here.teamId, player.getUUID(),
                player.getGameProfile().getName(), rulerId, ruler.getGameProfile().getName(), now);
        for (UUID id : members(realm)) coup.sides.put(id, RULER);
        coup.sides.put(coup.leader, LEADER);
        coup.sides.put(coup.ruler, RULER);
        COUPS.put(realm.id(), coup);
        coup.previousCoup = data.lastCoups.put(realm.id(), System.currentTimeMillis());
        data.setDirty();

        Msg.news(server, "🗡️", "COUP D'ETAT! " + coup.leaderName + " rises against the " + leaderTitle + " of the "
                + realm.title() + ", " + coup.rulerName + ", in " + here.name + "!", ChatFormatting.DARK_PURPLE);
        int seconds = Config.COUP_CHOICE_SECONDS.get();
        for (UUID id : coup.sides.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            coup.bar.addPlayer(p);
            boolean locked = id.equals(coup.leader) || id.equals(coup.ruler); // they see it too, their side is fixed
            PacketDistributor.sendToPlayer(p, new CoupPayload(coup.leaderName, coup.rulerName, leaderTitle, realm.title(), seconds,
                    coup.sides.get(id), locked));
        }
        updateBar(server, coup, now);
    }

    /** A subject picks a side during the choice (from client.CoupScreen). */
    public static void choose(ServerPlayer player, int side) throws CommandSyntaxException {
        Coup coup = coupOf(player.getUUID()).orElseThrow(() -> Errors.of("There is no coup in your realm."));
        if (player.getUUID().equals(coup.leader) || player.getUUID().equals(coup.ruler)) throw Errors.of("Your side is clear.");
        if (coup.fighting) throw Errors.of("The sides are chosen; the fight is on.");
        if (side != RULER && side != LEADER) return;
        coup.sides.put(player.getUUID(), side);
        Msg.info(player, side == LEADER ? "You join " + coup.leaderName + "." : "You stand with " + coup.rulerName + ".");
    }

    // ------------------------------------------------------------------ the rest of the game during a coup

    /** True if a coup is under way in this realm (kingdom id or independent city). */
    public static boolean underway(UUID realm) {
        return COUPS.containsKey(realm);
    }

    /**
     * Who rules the realm and who belongs to it is settled by the coup: no kicking, handing over, crowning, joining,
     * leaving, rebelling or disbanding in its realm meanwhile.
     */
    public static void checkNoCoup(ValorisData data, City city) throws CommandSyntaxException {
        Optional<Realm> realm = Realms.ofCity(data, city);
        if (realm.isPresent() && underway(realm.get().id())) {
            throw Errors.of("A coup d'etat is under way in the " + realm.get().title() + ". Wait until it is over.");
        }
    }

    /** The realm (kingdom id or city) can start no siege during its coup. */
    public static void checkNoCoup(UUID realm, String title) throws CommandSyntaxException {
        if (underway(realm)) throw Errors.of("A coup d'etat is under way in the " + title + ". Wait until it is over.");
    }

    /** A siege against the realm calls its coup off: the enemy is at the gates. */
    public static void siegeStarted(MinecraftServer server, ValorisData data, UUID attacker, UUID defender, UUID city) {
        for (Coup coup : List.copyOf(COUPS.values())) {
            Optional<Realm> realm = Realms.byId(data, coup.realm);
            boolean hit = coup.realm.equals(defender) || coup.realm.equals(attacker)
                    || realm.map(r -> Realms.citiesOf(r).contains(city)).orElse(false);
            if (hit) callOff(server, coup, "A siege begins: the coup in the " + coup.realmTitle + " is called off.");
        }
    }

    /** Ends a coup without a winner: the ruler keeps the realm. */
    private static void callOff(MinecraftServer server, Coup coup, String why) {
        COUPS.remove(coup.realm);
        ValorisData data = ValorisData.get(server);
        if (coup.previousCoup == null) data.lastCoups.remove(coup.realm);
        else data.lastCoups.put(coup.realm, coup.previousCoup);
        data.setDirty();
        titles(server, coup, null, null);
        coup.bar.removeAllPlayers();
        restorePvp(coup);
        Msg.news(server, "🏳️", why, ChatFormatting.YELLOW);
    }

    /**
     * Still the same coup? The realm must exist and keep its ruler, and the leader must still belong to it (one who
     * left it lost). Returns true if the coup ended.
     */
    private static boolean stillValid(MinecraftServer server, ValorisData data, Coup coup) {
        Optional<Realm> realm = Realms.byId(data, coup.realm);
        if (realm.isEmpty() || !Realms.leaderOf(data, realm.get()).map(coup.ruler::equals).orElse(false)) {
            callOff(server, coup, "The " + coup.realmTitle + " changed: the coup is called off.");
            return true;
        }
        if (!members(realm.get()).contains(coup.leader)) {
            end(server, coup, RULER, coup.leaderName + " left the " + coup.realmTitle + ": the coup failed.");
            return true;
        }
        return false;
    }

    private static Optional<Coup> coupOf(UUID player) {
        return COUPS.values().stream().filter(c -> c.sides.containsKey(player)).findFirst();
    }

    // ------------------------------------------------------------------ the fight

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (COUPS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % 20 != 0) return;
        ValorisData data = ValorisData.get(server);
        for (Coup coup : List.copyOf(COUPS.values())) {
            if (stillValid(server, data, coup)) continue;
            if (!coup.fighting && now >= coup.fightAt) beginFight(server, coup);
            if (coup.fighting) {
                if (now >= coup.endAt) {
                    end(server, coup, RULER, coup.rulerName + " held out: the coup of " + coup.leaderName + " failed.");
                    continue;
                }
                if (checkBorders(server, data, coup, now)) continue;
            }
            updateBar(server, coup, now);
        }
    }

    private static void beginFight(MinecraftServer server, Coup coup) {
        coup.fighting = true;
        FTBTeamsAPI.api().getManager().getTeamByID(coup.city).ifPresent(team -> {
            if (!team.getProperty(FTBChunksProperties.ALLOW_PVP)) {
                coup.prevPvp = "false";
                team.setProperty(FTBChunksProperties.ALLOW_PVP, true);
                team.markDirty();
            }
        });
        long leaders = coup.sides.values().stream().filter(s -> s == LEADER).count();
        long loyal = coup.sides.size() - leaders;
        tell(server, coup, Msg.text("The sides are chosen: " + leaders + " with " + coup.leaderName + ", " + loyal + " with "
                + coup.rulerName + ". Fight! " + coup.leaderName + " wins by killing the " + coup.rulerTitle + "; "
                + coup.rulerName + " wins by killing " + coup.leaderName + " or holding out " + Config.COUP_MINUTES.get()
                + " minutes.", ChatFormatting.LIGHT_PURPLE));
    }

    /** The leader and the ruler must stay in the city. True if the coup ended. */
    private static boolean checkBorders(MinecraftServer server, ValorisData data, Coup coup, long now) {
        int limit = Config.COUP_RETURN_SECONDS.get();
        for (UUID id : new UUID[]{coup.ruler, coup.leader}) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            boolean inside = p != null && cityAt(data, p).map(c -> c.teamId.equals(coup.city)).orElse(false);
            String name = id.equals(coup.ruler) ? coup.rulerName : coup.leaderName;
            String other = id.equals(coup.ruler) ? coup.leaderName : coup.rulerName;
            if (inside) {
                if (coup.outsideSince.remove(id) != null) {
                    titles(server, coup, null, null);
                    tell(server, coup, Msg.text(name + " is back in the city.", ChatFormatting.YELLOW));
                }
                continue;
            }
            long since = coup.outsideSince.computeIfAbsent(id, k -> now);
            int left = (int) (limit - (now - since) / 20);
            if (left <= 0) {
                end(server, coup, id.equals(coup.ruler) ? LEADER : RULER, name + " left the city: " + other + " wins the coup.");
                return true;
            }
            titles(server, coup, Component.literal(String.valueOf(left)).withStyle(ChatFormatting.RED),
                    Component.literal(other + " wins the coup if " + name + " does not return to the city").withStyle(ChatFormatting.GOLD));
        }
        return false;
    }

    /** At the highest priority: going down counts (PlayerRevive turns the death into bleeding out afterwards). */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer dead) || COUPS.isEmpty()) return;
        for (Coup coup : List.copyOf(COUPS.values())) {
            if (!coup.fighting) continue;
            Integer side = coup.sides.get(dead.getUUID());
            if (side != null && (side == RULER || side == LEADER)) coup.fallen[side]++;
            if (dead.getUUID().equals(coup.ruler)) {
                end(dead.getServer(), coup, LEADER, coup.rulerName + " has fallen. " + coup.leaderName + " seizes power!");
            } else if (dead.getUUID().equals(coup.leader)) {
                end(dead.getServer(), coup, RULER, coup.leaderName + " has fallen. The coup is crushed.");
            }
        }
    }

    /** Before the fight nobody of the realm hurts each other; during it, nobody hurts his own side. */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (COUPS.isEmpty() || !(event.getEntity() instanceof Player victim)) return;
        if (!(event.getSource().getEntity() instanceof Player attacker) || attacker == victim) return;
        Optional<Coup> coup = coupOf(victim.getUUID());
        if (coup.isEmpty() || !coup.get().sides.containsKey(attacker.getUUID())) return;
        Coup c = coup.get();
        if (!c.fighting || c.sides.get(attacker.getUUID()).equals(c.sides.get(victim.getUUID()))) event.setCanceled(true);
    }

    // ------------------------------------------------------------------ the end

    private static void end(MinecraftServer server, Coup coup, int winner, String why) {
        COUPS.remove(coup.realm);
        titles(server, coup, null, null);
        coup.bar.removeAllPlayers();
        restorePvp(coup);
        if (winner == LEADER) {
            try {
                seize(server, coup);
            } catch (CommandSyntaxException | RuntimeException e) {
                valoris2.LOGGER.warn("Coup: could not hand the realm over", e);
                Msg.news(server, "⚠️", "The coup in the " + coup.realmTitle + " ended, but the power could not change hands: "
                        + e.getMessage(), ChatFormatting.RED);
                return;
            }
            Msg.news(server, "👑", why + " " + coup.leaderName + " now rules the " + coup.realmTitle + ".", ChatFormatting.DARK_PURPLE);
        } else {
            Msg.news(server, "🛡️", why + " " + coup.rulerName + " remains the " + coup.rulerTitle + " of the "
                    + coup.realmTitle + ".", ChatFormatting.GOLD);
        }
        com.valoris.discord.DiscordLink.announceRaw(summary(coup, winner, why));
    }

    /** The end of the coup for the Discord news channel: who won, how, and how both sides fared. */
    private static String summary(Coup coup, int winner, String why) {
        java.util.function.Function<String, String> p = com.valoris.discord.DiscordLink::plain;
        int[] count = new int[3];
        for (int side : coup.sides.values()) if (side == RULER || side == LEADER) count[side]++;
        long seconds = Math.max(0, (System.currentTimeMillis() - coup.startedMs) / 1000);
        String length = seconds >= 60 ? (seconds / 60) + " min " + (seconds % 60) + " s" : seconds + " s";
        boolean leaderWon = winner == LEADER;
        return "📊 **The coup in the " + p.apply(coup.realmTitle) + " is over**\n"
                + "Result: **" + (leaderWon ? p.apply(coup.leaderName) + " seized power" : p.apply(coup.rulerName) + " held the throne")
                + "** (" + p.apply(why) + ")\n"
                + "Lasted: " + length + "\n\n"
                + "**For the " + p.apply(coup.rulerTitle) + " " + p.apply(coup.rulerName) + "**: " + count[RULER]
                + (count[RULER] == 1 ? " player" : " players") + ", " + coup.fallen[RULER] + " fell\n"
                + "**For the rebel " + p.apply(coup.leaderName) + "**: " + count[LEADER]
                + (count[LEADER] == 1 ? " player" : " players") + ", " + coup.fallen[LEADER] + " fell";
    }

    private static void restorePvp(Coup coup) {
        if (coup.prevPvp == null) return;
        FTBTeamsAPI.api().getManager().getTeamByID(coup.city).ifPresent(team -> {
            team.setProperty(FTBChunksProperties.ALLOW_PVP, Boolean.parseBoolean(coup.prevPvp));
            team.markDirty();
        });
        coup.prevPvp = null;
    }

    /** The leader of the coup takes the realm; the fallen ruler stays a subject. */
    private static void seize(MinecraftServer server, Coup coup) throws CommandSyntaxException {
        ValorisData data = ValorisData.get(server);
        Realm realm = Realms.byId(data, coup.realm).orElseThrow(() -> Errors.of("the realm no longer exists"));
        GameProfile profile = new GameProfile(coup.leader, coup.leaderName);
        if (!realm.isKingdom()) {
            take(server, realm.city(), profile);
            return;
        }
        // a kingdom: the whole kingdom changes hands, its capital stays where it is. The leader moves into the capital
        // (if he lives elsewhere) and rules it, so he is king; the fallen king stays there as a resident
        Kingdom k = realm.kingdom();
        City capital = data.byTeam(k.capital).orElseThrow(() -> Errors.of("the kingdom has no capital"));
        PartyTeam capitalParty = CityService.partyOf(capital);
        if (!capitalParty.getMembers().contains(coup.leader)) {
            City from = null;
            for (UUID cityId : k.cities) {
                City c = data.byTeam(cityId).orElse(null);
                if (c != null && CityService.partyOf(c).getMembers().contains(coup.leader)) from = c;
            }
            if (from == null) throw Errors.of(coup.leaderName + " no longer lives in the kingdom");
            PartyTeam party = CityService.partyOf(from);
            if (party.isOwner(coup.leader)) {
                // he rules a city of his own: it passes to the next of its residents (officers first)
                Optional<UUID> heir = party.getMembers().stream().filter(id -> !id.equals(coup.leader))
                        .max(java.util.Comparator.comparingInt(id -> party.getRankForPlayer(id).getPower()));
                if (heir.isEmpty()) {
                    // nobody to leave it to (leaving would empty and delete the city): it becomes the capital instead
                    City city = from;
                    com.valoris.tech.TechService.moveResearch(data, capital, city);
                    k.capital = city.teamId;
                    data.touch();
                    com.valoris.city.ClaimLimitHook.syncLimits(server, capital);
                    com.valoris.city.ClaimLimitHook.syncLimits(server, city);
                    Msg.toKingdom(k, Msg.text(coup.leaderName + " has no one to leave " + city.name
                            + " to: it becomes the capital of the " + k.title() + ".", ChatFormatting.YELLOW));
                    return;
                }
                take(server, from, new GameProfile(heir.get(), Msg.nameOf(server, heir.get())));
                Msg.toTeam(from.teamId, Msg.text(Msg.nameOf(server, heir.get()) + " now rules " + from.name
                        + ": " + coup.leaderName + " moves to the capital.", ChatFormatting.GOLD));
            }
            ServerPlayer online = server.getPlayerList().getPlayer(coup.leader);
            City left = from;
            com.valoris.events.TeamGuard.allow(() -> party.leave(coup.leader));
            try {
                com.valoris.events.TeamGuard.allow(() -> capitalParty.join(online, profile));
            } catch (CommandSyntaxException | RuntimeException e) {
                com.valoris.events.TeamGuard.allow(() -> party.join(online, profile)); // back home rather than homeless
                throw e;
            }
            left.members.remove(coup.leader);
            capital.members.add(coup.leader);
            data.touch();
        }
        take(server, capital, profile);
    }

    private static void take(MinecraftServer server, City city, GameProfile profile) throws CommandSyntaxException {
        PartyTeam party = CityService.partyOf(city);
        party.transferOwnership(server.createCommandSourceStack(), profile);
        city.ruler = profile.getId();
        ValorisData.get(server).touch();
    }

    // ------------------------------------------------------------------ showing it

    private static void tell(MinecraftServer server, Coup coup, Component text) {
        for (UUID id : coup.sides.keySet()) Msg.toPlayer(server, id, text);
    }

    private static void titles(MinecraftServer server, Coup coup, Component title, Component subtitle) {
        for (UUID id : coup.sides.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            if (title == null) {
                p.connection.send(new ClientboundClearTitlesPacket(true));
                continue;
            }
            p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 30, 5));
            p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }

    private static void updateBar(MinecraftServer server, Coup coup, long now) {
        long left = coup.fighting ? coup.endAt - now : coup.fightAt - now;
        long secs = Math.max(0, left / 20);
        String time = secs / 60 + ":" + String.format("%02d", secs % 60);
        long leaders = coup.sides.values().stream().filter(s -> s == LEADER).count();
        coup.bar.setName(Component.literal("Coup d'etat: " + coup.leaderName + " (" + leaders + ") vs " + coup.rulerName
                + " (" + (coup.sides.size() - leaders) + ")  -  " + (coup.fighting ? time : "choosing sides " + time)));
        long total = coup.fighting ? coup.endAt - coup.fightAt : coup.fightAt - (coup.fightAt - Config.COUP_CHOICE_SECONDS.get() * 20L);
        coup.bar.setProgress(Math.max(0F, Math.min(1F, left / (float) Math.max(1, total))));
        for (UUID id : coup.sides.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) coup.bar.addPlayer(p);
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        for (Coup coup : COUPS.values()) { // a coup does not outlive a restart: the ruler keeps the realm
            coup.bar.removeAllPlayers();
            restorePvp(coup);
        }
        COUPS.clear();
    }
}
