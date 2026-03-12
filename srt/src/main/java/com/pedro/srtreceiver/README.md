# SRT Receiver Implementation

This package implements an SRT listener/receiver for Android, creating a complete pipeline:
SRT → MPEG-TS → PES → H264/AAC → MediaCodec → SurfaceView + AudioTrack

## Requirements

### LibSRT Dependency

This implementation requires **libsrt** (Secure Reliable Transport library) to be built for Android.

#### Building libsrt for Android

1. Clone the SRT repository:
   ```bash
   git clone https://github.com/Haivision/srt.git
   cd srt
   ```

2. Build for Android using NDK. You need to build for each ABI:
   - armeabi-v7a
   - arm64-v8a
   - x86
   - x86_64

3. Place the compiled `libsrt.so` files in the appropriate directories:
   ```
   srt/src/main/jniLibs/armeabi-v7a/libsrt.so
   srt/src/main/jniLibs/arm64-v8a/libsrt.so
   srt/src/main/jniLibs/x86/libsrt.so
   srt/src/main/jniLibs/x86_64/libsrt.so
   ```

4. Copy SRT header files to:
   ```
   srt/src/main/cpp/srt-include/srt/
   ```

#### Alternative: Use Pre-built libsrt

You can also use pre-built libsrt binaries if available for Android.

## Usage

```kotlin
import com.pedro.srtreceiver.SrtReceiver
import android.view.SurfaceView

// In your Activity or Fragment
val surfaceView: SurfaceView = findViewById(R.id.surfaceView)
val receiver = SrtReceiver(surfaceView)

// Start receiving on port 9991
receiver.start(9991)

// To stop
receiver.stop()
```

### Sender Configuration

From another device or computer, push SRT stream as caller:

```bash
ffmpeg -i input.mp4 -c copy -f mpegts "srt://DEVICE_IP:9991?mode=caller"
```

Or using OBS Studio:
- Settings → Stream
- Service: Custom
- Server: `srt://DEVICE_IP:9991?mode=caller`
- Stream Key: (leave empty)

## Implementation Details

### Components

1. **SrtServerSocket** - JNI wrapper for libsrt, implements listener mode
2. **BlockingByteQueue** - Thread-safe queue for data flow between threads
3. **TsDemuxer** - MPEG-TS demultiplexer
4. **TsPacket** - TS packet data structure
5. **PatParser** - Program Association Table parser
6. **PmtParser** - Program Map Table parser  
7. **PesAssembler** - PES packet reassembler with PTS extraction
8. **H264Parser** - H.264 Annex-B NAL unit parser
9. **AacParser** - AAC ADTS frame parser
10. **VideoDecoder** - MediaCodec H.264 decoder
11. **AudioDecoder** - MediaCodec AAC decoder + AudioTrack
12. **SrtReceiver** - Main orchestrator class

### Pipeline Flow

```
SRT Socket (port listening)
    ↓
Receive Thread (1316 byte buffer = 7 TS packets)
    ↓
BlockingByteQueue
    ↓
Demux Thread
    ↓
TsDemuxer (PAT/PMT/PES parsing)
    ↓
H264Parser / AacParser
    ↓
VideoDecoder / AudioDecoder (MediaCodec)
    ↓
SurfaceView / AudioTrack
```

### Threading Model

- **SRT Receive Thread**: Blocks on SRT socket receive, pushes to queue
- **Demux Thread**: Pulls from queue, demuxes TS packets
- **Decode Thread**: Implicit in MediaCodec (handles decoding in background)

### Performance Optimizations

- Low latency mode (120ms)
- Timestamp-based packet delivery enabled
- No frame reordering
- Small buffer sizes
- Direct surface rendering

## Minimum API Level

API 24 (Android 7.0) or higher is recommended for best MediaCodec support.

## Permissions

Add to your AndroidManifest.xml:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## Troubleshooting

### No video displayed
- Check that SPS/PPS are received (check logs for "H264 config ready")
- Verify SurfaceView is properly initialized and visible
- Check that sender is encoding H.264 (AVC)

### No audio
- Verify sender is encoding AAC
- Check AudioTrack initialization in logs
- Ensure audio codec parameters match

### Connection issues
- Verify firewall allows incoming connections on specified port
- Check that sender is using `mode=caller` in SRT URL
- Verify network connectivity

### Build issues
- Ensure libsrt.so is present for all target ABIs
- Verify SRT headers are in correct directory
- Check NDK is properly configured

## License

This implementation follows the same license as the RootEncoder project.
