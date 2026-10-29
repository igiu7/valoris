package com.valoris.client.screen;

import com.valoris.furnace.FurnaceRegistry;
import com.valoris.furnace.ValorisFurnaceMenu;
import com.valoris.valoris2;

import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.recipebook.BlastingRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.SmeltingRecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Screens for the bloomery (furnace look) and the steel furnace (blast furnace look). */
@EventBusSubscriber(modid = valoris2.MODID, value = Dist.CLIENT)
public final class FurnaceScreens {
    private static final ResourceLocation FURNACE_BG = ResourceLocation.withDefaultNamespace("textures/gui/container/furnace.png");
    private static final ResourceLocation FURNACE_LIT = ResourceLocation.withDefaultNamespace("container/furnace/lit_progress");
    private static final ResourceLocation FURNACE_BURN = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");
    private static final ResourceLocation BLAST_BG = ResourceLocation.withDefaultNamespace("textures/gui/container/blast_furnace.png");
    private static final ResourceLocation BLAST_LIT = ResourceLocation.withDefaultNamespace("container/blast_furnace/lit_progress");
    private static final ResourceLocation BLAST_BURN = ResourceLocation.withDefaultNamespace("container/blast_furnace/burn_progress");

    private FurnaceScreens() {}

    public static class Bloomery extends AbstractFurnaceScreen<ValorisFurnaceMenu.Bloomery> {
        public Bloomery(ValorisFurnaceMenu.Bloomery menu, Inventory inv, Component title) {
            super(menu, new SmeltingRecipeBookComponent(), inv, title, FURNACE_BG, FURNACE_LIT, FURNACE_BURN);
        }
    }

    public static class Steel extends AbstractFurnaceScreen<ValorisFurnaceMenu.Steel> {
        public Steel(ValorisFurnaceMenu.Steel menu, Inventory inv, Component title) {
            super(menu, new BlastingRecipeBookComponent(), inv, title, BLAST_BG, BLAST_LIT, BLAST_BURN);
        }
    }

    /** The bronze furnace: the furnace screen with a second input slot (copper left, tin right). */
    public static class Bronze extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<com.valoris.furnace.BronzeFurnaceMenu> {
        public Bronze(com.valoris.furnace.BronzeFurnaceMenu menu, Inventory inv, Component title) {
            super(menu, inv, title);
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            super.render(g, mouseX, mouseY, partialTick);
            renderTooltip(g, mouseX, mouseY);
        }

        @Override
        protected void renderBg(net.minecraft.client.gui.GuiGraphics g, float partialTick, int mouseX, int mouseY) {
            int x = leftPos, y = topPos;
            g.blit(FURNACE_BG, x, y, 0, 0, imageWidth, imageHeight);
            g.blit(FURNACE_BG, x + 33, y + 16, 55, 16, 18, 18); // a copy of the input slot for the second metal
            if (menu.isLit()) {
                int h = net.minecraft.util.Mth.ceil(menu.litProgress() * 13.0F) + 1;
                g.blitSprite(FURNACE_LIT, 14, 14, 0, 14 - h, x + 56, y + 36 + 14 - h, 14, h);
            }
            int w = net.minecraft.util.Mth.ceil(menu.burnProgress() * 24.0F);
            g.blitSprite(FURNACE_BURN, 24, 16, 0, 0, x + 79, y + 34, w, 16);
        }
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(FurnaceRegistry.BLOOMERY_MENU.get(), Bloomery::new);
        event.register(FurnaceRegistry.STEEL_FURNACE_MENU.get(), Steel::new);
        event.register(FurnaceRegistry.BRONZE_FURNACE_MENU.get(), Bronze::new);
    }
}
