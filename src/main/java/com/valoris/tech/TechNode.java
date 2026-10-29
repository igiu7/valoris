package com.valoris.tech;

import java.util.List;
import java.util.Map;

import com.valoris.skill.Skill;

/**
 * One technology of the tree, as read from config/valoris2/technology.json.
 * x / y: column and row in the tree screen. researchMinutes: real time the research takes.
 * features: human readable lines shown under "Unlocks". claimLimit: chunks the city may claim (0 = no change).
 */
public record TechNode(String id, String name, String era, String icon, String description,
                       List<String> requires, Map<Skill, Integer> skills, boolean starting, List<String> unlocks,
                       int researchMinutes, int x, int y, List<String> features, int claimLimit) {}
