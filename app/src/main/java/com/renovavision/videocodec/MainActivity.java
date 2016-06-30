package com.renovavision.videocodec;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;

import com.renovavision.videocodec.decoder.VideoDecoder;
import com.renovavision.videocodec.encoder.VideoEncoder;
import com.renovavision.videocodec.model.VideoPacket;
import com.renovavision.videocodec.surface.SurfaceView;

import java.nio.ByteBuffer;

/**
 * Created by Alexandr Golovach on 11.05.16.
 */

public class MainActivity extends AppCompatActivity {

    // video output dimension
    static final int OUTPUT_WIDTH = 640;
    static final int OUTPUT_HEIGHT = 480;

    VideoEncoder mEncoder;
    VideoDecoder mDecoder;

    SurfaceView mEncoderSurfaceView;
    android.view.SurfaceView mDecoderSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mEncoderSurfaceView = (SurfaceView) findViewById(R.id.encoder_surface);
        mEncoderSurfaceView.getHolder().addCallback(mEncoderCallback);

        mDecoderSurfaceView = (android.view.SurfaceView) findViewById(R.id.decoder_surface);
        mDecoderSurfaceView.getHolder().addCallback(mDecoderCallback);

        mEncoder = new MyEncoder();
        mDecoder = new VideoDecoder();
    }

    private SurfaceHolder.Callback mEncoderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            // surface is fully initialized on the activity
            mEncoderSurfaceView.startGLThread();
            mEncoder.start();
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            mEncoder.stop();
        }
    };

    private SurfaceHolder.Callback mDecoderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            // surface is fully initialized on the activity
            //mDecoderSurfaceView.startGLThread();
            mDecoder.start();
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            mDecoder.stop();
        }
    };

    class MyEncoder extends VideoEncoder {

        byte[] mBuffer = new byte[0];

        public MyEncoder() {
            super(mEncoderSurfaceView, OUTPUT_WIDTH, OUTPUT_HEIGHT);
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

            Log.d("ENCODER_FLAG", String.valueOf(info.flags));

            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                // this is the first and only config sample, which contains information about codec
                // like H.264, that let's configure the decoder

                VideoPacket.StreamSettings streamSettings = VideoPacket.getStreamSettings(mBuffer);

                mDecoder.configure(mDecoderSurfaceView.getHolder().getSurface(),
                        OUTPUT_WIDTH,
                        OUTPUT_HEIGHT,
                        streamSettings.sps, streamSettings.pps);
            } else {
                // pass byte[] to decoder's queue to render asap
                mDecoder.decodeSample(mBuffer,
                        0,
                        info.size,
                        info.presentationTimeUs,
                        info.flags);
            }
        }
    }
}
