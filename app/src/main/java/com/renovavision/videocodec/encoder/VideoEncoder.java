package com.renovavision.videocodec.encoder;

import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Looper;
import android.support.annotation.NonNull;

import android.util.Log;
import android.view.Surface;

import com.renovavision.videocodec.VideoCodecConstants;
import com.renovavision.videocodec.surface.SurfaceView;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Alexandr Golovach on 12.05.16.
 */

public class VideoEncoder {

    private static final String TAG = "VIDEO_ENCODER_TAG";

    private Worker mWorker;

    // video width
    private int mWidth;

    // video height
    private int mHeight;

    private SurfaceView mSurfaceView;

    protected Camera camera;
    protected Thread cameraThread;
    protected Looper cameraLooper;
    protected boolean cameraOpenedManually = true;
    protected int cameraId = 0;

    protected boolean surfaceReady = false;
    protected boolean unlocked = false;
    protected boolean previewStarted = false;
    protected boolean updated = false;
    protected boolean streaming = false;

    public VideoEncoder(SurfaceView surfaceView, int width, int height) {
        this.mSurfaceView = surfaceView;
        this.mWidth = width;
        this.mHeight = height;
    }

    // will call when surface will be created
    protected void onSurfaceCreated(Surface surface) {
        startPreview();
    }

    // will call before surface will be destroyed
    protected void onSurfaceDestroyed(Surface surface) {
        stopPreview();
    }

    protected void onEncodedSample(MediaCodec.BufferInfo info, ByteBuffer data) {
    }

    public void start() {
        if (mWorker == null) {
            mWorker = new Worker();
            mWorker.setIsRunning(true);
            mWorker.start();
        }
    }

    public void stop() {
        if (mWorker != null) {
            mWorker.setIsRunning(false);
            mWorker = null;
        }
    }

    public synchronized void startPreview() throws RuntimeException {
        cameraOpenedManually = true;
        if (!previewStarted) {
            createCamera();
            updateCamera();
        }
    }

    /**
     * Stops the preview.
     */
    public synchronized void stopPreview() {
        cameraOpenedManually = false;
        stop();
    }

    private void openCamera() throws RuntimeException {
        final Semaphore lock = new Semaphore(0);
        final RuntimeException[] exception = new RuntimeException[1];
        cameraThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                cameraLooper = Looper.myLooper();
                try {
                    camera = Camera.open(cameraId);
                } catch (RuntimeException e) {
                    exception[0] = e;
                } finally {
                    lock.release();
                    Looper.loop();
                }
            }
        });
        cameraThread.start();
        lock.acquireUninterruptibly();
    }

    protected synchronized void createCamera() throws RuntimeException {
        if (mSurfaceView == null) {
            //throw new InvalidSurfaceException("Invalid surface !");
        }
        if (mSurfaceView.getHolder() == null || !surfaceReady) {
            // throw new InvalidSurfaceException("Invalid surface !");
        }

        if (camera == null) {
            openCamera();
            updated = false;
            unlocked = false;
            camera.setErrorCallback(new Camera.ErrorCallback() {

                @Override
                public void onError(int error, Camera camera) {
                    // On some phones when trying to use the camera facing front the media server
                    // will die. Whether or not this callback may be called really depends on the
                    // phone
                    if (error == Camera.CAMERA_ERROR_SERVER_DIED) {
                        // In this case the application must release the camera and instantiate a
                        // new one
                        Log.e(TAG, "Media server died !");
                        // We don't know in what thread we are so stop needs to be synchronized
                        cameraOpenedManually = false;
                        stop();
                    } else {
                        Log.e(TAG, "Error unknown with the camera: " + error);
                    }
                }
            });

            try {
                // If the phone has a flash, we turn it on/off according to flashEnabled
                // setRecordingHint(true) is a very nice optimization if you plane to only use
                // the Camera for recording
                Camera.Parameters parameters = camera.getParameters();
                if (parameters.getFlashMode() != null) {
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                }
                parameters.setRecordingHint(true);
                camera.setParameters(parameters);
                camera.setDisplayOrientation(270);

                try {
                    mSurfaceView.startGLThread();
                    camera.setPreviewTexture(mSurfaceView.getSurfaceTexture());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (RuntimeException e) {
                destroyCamera();
                throw e;
            }
        }
    }

    protected synchronized void destroyCamera() {
        if (camera != null) {
            if (streaming) {
                //super.stop();
            }
            lockCamera();
            camera.stopPreview();
            try {
                camera.release();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage() != null ? e.getMessage() : "unknown error");
            }
            camera = null;
            cameraLooper.quit();
            unlocked = false;
            previewStarted = false;
        }
    }

    protected synchronized void updateCamera() throws RuntimeException {
        // The camera is already correctly configured
        if (updated) {
            return;
        }

        if (previewStarted) {
            previewStarted = false;
            camera.stopPreview();
        }

        Camera.Parameters parameters = camera.getParameters();

        mSurfaceView.requestAspectRatio(mWidth / mHeight);

        //parameters.setPreviewFormat(ImageFormat.YUV_420_888);
        parameters.setPreviewSize(mWidth, mHeight);
        //parameters.setPreviewFpsRange(30, 30);

        try {
            camera.setParameters(parameters);

            camera.setDisplayOrientation(270);

            camera.startPreview();
            previewStarted = true;
            updated = true;
        } catch (RuntimeException e) {
            destroyCamera();
            throw e;
        }
    }

    protected void lockCamera() {
        if (unlocked) {
            try {
                camera.reconnect();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
            unlocked = false;
        }
    }

    protected void unlockCamera() {
        if (!unlocked) {
            Log.d(TAG, "Unlocking camera");
            try {
                camera.unlock();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
            unlocked = true;
        }
    }

    // background thread which prepare MediaCodec and start encoding using surface-to-buffer method
    protected class Worker extends Thread {

        //
        private MediaCodec.BufferInfo mBufferInfo;

        // video codec which get access to hardware codec
        private MediaCodec mCodec;

        // indicator for inner loop
        @NonNull
        private final AtomicBoolean mIsRunning = new AtomicBoolean(false);

        private Surface mSurface;

        private final long mTimeoutUsec;

        public Worker() {
            this.mBufferInfo = new MediaCodec.BufferInfo();
            this.mTimeoutUsec = 10000L;
        }

        public void setIsRunning(boolean running) {
            mIsRunning.set(running);
        }

        @NonNull
        public AtomicBoolean isRunning() {
            return mIsRunning;
        }

        @Override
        public void run() {
            // prepare video codec
            prepare();

            try {
                while (mIsRunning.get()) {
                    // encode video sources from input buffer
                    encode();
                }

                encode();
            } finally {
                // release video codec resourses
                release();
            }
        }

        void encode() {
            if (!mIsRunning.get()) {
                // if not running anymore, complete stream
                mCodec.signalEndOfInputStream();
            }

            // pre-lollipop api
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {

                // get output buffers
                ByteBuffer[] outputBuffers = mCodec.getOutputBuffers();
                for (; ; ) {
                    //get status
                    int status = mCodec.dequeueOutputBuffer(mBufferInfo, mTimeoutUsec);
                    if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // something wrong with codec - need try again
                        if (!mIsRunning.get()) {
                            break;
                        }
                    } else if (status == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        // need get new output buffers
                        outputBuffers = mCodec.getOutputBuffers();
                    } else if (status >= 0) {

                        // encoded sample
                        ByteBuffer data = outputBuffers[status];
                        data.position(mBufferInfo.offset);
                        data.limit(mBufferInfo.offset + mBufferInfo.size);

                        final int endOfStream = mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                        if (endOfStream == 0) {
                            onEncodedSample(mBufferInfo, data);
                        }
                        // releasing buffer is important
                        mCodec.releaseOutputBuffer(status, false);

                        // don't have any buffers - need finish
                        if (endOfStream == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                            break;
                        }
                    }
                }
            } else {
                for (; ; ) {
                    //get status
                    int status = mCodec.dequeueOutputBuffer(mBufferInfo, mTimeoutUsec);
                    if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // something wrong with codec - need try again
                        if (!mIsRunning.get()) {
                            break;
                        }
                    } else if (status >= 0) {
                        // encoded sample
                        ByteBuffer data = mCodec.getOutputBuffer(status);
                        if (data != null) {

                            final int endOfStream = mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                            if (endOfStream == 0) {
                                onEncodedSample(mBufferInfo, data);
                            }
                            // release buffer
                            mCodec.releaseOutputBuffer(status, false);

                            // don't have any buffers - need finish
                            if (endOfStream == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                                break;
                            }
                        }
                    }
                }
            }
        }

        // release all resources
        private void release() {
            onSurfaceDestroyed(mSurface);

            mCodec.stop();
            mCodec.release();
            mSurface.release();
        }

        private MediaFormat getOutputFormat() {
            return mCodec.getOutputFormat();
        }

        private void prepare() {
            // configure video output
            MediaFormat format = MediaFormat.createVideoFormat(VideoCodecConstants.VIDEO_CODEC, mWidth, mHeight);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, VideoCodecConstants.VIDEO_BITRATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, VideoCodecConstants.VIDEO_FPS);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VideoCodecConstants.VIDEO_FI);

            try {
                mCodec = MediaCodec.createEncoderByType(VideoCodecConstants.VIDEO_CODEC);
            } catch (IOException e) {
                // can not create avc codec - throw exception
                throw new RuntimeException(e);
            }
            mCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            // create surface associated with code
            mSurface = mCodec.createInputSurface();
            mSurfaceView.addMediaCodecSurface(mSurface);
            // notify codec to start watch surface and encode samples
            mCodec.start();

            onSurfaceCreated(mSurface);
        }
    }
}
