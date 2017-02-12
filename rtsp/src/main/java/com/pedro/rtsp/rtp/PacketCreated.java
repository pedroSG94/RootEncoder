package com.pedro.rtsp.rtp;

import java.nio.ByteBuffer;

/**
 * Created by pedro on 10/02/17.
 */

public interface PacketCreated {
    void onAccPacketCreated(ByteBuffer buffer);

    void onH264PacketCreated(ByteBuffer buffer);
}
