package com.renovavision.videocodec.model;

/**
 * Created by Alexandr Golovach on 27.06.16.
 */

public class VideoPacket extends MediaPacket {

    public static final byte[] PACKET_HEADER = new byte[]{0, 0, 0, 0, 1};

    public enum Flag {

        CONFIG((byte) 2), FRAME((byte) 1), END((byte) 4);

        private byte type;

        Flag(byte type) {
            this.type = type;
        }

        public byte getFlag() {
            return type;
        }

        public static Flag getFlag(byte value) {
            for (Flag type : Flag.values()) {
                if (type.getFlag() == value) {
                    return type;
                }
            }

            return null;
        }
    }

    public Flag flag;

    public long presentationTimeStamp;

    public byte[] data;

    // create packet from byte array
    public static VideoPacket fromArray(byte[] values) {
        // should be a type value - 1 byte
        byte typeValue = values[6];
        // should be a flag value - 1 byte
        byte flagValue = values[7];

        VideoPacket videoPacket = new VideoPacket();
        videoPacket.type = Type.getType(typeValue);
        videoPacket.flag = Flag.getFlag(flagValue);

        // should be 8 bytes for timestamp
        byte[] timeStamp = new byte[8];
        System.arraycopy(values, 8, timeStamp, 0, 8);
        videoPacket.presentationTimeStamp = ByteUtils.bytesToLong(timeStamp);

        // all other bytes is data
        int dataLength = values.length - 16;
        byte[] data = new byte[dataLength];
        System.arraycopy(values, 16, data, 0, dataLength);
        videoPacket.data = data;

        return videoPacket;
    }

    // create byte array
    public static byte[] toArray(Type type, Flag flag, long presentationTimeStamp, byte[] data) {
        int packetSize = 16 + data.length; // 6 - header + 1 - type + 1 - flag + 8 - timeStamp + data.length
        byte[] values = new byte[packetSize];

        // set packet header
        System.arraycopy(PACKET_HEADER, 0, values, 0, PACKET_HEADER.length);

        // set type value
        values[6] = type.getType();
        // set flag value
        values[7] = flag.getFlag();
        // set timeStamp
        byte[] longToBytes = ByteUtils.longToBytes(presentationTimeStamp);
        System.arraycopy(longToBytes, 0, values, 8, longToBytes.length);

        // set data array
        System.arraycopy(data, 0, values, 16, data.length);
        return values;
    }

    public static boolean isVideoPacket(byte[] values) {
        return values[0] == Type.VIDEO.getType();
    }
}
