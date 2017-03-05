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
rtmpBuilder.prepareAudio();
rtmpBuilder.prepareVideo();
rtmpBuilder.startStream("rtmp://yourEndPoint");
//stop stream
rtmpBuilder.stopStream();

```


RTSP:


```java
//create builder
RtspBuilder rtspBuilder = new RtspBuilder(surfaceView, protocol, connectCheckerRtsp);
//start stream
rtspBuilder.prpareAudio();
rtspBuilder.prepareVideo();
rtspBuilder.startStream("rtsp://yourEndPoint");
//stop stream
rtspBuilder.stopStream();

//update port destination getted in connection
@Override
public void onConnectionSuccessRtsp() { 
   rtspBuilder.updateDestination();
}

```
