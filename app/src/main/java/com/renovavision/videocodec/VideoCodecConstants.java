package com.renovavision.videocodec;

/**
 * Created by Alexandr Golovach on 11.05.16.
 */

// base class for video encoder/decoder configuration
public interface VideoCodecConstants {

    // video codec
    String VIDEO_CODEC = "video/avc";

    // frame per seconds
    int VIDEO_FPS = 30;

    // i frame interval
    int VIDEO_FI = 2;

    // video bitrate
    int VIDEO_BITRATE = 3000 * 1000;
}
