package io.github.rurichan.rhythmcraft;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.StringTextComponent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class BeatmaniaScreen extends Screen {

    private enum State {
        SONG_SELECT,
        GAMEPLAY,
        RESULTS,
        SETTINGS
    }

    public static class SongEntry {
        public String title = "Unknown Title";
        public String artist = "Unknown Artist";
        public List<BMSParser.BMSMetadata> charts = new ArrayList<>();
    }

    private State state = State.SONG_SELECT;
    private List<SongEntry> songs = new ArrayList<>();
    private int selectedSongIndex = 0;
    private int selectedDifficultyIndex = 0;
    private boolean focusDifficultyColumn = false;

    // Gameplay data
    private BMSParser.BMSChart activeChart;
    private Clip bgmClip;
    private long startTime;
    private int combo = 0;
    private int maxCombo = 0;
    private double health = 22.0; // Groove Gauge starts at 22%

    // Score counters
    private int pgreat = 0;
    private int great = 0;
    private int good = 0;
    private int bad = 0;
    private int poor = 0;
    private String lastJudgment = "";
    private int judgmentDisplayTicks = 0;

    // Lanes drawing config
    private int startX;
    private final int[] laneX = new int[8];
    private final int[] laneW = new int[8];
    private final boolean[] lanePressed = new boolean[8];

    // Key binding helper
    private int bindingLaneIndex = -1; // -1 means not binding

    public BeatmaniaScreen() {
        super(new StringTextComponent("Beatmania IIDX"));
        initLanesWidth();
    }

    private void initLanesWidth() {
        // Scratch is lane 7
        laneW[7] = 30;
        // Keys 1-7 are lanes 0-6
        for (int i = 0; i < 7; i++) {
            laneW[i] = 18;
        }
    }

    @Override
    protected void init() {
        super.init();
        scanSongs();

        // Recalculate lane X coordinates based on window size
        startX = (this.width - 172) / 2;
        laneX[7] = startX; // Scratch on the left
        for (int i = 0; i < 7; i++) {
            laneX[i] = startX + 34 + i * 20; // 18 width + 2 spacing
        }
    }

    private void scanSongs() {
        songs.clear();
        File songsDir = new File(Minecraft.getInstance().gameDirectory, "rhythmcraft/songs");
        if (!songsDir.exists()) {
            songsDir.mkdirs();
        }

        // Auto-generate test song folders if empty
        File[] folders = songsDir.listFiles(File::isDirectory);
        if (folders == null || folders.length == 0) {
            try {
                generateDemoSong(songsDir);
            } catch (Exception e) {
                RhythmCraft.LOGGER.error("Failed to generate demo song", e);
            }
            folders = songsDir.listFiles(File::isDirectory);
        }

        if (folders != null) {
            for (File f : folders) {
                File[] files = f.listFiles();
                if (files == null) continue;

                SongEntry song = new SongEntry();
                for (File file : files) {
                    if (file.getName().toLowerCase().endsWith(".bms") || file.getName().toLowerCase().endsWith(".bme")) {
                        BMSParser.BMSMetadata meta = BMSParser.readMetadata(file);
                        song.charts.add(meta);
                        // Use metadata of first chart as default song metadata
                        if (song.charts.size() == 1) {
                            song.title = meta.title;
                            song.artist = meta.artist;
                        }
                    }
                }

                if (!song.charts.isEmpty()) {
                    // Sort charts by difficulty level
                    song.charts.sort((c1, c2) -> Integer.compare(c1.playLevel, c2.playLevel));
                    songs.add(song);
                }
            }
        }
    }

    private void generateDemoSong(File songsDir) throws Exception {
        File demoDir = new File(songsDir, "demo_metronome");
        demoDir.mkdirs();

        // 1. Generate Demo BMS normal File (Level 3)
        File normalFile = new File(demoDir, "chart_normal.bms");
        try (FileWriter writer = new FileWriter(normalFile)) {
            writer.write("#TITLE Test Metronome\n");
            writer.write("#ARTIST RhythmCraft\n");
            writer.write("#DIFFICULTY 2\n");
            writer.write("#PLAYLEVEL 3\n");
            writer.write("#BPM 120\n\n");
            writer.write("#00111:01010101\n"); // Key 1 beat
            writer.write("#00116:01000100\n"); // Scratch beat
            writer.write("#00211:01010101\n");
            writer.write("#00216:01000100\n");
            writer.write("#00311:01010101\n");
            writer.write("#00316:01000100\n");
            writer.write("#00411:01010101\n");
            writer.write("#00416:01000100\n");
        }

        // 2. Generate Demo BMS another File (Level 8 - double note speed/density)
        File anotherFile = new File(demoDir, "chart_another.bms");
        try (FileWriter writer = new FileWriter(anotherFile)) {
            writer.write("#TITLE Test Metronome\n");
            writer.write("#ARTIST RhythmCraft\n");
            writer.write("#DIFFICULTY 4\n");
            writer.write("#PLAYLEVEL 8\n");
            writer.write("#BPM 120\n\n");
            writer.write("#00111:01010101\n");
            writer.write("#00112:00010001\n");
            writer.write("#00113:01000100\n");
            writer.write("#00116:01010101\n"); // Heavy scratch
            writer.write("#00211:01010101\n");
            writer.write("#00212:00010001\n");
            writer.write("#00213:01000100\n");
            writer.write("#00216:01010101\n");
            writer.write("#00311:01010101\n");
            writer.write("#00312:00010001\n");
            writer.write("#00313:01000100\n");
            writer.write("#00316:01010101\n");
            writer.write("#00411:01010101\n");
            writer.write("#00412:00010001\n");
            writer.write("#00413:01000100\n");
            writer.write("#00416:01010101\n");
        }

        // 3. Generate Demo Audio WAV (repeating click track synced to 120BPM)
        File wavFile = new File(demoDir, "bgm.wav");
        int sampleRate = 44100;
        double duration = 10.0; // 10 seconds
        int numSamples = (int) (duration * sampleRate);
        byte[] data = new byte[numSamples * 2];

        // 120BPM -> 0.5s per beat
        int samplesPerBeat = (int) (0.5 * sampleRate);
        for (int i = 0; i < numSamples; i++) {
            double time = (double) i / sampleRate;
            int offsetInBeat = i % samplesPerBeat;
            // Beep duration: 0.05 seconds (2205 samples)
            double frequency = (offsetInBeat < 2205) ? 440.0 : 0.0;
            double amplitude = Math.sin(2.0 * Math.PI * frequency * time) * 16384.0;
            short val = (short) amplitude;
            data[i * 2] = (byte) (val & 0xff);
            data[i * 2 + 1] = (byte) ((val >> 8) & 0xff);
        }

        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             AudioInputStream ais = new AudioInputStream(bais, format, numSamples)) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wavFile);
        }
    }

    private void startSong(File bmsFile) {
        try {
            activeChart = BMSParser.parse(bmsFile);
            combo = 0;
            maxCombo = 0;
            health = 22.0;
            pgreat = 0;
            great = 0;
            good = 0;
            bad = 0;
            poor = 0;
            lastJudgment = "";
            judgmentDisplayTicks = 0;

            // Load and Play BGM Clip
            if (activeChart.bgmFile.exists()) {
                if (activeChart.bgmFile.getName().toLowerCase().endsWith(".ogg")) {
                    bgmClip = loadOggToClip(activeChart.bgmFile);
                } else {
                    AudioInputStream audioIn = AudioSystem.getAudioInputStream(activeChart.bgmFile);
                    bgmClip = AudioSystem.getClip();
                    bgmClip.open(audioIn);
                }
                bgmClip.start();
            }

            startTime = System.currentTimeMillis();
            state = State.GAMEPLAY;
        } catch (Exception e) {
            RhythmCraft.LOGGER.error("Failed to load song chart " + bmsFile.getName(), e);
        }
    }

    private Clip loadOggToClip(File oggFile) throws Exception {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.IntBuffer channelsBuffer = stack.mallocInt(1);
            java.nio.IntBuffer sampleRateBuffer = stack.mallocInt(1);
            java.nio.ShortBuffer rawAudio = STBVorbis.stb_vorbis_decode_filename(
                oggFile.getAbsolutePath(), channelsBuffer, sampleRateBuffer
            );
            if (rawAudio == null) {
                throw new RuntimeException("Failed to decode OGG file: " + oggFile.getName());
            }

            int channels = channelsBuffer.get(0);
            int sampleRate = sampleRateBuffer.get(0);

            int numSamples = rawAudio.remaining();
            byte[] pcmData = new byte[numSamples * 2];
            for (int i = 0; i < numSamples; i++) {
                short val = rawAudio.get(i);
                pcmData[i * 2] = (byte) (val & 0xff);
                pcmData[i * 2 + 1] = (byte) ((val >> 8) & 0xff);
            }

            MemoryUtil.memFree(rawAudio);

            AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
            ByteArrayInputStream bais = new ByteArrayInputStream(pcmData);
            AudioInputStream ais = new AudioInputStream(bais, format, pcmData.length / format.getFrameSize());
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            return clip;
        }
    }

    private void stopAudio() {
        if (bgmClip != null) {
            bgmClip.stop();
            bgmClip.close();
            bgmClip = null;
        }
    }

    private long getPlayTime() {
        if (bgmClip != null && bgmClip.isOpen()) {
            return (bgmClip.getMicrosecondPosition() / 1000) - RhythmConfig.get().audioDelay;
        }
        return (System.currentTimeMillis() - startTime) - RhythmConfig.get().audioDelay;
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(matrixStack);
        super.render(matrixStack, mouseX, mouseY, partialTicks);

        switch (state) {
            case SONG_SELECT:
                renderSongSelect(matrixStack);
                break;
            case GAMEPLAY:
                renderGameplay(matrixStack);
                break;
            case RESULTS:
                renderResults(matrixStack);
                break;
            case SETTINGS:
                renderSettings(matrixStack);
                break;
        }
    }

    private void renderSongSelect(MatrixStack matrixStack) {
        drawCenteredString(matrixStack, this.font, "RhythmCraft - Song Selection", this.width / 2, 15, 0x00FFCC);

        if (songs.isEmpty()) {
            drawCenteredString(matrixStack, this.font, "No songs found in rhythmcraft/songs/", this.width / 2, 100, 0xFF0000);
            return;
        }

        // Draw Left Column - Songs List
        int startY = 45;
        this.font.drawShadow(matrixStack, "SONGS", 40, startY - 15, 0x00FFCC);
        for (int i = 0; i < songs.size(); i++) {
            SongEntry song = songs.get(i);
            int color = (i == selectedSongIndex) ? (focusDifficultyColumn ? 0x88FF88 : 0x00FF00) : 0xCCCCCC;
            String prefix = (i == selectedSongIndex) ? "> " : "  ";
            this.font.drawShadow(matrixStack, prefix + song.title, 40, startY + i * 15, color);
        }

        // Draw Right Column - Difficulty Charts List for Selected Song
        this.font.drawShadow(matrixStack, "DIFFICULTY / CHARTS", this.width / 2 + 10, startY - 15, 0x00FFCC);
        SongEntry currentSong = songs.get(selectedSongIndex);
        for (int i = 0; i < currentSong.charts.size(); i++) {
            BMSParser.BMSMetadata chartMeta = currentSong.charts.get(i);
            int color = (i == selectedDifficultyIndex && focusDifficultyColumn) ? 0x00FF00 : (i == selectedDifficultyIndex ? 0x88FF88 : 0xCCCCCC);
            String prefix = (i == selectedDifficultyIndex && focusDifficultyColumn) ? ">> " : (i == selectedDifficultyIndex ? "> " : "   ");
            this.font.drawShadow(matrixStack, prefix + chartMeta.getDifficultyLabel(), this.width / 2 + 10, startY + i * 15, color);
        }

        // Draw Selected Metadata
        if (selectedDifficultyIndex < currentSong.charts.size()) {
            BMSParser.BMSMetadata selectedMeta = currentSong.charts.get(selectedDifficultyIndex);
            String metaStr = String.format("Artist: %s  |  BPM: %.1f", selectedMeta.artist, selectedMeta.bpm);
            drawCenteredString(matrixStack, this.font, metaStr, this.width / 2, this.height - 60, 0x999999);
        }

        drawCenteredString(matrixStack, this.font, "Use Arrow Keys to navigate (LEFT/RIGHT to switch columns)", this.width / 2, this.height - 40, 0xCCCCCC);
        drawCenteredString(matrixStack, this.font, "Press ENTER to Play  |  Press TAB for Settings", this.width / 2, this.height - 25, 0x00FFCC);
    }

    private void renderGameplay(MatrixStack matrixStack) {
        long time = getPlayTime();

        // 1. Draw lanes container background
        fill(matrixStack, startX - 4, 0, startX + 176, this.height, 0x99111111);

        // 2. Draw receptor line
        int receptorY = this.height - 50;
        fill(matrixStack, startX - 4, receptorY, startX + 176, receptorY + 3, 0xFFFF00FF); // Magenta line

        // 3. Draw key pressed lane highlight and lane borders
        for (int i = 0; i < 8; i++) {
            int x = laneX[i];
            int w = laneW[i];
            // Border line
            fill(matrixStack, x - 1, 0, x, this.height, 0x33FFFFFF);

            // Pressed glow
            if (lanePressed[i]) {
                fill(matrixStack, x, 0, x + w, receptorY, 0x2200FFFF);
            }
        }
        fill(matrixStack, startX + 172, 0, startX + 173, this.height, 0x33FFFFFF);

        // 4. Draw falling notes
        float speed = RhythmConfig.get().scrollSpeed;
        boolean allNotesPassed = true;

        for (BMSParser.Note note : activeChart.notes) {
            if (note.hit) continue;

            long diffTime = note.timestamp - time;

            // Check if note is missed (POOR)
            if (diffTime < -150) {
                note.hit = true;
                triggerJudgment("POOR");
                continue;
            }

            allNotesPassed = false;

            // Skip notes that are too far in the future to show yet
            if (diffTime > 2000) continue;

            // Compute Y position
            int y = (int) (receptorY - (diffTime * speed * 0.15f));
            if (y > 0 && y < receptorY) {
                int x = laneX[note.lane];
                int w = laneW[note.lane];
                int color = getNoteColor(note.lane);
                fill(matrixStack, x, y - 3, x + w, y + 3, color);
            }
        }

        // If all notes are handled, transition to results screen
        if (allNotesPassed && (bgmClip == null || !bgmClip.isRunning())) {
            state = State.RESULTS;
            stopAudio();
        }

        // 5. Draw scoring / stats
        drawCenteredString(matrixStack, this.font, "COMBO: " + combo, this.width / 2, this.height / 2 - 20, 0xFFFFFF);

        if (judgmentDisplayTicks > 0) {
            int jColor = getJudgmentColor(lastJudgment);
            drawCenteredString(matrixStack, this.font, lastJudgment, this.width / 2, this.height / 2, jColor);
            judgmentDisplayTicks--;
        }

        // Health bar (Groove Gauge)
        int barWidth = 100;
        int barX = (this.width - barWidth) / 2;
        int barY = 15;
        fill(matrixStack, barX - 1, barY - 1, barX + barWidth + 1, barY + 9, 0xFF444444); // bg
        int healthColor = (health >= 80.0) ? 0xFF00FF00 : 0xFFFF0000; // Green if clear-state, otherwise red
        int filledWidth = (int) (barWidth * (health / 100.0));
        fill(matrixStack, barX, barY, barX + filledWidth, barY + 8, healthColor);

        drawCenteredString(matrixStack, this.font, String.format("GROOVE: %.1f%%", health), this.width / 2, barY + 12, healthColor);
    }

    private void renderResults(MatrixStack matrixStack) {
        drawCenteredString(matrixStack, this.font, "STAGE RESULTS", this.width / 2, 20, 0x00FFCC);

        int startY = 60;
        this.font.drawShadow(matrixStack, "PGREAT: " + pgreat, this.width / 2 - 50, startY, 0xFFFFCC);
        this.font.drawShadow(matrixStack, "GREAT:  " + great, this.width / 2 - 50, startY + 15, 0x00FFFF);
        this.font.drawShadow(matrixStack, "GOOD:   " + good, this.width / 2 - 50, startY + 30, 0x00FF00);
        this.font.drawShadow(matrixStack, "BAD:    " + bad, this.width / 2 - 50, startY + 45, 0xCC00FF);
        this.font.drawShadow(matrixStack, "POOR:   " + poor, this.width / 2 - 50, startY + 60, 0xFF0000);

        this.font.drawShadow(matrixStack, "MAX COMBO: " + maxCombo, this.width / 2 - 50, startY + 90, 0xFFCC00);

        boolean isClear = health >= 80.0;
        String clearStr = isClear ? "STAGE CLEAR" : "FAILED";
        int clearColor = isClear ? 0x00FF00 : 0xFF0000;
        drawCenteredString(matrixStack, this.font, clearStr, this.width / 2, startY + 120, clearColor);

        drawCenteredString(matrixStack, this.font, "Press ESC/ENTER to return to Menu", this.width / 2, this.height - 40, 0xCCCCCC);
    }

    private void renderSettings(MatrixStack matrixStack) {
        drawCenteredString(matrixStack, this.font, "SETTINGS & KEYBINDS", this.width / 2, 20, 0x00FFCC);

        int startY = 50;
        RhythmConfig config = RhythmConfig.get();

        // 1. Render Speed / Delay settings
        this.font.drawShadow(matrixStack, String.format("Speed: %.1f [UP/DOWN Arrow keys to change]", config.scrollSpeed), 40, startY, 0xFFFFFF);
        this.font.drawShadow(matrixStack, String.format("Delay: %d ms [LEFT/RIGHT Arrow keys to change]", config.audioDelay), 40, startY + 15, 0xFFFFFF);

        // 2. Render Keybinds listing
        this.font.drawShadow(matrixStack, "Click line to bind key:", 40, startY + 45, 0x00FFFF);

        for (int i = 0; i < 8; i++) {
            String label = (i == 7) ? "Scratch" : "Key " + (i + 1);
            int boundKey = config.keyBinds[i];
            String keyName = GLFW.glfwGetKeyName(boundKey, 0);
            if (keyName == null) {
                if (boundKey == GLFW.GLFW_KEY_SPACE) keyName = "SPACE";
                else if (boundKey == GLFW.GLFW_KEY_LEFT_SHIFT) keyName = "L_SHIFT";
                else if (boundKey == GLFW.GLFW_KEY_RIGHT_SHIFT) keyName = "R_SHIFT";
                else keyName = "Key " + boundKey;
            }

            int color = (i == bindingLaneIndex) ? 0xFFFF00 : 0xCCCCCC;
            String hoverMsg = (i == bindingLaneIndex) ? "PRESS ANY KEY..." : "Press Key " + (i + 1) + " to bind";
            this.font.drawShadow(matrixStack, String.format("%s: [%s]", label, keyName.toUpperCase()), 40, startY + 65 + i * 15, color);
            this.font.drawShadow(matrixStack, hoverMsg, 180, startY + 65 + i * 15, color);
        }

        drawCenteredString(matrixStack, this.font, "Press TAB to Save & Exit Settings", this.width / 2, this.height - 40, 0x00FFCC);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (state == State.SETTINGS) {
            // Keybinding mode
            if (bindingLaneIndex >= 0 && bindingLaneIndex < 8) {
                RhythmConfig.get().keyBinds[bindingLaneIndex] = keyCode;
                RhythmConfig.save();
                bindingLaneIndex = -1;
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_TAB) {
                state = State.SONG_SELECT;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_UP) {
                RhythmConfig.get().scrollSpeed = Math.min(10.0f, RhythmConfig.get().scrollSpeed + 0.1f);
                RhythmConfig.save();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_DOWN) {
                RhythmConfig.get().scrollSpeed = Math.max(1.0f, RhythmConfig.get().scrollSpeed - 0.1f);
                RhythmConfig.save();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                RhythmConfig.get().audioDelay += 5;
                RhythmConfig.save();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_LEFT) {
                RhythmConfig.get().audioDelay -= 5;
                RhythmConfig.save();
                return true;
            } else if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_8) {
                // Quick shortcut: press 1-8 keys to start binding lane 0-7
                bindingLaneIndex = keyCode - GLFW.GLFW_KEY_1;
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (state == State.GAMEPLAY) {
                stopAudio();
                state = State.SONG_SELECT;
            } else if (state == State.RESULTS) {
                state = State.SONG_SELECT;
            } else {
                // Exit screen
                this.onClose();
            }
            return true;
        }

        switch (state) {
            case SONG_SELECT:
                if (keyCode == GLFW.GLFW_KEY_UP) {
                    if (focusDifficultyColumn) {
                        if (selectedDifficultyIndex > 0) selectedDifficultyIndex--;
                    } else {
                        if (selectedSongIndex > 0) {
                            selectedSongIndex--;
                            selectedDifficultyIndex = 0; // Reset difficulty selection for new song
                        }
                    }
                    return true;
                } else if (keyCode == GLFW.GLFW_KEY_DOWN) {
                    if (focusDifficultyColumn) {
                        SongEntry currentSong = songs.get(selectedSongIndex);
                        if (selectedDifficultyIndex < currentSong.charts.size() - 1) selectedDifficultyIndex++;
                    } else {
                        if (selectedSongIndex < songs.size() - 1) {
                            selectedSongIndex++;
                            selectedDifficultyIndex = 0; // Reset difficulty selection for new song
                        }
                    }
                    return true;
                } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                    if (!focusDifficultyColumn && !songs.isEmpty() && songs.get(selectedSongIndex).charts.size() > 1) {
                        focusDifficultyColumn = true;
                        selectedDifficultyIndex = 0;
                    }
                    return true;
                } else if (keyCode == GLFW.GLFW_KEY_LEFT) {
                    if (focusDifficultyColumn) {
                        focusDifficultyColumn = false;
                    }
                    return true;
                } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    if (!songs.isEmpty()) {
                        SongEntry currentSong = songs.get(selectedSongIndex);
                        if (selectedDifficultyIndex < currentSong.charts.size()) {
                            startSong(currentSong.charts.get(selectedDifficultyIndex).file);
                        }
                    }
                    return true;
                } else if (keyCode == GLFW.GLFW_KEY_TAB) {
                    state = State.SETTINGS;
                    return true;
                }
                break;
            case GAMEPLAY:
                // Check gameplay key presses
                int lane = getLaneFromKey(keyCode);
                if (lane != -1) {
                    lanePressed[lane] = true;
                    handleGameplayPress(lane);
                    return true;
                }
                break;
            case RESULTS:
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    state = State.SONG_SELECT;
                    return true;
                }
                break;
        }

        // Return true to prevent MC keys from executing (except ESC)
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (state == State.GAMEPLAY) {
            int lane = getLaneFromKey(keyCode);
            if (lane != -1) {
                lanePressed[lane] = false;
                return true;
            }
        }
        return true;
    }

    private int getLaneFromKey(int keyCode) {
        RhythmConfig config = RhythmConfig.get();
        for (int i = 0; i < 8; i++) {
            if (config.keyBinds[i] == keyCode) return i;
        }
        return -1;
    }

    private void handleGameplayPress(int lane) {
        long time = getPlayTime();
        BMSParser.Note targetNote = null;
        long minDiff = Long.MAX_VALUE;

        // Find closest unhit note in this lane
        for (BMSParser.Note note : activeChart.notes) {
            if (note.hit || note.lane != lane) continue;

            long diff = Math.abs(note.timestamp - time);
            if (diff < minDiff) {
                minDiff = diff;
                targetNote = note;
            }
        }

        // Perform hit judgment
        if (targetNote != null && minDiff < 150) {
            targetNote.hit = true;

            if (minDiff < 20) {
                triggerJudgment("PGREAT");
            } else if (minDiff < 45) {
                triggerJudgment("GREAT");
            } else if (minDiff < 75) {
                triggerJudgment("GOOD");
            } else if (minDiff < 120) {
                triggerJudgment("BAD");
            } else {
                triggerJudgment("POOR");
            }
        }
    }

    private void triggerJudgment(String jud) {
        lastJudgment = jud;
        judgmentDisplayTicks = 15; // Displays for 15 frames

        switch (jud) {
            case "PGREAT":
                pgreat++;
                combo++;
                health = Math.min(100.0, health + 0.5);
                break;
            case "GREAT":
                great++;
                combo++;
                health = Math.min(100.0, health + 0.3);
                break;
            case "GOOD":
                good++;
                combo++;
                health = Math.min(100.0, health + 0.1);
                break;
            case "BAD":
                bad++;
                combo = 0;
                health = Math.max(0.0, health - 2.0);
                break;
            case "POOR":
                poor++;
                combo = 0;
                health = Math.max(0.0, health - 4.0);
                break;
        }

        if (combo > maxCombo) {
            maxCombo = combo;
        }
    }

    private int getNoteColor(int lane) {
        if (lane == 7) return 0xFFFF2222; // Scratch: Red
        if (lane == 0 || lane == 2 || lane == 4 || lane == 6) return 0xFFFFFFFF; // White notes
        return 0xFF4488FF; // Blue notes
    }

    private int getJudgmentColor(String jud) {
        switch (jud) {
            case "PGREAT": return 0xFFFFCC;
            case "GREAT": return 0x00FFFF;
            case "GOOD": return 0x00FF00;
            case "BAD": return 0xCC00FF;
            default: return 0xFF0000;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        stopAudio();
        super.onClose();
    }
}
