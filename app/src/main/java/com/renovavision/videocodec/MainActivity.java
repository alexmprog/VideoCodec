package com.renovavision.videocodec;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;

import com.renovavision.videocodec.decoder.VideoDecoder;
import com.renovavision.videocodec.encoder.VideoEncoder;
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

                byte[] sps = null, pps = null;
//                int p = 4, q = 4;
//                int len = info.size;
//                if (len < 128) {
//                    if (len > 0 && mBuffer[0] == 0 && mBuffer[1] == 0 && mBuffer[2] == 0 && mBuffer[3] == 1) {
//                        // Parses the SPS and PPS, they could be in two different packets and in a different order
//                        //depending on the phone so we don't make any assumption about that
//                        while (p < len) {
//                            while (!(mBuffer[p + 0] == 0 && mBuffer[p + 1] == 0 && mBuffer[p + 2] == 0 && mBuffer[p + 3] == 1) && p + 3 < len)
//                                p++;
//                            if (p + 3 >= len) p = len;
//                            if ((mBuffer[q] & 0x1F) == 7) {
//                                mSPS = new byte[p - q];
//                                System.arraycopy(mBuffer, q, mSPS, 0, p - q);
//                            } else {
//                                mPPS = new byte[p - q];
//                                System.arraycopy(mBuffer, q, mPPS, 0, p - q);
//                            }
//                            p += 4;
//                            q = p;
//                        }
//                    }
//                }

                // The PPS and PPS shoud be there
//                MediaFormat format = mEncoder.;
//                ByteBuffer spsb = format.getByteBuffer("csd-0");
//                ByteBuffer ppsb = format.getByteBuffer("csd-1");
//                mSPS = new byte[spsb.capacity() - 4];
//                spsb.position(4);
//                spsb.get(mSPS, 0, mSPS.length);
//                mPPS = new byte[ppsb.capacity() - 4];
//                ppsb.position(4);
//                ppsb.get(mPPS, 0, mPPS.length);

                ByteBuffer spsPpsBuffer = ByteBuffer.wrap(mBuffer);
                if (spsPpsBuffer.getInt() == 0x00000001) {
                    System.out.println("parsing sps/pps");
                } else {
                    System.out.println("something is amiss?");
                }
                int ppsIndex = 0;
                while (!(spsPpsBuffer.get() == 0x00 && spsPpsBuffer.get() == 0x00 && spsPpsBuffer.get() == 0x00 && spsPpsBuffer.get() == 0x01)) {

                }
                ppsIndex = spsPpsBuffer.position();
                sps = new byte[ppsIndex - 4];
                System.arraycopy(mBuffer, 0, sps, 0, sps.length);
                ppsIndex -= 4;
                pps = new byte[mBuffer.length - ppsIndex];
                System.arraycopy(mBuffer, ppsIndex, pps, 0, pps.length);

                // sps buffer
                ByteBuffer csd0 = ByteBuffer.wrap(sps, 0, sps.length);

                // pps buffer
                ByteBuffer csd1 = ByteBuffer.wrap(pps, 0, pps.length);

                mDecoder.configure(mDecoderSurfaceView.getHolder().getSurface(),
                        OUTPUT_WIDTH,
                        OUTPUT_HEIGHT,
                        csd0, csd1);
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
