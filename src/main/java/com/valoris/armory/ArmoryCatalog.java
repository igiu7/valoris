package com.valoris.armory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.valoris.valoris2;

import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

/** Server side: loads config/valoris2/armory.json (created from the built-in default on first start). */
public final class ArmoryCatalog {
    private static final String DEFAULT_RESOURCE = "/valoris2/armory_default.json";

    private static List<ArmoryEntry> entries = List.of();
    private static Map<String, ArmoryEntry> byId = Map.of();
    private static Map<String, String> eraNames = Map.of();
    private static List<String> removeRecipesFrom = List.of();
    private static List<String> keepRecipes = List.of();
    private static List<String> removeAllRecipesFrom = List.of();
    private static List<String> keepRecipeIds = List.of();
    private static Map<String, double[]> armorBalance = Map.of();

    private ArmoryCatalog() {}

    public static List<ArmoryEntry> entries() { return entries; }
    public static ArmoryEntry entry(String id) { return byId.get(id); }
    public static Map<String, String> eraNames() { return eraNames; }
    public static List<String> removeRecipesFrom() { return removeRecipesFrom; }
    public static List<String> keepRecipes() { return keepRecipes; }
    public static List<String> removeAllRecipesFrom() { return removeAllRecipesFrom; }
    public static List<String> keepRecipeIds() { return keepRecipeIds; }

    public static Map<String, double[]> armorBalance() { return armorBalance; }

    private static double num(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsDouble() : 0;
    }

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("valoris2").resolve("armory.json");
    }

    /** Needs the server's registries (item components). Returns an error message or null. */
    public static synchronized String load(MinecraftServer server) {
        try {
            Path path = file();
            com.valoris.util.ConfigFiles.ensure(path, DEFAULT_RESOURCE);
            JsonObject root;
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
            List<ArmoryEntry> list = new ArrayList<>();
            Map<String, ArmoryEntry> map = new LinkedHashMap<>();
            int missing = 0;
            for (JsonElement el : root.getAsJsonArray("entries")) {
                JsonObject o = el.getAsJsonObject();
                ItemStack result = ItemStack.CODEC.parse(ops, o.get("result")).result().orElse(ItemStack.EMPTY);
                if (result.isEmpty()) { missing++; continue; } // mod not installed or bad id
                List<ArmoryEntry.Cost> cost = new ArrayList<>();
                for (JsonElement c : o.getAsJsonArray("cost")) {
                    JsonObject co = c.getAsJsonObject();
                    boolean tag = co.has("tag");
                    cost.add(new ArmoryEntry.Cost(co.get(tag ? "tag" : "item").getAsString(), tag, co.get("count").getAsInt()));
                }
                ArmoryEntry entry = new ArmoryEntry(o.get("id").getAsString(), o.get("category").getAsString(),
                        o.get("era").getAsString(), o.get("tech").getAsString(), result, List.copyOf(cost));
                if (map.putIfAbsent(entry.id(), entry) == null) list.add(entry);
            }
            Map<String, String> eras = new LinkedHashMap<>();
            if (root.has("eraNames")) {
                root.getAsJsonObject("eraNames").entrySet().forEach(e -> eras.put(e.getKey(), e.getValue().getAsString()));
            }
            List<String> remove = new ArrayList<>();
            if (root.has("removeRecipesFrom")) root.getAsJsonArray("removeRecipesFrom").forEach(e -> remove.add(e.getAsString()));
            List<String> keep = new ArrayList<>();
            if (root.has("keepRecipes")) root.getAsJsonArray("keepRecipes").forEach(e -> keep.add(e.getAsString()));
            List<String> removeAll = new ArrayList<>();
            if (root.has("removeAllRecipesFrom")) root.getAsJsonArray("removeAllRecipesFrom").forEach(e -> removeAll.add(e.getAsString()));
            List<String> keepIds = new ArrayList<>();
            if (root.has("keepRecipeIds")) root.getAsJsonArray("keepRecipeIds").forEach(e -> keepIds.add(e.getAsString()));

            Map<String, double[]> armor = new LinkedHashMap<>();
            if (root.has("armorBalance")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("armorBalance").entrySet()) {
                    if (e.getKey().startsWith("_") || !e.getValue().isJsonObject()) continue;
                    JsonObject v = e.getValue().getAsJsonObject();
                    armor.put(e.getKey(), new double[]{num(v, "helmet"), num(v, "chestplate"), num(v, "leggings"),
                            num(v, "boots"), num(v, "toughness"), num(v, "knockbackResistance"), num(v, "horse")});
                }
            }

            entries = List.copyOf(list);
            armorBalance = armor;
            com.valoris.tech.ArmorBalance.set(armor, entries);
            byId = map;
            eraNames = eras;
            removeRecipesFrom = remove;
            keepRecipes = keep;
            removeAllRecipesFrom = removeAll;
            keepRecipeIds = keepIds;
            valoris2.LOGGER.info("Armory: {} entries loaded, {} skipped (item not found)", list.size(), missing);
            return null;
        } catch (IOException | RuntimeException e) {
            valoris2.LOGGER.error("Could not load armory.json", e);
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }
}
