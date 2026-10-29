package com.valoris.skill;

/**
 * Levels 1..30. XP needed to go from level L to L+1 is round(50 * L^1.5) + 50:
 * level 2 needs 100 XP, level 10 about 6,000 XP in total, level 20 about 34,500 and level 30 about 96,000.
 * The XP rules give roughly 4,000 XP per hour of focused work, so a player who sticks to one profession
 * reaches level 10 in the first evening, level 20 after about a week and level 30 after about two weeks.
 */
public final class SkillCurve {
    public static final int MAX_LEVEL = 30;
    /** TOTAL[L] = total XP needed to reach level L (TOTAL[1] = 0). */
    private static final long[] TOTAL = new long[MAX_LEVEL + 1];

    static {
        long sum = 0;
        TOTAL[1] = 0;
        for (int level = 1; level < MAX_LEVEL; level++) {
            sum += needFor(level);
            TOTAL[level + 1] = sum;
        }
    }

    private SkillCurve() {}

    /** XP needed to advance from this level to the next one. */
    public static long needFor(int level) {
        return Math.round(50 * Math.pow(level, 1.5)) + 50;
    }

    public static long totalFor(int level) {
        return TOTAL[Math.max(1, Math.min(MAX_LEVEL, level))];
    }

    public static int levelFor(double xp) {
        int level = 1;
        while (level < MAX_LEVEL && xp >= TOTAL[level + 1]) level++;
        return level;
    }

    /** XP gathered inside the current level (0 at max level). */
    public static long progressInLevel(double xp) {
        int level = levelFor(xp);
        if (level >= MAX_LEVEL) return 0;
        return (long) Math.floor(xp - TOTAL[level]);
    }
}
