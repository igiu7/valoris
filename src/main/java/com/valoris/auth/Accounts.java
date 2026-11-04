package com.valoris.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;

import net.minecraft.server.MinecraftServer;

/**
 * The accounts of the offline-mode login (Auth): name -> salted PBKDF2 hash of the password. Kept in the server
 * folder (valoris_accounts.json), not in the world, so a new world keeps them. Passwords are never stored.
 */
public final class Accounts {
    private Accounts() {}

    private static final String FILE = "valoris_accounts.json";
    private static final int ITERATIONS = 100_000, KEY_BITS = 256, SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Account {
        String name;       // as first registered (the case the player must log in with)
        String salt;
        String hash;
        int iterations;
        long registered;
        String lastIp;
        long lastLogout;   // millis; for the login session (Config auth.sessionMinutes)
        String discordId;  // the Discord member who confirmed this name (discord.DiscordLink), or null
        String discordName;
    }

    private static Map<String, Account> accounts = new LinkedHashMap<>();
    private static Path file;

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public static synchronized void load(MinecraftServer server) {
        file = server.getServerDirectory().resolve(FILE);
        accounts = new LinkedHashMap<>();
        if (!Files.exists(file)) return;
        try {
            Map<String, Account> read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    new TypeToken<LinkedHashMap<String, Account>>() {}.getType());
            if (read != null) accounts = read;
        } catch (Exception e) {
            LogUtils.getLogger().error("Valoris: cannot read {}; nobody can log in until it is fixed", file, e);
            throw new IllegalStateException("Broken " + FILE, e);
        }
    }

    private static void save() {
        if (file == null) return;
        try {
            Path tmp = file.resolveSibling(FILE + ".tmp");
            Files.writeString(tmp, GSON.toJson(accounts), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LogUtils.getLogger().error("Valoris: cannot save {}", file, e);
        }
    }

    public static synchronized Account get(String name) {
        return accounts.get(key(name));
    }

    public static synchronized void register(String name, String password, String ip, String discordId, String discordName) {
        Account a = new Account();
        a.name = name;
        a.discordId = discordId;
        a.discordName = discordName;
        a.iterations = ITERATIONS;
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        a.salt = Base64.getEncoder().encodeToString(salt);
        a.hash = Base64.getEncoder().encodeToString(hash(password, salt, ITERATIONS));
        a.registered = System.currentTimeMillis();
        a.lastIp = ip;
        accounts.put(key(name), a);
        save();
    }

    public static synchronized boolean check(String name, String password) {
        Account a = accounts.get(key(name));
        if (a == null) return false;
        byte[] expected = Base64.getDecoder().decode(a.hash);
        byte[] actual = hash(password, Base64.getDecoder().decode(a.salt), a.iterations);
        return MessageDigest.isEqual(expected, actual);
    }

    public static synchronized void setPassword(String name, String password) {
        Account a = accounts.get(key(name));
        if (a == null) return;
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        a.salt = Base64.getEncoder().encodeToString(salt);
        a.iterations = ITERATIONS;
        a.hash = Base64.getEncoder().encodeToString(hash(password, salt, ITERATIONS));
        save();
    }

    public static synchronized void touch(String name, String ip, long lastLogout) {
        Account a = accounts.get(key(name));
        if (a == null) return;
        if (ip != null) a.lastIp = ip;
        if (lastLogout > 0) a.lastLogout = lastLogout;
        save();
    }

    /** Links the Discord member to an account made without one. */
    public static synchronized void setDiscord(String name, String discordId, String discordName) {
        Account a = accounts.get(key(name));
        if (a == null) return;
        a.discordId = discordId;
        a.discordName = discordName;
        save();
    }

    /** Discord member id -> the Minecraft names they confirmed. */
    public static synchronized Map<String, java.util.List<String>> linked() {
        Map<String, java.util.List<String>> out = new LinkedHashMap<>();
        for (Account a : accounts.values()) {
            if (a.discordId != null) out.computeIfAbsent(a.discordId, k -> new java.util.ArrayList<>()).add(a.name);
        }
        return out;
    }

    /** How many accounts this Discord member has confirmed. */
    public static synchronized int countByDiscord(String discordId) {
        int n = 0;
        for (Account a : accounts.values()) if (discordId.equals(a.discordId)) n++;
        return n;
    }

    public static synchronized boolean remove(String name) {
        boolean removed = accounts.remove(key(name)) != null;
        if (removed) save();
        return removed;
    }

    private static byte[] hash(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        }
    }
}
