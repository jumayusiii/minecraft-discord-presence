package dev.discordpresence.client;

import dev.discordpresence.DiscordPresenceConfig;
import dev.discordpresence.rpc.DiscordRpcService;
import dev.discordpresence.rpc.RichPresence;
import java.util.concurrent.atomic.AtomicReference;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = "discordpresence", dist = {Dist.CLIENT})
@EventBusSubscriber(modid = "discordpresence", value = {Dist.CLIENT})
public final class DiscordPresenceClient {
    private static final DiscordRpcService SERVICE = new DiscordRpcService();
    private static final AtomicReference<RichPresence> LAST_PRESENCE = new AtomicReference<RichPresence>();
    private static final long SESSION_START = System.currentTimeMillis();
    private static final int SAMPLE_INTERVAL_TICKS = 10;
    private static int tickCounter;

    public DiscordPresenceClient(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, (IConfigSpec) DiscordPresenceConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        SERVICE.start();
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (++tickCounter % SAMPLE_INTERVAL_TICKS != 0) {
            return;
        }
        refresh();
    }

    @SubscribeEvent
    static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        refresh();
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        refresh();
    }

    @SubscribeEvent
    static void onClientStopping(ClientStoppingEvent event) {
        SERVICE.stop();
    }

    private static void refresh() {
        DiscordPresenceConfig.Settings settings = DiscordPresenceConfig.read();
        if (!settings.enabled()) {
            LAST_PRESENCE.set(null);
            return;
        }
        RichPresence presence = PresenceTracker.capture(settings, SESSION_START);
        if (presence.equals(LAST_PRESENCE.getAndSet(presence))) {
            return;
        }
        SERVICE.update(presence);
    }
}
