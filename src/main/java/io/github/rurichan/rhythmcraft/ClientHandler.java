package io.github.rurichan.rhythmcraft;

import net.minecraft.client.Minecraft;

public class ClientHandler {
    public static void openBeatmaniaScreen() {
        Minecraft.getInstance().setScreen(new BeatmaniaScreen());
    }
}
