package dev.discordpresence.client;

import dev.discordpresence.DiscordPresenceConfig;
import dev.discordpresence.rpc.RichPresence;
import java.util.ArrayList;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

final class PresenceTracker {
    private PresenceTracker() {
    }

    static RichPresence capture(DiscordPresenceConfig.Settings settings, long sessionStart) {
        Minecraft minecraft = Minecraft.getInstance();
        String version = SharedConstants.getCurrentVersion().name();
        long startTimestamp = settings.showSessionTime() ? sessionStart : 0L;
        String largeImage = settings.largeImage();
        String largeImageText = "Minecraft " + version;

        if (minecraft.level == null || minecraft.player == null) {
            boolean prism = PrismDetector.isPrismRunning();
            return new RichPresence(
                    "Main Menu",
                    "Minecraft " + version,
                    largeImage,
                    largeImageText,
                    prism ? settings.smallImage() : null,
                    prism ? "Prism Launcher" : null,
                    startTimestamp
            );
        }

        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;

        // Only show dimensions with a friendly name. Custom dimensions would otherwise
        // expose raw ids such as "minecraft:cb" on the profile.
        String dimension = dimensionName(level);
        if (settings.showDimension() && dimension != null) {
            largeImageText = largeImageText + " \u2022 " + dimension;
        }

        ServerData server = minecraft.getCurrentServer();
        IntegratedServer integrated = minecraft.getSingleplayerServer();
        boolean singleplayer = integrated != null;
        boolean published = integrated != null && integrated.isPublished();

        String mode;
        String modeImage;
        if (published) {
            mode = "LAN World";
            modeImage = "lan";
        } else if (singleplayer) {
            mode = "Singleplayer";
            modeImage = "singleplayer";
        } else if (server != null && server.isRealm()) {
            mode = "Realms";
            modeImage = "realms";
        } else if (server != null && server.isLan()) {
            mode = "LAN World";
            modeImage = "lan";
        } else {
            mode = "Multiplayer";
            modeImage = "multiplayer";
        }

        // The state line only carries the world/server name and the player count.
        ArrayList<String> parts = new ArrayList<String>();
        if (singleplayer) {
            if (settings.showWorldName()) {
                String worldName = integrated.getWorldData().getLevelName();
                if (worldName != null && !worldName.isBlank()) {
                    parts.add(worldName);
                }
            }
            if (published && settings.showPlayerCount()) {
                parts.add(players(integrated.getPlayerCount()));
            }
        } else if (server != null) {
            if (settings.showWorldName() && server.name != null && !server.name.isBlank()) {
                parts.add(server.name);
            }
            if (settings.showPlayerCount()) {
                int count = onlinePlayers(minecraft);
                if (count > 0) {
                    parts.add(players(count));
                }
            }
        }

        String state = parts.isEmpty() ? "Minecraft " + version : String.join(" \u2022 ", parts);

        String gameModeText = null;
        if (settings.showGameMode()) {
            GameType gameType = gameMode(minecraft, player);
            if (gameType != null) {
                gameModeText = prettyGameMode(gameType);
            }
        }

        String smallImage;
        String smallImageText;
        if (PrismDetector.isPrismRunning()) {
            smallImage = settings.smallImage();
            smallImageText = gameModeText == null ? "Prism Launcher" : "Prism Launcher \u2022 " + gameModeText;
        } else {
            smallImage = modeImage;
            smallImageText = gameModeText == null ? mode : mode + " \u2022 " + gameModeText;
        }

        return new RichPresence(mode, state, largeImage, largeImageText, smallImage, smallImageText, startTimestamp);
    }

    private static String dimensionName(Level level) {
        Identifier id = level.dimension().identifier();
        if (id.equals(Level.OVERWORLD.identifier())) {
            return "Overworld";
        }
        if (id.equals(Level.NETHER.identifier())) {
            return "the Nether";
        }
        if (id.equals(Level.END.identifier())) {
            return "the End";
        }
        return null;
    }

    private static GameType gameMode(Minecraft minecraft, LocalPlayer player) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            return null;
        }
        PlayerInfo info = connection.getPlayerInfo(player.getUUID());
        return info != null ? info.getGameMode() : null;
    }

    private static String prettyGameMode(GameType gameType) {
        if (gameType == GameType.SURVIVAL) {
            return "Survival";
        }
        if (gameType == GameType.CREATIVE) {
            return "Creative";
        }
        if (gameType == GameType.ADVENTURE) {
            return "Adventure";
        }
        if (gameType == GameType.SPECTATOR) {
            return "Spectator";
        }
        return "Unknown";
    }

    private static int onlinePlayers(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();
        return connection == null ? 0 : connection.getListedOnlinePlayers().size();
    }

    private static String players(int count) {
        return count == 1 ? "1 player" : count + " players";
    }
}
