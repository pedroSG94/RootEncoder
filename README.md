# rtmp-rtsp-stream-client-java

[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-rtmp%20rtsp%20stream%20client%20java-green.svg?style=true)](https://android-arsenal.com/details/1/5333)

Library for stream in rtmp and rtsp all code in java.

Permissions:
----


```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.CAMERA" />
```

Compile
----

To use this library in your project with gradle add this to your build.gradle:

```gradle
allprojects {
  repositories {
    maven { url 'https://jitpack.io' }
  }
}
dependencies {
  compile 'com.github.pedroSG94.rtmp-rtsp-stream-client-java:builder:1.0.6'
}

```

Features:
----

- [x] Encoder type buffer to buffer.
- [x] Android min API 16.
- [x] RTMP/RTSP auth wowza.
- [x] Disable/Enable video and audio.
- [x] Audio noise suppressor.
- [x] Audio echo cancellation.
- [x] Switch camera while streaming.
- [x] Change video bitrate while streaming (API 19+).
- [x] H264 and ACC hard encoding.
- [x] RTSP TCP/UDP


# Use example:
----

RTMP:
----

```java

//default

//create builder
RtmpBuilder rtmpBuilder = new RtmpBuilder(surfaceView, connectCheckerRtmp);
//start stream
if (rtmpBuilder.prepareAudio() && rtmpBuilder.prepareVideo()) {
  rtmpBuilder.startStream("rtmp://yourEndPoint");
} else {
 /**This device cant init encoders, this could be for 2 reasons: The encoder selected doesnt support any configuration setted or your device hasnt a H264 or ACC encoder (in this case you can see log error valid encoder not found)*/
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
 /**This device cant init encoders, this could be for 2 reasons: The encoder selected doesnt support any configuration setted or your device hasnt a H264 or ACC encoder (in this case you can see log error valid encoder not found)*/
}
//stop stream
rtmpBuilder.stopStream();

```

RTSP:
----

```java

//default

//create builder
RtspBuilder rtspBuilder = new RtspBuilder(surfaceView, protocol, connectCheckerRtsp);
//start stream
if (rtspBuilder.prepareAudio() && rtspBuilder.prepareVideo()) {
  rtspBuilder.startStream("rtsp://yourEndPoint");
} else {
 /**This device cant init encoders, this could be for 2 reasons: The encoder selected doesnt support any configuration setted or your device hasnt a H264 or ACC encoder (in this case you can see log error valid encoder not found)*/
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
 /**This device cant init encoders, this could be for 2 reasons: The encoder selected doesnt support any configuration setted or your device hasnt a H264 or ACC encoder (in this case you can see log error valid encoder not found)*/
}
//stop stream
rtspBuilder.stopStream();

```
