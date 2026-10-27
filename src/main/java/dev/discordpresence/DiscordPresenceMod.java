package dev.discordpresence;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(DiscordPresenceMod.MODID)
public final class DiscordPresenceMod {
    public static final String MODID = "discordpresence";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DiscordPresenceMod() {
        LOGGER.debug("Discord Presence initialized");
    }
}
