package com.valoris.registry;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

/** Animal products. Registered into ModBlocks' item register. */
public final class ModAnimals {
    private ModAnimals() {}

    /** White fur from wolves; armor at the Armory Table needs it. */
    public static final DeferredItem<Item> FUR = ModBlocks.ITEMS.register("fur", () -> new Item(new Item.Properties()));

    private static DeferredItem<Item> food(String id, int nutrition, float saturation) {
        return ModBlocks.ITEMS.register(id, () -> new Item(new Item.Properties().food(
                new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build())));
    }

    // one piece of meat from every wolf and every horse (cooked if the animal was burning); cook in a furnace,
    // smoker or on a campfire. Values like vanilla: wolf as mutton, horse as beef.
    public static final DeferredItem<Item> RAW_WOLF_MEAT = food("raw_wolf_meat", 2, 0.3F);
    public static final DeferredItem<Item> COOKED_WOLF_MEAT = food("cooked_wolf_meat", 6, 0.8F);
    public static final DeferredItem<Item> RAW_HORSE_MEAT = food("raw_horse_meat", 3, 0.3F);
    public static final DeferredItem<Item> COOKED_HORSE_MEAT = food("cooked_horse_meat", 8, 0.8F);

    // bears (see mobs.Bear): 2-4 pieces of meat (cooked if burning) and 1-2 bear furs; the meat is the most filling
    public static final DeferredItem<Item> RAW_BEAR_MEAT = food("raw_bear_meat", 4, 0.3F);
    public static final DeferredItem<Item> COOKED_BEAR_MEAT = food("cooked_bear_meat", 10, 0.8F);
    public static final DeferredItem<Item> BEAR_FUR = ModBlocks.ITEMS.register("bear_fur", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> BEAR_SPAWN_EGG = ModBlocks.ITEMS.register("bear_spawn_egg",
            () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.BEAR, 0x4a2f1c, 0x9c7650, new Item.Properties()));

    /** Loads this class so its entries join ModBlocks' registers before they are registered. */
    public static void init() {}
}
