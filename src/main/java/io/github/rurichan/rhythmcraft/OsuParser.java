package io.github.rurichan.rhythmcraft;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.IResource;
import net.minecraft.util.ResourceLocation;

public class OsuParser {
    public enum NoteType {
        DON,        // Red (normal)
        KATSU,      // Blue (normal)
        BIG_DON,    // Large Red
        BIG_KATSU,  // Large Blue
        DRUMROLL,   // Yellow Slider
        BALLOON     // Spinner / Dendou
    }

    public static class OsuNote {
        public int time; // ms
        public NoteType type;
        public int endTime; // ms (for sliders/spinners)
        public boolean hit = false;
        public boolean hitLeft = false;  // For double hits on big notes
        public boolean hitRight = false; // For double hits on big notes
        
        // Slider/drumroll ticks
        public List<Integer> tickTimes = new ArrayList<>();
        public boolean[] tickHit;
        
        // Balloon spinner hits
        public int requiredHits = 5;
        public int hitCount = 0;
    }

    public static class OsuMetadata {
        public String title = "Unknown Title";
        public String artist = "Unknown Artist";
        public String version = "Normal";
        public String audioFilename = "bgm.ogg";
        public ResourceLocation resourceLocation; // Set if from resource pack
        public File localFile; // Set if from local folder
    }

    public static class OsuChart extends OsuMetadata {
        public List<OsuNote> notes = new ArrayList<>();
        public List<TimingPoint> timingPoints = new ArrayList<>();
        public float sliderMultiplier = 1.4f;
    }

    public static class TimingPoint {
        public int time;
        public double beatLength;
        public boolean uninherited;
    }

    public static OsuChart parse(BufferedReader reader) throws Exception {
        OsuChart chart = new OsuChart();
        String line;
        String section = "";

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("//")) continue;

            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1);
                continue;
            }

            if (section.equals("General")) {
                if (line.startsWith("AudioFilename:")) {
                    chart.audioFilename = line.substring("AudioFilename:".length()).trim();
                }
            } else if (section.equals("Metadata")) {
                if (line.startsWith("Title:")) {
                    chart.title = line.substring("Title:".length()).trim();
                } else if (line.startsWith("TitleUnicode:")) {
                    chart.title = line.substring("TitleUnicode:".length()).trim();
                } else if (line.startsWith("Artist:")) {
                    chart.artist = line.substring("Artist:".length()).trim();
                } else if (line.startsWith("ArtistUnicode:")) {
                    chart.artist = line.substring("ArtistUnicode:".length()).trim();
                } else if (line.startsWith("Version:")) {
                    chart.version = line.substring("Version:".length()).trim();
                }
            } else if (section.equals("Difficulty")) {
                if (line.startsWith("SliderMultiplier:")) {
                    chart.sliderMultiplier = Float.parseFloat(line.substring("SliderMultiplier:".length()).trim());
                }
            } else if (section.equals("TimingPoints")) {
                try {
                    String[] parts = line.split(",");
                    if (parts.length >= 2) {
                        TimingPoint tp = new TimingPoint();
                        tp.time = (int) Double.parseDouble(parts[0]);
                        tp.beatLength = Double.parseDouble(parts[1]);
                        tp.uninherited = parts.length < 7 || Integer.parseInt(parts[6]) == 1;
                        chart.timingPoints.add(tp);
                    }
                } catch (Exception e) {
                    // Ignore malformed timing points
                }
            } else if (section.equals("HitObjects")) {
                try {
                    String[] parts = line.split(",");
                    if (parts.length >= 5) {
                        int time = (int) Double.parseDouble(parts[2]);
                        int type = Integer.parseInt(parts[3]);
                        int hitSound = Integer.parseInt(parts[4]);

                        OsuNote note = new OsuNote();
                        note.time = time;

                        if ((type & 8) != 0) {
                            // Spinner / Balloon
                            note.type = NoteType.BALLOON;
                            note.endTime = (int) Double.parseDouble(parts[5]);
                            int duration = note.endTime - note.time;
                            note.requiredHits = Math.max(3, duration / 80);
                        } else if ((type & 2) != 0) {
                            // Slider / Drumroll
                            note.type = NoteType.DRUMROLL;
                            int slides = Integer.parseInt(parts[5]);
                            double length = Double.parseDouble(parts[6]);

                            // Retrieve active uninherited timing point and speed multiplier
                            double activeBeatLength = 500.0; // 120 bpm fallback
                            double velocityMultiplier = 1.0;

                            for (TimingPoint tp : chart.timingPoints) {
                                if (tp.time <= time) {
                                    if (tp.uninherited) {
                                        activeBeatLength = tp.beatLength;
                                        velocityMultiplier = 1.0;
                                    } else {
                                        if (tp.beatLength < 0) {
                                            velocityMultiplier = -100.0 / tp.beatLength;
                                        }
                                    }
                                }
                            }

                            double duration = (length / (chart.sliderMultiplier * 100.0 * velocityMultiplier)) * activeBeatLength * slides;
                            note.endTime = time + (int) duration;

                            // Ticks spacing at 1/4 beat or 80ms
                            double spacing = Math.max(50.0, activeBeatLength / 4.0);
                            for (double t = time; t <= note.endTime; t += spacing) {
                                note.tickTimes.add((int) t);
                            }
                            if (note.tickTimes.isEmpty()) {
                                note.tickTimes.add(time);
                            }
                            note.tickHit = new boolean[note.tickTimes.size()];
                        } else {
                            // Normal circle note
                            boolean isKatsu = (hitSound & 2) != 0 || (hitSound & 8) != 0;
                            boolean isFinish = (hitSound & 4) != 0;

                            if (isKatsu) {
                                note.type = isFinish ? NoteType.BIG_KATSU : NoteType.KATSU;
                            } else {
                                note.type = isFinish ? NoteType.BIG_DON : NoteType.DON;
                            }
                            note.endTime = time;
                        }
                        chart.notes.add(note);
                    }
                } catch (Exception e) {
                    // Ignore malformed hit objects
                }
            }
        }

        // Sort notes by start time
        chart.notes.sort((n1, n2) -> Integer.compare(n1.time, n2.time));
        return chart;
    }

    public static OsuChart parseLocal(File file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            OsuChart chart = parse(reader);
            chart.localFile = file;
            return chart;
        }
    }

    public static OsuChart parseResource(ResourceLocation loc) throws Exception {
        IResource res = Minecraft.getInstance().getResourceManager().getResource(loc);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
            OsuChart chart = parse(reader);
            chart.resourceLocation = loc;
            return chart;
        }
    }
}
