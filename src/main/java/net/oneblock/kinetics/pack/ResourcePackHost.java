package net.oneblock.kinetics.pack;

import com.sun.net.httpserver.HttpServer;
import net.oneblock.economy.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ResourcePackHost implements Listener {
    private static final int PACK_FORMAT = 88;
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("oneblock-kinetics".getBytes(StandardCharsets.UTF_8));

    private final Plugin plugin;
    private byte[] zip;
    private byte[] sha1;
    private HttpServer server;
    private boolean enabled;
    private String url;
    private boolean required;

    public ResourcePackHost(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start(ConfigurationSection c) {
        stop();
        enabled = c == null || c.getBoolean("enabled", true);
        if (!enabled) {
            return;
        }
        try {
            zip = build();
            sha1 = MessageDigest.getInstance("SHA-1").digest(zip);
            Files.write(new File(plugin.getDataFolder(), "resourcepack.zip").toPath(), zip);
        } catch (IOException | NoSuchAlgorithmException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not build the resource pack", e);
            enabled = false;
            return;
        }
        required = c != null && c.getBoolean("required", false);
        String hash = HexFormat.of().formatHex(sha1);
        url = (c == null ? "http://localhost:8163/pack.zip" : c.getString("url", "http://localhost:8163/pack.zip")) + "?v=" + hash.substring(0, 8);

        if (c == null || c.getBoolean("self-host", true)) {
            int port = c == null ? 8163 : c.getInt("port", 8163);
            try {
                server = HttpServer.create(new InetSocketAddress(port), 0);
                server.createContext("/pack.zip", exchange -> {
                    exchange.getResponseHeaders().add("Content-Type", "application/zip");
                    exchange.sendResponseHeaders(200, zip.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(zip);
                    }
                });
                server.start();
                plugin.getLogger().info("Serving the resource pack on port " + port + " (players download it from " + url + ")");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not start the resource pack web server on port " + port
                        + ". Change resource-pack.port, or set self-host: false and upload resourcepack.zip somewhere.", e);
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            send(p);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public boolean enabled() {
        return enabled;
    }

    private void send(Player p) {
        if (enabled) {
            p.setResourcePack(PACK_ID, url, sha1,
                    Text.mm("<gold>This server uses custom models for its machines."), required);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        send(event.getPlayer());
    }

    private static byte[] build() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            put(out, "pack.mcmeta", "{\"pack\":{\"description\":\"OneBlock machines\",\"min_format\":" + PACK_FORMAT
                    + ",\"max_format\":" + PACK_FORMAT + "}}");
            for (String host : new String[]{"petrified_oak_slab", "waxed_oxidized_copper_chain"}) {
                put(out, "assets/minecraft/blockstates/" + host + ".json",
                        "{\"variants\":{\"\":{\"model\":\"oneblock:block/kinetic_host\"}}}");
            }
            put(out, "assets/oneblock/models/block/kinetic_host.json",
                    "{\"textures\":{\"particle\":\"" + Models.METAL + "\"}}");
            for (Map.Entry<String, Models.Model> m : Models.all().entrySet()) {
                put(out, "assets/oneblock/models/item/" + m.getKey() + ".json", Models.modelJson(m.getValue()));
                put(out, "assets/oneblock/items/" + m.getKey() + ".json", Models.itemJson(m.getKey(), m.getValue()));
            }
        }
        return bytes.toByteArray();
    }

    private static void put(ZipOutputStream out, String path, String content) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        entry.setTime(0);
        out.putNextEntry(entry);
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
}
