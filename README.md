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
  compile 'com.github.pedroSG94.rtmp-rtsp-stream-client-java:rtplibrary:1.1.3'
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
RtmpCamera1 rtmpCamera1 = new RtmpCamera1(surfaceView, connectCheckerRtmp);
//start stream
if (rtmpCamera1.prepareAudio() && rtmpCamera1.prepareVideo()) {
  rtmpCamera1.startStream("rtmp://yourEndPoint");
} else {
 /**This device cant init encoders, this could be for 2 reasons: The encoder selected doesnt support any configuration setted or your device hasnt a H264 or AAC encoder (in this case you can see log error valid encoder not found)*/
}
//stop stream
rtmpCamera1.stopStream();

//with params

//create builder
RtmpCamera1 rtmpCamera1 = new RtmpCamera1(surfaceView, connectCheckerRtmp);
//start stream
if (rtmpCamera1.prepareAudio(int bitrate, int sampleRate, boolean isStereo, boolean echoCanceler,
      boolean noiseSuppressor) && rtmpCamera1.prepareVideo(int width, int height, int fps, int bitrate, boolean hardwareRotation, int rotation)) {
  rtmpCamera1.startStream("rtmp://yourEndPoint");
} else {
 /**This device cant init encoders, this could be for 2 reasons: The encoder selected doesnt support any configuration setted or your device hasnt a H264 or AAC encoder (in this case you can see log error valid encoder not found)*/
}
//stop stream
rtmpCamera1.stopStream();

```

### RTSP:

```java

//default

//create builder
RtspCamera1 rtspCamera1 = new RtspCamera1(surfaceView, protocol, connectCheckerRtsp);
//start stream
if (rtspCamera1.prepareAudio() && rtspCamera1.prepareVideo()) {
  rtspCamera1.startStream("rtsp://yourEndPoint");
} else {
 /**This device cant init encoders, this could be for 2 reasons: The encoder selected doesnt support any configuration setted or your device hasnt a H264 or AAC encoder (in this case you can see log error valid encoder not found)*/
}
//stop stream
rtspCamera1.stopStream();

//with params

//create builder
RtspCamera1 rtspCamera1 = new RtspCamera1(surfaceView, protocol, connectCheckerRtsp);
//start stream
if (rtspCamera1.prepareAudio(int bitrate, int sampleRate, boolean isStereo, boolean echoCanceler,
      boolean noiseSuppressor) && rtspCamera1.prepareVideo(int width, int height, int fps, int bitrate, boolean hardwareRotation, int rotation)) {
  rtspCamera1.startStream("rtsp://yourEndPoint");
} else {
 /**This device cant init encoders, this could be for 2 reasons: The encoder selected doesnt support any configuration setted or your device hasnt a H264 or AAC encoder (in this case you can see log error valid encoder not found)*/
}
//stop stream
rtspCamera1.stopStream();

```
