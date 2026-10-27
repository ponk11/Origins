package com.mytheriamc.origins;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.bukkit.plugin.java.JavaPlugin;

final class DiscordWebhook {
    private final JavaPlugin plugin;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    DiscordWebhook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void send(String event, String message) {
        if (!plugin.getConfig().getBoolean("discord.enabled", false)
                || !plugin.getConfig().getBoolean("discord.events." + event, true)) return;
        String url = plugin.getConfig().getString("discord.webhook-url", "").trim();
        if (url.isEmpty() || !url.startsWith("https://discord.com/api/webhooks/")) return;
        String username = escape(plugin.getConfig().getString("discord.username", "Mytheria Origins"));
        String content = escape(message);
        String body = "{\"username\":\"" + username + "\",\"content\":\"" + content + "\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).thenAccept(response -> {
            if (response.statusCode() >= 300) plugin.getLogger().warning("Discord webhook returned HTTP " + response.statusCode());
        }).exceptionally(error -> {
            plugin.getLogger().warning("Discord webhook failed: " + error.getMessage());
            return null;
        });
    }

    private String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
