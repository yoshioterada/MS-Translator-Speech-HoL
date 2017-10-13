/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.yoshio3;

import com.yoshio3.sound.SoundUtil;
import com.yoshio3.websocket.TranslatorWebSockerClientEndpoint;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

/**
 *
 * @author yoterada
 */
public class Main {

    private static final String TRANSLATOR_WEBSOCKET_ENDPOINT = "wss://dev.microsofttranslator.com/speech/translate?features=partial";

    private final static String FROM = "ja-JP";
    private final static String TO = "en-US";

    public static void main(String... args) {
        try {
            Main main = new Main();
            StringBuilder strBuilder = new StringBuilder();
            String microsoftTranslatorURI = strBuilder.append(TRANSLATOR_WEBSOCKET_ENDPOINT)
                    .append("&from=")
                    .append(FROM)
                    .append("&to=")
                    .append(TO)
                    .append("&api-version=1.0")
                    .toString();

            URI serverEndpointUri = new URI(microsoftTranslatorURI);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> {
                main.connectAndSendData(serverEndpointUri);

                try (BufferedReader stdReader = new BufferedReader(new InputStreamReader(System.in))) {
                    String line;
                    //https://www.webrtc-experiment.com/RecordRTC/ で音声データ作成
                    while ((line = stdReader.readLine()) != null) {
                        if (line.equals("exit")) {
                            executor.shutdown();
                            System.exit(0);
                            break;
                        }
                    }
                } catch (IOException ioe) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ioe);
                }
            });
        } catch (URISyntaxException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void connectAndSendData(URI serverEndpointUri) {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            Session translatorSession = container.connectToServer(TranslatorWebSockerClientEndpoint.class, serverEndpointUri);

            sendSoundHeader(translatorSession);
            sendSoundData("/Users/tyoshio2002/Downloads/sound.wav", translatorSession);

        } catch (UnsupportedAudioFileException | IOException | DeploymentException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    // In order to send the sound data as Stream
    // Size of 0 Header, I created.
    private void sendSoundHeader(Session session) throws IOException {
        SoundUtil soundUtil = new SoundUtil();
        byte[] createWAVHeaderForInfinite16KMonoSound = soundUtil.createWAVHeaderForInfinite16KMonoSound();
        session.getBasicRemote().sendBinary(ByteBuffer.wrap(createWAVHeaderForInfinite16KMonoSound));
    }

    private void sendSoundData(String fileName, Session session) throws IOException, UnsupportedAudioFileException {
        Path path = Paths.get(fileName);
        byte[] readAllBytes = Files.readAllBytes(path);

        SoundUtil soundUtil = new SoundUtil();
        //44.1KHz->16Khz, Stereo->Monoral 
        byte[] convertedSound = soundUtil.convertPCMDataFrom41KStereoTo16KMonoralSound(readAllBytes);
        //Remove WAVE Header
        byte[] trimedHeader = soundUtil.trimWAVHeader(convertedSound);
        //Create Chunk Data
        List<byte[]> divided = divideArray(trimedHeader, 4096);
        //Send chunk sound data to Microsoft Translator
        divided.stream().forEachOrdered(bytes -> {
            try {
                session.getBasicRemote().sendBinary(ByteBuffer.wrap(bytes));
            } catch (IOException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    private List<byte[]> divideArray(byte[] source, int chunksize) {
        List<byte[]> result = new ArrayList<>();
        int start = 0;
        while (start < source.length) {
            int end = Math.min(source.length, start + chunksize);
            result.add(Arrays.copyOfRange(source, start, end));
            start += chunksize;
        }
        return result;
    }
}
