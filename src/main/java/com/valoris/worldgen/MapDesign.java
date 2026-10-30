package com.valoris.worldgen;

import java.util.Random;

/**
 * The Valoris map: 4000x4000 blocks (x and z from -2000 to 2000) in an endless ocean.
 *
 * Every land mass has a profile that decides its biomes, so the soil tiers (com.valoris.farming.SoilQuality)
 * belong to whole regions and players of different regions have to trade:
 * - the heart continent, the biggest, in the middle: tier 1-2 only (plains, forests; its mountains are meadow
 *   and cherry grove);
 * - the south-west continent: tier 2-3 (forests in its north, savanna, jungle and swamps in its south);
 * - the north-east continent: tier 3-4 (taiga and windswept hills in its south, snow and ice in its north);
 * - islands around them, each of one kind: icy, taiga, green, cherry, swamp, jungle, desert (a big one in the south).
 * Within that, the north is colder and the south warmer.
 *
 * Pure Java on purpose (no Minecraft classes): the same code makes the terrain in game (MapHeightFunction,
 * MapBiomeSource) and the preview image outside the game. The map is fixed: it does not depend on the world seed
 * (caves and ores still do).
 */
public final class MapDesign {
    private MapDesign() {}

    public static final int HALF = 2000;
    public static final int SEA_LEVEL = 63;
    public static final int MAX_HEIGHT = 312;

    // ------------------------------------------------------------------ noise

    /** Classic 2D Perlin noise, roughly -1..1. */
    static final class Perlin {
        private final int[] p = new int[512];

        Perlin(long seed) {
            int[] perm = new int[256];
            for (int i = 0; i < 256; i++) perm[i] = i;
            Random r = new Random(seed);
            for (int i = 255; i > 0; i--) {
                int j = r.nextInt(i + 1);
                int t = perm[i]; perm[i] = perm[j]; perm[j] = t;
            }
            for (int i = 0; i < 512; i++) p[i] = perm[i & 255];
        }

        double noise(double x, double y) {
            int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
            double xf = x - xi, yf = y - yi;
            int X = xi & 255, Y = yi & 255;
            double u = fade(xf), v = fade(yf);
            int aa = p[p[X] + Y], ab = p[p[X] + Y + 1], ba = p[p[X + 1] + Y], bb = p[p[X + 1] + Y + 1];
            double x1 = lerp(u, grad(aa, xf, yf), grad(ba, xf - 1, yf));
            double x2 = lerp(u, grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1));
            return lerp(v, x1, x2);
        }

        private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }

        private static double grad(int h, double x, double y) {
            return switch (h & 7) {
                case 0 -> x + y;
                case 1 -> -x + y;
                case 2 -> x - y;
                case 3 -> -x - y;
                case 4 -> x;
                case 5 -> -x;
                case 6 -> y;
                default -> -y;
            };
        }

        /** Fractal noise, roughly -1..1. */
        double fbm(double x, double z, double scale, int octaves) {
            double sum = 0, amp = 1, norm = 0, f = 1.0 / scale;
            for (int i = 0; i < octaves; i++) {
                sum += noise(x * f + i * 31.7, z * f - i * 17.3) * amp;
                norm += amp;
                amp *= 0.5;
                f *= 2.0;
            }
            return sum / norm;
        }
    }

    private static final long SEED = 0x5641_4C4F_5249_53L; // "VALORIS"
    private static final Perlin WARP_X = new Perlin(SEED + 1), WARP_Z = new Perlin(SEED + 2);
    private static final Perlin COAST = new Perlin(SEED + 3), HILLS = new Perlin(SEED + 4);
    private static final Perlin RIDGE = new Perlin(SEED + 5), RANGE = new Perlin(SEED + 6);
    private static final Perlin RIVER = new Perlin(SEED + 7), FLOOR = new Perlin(SEED + 8);
    private static final Perlin LAT = new Perlin(SEED + 9), MOIST = new Perlin(SEED + 10), DETAIL = new Perlin(SEED + 11);

    static double clamp(double v, double lo, double hi) { return v < lo ? lo : v > hi ? hi : v; }
    static double lerp(double t, double a, double b) { return a + t * (b - a); }
    static double smooth(double e0, double e1, double v) {
        double t = clamp((v - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    // ------------------------------------------------------------------ land masses

    /** What grows on a land mass (see biome()) and the soil tiers it may have (min..max). */
    public enum Profile {
        HEART(1, 2),     // plains and forests, meadow and cherry grove on the mountains
        WARM(2, 3),      // forests in the north, savanna, jungle and swamp in the south
        COLD(3, 4),      // taiga and windswept hills in the south, snow and ice in the north
        ICE(4, 4),       // island: snowy plains, ice spikes, snowy taiga
        TAIGA(3, 3),     // island
        DESERT(4, 4),    // island: desert and badlands
        JUNGLE(3, 3),    // island
        GREEN(1, 2),     // island: plains and flower forest
        SWAMP(3, 3),     // island
        CHERRY(2, 2);    // island: cherry grove and meadow

        public final int minTier, maxTier;

        Profile(int minTier, int maxTier) {
            this.minTier = minTier;
            this.maxTier = maxTier;
        }
    }

    /**
     * The soil tiers allowed at a column: those of its land mass (for the sea, of the nearest one). Beaches,
     * rivers and the sea floor would otherwise bring other tiers (and their ores) into a region.
     */
    public static Profile regionAt(int x, int z) {
        return MASSES[sample(x, z).continent].profile;
    }

    /** A land mass: its profile and how tall its mountain ranges grow. */
    record Mass(Profile profile, double peak) {}

    public static final int HEART = 0, WARM = 1, COLD = 2;

    private static final Mass[] MASSES = {
            new Mass(Profile.HEART, 160),   // 0 heart continent, in the middle
            new Mass(Profile.WARM, 120),    // 1 south-east continent
            new Mass(Profile.COLD, 140),    // 2 north and north-west continent
            // the north-east archipelago
            new Mass(Profile.ICE, 0),       // 3
            new Mass(Profile.ICE, 0),       // 4
            new Mass(Profile.ICE, 0),       // 5
            new Mass(Profile.TAIGA, 0),     // 6
            new Mass(Profile.TAIGA, 0),     // 7
            new Mass(Profile.GREEN, 0),     // 8
            new Mass(Profile.CHERRY, 0),    // 9
            new Mass(Profile.TAIGA, 0),     // 10
            // a few in the west and south-west
            new Mass(Profile.GREEN, 0),     // 11
            new Mass(Profile.DESERT, 0),    // 12
            new Mass(Profile.JUNGLE, 0),    // 13
            new Mass(Profile.SWAMP, 0),     // 14
    };

    public static Profile profile(int mass) {
        return MASSES[mass].profile;
    }

    /** An elliptic piece of a land mass, turned by angle (degrees). */
    record Blob(int mass, double cx, double cz, double rx, double rz, double angle) {
        double field(double x, double z) {
            double a = Math.toRadians(angle), c = Math.cos(a), s = Math.sin(a);
            double dx = x - cx, dz = z - cz;
            double u = dx * c + dz * s, v = -dx * s + dz * c;
            return 1.0 - Math.sqrt((u / rx) * (u / rx) + (v / rz) * (v / rz));
        }
    }

    // every land mass is several overlapping lobes (they melt together) plus capes; BAYS cut gulfs into the coast
    private static final Blob[] LAND = {
            // heart: a long diagonal body (south-west to north-east), an arm to the north-east, lobes south-west and south
            new Blob(0, -300, 0, 854, 659, -35),
            new Blob(0, -994, 448, 427, 317, -10),
            new Blob(0, 327, -448, 488, 293, -40),
            new Blob(0, -54, 582, 390, 281, 20),
            new Blob(0, -916, -336, 281, 122, 30),
            // cold: a wide band along the north coast with a tail down the west coast and a cape to the east
            new Blob(2, -1100, -1350, 500, 300, 10),
            new Blob(2, -450, -1550, 380, 200, -5),
            new Blob(2, -1550, -900, 260, 330, 15),
            new Blob(2, -1600, -400, 150, 260, 0),
            new Blob(2, 100, -1650, 260, 110, -10),
            // warm: one wide, massive horseshoe in the south-east, open to the south around a big gulf
            new Blob(1, 1000, 1100, 620, 430, -10),
            new Blob(1, 1430, 640, 320, 290, 0),
            new Blob(1, 480, 1330, 320, 280, 15),
            new Blob(1, 1380, 1520, 300, 220, 0),
            new Blob(1, 560, 1650, 220, 150, -10),
            // north-east archipelago
            new Blob(3, 1100, -1550, 200, 140, 20),
            new Blob(3, 1250, -1480, 110, 60, -30),
            new Blob(4, 1600, -1300, 150, 120, 0),
            new Blob(5, 700, -1150, 120, 90, -20),
            new Blob(6, 1350, -850, 180, 120, 30),
            new Blob(7, 980, -760, 120, 100, 0),
            new Blob(8, 1050, -250, 170, 120, 10),
            new Blob(8, 1180, -180, 90, 45, -25),
            new Blob(9, 650, 250, 150, 110, 0),
            new Blob(10, 1700, -650, 110, 80, 0),
            // west and south-west
            new Blob(11, -1640, 700, 170, 120, 0),
            // the big desert island along the south coast, and a jungle island west of it
            new Blob(12, -550, 1590, 480, 220, 5),
            new Blob(12, -980, 1470, 230, 150, 20),
            new Blob(12, -180, 1520, 180, 120, -25),
            new Blob(13, -1400, 1220, 200, 150, 10),
            new Blob(14, -140, 980, 120, 100, 0),
    };

    /** How strongly lobes melt together: lower = rounder joints. */
    private static final double SOFT = 7.0;

    /** Width of the beach strip, in units of the land field (about 25-40 blocks on a continent). */
    private static final double BEACH = 0.06;

    private static final Blob[] BAYS = {
            new Blob(0, -591, 627, 160, 125, 0),
            new Blob(2, -800, -1130, 160, 110, 0),
            new Blob(1, 960, 1700, 230, 260, 0),
            new Blob(12, -620, 1640, 110, 70, 0),
    };

    /** Mountain ranges: polylines along which a continuous crest runs (see ranges()). */
    record Chain(int mass, double[] points) {}

    private static final Chain[] CHAINS = {
            new Chain(0, new double[] {-972, 470, -412, 67, 148, -280, 462, -538}),
            new Chain(0, new double[] {-412, 67, -132, 538}),
            new Chain(1, new double[] {460, 1480, 900, 1080, 1450, 720}),
            new Chain(2, new double[] {-1580, -500, -1350, -1100, -750, -1450, -100, -1620}),
    };

    // ------------------------------------------------------------------ mountains

    private static double warpX(double x, double z) {
        return x + 190 * WARP_X.fbm(x, z, 700, 3) + 55 * WARP_X.fbm(x + 999, z, 170, 2);
    }

    private static double warpZ(double x, double z) {
        return z + 190 * WARP_Z.fbm(x, z, 700, 3) + 55 * WARP_Z.fbm(x, z + 999, 170, 2);
    }

    /**
     * Mountain ranges: one continuous crest along every chain. Along the crest the height rises to sharp summits
     * and drops to saddles (never below half), with an occasional deeper pass; the free ends of a chain taper off.
     * Across the crest the slope is steep and concave, and the foot is narrow: 1/2.3 of the local crest height to
     * each side. Returns {height above the land, how close to the crest 0..1}.
     */
    private static double[] ranges(double wx, double wz, int mass) {
        double tallest = MASSES[mass].peak;
        if (tallest <= 0) return new double[] {0, 0};
        // spurs and gullies: the slopes are not smooth
        double jag = 9 * DETAIL.fbm(wx, wz, 32, 2);
        double best = 0, crestness = 0;
        for (int ci = 0; ci < CHAINS.length; ci++) {
            if (CHAINS[ci].mass != mass) continue;
            double[] p = CHAINS[ci].points;
            double d = Double.MAX_VALUE, along = 0, run = 0;
            for (int i = 0; i + 3 < p.length; i += 2) {
                double ax = p[i], az = p[i + 1], vx = p[i + 2] - ax, vz = p[i + 3] - az;
                double len = Math.sqrt(vx * vx + vz * vz);
                double t = clamp(((wx - ax) * vx + (wz - az) * vz) / (len * len), 0, 1);
                double dd = Math.hypot(wx - (ax + t * vx), wz - (az + t * vz));
                if (dd < d) { d = dd; along = run + t * len; }
                run += len;
            }
            if (d > tallest) continue; // too far from this chain to matter
            double summits = Math.pow(1 - Math.abs(RIDGE.noise(along / 70.0, ci * 7.31)), 2.5);
            double pass = clamp(0.85 + 0.7 * RANGE.noise(along / 280.0, ci * 3.17), 0.5, 1);
            double ends = smooth(0, 160, along) * smooth(0, 160, run - along);
            double crest = tallest * (0.5 + 0.5 * summits) * pass * ends;
            if (crest < 2) continue;
            double halfWidth = crest / 2.3;
            double dj = d + jag;
            if (dj >= halfWidth) continue;
            double k = 1 - Math.max(0, dj) / halfWidth;
            best = Math.max(best, crest * Math.pow(k, 1.25));
            crestness = Math.max(crestness, k);
        }
        return new double[] {best, crestness};
    }

    // ------------------------------------------------------------------ one column

    /** Everything known about one column of the map; continent is the land mass (index into MASSES). */
    public record Sample(int height, double exact, double land, int continent, double mountain, boolean river, double lat, double moist) {}

    private static final int CACHE = 4096;
    private static final ThreadLocal<long[]> CACHE_KEYS = ThreadLocal.withInitial(() -> {
        long[] k = new long[CACHE];
        java.util.Arrays.fill(k, Long.MIN_VALUE);
        return k;
    });
    private static final ThreadLocal<Sample[]> CACHE_VALUES = ThreadLocal.withInitial(() -> new Sample[CACHE]);

    public static Sample sample(int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        int slot = (int) ((x * 73856093L ^ z * 19349663L) & (CACHE - 1));
        long[] keys = CACHE_KEYS.get();
        Sample[] values = CACHE_VALUES.get();
        if (keys[slot] == key) return values[slot];
        Sample s = compute(x, z);
        keys[slot] = key;
        values[slot] = s;
        return s;
    }

    public static int height(int x, int z) {
        return sample(x, z).height;
    }

    /** The surface height before rounding (smoother terrain in game). */
    public static double exactHeight(int x, int z) {
        return sample(x, z).exact;
    }

    private static Sample compute(double x, double z) {
        // warp the coordinates so coasts and ranges are not geometric
        double wx = warpX(x, z);
        double wz = warpZ(x, z);

        // the lobes of one land mass melt together (soft maximum), so it stays one piece of land
        double[] sum = new double[MASSES.length];
        for (Blob b : LAND) sum[b.mass] += Math.exp(SOFT * b.field(wx, wz));
        double best = -10;
        int mass = HEART;
        for (int k = 0; k < sum.length; k++) {
            if (sum[k] <= 0) continue;
            double f = Math.log(sum[k]) / SOFT;
            if (f > best) { best = f; mass = k; }
        }
        double bay = 0;
        for (Blob b : BAYS) bay = Math.max(bay, b.field(wx, wz));
        // ragged coasts: capes, coves and small headlands at several sizes
        double land = best - 1.4 * bay + 0.17 * COAST.fbm(x, z, 260, 5) + 0.06 * COAST.fbm(x + 500, z - 500, 70, 3);
        // nothing near the edge of the map: open ocean beyond
        double edge = Math.max(Math.abs(x), Math.abs(z));
        if (edge > 1830) land -= (edge - 1830) / 100.0;

        // climate: -1 far north (cold) .. 1 far south (warm)
        double lat = clamp(z / 1800.0 + 0.08 * LAT.fbm(x, z, 600, 3), -1, 1);
        double moist = clamp(1.4 * MOIST.fbm(x, z, 420, 4), -1, 1);

        double h;
        double mountain = 0;
        boolean river = false;
        if (land < 0) {
            // sea floor: shelf near the coast, deep ocean further out
            double t = smooth(0, 0.3, -land);
            h = 62 - 30 * t + 4 * t * FLOOR.fbm(x, z, 90, 3);
        } else {
            h = 63.5 + 26 * Math.pow(Math.min(land, 1.2), 1.4);
            double coastFade = smooth(0, 0.08, land);
            h += (HILLS.fbm(x, z, 260, 5) * (5 + 18 * land) + 2 * DETAIL.fbm(x, z, 40, 2)) * coastFade;

            // mountains: continuous steep ranges along the chains
            double landFade = smooth(0.04, 0.2, land);
            if (landFade > 0) {
                double[] r = ranges(wx, wz, mass);
                mountain = r[1] * landFade;
                double rise = r[0] * landFade;
                if (rise > 0) h += rise + 2.5 * DETAIL.fbm(wx, wz, 14, 2) * smooth(0, 20, rise); // rough rock faces
            }

            // beaches: a low strip along the coast (except under mountains, which end in cliffs)
            if (land < BEACH && mountain < 0.15) h = Math.min(h, 63.6 + 60 * land);

            // rivers: winding lines where the noise crosses zero, not through high mountains
            double rn = Math.abs(RIVER.fbm(x, z, 900, 4));
            double strength = (1 - smooth(0.0, 0.028, rn)) * (1 - smooth(0.15, 0.4, mountain)) * smooth(0.03, 0.1, land);
            if (strength > 0) {
                h = lerp(strength, h, 58);
                river = strength > 0.55 && h < SEA_LEVEL;
            }
        }
        double exact = clamp(h, 8, MAX_HEIGHT);
        return new Sample((int) Math.round(exact), exact, land, mass, mountain, river, lat, moist);
    }

    // ------------------------------------------------------------------ biomes

    /** Every biome the map can place. */
    public static final String[] BIOMES = {
            "frozen_river", "river", "deep_frozen_ocean", "frozen_ocean", "deep_cold_ocean", "cold_ocean",
            "deep_ocean", "ocean", "deep_lukewarm_ocean", "lukewarm_ocean", "warm_ocean",
            "snowy_beach", "beach", "stony_shore",
            "frozen_peaks", "jagged_peaks", "snowy_slopes", "grove", "meadow", "cherry_grove",
            "windswept_hills", "windswept_forest", "windswept_savanna", "savanna_plateau",
            "badlands", "eroded_badlands", "wooded_badlands",
            "ice_spikes", "snowy_taiga", "snowy_plains", "old_growth_spruce_taiga", "old_growth_pine_taiga", "taiga",
            "plains", "sunflower_plains", "forest", "birch_forest", "flower_forest", "dark_forest",
            "savanna", "jungle", "sparse_jungle", "bamboo_jungle", "desert",
            "swamp", "mangrove_swamp",
            "valoris2:beach_t1", "valoris2:beach_t2", "valoris2:beach_t3", "valoris2:beach_t4",
            "valoris2:river_t1", "valoris2:river_t2", "valoris2:river_t3", "valoris2:river_t4",
            "valoris2:jagged_peaks_t1", "valoris2:jagged_peaks_t2", "valoris2:jagged_peaks_t3", "valoris2:jagged_peaks_t4",
    };

    /** North of this (climate value; about z -1260) the cold continent is snow and ice, south of it taiga. */
    private static final double SNOW_LINE = -0.7;

    /** Soil tier of a biome as com.valoris.farming.SoilQuality sees it with its default settings. */
    public static int biomeTier(String b) {
        if (b.startsWith("valoris2:")) return b.charAt(b.length() - 1) - '0';
        if (b.contains("desert") || b.contains("badlands") || b.startsWith("snowy") || b.startsWith("frozen")
                || b.equals("ice_spikes") || b.endsWith("_peaks") || b.equals("grove") || b.equals("deep_frozen_ocean")) return 4;
        if (b.contains("plains")) return 1;
        if (b.contains("forest") && !b.startsWith("windswept") || b.equals("meadow") || b.equals("cherry_grove")
                || b.equals("river") || b.equals("mushroom_fields")) return 2;
        return 3;
    }

    /**
     * The biome (path in the minecraft namespace, or a full id for the map's own biomes) of a column.
     * Beaches, rivers and the snowy peaks take the soil tier of the land around them (valoris2:beach_t2 on tier 2
     * land and so on), so every region keeps its tiers; slopes below the peaks keep the region's biome.
     */
    public static String biome(int x, int z) {
        Sample s = sample(x, z);
        double lat = s.lat, m = s.moist;
        int h = s.height;
        Profile p = MASSES[s.continent].profile;
        boolean snowy = p == Profile.ICE || (p == Profile.COLD && lat < SNOW_LINE);

        if (h < SEA_LEVEL && !s.river && s.land < 0) {
            boolean deep = h < 44;
            if (lat < -0.75) return deep ? "deep_frozen_ocean" : "frozen_ocean"; // pack ice only in the far north
            if (lat < -0.4) return deep ? "deep_cold_ocean" : "cold_ocean";
            if (lat > 0.6) return deep ? "deep_lukewarm_ocean" : "warm_ocean";
            if (lat > 0.3) return deep ? "deep_lukewarm_ocean" : "lukewarm_ocean";
            return deep ? "deep_ocean" : "ocean";
        }
        String region = regionBiome(p, x, z, lat, m, h);
        int tier = Math.max(p.minTier, Math.min(p.maxTier, biomeTier(region)));

        // rivers and lakes inside the land are fresh water, not sea
        if (h < SEA_LEVEL) return snowy ? "frozen_river" : "valoris2:river_t" + tier;
        // the whole coastal strip is beach (its height is kept low in compute), cliffs under mountains are stony
        if (s.land < BEACH && p != Profile.SWAMP) {
            if (s.mountain >= 0.15) return "stony_shore";
            if (h <= SEA_LEVEL + 6) return snowy ? "snowy_beach" : "valoris2:beach_t" + tier;
        }
        // snowy peaks on every mountain, of the region's tier
        if (h >= 150) return snowy ? (h >= 205 ? "frozen_peaks" : "snowy_slopes") : "valoris2:jagged_peaks_t" + tier;
        if (h >= 118 && snowy) return "grove";
        return region;
    }

    /** What grows on this land mass at this place, leaving out beaches, rivers and peaks. */
    private static String regionBiome(Profile p, int x, int z, double lat, double m, int h) {
        return switch (p) {
            // tier 1-2: plains and forests
            case HEART -> {
                if (m < 0.1) yield DETAIL.fbm(x, z, 150, 2) > 0.3 ? "sunflower_plains" : "plains";
                if (m < 0.3) yield lat < -0.15 ? "birch_forest" : "forest";
                if (m < 0.5) yield "flower_forest";
                yield "dark_forest";
            }
            // tier 2-3: forests (2) in the north part, savanna, jungle and swamp (3) in the south part
            case WARM -> {
                if (lat < 0.55) yield m < 0.0 ? "forest" : m < 0.3 ? "birch_forest" : "dark_forest";
                if (m > 0.35 && h < 68) yield "swamp";
                if (m > 0.6) yield "bamboo_jungle";
                if (m > 0.35) yield "jungle";
                if (m > 0.15) yield "sparse_jungle";
                yield "savanna";
            }
            // tier 3-4: taiga (3) in the south part, snow and ice (4) in the north part
            case COLD -> lat < SNOW_LINE
                    ? (m > 0.45 ? "ice_spikes" : m > 0.05 ? "snowy_taiga" : "snowy_plains")
                    : (m > 0.35 ? "old_growth_spruce_taiga" : m < -0.35 ? "old_growth_pine_taiga" : "taiga");
            case ICE -> m > 0.4 ? "ice_spikes" : m > 0.0 ? "snowy_taiga" : "snowy_plains";
            case TAIGA -> m > 0.3 ? "old_growth_spruce_taiga" : "taiga";
            case DESERT -> h > 92 ? (m < -0.2 ? "eroded_badlands" : "badlands") : "desert";
            case JUNGLE -> m > 0.4 ? "bamboo_jungle" : m > -0.2 ? "jungle" : "sparse_jungle";
            case GREEN -> m > 0.2 ? "flower_forest" : "plains";
            case SWAMP -> m > 0.2 ? "mangrove_swamp" : "swamp";
            case CHERRY -> m > -0.2 ? "cherry_grove" : "meadow";
        };
    }

    /** A plains spot on the heart continent for the world spawn: {x, surface y, z}. */
    public static int[] spawnPoint() {
        for (int r = 0; r < 1200; r += 8) {
            for (int a = 0; a < 360; a += 10) {
                int x = (int) (-300 + r * Math.cos(Math.toRadians(a)));
                int z = (int) (0 + r * Math.sin(Math.toRadians(a)));
                String b = biome(x, z);
                int h = height(x, z);
                if ((b.equals("plains") || b.equals("sunflower_plains")) && h > SEA_LEVEL + 1 && h < 95) {
                    return new int[] {x, h, z};
                }
            }
        }
        return new int[] {0, height(0, 0), 0};
    }
}
