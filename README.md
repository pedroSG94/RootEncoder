# rtmp-rtsp-stream-client-java


Library for stream in rtmp and rtsp all code in java.


This library use encoder type buffer to buffer.


Tested on Samsung S7 and Wowza server.


# Permissions


```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.FLASHLIGHT"/>
```

# Protocol data send


RTMP -> TCP (never lost packet but more latency)


RTSP -> UDP (low latency but could lost packets)
