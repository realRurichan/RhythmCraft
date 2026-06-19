package io.github.rurichan.rhythmcraft;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class BMSParser {

    public static class Note {
        public final int lane; // 0-6 for keys, 7 for scratch
        public final long timestamp; // Milliseconds
        public boolean hit = false;

        public Note(int lane, long timestamp) {
            this.lane = lane;
            this.timestamp = timestamp;
        }
    }

    public static class BMSMetadata {
        public File file;
        public String title = "Unknown Title";
        public String artist = "Unknown Artist";
        public double bpm = 130.0;
        public int difficulty = 2; // 1: Beginner, 2: Normal, 3: Hyper, 4: Another, 5: Insane
        public int playLevel = 1;

        public String getDifficultyLabel() {
            String name;
            switch (difficulty) {
                case 1: name = "BEGINNER"; break;
                case 2: name = "NORMAL"; break;
                case 3: name = "HYPER"; break;
                case 4: name = "ANOTHER"; break;
                case 5: name = "INSANE"; break;
                default:
                    String fName = file.getName();
                    int dot = fName.lastIndexOf('.');
                    name = dot > 0 ? fName.substring(0, dot).toUpperCase() : fName.toUpperCase();
                    break;
            }
            return name + " [★" + playLevel + "]";
        }
    }

    public static class BMSChart {
        public BMSMetadata metadata;
        public List<Note> notes = new ArrayList<>();
        public File bgmFile = null;
    }

    private static class RawLine {
        int channel;
        String data;

        RawLine(int channel, String data) {
            this.channel = channel;
            this.data = data;
        }
    }

    /**
     * Reads file lines while safely falling back to common CJK encodings to prevent Mojibake (corrupted text).
     * Try UTF-8 -> Shift_JIS (Japanese) -> GBK (Chinese) -> MS949 (Korean) -> System Default.
     */
    public static List<String> readFileLines(File file) {
        // 1. Try UTF-8
        try {
            return readLinesWithCharset(file, StandardCharsets.UTF_8);
        } catch (Exception e1) {
            // 2. Try Shift_JIS (Japanese)
            try {
                return readLinesWithCharset(file, Charset.forName("Shift_JIS"));
            } catch (Exception e2) {
                // 3. Try GBK (Chinese)
                try {
                    return readLinesWithCharset(file, Charset.forName("GBK"));
                } catch (Exception e3) {
                    // 4. Try MS949 (Korean)
                    try {
                        return readLinesWithCharset(file, Charset.forName("MS949"));
                    } catch (Exception e4) {
                        // 5. Fallback to System Default
                        try {
                            return readLinesWithCharset(file, Charset.defaultCharset());
                        } catch (Exception e5) {
                            return new ArrayList<>();
                        }
                    }
                }
            }
        }
    }

    private static List<String> readLinesWithCharset(File file, Charset charset) throws Exception {
        List<String> lines = new ArrayList<>();
        CharsetDecoder decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), decoder))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    public static BMSMetadata readMetadata(File bmsFile) {
        BMSMetadata meta = new BMSMetadata();
        meta.file = bmsFile;
        List<String> lines = readFileLines(bmsFile);

        for (String line : lines) {
            line = line.trim();
            if (!line.startsWith("#")) continue;
            line = line.substring(1);

            // Quick breakout if we hit actual note channels to avoid parsing the whole file
            if (line.length() > 5 && Character.isDigit(line.charAt(0)) && Character.isDigit(line.charAt(1)) && Character.isDigit(line.charAt(2))) {
                break;
            }

            String upper = line.toUpperCase();
            if (upper.startsWith("TITLE ")) {
                meta.title = line.substring(6).trim();
            } else if (upper.startsWith("ARTIST ")) {
                meta.artist = line.substring(7).trim();
            } else if (upper.startsWith("BPM ")) {
                try {
                    meta.bpm = Double.parseDouble(line.substring(4).trim());
                } catch (NumberFormatException ignored) {}
            } else if (upper.startsWith("DIFFICULTY ")) {
                try {
                    meta.difficulty = Integer.parseInt(line.substring(11).trim());
                } catch (NumberFormatException ignored) {}
            } else if (upper.startsWith("PLAYLEVEL ")) {
                try {
                    meta.playLevel = Integer.parseInt(line.substring(10).trim());
                } catch (NumberFormatException ignored) {}
            }
        }
        return meta;
    }

    public static BMSChart parse(File bmsFile) throws Exception {
        BMSChart chart = new BMSChart();
        chart.metadata = readMetadata(bmsFile);
        
        File oggFile = new File(bmsFile.getParentFile(), "bgm.ogg");
        if (oggFile.exists()) {
            chart.bgmFile = oggFile;
        } else {
            chart.bgmFile = new File(bmsFile.getParentFile(), "bgm.wav");
        }

        // Measure -> List of lines
        Map<Integer, List<RawLine>> measureData = new HashMap<>();
        Map<Integer, Double> measureLengths = new HashMap<>(); // Scale factor (default 1.0)

        List<String> lines = readFileLines(bmsFile);

        for (String line : lines) {
            line = line.trim();
            if (!line.startsWith("#")) continue;
            line = line.substring(1);

            // Parse Note Channel data
            // Format: xxxYY:zzzz... where xxx is measure, YY is channel
            if (line.length() > 6 && Character.isDigit(line.charAt(0)) && line.contains(":")) {
                String[] parts = line.split(":", 2);
                String header = parts[0];
                String data = parts[1].trim();

                if (header.length() == 5) {
                    try {
                        int measure = Integer.parseInt(header.substring(0, 3));
                        int channel = Integer.parseInt(header.substring(3, 5));

                        // Channel 02 is measure length multiplier
                        if (channel == 2) {
                            measureLengths.put(measure, Double.parseDouble(data));
                        } else {
                            measureData.computeIfAbsent(measure, k -> new ArrayList<>())
                                       .add(new RawLine(channel, data));
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // Calculate measure timestamps and populate notes
        int maxMeasure = 0;
        for (int m : measureData.keySet()) {
            if (m > maxMeasure) maxMeasure = m;
        }
        for (int m : measureLengths.keySet()) {
            if (m > maxMeasure) maxMeasure = m;
        }

        double currentBpm = chart.metadata.bpm;
        double currentPositionMs = 0.0;

        for (int m = 0; m <= maxMeasure; m++) {
            double lengthScale = measureLengths.getOrDefault(m, 1.0);
            // Time for a 4/4 measure in ms = 4 * 60000 / BPM
            double measureDuration = (4.0 * 60000.0 / currentBpm) * lengthScale;

            List<RawLine> measureLines = measureData.get(m);
            if (measureLines != null) {
                for (RawLine rl : measureLines) {
                    int lane = channelToLane(rl.channel);
                    if (lane == -1) continue; // Unused channel

                    String data = rl.data;
                    int length = data.length();
                    int numNotes = length / 2;

                    for (int i = 0; i < numNotes; i++) {
                        String noteId = data.substring(i * 2, i * 2 + 2);
                        if (!noteId.equals("00")) {
                            double offsetRatio = (double) i / numNotes;
                            long timestamp = (long) (currentPositionMs + offsetRatio * measureDuration);
                            chart.notes.add(new Note(lane, timestamp));
                        }
                    }
                }
            }

            currentPositionMs += measureDuration;
        }

        // Sort notes by timestamp
        chart.notes.sort(Comparator.comparingLong(n -> n.timestamp));

        return chart;
    }

    private static int channelToLane(int channel) {
        switch (channel) {
            case 11: return 0; // Key 1
            case 12: return 1; // Key 2
            case 13: return 2; // Key 3
            case 14: return 3; // Key 4
            case 15: return 4; // Key 5
            case 18: return 5; // Key 6
            case 19: return 6; // Key 7
            case 16: return 7; // Scratch (1P)
            default: return -1;
        }
    }
}
