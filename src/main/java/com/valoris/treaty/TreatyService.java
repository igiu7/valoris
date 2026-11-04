package com.valoris.treaty;

import java.util.Base64;
import java.util.Optional;

import com.valoris.registry.ModBlocks;
import com.valoris.util.Msg;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Server side of the treaties: saving a draft as a Treaty, and signing it. */
public final class TreatyService {
    private TreatyService() {}

    /** Treaty and Quill -> Treaty with this title and text (the title becomes the item's name). */
    public static void save(ServerPlayer player, InteractionHand hand, String title, String content) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(ModBlocks.TREATY_AND_QUILL.get())) return;
        title = title.strip();
        content = content.strip();
        if (title.isEmpty() || content.isEmpty()) return;
        if (title.length() > TreatyData.TITLE_MAX) title = title.substring(0, TreatyData.TITLE_MAX);
        if (content.length() > TreatyData.CONTENT_MAX) content = content.substring(0, TreatyData.CONTENT_MAX);
        ItemStack treaty = new ItemStack(ModBlocks.TREATY.get());
        treaty.set(TreatyData.COMPONENT.get(), new TreatyData(title, content, player.getGameProfile().getName(),
                Optional.empty(), Optional.empty()));
        treaty.set(DataComponents.ITEM_NAME, Component.literal(title));
        player.setItemInHand(hand, treaty);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PUT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** A party signs: an empty place, and not the party who signed the other one. */
    public static void sign(ServerPlayer player, InteractionHand hand, int slot, String strokes) {
        ItemStack stack = player.getItemInHand(hand);
        TreatyData data = stack.get(TreatyData.COMPONENT.get());
        if (!stack.is(ModBlocks.TREATY.get()) || data == null || slot < 0 || slot > 1) return;
        if (data.signature(slot).isPresent()) return;
        String name = player.getGameProfile().getName();
        if (data.signature(1 - slot).map(s -> s.name().equals(name)).orElse(false)) {
            player.sendSystemMessage(Msg.text("You already signed this treaty for the other party.", ChatFormatting.RED));
            return;
        }
        if (!valid(strokes)) return;
        stack.set(TreatyData.COMPONENT.get(), data.withSignature(slot, new TreatyData.Signature(name, strokes)));
        player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Strokes the client may send: points inside the canvas, not too many, at least one. */
    private static boolean valid(String strokes) {
        byte[] b;
        try {
            b = Base64.getDecoder().decode(strokes);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (b.length < 2 || b.length % 2 != 0 || b.length > 2 * (TreatyData.POINTS_MAX + 200)) return false;
        boolean any = false;
        for (int i = 0; i < b.length; i += 2) {
            int x = b[i] & 255, y = b[i + 1] & 255;
            if (x == 255 && y == 255) continue;
            if (x >= TreatyData.CANVAS_W || y >= TreatyData.CANVAS_H) return false;
            any = true;
        }
        return any;
    }
}
