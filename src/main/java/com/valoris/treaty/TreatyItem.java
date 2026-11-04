package com.valoris.treaty;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Treaty and Quill (draft = true): right click writes the title and the text, Save makes it a Treaty. Treaty: right
 * click reads it; each of the two parties signs it by hand, and the signed treaty is exported as a PNG
 * (client.TreatyScreen).
 */
public class TreatyItem extends Item {
    private final boolean draft;

    public TreatyItem(boolean draft, Properties properties) {
        super(properties);
        this.draft = draft;
    }

    public boolean draft() {
        return draft;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide && FMLEnvironment.dist.isClient()) com.valoris.client.TreatyScreen.open(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        TreatyData data = stack.get(TreatyData.COMPONENT.get());
        if (draft || data == null) return;
        if (!data.author().isEmpty()) tooltip.add(Component.literal("Written by " + data.author()).withStyle(ChatFormatting.GRAY));
        for (int slot = 0; slot < 2; slot++) {
            String party = slot == 0 ? "First party: " : "Second party: ";
            tooltip.add(Component.literal(party + data.signature(slot).map(TreatyData.Signature::name).orElse("not signed"))
                    .withStyle(data.signature(slot).isPresent() ? ChatFormatting.DARK_GREEN : ChatFormatting.GRAY));
        }
    }
}
