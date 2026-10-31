package com.valoris.data;

import java.util.Collection;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-persistent mod data: cities, kingdoms and kingdom relations. */
public class ValorisData extends SavedData {
    private static final String NAME = "valoris_data";

    public static class City {
        public final UUID teamId;   // FTB party team id
        public UUID ruler;          // always the FTB team owner
        public String name;
        public boolean founded;
        public final Set<String> techs = new LinkedHashSet<>(); // researched technology ids
        public String researching = "";                        // technology being researched ("" = none)
        public long researchStart;                              // epoch millis when the research started
        public long researchEnd;                                // epoch millis when the research completes
        public String bannerDim = "";  // dimension id of the city banner block ("" = not registered)
        public long bannerPos;         // BlockPos.asLong()
        public String noticeTitle = "";  // notice on the notice board ("" = none)
        public String noticeBody = "";
        public long noticeAt;            // epoch millis when it was posted
        /** Residents (ruler included) as Valoris knows them: kept in step with the FTB team, used to repair it. */
        public final Set<UUID> members = new LinkedHashSet<>();
        /** The kingdom this city rebelled against, while the rebellion lasts (null = none). */
        public UUID rebelledFrom;
        /** Epoch millis until which the city may not rebel again (its last rebellion was put down). */
        public long rebelCooldownUntil;

        public boolean hasBanner() {
            return !bannerDim.isEmpty();
        }

        public City(UUID teamId, UUID ruler, String name) {
            this.teamId = teamId;
            this.ruler = ruler;
            this.name = name;
        }
    }

    public static class Kingdom {
        public final UUID id;
        public String name;
        public UUID capital;                                   // city team id; its ruler is the king
        public final Set<UUID> cities = new LinkedHashSet<>(); // city team ids (includes capital)
        public int color = -1;                                 // RGB of every city on the map (-1 = not chosen yet)
        public String prefix = DEFAULT_PREFIX;                 // shown before the name: "Kingdom of" X
        public String rulerTitle = DEFAULT_RULER_TITLE;        // what its ruler is called: "King" Y

        public static final String DEFAULT_PREFIX = "Kingdom of", DEFAULT_RULER_TITLE = "King";

        public Kingdom(UUID id, String name, UUID capital) {
            this.id = id;
            this.name = name;
            this.capital = capital;
            this.cities.add(capital);
        }

        /** The full name, e.g. "Kingdom of Avalon". */
        public String title() {
            return prefix + " " + name;
        }
    }

    public enum SiegePhase { PREPARATION, ACTIVE }

    public static class Siege {
        public final UUID id;
        // realm ids (kingdom id, or the team id of an independent city), see Realms
        public UUID attackerKingdom;
        public UUID defenderKingdom;
        public final UUID cityTeam;
        public String attackerName;
        public String defenderName;
        public String cityName;
        public long startTick;
        public long endTick;
        public SiegePhase phase = SiegePhase.PREPARATION;
        public boolean protectionApplied;
        public String prevEditMode = "";  // old saves only, see prevProps
        public boolean prevPvp;           // old saves only, see prevProps
        /** Realms that joined the siege later, in order; the first attacker (attackerKingdom) leads it. */
        public final java.util.List<UUID> coAttackers = new java.util.ArrayList<>();
        public final Map<UUID, String> coAttackerNames = new HashMap<>();

        public boolean isAttacking(UUID realm) {
            return attackerKingdom.equals(realm) || coAttackers.contains(realm);
        }

        /** FTB team properties of the city before the siege opened its land: property id -> value. */
        public final Map<String, String> prevProps = new HashMap<>();
        public int lastMark = Integer.MAX_VALUE; // last announced countdown mark (seconds)
        public int bannerHits;                    // hits the attackers landed on the city banner so far

        public Siege(UUID id, UUID attackerKingdom, UUID defenderKingdom, UUID cityTeam,
                     String attackerName, String defenderName, String cityName) {
            this.id = id;
            this.attackerKingdom = attackerKingdom;
            this.defenderKingdom = defenderKingdom;
            this.cityTeam = cityTeam;
            this.attackerName = attackerName;
            this.defenderName = defenderName;
            this.cityName = cityName;
        }
    }

    /**
     * A war between two sides. The first realm of each side leads it (the one who declared it, the one attacked);
     * allies join a side by being called (offensive) or automatically when their ally is attacked (defensive).
     * Every member of one side is at war with every member of the other side.
     */
    public static class War {
        public final UUID id;
        public final List<UUID> attackers = new ArrayList<>(); // [0] leads
        public final List<UUID> defenders = new ArrayList<>(); // [0] leads
        public final Map<UUID, String> names = new HashMap<>();
        public long started; // epoch millis
        /** A city's rebellion against its kingdom (the rebel city attacks), not an ordinary war. */
        public boolean rebellion;

        // ---- statistics (side index 1 = attackers, 2 = defenders) ----
        public final Map<UUID, int[]> realmStats = new HashMap<>();   // realm -> {kills, deaths}
        public final Map<UUID, String> joinReasons = new HashMap<>(); // realm -> how it entered the war
        public final Map<UUID, Long> joinedAt = new HashMap<>();      // realm -> epoch millis
        public final int[] sideKills = new int[3];
        public final int[] citiesTaken = new int[3];
        public final int[] siegesRepelled = new int[3];
        public final List<Long> logTimes = new ArrayList<>();
        public final List<String> logTexts = new ArrayList<>();

        public War(UUID id) {
            this.id = id;
        }

        public int[] stats(UUID realm) {
            return realmStats.computeIfAbsent(realm, k -> new int[2]);
        }

        /** Adds a line to the war's chronicle (the last 60 are kept). */
        public void log(String text) {
            logTimes.add(System.currentTimeMillis());
            logTexts.add(text);
            while (logTexts.size() > 60) {
                logTimes.remove(0);
                logTexts.remove(0);
            }
        }

        /** 1 = attackers, 2 = defenders, 0 = not in this war. */
        public int sideOf(UUID realm) {
            if (attackers.contains(realm)) return 1;
            if (defenders.contains(realm)) return 2;
            return 0;
        }

        public List<UUID> side(int side) {
            return side == 1 ? attackers : defenders;
        }

        public List<UUID> opposite(int side) {
            return side == 1 ? defenders : attackers;
        }

        public boolean isLeader(UUID realm) {
            return (!attackers.isEmpty() && attackers.get(0).equals(realm))
                    || (!defenders.isEmpty() && defenders.get(0).equals(realm));
        }

        public boolean opposed(UUID a, UUID b) {
            int sa = sideOf(a), sb = sideOf(b);
            return sa != 0 && sb != 0 && sa != sb;
        }

        /** Removes a realm; the next member of its side takes the lead. Returns false when its side is now empty. */
        public boolean remove(UUID realm) {
            attackers.remove(realm);
            defenders.remove(realm);
            return !attackers.isEmpty() && !defenders.isEmpty();
        }
    }

    public enum ConquestState { CHOOSING, AWAITING_RULER }

    /** A captured city waiting for the conquering king's decision. */
    public static class Conquest {
        public final UUID cityTeam;
        public UUID attackerKingdom; // realm id of the conqueror
        public String cityName;
        public String attackerName;
        public ConquestState state = ConquestState.CHOOSING;
        public long deadline; // game tick, only used while AWAITING_RULER
        public long decideBy; // game tick by which the conqueror must decide while CHOOSING (0 = not set yet)

        public Conquest(UUID cityTeam, UUID attackerKingdom, String cityName, String attackerName) {
            this.cityTeam = cityTeam;
            this.attackerKingdom = attackerKingdom;
            this.cityName = cityName;
            this.attackerName = attackerName;
        }
    }

    private final Map<UUID, City> cities = new HashMap<>();
    private final Map<UUID, Siege> sieges = new HashMap<>();
    private final Map<UUID, Conquest> conquests = new HashMap<>();
    private final Map<UUID, Long> siegeCooldowns = new HashMap<>(); // city team -> game tick
    private final Map<UUID, int[]> skillCache = new HashMap<>();    // player -> skill levels (for offline members)
    private final Map<UUID, Kingdom> kingdoms = new HashMap<>();
    private final Map<String, Relation> relations = new HashMap<>(); // alliances (wars live in 'wars')
    private final Map<UUID, War> wars = new java.util.LinkedHashMap<>();
    /** Version of the technology tree the cities' data was last converted to (see TechService.tick). */
    public int techTreeVersion;
    /** Eras the admins opened for research (the first era is always open), see /valoris tech era. */
    public final Set<String> openEras = new LinkedHashSet<>();

    public static ValorisData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(ValorisData::new, ValorisData::load, null), NAME);
    }

    // ---- cities ----
    public Collection<City> all() { return cities.values(); }
    public Optional<City> byTeam(UUID teamId) { return Optional.ofNullable(cities.get(teamId)); }
    public Optional<City> byRuler(UUID ruler) { return cities.values().stream().filter(c -> c.ruler.equals(ruler)).findFirst(); }
    public Optional<City> cityByName(String name) { return cities.values().stream().filter(c -> c.name.equalsIgnoreCase(name)).findFirst(); }
    public void add(City city) { cities.put(city.teamId, city); setDirty(); }
    public void remove(UUID teamId) {
        if (cities.remove(teamId) != null) {
            clearRelations(teamId);
            setDirty();
        }
    }

    public Optional<City> cityByBanner(String dimension, long pos) {
        return cities.values().stream().filter(c -> c.hasBanner() && c.bannerPos == pos && c.bannerDim.equals(dimension)).findFirst();
    }

    // ---- sieges / conquests ----
    public Collection<Siege> sieges() { return sieges.values(); }
    public Optional<Siege> siegeOfCity(UUID cityTeam) { return sieges.values().stream().filter(x -> x.cityTeam.equals(cityTeam)).findFirst(); }
    public Optional<Siege> siegeOfAttacker(UUID realmId) { return sieges.values().stream().filter(x -> x.isAttacking(realmId)).findFirst(); }
    public void addSiege(Siege siege) { sieges.put(siege.id, siege); setDirty(); }
    public void removeSiege(UUID id) { if (sieges.remove(id) != null) setDirty(); }

    public Collection<Conquest> conquests() { return conquests.values(); }
    public Optional<Conquest> conquestOfCity(UUID cityTeam) { return Optional.ofNullable(conquests.get(cityTeam)); }
    public Optional<Conquest> firstConquestOf(UUID attackerKingdom) { return conquests.values().stream().filter(c -> c.attackerKingdom.equals(attackerKingdom)).findFirst(); }
    public void addConquest(Conquest c) { conquests.put(c.cityTeam, c); setDirty(); }
    public void removeConquest(UUID cityTeam) { if (conquests.remove(cityTeam) != null) setDirty(); }

    public long cooldownUntil(UUID cityTeam) { return siegeCooldowns.getOrDefault(cityTeam, 0L); }
    public void setCooldown(UUID cityTeam, long untilTick) { siegeCooldowns.put(cityTeam, untilTick); setDirty(); }

    // ---- skill cache ----
    public int[] cachedSkills(UUID player) { return skillCache.get(player); }

    public void cacheSkills(UUID player, int[] levels) {
        int[] old = skillCache.get(player);
        if (old == null || !java.util.Arrays.equals(old, levels)) {
            skillCache.put(player, levels.clone());
            setDirty();
        }
    }

    // ---- kingdoms ----
    public Collection<Kingdom> kingdoms() { return kingdoms.values(); }
    public Optional<Kingdom> kingdomById(UUID id) { return Optional.ofNullable(kingdoms.get(id)); }
    public Optional<Kingdom> kingdomByName(String name) { return kingdoms.values().stream().filter(k -> k.name.equalsIgnoreCase(name)).findFirst(); }
    public Optional<Kingdom> kingdomOfCity(UUID cityTeamId) { return kingdoms.values().stream().filter(k -> k.cities.contains(cityTeamId)).findFirst(); }
    public void addKingdom(Kingdom k) { kingdoms.put(k.id, k); setDirty(); }

    public void removeKingdom(UUID id) {
        if (kingdoms.remove(id) != null) {
            clearRelations(id);
            setDirty();
        }
    }

    // ---- wars ----
    public Collection<War> wars() { return wars.values(); }
    public Optional<War> warById(UUID id) { return Optional.ofNullable(wars.get(id)); }
    public List<War> warsOf(UUID realm) { return wars.values().stream().filter(w -> w.sideOf(realm) != 0).toList(); }
    public void addWar(War w) { wars.put(w.id, w); setDirty(); }
    public void removeWar(UUID id) { if (wars.remove(id) != null) setDirty(); }

    /** The wars in which a and b fight on opposite sides. */
    public List<War> warsBetween(UUID a, UUID b) {
        return wars.values().stream().filter(w -> w.opposed(a, b)).toList();
    }

    // ---- relations (empty = neutral) ----
    private static String key(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    /** WAR when a and b fight on opposite sides of any war, otherwise ALLIANCE or neutral (empty). */
    public Optional<Relation> relation(UUID a, UUID b) {
        if (!warsBetween(a, b).isEmpty()) return Optional.of(Relation.WAR);
        return Optional.ofNullable(relations.get(key(a, b)));
    }

    /** Alliances only: wars are made with WarService. Setting WAR here starts a plain war of a on b. */
    public void setRelation(UUID a, UUID b, Relation relation) {
        if (relation == Relation.WAR) {
            relations.remove(key(a, b));
            if (warsBetween(a, b).isEmpty()) {
                War w = new War(UUID.randomUUID());
                w.attackers.add(a);
                w.defenders.add(b);
                w.started = System.currentTimeMillis();
                wars.put(w.id, w);
            }
        } else if (relation == null) {
            relations.remove(key(a, b));
        } else {
            relations.put(key(a, b), relation);
        }
        setDirty();
    }

    /** Drops every alliance of a realm and takes it out of every war (it joined a kingdom or vanished). */
    public void clearRelations(UUID realmId) {
        String s = realmId.toString();
        relations.keySet().removeIf(key -> key.contains(s));
        dropTruces(realmId);
        wars.values().removeIf(w -> w.sideOf(realmId) != 0 && !w.remove(realmId));
        setDirty();
    }

    /** An independent city became a kingdom: its relations, sieges and conquests now belong to the kingdom. */
    public void replaceRealm(UUID from, UUID to) {
        Map<UUID, Relation> alliances = new HashMap<>();
        String self = from.toString();
        relations.forEach((k, r) -> {
            String[] p = k.split("\\|");
            if (p[0].equals(self)) alliances.put(UUID.fromString(p[1]), r);
            else if (p[1].equals(self)) alliances.put(UUID.fromString(p[0]), r);
        });
        relations.keySet().removeIf(key -> key.contains(self));
        alliances.forEach((other, rel) -> {
            if (!other.equals(to) && relation(to, other).isEmpty()) relations.put(key(to, other), rel);
        });
        copyTruces(from, to);
        dropTruces(from);
        for (War w : wars.values()) {
            for (List<UUID> side : List.of(w.attackers, w.defenders)) {
                int i = side.indexOf(from);
                if (i >= 0 && !side.contains(to)) side.set(i, to);
                else if (i >= 0) side.remove(i);
            }
            String name = w.names.remove(from);
            if (name != null) w.names.putIfAbsent(to, name);
            int[] st = w.realmStats.remove(from);
            if (st != null) w.realmStats.putIfAbsent(to, st);
            String why = w.joinReasons.remove(from);
            if (why != null) w.joinReasons.putIfAbsent(to, why);
            Long at = w.joinedAt.remove(from);
            if (at != null) w.joinedAt.putIfAbsent(to, at);
        }
        for (Siege s : sieges.values()) {
            if (s.attackerKingdom.equals(from)) s.attackerKingdom = to;
            int i = s.coAttackers.indexOf(from);
            if (i >= 0) {
                s.coAttackers.set(i, to);
                s.coAttackerNames.put(to, s.coAttackerNames.remove(from));
            }
            if (s.defenderKingdom.equals(from)) s.defenderKingdom = to;
        }
        for (Conquest c : conquests.values()) if (c.attackerKingdom.equals(from)) c.attackerKingdom = to;
        setDirty();
    }

    // ---- truces (after peace: no war between the two realms until the time is up) ----
    private final Map<String, Long> truces = new HashMap<>(); // key(a, b) -> epoch millis

    /** When the truce between a and b ends (epoch millis), or 0 when there is none. */
    public long truceUntil(UUID a, UUID b) {
        String k = key(a, b);
        Long until = truces.get(k);
        if (until == null) return 0;
        if (until <= System.currentTimeMillis()) {
            truces.remove(k);
            setDirty();
            return 0;
        }
        return until;
    }

    public boolean hasTruce(UUID a, UUID b) {
        return !a.equals(b) && truceUntil(a, b) > 0;
    }

    /** Sets a truce, keeping the later end if there already is one. */
    public void setTruce(UUID a, UUID b, long until) {
        if (a.equals(b)) return;
        truces.merge(key(a, b), until, Math::max);
        setDirty();
    }

    /** The realms a realm has a truce with -> when each truce ends. */
    public Map<UUID, Long> trucesOf(UUID realm) {
        Map<UUID, Long> out = new HashMap<>();
        String self = realm.toString();
        for (String k : List.copyOf(truces.keySet())) {
            String[] p = k.split("\\|");
            UUID other = p[0].equals(self) ? UUID.fromString(p[1]) : p[1].equals(self) ? UUID.fromString(p[0]) : null;
            if (other == null) continue;
            long until = truceUntil(realm, other);
            if (until > 0) out.put(other, until);
        }
        return out;
    }

    /** "to" also gets every truce of "from" (a city leaving a kingdom keeps its kingdom's truces, and the reverse). */
    public void copyTruces(UUID from, UUID to) {
        trucesOf(from).forEach((other, until) -> { if (!other.equals(to)) setTruce(to, other, until); });
    }

    private void dropTruces(UUID realm) {
        String s = realm.toString();
        if (truces.keySet().removeIf(k -> k.contains(s))) setDirty();
    }

    /** Every alliance and every enemy (through any war) of a realm. */
    public Map<UUID, Relation> relationsOf(UUID realmId) {
        Map<UUID, Relation> out = new HashMap<>();
        String self = realmId.toString();
        relations.forEach((k, r) -> {
            String[] p = k.split("\\|");
            if (p[0].equals(self)) out.put(UUID.fromString(p[1]), r);
            else if (p[1].equals(self)) out.put(UUID.fromString(p[0]), r);
        });
        for (War w : wars.values()) {
            int side = w.sideOf(realmId);
            if (side != 0) for (UUID enemy : w.opposite(side)) out.put(enemy, Relation.WAR);
        }
        return out;
    }

    public void touch() { setDirty(); }

    private static void copy(int[] from, int[] to) {
        System.arraycopy(from, 0, to, 0, Math.min(from.length, to.length));
    }

    // ---- persistence ----

    /** One city as NBT (also used for the backups and the archive of removed cities, see DataVault). */
    public static CompoundTag saveCity(City c) {
        CompoundTag t = new CompoundTag();
        t.putUUID("team", c.teamId);
        t.putUUID("ruler", c.ruler);
        t.putString("name", c.name);
        t.putBoolean("founded", c.founded);
        t.putString("bannerDim", c.bannerDim);
        t.putLong("bannerPos", c.bannerPos);
        ListTag techList = new ListTag();
        for (String tech : c.techs) techList.add(StringTag.valueOf(tech));
        t.put("techs", techList);
        t.putString("researching", c.researching);
        t.putLong("researchEnd", c.researchEnd);
        t.putLong("researchStart", c.researchStart);
        t.putString("noticeTitle", c.noticeTitle);
        t.putString("noticeBody", c.noticeBody);
        t.putLong("noticeAt", c.noticeAt);
        if (c.rebelledFrom != null) t.putUUID("rebelledFrom", c.rebelledFrom);
        t.putLong("rebelCooldownUntil", c.rebelCooldownUntil);
        ListTag members = new ListTag();
        for (UUID m : c.members) members.add(StringTag.valueOf(m.toString()));
        t.put("members", members);
        return t;
    }

    public static City loadCity(CompoundTag t) {
        City c = new City(t.getUUID("team"), t.getUUID("ruler"), t.getString("name"));
        c.founded = t.getBoolean("founded");
        c.bannerDim = t.getString("bannerDim");
        c.bannerPos = t.getLong("bannerPos");
        ListTag techList = t.getList("techs", Tag.TAG_STRING);
        for (int i = 0; i < techList.size(); i++) c.techs.add(techList.getString(i));
        c.researching = t.getString("researching");
        c.researchEnd = t.getLong("researchEnd");
        c.researchStart = t.getLong("researchStart");
        c.noticeTitle = t.getString("noticeTitle");
        c.noticeBody = t.getString("noticeBody");
        c.noticeAt = t.getLong("noticeAt");
        c.rebelledFrom = t.hasUUID("rebelledFrom") ? t.getUUID("rebelledFrom") : null;
        c.rebelCooldownUntil = t.getLong("rebelCooldownUntil");
        ListTag members = t.getList("members", Tag.TAG_STRING);
        for (int i = 0; i < members.size(); i++) {
            try {
                c.members.add(UUID.fromString(members.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        // research saved by an older version stored game ticks: drop it
        if (!c.researching.isEmpty() && c.researchEnd < 1_000_000_000_000L) c.researching = "";
        return c;
    }

    public static CompoundTag saveKingdom(Kingdom k) {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", k.id);
        t.putString("name", k.name);
        t.putUUID("capital", k.capital);
        t.putInt("color", k.color);
        t.putString("prefix", k.prefix);
        t.putString("rulerTitle", k.rulerTitle);
        ListTag members = new ListTag();
        for (UUID u : k.cities) members.add(StringTag.valueOf(u.toString()));
        t.put("cities", members);
        return t;
    }

    public static Kingdom loadKingdom(CompoundTag t) {
        Kingdom k = new Kingdom(t.getUUID("id"), t.getString("name"), t.getUUID("capital"));
        ListTag members = t.getList("cities", Tag.TAG_STRING);
        for (int i = 0; i < members.size(); i++) k.cities.add(UUID.fromString(members.getString(i)));
        k.color = t.contains("color") ? t.getInt("color") : -1;
        if (t.contains("prefix")) k.prefix = t.getString("prefix");
        if (t.contains("rulerTitle")) k.rulerTitle = t.getString("rulerTitle");
        return k;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag cityList = new ListTag();
        for (City c : cities.values()) cityList.add(saveCity(c));
        tag.put("cities", cityList);

        ListTag kingdomList = new ListTag();
        for (Kingdom k : kingdoms.values()) kingdomList.add(saveKingdom(k));
        tag.put("kingdoms", kingdomList);

        ListTag relList = new ListTag();
        relations.forEach((k, r) -> {
            CompoundTag t = new CompoundTag();
            t.putString("key", k);
            t.putString("type", r.name());
            relList.add(t);
        });
        tag.put("relations", relList);

        ListTag warList = new ListTag();
        for (War w : wars.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", w.id);
            t.putLong("started", w.started);
            t.putBoolean("rebellion", w.rebellion);
            ListTag a = new ListTag();
            for (UUID id : w.attackers) a.add(StringTag.valueOf(id.toString()));
            ListTag d = new ListTag();
            for (UUID id : w.defenders) d.add(StringTag.valueOf(id.toString()));
            t.put("attackers", a);
            t.put("defenders", d);
            CompoundTag names = new CompoundTag();
            w.names.forEach((id, n) -> names.putString(id.toString(), n));
            t.put("names", names);
            CompoundTag stats = new CompoundTag();
            w.realmStats.forEach((id, v) -> stats.putIntArray(id.toString(), v));
            t.put("realmStats", stats);
            CompoundTag reasons = new CompoundTag();
            w.joinReasons.forEach((id, v) -> reasons.putString(id.toString(), v));
            t.put("joinReasons", reasons);
            CompoundTag joined = new CompoundTag();
            w.joinedAt.forEach((id, v) -> joined.putLong(id.toString(), v));
            t.put("joinedAt", joined);
            t.putIntArray("sideKills", w.sideKills);
            t.putIntArray("citiesTaken", w.citiesTaken);
            t.putIntArray("siegesRepelled", w.siegesRepelled);
            ListTag log = new ListTag();
            for (int i = 0; i < w.logTexts.size(); i++) {
                CompoundTag e = new CompoundTag();
                e.putLong("t", w.logTimes.get(i));
                e.putString("s", w.logTexts.get(i));
                log.add(e);
            }
            t.put("log", log);
            warList.add(t);
        }
        tag.put("wars", warList);

        ListTag siegeList = new ListTag();
        for (Siege x : sieges.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", x.id);
            t.putUUID("attacker", x.attackerKingdom);
            t.putUUID("defender", x.defenderKingdom);
            t.putUUID("city", x.cityTeam);
            t.putString("attackerName", x.attackerName);
            t.putString("defenderName", x.defenderName);
            t.putString("cityName", x.cityName);
            t.putLong("start", x.startTick);
            t.putLong("end", x.endTick);
            t.putString("phase", x.phase.name());
            t.putBoolean("applied", x.protectionApplied);
            t.putString("prevEdit", x.prevEditMode);
            t.putBoolean("prevPvp", x.prevPvp);
            CompoundTag prev = new CompoundTag();
            x.prevProps.forEach(prev::putString);
            t.put("prevProps", prev);
            ListTag co = new ListTag();
            for (UUID id : x.coAttackers) {
                CompoundTag c = new CompoundTag();
                c.putUUID("id", id);
                c.putString("name", x.coAttackerNames.getOrDefault(id, ""));
                co.add(c);
            }
            t.put("coAttackers", co);
            t.putInt("lastMark", x.lastMark);
            t.putInt("bannerHits", x.bannerHits);
            siegeList.add(t);
        }
        tag.put("sieges", siegeList);

        ListTag conquestList = new ListTag();
        for (Conquest c : conquests.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("city", c.cityTeam);
            t.putUUID("attacker", c.attackerKingdom);
            t.putString("cityName", c.cityName);
            t.putString("attackerName", c.attackerName);
            t.putString("state", c.state.name());
            t.putLong("deadline", c.deadline);
            t.putLong("decideBy", c.decideBy);
            conquestList.add(t);
        }
        tag.put("conquests", conquestList);

        ListTag cdList = new ListTag();
        siegeCooldowns.forEach((id, until) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("city", id);
            t.putLong("until", until);
            cdList.add(t);
        });
        tag.put("siegeCooldowns", cdList);

        ListTag skills = new ListTag();
        skillCache.forEach((id, levels) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("player", id);
            t.putIntArray("levels", levels);
            skills.add(t);
        });
        tag.put("skillCache", skills);
        tag.putInt("techTreeVersion", techTreeVersion);
        CompoundTag truceTag = new CompoundTag();
        truces.forEach(truceTag::putLong);
        tag.put("truces", truceTag);
        ListTag eras = new ListTag();
        for (String e : openEras) eras.add(StringTag.valueOf(e));
        tag.put("openEras", eras);
        return tag;
    }

    public static ValorisData load(CompoundTag tag, HolderLookup.Provider registries) {
        ValorisData data = new ValorisData();
        for (Tag raw : tag.getList("cities", Tag.TAG_COMPOUND)) {
            City c = loadCity((CompoundTag) raw);
            data.cities.put(c.teamId, c);
        }
        for (Tag raw : tag.getList("kingdoms", Tag.TAG_COMPOUND)) {
            Kingdom k = loadKingdom((CompoundTag) raw);
            data.kingdoms.put(k.id, k);
        }
        for (Tag raw : tag.getList("relations", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            try {
                Relation r = Relation.valueOf(t.getString("type"));
                String[] p = t.getString("key").split("\\|");
                if (r == Relation.WAR) data.setRelation(UUID.fromString(p[0]), UUID.fromString(p[1]), Relation.WAR); // old save
                else data.relations.put(t.getString("key"), r);
            } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException ignored) {
            }
        }
        for (Tag raw : tag.getList("wars", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            War w = new War(t.getUUID("id"));
            w.started = t.getLong("started");
            w.rebellion = t.getBoolean("rebellion");
            ListTag a = t.getList("attackers", Tag.TAG_STRING);
            for (int i = 0; i < a.size(); i++) w.attackers.add(UUID.fromString(a.getString(i)));
            ListTag d = t.getList("defenders", Tag.TAG_STRING);
            for (int i = 0; i < d.size(); i++) w.defenders.add(UUID.fromString(d.getString(i)));
            CompoundTag names = t.getCompound("names");
            for (String k : names.getAllKeys()) w.names.put(UUID.fromString(k), names.getString(k));
            CompoundTag stats = t.getCompound("realmStats");
            for (String k : stats.getAllKeys()) {
                int[] v = stats.getIntArray(k);
                if (v.length >= 2) w.realmStats.put(UUID.fromString(k), new int[]{v[0], v[1]});
            }
            CompoundTag reasons = t.getCompound("joinReasons");
            for (String k : reasons.getAllKeys()) w.joinReasons.put(UUID.fromString(k), reasons.getString(k));
            CompoundTag joined = t.getCompound("joinedAt");
            for (String k : joined.getAllKeys()) w.joinedAt.put(UUID.fromString(k), joined.getLong(k));
            copy(t.getIntArray("sideKills"), w.sideKills);
            copy(t.getIntArray("citiesTaken"), w.citiesTaken);
            copy(t.getIntArray("siegesRepelled"), w.siegesRepelled);
            for (Tag rawLog : t.getList("log", Tag.TAG_COMPOUND)) {
                CompoundTag e = (CompoundTag) rawLog;
                w.logTimes.add(e.getLong("t"));
                w.logTexts.add(e.getString("s"));
            }
            if (!w.attackers.isEmpty() && !w.defenders.isEmpty()) data.wars.put(w.id, w);
        }
        for (Tag raw : tag.getList("sieges", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            Siege x = new Siege(t.getUUID("id"), t.getUUID("attacker"), t.getUUID("defender"), t.getUUID("city"),
                    t.getString("attackerName"), t.getString("defenderName"), t.getString("cityName"));
            x.startTick = t.getLong("start");
            x.endTick = t.getLong("end");
            try {
                x.phase = SiegePhase.valueOf(t.getString("phase"));
            } catch (IllegalArgumentException ignored) {
            }
            x.protectionApplied = t.getBoolean("applied");
            x.prevEditMode = t.getString("prevEdit");
            x.prevPvp = t.getBoolean("prevPvp");
            for (Tag rawCo : t.getList("coAttackers", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) rawCo;
                x.coAttackers.add(c.getUUID("id"));
                x.coAttackerNames.put(c.getUUID("id"), c.getString("name"));
            }
            CompoundTag prev = t.getCompound("prevProps");
            for (String key : prev.getAllKeys()) x.prevProps.put(key, prev.getString(key));
            if (x.prevProps.isEmpty() && x.protectionApplied && !x.prevEditMode.isEmpty()) {
                x.prevProps.put("ftbchunks:block_edit_mode", x.prevEditMode.toLowerCase(java.util.Locale.ROOT));
                x.prevProps.put("ftbchunks:allow_pvp", String.valueOf(x.prevPvp));
            }
            x.lastMark = t.getInt("lastMark");
            x.bannerHits = t.getInt("bannerHits");
            data.sieges.put(x.id, x);
        }
        for (Tag raw : tag.getList("conquests", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            Conquest c = new Conquest(t.getUUID("city"), t.getUUID("attacker"), t.getString("cityName"), t.getString("attackerName"));
            try {
                c.state = ConquestState.valueOf(t.getString("state"));
            } catch (IllegalArgumentException ignored) {
            }
            c.deadline = t.getLong("deadline");
            c.decideBy = t.getLong("decideBy");
            data.conquests.put(c.cityTeam, c);
        }
        for (Tag raw : tag.getList("siegeCooldowns", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            data.siegeCooldowns.put(t.getUUID("city"), t.getLong("until"));
        }
        for (Tag raw : tag.getList("skillCache", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            int[] levels = t.getIntArray("levels");
            // saved before the warrior skill was removed (blacksmith, warrior, farmer, miner, hunter): drop warrior
            if (levels.length == 5) levels = new int[]{levels[0], levels[2], levels[3], levels[4]};
            data.skillCache.put(t.getUUID("player"), levels);
        }
        data.techTreeVersion = tag.getInt("techTreeVersion");
        CompoundTag truceTag = tag.getCompound("truces");
        for (String k : truceTag.getAllKeys()) data.truces.put(k, truceTag.getLong(k));
        ListTag eras = tag.getList("openEras", Tag.TAG_STRING);
        for (int i = 0; i < eras.size(); i++) data.openEras.add(eras.getString(i));
        return data;
    }
}
