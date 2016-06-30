package com.renovavision.videocodec.player;

import android.util.Log;
import android.view.Surface;

import com.renovavision.videocodec.decoder.VideoDecoder;
import com.renovavision.videocodec.model.ByteUtils;
import com.renovavision.videocodec.model.MediaPacket;
import com.renovavision.videocodec.model.VideoPacket;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Alexandr Golovach on 27.06.16.
 */

public class Player {

    private static final String TAG = Player.class.getSimpleName();

    private Worker mWorker;
    private int port;
    private Surface surface;
    private int width;
    private int height;

    //private int offset;

    private VideoDecoder videoDecoder;

    public Player(int port, Surface surface, int width, int height) {
        this.videoDecoder = new VideoDecoder();
        this.port = port;
        this.surface = surface;
        this.width = width;
        this.height = height;
    }

    public void start() {
        if (mWorker == null) {
            mWorker = new Worker();
            mWorker.setRunning(true);
            mWorker.start();
        }
        videoDecoder.start();
    }

    public void stop() {
        if (mWorker != null) {
            mWorker.setRunning(false);
            mWorker.shutDown();
            mWorker = null;
        }
        videoDecoder.stop();
    }

    private void decodeSample(byte[] data, int offset, int size, long presentationTimeUs, int flags) {
        videoDecoder.decodeSample(data, offset, size, presentationTimeUs, flags);
    }

    private void configure(Surface surface, int width, int height, ByteBuffer csd0, ByteBuffer csd1) {
        videoDecoder.configure(surface, width, height, csd0, csd1);
    }

    private void packetReceived(VideoPacket videoPacket) {
        if (videoPacket.type == MediaPacket.Type.VIDEO) {

            byte[] data = videoPacket.data;
            if (videoPacket.flag == VideoPacket.Flag.CONFIG) {
                VideoPacket.StreamSettings streamSettings = VideoPacket.getStreamSettings(data);
                configure(surface, width, height, streamSettings.sps, streamSettings.pps);
            } else if (videoPacket.flag == VideoPacket.Flag.END) {
                // need close stream
            } else {
                // nalu frame
                decodeSample(data, 0, data.length, videoPacket.presentationTimeStamp,
                        videoPacket.flag.getFlag());
//                offset += data.length;
            }
        }
    }

    protected class Worker extends Thread {

        private AtomicBoolean mIsRunning = new AtomicBoolean(false);

        private ServerSocket serverSocket;

        Worker() {

        }

        private void setRunning(boolean isRunning) {
            mIsRunning.set(isRunning);
        }

        private void shutDown() {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                }
            }
        }

        @Override
        public void run() {
            DataInputStream dataInputStream = null;

            Socket socket = null;

            try {
                serverSocket = new ServerSocket(port);

                while (mIsRunning.get()) {
                    socket = serverSocket.accept();
                    try {
                        dataInputStream = new DataInputStream(socket.getInputStream());

                        //int offset = -1;
                        byte[] packetSize;

                        while (true) {
                            int available = dataInputStream.available();
                            if (available > 0) {

//                                if (offset == -1) {
//                                    offset = 0;
//                                }

                                // get packet size
                                packetSize = new byte[4];
                                dataInputStream.readFully(packetSize, 0, 4);
//                                offset += 4;

                                // read packet
                                int size = ByteUtils.bytesToInt(packetSize);
                                byte[] packet = new byte[size];
                                dataInputStream.readFully(packet, 0, size);
//                                offset += size;

                                VideoPacket videoPacket = VideoPacket.fromArray(packet);
                                packetReceived(videoPacket);
                            }
                        }
                    } finally {
                        if (dataInputStream != null) {
                            try {
                                dataInputStream.close();
                            } catch (IOException e) {
                                Log.e(TAG, e.getMessage());
                            }
                        }
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage());
                    }
                }
            }
        }
    }
}
