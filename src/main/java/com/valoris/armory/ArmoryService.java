package com.valoris.armory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.valoris.city.Roles;
import com.valoris.data.ValorisData.City;
import com.valoris.net.ArmoryOpenPayload;
import com.valoris.registry.ModBlocks;
import com.valoris.skill.Skill;
import com.valoris.skill.SkillService;
import com.valoris.skill.XpRules;
import com.valoris.tech.TechConfig;
import com.valoris.tech.TechNode;
import com.valoris.tech.TechService;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server logic of the Armory Table: which technologies a player may use, and crafting. */
public final class ArmoryService {
    private ArmoryService() {}

    public static List<String> knownTechs(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        Optional<City> city = Roles.cityOfMember(player);
        for (TechNode node : TechConfig.nodes().values()) {
            if (TechService.isStarting(node.id())
                    || (city.isPresent() && TechService.has(city.get(), node.id()))) {
                out.add(node.id());
            }
        }
        return out;
    }

    public static void open(ServerPlayer player, BlockPos pos) {
        city(player).ifPresent(c -> TechService.update(player.getServer(), c));
        Map<String, String> names = new LinkedHashMap<>();
        TechConfig.nodes().values().forEach(n -> names.put(n.id(), n.name()));
        PacketDistributor.sendToPlayer(player, new ArmoryOpenPayload(pos, knownTechs(player), names));
    }

    private static Optional<City> city(ServerPlayer player) {
        return Roles.cityOfMember(player);
    }

    private static int count(Inventory inv, ArmoryEntry.Cost cost) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (cost.matches(s)) n += s.getCount();
        }
        return n;
    }

    private static void take(Inventory inv, ArmoryEntry.Cost cost) {
        int left = cost.count();
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!cost.matches(s)) continue;
            int t = Math.min(left, s.getCount());
            s.shrink(t);
            left -= t;
        }
    }

    public static void craft(ServerPlayer player, BlockPos pos, String entryId) {
        if (!player.level().getBlockState(pos).is(ModBlocks.ARMORY_TABLE.get())
                || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) {
            return;
        }
        ArmoryEntry entry = ArmoryCatalog.entry(entryId);
        if (entry == null) return;
        if (!knownTechs(player).contains(entry.tech())) {
            TechNode node = TechConfig.node(entry.tech());
            player.displayClientMessage(Component.literal("Your city needs the technology "
                    + (node == null ? entry.tech() : node.name()) + ".").withStyle(ChatFormatting.RED), true);
            return;
        }
        Inventory inv = player.getInventory();
        if (!player.isCreative()) {
            for (ArmoryEntry.Cost c : entry.cost()) {
                if (count(inv, c) < c.count()) {
                    player.displayClientMessage(Component.literal("You do not have enough materials.")
                            .withStyle(ChatFormatting.RED), true);
                    return;
                }
            }
            for (ArmoryEntry.Cost c : entry.cost()) take(inv, c);
        }
        ItemStack result = entry.result().copy();
        double xp = XpRules.armory(entry);
        // into the inventory; whatever does not fit is dropped at the player's feet (never lost, never doubled)
        inv.placeItemBackInInventory(result);
        player.containerMenu.broadcastChanges();
        player.level().playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (xp > 0) SkillService.addXp(player, Skill.BLACKSMITH, xp);
    }
}
