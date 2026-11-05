package com.valoris.client;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.valoris.net.PresencePayload;
import com.valoris.valoris2;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Discord Rich Presence: while Discord runs on this computer, the player's profile shows what they do in Valoris
 * (realm, city, title, a siege; the server decides it, discord.PresenceSender), or that they are in the menu.
 * Talks to the Discord app over its local IPC (a named pipe on Windows, a unix socket elsewhere) without any
 * library. The Discord application comes from the server and is remembered (config/valoris2-presence.txt) for the
 * menu next time. Without Discord running nothing happens; it tries again every 30 seconds.
 */
@EventBusSubscriber(modid = valoris2.MODID, value = Dist.CLIENT)
public final class RichPresence {
    private RichPresence() {}

    private static final Path CACHE = FMLPaths.CONFIGDIR.get().resolve("valoris2-presence.txt");
    private static final long MIN_GAP_MS = 5_000; // Discord takes 5 updates in 20 s
    private static final long REFRESH_MS = 15_000;

    private static volatile String appId, image = "";
    private static volatile JsonObject wanted;
    private static volatile long version;
    private static volatile long sessionStart = System.currentTimeMillis() / 1000;
    private static boolean started;

    // ------------------------------------------------------------------ what to show (client thread)

    /** From the server (network thread). */
    public static void receive(PresencePayload p) {
        if (p.appId().isEmpty()) return;
        if (!p.appId().equals(appId) || !p.image().equals(image)) {
            appId = p.appId();
            image = p.image();
            try {
                Files.writeString(CACHE, appId + "\n" + image + "\n", StandardCharsets.UTF_8);
            } catch (IOException ignored) {
            }
        }
        JsonObject a = base(p.details(), p.state(), sessionStart);
        if (p.max() > 0) {
            JsonObject party = new JsonObject();
            party.addProperty("id", "valoris-" + p.appId());
            JsonArray size = new JsonArray();
            size.add(Math.max(1, p.online()));
            size.add(Math.max(p.max(), p.online()));
            party.add("size", size);
            a.add("party", party);
        }
        show(a);
    }

    private static JsonObject base(String details, String state, long since) {
        JsonObject a = new JsonObject();
        a.addProperty("details", details);
        if (state != null && !state.isEmpty()) a.addProperty("state", state);
        JsonObject time = new JsonObject();
        time.addProperty("start", since);
        a.add("timestamps", time);
        if (!image.isEmpty()) {
            JsonObject assets = new JsonObject();
            assets.addProperty("large_image", image);
            assets.addProperty("large_text", "Valoris");
            a.add("assets", assets);
        }
        return a;
    }

    private static void show(JsonObject activity) {
        wanted = activity;
        version++;
    }

    private static void menu() {
        show(base("In the main menu", null, System.currentTimeMillis() / 1000));
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (started) return;
        started = true;
        try {
            if (Files.exists(CACHE)) {
                List<String> lines = Files.readAllLines(CACHE, StandardCharsets.UTF_8);
                if (!lines.isEmpty() && lines.get(0).matches("\\d{5,25}")) appId = lines.get(0);
                if (lines.size() > 1) image = lines.get(1).trim();
            }
        } catch (IOException ignored) {
        }
        menu();
        Thread t = new Thread(RichPresence::loop, "Valoris Rich Presence");
        t.setDaemon(true);
        t.start();
    }

    @SubscribeEvent
    public static void onJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        sessionStart = System.currentTimeMillis() / 1000;
        boolean single = Minecraft.getInstance().hasSingleplayerServer();
        show(base(single ? "Singleplayer" : "Joining the realms...", null, sessionStart));
    }

    @SubscribeEvent
    public static void onLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        menu();
    }

    // ------------------------------------------------------------------ talking to Discord (own thread)

    private static void loop() {
        Ipc ipc = null;
        String connectedApp = null;
        long sentVersion = -1, lastSend = 0;
        String refusedApp = null;
        while (true) {
            try {
                String app = appId;
                if (app == null || app.equals(refusedApp)) {
                    Thread.sleep(2000);
                    continue;
                }
                if (ipc == null || !app.equals(connectedApp)) {
                    if (ipc != null) ipc.close();
                    ipc = null;
                    ipc = Ipc.open();
                    if (ipc == null) { // Discord is not running
                        Thread.sleep(30_000);
                        continue;
                    }
                    JsonObject hello = new JsonObject();
                    hello.addProperty("v", 1);
                    hello.addProperty("client_id", app);
                    ipc.send(0, hello.toString());
                    Frame ready = ipc.receive();
                    if (ready.op != 1) { // closed: a wrong application id
                        LogUtils.getLogger().warn("Discord Rich Presence: Discord refused application {} ({})", app, ready.json);
                        refusedApp = app;
                        ipc.close();
                        ipc = null;
                        continue;
                    }
                    connectedApp = app;
                    sentVersion = -1;
                }
                long v = version;
                long now = System.currentTimeMillis();
                // sent again now and then even when nothing changed, so that ours is the activity updated last and
                // not the one of a launcher (CurseForge) that keeps updating its own
                if ((v != sentVersion && now - lastSend >= MIN_GAP_MS) || now - lastSend >= REFRESH_MS) {
                    JsonObject args = new JsonObject();
                    args.addProperty("pid", ProcessHandle.current().pid());
                    args.add("activity", wanted);
                    JsonObject cmd = new JsonObject();
                    cmd.addProperty("cmd", "SET_ACTIVITY");
                    cmd.add("args", args);
                    cmd.addProperty("nonce", UUID.randomUUID().toString());
                    ipc.send(1, cmd.toString());
                    Frame answer = ipc.receive();
                    if (answer.op == 2) throw new IOException("closed by Discord: " + answer.json);
                    sentVersion = v;
                    lastSend = now;
                }
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) { // Discord closed or restarted: start over a little later
                if (ipc != null) {
                    try {
                        ipc.close();
                    } catch (IOException ignored) {
                    }
                }
                ipc = null;
                connectedApp = null;
                try {
                    Thread.sleep(15_000);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }

    private record Frame(int op, String json) {}

    /** One connection to the local Discord app; frames are op and length (little endian ints) and JSON. */
    private abstract static class Ipc implements Closeable {
        abstract void write(byte[] data) throws IOException;

        abstract void readFully(byte[] data) throws IOException;

        void send(int op, String json) throws IOException {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            ByteBuffer b = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
            b.putInt(op).putInt(body.length).put(body);
            write(b.array());
        }

        Frame receive() throws IOException {
            byte[] head = new byte[8];
            readFully(head);
            ByteBuffer h = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
            int op = h.getInt(), len = h.getInt();
            if (len < 0 || len > 1 << 20) throw new IOException("bad frame");
            byte[] body = new byte[len];
            readFully(body);
            String json = new String(body, StandardCharsets.UTF_8);
            if (op == 1) { // a dispatch can still say it failed
                try {
                    JsonObject o = JsonParser.parseString(json).getAsJsonObject();
                    if (o.has("evt") && "ERROR".equals(o.get("evt").getAsString())) op = 2;
                } catch (Exception ignored) {
                }
            }
            return new Frame(op, json);
        }

        /** The first Discord pipe/socket that answers, or null. */
        static Ipc open() {
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            for (int i = 0; i < 10; i++) {
                try {
                    if (windows) {
                        RandomAccessFile pipe = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                        return new Ipc() {
                            void write(byte[] d) throws IOException { pipe.write(d); }
                            void readFully(byte[] d) throws IOException { pipe.readFully(d); }
                            public void close() throws IOException { pipe.close(); }
                        };
                    }
                    for (Path dir : unixDirs()) {
                        Path sock = dir.resolve("discord-ipc-" + i);
                        if (!Files.exists(sock)) continue;
                        SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
                        try {
                            ch.connect(UnixDomainSocketAddress.of(sock));
                        } catch (IOException e) {
                            ch.close();
                            continue;
                        }
                        return new Ipc() {
                            void write(byte[] d) throws IOException {
                                ByteBuffer b = ByteBuffer.wrap(d);
                                while (b.hasRemaining()) ch.write(b);
                            }

                            void readFully(byte[] d) throws IOException {
                                ByteBuffer b = ByteBuffer.wrap(d);
                                while (b.hasRemaining()) if (ch.read(b) < 0) throw new IOException("closed");
                            }

                            public void close() throws IOException { ch.close(); }
                        };
                    }
                } catch (IOException ignored) {
                }
            }
            return null;
        }

        private static List<Path> unixDirs() {
            List<Path> out = new ArrayList<>();
            for (String env : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
                String v = System.getenv(env);
                if (v != null && !v.isEmpty()) out.add(Path.of(v));
            }
            out.add(Path.of("/tmp"));
            List<Path> all = new ArrayList<>();
            for (Path base : out) { // Discord installed as Flatpak or Snap puts it one folder deeper
                all.add(base);
                all.add(base.resolve("app/com.discordapp.Discord"));
                all.add(base.resolve("snap.discord"));
            }
            return all;
        }
    }
}
