package com.renovavision.videocodec.sender;

import android.media.MediaCodec;

import com.renovavision.videocodec.encoder.VideoEncoder;
import com.renovavision.videocodec.model.MediaPacket;
import com.renovavision.videocodec.model.VideoPacket;
import com.renovavision.videocodec.surface.SurfaceView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class Sender {

    private Worker mWorker;
    private InetAddress address;
    private int port;

    private VideoEncoder videoEncoder;

    public Sender(String host, int port, SurfaceView surfaceView, int width, int height) {
        this.videoEncoder = new UDPEncoder(surfaceView, width, height);
        this.port = port;
        try {
            this.address = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        if (mWorker == null) {
            mWorker = new Worker();
            mWorker.setRunning(true);
            mWorker.start();
        }
        videoEncoder.start();
        videoEncoder.startPreview();
    }

    public void stop() {
        if (mWorker != null) {
            mWorker.setRunning(false);
            mWorker = null;
        }
        videoEncoder.stopPreview();
        videoEncoder.stop();
    }

    protected void send(byte[] data) {
        if (mWorker != null) {
            mWorker.send(address, port, data);
        }
    }

    class UDPEncoder extends VideoEncoder {

        byte[] mBuffer = new byte[0];

        public UDPEncoder(SurfaceView surfaceView, int width, int height) {
            super(surfaceView, width, height);
        }

        @Override
        protected void onEncodedSample(MediaCodec.BufferInfo info, ByteBuffer data) {
            // Here we could have just used ByteBuffer, but in real life case we might need to
            // send sample over network, etc. This requires byte[]
            if (mBuffer.length < info.size) {
                mBuffer = new byte[info.size];
            }
            data.position(info.offset);
            data.limit(info.offset + info.size);
            data.get(mBuffer, 0, info.size);

            MediaPacket.Type type = MediaPacket.Type.VIDEO;
            VideoPacket.Flag flag = VideoPacket.Flag.CONFIG;

            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                flag = VideoPacket.Flag.END;
            } else if ((info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                flag = VideoPacket.Flag.FRAME;
            }

            send(VideoPacket.toArray(type, flag, info.presentationTimeUs, mBuffer));
        }
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
                datagramSocket = new DatagramSocket();
                while (mIsRunning.get()) {
                    // sender thread - should only send packet using udp connection
                }
                datagramSocket.disconnect();
                datagramSocket.close();
            } catch (SocketException e) {
                mIsRunning.set(false);
                e.printStackTrace();
            }

        }

        public synchronized void send(InetAddress address, int port, byte[] data) {
            if (datagramSocket != null) {
                DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
                try {
                    datagramSocket.send(packet);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
