package com.valoris.city;

import java.util.Optional;
import java.util.UUID;

import com.valoris.data.ValorisData;
import com.valoris.util.Msg;
import com.valoris.valoris2;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * A strip of one chunk (diagonals too) around every claim belongs to nobody else: no other realm may claim it
 * (ClaimLimitHook), and outsiders may not build in it - unless that claim's city is under siege (preparation
 * included) or being looted. Cities of one kingdom are not outsiders to each other.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class BufferZone {
    private BufferZone() {}

    /** Shown by FTB Chunks as a translation key; clients have no translation for it, so the key is the text. */
    public static final String CLAIM_KEY = "You cannot claim land within one chunk of another realm's claim.";

    private static final int[][] AROUND = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};

    /** The team of a claim next to this chunk (diagonals too) that is foreign to the given team, if any. */
    public static Optional<UUID> foreignNeighbour(ValorisData data, ChunkDimPos pos, UUID team) {
        var manager = FTBChunksAPI.api().getManager();
        for (int[] d : AROUND) {
            ClaimedChunk n = manager.getChunk(pos.offset(d[0], d[1]));
            if (n == null) continue;
            UUID owner = n.getTeamData().getTeam().getId();
            if (!owner.equals(team) && !sameKingdom(data, owner, team)) return Optional.of(owner);
        }
        return Optional.empty();
    }

    private static boolean sameKingdom(ValorisData data, UUID a, UUID b) {
        return data.kingdomOfCity(a).map(k -> k.cities.contains(b)).orElse(false);
    }

    /** No building in unclaimed land next to a foreign claim. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative()) return;
        ChunkDimPos pos = new ChunkDimPos(player.serverLevel(), event.getPos());
        if (FTBChunksAPI.api().getManager().getChunk(pos) != null) return; // claimed land has its own protection
        ValorisData data = ValorisData.get(player.getServer());
        UUID own = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).map(Team::getId).orElse(player.getUUID());
        for (int[] d : AROUND) {
            ClaimedChunk n = FTBChunksAPI.api().getManager().getChunk(pos.offset(d[0], d[1]));
            if (n == null) continue;
            UUID owner = n.getTeamData().getTeam().getId();
            if (owner.equals(own) || sameKingdom(data, owner, own)) continue;
            if (data.siegeOfCity(owner).isPresent() || com.valoris.war.Looting.isLooted(owner)) continue;
            event.setCanceled(true);
            player.displayClientMessage(Msg.text("You cannot build within one chunk of another realm's land.",
                    ChatFormatting.RED), true);
            return;
        }
    }
}
