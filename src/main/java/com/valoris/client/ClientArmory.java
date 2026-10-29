package com.valoris.client;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.valoris.armory.ArmoryEntry;

/** Client copy of the armory catalog and of the technologies the player's city knows. */
public final class ClientArmory {
    private static Map<String, String> eraNames = new LinkedHashMap<>();
    private static List<ArmoryEntry> entries = List.of();
    private static Set<String> known = new HashSet<>();
    private static Map<String, String> techNames = Map.of();

    private ClientArmory() {}

    public static void setCatalog(Map<String, String> eras, List<ArmoryEntry> list) {
        eraNames = new LinkedHashMap<>(eras);
        entries = List.copyOf(list);
    }

    public static void setKnown(List<String> techs, Map<String, String> names) {
        known = new HashSet<>(techs);
        techNames = Map.copyOf(names);
    }

    public static List<ArmoryEntry> entries() { return entries; }
    public static Map<String, String> eraNames() { return eraNames; }
    public static boolean knows(String tech) { return known.contains(tech); }
    public static String techName(String tech) { return techNames.getOrDefault(tech, tech); }
}
