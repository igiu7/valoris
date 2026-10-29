package com.valoris.registry;

import com.valoris.valoris2;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One creative tab with every item of the mod. */
public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, valoris2.MODID);

    static {
        TABS.register("valoris", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.valoris2"))
                .icon(() -> new ItemStack(ModBlocks.CITY_BANNER_ITEM.get()))
                .displayItems((params, output) -> ModBlocks.ITEMS.getEntries().forEach(e -> output.accept(e.get())))
                .build());
    }

    private ModTabs() {}

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
