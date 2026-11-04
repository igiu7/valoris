package com.valoris.discord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

/**
 * A small Discord bot client on Java's own HTTP client and WebSocket (no library: JDA would bring its own Kotlin,
 * which clashes with Kotlin for Forge). The gateway connection keeps the bot online with its status and receives
 * the button clicks (interactions); everything else goes through the REST API. On any trouble it reconnects.
 */
public final class DiscordBot implements WebSocket.Listener {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String API = "https://discord.com/api/v10";
    private static final String GATEWAY = "wss://gateway.discord.gg/?v=10&encoding=json";
    private static final int INTENTS = 1; // GUILDS: interactions arrive anyway

    private final String token;
    private final Consumer<JsonObject> onInteraction;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Valoris Discord");
        t.setDaemon(true);
        return t;
    });

    private final java.util.concurrent.atomic.AtomicBoolean reconnecting = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile WebSocket ws;
    private volatile boolean stopped, ready, acked = true;
    private volatile Integer seq;
    private volatile String selfId;
    /** seconds before the next reconnect; doubles on every failure (up to 5 min), back to the start once online.
     *  Every reconnect is a new login, and Discord allows only 1000 a day. */
    private volatile int backoff = 5;
    private volatile String status = "";
    private ScheduledFuture<?> heartbeat;
    private final StringBuilder buffer = new StringBuilder();

    public DiscordBot(String token, Consumer<JsonObject> onInteraction) {
        this.token = token;
        this.onInteraction = onInteraction;
    }

    /** The bot user's id (known once online); for bots it is also the application id. */
    public String selfId() {
        return selfId;
    }

    public void start() {
        exec.execute(this::connect);
    }

    public void stop() {
        stopped = true;
        exec.execute(() -> {
            if (heartbeat != null) heartbeat.cancel(false);
            WebSocket w = ws;
            if (w != null) w.sendClose(WebSocket.NORMAL_CLOSURE, "server stopping");
        });
        exec.schedule(exec::shutdownNow, 2, TimeUnit.SECONDS);
    }

    /** The custom status under the bot's name. */
    public void setStatus(String text) {
        status = text;
        exec.execute(() -> {
            if (ready) send(op(3, presence()));
        });
    }

    // ------------------------------------------------------------------ gateway

    private void connect() {
        reconnecting.set(false);
        if (stopped) return;
        ready = false;
        seq = null;
        acked = true;
        http.newWebSocketBuilder().buildAsync(URI.create(GATEWAY), this).whenComplete((w, e) -> {
            if (e != null) {
                LOG.warn("Discord: cannot connect ({}), trying again in 30 s", e.toString());
                reconnectLater(30);
            }
        });
    }

    private void reconnectLater(int seconds) {
        if (stopped || exec.isShutdown() || !reconnecting.compareAndSet(false, true)) return;
        seconds = Math.max(seconds, backoff);
        backoff = Math.min(backoff * 2, 300);
        exec.execute(() -> {
            if (heartbeat != null) heartbeat.cancel(false);
            heartbeat = null;
            ws = null;
            ready = false;
        });
        exec.schedule(this::connect, seconds, TimeUnit.SECONDS);
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        ws = webSocket;
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        buffer.append(data);
        if (last) {
            String text = buffer.toString();
            buffer.setLength(0);
            exec.execute(() -> handle(text));
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int code, String reason) {
        if (webSocket != ws) return null;
        if (code == 4004) {
            LOG.error("Discord: the bot token is wrong (discord.token). The bot stays off.");
            return null;
        }
        if (code >= 4010 && code <= 4014) { // wrong shard / version / intents: trying again would not help
            LOG.error("Discord: the gateway refused the bot ({} {}). The bot stays off.", code, reason);
            return null;
        }
        if (!stopped) LOG.info("Discord: connection closed ({} {}), reconnecting", code, reason);
        reconnectLater(5);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        if (webSocket != ws) return;
        LOG.warn("Discord: connection error ({}), reconnecting", error.toString());
        reconnectLater(10);
    }

    private void handle(String text) {
        JsonObject msg;
        try {
            msg = JsonParser.parseString(text).getAsJsonObject();
        } catch (Exception e) {
            return;
        }
        if (msg.has("s") && !msg.get("s").isJsonNull()) seq = msg.get("s").getAsInt();
        int op = msg.get("op").getAsInt();
        switch (op) {
            case 10 -> { // hello: beat and identify
                long interval = msg.getAsJsonObject("d").get("heartbeat_interval").getAsLong();
                if (heartbeat != null) heartbeat.cancel(false);
                heartbeat = exec.scheduleAtFixedRate(this::beat, (long) (interval * Math.random()), interval, TimeUnit.MILLISECONDS);
                JsonObject d = new JsonObject();
                d.addProperty("token", token);
                d.addProperty("intents", INTENTS);
                JsonObject props = new JsonObject();
                props.addProperty("os", "linux");
                props.addProperty("browser", "valoris");
                props.addProperty("device", "valoris");
                d.add("properties", props);
                d.add("presence", presence());
                send(op(2, d));
            }
            case 11 -> acked = true;
            case 1 -> send(op(1, seq == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(seq)));
            case 7, 9 -> { // reconnect / invalid session: start over
                WebSocket w = ws;
                if (w != null) w.sendClose(4000, "reconnect");
                reconnectLater(op == 9 ? 5 : 1);
            }
            case 0 -> {
                String type = msg.get("t").getAsString();
                if (type.equals("READY")) {
                    ready = true;
                    backoff = 5;
                    try {
                        selfId = msg.getAsJsonObject("d").getAsJsonObject("user").get("id").getAsString();
                    } catch (Exception ignored) {
                    }
                    LOG.info("Discord: bot online");
                } else if (type.equals("INTERACTION_CREATE")) {
                    try {
                        onInteraction.accept(msg.getAsJsonObject("d"));
                    } catch (Exception e) {
                        LOG.warn("Discord: interaction failed", e);
                    }
                }
            }
            default -> {}
        }
    }

    private void beat() {
        if (!acked) { // no answer to the last beat: the connection is dead
            WebSocket w = ws;
            if (w != null) w.abort();
            reconnectLater(5);
            return;
        }
        acked = false;
        send(op(1, seq == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(seq)));
    }

    private void send(JsonObject payload) {
        WebSocket w = ws;
        if (w == null) return;
        try {
            w.sendText(payload.toString(), true).get(10, TimeUnit.SECONDS); // one message at a time
        } catch (Exception e) {
            LOG.warn("Discord: send failed ({})", e.toString());
        }
    }

    private static JsonObject op(int op, JsonElement d) {
        JsonObject o = new JsonObject();
        o.addProperty("op", op);
        o.add("d", d);
        return o;
    }

    private JsonObject presence() {
        JsonObject activity = new JsonObject();
        activity.addProperty("type", 4); // custom status
        activity.addProperty("name", "Custom Status");
        activity.addProperty("state", status.isEmpty() ? "Starting..." : status);
        JsonArray activities = new JsonArray();
        activities.add(activity);
        JsonObject p = new JsonObject();
        p.add("since", JsonNull.INSTANCE);
        p.add("activities", activities);
        p.addProperty("status", "online");
        p.addProperty("afk", false);
        return p;
    }

    // ------------------------------------------------------------------ REST

    public static final class ApiException extends RuntimeException {
        public final int status;

        ApiException(int status, String body) {
            super("Discord API " + status + ": " + body);
            this.status = status;
        }
    }

    /*
     * Rate limits: every REST call goes through one queue, one call at a time. When Discord says a limit is used up
     * (X-RateLimit-Remaining 0) the queue waits X-RateLimit-Reset-After before the next call; a 429 is waited out
     * (retry_after) and tried again, up to MAX_TRIES. So bursts (a siege ending: victory, summary, a kingdom falling,
     * roles changing...) are spread out instead of being dropped. Answers to button clicks skip the queue: Discord
     * wants them within 3 seconds and does not limit them like the rest.
     */
    private static final int MAX_TRIES = 5;
    private CompletableFuture<Void> queue = CompletableFuture.completedFuture(null);

    /** One REST call, queued. Fails with ApiException on an error status. */
    public CompletableFuture<JsonElement> rest(String method, String path, JsonObject body) {
        return enqueue(method, path, body);
    }

    /** The same with a JSON array as the body. */
    public CompletableFuture<JsonElement> restArray(String method, String path, JsonArray body) {
        return enqueue(method, path, body);
    }

    /** At once, not queued (interaction callbacks). */
    public CompletableFuture<JsonElement> restNow(String method, String path, JsonObject body) {
        CompletableFuture<JsonElement> result = new CompletableFuture<>();
        attempt(method, path, body, 1, result);
        return result;
    }

    private synchronized CompletableFuture<JsonElement> enqueue(String method, String path, JsonElement body) {
        CompletableFuture<JsonElement> result = new CompletableFuture<>();
        queue = queue.handle((v, e) -> null).thenCompose(v -> attempt(method, path, body, 1, result));
        return result;
    }

    /** Sends; completes result; the returned future ends when the queue may go on (after any wait Discord asks for). */
    private CompletableFuture<Void> attempt(String method, String path, JsonElement body, int tries,
                                            CompletableFuture<JsonElement> result) {
        if (stopped && exec.isShutdown()) {
            result.completeExceptionally(new IllegalStateException("bot stopped"));
            return CompletableFuture.completedFuture(null);
        }
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(API + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bot " + token)
                .header("User-Agent", "DiscordBot (https://github.com, 1.0) Valoris")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body.toString()));
        if (body != null) b.header("Content-Type", "application/json");
        return http.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString()).handle((r, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
                return CompletableFuture.<Void>completedFuture(null);
            }
            if (r.statusCode() == 429) {
                double wait = r.headers().firstValue("Retry-After").map(DiscordBot::number).orElse(1.0);
                try {
                    wait = JsonParser.parseString(r.body()).getAsJsonObject().get("retry_after").getAsDouble();
                } catch (Exception ignored) {
                }
                if (tries >= MAX_TRIES) {
                    result.completeExceptionally(new ApiException(429, r.body()));
                    return delay(wait);
                }
                LOG.debug("Discord: rate limited on {} {}, waiting {} s", method, path, wait);
                return delay(wait).thenCompose(v -> attempt(method, path, body, tries + 1, result));
            }
            if (r.statusCode() >= 400) {
                result.completeExceptionally(new ApiException(r.statusCode(), r.body()));
            } else {
                String text = r.body();
                try {
                    result.complete(text == null || text.isBlank() ? JsonNull.INSTANCE : JsonParser.parseString(text));
                } catch (Exception e) {
                    result.completeExceptionally(e);
                }
            }
            boolean spent = r.headers().firstValue("X-RateLimit-Remaining").map(v -> v.equals("0")).orElse(false);
            return spent ? delay(r.headers().firstValue("X-RateLimit-Reset-After").map(DiscordBot::number).orElse(1.0))
                    : CompletableFuture.<Void>completedFuture(null);
        }).thenCompose(f -> f);
    }

    private static double number(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }

    private static CompletableFuture<Void> delay(double seconds) {
        long ms = (long) (Math.min(seconds, 600) * 1000) + 50;
        return CompletableFuture.runAsync(() -> {}, CompletableFuture.delayedExecutor(ms, TimeUnit.MILLISECONDS));
    }
}
