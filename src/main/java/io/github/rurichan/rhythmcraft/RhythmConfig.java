package io.github.rurichan.rhythmcraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class RhythmConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File configFile;

    public int[] keyBinds = new int[]{
        GLFW.GLFW_KEY_S,          // Key 1
        GLFW.GLFW_KEY_D,          // Key 2
        GLFW.GLFW_KEY_F,          // Key 3
        GLFW.GLFW_KEY_SPACE,      // Key 4 (Center)
        GLFW.GLFW_KEY_J,          // Key 5
        GLFW.GLFW_KEY_K,          // Key 6
        GLFW.GLFW_KEY_L,          // Key 7
        GLFW.GLFW_KEY_LEFT_SHIFT  // Scratch
    };

    public float scrollSpeed = 2.5f;
    public int audioDelay = 0; // ms

    private static RhythmConfig instance;

    public static RhythmConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        File dir = new File(Minecraft.getInstance().gameDirectory, "config");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        configFile = new File(dir, "rhythmcraft_client.json");

        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                instance = GSON.fromJson(reader, RhythmConfig.class);
                if (instance == null) {
                    instance = new RhythmConfig();
                }
            } catch (Exception e) {
                RhythmCraft.LOGGER.error("Failed to load RhythmCraft config, resetting to default", e);
                instance = new RhythmConfig();
            }
        } else {
            instance = new RhythmConfig();
            save();
        }
    }

    public static void save() {
        if (configFile == null) {
            File dir = new File(Minecraft.getInstance().gameDirectory, "config");
            configFile = new File(dir, "rhythmcraft_client.json");
        }
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(instance, writer);
        } catch (Exception e) {
            RhythmCraft.LOGGER.error("Failed to save RhythmCraft config", e);
        }
    }
}
