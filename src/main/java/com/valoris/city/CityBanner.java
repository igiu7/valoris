package com.valoris.city;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import com.valoris.registry.ModBlocks;

public final class CityBanner {
    private static final String TAG = "valoris_city_banner";

    private CityBanner() {}

    public static ItemStack create() {
        ItemStack stack = new ItemStack(ModBlocks.CITY_BANNER_ITEM.get());
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("City Banner").withStyle(ChatFormatting.GOLD).withStyle(s -> s.withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Place the sword in the stone to found your city.").withStyle(ChatFormatting.GRAY),
                Component.literal("It cannot be dropped.").withStyle(ChatFormatting.DARK_GRAY))));
        return stack;
    }

    public static boolean isCityBanner(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(ModBlocks.CITY_BANNER_ITEM.get())) return true;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(TAG);
    }

    public static boolean hasBanner(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isCityBanner(inv.getItem(i))) return true;
        }
        return isCityBanner(player.containerMenu.getCarried());
    }

    public static void giveIfMissing(ServerPlayer player) {
        if (!hasBanner(player)) {
            player.getInventory().add(create());
            player.containerMenu.broadcastChanges();
        }
    }

    public static void removeAll(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isCityBanner(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
        }
        if (isCityBanner(player.containerMenu.getCarried())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        player.containerMenu.broadcastChanges();
    }
}
