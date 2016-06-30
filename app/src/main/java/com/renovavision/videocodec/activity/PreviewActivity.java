package com.renovavision.videocodec.activity;

import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.renovavision.videocodec.R;
import com.renovavision.videocodec.player.Player;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

public class PreviewActivity extends AppCompatActivity {

    Player mPlayer;
    AtomicBoolean isSurfaceCreated = new AtomicBoolean(false);

    SurfaceView mDecoderSurfaceView;
    Button mStartButton;
    TextView mIpView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        mDecoderSurfaceView = (SurfaceView) findViewById(R.id.decoder_surface);
        mDecoderSurfaceView.getHolder().addCallback(mEncoderCallback);

        mIpView = (TextView) findViewById(R.id.ip_view);
        mIpView.setText(wifiIpAddress());

        mStartButton = (Button) findViewById(R.id.player_button);
        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isSurfaceCreated.get()) {
                    mPlayer.start();
                }
            }
        });
    }

    /**
     * Get the device IP address and format it into a human readable one.
     *
     * @return device's IP address
     */
    protected String wifiIpAddress() {
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

        // Convert little-endian to big-endian if needed
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        String ipAddressString;
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (UnknownHostException ex) {
            //Log.e(TAG, "Unable to get host address.");
            ipAddressString = null;
        }

        return ipAddressString;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPlayer != null) {
            mPlayer.stop();
        }
    }

    private SurfaceHolder.Callback mEncoderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            // surface is fully initialized on the activity
            mPlayer = new Player(5006, surfaceHolder.getSurface(), 640, 480);
            isSurfaceCreated.set(true);
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            isSurfaceCreated.set(false);
        }
    };

}

