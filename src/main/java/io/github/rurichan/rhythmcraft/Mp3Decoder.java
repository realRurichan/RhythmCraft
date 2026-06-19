package io.github.rurichan.rhythmcraft;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class Mp3Decoder {
    public static Clip loadMp3ToClip(InputStream is) throws Exception {
        Bitstream bitstream = new Bitstream(is);
        Decoder decoder = new Decoder();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        int sampleRate = -1;
        int channels = -1;

        try {
            Header header;
            while ((header = bitstream.readFrame()) != null) {
                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                if (sampleRate == -1) {
                    sampleRate = output.getSampleFrequency();
                    channels = output.getChannelCount();
                }

                short[] pcm = output.getBuffer();
                int length = output.getBufferLength();
                for (int i = 0; i < length; i++) {
                    short val = pcm[i];
                    baos.write(val & 0xff);
                    baos.write((val >> 8) & 0xff);
                }
                bitstream.closeFrame();
            }
        } finally {
            bitstream.close();
        }

        byte[] pcmData = baos.toByteArray();
        if (sampleRate == -1) {
            throw new RuntimeException("Failed to decode any MP3 frames");
        }

        AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
        ByteArrayInputStream bais = new ByteArrayInputStream(pcmData);
        AudioInputStream ais = new AudioInputStream(bais, format, pcmData.length / format.getFrameSize());
        Clip clip = AudioSystem.getClip();
        clip.open(ais);
        return clip;
    }
}
