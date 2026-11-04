package com.valoris.discord;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.valoris.auth.Accounts;
import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.data.ValorisData.Kingdom;
import com.valoris.realm.Heraldry;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;

/**
 * Discord roles of the realms: one role per kingdom and one per city, coloured like them in game (a city of a
 * kingdom takes the kingdom's colour), given to every confirmed member (Accounts.linked) who lives there - the
 * kingdom's role and the city's role. It works by comparing, every 30 seconds, what should be with what is (roles
 * of the Discord server and those the bot gave), so every case ends right: a city founded, renamed, recoloured,
 * joining or leaving a kingdom, conquered, disbanded; a kingdom founded or destroyed; a player joining, leaving or
 * moving; a role deleted or taken away by hand (made and given again); a new world (all the old roles go).
 * The bot only ever touches the roles it made (saved in valoris_discord_roles.json in the server folder). They sit
 * as high as the bot may put them: right under its own highest role, kingdoms above cities.
 */
final class DiscordRoles {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String FILE = "valoris_discord_roles.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long VERIFY_MS = 30 * 60_000L; // members' real roles are read again this often

    /** What the bot made and gave. */
    private static final class State {
        Map<String, String> roles = new LinkedHashMap<>();     // "kingdom:<id>" / "city:<team>" -> role id
        Map<String, List<String>> given = new LinkedHashMap<>(); // member id -> role ids given
    }

    record Want(String key, String name, int color) {}

    /** The wanted state, taken on the server thread. */
    record Snapshot(List<Want> roles, Map<String, Set<String>> members) {}

    private final DiscordBot bot;
    private final String guild;
    private final Path file;
    private State state = new State();
    private final AtomicBoolean running = new AtomicBoolean();
    private String botId;
    private long lastVerify;

    DiscordRoles(DiscordBot bot, String guild, MinecraftServer server) {
        this.bot = bot;
        this.guild = guild;
        this.file = server.getServerDirectory().resolve(FILE);
        try {
            if (Files.exists(file)) {
                State read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), State.class);
                if (read != null) state = read;
                if (state.roles == null) state.roles = new LinkedHashMap<>();
                if (state.given == null) state.given = new LinkedHashMap<>();
            }
        } catch (Exception e) {
            LOG.warn("Discord: cannot read {} ({}); roles made before are forgotten", file, e.toString());
        }
    }

    private void save() {
        try {
            Path tmp = file.resolveSibling(FILE + ".tmp");
            Files.writeString(tmp, GSON.toJson(state), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            LOG.warn("Discord: cannot save {} ({})", file, e.toString());
        }
    }

    // ------------------------------------------------------------------ what should be (server thread)

    static Snapshot snapshot(MinecraftServer server) {
        ValorisData data = ValorisData.get(server);
        List<Want> roles = new ArrayList<>();
        Map<UUID, Kingdom> kingdomOf = new HashMap<>();
        List<Kingdom> kingdoms = new ArrayList<>(data.kingdoms());
        kingdoms.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        for (Kingdom k : kingdoms) {
            roles.add(new Want("kingdom:" + k.id, k.name, Heraldry.kingdomColor(k)));
            for (UUID c : k.cities) kingdomOf.put(c, k);
        }
        List<City> cities = new ArrayList<>(data.all());
        cities.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        Map<UUID, City> cityOf = new HashMap<>();
        for (City c : cities) {
            Kingdom k = kingdomOf.get(c.teamId);
            roles.add(new Want("city:" + c.teamId, c.name, k != null ? Heraldry.kingdomColor(k) : Heraldry.teamColor(c.teamId)));
            for (UUID m : c.members) cityOf.put(m, c);
            if (c.ruler != null) cityOf.put(c.ruler, c);
        }
        Map<String, Set<String>> members = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : Accounts.linked().entrySet()) {
            Set<String> keys = new LinkedHashSet<>();
            for (String name : e.getValue()) {
                City c = cityOf.get(UUIDUtil.createOfflinePlayerUUID(name)); // the server runs in offline mode
                if (c == null) continue;
                keys.add("city:" + c.teamId);
                Kingdom k = kingdomOf.get(c.teamId);
                if (k != null) keys.add("kingdom:" + k.id);
            }
            members.put(e.getKey(), keys);
        }
        return new Snapshot(roles, members);
    }

    // ------------------------------------------------------------------ making it so (off the server thread)

    void sync(Snapshot want) {
        if (!running.compareAndSet(false, true)) return; // the last round is still going
        warned = false;
        run(want).whenComplete((v, e) -> {
            if (e != null) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                if (c instanceof DiscordBot.ApiException api && api.status == 403) {
                    LOG.warn("Discord: the bot may not manage roles. Give it the Manage Roles permission and put its role high in the list.");
                } else {
                    LOG.warn("Discord: role sync failed ({})", c.toString());
                }
            }
            save();
            running.set(false);
        });
    }

    private CompletableFuture<Void> run(Snapshot want) {
        String base = "/guilds/" + guild;
        CompletableFuture<Void> id = botId != null ? CompletableFuture.completedFuture(null)
                : bot.rest("GET", "/users/@me", null).thenAccept(me -> botId = me.getAsJsonObject().get("id").getAsString());
        return id.thenCompose(v -> bot.rest("GET", base + "/members/" + botId, null)).thenCompose(meMember -> {
            Set<String> botRoles = new HashSet<>();
            for (JsonElement r : meMember.getAsJsonObject().getAsJsonArray("roles")) botRoles.add(r.getAsString());
            return bot.rest("GET", base + "/roles", null).thenCompose(all -> {
                Map<String, JsonObject> guildRoles = new HashMap<>();
                int botTop = 0;
                for (JsonElement r : all.getAsJsonArray()) {
                    JsonObject o = r.getAsJsonObject();
                    guildRoles.put(o.get("id").getAsString(), o);
                    if (botRoles.contains(o.get("id").getAsString())) botTop = Math.max(botTop, o.get("position").getAsInt());
                }
                int top = botTop;
                return roles(base, want, guildRoles)
                        .thenCompose(x -> order(base, want, guildRoles, top))
                        .thenCompose(x -> members(base, want));
            });
        });
    }

    /** Make the missing roles, fix names and colours, delete the roles of realms that are gone. */
    private CompletableFuture<Void> roles(String base, Snapshot want, Map<String, JsonObject> guildRoles) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        Set<String> keys = new HashSet<>();
        for (Want w : want.roles()) {
            keys.add(w.key());
            String trimmed = w.name().trim().isEmpty() ? "?" : w.name().trim();
            String name = (trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed).trim(); // as Discord keeps it
            int color = Math.max(1, w.color() & 0xFFFFFF); // 0 would mean "no colour"
            JsonObject body = new JsonObject();
            body.addProperty("name", name);
            body.addProperty("color", color);
            chain = chain.thenCompose(v -> {
                String id = state.roles.get(w.key());
                JsonObject existing = id == null ? null : guildRoles.get(id);
                if (existing == null) { // new, or deleted by hand: make it
                    JsonObject create = body.deepCopy();
                    create.addProperty("permissions", "0");
                    create.addProperty("mentionable", false);
                    create.addProperty("hoist", false);
                    return tolerant(bot.rest("POST", base + "/roles", create).thenAccept(r -> {
                        String newId = r.getAsJsonObject().get("id").getAsString();
                        if (id != null) dropGiven(id);
                        state.roles.put(w.key(), newId);
                        guildRoles.put(newId, r.getAsJsonObject());
                    }), "make the role " + name);
                }
                if (existing.get("name").getAsString().equals(name) && existing.get("color").getAsInt() == color) {
                    return CompletableFuture.completedFuture(null);
                }
                return tolerant(bot.rest("PATCH", base + "/roles/" + id, body).thenAccept(r -> guildRoles.put(id, r.getAsJsonObject())),
                        "update the role " + name);
            });
        }
        for (String key : new ArrayList<>(state.roles.keySet())) {
            if (keys.contains(key)) continue;
            String id = state.roles.get(key);
            chain = chain.thenCompose(v -> {
                state.roles.remove(key);
                dropGiven(id);
                if (!guildRoles.containsKey(id)) return CompletableFuture.completedFuture(null);
                guildRoles.remove(id);
                return tolerant(bot.rest("DELETE", base + "/roles/" + id, null).thenAccept(r -> {}), "delete a role");
            });
        }
        return chain;
    }

    /** A step that may fail on its own (logged once per round) without stopping the round. */
    private CompletableFuture<Void> tolerant(CompletableFuture<Void> step, String what) {
        return step.exceptionally(e -> {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            if (!warned) {
                warned = true;
                LOG.warn("Discord: could not {} ({})", what, c.toString());
            }
            return null;
        });
    }

    private boolean warned;

    private void dropGiven(String roleId) {
        for (List<String> l : state.given.values()) l.remove(roleId);
    }

    /** Right under the bot's highest role: kingdoms first, then cities. Only moved when out of place. */
    private CompletableFuture<Void> order(String base, Snapshot want, Map<String, JsonObject> guildRoles, int botTop) {
        List<String> ids = new ArrayList<>();
        for (Want w : want.roles()) {
            String id = state.roles.get(w.key());
            if (id != null && guildRoles.containsKey(id)) ids.add(id);
        }
        if (ids.isEmpty() || botTop <= 1) return CompletableFuture.completedFuture(null);
        // in place: in the wanted order, and no other role between them and the bot's own
        boolean inPlace = true;
        int lowest = Integer.MAX_VALUE, previous = Integer.MAX_VALUE;
        for (String id : ids) {
            int pos = guildRoles.get(id).get("position").getAsInt();
            if (pos >= previous || pos >= botTop) { inPlace = false; break; }
            previous = pos;
            lowest = Math.min(lowest, pos);
        }
        if (inPlace) {
            Set<String> ours = new HashSet<>(ids);
            for (Map.Entry<String, JsonObject> e : guildRoles.entrySet()) {
                int pos = e.getValue().get("position").getAsInt();
                if (!ours.contains(e.getKey()) && pos > lowest && pos < botTop) { inPlace = false; break; }
            }
        }
        if (inPlace) return CompletableFuture.completedFuture(null);
        JsonArray body = new JsonArray();
        for (int i = 0; i < ids.size(); i++) {
            JsonObject o = new JsonObject();
            o.addProperty("id", ids.get(i));
            o.addProperty("position", Math.max(1, botTop - 1 - i));
            body.add(o);
        }
        return tolerant(bot.restArray("PATCH", base + "/roles", body).thenAccept(r -> {}), "move the roles up");
    }

    /** Give and take the realm roles of every confirmed member. */
    private CompletableFuture<Void> members(String base, Snapshot want) {
        boolean verify = System.currentTimeMillis() - lastVerify > VERIFY_MS;
        if (verify) lastVerify = System.currentTimeMillis();
        Set<String> managed = new HashSet<>(state.roles.values());
        Set<String> everyone = new LinkedHashSet<>(want.members().keySet());
        everyone.addAll(state.given.keySet());
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (String member : everyone) {
            Set<String> should = new LinkedHashSet<>();
            for (String key : want.members().getOrDefault(member, Set.of())) {
                String id = state.roles.get(key);
                if (id != null) should.add(id);
            }
            chain = chain.thenCompose(v -> {
                CompletableFuture<Set<String>> has;
                if (verify) { // what they really have (a role may have been taken or given by hand)
                    has = bot.rest("GET", base + "/members/" + member, null).handle((m, e) -> {
                        if (e != null) return null; // left the Discord server
                        Set<String> out = new LinkedHashSet<>();
                        for (JsonElement r : m.getAsJsonObject().getAsJsonArray("roles")) {
                            if (managed.contains(r.getAsString())) out.add(r.getAsString());
                        }
                        return out;
                    });
                } else {
                    has = CompletableFuture.completedFuture(new LinkedHashSet<>(state.given.getOrDefault(member, List.of())));
                }
                return has.thenCompose(current -> {
                    if (current == null) { state.given.remove(member); return CompletableFuture.completedFuture(null); }
                    CompletableFuture<Void> c = CompletableFuture.completedFuture(null);
                    for (String r : should) {
                        if (current.contains(r)) continue;
                        c = c.thenCompose(x -> bot.rest("PUT", base + "/members/" + member + "/roles/" + r, null)
                                .handle((ok, e) -> { if (e == null) current.add(r); return null; }));
                    }
                    for (String r : new ArrayList<>(current)) {
                        if (should.contains(r)) continue;
                        c = c.thenCompose(x -> bot.rest("DELETE", base + "/members/" + member + "/roles/" + r, null)
                                .handle((ok, e) -> { if (e == null) current.remove(r); return null; }));
                    }
                    return c.thenAccept(x -> {
                        if (current.isEmpty() && should.isEmpty()) state.given.remove(member);
                        else state.given.put(member, new ArrayList<>(current));
                    });
                });
            });
        }
        return chain;
    }
}
