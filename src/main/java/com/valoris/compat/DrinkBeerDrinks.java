package com.valoris.compat;

import com.valoris.registry.ModBlocks;

import lekavar.lma.drinkbeer.blocks.BeerMugBlock;
import lekavar.lma.drinkbeer.items.BeerMugItem;
import lekavar.lma.drinkbeer.utils.beer.BeerDefinition;
import lekavar.lma.drinkbeer.utils.beer.BeerSpecialAction;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Sake (a porcelain tokkuri with a cup) and vodka (a glazed clay jug) as drinks of DrinkBeer Refill: brewed in its
 * beer barrel into empty beer mugs (data/valoris2/recipe/sake.json, vodka.json), drunk like its beers (Drunk, the
 * empty mug back, Speed I for 1:30) and set on a table up to three at a time. Only loaded with DrinkBeer
 * installed (valoris2 constructor).
 */
public final class DrinkBeerDrinks {
    private DrinkBeerDrinks() {}

    /** Ids of DrinkBeer's own beers are 1-9. */
    private static final int SAKE_ID = 101, VODKA_ID = 102;

    public static DeferredBlock<Block> SAKE_BLOCK, VODKA_BLOCK;
    public static DeferredItem<Item> SAKE, VODKA;

    private static BeerDefinition speed(int id) {
        return new BeerDefinition(id, 1, () -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1800), true, BeerSpecialAction.NONE);
    }

    /** Adds the drinks to ModBlocks' registers (before they are registered). */
    public static void init() {
        SAKE_BLOCK = ModBlocks.BLOCKS.register("sake", BeerMugBlock::new);
        VODKA_BLOCK = ModBlocks.BLOCKS.register("vodka", BeerMugBlock::new);
        SAKE = ModBlocks.ITEMS.register("sake", () -> new BeerMugItem(SAKE_BLOCK.get(), speed(SAKE_ID)));
        VODKA = ModBlocks.ITEMS.register("vodka", () -> new BeerMugItem(VODKA_BLOCK.get(), speed(VODKA_ID)));
    }
}
