#rtmp-rtsp-stream-client-java

[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-rtmp%20rtsp%20stream%20client%20java-green.svg?style=true)](https://android-arsenal.com/details/1/5333)

Library for stream in rtmp and rtsp all code in java.

Permissions:
----


```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.CAMERA" />
```

Features:
----

- [x] Encoder type buffer to buffer.
- [x] Android min API 16.
- [x] RTMP/RTSP auth wowza.
- [x] Disable/Enable video and audio.
- [x] Switch camera while streaming.
- [x] Change video bitrate while streaming (API 19+).
- [x] H264 and ACC hard encoding.
- [x] RTSP TCP/UDP


Use example:
----

RTMP:
----

```java
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

```

RTSP:
----

```java
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

//update port destination getted in connection
@Override
public void onConnectionSuccessRtsp() { 
   rtspBuilder.updateDestination();
}

```
