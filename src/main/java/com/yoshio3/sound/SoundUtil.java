/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.yoshio3.sound;
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 *
 * @author yoterada
 */
public class SoundUtil {

    private final static int FORMAT_CHUNK_SIZE = 16;
    private final static int FORMAT = 1;
    private final static int CHANNEL = 1; //mono:1 stereo:2
    private final static int SAMPLERATE = 16000;
    private final static int BIT_PER_SAMPLE = 16;
    private final static int WAV_HEADER_SIZE = 44;
    private final static int BYTE_BUFFER = 1024;

    /**
     *
     * @param soundBinary
     * @return
     * @throws UnsupportedAudioFileException
     * @throws IOException
     */
    public byte[] convertPCMDataFrom41KStereoTo16KMonoralSound(byte[] soundBinary) throws UnsupportedAudioFileException, IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(soundBinary);
        AudioInputStream sourceStream = AudioSystem.getAudioInputStream(bais);
        return convertedByte41KStereoTo16KMonoralSound(sourceStream);
    }

    public byte[] createWAVHeaderForInfinite16KMonoSound() throws IOException {
        int chunk = 0;
        int dataSize = 0;
        // Please refer to WAV format. Following is Japanese explanation.
        // http://www.wdic.org/w/TECH/WAV
        // http://docs.microsofttranslator.com/speech-translate.html
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        bOut.write("RIFF".getBytes()); // RIFF Header
        bOut.write(getIntByteArray(chunk)); //Total File Size(datasize + 44) - 8;
        bOut.write("WAVE".getBytes()); //WAVE Header
        bOut.write("fmt ".getBytes()); //FORMAT Header
        bOut.write(getIntByteArray(FORMAT_CHUNK_SIZE));
        bOut.write(getFloatByteArray(FORMAT));
        bOut.write(getFloatByteArray(CHANNEL));
        bOut.write(getIntByteArray(SAMPLERATE));
        int ByteRate = SAMPLERATE * 2 * CHANNEL; // Sampling * 2byte * Channel
        bOut.write(getIntByteArray(ByteRate));
        int BlockSize = (int) CHANNEL * BIT_PER_SAMPLE / 8;
        bOut.write(getFloatByteArray(BlockSize));
        bOut.write(getFloatByteArray(BIT_PER_SAMPLE));
        bOut.write("data".getBytes()); // Data Header
        bOut.write(getIntByteArray(dataSize));

        return bOut.toByteArray();
    }    

//    public boolean is16KMonoralSound(AudioInputStream inStream) {
    public boolean is16KMonoralSound(byte[] soundData) throws UnsupportedAudioFileException, IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(soundData);
        AudioInputStream sourceStream = AudioSystem.getAudioInputStream(bais);
        long frameLength = sourceStream.getFrameLength();
        AudioFormat format = sourceStream.getFormat();

        int sampleSizeInBits = format.getSampleSizeInBits();
        float frameRate = format.getFrameRate();
        int channels = format.getChannels();

        return sampleSizeInBits == BIT_PER_SAMPLE
                && frameRate == SAMPLERATE
                && channels == CHANNEL;
    }
    
    public byte[] trimWAVHeader(byte[] origin){       
        return Arrays.copyOfRange(origin, WAV_HEADER_SIZE ,origin.length - WAV_HEADER_SIZE);
    }
    
    
    /**
     *
     * @param sourceStream
     * @return
     * @throws UnsupportedAudioFileException
     * @throws IOException
     */
    private byte[] convertedByte41KStereoTo16KMonoralSound(AudioInputStream sourceStream) throws UnsupportedAudioFileException, IOException {
        AudioFormat sourceFormat = sourceStream.getFormat();
        AudioFormat targetFormat = new AudioFormat(SAMPLERATE, BIT_PER_SAMPLE, CHANNEL, true, false);
        byte[] soundData;
        if (AudioSystem.isConversionSupported(targetFormat, sourceFormat)) {
            try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(targetFormat, sourceStream)) {
                soundData = getByteFromAudioInputStream(audioInputStream);
            }
            return createWAVBinaryDataFor16KMonoSound(soundData);
        } else {
            return getByteFromAudioInputStream(sourceStream);
        }
    }

    /**
     *
     * @param audioInputStream
     * @return
     * @throws IOException
     */
    
    private byte[] getByteFromAudioInputStream(AudioInputStream audioInputStream) throws IOException {
        try (ByteArrayOutputStream bout = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[BYTE_BUFFER];
            int bytesRead;
            while ((bytesRead = audioInputStream.read(bytes, 0, bytes.length)) != -1) {
                bout.write(bytes, 0, bytesRead);
            }
            return bout.toByteArray();
        }
    }    

    private byte[] createWAVBinaryDataFor16KMonoSound(byte[] soundData) throws IOException {
        int totalFileSize = soundData.length + WAV_HEADER_SIZE;
        int chunk = totalFileSize - 8;
        int dataSize = totalFileSize - 126;

        // Please refer to WAV format. Following is Japanese explanation.
        // http://www.wdic.org/w/TECH/WAV
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        bOut.write("RIFF".getBytes()); // RIFF Header
        bOut.write(getIntByteArray(chunk)); //Total File Size(datasize + 44) - 8;
        bOut.write("WAVE".getBytes()); //WAVE Header
        bOut.write("fmt ".getBytes()); //FORMAT Header
        bOut.write(getIntByteArray(FORMAT_CHUNK_SIZE));
        bOut.write(getFloatByteArray(FORMAT));
        bOut.write(getFloatByteArray(CHANNEL));
        bOut.write(getIntByteArray(SAMPLERATE));
        int ByteRate = SAMPLERATE * 2 * CHANNEL; // Sampling * 2byte * Channel
        bOut.write(getIntByteArray(ByteRate));
        int BlockSize = (int) CHANNEL * BIT_PER_SAMPLE / 8;
        bOut.write(getFloatByteArray(BlockSize));
        bOut.write(getFloatByteArray(BIT_PER_SAMPLE));
        bOut.write("data".getBytes()); // Data Header
        bOut.write(getIntByteArray(dataSize));
        bOut.write(soundData);
        return bOut.toByteArray();
    }
    
    private byte[] getIntByteArray(int intValue) {
        byte[] array = ByteBuffer
                .allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(intValue).array();
        return array;
    }

    private byte[] getFloatByteArray(int data) {
        byte[] array = ByteBuffer
                .allocate(2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putChar((char) data)
                .array();
        return array;
    }
}    