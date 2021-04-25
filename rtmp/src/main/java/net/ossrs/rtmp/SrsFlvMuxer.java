package net.ossrs.rtmp;

import android.media.MediaCodec;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import com.github.faucamp.simplertmp.DefaultRtmpPublisher;
import com.github.faucamp.simplertmp.RtmpPublisher;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Created by winlin on 5/2/15.
 * Updated by leoma on 4/1/16.
 * modified by pedro
 * to POST the h.264/avc annexb frame over RTMP.
 * modified by Troy
 * to accept any RtmpPublisher implementation.
 *
 * Usage:
 * muxer = new SrsRtmp("rtmp://ossrs.net/live/yasea");
 * muxer.start();
 *
 * MediaFormat aformat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate,
 * achannel);
 * // setup the aformat for audio.
 * atrack = muxer.addTrack(aformat);
 *
 * MediaFormat vformat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, vsize.width,
 * vsize.height);
 * // setup the vformat for video.
 * vtrack = muxer.addTrack(vformat);
 *
 * // encode the video frame from camera by h.264 codec to es and bi,
 * // where es is the h.264 ES(element stream).
 * ByteBuffer es, MediaCodec.BufferInfo bi;
 * muxer.writeSampleData(vtrack, es, bi);
 *
 * // encode the audio frame from microphone by aac codec to es and bi,
 * // where es is the aac ES(element stream).
 * ByteBuffer es, MediaCodec.BufferInfo bi;
 * muxer.writeSampleData(atrack, es, bi);
 *
 * muxer.stop();
 * muxer.release();
 */

public class SrsFlvMuxer {

  private static final String TAG = "SrsFlvMuxer";

  private static final int VIDEO_ALLOC_SIZE = 128 * 1024;
  private static final int AUDIO_ALLOC_SIZE = 4 * 1024;
  private volatile boolean connected = false;
  private RtmpPublisher publisher;
  private Thread worker;
  private SrsFlv flv = new SrsFlv();
  private boolean needToFindKeyFrame = true;
  private SrsAllocator mVideoAllocator = new SrsAllocator(VIDEO_ALLOC_SIZE);
  private SrsAllocator mAudioAllocator = new SrsAllocator(AUDIO_ALLOC_SIZE);
  private volatile BlockingQueue<SrsFlvFrame> mFlvVideoTagCache = new LinkedBlockingQueue<>(30);
  private volatile BlockingQueue<SrsFlvFrame> mFlvAudioTagCache = new LinkedBlockingQueue<>(30);
  private ConnectCheckerRtmp connectCheckerRtmp;
  private int sampleRate = 0;
  private boolean isPpsSpsSend = false;
  private byte profileIop = ProfileIop.BASELINE;
  private String url;
  //re connection
  private boolean doingRetry;
  private int numRetry;
  private int reTries;
  private Handler handler;
  private Runnable runnable;
  private boolean akamaiTs = false;

  private long mAudioFramesSent = 0;
  private long mVideoFramesSent = 0;
  private long mDroppedAudioFrames = 0;
  private long mDroppedVideoFrames = 0;
  private long startTs = 0;

  /**
   * constructor.
   */
  public SrsFlvMuxer(ConnectCheckerRtmp connectCheckerRtmp, RtmpPublisher publisher) {
    this.connectCheckerRtmp = connectCheckerRtmp;
    this.publisher = publisher;
    handler = new Handler(Looper.getMainLooper());
  }

  public SrsFlvMuxer(ConnectCheckerRtmp connectCheckerRtmp) {
    this(connectCheckerRtmp, new DefaultRtmpPublisher(connectCheckerRtmp));
  }

  public void setProfileIop(byte profileIop) {
    this.profileIop = profileIop;
  }

  public void setSpsPPs(ByteBuffer sps, ByteBuffer pps) {
    flv.setSpsPPs(sps, pps);
  }

  public void setSampleRate(int sampleRate) {
    this.sampleRate = sampleRate;
  }

  public void setIsStereo(boolean isStereo) {
    int channel = (isStereo) ? 2 : 1;
    flv.setAchannel(channel);
  }

  public void forceAkamaiTs(boolean enabled) {
    akamaiTs = enabled;
  }

  public void setAuthorization(String user, String password) {
    publisher.setAuthorization(user, password);
  }

  public boolean isConnected() {
    return connected;
  }

  public void resizeFlvTagCache(int newSize) {
    synchronized (mFlvAudioTagCache) {
      mFlvAudioTagCache = resizeFlvTagCacheInternal(mFlvAudioTagCache, newSize);
    }
    synchronized (mFlvVideoTagCache) {
      mFlvVideoTagCache = resizeFlvTagCacheInternal(mFlvVideoTagCache, newSize);
    }
  }

  private BlockingQueue<SrsFlvFrame> resizeFlvTagCacheInternal(BlockingQueue<SrsFlvFrame> cache,
      int newSize) {
    if (newSize < cache.size() - cache.remainingCapacity()) {
      throw new RuntimeException("Can't fit current cache inside new cache size");
    }

    BlockingQueue<SrsFlvFrame> newQueue = new LinkedBlockingQueue<>(newSize);
    cache.drainTo(newQueue);
    return newQueue;
  }

  public int getFlvTagCacheSize() {
    return mFlvVideoTagCache.size() + mFlvAudioTagCache.size();
  }

  public long getSentAudioFrames() {
    return mAudioFramesSent;
  }

  public long getSentVideoFrames() {
    return mVideoFramesSent;
  }

  public long getDroppedAudioFrames() {
    return mDroppedAudioFrames;
  }

  public long getDroppedVideoFrames() {
    return mDroppedVideoFrames;
  }

  public void resetSentAudioFrames() {
    mAudioFramesSent = 0;
  }

  public void resetSentVideoFrames() {
    mVideoFramesSent = 0;
  }

  public void resetDroppedAudioFrames() {
    mDroppedAudioFrames = 0;
  }

  public void resetDroppedVideoFrames() {
    mDroppedVideoFrames = 0;
  }

  /**
   * set video resolution for publisher
   *
   * @param width width
   * @param height height
   */
  public void setVideoResolution(int width, int height) {
    publisher.setVideoResolution(width, height);
  }

  private void disconnect(ConnectCheckerRtmp connectChecker) {
    try {
      publisher.close();
    } catch (IllegalStateException e) {
      // Ignore illegal state.
    }
    connected = false;

    if (connectChecker != null) {
      reTries = numRetry;
      doingRetry = false;
      connectChecker.onDisconnectRtmp();
    }

    resetSentAudioFrames();
    resetSentVideoFrames();
    resetDroppedAudioFrames();
    resetDroppedVideoFrames();

    Log.i(TAG, "worker: disconnect ok.");
  }

  public void setReTries(int reTries) {
    numRetry = reTries;
    this.reTries = reTries;
  }

  public boolean shouldRetry(String reason) {
    boolean validReason = doingRetry && !reason.contains("Endpoint malformed");
    return validReason && reTries > 0;
  }

  public void reConnect(final long delay) {
    reConnect(delay, null);
  }

  public void reConnect(final long delay, final String backupUrl) {
    reTries--;
    stop(null);
    runnable = new Runnable() {
      @Override
      public void run() {
        String reconnectUrl = backupUrl != null ? backupUrl : url;
        start(reconnectUrl, true);
      }
    };
    handler.postDelayed(runnable, delay);
  }

  private boolean connect(String url) {
    this.url = url;
    if (!connected) {
      connectCheckerRtmp.onConnectionStartedRtmp(url);
      Log.i(TAG, String.format("worker: connecting to RTMP server by url=%s\n", url));
      if (publisher.connect(url)) {
        connected = publisher.publish("live");
      }
    }
    return connected;
  }

  private void sendFlvTag(SrsFlvFrame frame) {
    if (!connected || frame == null) {
      return;
    }

    int dts = akamaiTs ? (int) ((System.nanoTime() / 1000 - startTs) / 1000) : frame.dts;
    if (frame.is_video()) {
      if (frame.is_keyframe()) {
        Log.i(TAG, String.format("worker: send frame type=%d, dts=%d, size=%dB", frame.type, dts,
            frame.flvTag.array().length));
      }
      publisher.publishVideoData(frame.flvTag.array(), frame.flvTag.size(), dts);
      mVideoAllocator.release(frame.flvTag);
      mVideoFramesSent++;
    } else if (frame.is_audio()) {
      publisher.publishAudioData(frame.flvTag.array(), frame.flvTag.size(), dts);
      mAudioAllocator.release(frame.flvTag);
      mAudioFramesSent++;
    }
  }

  public void start(final String rtmpUrl) {
    start(rtmpUrl, false);
  }

  /**
   * start to the remote SRS for remux.
   */
  private void start(final String rtmpUrl, final boolean isRetry) {
    if (!isRetry) doingRetry = true;
    startTs = System.nanoTime() / 1000;
    worker = new Thread(new Runnable() {
      @Override
      public void run() {
        android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE);
        if (!connect(rtmpUrl)) {
          return;
        }
        if (isRetry) flv.retry();
        reTries = numRetry;
        connectCheckerRtmp.onConnectionSuccessRtmp();
        while (!Thread.interrupted()) {
          try {
            SrsFlvFrame frame = mFlvAudioTagCache.poll(1, TimeUnit.MILLISECONDS);
            if (frame != null) {
              sendFlvTag(frame);
            }

            frame = mFlvVideoTagCache.poll(1, TimeUnit.MILLISECONDS);
            if (frame != null) {
              sendFlvTag(frame);
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
      }
    });
    worker.start();
  }

  public void stop() {
    stop(connectCheckerRtmp);
  }

  /**
   * stop the muxer, disconnect RTMP connection.
   */
  private void stop(final ConnectCheckerRtmp connectCheckerRtmp) {
    startTs = 0;
    handler.removeCallbacks(runnable);
    if (worker != null) {
      worker.interrupt();
      try {
        worker.join(100);
      } catch (InterruptedException e) {
        worker.interrupt();
      }
      worker = null;
    }
    mFlvAudioTagCache.clear();
    mFlvVideoTagCache.clear();
    flv.reset();
    needToFindKeyFrame = true;
    Log.i(TAG, "SrsFlvMuxer closed");

    new Thread(new Runnable() {
      @Override
      public void run() {
        disconnect(connectCheckerRtmp);
      }
    }).start();
  }

  public void sendVideo(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
    flv.writeVideoSample(byteBuffer, bufferInfo);
  }

  public void sendAudio(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
    flv.writeAudioSample(byteBuffer, bufferInfo);
  }

  // E.4.3.1 VIDEODATA
  // Frame Type UB [4]
  // Type of video frame. The following values are defined:
  //     1 = key frame (for AVC, a seekable frame)
  //     2 = inter frame (for AVC, a non-seekable frame)
  //     3 = disposable inter frame (H.263 only)
  //     4 = generated key frame (reserved for server use only)
  //     5 = video info/command frame
  private class SrsCodecVideoAVCFrame {
    public final static int KeyFrame = 1;
    public final static int InterFrame = 2;
  }

  // AVCPacketType IF CodecID == 7 UI8
  // The following values are defined:
  //     0 = AVC sequence header
  //     1 = AVC NALU
  //     2 = AVC end of sequence (lower level NALU sequence ender is
  //         not required or supported)
  private class SrsCodecVideoAVCType {
    public final static int SequenceHeader = 0;
    public final static int NALU = 1;
  }

  /**
   * E.4.1 FLV Tag, page 75
   */
  private class SrsCodecFlvTag {
    // 8 = audio
    public final static int Audio = 8;
    // 9 = video
    public final static int Video = 9;
  }

  private class AudioSampleRate {
    public final static int R11025 = 11025;
    public final static int R12000 = 12000;
    public final static int R16000 = 16000;
    public final static int R22050 = 22050;
    public final static int R24000 = 24000;
    public final static int R32000 = 32000;
    public final static int R44100 = 44100;
    public final static int R48000 = 48000;
    public final static int R64000 = 64000;
    public final static int R88200 = 88200;
    public final static int R96000 = 96000;
  }

  // E.4.3.1 VIDEODATA
  // CodecID UB [4]
  // Codec Identifier. The following values are defined:
  //     2 = Sorenson H.263
  //     3 = Screen video
  //     4 = On2 VP6
  //     5 = On2 VP6 with alpha channel
  //     6 = Screen video version 2
  //     7 = AVC
  private class SrsCodecVideo {
    public final static int AVC = 7;
  }

  /**
   * the aac object type, for RTMP sequence header
   * for AudioSpecificConfig, @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 33
   * for audioObjectType, @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 23
   */
  private class SrsAacObjectType {
    public final static int AacLC = 2;
  }

  /**
   * Table 7-1 – NAL unit type codes, syntax element categories, and NAL unit type classes
   * H.264-AVC-ISO_IEC_14496-10-2012.pdf, page 83.
   */
  private class SrsAvcNaluType {
    // Unspecified
    public final static int Reserved = 0;

    // Coded slice of a non-IDR picture slice_layer_without_partitioning_rbsp( )
    public final static int NonIDR = 1;
    // Coded slice data partition A slice_data_partition_a_layer_rbsp( )
    public final static int DataPartitionA = 2;
    // Coded slice data partition B slice_data_partition_b_layer_rbsp( )
    public final static int DataPartitionB = 3;
    // Coded slice data partition C slice_data_partition_c_layer_rbsp( )
    public final static int DataPartitionC = 4;
    // Coded slice of an IDR picture slice_layer_without_partitioning_rbsp( )
    public final static int IDR = 5;
    // Supplemental enhancement information (SEI) sei_rbsp( )
    public final static int SEI = 6;
    // Sequence parameter set seq_parameter_set_rbsp( )
    public final static int SPS = 7;
    // Picture parameter set pic_parameter_set_rbsp( )
    public final static int PPS = 8;
    // Access unit delimiter access_unit_delimiter_rbsp( )
    public final static int AccessUnitDelimiter = 9;
    // End of sequence end_of_seq_rbsp( )
    public final static int EOSequence = 10;
    // End of stream end_of_stream_rbsp( )
    public final static int EOStream = 11;
    // Filler data filler_data_rbsp( )
    public final static int FilterData = 12;
    // Sequence parameter set extension seq_parameter_set_extension_rbsp( )
    public final static int SPSExt = 13;
    // Prefix NAL unit prefix_nal_unit_rbsp( )
    public final static int PrefixNALU = 14;
    // Subset sequence parameter set subset_seq_parameter_set_rbsp( )
    public final static int SubsetSPS = 15;
    // Coded slice of an auxiliary coded picture without partitioning slice_layer_without_partitioning_rbsp( )
    public final static int LayerWithoutPartition = 19;
    // Coded slice extension slice_layer_extension_rbsp( )
    public final static int CodedSliceExt = 20;
  }

  /**
   * the search result for annexb.
   */
  private class SrsAnnexbSearch {
    public int nb_start_code = 0;
    public boolean match = false;
  }

  /**
   * the demuxed tag frame.
   */
  private class SrsFlvFrameBytes {
    public ByteBuffer data;
    public int size;
  }

  /**
   * the muxed flv frame.
   */
  private class SrsFlvFrame {
    // the tag bytes.
    public SrsAllocator.Allocation flvTag;
    // the codec type for audio/aac and video/avc for instance.
    public int avc_aac_type;
    // the frame type, keyframe or not.
    public int frame_type;
    // the tag type, audio, video or data.
    public int type;
    // the dts in ms, tbn is 1000.
    public int dts;

    public boolean is_keyframe() {
      return is_video() && frame_type == SrsCodecVideoAVCFrame.KeyFrame;
    }

    public boolean is_sequenceHeader() {
      return avc_aac_type == 0;
    }

    public boolean is_video() {
      return type == SrsCodecFlvTag.Video;
    }

    public boolean is_audio() {
      return type == SrsCodecFlvTag.Audio;
    }
  }

  /**
   * the raw h.264 stream, in annexb.
   */
  private class SrsRawH264Stream {
    private final static String TAG = "SrsFlvMuxer";

    private SrsAnnexbSearch annexb = new SrsAnnexbSearch();
    private SrsFlvFrameBytes nalu_header = new SrsFlvFrameBytes();
    private SrsFlvFrameBytes seq_hdr = new SrsFlvFrameBytes();
    private SrsFlvFrameBytes sps_hdr = new SrsFlvFrameBytes();
    private SrsFlvFrameBytes sps_bb = new SrsFlvFrameBytes();
    private SrsFlvFrameBytes pps_hdr = new SrsFlvFrameBytes();
    private SrsFlvFrameBytes pps_bb = new SrsFlvFrameBytes();

    public boolean isSps(SrsFlvFrameBytes frame) {
      return frame.size >= 1 && (frame.data.get(0) & 0x1f) == SrsAvcNaluType.SPS;
    }

    public boolean isPps(SrsFlvFrameBytes frame) {
      return frame.size >= 1 && (frame.data.get(0) & 0x1f) == SrsAvcNaluType.PPS;
    }

    public SrsFlvFrameBytes muxNaluHeader(SrsFlvFrameBytes frame) {
      if (nalu_header.data == null) {
        nalu_header.data = ByteBuffer.allocate(4);
        nalu_header.size = 4;
      }
      nalu_header.data.rewind();

      // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
      // lengthSizeMinusOne, or NAL_unit_length, always use 4bytes size
      int NAL_unit_length = frame.size;

      // mux the avc NALU in "ISO Base Media File Format"
      // from H.264-AVC-ISO_IEC_14496-15.pdf, page 20
      // NALUnitLength
      nalu_header.data.putInt(NAL_unit_length);

      // reset the buffer.
      nalu_header.data.rewind();
      return nalu_header;
    }

    public void muxSequenceHeader(ByteBuffer sps, ByteBuffer pps,
        ArrayList<SrsFlvFrameBytes> frames) {
      // 5bytes sps/pps header:
      //      configurationVersion, AVCProfileIndication, profile_compatibility,
      //      AVCLevelIndication, lengthSizeMinusOne
      // 3bytes size of sps:
      //      numOfSequenceParameterSets, sequenceParameterSetLength(2B)
      // Nbytes of sps.
      //      sequenceParameterSetNALUnit
      // 3bytes size of pps:
      //      numOfPictureParameterSets, pictureParameterSetLength
      // Nbytes of pps:
      //      pictureParameterSetNALUnit

      // decode the SPS:
      // @see: 7.3.2.1.1, H.264-AVC-ISO_IEC_14496-10-2012.pdf, page 62
      if (seq_hdr.data == null) {
        seq_hdr.data = ByteBuffer.allocate(5);
        seq_hdr.size = 5;
      }
      seq_hdr.data.rewind();
      // @see: Annex A Profiles and levels, H.264-AVC-ISO_IEC_14496-10.pdf, page 205
      //      Baseline profile profile_idc is 66(0x42).
      //      Main profile profile_idc is 77(0x4d).
      //      Extended profile profile_idc is 88(0x58).
      byte profile_idc = sps.get(1);
      //u_int8_t constraint_set = frame[2];
      byte level_idc = sps.get(3);

      // generate the sps/pps header
      // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
      // configurationVersion
      seq_hdr.data.put((byte) 0x01);
      // AVCProfileIndication
      seq_hdr.data.put(profile_idc);
      // profile_compatibility
      seq_hdr.data.put(profileIop);
      // AVCLevelIndication
      seq_hdr.data.put(level_idc);
      // lengthSizeMinusOne, or NAL_unit_length, always use 4bytes size,
      // so we always set it to 0x03.
      seq_hdr.data.put((byte) 0x03);

      // reset the buffer.
      seq_hdr.data.rewind();
      frames.add(seq_hdr);

      // sps
      if (sps_hdr.data == null) {
        sps_hdr.data = ByteBuffer.allocate(3);
        sps_hdr.size = 3;
      }
      sps_hdr.data.rewind();
      // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
      // numOfSequenceParameterSets, always 1
      sps_hdr.data.put((byte) 0x01);
      // sequenceParameterSetLength
      sps_hdr.data.putShort((short) sps.array().length);

      sps_hdr.data.rewind();
      frames.add(sps_hdr);

      // sequenceParameterSetNALUnit
      sps_bb.size = sps.array().length;
      sps_bb.data = sps.duplicate();
      frames.add(sps_bb);

      // pps
      if (pps_hdr.data == null) {
        pps_hdr.data = ByteBuffer.allocate(3);
        pps_hdr.size = 3;
      }
      pps_hdr.data.rewind();
      // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
      // numOfPictureParameterSets, always 1
      pps_hdr.data.put((byte) 0x01);
      // pictureParameterSetLength
      pps_hdr.data.putShort((short) pps.array().length);

      pps_hdr.data.rewind();
      frames.add(pps_hdr);

      // pictureParameterSetNALUnit
      pps_bb.size = pps.array().length;
      pps_bb.data = pps.duplicate();
      frames.add(pps_bb);
    }

    public SrsAllocator.Allocation muxFlvTag(ArrayList<SrsFlvFrameBytes> frames, int frame_type,
        int avc_packet_type) {
      // for h264 in RTMP video payload, there is 5bytes header:
      //      1bytes, FrameType | CodecID
      //      1bytes, AVCPacketType
      //      3bytes, CompositionTime, the cts.
      // @see: E.4.3 Video Tags, video_file_format_spec_v10_1.pdf, page 78
      int size = 5;
      for (int i = 0; i < frames.size(); i++) {
        size += frames.get(i).size;
      }
      SrsAllocator.Allocation allocation = mVideoAllocator.allocate(size);

      // @see: E.4.3 Video Tags, video_file_format_spec_v10_1.pdf, page 78
      // Frame Type, Type of video frame.
      // CodecID, Codec Identifier.
      // set the rtmp header
      allocation.put((byte) ((frame_type << 4) | SrsCodecVideo.AVC));

      // AVCPacketType
      allocation.put((byte) avc_packet_type);

      // CompositionTime
      // pts = dts + cts, or
      // cts = pts - dts.
      // where cts is the header in rtmp video packet payload header.
      int cts = 0;
      allocation.put((byte) (cts >> 16));
      allocation.put((byte) (cts >> 8));
      allocation.put((byte) cts);

      // h.264 raw data.
      for (int i = 0; i < frames.size(); i++) {
        SrsFlvFrameBytes frame = frames.get(i);
        frame.data.rewind();
        int s = Math.min(frame.size, frame.data.remaining());
        frame.data.get(allocation.array(), allocation.size(), s);
        allocation.appendOffset(s);
      }

      return allocation;
    }

    private SrsAnnexbSearch searchStartcode(ByteBuffer bb, int size) {
      annexb.match = false;
      annexb.nb_start_code = 0;
      if (size - 4 > 0) {
        if (bb.get(0) == 0x00 && bb.get(1) == 0x00 && bb.get(2) == 0x00 && bb.get(3) == 0x01) {
          // match N[00] 00 00 00 01, where N>=0
          annexb.match = true;
          annexb.nb_start_code = 4;
        } else if (bb.get(0) == 0x00 && bb.get(1) == 0x00 && bb.get(2) == 0x01) {
          // match N[00] 00 00 01, where N>=0
          annexb.match = true;
          annexb.nb_start_code = 3;
        }
      }
      return annexb;
    }

    private SrsAnnexbSearch searchAnnexb(ByteBuffer bb, int size) {
      annexb.match = false;
      annexb.nb_start_code = 0;
      for (int i = bb.position(); i < size - 4; i++) {
        // not match.
        if (bb.get(i) != 0x00 || bb.get(i + 1) != 0x00) {
          continue;
        }
        // match N[00] 00 00 01, where N>=0
        if (bb.get(i + 2) == 0x01) {
          annexb.match = true;
          annexb.nb_start_code = i + 3 - bb.position();
          break;
        }
        // match N[00] 00 00 00 01, where N>=0
        if (bb.get(i + 2) == 0x00 && bb.get(i + 3) == 0x01) {
          annexb.match = true;
          annexb.nb_start_code = i + 4 - bb.position();
          break;
        }
      }
      return annexb;
    }

    public SrsFlvFrameBytes demuxAnnexb(ByteBuffer bb, int size, boolean isOnlyChkHeader) {
      SrsFlvFrameBytes tbb = new SrsFlvFrameBytes();
      if (bb.position() < size - 4) {
        // each frame must prefixed by annexb format.
        // about annexb, @see H.264-AVC-ISO_IEC_14496-10.pdf, page 211.
        SrsAnnexbSearch tbbsc =
            isOnlyChkHeader ? searchStartcode(bb, size) : searchAnnexb(bb, size);
        // tbbsc.nb_start_code always 4 , after 00 00 00 01
        if (!tbbsc.match || tbbsc.nb_start_code < 3) {
          Log.i(TAG, "annexb not match, Trying without it. Maybe isn't h264 buffer");
        } else {
          // the start codes.
          for (int i = 0; i < tbbsc.nb_start_code; i++) {
            bb.get();
          }
        }
        // find out the frame size.
        tbb.data = bb.slice();
        tbb.size = size - bb.position();
      }
      return tbb;
    }
  }

  /**
   * remux the annexb to flv tags.
   */
  private class SrsFlv {
    private SrsRawH264Stream avc = new SrsRawH264Stream();
    private ArrayList<SrsFlvFrameBytes> ipbs = new ArrayList<>();
    private SrsAllocator.Allocation audio_tag;
    private SrsAllocator.Allocation video_tag;
    private ByteBuffer Sps;
    private ByteBuffer Pps;
    private boolean aac_specific_config_got;
    private int achannel;

    public SrsFlv() {
      reset();
    }

    public void setAchannel(int achannel) {
      this.achannel = achannel;
    }

    public void reset() {
      Sps = null;
      Pps = null;
      isPpsSpsSend = false;
      aac_specific_config_got = false;
    }

    public void retry() {
      isPpsSpsSend = false;
      aac_specific_config_got = false;
    }

    public void writeAudioSample(final ByteBuffer bb, MediaCodec.BufferInfo bi) {
      int dts = (int) (bi.presentationTimeUs / 1000);

      audio_tag = mAudioAllocator.allocate(bi.size + 2);
      byte aac_packet_type = 1; // 1 = AAC raw
      if (!aac_specific_config_got) {
        // @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf
        // AudioSpecificConfig (), page 33
        // 1.6.2.1 AudioSpecificConfig
        // audioObjectType; 5 bslbf
        byte ch = (byte) (bi.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG ? bb.get(0) & 0xf8
            : (bb.get(0) & 0xf8) / 2);
        // 3bits left.

        // samplingFrequencyIndex; 4 bslbf
        // For the values refer to https://wiki.multimedia.cx/index.php/MPEG-4_Audio#Sampling_Frequencies
        byte samplingFrequencyIndex;
        switch (sampleRate) {
          case AudioSampleRate.R96000:
            samplingFrequencyIndex = 0x00;
            break;
          case AudioSampleRate.R88200:
            samplingFrequencyIndex = 0x01;
            break;
          case AudioSampleRate.R64000:
            samplingFrequencyIndex = 0x02;
            break;
          case AudioSampleRate.R48000:
            samplingFrequencyIndex = 0x03;
            break;
          case AudioSampleRate.R44100:
            samplingFrequencyIndex = 0x04;
            break;
          case AudioSampleRate.R32000:
            samplingFrequencyIndex = 0x05;
            break;
          case AudioSampleRate.R24000:
            samplingFrequencyIndex = 0x06;
            break;
          case AudioSampleRate.R22050:
            samplingFrequencyIndex = 0x07;
            break;
          case AudioSampleRate.R16000:
            samplingFrequencyIndex = 0x08;
            break;
          case AudioSampleRate.R12000:
            samplingFrequencyIndex = 0x09;
            break;
          case AudioSampleRate.R11025:
            samplingFrequencyIndex = 0x0a;
            break;
          default:
            // 44100 Hz shall be the fallback value when sampleRate is irregular.
            // not implemented: other sample rates might be possible with samplingFrequencyIndex = 0x0f.
            samplingFrequencyIndex = 0x04; // 4: 44100 Hz
        }
        ch |= (samplingFrequencyIndex >> 1) & 0x07;
        audio_tag.put(ch, 2);

        ch = (byte) ((samplingFrequencyIndex << 7) & 0x80);
        // 7bits left.

        // channelConfiguration; 4 bslbf
        byte channelConfiguration = 1;
        if (achannel == 2) {
          channelConfiguration = 2;
        }
        ch |= (channelConfiguration << 3) & 0x78;
        // 3bits left.

        // GASpecificConfig(), page 451
        // 4.4.1 Decoder configuration (GASpecificConfig)
        // frameLengthFlag; 1 bslbf
        // dependsOnCoreCoder; 1 bslbf
        // extensionFlag; 1 bslbf
        audio_tag.put(ch, 3);

        aac_specific_config_got = true;
        aac_packet_type = 0; // 0 = AAC sequence header

        writeAdtsHeader(audio_tag.array(), 4);
        audio_tag.appendOffset(7);
      } else {
        bb.get(audio_tag.array(), 2, bi.size);
        audio_tag.appendOffset(bi.size + 2);
      }

      byte sound_format = 10; // AAC
      byte sound_type = 0; // 0 = Mono sound
      if (achannel == 2) {
        sound_type = 1; // 1 = Stereo sound
      }
      byte sound_size = 1; // 1 = 16-bit samples
      byte sound_rate = 3; // 44100, 22050, 11025
      if (sampleRate == 22050) {
        sound_rate = 2;
      } else if (sampleRate == 11025) {
        sound_rate = 1;
      }

      // for audio frame, there is 1 or 2 bytes header:
      //      1bytes, SoundFormat|SoundRate|SoundSize|SoundType
      //      1bytes, AACPacketType for SoundFormat == 10, 0 is sequence header.
      byte audio_header = (byte) (sound_type & 0x01);
      audio_header |= (sound_size << 1) & 0x02;
      audio_header |= (sound_rate << 2) & 0x0c;
      audio_header |= (sound_format << 4) & 0xf0;

      audio_tag.put(audio_header, 0);
      audio_tag.put(aac_packet_type, 1);
      writeRtmpPacket(SrsCodecFlvTag.Audio, dts, 0, aac_packet_type, audio_tag);
    }

    private void writeAdtsHeader(byte[] frame, int offset) {
      // adts sync word 0xfff (12-bit)
      frame[offset] = (byte) 0xff;
      frame[offset + 1] = (byte) 0xf0;
      // version 0 for MPEG-4, 1 for MPEG-2 (1-bit)
      frame[offset + 1] |= 0 << 3;
      // layer 0 (2-bit)
      frame[offset + 1] |= 0 << 1;
      // protection absent: 1 (1-bit)
      frame[offset + 1] |= 1;
      // profile: audio_object_type - 1 (2-bit)
      frame[offset + 2] = (SrsAacObjectType.AacLC - 1) << 6;
      // sampling frequency index: 4 (4-bit)
      frame[offset + 2] |= (4 & 0xf) << 2;
      // channel configuration (3-bit)
      frame[offset + 2] |= (2 & (byte) 0x4) >> 2;
      frame[offset + 3] = (byte) ((2 & (byte) 0x03) << 6);
      // original: 0 (1-bit)
      frame[offset + 3] |= 0 << 5;
      // home: 0 (1-bit)
      frame[offset + 3] |= 0 << 4;
      // copyright id bit: 0 (1-bit)
      frame[offset + 3] |= 0 << 3;
      // copyright id start: 0 (1-bit)
      frame[offset + 3] |= 0 << 2;
      // frame size (13-bit)
      frame[offset + 3] |= ((frame.length - 2) & 0x1800) >> 11;
      frame[offset + 4] = (byte) (((frame.length - 2) & 0x7f8) >> 3);
      frame[offset + 5] = (byte) (((frame.length - 2) & 0x7) << 5);
      // buffer fullness (0x7ff for variable bitrate)
      frame[offset + 5] |= (byte) 0x1f;
      frame[offset + 6] = (byte) 0xfc;
      // number of data block (nb - 1)
      frame[offset + 6] |= 0x0;
    }

    public void writeVideoSample(final ByteBuffer bb, MediaCodec.BufferInfo bi) {
      if (bi.size < 4) return;

      bb.rewind();  //Sometimes the position is not 0.
      int pts = (int) (bi.presentationTimeUs / 1000);
      int type = SrsCodecVideoAVCFrame.InterFrame;
      SrsFlvFrameBytes frame = avc.demuxAnnexb(bb, bi.size, true);
      int nal_unit_type = frame.data.get(0) & 0x1f;
      if (nal_unit_type == SrsAvcNaluType.IDR || bi.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
        type = SrsCodecVideoAVCFrame.KeyFrame;
      } else if (nal_unit_type == SrsAvcNaluType.SPS || nal_unit_type == SrsAvcNaluType.PPS) {
        SrsFlvFrameBytes frame_pps = avc.demuxAnnexb(bb, bi.size, false);
        frame.size = frame.size - frame_pps.size - 4;  // 4 ---> 00 00 00 01 pps
        if (!frame.data.equals(Sps)) {
          byte[] sps = new byte[frame.size];
          frame.data.get(sps);
          isPpsSpsSend = false;
          Sps = ByteBuffer.wrap(sps);
        }

        SrsFlvFrameBytes frame_sei = avc.demuxAnnexb(bb, bi.size, false);
        if (frame_sei.size > 0) {
          if (SrsAvcNaluType.SEI == (frame_sei.data.get(0) & 0x1f)) {
            frame_pps.size = frame_pps.size - frame_sei.size - 3;// 3 ---> 00 00 01 SEI
          }
        }

        if (frame_pps.size > 0 && !frame_pps.data.equals(Pps)) {
          byte[] pps = new byte[frame_pps.size];
          frame_pps.data.get(pps);
          isPpsSpsSend = false;
          Pps = ByteBuffer.wrap(pps);
          writeH264SpsPps(pts);
        }
        return;
      } else if (nal_unit_type != SrsAvcNaluType.NonIDR) {
        return;
      }

      ipbs.add(avc.muxNaluHeader(frame));
      ipbs.add(frame);

      writeH264IpbFrame(ipbs, type, pts);
      ipbs.clear();
    }

    public void setSpsPPs(ByteBuffer sps, ByteBuffer pps) {
      Sps = sps;
      Pps = pps;
    }

    private void writeH264SpsPps(int pts) {
      // when not got sps/pps, wait.
      if (Pps == null || Sps == null || isPpsSpsSend) {
        return;
      }

      // h264 raw to h264 packet.
      ArrayList<SrsFlvFrameBytes> frames = new ArrayList<>();
      avc.muxSequenceHeader(Sps, Pps, frames);

      // h264 packet to flv packet.
      int frame_type = SrsCodecVideoAVCFrame.KeyFrame;
      int avc_packet_type = SrsCodecVideoAVCType.SequenceHeader;
      video_tag = avc.muxFlvTag(frames, frame_type, avc_packet_type);

      isPpsSpsSend = true;
      // the timestamp in rtmp message header is dts.
      writeRtmpPacket(SrsCodecFlvTag.Video, pts, frame_type, avc_packet_type, video_tag);
      Log.i(TAG, String.format("flv: h264 sps/pps sent, sps=%dB, pps=%dB", Sps.array().length,
          Pps.array().length));
    }

    private void writeH264IpbFrame(ArrayList<SrsFlvFrameBytes> frames, int frame_type, int dts) {
      // when sps or pps not sent, ignore the packet.
      // @see https://github.com/simple-rtmp-server/srs/issues/203
      if (Pps == null || Sps == null) {
        return;
      }
      video_tag = avc.muxFlvTag(frames, frame_type, SrsCodecVideoAVCType.NALU);
      // the timestamp in rtmp message header is dts.
      writeRtmpPacket(SrsCodecFlvTag.Video, dts, frame_type, SrsCodecVideoAVCType.NALU, video_tag);
    }

    private void writeRtmpPacket(int type, int dts, int frame_type, int avc_aac_type,
        SrsAllocator.Allocation tag) {
      SrsFlvFrame frame = new SrsFlvFrame();
      frame.flvTag = tag;
      frame.type = type;
      frame.dts = dts;
      frame.frame_type = frame_type;
      frame.avc_aac_type = avc_aac_type;
      if (frame.is_video()) {
        if (needToFindKeyFrame) {
          if (frame.is_keyframe()) {
            needToFindKeyFrame = false;
            flvFrameCacheAdd(frame);
          }
        } else {
          flvFrameCacheAdd(frame);
        }
      } else if (frame.is_audio()) {
        flvFrameCacheAdd(frame);
      }
    }

    private void flvFrameCacheAdd(SrsFlvFrame frame) {
      boolean wasFrameAdded = frame.is_video() ? mFlvVideoTagCache.offer(frame) : mFlvAudioTagCache.offer(frame);
      if(!wasFrameAdded) {
        Log.i(TAG, "frame discarded");
        if (frame.is_video()) {
          mDroppedVideoFrames++;
        } else {
          mDroppedAudioFrames++;
        }
      }
    }
  }

  public boolean hasCongestion() {
    float size = mFlvVideoTagCache.size();
    float remaining = mFlvVideoTagCache.remainingCapacity();
    float capacity = size + remaining;
    return size >= capacity * 0.2;  //more than 20% queue used. You could have congestion
  }

  public void setLogs(boolean enable) {
    publisher.setLogs(enable);
  }
}
