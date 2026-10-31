package com.valoris.furnace;

import java.util.ArrayList;
import java.util.List;

import com.valoris.tech.TechConfig;
import com.valoris.valoris2;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Removes every smelting / blasting / smoking / campfire recipe that produces a restricted item
 * (iron, steel by default). Create's fan processing uses the same recipes, so it is covered too.
 * Iron and steel then come only from the bloomery and the steel furnace.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public class CookingRestrictions {

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        com.valoris.armory.ArmoryCatalog.load(event.getServer());
        apply(event.getServer());
    }

    /** Recipes for gear of these mods are removed: it is made only at the Armory Table. */
    private static boolean armoryRecipe(ResourceLocation recipe, ItemStack result) {
        if (result.isEmpty()) return false;
        for (String keep : com.valoris.armory.ArmoryCatalog.keepRecipeIds()) {
            if (recipe.toString().startsWith(keep)) return false;
        }
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(result.getItem());
        // mods whose content is removed except what the Armory makes: no recipe at all, ingots included
        if (com.valoris.armory.ArmoryCatalog.removeAllRecipesFrom().contains(id.getNamespace())) return true;
        if (!com.valoris.armory.ArmoryCatalog.removeRecipesFrom().contains(id.getNamespace())) return false;
        for (String keep : com.valoris.armory.ArmoryCatalog.keepRecipes()) {
            if (id.getPath().contains(keep)) return false;
        }
        return true;
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) apply(server);
    }

    private static boolean restricted(ItemStack result) {
        if (result.isEmpty()) return false;
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
        for (String rule : TechConfig.cookingRestricted()) {
            if (rule.startsWith("#")) {
                TagKey<net.minecraft.world.item.Item> tag =
                        TagKey.create(Registries.ITEM, ResourceLocation.parse(rule.substring(1)));
                if (result.is(tag)) return true;
            } else if (rule.equals(id)) {
                return true;
            }
        }
        return false;
    }

    public static void apply(MinecraftServer server) {
        if (TechConfig.nodes().isEmpty()) TechConfig.load();
        RecipeManager manager = server.getRecipeManager();
        List<RecipeHolder<?>> kept = new ArrayList<>();
        int removed = 0;
        int armory = 0;
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            RecipeType<?> type = holder.value().getType();
            ItemStack result;
            try {
                result = holder.value().getResultItem(server.registryAccess());
            } catch (RuntimeException e) {
                kept.add(holder);
                continue;
            }
            boolean cooking = type == RecipeType.SMELTING || type == RecipeType.BLASTING
                    || type == RecipeType.SMOKING || type == RecipeType.CAMPFIRE_COOKING;
            if (cooking && restricted(result)) {
                removed++;
                continue;
            }
            if (armoryRecipe(holder.id(), result)) {
                armory++;
                continue;
            }
            kept.add(holder);
        }
        if (removed > 0 || armory > 0) {
            manager.replaceRecipes(kept);
            valoris2.LOGGER.info("Removed {} cooking recipes for iron/steel and {} recipes of armory mods", removed, armory);
        }
    }
}
