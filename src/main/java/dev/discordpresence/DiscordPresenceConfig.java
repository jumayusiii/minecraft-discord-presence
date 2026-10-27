package dev.discordpresence;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class DiscordPresenceConfig {
    private static final String APPLICATION_ID_RESOURCE = "/discordpresence/application.properties";
    private static final String APPLICATION_ID_PROPERTY = "applicationId";
    private static final String BUNDLED_APPLICATION_ID = loadApplicationId();

    public static final String DEFAULT_LARGE_IMAGE =
            "https://cdn.jsdelivr.net/gh/InventivetalentDev/minecraft-assets@1.21.5/assets/icons/icon_256x256.png";
    public static final String DEFAULT_SMALL_IMAGE =
            "https://cdn.jsdelivr.net/gh/PrismLauncher/PrismLauncher@094b61eb7deebb59edafff87fc292ced525eb3c1/program_info/org.prismlauncher.PrismLauncher_256.png";

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Whether the Discord presence is active.")
            .translation("discordpresence.configuration.enabled")
            .define("enabled", true);

    public static final ModConfigSpec.ConfigValue<String> LARGE_IMAGE = BUILDER.comment(new String[]{
            "Big picture of the presence: a full https image URL or the key of an",
            "uploaded Rich Presence asset. Blank falls back to the Minecraft logo."
    }).translation("discordpresence.configuration.largeImage").define("largeImage", DEFAULT_LARGE_IMAGE);

    public static final ModConfigSpec.ConfigValue<String> SMALL_IMAGE = BUILDER.comment(new String[]{
            "Small picture of the presence while Prism Launcher is running: a full https",
            "image URL or the key of an uploaded Rich Presence asset. Blank falls back to",
            "the Prism Launcher logo."
    }).translation("discordpresence.configuration.smallImage").define("smallImage", DEFAULT_SMALL_IMAGE);

    public static final ModConfigSpec.BooleanValue SHOW_DIMENSION = BUILDER
            .comment("Show the dimension you are in (Overworld, the Nether, the End) in the large image text.")
            .translation("discordpresence.configuration.showDimension")
            .define("showDimension", true);

    public static final ModConfigSpec.BooleanValue SHOW_WORLD_NAME = BUILDER
            .comment("Show the name of the singleplayer world or of the multiplayer server in the presence state.")
            .translation("discordpresence.configuration.showWorldName")
            .define("showWorldName", true);

    public static final ModConfigSpec.BooleanValue SHOW_PLAYER_COUNT = BUILDER
            .comment("Show how many players are online on the server in the presence state.")
            .translation("discordpresence.configuration.showPlayerCount")
            .define("showPlayerCount", true);

    public static final ModConfigSpec.BooleanValue SHOW_GAME_MODE = BUILDER
            .comment("Show your current game mode (Survival, Creative, Adventure, Spectator).")
            .translation("discordpresence.configuration.showGameMode")
            .define("showGameMode", true);

    public static final ModConfigSpec.BooleanValue SHOW_SESSION_TIME = BUILDER
            .comment("Show the elapsed session timer in the Discord presence.")
            .translation("discordpresence.configuration.showSessionTime")
            .define("showSessionTime", true);

    public static final ModConfigSpec.IntValue UPDATE_INTERVAL_SECONDS = BUILDER.comment(new String[]{
            "Minimum number of seconds between two presence updates.",
            "Keep this at 4 or higher, Discord rate limits activity updates."
    }).translation("discordpresence.configuration.updateIntervalSeconds")
            .defineInRange("updateIntervalSeconds", 5, 4, 120);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static final Settings DEFAULTS = new Settings(
            false,
            null,
            true,
            true,
            true,
            true,
            true,
            5,
            DEFAULT_LARGE_IMAGE,
            DEFAULT_SMALL_IMAGE
    );

    private DiscordPresenceConfig() {
    }

    public static Settings read() {
        try {
            return new Settings(
                    ENABLED.get(),
                    BUNDLED_APPLICATION_ID,
                    SHOW_DIMENSION.get(),
                    SHOW_WORLD_NAME.get(),
                    SHOW_PLAYER_COUNT.get(),
                    SHOW_GAME_MODE.get(),
                    SHOW_SESSION_TIME.get(),
                    UPDATE_INTERVAL_SECONDS.get(),
                    orDefault(LARGE_IMAGE.get(), DEFAULT_LARGE_IMAGE),
                    orDefault(SMALL_IMAGE.get(), DEFAULT_SMALL_IMAGE)
            );
        } catch (RuntimeException e) {
            return DEFAULTS;
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /**
     * Reads the application ID injected at build time. Keeping it in a resource
     * rather than in a config option means players never have to create a Discord
     * application, while the jar can still be rebuilt under another application.
     */
    private static String loadApplicationId() {
        try (InputStream input = DiscordPresenceConfig.class.getResourceAsStream(APPLICATION_ID_RESOURCE)) {
            if (input == null) {
                return null;
            }
            Properties properties = new Properties();
            properties.load(input);
            String applicationId = properties.getProperty(APPLICATION_ID_PROPERTY, "").trim();
            boolean valid = !applicationId.isEmpty()
                    && applicationId.chars().allMatch(Character::isDigit);
            return valid ? applicationId : null;
        } catch (IOException e) {
            return null;
        }
    }

    public record Settings(
            boolean enabled,
            String discordApplicationId,
            boolean showDimension,
            boolean showWorldName,
            boolean showPlayerCount,
            boolean showGameMode,
            boolean showSessionTime,
            int updateIntervalSeconds,
            String largeImage,
            String smallImage
    ) {
    }
}
