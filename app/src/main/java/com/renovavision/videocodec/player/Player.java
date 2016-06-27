package com.renovavision.videocodec.player;

import android.view.Surface;

import com.renovavision.videocodec.decoder.VideoDecoder;
import com.renovavision.videocodec.model.MediaPacket;
import com.renovavision.videocodec.model.VideoPacket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Alexandr Golovach on 27.06.16.
 */
public class Player {

    private Worker mWorker;
    private int port;
    private Surface surface;
    private int width;
    private int height;

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
    }

    public void stop() {
        if (mWorker != null) {
            mWorker.setRunning(false);
            mWorker = null;
        }
    }

    public void decodeSample(byte[] data, int offset, int size, long presentationTimeUs, int flags) {
        videoDecoder.decodeSample(data, offset, size, presentationTimeUs, flags);
    }

    public void configure(Surface surface, int width, int height, ByteBuffer csd0, ByteBuffer csd1) {
        videoDecoder.configure(surface, width, height, csd0, csd1);
    }

    protected class Worker extends Thread {

        private AtomicBoolean mIsRunning = new AtomicBoolean(false);

        private DatagramSocket datagramSocket;

        Worker() {

        }

        private void setRunning(boolean isRunning) {
            mIsRunning.set(isRunning);
        }

        @Override
        public void run() {
            try {
                datagramSocket = new DatagramSocket(port);

                int len = 1024;
                ByteBuffer byteBuffer = ByteBuffer.allocate(len);
                byte[] data;

                while (mIsRunning.get()) {
                    // player thread - should only send packet using udp connection
                    DatagramPacket datagramPacket = new DatagramPacket(byteBuffer.array(), len);
                    datagramSocket.receive(datagramPacket);
                    data = new byte[datagramPacket.getLength()];
                    System.arraycopy(datagramPacket.getData(), datagramPacket.getOffset(), data, 0, datagramPacket.getLength());
                    VideoPacket videoPacket = VideoPacket.fromArray(data);
                    VideoPacket.isVideoPacket(data);
                    MediaPacket.Type type = videoPacket.type;
                    byteBuffer.clear();
                }
                datagramSocket.disconnect();
                datagramSocket.close();
            } catch (IOException e) {
                mIsRunning.set(false);
                e.printStackTrace();
            }

        }
    }
}
