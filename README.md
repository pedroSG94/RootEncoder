# rtmp-rtsp-stream-client-java


Library for stream in rtmp and rtsp all code in java.


This library use encoder type buffer to buffer.


Tested on Samsung S7 and Wowza server.


# Permissions


```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.CAMERA" />
```

# Protocol data send


RTMP -> TCP


RTSP -> UDP/TCP

# Use example


RTMP:


```java
//create builder
RtmpBuilder rtmpBuilder = new RtmpBuilder(surfaceView, connectCheckerRtmp);
//start stream
if (rtmpBuilder.prepareAudio() && rtmpBuilder.prepareVideo()) {
  rtmpBuilder.startStream("rtmp://yourEndPoint");
} else {
 /**This device cant init encoders, this could be for 2 reasons: The encoder selected doesnt support some configuration setted like a colorformat (if you see log you can know if this is the reason) and you need select other encoder, or your device hasnt a H264 or ACC encoder (in this case you can see log error valid encoder not found)*/
}
//stop stream
rtmpBuilder.stopStream();

```


RTSP:


```java
//create builder
RtspBuilder rtspBuilder = new RtspBuilder(surfaceView, protocol, connectCheckerRtsp);
//start stream
if (rtspBuilder.prepareAudio() && rtspBuilder.prepareVideo()) {
  rtspBuilder.startStream("rtsp://yourEndPoint");
} else {
 /**This device cant init encoders, this could be for 2 reasons: The encoder selected doesnt support some configuration setted like a colorformat (if you see log you can know if this is the reason) and you need select other encoder, or your device hasnt a H264 or ACC encoder (in this case you can see log error valid encoder not found)*/
}
//stop stream
rtspBuilder.stopStream();

//update port destination getted in connection
@Override
public void onConnectionSuccessRtsp() { 
   rtspBuilder.updateDestination();
}

```
