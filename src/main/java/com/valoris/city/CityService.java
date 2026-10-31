package com.valoris.city;

import java.util.List;
import java.util.Optional;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.data.ValorisData.Kingdom;
import com.valoris.diplomacy.Requests;
import com.valoris.diplomacy.Requests.Type;
import com.valoris.kingdom.KingdomService;
import com.valoris.registry.ModBlocks;
import com.valoris.war.SiegeService;
import com.valoris.util.Errors;
import com.valoris.util.Msg;
import com.valoris.util.Names;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class CityService {
    private CityService() {}

    // ------------------------------------------------------------ lookups

    /**
     * The city ruled by this player. A city whose FTB team cannot be found is NOT deleted here (that used to wipe cities
     * and their technologies when FTB data failed to load): it is kept, reported to the staff and repaired by
     * DataVault when its ruler logs in.
     */
    public static Optional<City> cityOf(ServerPlayer player) {
        ValorisData data = ValorisData.get(player.getServer());
        Optional<City> city = data.byRuler(player.getUUID());
        if (city.isPresent() && FTBTeamsAPI.api().getManager().getTeamByID(city.get().teamId).isEmpty()) {
            com.valoris.data.DataVault.reportOrphan(player.getServer(), city.get());
            return Optional.empty();
        }
        return city;
    }

    public static City requireRuler(ServerPlayer player) throws CommandSyntaxException {
        return cityOf(player).orElseThrow(() -> Errors.of("You are not a city ruler."));
    }

    public static City requireFounded(ServerPlayer player) throws CommandSyntaxException {
        City city = requireRuler(player);
        if (!city.founded) throw Errors.of("Your city is not founded yet. Place the city banner first.");
        return city;
    }

    public static PartyTeam partyOf(City city) throws CommandSyntaxException {
        return FTBTeamsAPI.api().getManager().getTeamByID(city.teamId)
                .filter(t -> t instanceof PartyTeam)
                .map(t -> (PartyTeam) t)
                .orElseThrow(() -> Errors.of("The city's FTB team no longer exists."));
    }

    private static boolean isInParty(ServerPlayer player) {
        return FTBTeamsAPI.api().getManager().getTeamForPlayer(player).map(Team::isPartyTeam).orElse(false);
    }

    // ------------------------------------------------------------ founding

    public static City createCity(ServerPlayer player, String rawName) throws CommandSyntaxException {
        String name = Names.check(rawName);
        ValorisData data = ValorisData.get(player.getServer());
        if (cityOf(player).isPresent()) throw Errors.of("You already rule a city.");
        if (data.cityByName(name).isPresent()) throw Errors.of("A city with that name already exists.");
        if (data.kingdomByName(name).isPresent()) throw Errors.of("A kingdom with that name already exists.");
        if (player.getInventory().getFreeSlot() == -1) {
            throw Errors.of("You need a free inventory slot to receive the city banner.");
        }

        // Throws if the player is already in a party, name is taken in FTB Teams, etc.
        Team team = FTBTeamsAPI.api().getManager().createPartyTeam(player, name, null, null);
        City city = new City(team.getId(), player.getUUID(), name);
        city.members.add(player.getUUID());
        data.add(city);
        CityBanner.giveIfMissing(player);
        Msg.ok(player, "City \"" + name + "\" created. Place the red city banner you received to found it.");
        return city;
    }

    /** Called when the city banner is placed. Returns false if the placement must be cancelled. */
    public static boolean tryFound(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Optional<City> opt = cityOf(player);
        if (opt.isEmpty()) return true; // not a city ruler: an ordinary banner
        if (opt.get().founded) return relocateBanner(player, level, pos, opt.get());
        City city = opt.get();
        Team team = FTBTeamsAPI.api().getManager().getTeamByID(city.teamId).orElse(null);
        if (team == null) return true;

        ChunkTeamData td = FTBChunksAPI.api().getManager().getOrCreateData(team);
        ClaimResult result = td.claim(player.createCommandSourceStack(), new ChunkDimPos(level, pos), false);
        if (!result.isSuccess()) {
            player.sendSystemMessage(Component.literal("Cannot found a city here: ")
                    .withStyle(ChatFormatting.RED).append(result.getMessage()));
            return false;
        }
        city.founded = true;
        city.bannerDim = level.dimension().location().toString();
        city.bannerPos = pos.asLong();
        ValorisData.get(player.getServer()).touch();
        player.server.execute(() -> CityBanner.removeAll(player));
        Msg.ok(player, "The city of " + city.name + " has been founded! You may claim up to "
                + com.valoris.tech.TechService.claimLimit(city) + " chunks.");
        return true;
    }

    // ------------------------------------------------------------ city banner block

    public static ServerLevel levelOf(MinecraftServer server, City city) {
        if (!city.hasBanner()) return null;
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(city.bannerDim)));
    }

    /** True if the banner block exists. Unloaded chunks count as "present" (no chunk loading here). */
    public static boolean bannerPresent(MinecraftServer server, City city) {
        ServerLevel level = levelOf(server, city);
        if (level == null) return false;
        BlockPos pos = BlockPos.of(city.bannerPos);
        if (!level.isLoaded(pos)) return true;
        BlockState state = level.getBlockState(pos);
        return state.is(ModBlocks.CITY_BANNER.get());
    }

    /** /valoris city banner: hands out a replacement city banner when the block is missing. */
    public static void giveReplacementBanner(ServerPlayer player) throws CommandSyntaxException {
        City city = requireFounded(player);
        com.valoris.war.ConquestService.checkNotConquered(ValorisData.get(player.getServer()), city);
        if (SiegeService.isUnderSiege(ValorisData.get(player.getServer()), city.teamId)) {
            throw Errors.of("You cannot move the city banner during a siege.");
        }
        if (city.hasBanner() && bannerPresent(player.getServer(), city)) {
            throw Errors.of("Your city banner is intact.");
        }
        if (player.getInventory().getFreeSlot() == -1) throw Errors.of("You need a free inventory slot.");
        CityBanner.giveIfMissing(player);
        Msg.ok(player, "Place the banner inside your city's claimed land to register it as the city banner.");
    }

    /**
     * /valoris city banner move: puts the city banner where the ruler stands (inside the city's land).
     * Not while a siege is coming or running, nor while the city awaits its conqueror's decision.
     */
    public static void moveBanner(ServerPlayer player) throws CommandSyntaxException {
        City city = requireFounded(player);
        MinecraftServer server = player.getServer();
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        com.valoris.war.ConquestService.checkNotConquered(ValorisData.get(server), city);
        if (ValorisData.get(server).siegeOfCity(city.teamId).isPresent()) {
            throw Errors.of("The city banner cannot be moved while the city is besieged or a siege is coming.");
        }

        ClaimedChunk chunk = FTBChunksAPI.api().getManager().getChunk(new ChunkDimPos(level, pos));
        if (chunk == null || !chunk.getTeamData().getTeam().getId().equals(city.teamId)) {
            throw Errors.of("Stand inside your city's claimed land to move the city banner.");
        }
        BlockState banner = ModBlocks.CITY_BANNER.get().defaultBlockState()
                .setValue(CityBannerBlock.FACING, player.getDirection().getOpposite());
        BlockState current = level.getBlockState(pos);
        if (!(current.isAir() || current.canBeReplaced())) {
            throw Errors.of("The banner cannot stand here. You need a free spot.");
        }

        // remove the old banner block (if it still stands)
        ServerLevel oldLevel = levelOf(server, city);
        if (oldLevel != null) {
            BlockPos old = BlockPos.of(city.bannerPos);
            BlockState oldState = oldLevel.getBlockState(old);
            if (oldState.is(ModBlocks.CITY_BANNER.get())) oldLevel.removeBlock(old, false);
        }

        level.setBlock(pos, banner, 3);
        city.bannerDim = level.dimension().location().toString();
        city.bannerPos = pos.asLong();
        ValorisData.get(server).touch();
        Msg.toTeam(city.teamId, Msg.text("The city banner of " + city.name + " was moved by "
                + player.getGameProfile().getName() + ".", ChatFormatting.GOLD));
    }

    private static boolean relocateBanner(ServerPlayer player, ServerLevel level, BlockPos pos, City city) {
        MinecraftServer server = player.getServer();
        if (city.hasBanner() && bannerPresent(server, city)) {
            player.sendSystemMessage(Msg.text("Your city banner already stands.", ChatFormatting.RED));
            return false;
        }
        ValorisData vd = ValorisData.get(server);
        if (vd.conquestOfCity(city.teamId).isPresent() || vd.siegeOfCity(city.teamId).isPresent()) {
            player.sendSystemMessage(Msg.text("The city banner cannot be placed now (siege or conquest).", ChatFormatting.RED));
            return false;
        }
        ClaimedChunk chunk = FTBChunksAPI.api().getManager().getChunk(new ChunkDimPos(level, pos));
        if (chunk == null || !chunk.getTeamData().getTeam().getId().equals(city.teamId)) {
            player.sendSystemMessage(Msg.text("Place the city banner inside your city's claimed land.", ChatFormatting.RED));
            return false;
        }
        city.bannerDim = level.dimension().location().toString();
        city.bannerPos = pos.asLong();
        ValorisData.get(server).touch();
        server.execute(() -> CityBanner.removeAll(player));
        Msg.ok(player, "City banner registered.");
        return true;
    }

    // ------------------------------------------------------------ claims

    private static ChunkTeamData foundedCityData(ServerPlayer player) throws CommandSyntaxException {
        City city = requireFounded(player);
        Team team = FTBTeamsAPI.api().getManager().getTeamByID(city.teamId)
                .orElseThrow(() -> Errors.of("The city's FTB team no longer exists."));
        return FTBChunksAPI.api().getManager().getOrCreateData(team);
    }

    public static void claimHere(ServerPlayer player) throws CommandSyntaxException {
        ChunkTeamData td = foundedCityData(player);
        report(player, td.claim(player.createCommandSourceStack(), new ChunkDimPos(player), false), "Chunk claimed.");
    }

    public static void unclaimHere(ServerPlayer player) throws CommandSyntaxException {
        City city = requireFounded(player);
        if (SiegeService.isUnderSiege(ValorisData.get(player.getServer()), city.teamId)) {
            throw Errors.of("You cannot unclaim land during a siege.");
        }
        ChunkTeamData td = foundedCityData(player);
        report(player, td.unclaim(player.createCommandSourceStack(), new ChunkDimPos(player), false, false), "Chunk unclaimed.");
    }

    private static void report(ServerPlayer player, ClaimResult r, String ok) {
        if (r.isSuccess()) Msg.ok(player, ok);
        else player.sendSystemMessage(r.getMessage().withStyle(ChatFormatting.RED));
    }

    // ------------------------------------------------------------ info

    public static void info(ServerPlayer player) throws CommandSyntaxException {
        City city = Roles.cityOfMember(player).orElseThrow(() -> Errors.of("You do not belong to a city."));
        MinecraftServer server = player.getServer();
        Team team = FTBTeamsAPI.api().getManager().getTeamByID(city.teamId)
                .orElseThrow(() -> Errors.of("The city's FTB team no longer exists."));
        ChunkTeamData td = FTBChunksAPI.api().getManager().getOrCreateData(team);
        String kingdom = ValorisData.get(server).kingdomOfCity(city.teamId).map(k -> k.name).orElse("none");
        Msg.info(player, "City: " + city.name
                + "\nRuler: " + Msg.nameOf(server, city.ruler)
                + "\nFounded: " + (city.founded ? "yes" : "no (place the city banner)")
                + "\nResidents: " + team.getMembers().size()
                + "\nClaimed chunks: " + td.getClaimedChunks().size() + "/" + com.valoris.tech.TechService.claimLimit(city)
                + "\nKingdom: " + kingdom);
    }

    // ------------------------------------------------------------ residents

    /** Ruler invites a player to the city. */
    public static void invite(ServerPlayer ruler, ServerPlayer target) throws CommandSyntaxException {
        City city = requireFounded(ruler);
        if (target.getUUID().equals(ruler.getUUID())) throw Errors.of("You cannot invite yourself.");
        if (isInParty(target)) throw Errors.of(target.getGameProfile().getName() + " already belongs to a city.");
        Requests.add(ruler.getServer(), Type.CITY_INVITE, city.teamId, target.getUUID(), null);
        Msg.ok(ruler, "Invitation sent to " + target.getGameProfile().getName() + ".");
        target.sendSystemMessage(Msg.text(ruler.getGameProfile().getName() + " invited you to the city of "
                + city.name + " ", ChatFormatting.GOLD)
                .append(Msg.button("[Accept]", ChatFormatting.GREEN, "/valoris resident accept " + Msg.q(city.name)))
                .append(Component.literal(" "))
                .append(Msg.button("[Decline]", ChatFormatting.RED, "/valoris resident decline " + Msg.q(city.name))));
        com.valoris.diplomacy.Inbox.notify(ruler.getServer(), target.getUUID());
    }

    /** Player declines an invitation. */
    public static void declineInvite(ServerPlayer player, String cityName) throws CommandSyntaxException {
        MinecraftServer server = player.getServer();
        City city = ValorisData.get(server).cityByName(cityName).orElseThrow(() -> Errors.of("No such city."));
        Requests.find(server, Type.CITY_INVITE, city.teamId, player.getUUID()).ifPresent(Requests::remove);
        Msg.ok(player, "Invitation declined.");
        Msg.toPlayer(server, city.ruler, Msg.text(player.getGameProfile().getName() + " declined your invitation.",
                ChatFormatting.RED));
    }

    /** Player accepts an invitation. */
    public static void acceptInvite(ServerPlayer player, String cityName) throws CommandSyntaxException {
        MinecraftServer server = player.getServer();
        City city = ValorisData.get(server).cityByName(cityName).filter(c -> c.founded)
                .orElseThrow(() -> Errors.of("No such city."));
        Requests.Request r = Requests.find(server, Type.CITY_INVITE, city.teamId, player.getUUID())
                .orElseThrow(() -> Errors.of("You have no (valid) invitation from that city."));
        partyOf(city).join(player);
        Requests.remove(r);
        Msg.ok(player, "You are now a resident of " + city.name + ".");
    }

    /** Player asks to join a city. */
    public static void requestJoin(ServerPlayer player, String cityName) throws CommandSyntaxException {
        MinecraftServer server = player.getServer();
        if (isInParty(player)) throw Errors.of("You already belong to a city. Leave it first.");
        City city = ValorisData.get(server).cityByName(cityName).filter(c -> c.founded)
                .orElseThrow(() -> Errors.of("No such city."));
        Requests.add(server, Type.CITY_JOIN, player.getUUID(), city.teamId, null);
        Msg.ok(player, "Your request was sent to the ruler of " + city.name + ".");
        String name = player.getGameProfile().getName();
        Msg.toPlayer(server, city.ruler, Msg.text(name + " asks to join your city ", ChatFormatting.GOLD)
                .append(Msg.button("[Accept]", ChatFormatting.GREEN, "/valoris city accept " + name))
                .append(Component.literal(" "))
                .append(Msg.button("[Decline]", ChatFormatting.RED, "/valoris city decline " + name)));
        com.valoris.diplomacy.Inbox.notify(server, city.ruler);
    }

    public static void acceptRequest(ServerPlayer ruler, ServerPlayer target) throws CommandSyntaxException {
        City city = requireFounded(ruler);
        Requests.Request r = Requests.find(ruler.getServer(), Type.CITY_JOIN, target.getUUID(), city.teamId)
                .orElseThrow(() -> Errors.of("That player has no pending request to your city."));
        if (isInParty(target)) throw Errors.of("That player already joined another city.");
        partyOf(city).join(target);
        Requests.remove(r);
        Msg.ok(ruler, target.getGameProfile().getName() + " is now a resident.");
        Msg.ok(target, "You are now a resident of " + city.name + ".");
    }

    public static void declineRequest(ServerPlayer ruler, ServerPlayer target) throws CommandSyntaxException {
        City city = requireFounded(ruler);
        Requests.find(ruler.getServer(), Type.CITY_JOIN, target.getUUID(), city.teamId).ifPresent(Requests::remove);
        Msg.ok(ruler, "Request declined.");
        target.sendSystemMessage(Msg.text("The city of " + city.name + " declined your request.", ChatFormatting.RED));
    }

    public static void kick(ServerPlayer ruler, ServerPlayer target) throws CommandSyntaxException {
        City city = requireFounded(ruler);
        com.valoris.war.ConquestService.checkNotConquered(ValorisData.get(ruler.getServer()), city);
        com.valoris.events.TeamGuard.allow(() -> partyOf(city).kick(ruler.createCommandSourceStack(), List.of(target.getGameProfile())));
        Msg.ok(ruler, target.getGameProfile().getName() + " was removed from the city.");
    }

    /** A resident leaves their city. */
    public static void leave(ServerPlayer player) throws CommandSyntaxException {
        City city = Roles.cityOfMember(player).orElseThrow(() -> Errors.of("You do not belong to a city."));
        if (city.ruler.equals(player.getUUID())) {
            throw Errors.of("Rulers cannot leave. Transfer the city or disband it.");
        }
        com.valoris.events.TeamGuard.allow(() -> partyOf(city).leave(player.getUUID()));
        Msg.ok(player, "You left the city of " + city.name + ".");
    }

    /** Ruler hands the city to another resident. The capital cannot be transferred (use the crown). */
    public static void transfer(ServerPlayer ruler, ServerPlayer target) throws CommandSyntaxException {
        City city = requireFounded(ruler);
        ValorisData data = ValorisData.get(ruler.getServer());
        com.valoris.war.ConquestService.checkNotConquered(data, city);
        if (data.kingdomOfCity(city.teamId).map(k -> k.capital.equals(city.teamId)).orElse(false)) {
            throw Errors.of("This is a capital. Use /valoris kingdom crown or disband the kingdom first.");
        }
        partyOf(city).transferOwnership(ruler.createCommandSourceStack(), target.getGameProfile());
        city.ruler = target.getUUID();
        data.touch();
        Msg.ok(ruler, target.getGameProfile().getName() + " now rules " + city.name + ".");
    }

    // ------------------------------------------------------------ disbanding

    public static void disband(ServerPlayer player) throws CommandSyntaxException {
        City city = requireRuler(player);
        ValorisData data = ValorisData.get(player.getServer());
        Optional<ValorisData.Kingdom> kingdom = data.kingdomOfCity(city.teamId);
        if (kingdom.isPresent() && kingdom.get().capital.equals(city.teamId)) {
            throw Errors.of("You rule a kingdom. Disband the kingdom or transfer the crown first.");
        }
        if (kingdom.isPresent()) {
            throw Errors.of("Your city belongs to the " + kingdom.get().title()
                    + ". Only the " + kingdom.get().rulerTitle + " can release it, or you can rebel.");
        }
        // neither a besieged city nor an independent city that is besieging another one can slip away
        boolean siege = data.sieges().stream().anyMatch(s -> s.isAttacking(city.teamId) || s.cityTeam.equals(city.teamId));
        if (siege) throw Errors.of("You cannot disband the city during a siege.");
        if (com.valoris.war.Looting.involves(city.teamId)) throw Errors.of("You cannot disband the city during the looting time.");
        com.valoris.war.ConquestService.checkNotConquered(data, city);
        destroyCity(player.getServer(), city, player.createCommandSourceStack());
        CityBanner.removeAll(player);
        Msg.ok(player, "Your city has been disbanded.");
    }

    /** Unclaims everything, disbands the FTB team and removes the city (and its banner block). */
    public static void destroyCity(MinecraftServer server, City city, CommandSourceStack src) throws CommandSyntaxException {
        // archived while its residents and land are all still there
        com.valoris.data.DataVault.archiveCity(server, city, "destroyed");
        Team team = FTBTeamsAPI.api().getManager().getTeamByID(city.teamId).orElse(null);
        if (team != null) {
            ChunkTeamData td = FTBChunksAPI.api().getManager().getOrCreateData(team);
            // as the server: the ruler / banner-chunk rules of ClaimLimitHook do not apply when a city is destroyed
            CommandSourceStack serverSrc = server.createCommandSourceStack();
            for (ClaimedChunk c : List.copyOf(td.getClaimedChunks())) {
                td.unclaim(serverSrc, c.getPos(), false, true);
            }
            if (team instanceof PartyTeam party) com.valoris.events.TeamGuard.allow(() -> party.forceDisband(src));
        }
        ServerLevel level = levelOf(server, city);
        if (level != null) {
            BlockPos pos = BlockPos.of(city.bannerPos);
            if (level.isLoaded(pos)) {
                BlockState state = level.getBlockState(pos);
                if (state.is(ModBlocks.CITY_BANNER.get())) level.removeBlock(pos, false);
            }
        }
        onTeamDeleted(server, city.teamId); // no-op if the FTB event already handled it
    }

    // ------------------------------------------------------------ FTB hooks

    /** The FTB team of a city disappeared (disband, or removed through FTB commands). */
    public static void onTeamDeleted(MinecraftServer server, java.util.UUID teamId) {
        ValorisData data = ValorisData.get(server);
        data.byTeam(teamId).ifPresent(city -> {
            // everything about the city goes to the archive first, so its technologies can be given back
            com.valoris.data.DataVault.archiveCity(server, city, "removed");
            data.kingdomOfCity(teamId).ifPresent(k -> KingdomService.detachCity(server, data, k, city));
            data.remove(teamId);
        });
    }

    /** FTB team ownership changed (e.g. via /ftbteams): keep the ruler in sync. */
    public static void onOwnerChanged(MinecraftServer server, Team team) {
        ValorisData data = ValorisData.get(server);
        data.byTeam(team.getId()).ifPresent(city -> {
            city.ruler = team.getOwner();
            data.touch();
        });
    }
}
