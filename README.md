# RootEncoder for Android

[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-rtmp%20rtsp%20stream%20client%20java-green.svg?style=true)](https://android-arsenal.com/details/1/5333)
[![Release](https://jitpack.io/v/pedroSG94/RootEncoder.svg)](https://jitpack.io/#pedroSG94/RootEncoder)
[![Documentation](https://img.shields.io/badge/library-documentation-orange)](https://pedroSG94.github.io/RootEncoder)

<p align="center">
<strong>Sponsored with 💖 &nbsp by</strong><br />
<a href="https://getstream.io/?utm_source=github.com/pedroSG94/rtmp-rtsp-stream-client-java&utm_medium=github&utm_campaign=oss_sponsorship" target="_blank">
<img src="https://stream-blog-v2.imgix.net/blog/wp-content/uploads/f7401112f41742c4e173c30d4f318cb8/stream_logo_white.png?w=350" alt="Stream Chat" style="margin: 8px" />
</a>
<br />
Enterprise Grade APIs for Feeds & Chat. <a href="https://getstream.io/tutorials/android-chat/?utm_source=https://github.com/pedroSG94/rtmp-rtsp-stream-client-java&utm_medium=github&utm_content=developer&utm_term=java" target="_blank">Try the Android Chat tutorial</a> 💬
</p>

</br>

RootEncoder (rtmp-rtsp-stream-client-java) is a stream encoder to push video/audio to media servers using protocols RTMP, RTSP and SRT with all code written in Java/Kotlin

Note: The library was renamed from rtmp-rtsp-stream-client-java to RootEncoder after add SRT protocol because the name has no sense anymore


## iOS version (under develop):

https://github.com/pedroSG94/RootEncoder-iOS

## Wiki

https://github.com/pedroSG94/RootEncoder/wiki

## Permissions:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.CAMERA" />
```

## Compile

To use this library in your project with gradle add this to your build.gradle:

<details open>
<summary>Last version</summary>

```gradle
allprojects {
  repositories {
    maven { url 'https://jitpack.io' }
  }
}
dependencies {
  implementation 'com.github.pedroSG94.RootEncoder:library:2.5.0'
}

```

</details>

<details close>
<summary>Old versions (2.2.6 or less)</summary>

```gradle
allprojects {
  repositories {
    maven { url 'https://jitpack.io' }
  }
}
dependencies {
  implementation 'com.github.pedroSG94.RootEncoder:rtplibrary:2.2.6'
}
```

</details>

## Features:

- [x] Android min API 16.

### Encoder:

- [x] Support [camera1](https://developer.android.com/reference/android/hardware/Camera.html) and [camera2](https://developer.android.com/reference/android/hardware/camera2/package-summary.html) API
- [x] Encoder type buffer to buffer.
- [x] Encoder type surface to buffer.
- [x] Audio noise suppressor.
- [x] Audio echo cancellation.
- [x] Disable/Enable video and audio while streaming.
- [x] Switch camera while streaming.
- [x] Change video bitrate while streaming (API 19+).
- [x] AV1, H264, H265, G711, AAC and OPUS hardware/software encoding.
- [x] Force video and audio Codec to use hardware/software encoding (Not recommended).
- [X] Record MP4 file while streaming (API 18+).
- [X] Set Image, Gif or Text to stream on real time.
- [X] OpenGL real time filters. [More info](https://github.com/pedroSG94/RootEncoder/wiki/Real-time-filters)
- [x] Stream from video and audio files like mp4, webm, mp3, etc (Limited by device decoders). [More info](https://github.com/pedroSG94/RootEncoder/wiki/Stream-from-file)
- [x] Stream device screen (API 21+).

### RTMP:

- [X] Get upload bandwidth used.
- [x] RTSP auth (adobe and llnw).
- [x] AV1, H265 ([Using RTMP enhanced](https://github.com/veovera/enhanced-rtmp/tree/main)), H264, AAC and G711 support.
- [x] RTMPS (under TLS)
- [x] RTMPT and RTMPTS (tunneled and tunneled under TLS)
- [x] AMF0
- [ ] AMF3

### RTSP:

- [X] Get upload bandwidth used.
- [x] RTMP auth (basic and digest).
- [x] AV1, H264, H265, AAC, G711 and OPUS support.
- [x] TCP/UDP.
- [x] RTSPS.

### SRT (beta):

- [X] Get upload bandwidth used.
- [X] H264, H265, AAC and OPUS support.
- [X] Resend lost packets
- [X] Encrypt (AES128, AES192 and AES256)
- [ ] SRT auth.

https://haivision.github.io/srt-rfc/draft-sharabayko-srt.html

### UDP (beta):

- [X] Get upload bandwidth used.
- [X] H264, H265, AAC and OPUS support.
- [X] Unicast, Multicast and Broadcast support.
- [X] MPEG2-TS support.


## Other related projects:

https://github.com/pedroSG94/RTSP-Server

### 3rd party projects:

Projects related with the library developed by other users.
Contact with user owner if you have any problem or question.

https://github.com/FunnyDevs/rtmp-rtsp-stream-client-java-recordcontrollers

## Real time filters:

### NOTE:
In library version 2.0.9, the filters was refactored. Check the wiki link to migrate your implementation.

https://github.com/pedroSG94/RootEncoder/wiki/Real-time-filters

## Looking for sponsors

This library need sponsors to get new devices or pay platforms to test and debug errors. Any donation or sponsor is welcome!
If you are interested. You can contact me by email or donate directly on [Github](https://github.com/sponsors/pedroSG94) or [Paypal](https://www.paypal.com/paypalme/pedroSG94)
Thank you!

## Use examples:

### Rotation example

This is the recommend way to use the library. 
This example support screen rotation, stream orientation (vertical, horizontal) filters and change video/audio sources on fly:
https://github.com/pedroSG94/RootEncoder/tree/master/app/src/main/java/com/pedro/streamer/rotation

### Screen example

Example to stream using Screen as video source using a service to stream in background:
https://github.com/pedroSG94/RootEncoder/tree/master/app/src/main/java/com/pedro/streamer/screen

### From file example

Code example to stream using a video file as video/audio source:
https://github.com/pedroSG94/RootEncoder/tree/master/app/src/main/java/com/pedro/streamer/file

### Old Api example

Code example for low API devices (Android API 16+):
https://github.com/pedroSG94/RootEncoder/tree/master/app/src/main/java/com/pedro/streamer/oldapi
