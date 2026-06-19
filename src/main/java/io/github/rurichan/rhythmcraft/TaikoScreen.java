package io.github.rurichan.rhythmcraft;

import com.mojang.blaze3d.matrix.MatrixStack;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.StringTextComponent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public class TaikoScreen extends Screen {
    public enum State {
        SONG_SELECT,
        GAMEPLAY,
        RESULTS,
        SETTINGS
    }

    public static class SongEntry {
        public String title = "Unknown Title";
        public String artist = "Unknown Artist";
        public List<OsuParser.OsuChart> charts = new ArrayList<>();
    }

    private State state = State.SONG_SELECT;
    private final List<SongEntry> songs = new ArrayList<>();
    private int selectedSongIndex = 0;
    private int selectedDifficultyIndex = 0;
    private boolean focusDifficultyColumn = false;

    // Gameplay data
    private OsuParser.OsuChart activeChart;
    private Clip bgmClip;
    private long startTime;
    private int combo = 0;
    private int maxCombo = 0;
    private double health = 0.0; // Soul Gauge starts at 0%
    private int score = 0;

    // Score counters
    private int goodCount = 0;
    private int okCount = 0;
    private int badCount = 0;
    private String lastJudgment = "";
    private int judgmentDisplayTicks = 0;

    // Drum hit states (to render visual feedback)
    private final boolean[] drumPressed = new boolean[4]; // 0: Left Rim, 1: Left Center, 2: Right Center, 3: Right Rim

    // Key binding helper
    private int bindingKeyIndex = -1; // -1 means not binding

    public TaikoScreen() {
        super(new StringTextComponent("Taiko no Tatsujin"));
    }

    @Override
    protected void init() {
        super.init();
        scanSongs();
    }

    private void scanSongs() {
        songs.clear();

        // 1. Scan Local Directory
        File songsDir = new File(Minecraft.getInstance().gameDirectory, "rhythmcraft/taiko");
        if (!songsDir.exists()) {
            songsDir.mkdirs();
        }

        // Auto-generate metronome demo song if empty
        File demoDir = new File(songsDir, "demo_metronome");
        if (!demoDir.exists() || demoDir.list() == null || demoDir.list().length == 0) {
            try {
                generateDemoTaikoSong(demoDir);
            } catch (Exception e) {
                RhythmCraft.LOGGER.error("Failed to generate demo Taiko song", e);
            }
        }

        File[] folders = songsDir.listFiles(File::isDirectory);
        if (folders != null) {
            for (File folder : folders) {
                File[] files = folder.listFiles();
                if (files == null) continue;

                SongEntry song = new SongEntry();
                for (File file : files) {
                    if (file.getName().toLowerCase().endsWith(".osu")) {
                        try {
                            OsuParser.OsuChart chart = OsuParser.parseLocal(file);
                            song.charts.add(chart);
                        } catch (Exception e) {
                            RhythmCraft.LOGGER.error("Failed to parse local osu chart: " + file.getName(), e);
                        }
                    }
                }

                if (!song.charts.isEmpty()) {
                    // Sort charts by difficulty version length or name
                    song.charts.sort((c1, c2) -> c1.version.compareToIgnoreCase(c2.version));
                    song.title = song.charts.get(0).title;
                    song.artist = song.charts.get(0).artist;
                    songs.add(song);
                }
            }
        }

        // 2. Scan Resource Packs
        try {
            Collection<ResourceLocation> resources = Minecraft.getInstance().getResourceManager().listResources("taiko", (filename) -> filename.endsWith(".osu"));
            java.util.Map<String, List<ResourceLocation>> groups = new java.util.HashMap<>();
            for (ResourceLocation res : resources) {
                String path = res.getPath();
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash != -1) {
                    String folder = path.substring(0, lastSlash);
                    groups.computeIfAbsent(folder, k -> new ArrayList<>()).add(res);
                }
            }

            for (java.util.Map.Entry<String, List<ResourceLocation>> entry : groups.entrySet()) {
                SongEntry song = new SongEntry();
                for (ResourceLocation loc : entry.getValue()) {
                    try {
                        OsuParser.OsuChart chart = OsuParser.parseResource(loc);
                        song.charts.add(chart);
                    } catch (Exception e) {
                        RhythmCraft.LOGGER.error("Failed to parse resource pack osu chart: " + loc.toString(), e);
                    }
                }

                if (!song.charts.isEmpty()) {
                    // Deduplicate
                    boolean duplicate = false;
                    for (SongEntry existing : songs) {
                        if (existing.title.equalsIgnoreCase(song.charts.get(0).title) && existing.artist.equalsIgnoreCase(song.charts.get(0).artist)) {
                            duplicate = true;
                            // Add charts to existing
                            for (OsuParser.OsuChart c : song.charts) {
                                boolean chartDuplicate = false;
                                for (OsuParser.OsuChart ec : existing.charts) {
                                    if (ec.version.equalsIgnoreCase(c.version)) {
                                        chartDuplicate = true;
                                        break;
                                    }
                                }
                                if (!chartDuplicate) existing.charts.add(c);
                            }
                            existing.charts.sort((c1, c2) -> c1.version.compareToIgnoreCase(c2.version));
                            break;
                        }
                    }
                    if (!duplicate) {
                        song.charts.sort((c1, c2) -> c1.version.compareToIgnoreCase(c2.version));
                        song.title = song.charts.get(0).title;
                        song.artist = song.charts.get(0).artist;
                        songs.add(song);
                    }
                }
            }
        } catch (Exception e) {
            RhythmCraft.LOGGER.error("Failed to scan resource pack osu charts", e);
        }
    }

    private void generateDemoTaikoSong(File demoDir) throws Exception {
        demoDir.mkdirs();

        // 1. Generate Demo OSU File
        File osuFile = new File(demoDir, "chart_taiko.osu");
        if (!osuFile.exists()) {
            try (java.io.FileWriter writer = new java.io.FileWriter(osuFile)) {
                writer.write("[General]\n");
                writer.write("AudioFilename: bgm.wav\n");
                writer.write("Mode: 1\n\n");
                writer.write("[Metadata]\n");
                writer.write("Title: Test Metronome (Taiko)\n");
                writer.write("Artist: RhythmCraft\n");
                writer.write("Creator: RhythmCraft\n");
                writer.write("Version: Taiko Oni\n\n");
                writer.write("[Difficulty]\n");
                writer.write("SliderMultiplier: 1.4\n\n");
                writer.write("[TimingPoints]\n");
                writer.write("0,500,4,2,1,60,1,0\n\n");
                writer.write("[HitObjects]\n");
                writer.write("256,192,1000,1,0,0:0:0:0:\n");
                writer.write("256,192,1500,1,2,0:0:0:0:\n");
                writer.write("256,192,2000,1,0,0:0:0:0:\n");
                writer.write("256,192,2500,1,2,0:0:0:0:\n");
                writer.write("256,192,3000,1,0,0:0:0:0:\n");
                writer.write("256,192,3500,1,2,0:0:0:0:\n");
                writer.write("256,192,4000,1,4,0:0:0:0:\n");
                writer.write("256,192,4500,1,6,0:0:0:0:\n");
                writer.write("256,192,5000,2,0,1,200\n");
                writer.write("256,192,6000,8,0,7000\n");
            }
        }

        // 2. Generate Demo BGM WAV if not exists
        File wavFile = new File(demoDir, "bgm.wav");
        if (!wavFile.exists()) {
            int sampleRate = 44100;
            double duration = 10.0;
            int numSamples = (int) (duration * sampleRate);
            byte[] data = new byte[numSamples * 2];

            int samplesPerBeat = (int) (0.5 * sampleRate);
            for (int i = 0; i < numSamples; i++) {
                double time = (double) i / sampleRate;
                int offsetInBeat = i % samplesPerBeat;
                double frequency = (offsetInBeat < 2205) ? 440.0 : 0.0;
                double amplitude = Math.sin(2.0 * Math.PI * frequency * time) * 16384.0;
                short val = (short) amplitude;
                data[i * 2] = (byte) (val & 0xff);
                data[i * 2 + 1] = (byte) ((val >> 8) & 0xff);
            }

            AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
                 AudioInputStream ais = new AudioInputStream(bais, format, numSamples)) {
                AudioSystem.write(ais, javax.sound.sampled.AudioFileFormat.Type.WAVE, wavFile);
            }
        }
    }

    private void startSong(OsuParser.OsuChart chart) {
        try {
            activeChart = chart;
            combo = 0;
            maxCombo = 0;
            health = 0.0;
            score = 0;
            goodCount = 0;
            okCount = 0;
            badCount = 0;
            lastJudgment = "";
            judgmentDisplayTicks = 0;

            // Reset notes hit states
            for (OsuParser.OsuNote note : activeChart.notes) {
                note.hit = false;
                note.hitLeft = false;
                note.hitRight = false;
                note.hitCount = 0;
                if (note.tickHit != null) {
                    java.util.Arrays.fill(note.tickHit, false);
                }
            }

            // Load and Play Audio
            if (chart.localFile != null) {
                File audioFile = new File(chart.localFile.getParentFile(), chart.audioFilename);
                if (audioFile.exists()) {
                    if (audioFile.getName().toLowerCase().endsWith(".ogg")) {
                        try (FileInputStream fis = new FileInputStream(audioFile)) {
                            bgmClip = loadOggToClipFromStream(fis);
                        }
                    } else if (audioFile.getName().toLowerCase().endsWith(".mp3")) {
                        try (FileInputStream fis = new FileInputStream(audioFile)) {
                            bgmClip = Mp3Decoder.loadMp3ToClip(fis);
                        }
                    } else {
                        AudioInputStream audioIn = AudioSystem.getAudioInputStream(audioFile);
                        bgmClip = AudioSystem.getClip();
                        bgmClip.open(audioIn);
                    }
                    bgmClip.start();
                }
            } else if (chart.resourceLocation != null) {
                ResourceLocation audioLoc = getAudioResourceLocation(chart);
                if (audioLoc != null) {
                    IResource res = Minecraft.getInstance().getResourceManager().getResource(audioLoc);
                    if (audioLoc.getPath().toLowerCase().endsWith(".ogg")) {
                        bgmClip = loadOggToClipFromStream(res.getInputStream());
                    } else if (audioLoc.getPath().toLowerCase().endsWith(".mp3")) {
                        bgmClip = Mp3Decoder.loadMp3ToClip(res.getInputStream());
                    } else {
                        AudioInputStream audioIn = AudioSystem.getAudioInputStream(res.getInputStream());
                        bgmClip = AudioSystem.getClip();
                        bgmClip.open(audioIn);
                    }
                    bgmClip.start();
                }
            }

            startTime = System.currentTimeMillis();
            state = State.GAMEPLAY;
        } catch (Exception e) {
            RhythmCraft.LOGGER.error("Failed to start song", e);
        }
    }

    private ResourceLocation getAudioResourceLocation(OsuParser.OsuChart chart) {
        if (chart.resourceLocation == null) return null;
        String path = chart.resourceLocation.getPath();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash != -1) {
            String folder = path.substring(0, lastSlash);
            return new ResourceLocation(chart.resourceLocation.getNamespace(), folder + "/" + chart.audioFilename);
        }
        return null;
    }

    private Clip loadOggToClipFromStream(InputStream is) throws Exception {
        ByteBuffer vorbisBuffer = readInputStreamToDirectBuffer(is);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.IntBuffer channelsBuffer = stack.mallocInt(1);
            java.nio.IntBuffer sampleRateBuffer = stack.mallocInt(1);
            java.nio.ShortBuffer rawAudio = STBVorbis.stb_vorbis_decode_memory(
                vorbisBuffer, channelsBuffer, sampleRateBuffer
            );
            if (rawAudio == null) {
                MemoryUtil.memFree(vorbisBuffer);
                throw new RuntimeException("Failed to decode OGG memory stream");
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
            MemoryUtil.memFree(vorbisBuffer);

            AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
            ByteArrayInputStream bais = new ByteArrayInputStream(pcmData);
            AudioInputStream ais = new AudioInputStream(bais, format, pcmData.length / format.getFrameSize());
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            return clip;
        }
    }

    private static ByteBuffer readInputStreamToDirectBuffer(InputStream is) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        byte[] bytes = baos.toByteArray();
        ByteBuffer directBuf = MemoryUtil.memAlloc(bytes.length);
        directBuf.put(bytes);
        directBuf.flip();
        return directBuf;
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
            return (bgmClip.getMicrosecondPosition() / 1000) - RhythmConfig.get().taikoAudioDelay;
        }
        return (System.currentTimeMillis() - startTime) - RhythmConfig.get().taikoAudioDelay;
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        fill(matrixStack, 0, 0, this.width, this.height, 0xFF0D0D0D); // Solid dark premium background
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
        drawCenteredString(matrixStack, this.font, "RhythmCraft - Taiko no Tatsujin", this.width / 2, 15, 0xFF5555);

        if (songs.isEmpty()) {
            drawCenteredString(matrixStack, this.font, "No osu!taiko songs found in rhythmcraft/taiko/ or active Resource Packs!", this.width / 2, 100, 0xFF0000);
            return;
        }

        // Draw Left Column - Songs List
        int startY = 45;
        this.font.drawShadow(matrixStack, "SONGS", 40, startY - 15, 0xFF5555);
        for (int i = 0; i < songs.size(); i++) {
            SongEntry song = songs.get(i);
            int color = (i == selectedSongIndex) ? (focusDifficultyColumn ? 0xFFAAAA : 0xFF3333) : 0xCCCCCC;
            String prefix = (i == selectedSongIndex) ? "> " : "  ";
            this.font.drawShadow(matrixStack, prefix + song.title, 40, startY + i * 15, color);
        }

        // Draw Right Column - Difficulty Charts List for Selected Song
        this.font.drawShadow(matrixStack, "DIFFICULTY / CHARTS", this.width / 2 + 10, startY - 15, 0xFF5555);
        SongEntry currentSong = songs.get(selectedSongIndex);
        for (int i = 0; i < currentSong.charts.size(); i++) {
            OsuParser.OsuChart chart = currentSong.charts.get(i);
            int color = (i == selectedDifficultyIndex && focusDifficultyColumn) ? 0xFF3333 : (i == selectedDifficultyIndex ? 0xFFAAAA : 0xCCCCCC);
            String prefix = (i == selectedDifficultyIndex && focusDifficultyColumn) ? ">> " : (i == selectedDifficultyIndex ? "> " : "   ");
            this.font.drawShadow(matrixStack, prefix + chart.version, this.width / 2 + 10, startY + i * 15, color);
        }

        // Draw Selected Metadata
        if (selectedDifficultyIndex < currentSong.charts.size()) {
            OsuParser.OsuChart selectedChart = currentSong.charts.get(selectedDifficultyIndex);
            String metaStr = String.format("Artist: %s  |  Notes: %d", selectedChart.artist, selectedChart.notes.size());
            drawCenteredString(matrixStack, this.font, metaStr, this.width / 2, this.height - 60, 0x999999);
        }

        drawCenteredString(matrixStack, this.font, "Use Arrow Keys to navigate (LEFT/RIGHT to switch columns)", this.width / 2, this.height - 40, 0xCCCCCC);
        drawCenteredString(matrixStack, this.font, "Press ENTER to Play  |  Press TAB for Settings", this.width / 2, this.height - 25, 0xFF5555);
    }

    private void renderGameplay(MatrixStack matrixStack) {
        long time = getPlayTime();

        // 1. Draw horizontal playfield track
        int trackY = 100;
        int trackH = 40;
        fill(matrixStack, 0, trackY, this.width, trackY + trackH, 0xAA222222);

        // 2. Draw target hit ring (white hollow circle at X = 80, Y = 120)
        boolean hasFlash = drumPressed[0] || drumPressed[1] || drumPressed[2] || drumPressed[3];
        int ringColor = hasFlash ? 0xFFFFFF00 : 0xFFFFFFFF;
        drawHollowCircle(matrixStack, 80, 120, 10, 2, ringColor);

        // 3. Draw scrolling notes
        float speed = RhythmConfig.get().taikoScrollSpeed;
        boolean allNotesPassed = true;

        for (OsuParser.OsuNote note : activeChart.notes) {
            long diffTime = note.time - time;

            // Miss logic: note passes the hit window (150ms)
            if (!note.hit && diffTime < -150) {
                note.hit = true;
                if (note.type != OsuParser.NoteType.DRUMROLL && note.type != OsuParser.NoteType.BALLOON) {
                    triggerJudgment("不可");
                }
                continue;
            }

            if (!note.hit) {
                allNotesPassed = false;
            }

            // Skip drawing if too far in the future or past
            if (diffTime > 2500) continue;
            if (diffTime < -150) continue;

            int x = (int) (80 + diffTime * speed * 0.15f);

            if (x > 80 && x < this.width) {
                if (note.type == OsuParser.NoteType.DON) {
                    drawFilledCircle(matrixStack, x, 120, 8, 0xFFFF3333); // Red
                } else if (note.type == OsuParser.NoteType.KATSU) {
                    drawFilledCircle(matrixStack, x, 120, 8, 0xFF3399FF); // Blue
                } else if (note.type == OsuParser.NoteType.BIG_DON) {
                    drawFilledCircle(matrixStack, x, 120, 12, 0xFFFF3333); // Large Red
                } else if (note.type == OsuParser.NoteType.BIG_KATSU) {
                    drawFilledCircle(matrixStack, x, 120, 12, 0xFF3399FF); // Large Blue
                } else if (note.type == OsuParser.NoteType.DRUMROLL) {
                    // Render Yellow Drumroll bar
                    int endX = (int) (80 + (note.endTime - time) * speed * 0.15f);
                    fill(matrixStack, x, 112, endX, 128, 0xFFFFCC00);
                    drawFilledCircle(matrixStack, x, 120, 8, 0xFFFFCC00);
                    drawFilledCircle(matrixStack, endX, 120, 8, 0xFFFFCC00);

                    // Render ticks
                    for (int i = 0; i < note.tickTimes.size(); i++) {
                        if (note.tickHit[i]) continue;
                        int tickX = (int) (80 + (note.tickTimes.get(i) - time) * speed * 0.15f);
                        if (tickX > x && tickX < endX) {
                            fill(matrixStack, tickX - 1, 119, tickX + 1, 121, 0xFFFFFFFF);
                        }
                    }
                } else if (note.type == OsuParser.NoteType.BALLOON) {
                    // Render Purple Balloon circle and text
                    drawFilledCircle(matrixStack, x, 120, 12, 0xFFCC33FF);
                    this.font.drawShadow(matrixStack, String.valueOf(note.requiredHits - note.hitCount), x - 4, 116, 0xFFFFFF);
                }
            }
        }

        // Auto transition to results when song ends
        if (allNotesPassed && (bgmClip == null || !bgmClip.isRunning())) {
            state = State.RESULTS;
            stopAudio();
        }

        // 4. Draw Soul Gauge (魂) at the top
        int barW = this.width - 80;
        int barX = 40;
        int barY = 30;
        fill(matrixStack, barX - 1, barY - 1, barX + barW + 1, barY + 9, 0xFF444444); // border/bg

        // Clear threshold tick line at 70%
        int clearTickX = barX + (int) (barW * 0.7);
        fill(matrixStack, clearTickX - 1, barY - 3, clearTickX + 1, barY + 11, 0xFFFFFFFF);

        // Filled progress
        int fillW = (int) (barW * (health / 100.0));
        int healthColor = (health >= 70.0) ? 0xFFFFD700 : 0xFFFF5500; // Gold if clear-state, otherwise orange/red
        fill(matrixStack, barX, barY, barX + fillW, barY + 8, healthColor);

        // Draw "魂" label
        this.font.drawShadow(matrixStack, "魂", this.width - 32, barY, health >= 70.0 ? 0xFFFFD700 : 0xFFFA8072);

        // 5. Draw Combo & Score & Judgment text
        drawCenteredString(matrixStack, this.font, String.format("SCORE: %07d", score), this.width / 2, 50, 0xFFFFFF);
        drawCenteredString(matrixStack, this.font, "COMBO: " + combo, this.width / 2, 70, 0xFFFFFF);

        if (judgmentDisplayTicks > 0) {
            int jColor = lastJudgment.equals("良") ? 0xFFFF3333 : (lastJudgment.equals("可") ? 0xFFFFFF : 0xFF3399FF);
            drawCenteredString(matrixStack, this.font, lastJudgment, this.width / 2, 150, jColor);
            judgmentDisplayTicks--;
        }

        // 6. Draw Drum visual feedback panel at the bottom
        int drumX = this.width / 2;
        int drumY = this.height - 40;

        // Button dimensions
        int btnW = 30;
        int btnH = 20;

        // Colors
        int leftRimColor = drumPressed[0] ? 0xFF3399FF : 0x553399FF;
        int leftCtrColor = drumPressed[1] ? 0xFFFF3333 : 0x55FF3333;
        int rightCtrColor = drumPressed[2] ? 0xFFFF3333 : 0x55FF3333;
        int rightRimColor = drumPressed[3] ? 0xFF3399FF : 0x553399FF;

        // Draw Buttons
        fill(matrixStack, drumX - 60, drumY, drumX - 30, drumY + btnH, leftRimColor);
        fill(matrixStack, drumX - 30, drumY, drumX, drumY + btnH, leftCtrColor);
        fill(matrixStack, drumX, drumY, drumX + 30, drumY + btnH, rightCtrColor);
        fill(matrixStack, drumX + 30, drumY, drumX + 60, drumY + btnH, rightRimColor);

        // Border outline
        hollowRect(matrixStack, drumX - 60, drumY, drumX + 60, drumY + btnH, 0xFFFFFFFF);
        fill(matrixStack, drumX - 30, drumY, drumX - 29, drumY + btnH, 0xFFFFFFFF);
        fill(matrixStack, drumX, drumY, drumX + 1, drumY + btnH, 0xFFFFFFFF);
        fill(matrixStack, drumX + 30, drumY, drumX + 31, drumY + btnH, 0xFFFFFFFF);

        // Labels
        RhythmConfig cfg = RhythmConfig.get();
        this.font.drawShadow(matrixStack, getKeyLabel(cfg.taikoKeyBinds[0]), drumX - 50, drumY + 6, 0xFFFFFF);
        this.font.drawShadow(matrixStack, getKeyLabel(cfg.taikoKeyBinds[1]), drumX - 20, drumY + 6, 0xFFFFFF);
        this.font.drawShadow(matrixStack, getKeyLabel(cfg.taikoKeyBinds[2]), drumX + 10, drumY + 6, 0xFFFFFF);
        this.font.drawShadow(matrixStack, getKeyLabel(cfg.taikoKeyBinds[3]), drumX + 40, drumY + 6, 0xFFFFFF);
    }

    private void renderResults(MatrixStack matrixStack) {
        drawCenteredString(matrixStack, this.font, "STAGE RESULTS", this.width / 2, 20, 0xFF5555);

        int startY = 50;
        this.font.drawShadow(matrixStack, "良 (GOOD): " + goodCount, this.width / 2 - 50, startY, 0xFF3333);
        this.font.drawShadow(matrixStack, "可 (OK):   " + okCount, this.width / 2 - 50, startY + 15, 0xFFFFFF);
        this.font.drawShadow(matrixStack, "不可 (BAD): " + badCount, this.width / 2 - 50, startY + 30, 0x3399FF);
        this.font.drawShadow(matrixStack, "MAX COMBO: " + maxCombo, this.width / 2 - 50, startY + 50, 0xFFFF33);
        this.font.drawShadow(matrixStack, "TOTAL SCORE: " + score, this.width / 2 - 50, startY + 65, 0xFFFFFF);

        boolean isClear = health >= 70.0;
        String clearStr = isClear ? "CLEAR SUCCESS!" : "FAILED";
        int clearColor = isClear ? 0xFFFFD700 : 0xFF3399FF;
        drawCenteredString(matrixStack, this.font, clearStr, this.width / 2, startY + 100, clearColor);

        drawCenteredString(matrixStack, this.font, "Press ESC/ENTER to return to Menu", this.width / 2, this.height - 40, 0xCCCCCC);
    }

    private void renderSettings(MatrixStack matrixStack) {
        drawCenteredString(matrixStack, this.font, "SETTINGS & KEYBINDS", this.width / 2, 20, 0xFF5555);

        int startY = 50;
        RhythmConfig config = RhythmConfig.get();

        this.font.drawShadow(matrixStack, String.format("Speed: %.1f [UP/DOWN Arrow keys to change]", config.taikoScrollSpeed), 40, startY, 0xFFFFFF);
        this.font.drawShadow(matrixStack, String.format("Delay: %d ms [LEFT/RIGHT Arrow keys to change]", config.taikoAudioDelay), 40, startY + 15, 0xFFFFFF);

        this.font.drawShadow(matrixStack, "Click line / Press 1-4 to bind key:", 40, startY + 45, 0xFF5555);

        String[] labels = new String[]{"Left Rim (Rim/Blue)", "Left Drum (Don/Red)", "Right Drum (Don/Red)", "Right Rim (Rim/Blue)"};
        for (int i = 0; i < 4; i++) {
            int boundKey = config.taikoKeyBinds[i];
            String keyName = getKeyLabel(boundKey);
            int color = (i == bindingKeyIndex) ? 0xFFFF00 : 0xCCCCCC;
            String hoverMsg = (i == bindingKeyIndex) ? "PRESS ANY KEY..." : "Press Key " + (i + 1) + " to bind";

            this.font.drawShadow(matrixStack, String.format("%s: [%s]", labels[i], keyName), 40, startY + 65 + i * 15, color);
            this.font.drawShadow(matrixStack, hoverMsg, 200, startY + 65 + i * 15, color);
        }

        drawCenteredString(matrixStack, this.font, "Press TAB to Save & Exit Settings", this.width / 2, this.height - 40, 0xFF5555);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (state == State.SETTINGS) {
            // Keybinding
            if (bindingKeyIndex >= 0 && bindingKeyIndex < 4) {
                RhythmConfig.get().taikoKeyBinds[bindingKeyIndex] = keyCode;
                RhythmConfig.save();
                bindingKeyIndex = -1;
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_TAB) {
                state = State.SONG_SELECT;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_UP) {
                RhythmConfig.get().taikoScrollSpeed = Math.min(10.0f, RhythmConfig.get().taikoScrollSpeed + 0.1f);
                RhythmConfig.save();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_DOWN) {
                RhythmConfig.get().taikoScrollSpeed = Math.max(0.5f, RhythmConfig.get().taikoScrollSpeed - 0.1f);
                RhythmConfig.save();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                RhythmConfig.get().taikoAudioDelay += 5;
                RhythmConfig.save();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_LEFT) {
                RhythmConfig.get().taikoAudioDelay -= 5;
                RhythmConfig.save();
                return true;
            } else if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_4) {
                bindingKeyIndex = keyCode - GLFW.GLFW_KEY_1;
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
                            selectedDifficultyIndex = 0;
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
                            selectedDifficultyIndex = 0;
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
                            startSong(currentSong.charts.get(selectedDifficultyIndex));
                        }
                    }
                    return true;
                } else if (keyCode == GLFW.GLFW_KEY_TAB) {
                    state = State.SETTINGS;
                    return true;
                }
                break;
            case GAMEPLAY:
                int index = getTaikoIndexFromKey(keyCode);
                if (index != -1) {
                    drumPressed[index] = true;
                    handleTaikoPress(index);
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

        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (state == State.GAMEPLAY) {
            int index = getTaikoIndexFromKey(keyCode);
            if (index != -1) {
                drumPressed[index] = false;
                return true;
            }
        }
        return true;
    }

    private int getTaikoIndexFromKey(int keyCode) {
        RhythmConfig config = RhythmConfig.get();
        for (int i = 0; i < 4; i++) {
            if (config.taikoKeyBinds[i] == keyCode) return i;
        }
        return -1;
    }

    private void handleTaikoPress(int index) {
        boolean isKatsu = (index == 0 || index == 3);
        long time = getPlayTime();

        // Plays native SoundEvents based on hit type
        if (isKatsu) {
            Minecraft.getInstance().getSoundManager().play(SimpleSound.forUI(SoundEvents.NOTE_BLOCK_SNARE, 1.2f));
        } else {
            Minecraft.getInstance().getSoundManager().play(SimpleSound.forUI(SoundEvents.NOTE_BLOCK_BASEDRUM, 1.0f));
        }

        // Process Note judgments
        OsuParser.OsuNote targetNote = null;
        long minDiff = Long.MAX_VALUE;

        for (OsuParser.OsuNote note : activeChart.notes) {
            if (note.hit) continue;

            // Find closest active note within range
            long diff = Math.abs(note.time - time);
            if (diff < minDiff) {
                minDiff = diff;
                targetNote = note;
            }
        }

        if (targetNote != null && minDiff < 150) {
            if (targetNote.type == OsuParser.NoteType.DRUMROLL) {
                // Drumroll tick hitting
                for (int i = 0; i < targetNote.tickTimes.size(); i++) {
                    if (targetNote.tickHit[i]) continue;
                    long tickDiff = Math.abs(targetNote.tickTimes.get(i) - time);
                    if (tickDiff < 70) {
                        targetNote.tickHit[i] = true;
                        score += 100;
                        combo++;
                        if (combo > maxCombo) maxCombo = combo;
                        break;
                    }
                }
            } else if (targetNote.type == OsuParser.NoteType.BALLOON) {
                // Spinner/Balloon hit
                if (!isKatsu) { // Only Don hits spinners
                    targetNote.hitCount++;
                    score += 50;
                    if (targetNote.hitCount >= targetNote.requiredHits) {
                        targetNote.hit = true;
                        score += 1000;
                        combo++;
                        if (combo > maxCombo) maxCombo = combo;
                        Minecraft.getInstance().getSoundManager().play(SimpleSound.forUI(SoundEvents.PLAYER_LEVELUP, 1.5f));
                    }
                }
            } else {
                // Standard Don/Katsu circle note
                boolean noteIsKatsu = (targetNote.type == OsuParser.NoteType.KATSU || targetNote.type == OsuParser.NoteType.BIG_KATSU);
                if (isKatsu == noteIsKatsu) {
                    targetNote.hit = true;
                    if (minDiff < 40) {
                        triggerJudgment("良");
                    } else if (minDiff < 90) {
                        triggerJudgment("可");
                    } else {
                        triggerJudgment("不可");
                    }
                } else {
                    // Wrong key pressed
                    targetNote.hit = true;
                    triggerJudgment("不可");
                }
            }
        }
    }

    private void triggerJudgment(String jud) {
        lastJudgment = jud;
        judgmentDisplayTicks = 15;

        switch (jud) {
            case "良":
                goodCount++;
                combo++;
                score += 300;
                health = Math.min(100.0, health + 1.0);
                break;
            case "可":
                okCount++;
                combo++;
                score += 150;
                health = Math.min(100.0, health + 0.4);
                break;
            case "不可":
                badCount++;
                combo = 0;
                health = Math.max(0.0, health - 2.0);
                break;
        }

        if (combo > maxCombo) {
            maxCombo = combo;
        }
    }

    private String getKeyLabel(int key) {
        String keyName = GLFW.glfwGetKeyName(key, 0);
        if (keyName == null) {
            if (key == GLFW.GLFW_KEY_SPACE) return "SPACE";
            if (key == GLFW.GLFW_KEY_LEFT_SHIFT) return "L_SHIFT";
            if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) return "R_SHIFT";
            return "Key " + key;
        }
        return keyName.toUpperCase();
    }

    private void drawFilledCircle(MatrixStack matrixStack, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt(r * r - dy * dy));
            fill(matrixStack, cx - dx, cy + dy, cx + dx, cy + dy + 1, color);
        }
    }

    private void drawHollowCircle(MatrixStack matrixStack, int cx, int cy, int r, int thickness, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx1 = (int) Math.round(Math.sqrt(r * r - dy * dy));
            int dx2 = (int) Math.round(Math.sqrt(Math.max(0, (r - thickness) * (r - thickness) - dy * dy)));
            if (Math.abs(dy) <= r - thickness) {
                fill(matrixStack, cx - dx1, cy + dy, cx - dx2, cy + dy + 1, color);
                fill(matrixStack, cx + dx2, cy + dy, cx + dx1, cy + dy + 1, color);
            } else {
                fill(matrixStack, cx - dx1, cy + dy, cx + dx1, cy + dy + 1, color);
            }
        }
    }

    private void hollowRect(MatrixStack matrixStack, int x1, int y1, int x2, int y2, int color) {
        fill(matrixStack, x1, y1, x2, y1 + 1, color);
        fill(matrixStack, x1, y2 - 1, x2, y2, color);
        fill(matrixStack, x1, y1, x1 + 1, y2, color);
        fill(matrixStack, x2 - 1, y1, x2, y2, color);
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
