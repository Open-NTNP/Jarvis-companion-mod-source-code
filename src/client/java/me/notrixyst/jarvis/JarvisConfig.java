package me.notrixyst.jarvis;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class JarvisConfig {

    private static final JarvisConfig INSTANCE = new JarvisConfig();

    private static final String KEY_API = "api_key";
    private static final String KEY_LEARN = "learn_from_player";
    private static final String KEY_ENABLED = "jarvis_enabled";
    private static final String KEY_SORTING = "inventory_sorting_enabled";
    private static final String KEY_DURABILITY_HUD = "durability_hud_enabled";

    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("jarvis-client.properties");

    private String apiKey = "";
    private boolean learnFromPlayer = true;
    private boolean jarvisEnabled = true;
    private boolean inventorySortingEnabled = true;
    private boolean durabilityHudEnabled = true;

    private JarvisConfig() {
    }

    public static JarvisConfig getInstance() {
        return INSTANCE;
    }

    public void load() {
        if (!Files.exists(file)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
            apiKey = properties.getProperty(KEY_API, "");
            learnFromPlayer = Boolean.parseBoolean(properties.getProperty(KEY_LEARN, "true"));
            jarvisEnabled = Boolean.parseBoolean(properties.getProperty(KEY_ENABLED, "true"));
            inventorySortingEnabled = Boolean.parseBoolean(properties.getProperty(KEY_SORTING, "true"));
            durabilityHudEnabled = Boolean.parseBoolean(properties.getProperty(KEY_DURABILITY_HUD, "true"));
        } catch (IOException ignored) {
            // Keep defaults on IO issues.
        }
    }

    public void save() {
        Properties properties = new Properties();
        properties.setProperty(KEY_API, apiKey == null ? "" : apiKey);
        properties.setProperty(KEY_LEARN, String.valueOf(learnFromPlayer));
        properties.setProperty(KEY_ENABLED, String.valueOf(jarvisEnabled));
        properties.setProperty(KEY_SORTING, String.valueOf(inventorySortingEnabled));
        properties.setProperty(KEY_DURABILITY_HUD, String.valueOf(durabilityHudEnabled));

        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                properties.store(out, "JARVIS client configuration");
            }
        } catch (IOException ignored) {
            // Ignore IO issues to avoid breaking gameplay.
        }
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean isLearnFromPlayer() {
        return learnFromPlayer;
    }

    public void setLearnFromPlayer(boolean learnFromPlayer) {
        this.learnFromPlayer = learnFromPlayer;
    }

    public boolean isJarvisEnabled() {
        return jarvisEnabled;
    }

    public void setJarvisEnabled(boolean jarvisEnabled) {
        this.jarvisEnabled = jarvisEnabled;
    }

    public boolean isInventorySortingEnabled() {
        return inventorySortingEnabled;
    }

    public void setInventorySortingEnabled(boolean inventorySortingEnabled) {
        this.inventorySortingEnabled = inventorySortingEnabled;
    }

    public boolean isDurabilityHudEnabled() {
        return durabilityHudEnabled;
    }

    public void setDurabilityHudEnabled(boolean durabilityHudEnabled) {
        this.durabilityHudEnabled = durabilityHudEnabled;
    }
}
