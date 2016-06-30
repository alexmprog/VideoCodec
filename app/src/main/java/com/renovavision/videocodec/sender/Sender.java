package com.renovavision.videocodec.sender;

import android.media.MediaCodec;
import android.support.annotation.NonNull;
import android.util.Log;

import com.renovavision.videocodec.encoder.VideoEncoder;
import com.renovavision.videocodec.model.MediaPacket;
import com.renovavision.videocodec.model.VideoPacket;
import com.renovavision.videocodec.surface.SurfaceView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class Sender {

    private static final String TAG = Sender.class.getSimpleName();

    private Worker mWorker;
    private InetAddress address;
    private int port;

    private VideoEncoder videoEncoder;

    public Sender(String host, int port, SurfaceView surfaceView, int width, int height) {
        this.videoEncoder = new Encoder(surfaceView, width, height);
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

    protected void send(VideoPacket videoPacket) {
        if (mWorker != null) {
            mWorker.send(videoPacket);
        }
    }

    class Encoder extends VideoEncoder {

        byte[] mBuffer = new byte[0];

        public Encoder(SurfaceView surfaceView, int width, int height) {
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
                flag = VideoPacket.Flag.KEY_FRAME;
            } else if (info.flags == 0) {
                flag = VideoPacket.Flag.FRAME;
            }

            // TODO: need store latest pps and sps params
            // TODO: need send config frame each time before KEY-FRAME
            send(new VideoPacket(type, flag, info.presentationTimeUs, mBuffer));
        }
    }

    protected class Worker extends Thread {

        private AtomicBoolean mIsRunning = new AtomicBoolean(false);

        Worker() {
        }

        private BlockingQueue<VideoPacket> packetsQueue = new ArrayBlockingQueue<>(2000);

        private void setRunning(boolean isRunning) {
            mIsRunning.set(isRunning);
        }

        @Override
        public void run() {
            Socket socket = null;
            DataOutputStream dataOutputStream = null;
            DataInputStream dataInputStream = null;

            try {
                socket = new Socket(address, port);
                dataOutputStream = new DataOutputStream(
                        socket.getOutputStream());
                dataInputStream = new DataInputStream(socket.getInputStream());

                while (mIsRunning.get()) {
                    if (dataInputStream.available() > 0) {
                        // can do some work here
                    }

                    while (!packetsQueue.isEmpty()) {
                        try {
                            VideoPacket videoPacket = packetsQueue.take();
                            dataOutputStream.write(videoPacket.toByteArray());
                            dataOutputStream.flush();
                        } catch (InterruptedException e) {
                            Log.e(TAG, e.getMessage());
                        }
                    }
                }

            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage());
                    }
                }

                if (dataOutputStream != null) {
                    try {
                        dataOutputStream.close();
                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage());
                    }
                }

                if (dataInputStream != null) {
                    try {
                        dataInputStream.close();
                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage());
                    }
                }

            }
        }

        public synchronized void send(@NonNull VideoPacket videoPacket) {
            try {
                packetsQueue.put(videoPacket);
            } catch (InterruptedException e) {
                Log.e(TAG, e.getMessage());
            }
        }
    }
}
