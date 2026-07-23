package dev.rbm72.weaponsplugin.boss.integration;

import dev.rbm72.weaponsplugin.WeaponsPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Fire-and-forget Discord webhook pings for boss kills and rare drops. Every call no-ops if
 * {@code discord.webhook-url} in config.yml is blank — no server hype feature should ever be able
 * to throw for admins who haven't set one up. The actual HTTP call always runs off the main thread.
 */
public final class DiscordNotifier {

    private DiscordNotifier() {
    }

    public static void kill(WeaponsPlugin plugin, String bossDisplayName, int nearbyPlayers) {
        send(plugin, "**" + bossDisplayName + "** has been defeated by " + nearbyPlayers + " player(s)! 💀");
    }

    public static void rareDrop(WeaponsPlugin plugin, String bossDisplayName, String itemName, double chance) {
        send(plugin, "✨ Rare drop! **" + bossDisplayName + "** dropped **" + itemName
                + "** (" + String.format("%.1f%%", chance * 100) + " chance)!");
    }

    private static void send(WeaponsPlugin plugin, String content) {
        String url = plugin.getConfig().getString("discord.webhook-url", "");
        if (url == null || url.isBlank()) {
            return;
        }
        String payload = "{\"content\":\"" + escape(content) + "\"}";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> post(plugin, url, payload));
    }

    private static void post(WeaponsPlugin plugin, String url, String payload) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(payload.getBytes(StandardCharsets.UTF_8));
            }
            connection.getResponseCode();
            connection.disconnect();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Discord webhook post failed", e);
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
