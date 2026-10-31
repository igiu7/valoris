package com.valoris;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.valoris.city.ClaimLimitHook;
import com.valoris.events.TeamHooks;
import com.valoris.net.ValorisNet;
import com.valoris.furnace.FurnaceRegistry;
import com.valoris.registry.ModBlocks;
import com.valoris.skill.SkillAttachments;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(valoris2.MODID)
public class valoris2 {
    public static final String MODID = "valoris2";
    public static final Logger LOGGER = LogUtils.getLogger();

    public valoris2(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ValorisNet::register);
        SkillAttachments.register(modEventBus);
        FurnaceRegistry.register(modEventBus); // also adds its blocks/items to ModBlocks
        com.valoris.registry.ModPlants.init();      // herbs, flax and healing items join ModBlocks' registers
        com.valoris.registry.ModAnimals.init();     // fur
        com.valoris.registry.ModArmor.register(modEventBus); // wolf and bear fur armor
        com.valoris.registry.JapaneseArmory.register(modEventBus); // samurai armor, katanas... (Japanese Armory port)
        com.valoris.registry.ModOres.init();        // overworld quartz ore
        com.valoris.registry.ModTools.init();       // steel tools
        if (net.neoforged.fml.ModList.get().isLoaded("drinkbeer")) com.valoris.compat.DrinkBeerDrinks.init(); // sake, vodka
        ModBlocks.register(modEventBus);
        com.valoris.registry.ModEntities.register(modEventBus); // bear
        com.valoris.worldgen.ModWorldgen.register(modEventBus); // Valoris map world type
        com.valoris.registry.ModTabs.register(modEventBus);
        com.valoris.rules.NoExperienceBottles.register(modEventBus); // loot modifier removing bottles o' enchanting
        if (FMLEnvironment.dist.isClient()) com.valoris.client.FtbSidebar.init();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Architectury events used by FTB Chunks
        event.enqueueWork(() -> {
            ClaimLimitHook.init();
            TeamHooks.init();
        });
    }
}
