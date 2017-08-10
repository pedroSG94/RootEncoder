# rtmp-rtsp-stream-client-java

[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-rtmp%20rtsp%20stream%20client%20java-green.svg?style=true)](https://android-arsenal.com/details/1/5333)
[![Release](https://jitpack.io/v/pedroSG94/rtmp-rtsp-stream-client-java.svg)](https://jitpack.io/#pedroSG94/rtmp-rtsp-stream-client-java)

Library for stream in rtmp and rtsp. All code in java.

If you need a player see this project:

https://github.com/pedroSG94/vlc-example-streamplayer

## Wiki (Becoming)

https://github.com/pedroSG94/rtmp-rtsp-stream-client-java/wiki

## Permissions:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

## Compile

To use this library in your project with gradle add this to your build.gradle:

```gradle
allprojects {
  repositories {
    maven { url 'https://jitpack.io' }
  }
}
dependencies {
  compile 'com.github.pedroSG94.rtmp-rtsp-stream-client-java:builder:1.0.8'
}

```

## Features:

- [x] Android min API 16.
- [x] Encoder type buffer to buffer.
- [x] Encoder type surface to buffer.
- [x] RTMP/RTSP auth wowza.
- [x] Audio noise suppressor.
- [x] Audio echo cancellation.
- [x] Disable/Enable video and audio while streaming.
- [x] Switch camera while streaming.
- [x] Change video bitrate while streaming (API 19+).
- [X] Record MP4 file while streaming (API 18+).
- [x] H264 and AAC hardware encoding.
- [x] RTSP TCP/UDP.
- [x] Stream from MP4 file (only video, no sound, API 18+).
- [x] Stream device display(API 21+).

### Backlog

- H265 support

## Use example:

### RTMP:

This code is a basic example. 
I recommend you go to Activities in app module and see all examples.

```java

//default

//create builder
RtmpBuilder rtmpBuilder = new RtmpBuilder(surfaceView, connectCheckerRtmp);
//start stream
if (rtmpBuilder.prepareAudio() && rtmpBuilder.prepareVideo()) {
  rtmpBuilder.startStream("rtmp://yourEndPoint");
} else {
 /**This device cant init encoders, this could be for 2 reasons: The encoder selected doesnt support any configuration setted or your device hasnt a H264 or AAC encoder (in this case you can see log error valid encoder not found)*/
}
//stop stream
rtmpBuilder.stopStream();

//with params

//create builder
RtmpBuilder rtmpBuilder = new RtmpBuilder(surfaceView, connectCheckerRtmp);
//start stream
if (rtmpBuilder.prepareAudio(int bitrate, int sampleRate, boolean isStereo, boolean echoCanceler,
      boolean noiseSuppressor) && rtmpBuilder.prepareVideo(int width, int height, int fps, int bitrate, boolean hardwareRotation, int rotation)) {
  rtmpBuilder.startStream("rtmp://yourEndPoint");
} else {
 /**This device cant init encoders, this could be for 2 reasons: The encoder selected doesnt support any configuration setted or your device hasnt a H264 or AAC encoder (in this case you can see log error valid encoder not found)*/
}
//stop stream
rtmpBuilder.stopStream();

```

### RTSP:

```java

//default

//create builder
RtspBuilder rtspBuilder = new RtspBuilder(surfaceView, protocol, connectCheckerRtsp);
//start stream
if (rtspBuilder.prepareAudio() && rtspBuilder.prepareVideo()) {
  rtspBuilder.startStream("rtsp://yourEndPoint");
} else {
 /**This device cant init encoders, this could be for 2 reasons: The encoder selected doesnt support any configuration setted or your device hasnt a H264 or AAC encoder (in this case you can see log error valid encoder not found)*/
}
//stop stream
rtspBuilder.stopStream();

//with params

//create builder
RtspBuilder rtspBuilder = new RtspBuilder(surfaceView, protocol, connectCheckerRtsp);
//start stream
if (rtspBuilder.prepareAudio(int bitrate, int sampleRate, boolean isStereo, boolean echoCanceler,
      boolean noiseSuppressor) && rtspBuilder.prepareVideo(int width, int height, int fps, int bitrate, boolean hardwareRotation, int rotation)) {
  rtspBuilder.startStream("rtsp://yourEndPoint");
} else {
 /**This device cant init encoders, this could be for 2 reasons: The encoder selected doesnt support any configuration setted or your device hasnt a H264 or AAC encoder (in this case you can see log error valid encoder not found)*/
}
//stop stream
rtspBuilder.stopStream();

```
