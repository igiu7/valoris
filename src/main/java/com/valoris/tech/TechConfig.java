package com.valoris.tech;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.valoris.skill.Skill;
import com.valoris.valoris2;

import net.neoforged.fml.loading.FMLPaths;

/** Loads config/valoris2/technology.json (created from the built-in default on first start). */
public final class TechConfig {
    private static final String DEFAULT_RESOURCE = "/valoris2/technology_default.json";

    private static Map<String, TechNode> nodes = new LinkedHashMap<>();
    private static Map<String, String> eraNames = new LinkedHashMap<>();
    private static List<String> alwaysLocked = List.of();
    private static List<String> disabledBlocks = List.of();
    private static List<String> cookingRestricted = List.of();

    private TechConfig() {}

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("valoris2").resolve("technology.json");
    }

    public static Map<String, TechNode> nodes() { return nodes; }
    public static TechNode node(String id) { return nodes.get(id); }
    public static Map<String, String> eraNames() { return eraNames; }
    public static List<String> alwaysLocked() { return alwaysLocked; }
    public static List<String> disabledBlocks() { return disabledBlocks; }
    public static List<String> cookingRestricted() { return cookingRestricted; }

    /** Loads the file (creating it if needed). Returns an error message, or null on success. */
    public static synchronized String load() {
        try {
            Path path = file();
            com.valoris.util.ConfigFiles.ensure(path, DEFAULT_RESOURCE);
            JsonObject root;
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            parse(root);
            TechRules.rebuild();
            valoris2.LOGGER.info("Loaded {} technologies from {}", nodes.size(), path);
            return null;
        } catch (IOException | RuntimeException e) {
            valoris2.LOGGER.error("Could not load technology.json", e);
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    private static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (o.has(key)) for (JsonElement e : o.getAsJsonArray(key)) out.add(e.getAsString());
        return out;
    }

    private static void parse(JsonObject root) {
        Map<String, String> eras = new LinkedHashMap<>();
        if (root.has("eraNames")) {
            root.getAsJsonObject("eraNames").entrySet().forEach(e -> eras.put(e.getKey(), e.getValue().getAsString()));
        }

        Map<String, TechNode> parsed = new LinkedHashMap<>();
        for (JsonElement el : root.getAsJsonArray("nodes")) {
            JsonObject o = el.getAsJsonObject();
            String id = o.get("id").getAsString();
            Map<Skill, Integer> skills = new LinkedHashMap<>();
            if (o.has("skills")) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("skills").entrySet()) {
                    if (e.getKey().equalsIgnoreCase("warrior")) continue; // removed skill: requirement dropped
                    Skill skill = Skill.byId(e.getKey())
                            .orElseThrow(() -> new IllegalArgumentException("Unknown skill '" + e.getKey() + "' in " + id));
                    skills.put(skill, e.getValue().getAsInt());
                }
            }
            parsed.put(id, new TechNode(id,
                    o.has("name") ? o.get("name").getAsString() : id,
                    o.has("era") ? o.get("era").getAsString() : "",
                    o.has("icon") ? o.get("icon").getAsString() : "minecraft:book",
                    o.has("description") ? o.get("description").getAsString() : "",
                    strings(o, "requires"), Collections.unmodifiableMap(skills),
                    o.has("starting") && o.get("starting").getAsBoolean(),
                    strings(o, "unlocks"),
                    o.has("researchMinutes") ? Math.max(0, o.get("researchMinutes").getAsInt()) : 0,
                    o.has("x") ? o.get("x").getAsInt() : -1,
                    o.has("y") ? o.get("y").getAsInt() : -1,
                    strings(o, "features"),
                    o.has("claimLimit") ? o.get("claimLimit").getAsInt() : 0));
        }
        for (TechNode n : parsed.values()) {
            for (String r : n.requires()) {
                if (!parsed.containsKey(r)) throw new IllegalArgumentException(n.id() + " requires unknown technology " + r);
            }
        }

        if (root.has("weaponBalance")) {
            JsonObject wb = root.getAsJsonObject("weaponBalance");
            Map<String, Double> mats = new HashMap<>();
            Map<String, Double> types = new HashMap<>();
            wb.getAsJsonObject("materials").entrySet().forEach(e -> mats.put(e.getKey(), e.getValue().getAsDouble()));
            wb.getAsJsonObject("types").entrySet().forEach(e -> types.put(e.getKey(), e.getValue().getAsDouble()));
            Map<String, String> perItem = new HashMap<>();
            if (wb.has("itemMaterials")) {
                wb.getAsJsonObject("itemMaterials").entrySet().forEach(e -> perItem.put(e.getKey(), e.getValue().getAsString()));
            }
            WeaponBalance.set(wb.has("referenceBase") ? wb.get("referenceBase").getAsDouble() : 3.5, mats, types, perItem);
        }

        autoLayout(parsed);
        nodes = parsed;
        eraNames = eras;
        alwaysLocked = strings(root, "alwaysLocked");
        disabledBlocks = strings(root, "disabledBlocks");
        cookingRestricted = strings(root, "cookingRestricted");
    }

    /** Nodes without x / y get a column after their deepest prerequisite and the first free row. */
    private static void autoLayout(Map<String, TechNode> parsed) {
        java.util.Set<Long> used = new java.util.HashSet<>();
        for (TechNode n : parsed.values()) if (n.x() >= 0 && n.y() >= 0) used.add(((long) n.x() << 32) | n.y());
        Map<String, Integer> depth = new HashMap<>();
        for (Map.Entry<String, TechNode> e : parsed.entrySet()) {
            TechNode n = e.getValue();
            if (n.x() >= 0 && n.y() >= 0) continue;
            int col = n.x() >= 0 ? n.x() : depth(n.id(), parsed, depth, 0);
            int row = n.y() >= 0 ? n.y() : 0;
            while (n.y() < 0 && used.contains(((long) col << 32) | row)) row++;
            used.add(((long) col << 32) | row);
            e.setValue(new TechNode(n.id(), n.name(), n.era(), n.icon(), n.description(), n.requires(), n.skills(),
                    n.starting(), n.unlocks(), n.researchMinutes(), col, row, n.features(), n.claimLimit()));
        }
    }

    private static int depth(String id, Map<String, TechNode> parsed, Map<String, Integer> memo, int guard) {
        Integer known = memo.get(id);
        if (known != null) return known;
        TechNode n = parsed.get(id);
        int d = 0;
        if (n != null && guard < 64) {
            if (n.x() >= 0) d = n.x();
            else for (String r : n.requires()) d = Math.max(d, depth(r, parsed, memo, guard + 1) + 1);
        }
        memo.put(id, d);
        return d;
    }

    /** Built-in defaults only (used on clients before the server sends anything). */
    public static void loadBuiltInWeaponBalance() {
        try (InputStream in = TechConfig.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) return;
            JsonObject root = JsonParser.parseReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject wb = root.getAsJsonObject("weaponBalance");
            Map<String, Double> mats = new HashMap<>();
            Map<String, Double> types = new HashMap<>();
            wb.getAsJsonObject("materials").entrySet().forEach(e -> mats.put(e.getKey(), e.getValue().getAsDouble()));
            wb.getAsJsonObject("types").entrySet().forEach(e -> types.put(e.getKey(), e.getValue().getAsDouble()));
            WeaponBalance.set(wb.get("referenceBase").getAsDouble(), mats, types, Map.of());
        } catch (IOException | RuntimeException e) {
            valoris2.LOGGER.warn("Could not read built-in weapon balance", e);
        }
    }

}
