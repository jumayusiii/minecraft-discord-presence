package dev.discordpresence.rpc;

import com.mojang.logging.LogUtils;
import dev.discordpresence.DiscordPresenceConfig;
import dev.discordpresence.ipc.DiscordIpcClient;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;

public final class DiscordRpcService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long RETRY_DELAY_MILLIS = 10_000L;
    private static final long IDLE_DELAY_MILLIS = 15_000L;
    private static final long MIN_UPDATE_INTERVAL_MILLIS = 4_000L;

    private final Object lock = new Object();
    private final AtomicReference<RichPresence> wanted = new AtomicReference<RichPresence>();
    private volatile boolean running;
    private Thread worker;
    private DiscordIpcClient client;
    private String connectedApplicationId;
    private RichPresence lastSubmitted;
    private long lastSendTime;
    private boolean warnedAboutMissingApplicationId;

    public void start() {
        synchronized (lock) {
            if (running) {
                return;
            }
            running = true;
            warnedAboutMissingApplicationId = false;
            worker = new Thread(this::loop, "discord-presence");
            worker.setDaemon(true);
            worker.start();
        }
    }

    public void stop() {
        Thread thread;
        synchronized (lock) {
            if (!running) {
                return;
            }
            running = false;
            thread = worker;
            lock.notifyAll();
        }
        if (thread != null) {
            try {
                thread.join(3_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void update(RichPresence presence) {
        if (!running) {
            return;
        }
        wanted.set(presence);
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    private void loop() {
        try {
            while (running) {
                DiscordPresenceConfig.Settings settings = DiscordPresenceConfig.read();
                String applicationId = settings.discordApplicationId();
                if (!settings.enabled() || applicationId == null) {
                    warnAboutMissingApplicationId(settings);
                    disconnect();
                    wanted.set(null);
                    lastSubmitted = null;
                    await(IDLE_DELAY_MILLIS);
                    continue;
                }

                if (client == null || !client.isOpen() || !applicationId.equals(connectedApplicationId)) {
                    disconnect();
                    try {
                        client = DiscordIpcClient.connect(applicationId);
                        connectedApplicationId = applicationId;
                        LOGGER.info("Connected to the Discord client as application {}", applicationId);
                        if (lastSubmitted != null) {
                            wanted.set(lastSubmitted);
                        }
                    } catch (IOException e) {
                        LOGGER.debug("Discord is not reachable yet ({}), retrying", e.toString());
                        await(RETRY_DELAY_MILLIS);
                        continue;
                    }
                }

                try {
                    RichPresence presence = awaitUpdate(1_000L);
                    if (presence == null) {
                        continue;
                    }
                    long minInterval = Math.max(
                            MIN_UPDATE_INTERVAL_MILLIS,
                            settings.updateIntervalSeconds() * 1_000L
                    );
                    long elapsed = System.currentTimeMillis() - lastSendTime;
                    if (elapsed < minInterval) {
                        await(minInterval - elapsed);
                    }
                    if (!running) {
                        break;
                    }
                    client.setActivity(presence.toJson());
                    lastSubmitted = presence;
                    lastSendTime = System.currentTimeMillis();
                    LOGGER.debug("Presence updated: {} | {}", presence.details(), presence.state());
                } catch (IOException e) {
                    LOGGER.debug("Lost the Discord connection ({})", e.toString());
                    disconnect();
                    await(5_000L);
                }
            }
        } finally {
            disconnect();
        }
    }

    private void warnAboutMissingApplicationId(DiscordPresenceConfig.Settings settings) {
        if (warnedAboutMissingApplicationId || !settings.enabled()) {
            return;
        }
        warnedAboutMissingApplicationId = true;
        LOGGER.warn(
                "This build has no Discord application ID, so the presence stays hidden. "
                        + "Build with -PdiscordApplicationId=<id> or set DISCORD_APPLICATION_ID."
        );
    }

    private void disconnect() {
        DiscordIpcClient connection = client;
        client = null;
        connectedApplicationId = null;
        if (connection != null) {
            try {
                connection.setActivity(null);
            } catch (IOException ignored) {
                // The connection is going away anyway.
            }
            connection.close();
        }
    }

    private RichPresence awaitUpdate(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        synchronized (lock) {
            while (running) {
                RichPresence presence = wanted.getAndSet(null);
                if (presence != null) {
                    return presence;
                }
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) {
                    return null;
                }
                try {
                    lock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            return null;
        }
    }

    private void await(long millis) {
        synchronized (lock) {
            if (!running) {
                return;
            }
            try {
                lock.wait(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
