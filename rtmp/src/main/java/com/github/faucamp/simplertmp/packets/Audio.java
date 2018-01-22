package com.github.faucamp.simplertmp.packets;

import com.github.faucamp.simplertmp.io.ChunkStreamInfo;

/**
 * Audio data packet
 *  
 * @author francois, yuhsuan.lin
 */
public class Audio extends ContentData {

    public Audio(RtmpHeader header) {
        super(header);
    }

    public Audio() {
        super(new RtmpHeader(RtmpHeader.CHUNK_FULL, ChunkStreamInfo.RTMP_CID_AUDIO, RtmpHeader.MESSAGE_AUDIO));
    }

    @Override
    public String toString() {
        return "RTMP Audio";
    }
}
