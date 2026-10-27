package dev.discordpresence.rpc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public record RichPresence(
        String details,
        String state,
        String largeImageKey,
        String largeImageText,
        String smallImageKey,
        String smallImageText,
        long startTimestamp
) {
    private static final int MAX_TEXT_LENGTH = 128;

    public JsonObject toJson() {
        JsonObject activity = new JsonObject();
        activity.addProperty("type", 0);
        put(activity, "details", details);
        put(activity, "state", state);

        if (startTimestamp > 0L) {
            JsonObject timestamps = new JsonObject();
            timestamps.addProperty("start", startTimestamp);
            activity.add("timestamps", timestamps);
        }

        JsonObject assets = new JsonObject();
        boolean hasAssets = false;
        if (largeImageKey != null && !largeImageKey.isBlank()) {
            assets.addProperty("large_image", largeImageKey);
            hasAssets = true;
            if (largeImageText != null && !largeImageText.isBlank()) {
                assets.addProperty("large_text", clamp(largeImageText));
            }
        }
        if (smallImageKey != null && !smallImageKey.isBlank()) {
            assets.addProperty("small_image", smallImageKey);
            hasAssets = true;
            if (smallImageText != null && !smallImageText.isBlank()) {
                assets.addProperty("small_text", clamp(smallImageText));
            }
        }
        if (hasAssets) {
            activity.add("assets", assets);
        }
        return activity;
    }

    private static void put(JsonObject target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.addProperty(key, clamp(value));
        }
    }

    private static String clamp(String value) {
        return value.length() <= MAX_TEXT_LENGTH ? value : value.substring(0, MAX_TEXT_LENGTH);
    }
}
