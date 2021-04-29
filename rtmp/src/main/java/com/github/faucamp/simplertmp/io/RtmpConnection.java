package com.github.faucamp.simplertmp.io;

import android.util.Log;
import com.github.faucamp.simplertmp.RtmpPublisher;
import com.github.faucamp.simplertmp.Util;
import com.github.faucamp.simplertmp.amf.AmfMap;
import com.github.faucamp.simplertmp.amf.AmfNull;
import com.github.faucamp.simplertmp.amf.AmfNumber;
import com.github.faucamp.simplertmp.amf.AmfObject;
import com.github.faucamp.simplertmp.amf.AmfString;
import com.github.faucamp.simplertmp.packets.Abort;
import com.github.faucamp.simplertmp.packets.Audio;
import com.github.faucamp.simplertmp.packets.Command;
import com.github.faucamp.simplertmp.packets.Data;
import com.github.faucamp.simplertmp.packets.Handshake;
import com.github.faucamp.simplertmp.packets.RtmpPacket;
import com.github.faucamp.simplertmp.packets.UserControl;
import com.github.faucamp.simplertmp.packets.Video;
import com.github.faucamp.simplertmp.packets.WindowAckSize;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.ossrs.rtmp.BitrateManager;
import net.ossrs.rtmp.ConnectCheckerRtmp;
import net.ossrs.rtmp.CreateSSLSocket;

/**
 * Main RTMP connection implementation class
 *
 * @author francois, leoma, pedro
 */
public class RtmpConnection implements RtmpPublisher {

  private static final String TAG = "RtmpConnection";
  private static final Pattern rtmpUrlPattern =
      Pattern.compile("^rtmps?://([^/:]+)(?::(\\d+))*/([^/]+)/?([^*]*)$");

  private int port;
  private String host;
  private String appName;
  private String streamName;
  private String publishType;
  private String tcUrl;
  private Socket socket;
  private final RtmpSessionInfo rtmpSessionInfo = new RtmpSessionInfo();
  private final RtmpDecoder rtmpDecoder = new RtmpDecoder(rtmpSessionInfo);
  private BufferedInputStream inputStream;
  private BufferedOutputStream outputStream;
  private Thread rxPacketHandler;
  private volatile boolean connected = false;
  private volatile boolean publishPermitted = false;
  private final Object connectingLock = new Object();
  private final Object publishLock = new Object();
  private int currentStreamId = 0;
  private int transactionIdCounter = 0;
  private int videoWidth;
  private int videoHeight;
  private final ConnectCheckerRtmp connectCheckerRtmp;
  //for secure transport
  private boolean tlsEnabled;
  //for auth
  private String user = null;
  private String password = null;
  private boolean onAuth = false;
  private String netConnectionDescription;
  private final BitrateManager bitrateManager;
  private boolean isEnableLogs = true;

  public RtmpConnection(ConnectCheckerRtmp connectCheckerRtmp) {
    this.connectCheckerRtmp = connectCheckerRtmp;
    bitrateManager = new BitrateManager(connectCheckerRtmp);
  }

  private void handshake(InputStream in, OutputStream out) throws IOException {
    Handshake handshake = new Handshake();
    handshake.writeC0(out);
    handshake.writeC1(out); // Write C1 without waiting for S0
    out.flush();
    handshake.readS0(in);
    handshake.readS1(in);
    handshake.writeC2(out);
    out.flush();
    handshake.readS2(in);
  }

  private String getAppName(String app, String name) {
    if (!name.contains("/")) {
      return app;
    } else {
      return app + "/" + name.substring(0, name.indexOf("/"));
    }
  }

  private String getStreamName(String name) {
    if (!name.contains("/")) {
      return name;
    } else {
      return name.substring(name.indexOf("/") + 1);
    }
  }

  private String getTcUrl(String url) {
    if (url.endsWith("/")) {
      return url.substring(0, url.length() - 1);
    } else {
      return url;
    }
  }

  @Override
  public boolean connect(String url) {
    if (url == null) {
      connectCheckerRtmp.onConnectionFailedRtmp(
          "Endpoint malformed, should be: rtmp://ip:port/appname/streamname");
      return false;
    }
    Matcher rtmpMatcher = rtmpUrlPattern.matcher(url);
    if (rtmpMatcher.matches()) {
      tlsEnabled = rtmpMatcher.group(0).startsWith("rtmps");
    } else {
      connectCheckerRtmp.onConnectionFailedRtmp(
          "Endpoint malformed, should be: rtmp://ip:port/appname/streamname");
      return false;
    }

    host = rtmpMatcher.group(1);
    String portStr = rtmpMatcher.group(2);
    port = portStr != null ? Integer.parseInt(portStr) : 1935;
    appName = getAppName(rtmpMatcher.group(3), rtmpMatcher.group(4));
    String streamName = getStreamName(rtmpMatcher.group(4));
    tcUrl = getTcUrl(rtmpMatcher.group(0).substring(0, rtmpMatcher.group(0).length() - streamName.length()));
    this.streamName = streamName;

    // socket connection
    Log.d(TAG, "connect() called. Host: "
        + host
        + ", port: "
        + port
        + ", appName: "
        + appName
        + ", publishPath: "
        + streamName);
    rtmpSessionInfo.reset();
    if (!establishConnection()) return false;
    // Start the "main" handling thread
    rxPacketHandler = new Thread(new Runnable() {

      @Override
      public void run() {
        Log.d(TAG, "starting main rx handler loop");
        handleRxPacketLoop();
      }
    });
    rxPacketHandler.start();
    return rtmpConnect();
  }

  private boolean establishConnection() {
    try {
      if (!tlsEnabled) {
        socket = new Socket();
        SocketAddress socketAddress = new InetSocketAddress(host, port);
        socket.connect(socketAddress, 5000);
      } else {
        socket = CreateSSLSocket.createSSlSocket(host, port);
        if (socket == null) throw new IOException("Socket creation failed");
      }
      inputStream = new BufferedInputStream(socket.getInputStream());
      outputStream = new BufferedOutputStream(socket.getOutputStream());
      Log.d(TAG, "connect(): socket connection established, doing handhake...");
      handshake(inputStream, outputStream);
      Log.d(TAG, "connect(): handshake done");
      return true;
    } catch (Exception e) {
      Log.e(TAG, "Error", e);
      connectCheckerRtmp.onConnectionFailedRtmp("Connect error, " + e.getMessage());
      return false;
    }
  }

  private boolean rtmpConnect() {
    if (connected) {
      connectCheckerRtmp.onConnectionFailedRtmp("Already connected");
      return false;
    }

    sendConnect("");
    synchronized (connectingLock) {
      try {
        connectingLock.wait(getConnectionTimeoutMs());
      } catch (InterruptedException ex) {
        // do nothing
      }
    }
    if (!connected) {
      shutdown(true);
      connectCheckerRtmp.onConnectionFailedRtmp("Fail to connect, time out");
    }
    return connected;
  }

  private void sendConnect(String user) {
    ChunkStreamInfo.markSessionTimestampTx();
    Log.d(TAG, "rtmpConnect(): Building 'connect' invoke packet");
    ChunkStreamInfo chunkStreamInfo =
        rtmpSessionInfo.getChunkStreamInfo(ChunkStreamInfo.RTMP_CID_OVER_STREAM);
    Command invoke = new Command("connect", ++transactionIdCounter, chunkStreamInfo);
    invoke.getHeader().setMessageStreamId(0);
    AmfObject args = new AmfObject();
    args.setProperty("app", appName + user);
    args.setProperty("flashVer", "FMLE/3.0 (compatible; Lavf57.56.101)");
    args.setProperty("swfUrl", "");
    args.setProperty("tcUrl", tcUrl + user);
    args.setProperty("fpad", false);
    args.setProperty("capabilities", 239);
    args.setProperty("audioCodecs", 3191);
    args.setProperty("videoCodecs", 252);
    args.setProperty("videoFunction", 1);
    args.setProperty("pageUrl", "");
    args.setProperty("objectEncoding", 0);
    invoke.addData(args);
    sendRtmpPacket(invoke);
  }

  private String getAdobeAuthUserResult(String user, String password, String salt, String challenge,
      String opaque) {
    String challenge2 = String.format("%08x", new Random().nextInt());
    String response = Util.stringToMD5BASE64(user + salt + password);
    if (!opaque.isEmpty()) {
      response += opaque;
    } else if (!challenge.isEmpty()) {
      response += challenge;
    }
    response = Util.stringToMD5BASE64(response + challenge2);
    String result = "?authmod=adobe&user=" + user + "&challenge=" + challenge2 + "&response=" + response;
    if (!opaque.isEmpty()) {
      result += "&opaque=" + opaque;
    }
    return result;
  }

  /**
   * Limelight auth. This auth is closely to Digest auth
   *  http://tools.ietf.org/html/rfc2617
   *  http://en.wikipedia.org/wiki/Digest_access_authentication
   *
   *  https://github.com/ossrs/librtmp/blob/feature/srs/librtmp/rtmp.c
   */
  private String getLlnwAuthUserResult(String user, String password, String nonce, String app) {
    String authMod = "llnw";
    String realm = "live";
    String method = "publish";
    String qop = "auth";
    String ncHex = String.format("%08x", 1);
    String cNonce = String.format("%08x", new Random().nextInt());
    String path = app;
    //extract query parameters
    int queryPos = path.indexOf("?");
    if (queryPos >= 0) path = path.substring(0, queryPos);

    if (!path.contains("/")) path += "/_definst_";
    String hash1 = Util.getMd5Hash(user + ":" + realm + ":" + password);
    String hash2 = Util.getMd5Hash(method + ":/" + path);
    String hash3 = Util.getMd5Hash(hash1 + ":" + nonce + ":" + ncHex + ":" + cNonce + ":" + qop + ":" + hash2);
    return "?authmod=" + authMod + "&user=" + user + "&nonce=" + nonce + "&cnonce=" + cNonce + "&nc=" + ncHex + "&response=" + hash3;
  }

  @Override
  public boolean publish(String type) {
    if (type == null) {
      connectCheckerRtmp.onConnectionFailedRtmp("Null publish type");
      return false;
    }
    publishType = type;
    return createStream();
  }

  private boolean createStream() {
    if (!connected || currentStreamId != 0) {
      connectCheckerRtmp.onConnectionFailedRtmp(
          "Create stream failed, connected= " + connected + ", StreamId= " + currentStreamId);
      return false;
    }
    netConnectionDescription = null;

    Log.d(TAG, "createStream(): Sending releaseStream command...");
    // transactionId == 2
    Command releaseStream = new Command("releaseStream", ++transactionIdCounter);
    releaseStream.getHeader().setChunkStreamId(ChunkStreamInfo.RTMP_CID_OVER_STREAM);
    releaseStream.addData(new AmfNull());  // command object: null for "createStream"
    releaseStream.addData(streamName);  // command object: null for "releaseStream"
    sendRtmpPacket(releaseStream);

    Log.d(TAG, "createStream(): Sending FCPublish command...");
    // transactionId == 3
    Command FCPublish = new Command("FCPublish", ++transactionIdCounter);
    FCPublish.getHeader().setChunkStreamId(ChunkStreamInfo.RTMP_CID_OVER_STREAM);
    FCPublish.addData(new AmfNull());  // command object: null for "FCPublish"
    FCPublish.addData(streamName);
    sendRtmpPacket(FCPublish);

    Log.d(TAG, "createStream(): Sending createStream command...");
    ChunkStreamInfo chunkStreamInfo =
        rtmpSessionInfo.getChunkStreamInfo(ChunkStreamInfo.RTMP_CID_OVER_CONNECTION);
    // transactionId == 4
    Command createStream = new Command("createStream", ++transactionIdCounter, chunkStreamInfo);
    createStream.addData(new AmfNull());  // command object: null for "createStream"
    sendRtmpPacket(createStream);

    // Waiting for "NetStream.Publish.Start" response.
    synchronized (publishLock) {
      try {
        publishLock.wait(5000);
      } catch (InterruptedException ex) {
        // do nothing
      }
    }
    if (!publishPermitted) {
      shutdown(true);
      if (netConnectionDescription != null && !netConnectionDescription.isEmpty()) {
        connectCheckerRtmp.onConnectionFailedRtmp(netConnectionDescription);
      } else {
        connectCheckerRtmp.onConnectionFailedRtmp(
            "Error configure stream, publish permitted failed");
      }
    }
    return publishPermitted;
  }

  private void fmlePublish() {
    if (!connected || currentStreamId == 0) {
      Log.e(TAG, "fmlePublish failed");
      return;
    }

    Log.d(TAG, "fmlePublish(): Sending publish command...");
    Command publish = new Command("publish", ++transactionIdCounter);
    publish.getHeader().setChunkStreamId(ChunkStreamInfo.RTMP_CID_OVER_STREAM);
    publish.getHeader().setMessageStreamId(currentStreamId);
    publish.addData(new AmfNull());  // command object: null for "publish"
    publish.addData(streamName);
    publish.addData(publishType);
    sendRtmpPacket(publish);
  }

  private void onMetaData() {
    if (!connected || currentStreamId == 0) {
      Log.e(TAG, "onMetaData failed");
      return;
    }

    Log.d(TAG, "onMetaData(): Sending empty onMetaData...");
    Data metadata = new Data("@setDataFrame");
    metadata.getHeader().setMessageStreamId(currentStreamId);
    metadata.addData("onMetaData");
    AmfMap ecmaArray = new AmfMap();
    ecmaArray.setProperty("duration", 0);
    ecmaArray.setProperty("width", videoWidth);
    ecmaArray.setProperty("height", videoHeight);
    ecmaArray.setProperty("videocodecid", 7);
    ecmaArray.setProperty("framerate", 30);
    ecmaArray.setProperty("videodatarate", 0);
    // @see FLV video_file_format_spec_v10_1.pdf
    // According to E.4.2.1 AUDIODATA
    // "If the SoundFormat indicates AAC, the SoundType should be 1 (stereo) and the SoundRate should be 3 (44 kHz).
    // However, this does not mean that AAC audio in FLV is always stereo, 44 kHz data. Instead, the Flash Player ignores
    // these values and extracts the channel and sample rate data is encoded in the AAC bit stream."
    ecmaArray.setProperty("audiocodecid", 10);
    ecmaArray.setProperty("audiosamplerate", 44100);
    ecmaArray.setProperty("audiosamplesize", 16);
    ecmaArray.setProperty("audiodatarate", 0);
    ecmaArray.setProperty("stereo", true);
    ecmaArray.setProperty("filesize", 0);
    metadata.addData(ecmaArray);
    sendRtmpPacket(metadata);
  }

  @Override
  public void close() {
    if (socket != null) {
      closeStream();
    }
    shutdown(true);
  }

  private void closeStream() {
    if (!connected || currentStreamId == 0 || !publishPermitted) {
      Log.e(TAG, "closeStream failed");
      return;
    }
    Log.d(TAG, "closeStream(): setting current stream ID to 0");
    Command closeStream = new Command("closeStream", ++transactionIdCounter);
    closeStream.getHeader().setChunkStreamId(ChunkStreamInfo.RTMP_CID_OVER_STREAM);
    closeStream.getHeader().setMessageStreamId(currentStreamId);
    closeStream.addData(new AmfNull());
    sendRtmpPacket(closeStream);
  }

  private synchronized void shutdown(boolean r) {
    if (socket != null) {
      try {
        // It will raise EOFException in handleRxPacketThread
        socket.shutdownInput();
        // It will raise SocketException in sendRtmpPacket
        socket.shutdownOutput();
      } catch (IOException | UnsupportedOperationException e) {
        Log.e(TAG, "Shutdown socket", e);
      }

      // shutdown rxPacketHandler
      if (rxPacketHandler != null) {
        rxPacketHandler.interrupt();
        try {
          rxPacketHandler.join(100);
        } catch (InterruptedException ie) {
          rxPacketHandler.interrupt();
        }
        rxPacketHandler = null;
      }

      // shutdown socket as well as its input and output stream
      try {
        socket.close();
        Log.d(TAG, "socket closed");
      } catch (IOException ex) {
        Log.e(TAG, "shutdown(): failed to close socket", ex);
      }
    }

    if (r) {
      reset();
    }
  }

  private void reset() {
    connected = false;
    publishPermitted = false;
    netConnectionDescription = null;
    tcUrl = null;
    appName = null;
    streamName = null;
    publishType = null;
    currentStreamId = 0;
    transactionIdCounter = 0;
    socket = null;
    rtmpSessionInfo.reset();
  }

  @Override
  public void publishAudioData(byte[] data, int size, int dts) {
    if (data == null
        || data.length == 0
        || dts < 0
        || !connected
        || currentStreamId == 0
        || !publishPermitted) {
      return;
    }
    Audio audio = new Audio();
    audio.setData(data, size);
    audio.getHeader().setAbsoluteTimestamp(dts);
    audio.getHeader().setMessageStreamId(currentStreamId);
    sendRtmpPacket(audio);
    //bytes to bits
    bitrateManager.calculateBitrate(size * 8);
  }

  @Override
  public void publishVideoData(byte[] data, int size, int dts) {
    if (data == null
        || data.length == 0
        || dts < 0
        || !connected
        || currentStreamId == 0
        || !publishPermitted) {
      return;
    }
    Video video = new Video();
    video.setData(data, size);
    video.getHeader().setAbsoluteTimestamp(dts);
    video.getHeader().setMessageStreamId(currentStreamId);
    sendRtmpPacket(video);
    //bytes to bits
    bitrateManager.calculateBitrate(size * 8);
  }

  private void sendRtmpPacket(RtmpPacket rtmpPacket) {
    try {
      ChunkStreamInfo chunkStreamInfo =
          rtmpSessionInfo.getChunkStreamInfo(rtmpPacket.getHeader().getChunkStreamId());
      chunkStreamInfo.setPrevHeaderTx(rtmpPacket.getHeader());
      if (!(rtmpPacket instanceof Video || rtmpPacket instanceof Audio)) {
        rtmpPacket.getHeader()
            .setAbsoluteTimestamp((int) chunkStreamInfo.markAbsoluteTimestampTx());
      }
      rtmpPacket.writeTo(outputStream, rtmpSessionInfo.getTxChunkSize(), chunkStreamInfo);
      if (isEnableLogs) {
        Log.d(TAG,
                "wrote packet: " + rtmpPacket + ", size: " + rtmpPacket.getHeader().getPacketLength());
      }
      if (rtmpPacket instanceof Command) {
        rtmpSessionInfo.addInvokedCommand(((Command) rtmpPacket).getTransactionId(),
            ((Command) rtmpPacket).getCommandName());
      }
      outputStream.flush();
    } catch (IOException ioe) {
      connectCheckerRtmp.onConnectionFailedRtmp("Error send packet: " + ioe.getMessage());
      Log.e(TAG, "Caught IOException during write loop, shutting down: " + ioe.getMessage());
      Thread.currentThread().interrupt();
    }
  }

  private void handleRxPacketLoop() {
    // Handle all queued received RTMP packets
    while (!Thread.interrupted()) {
      try {
        // It will be blocked when no data in input stream buffer
        RtmpPacket rtmpPacket = rtmpDecoder.readPacket(inputStream);
        if (rtmpPacket != null) {
          //Log.d(TAG, "handleRxPacketLoop(): RTMP rx packet message type: " + rtmpPacket.getHeader().getMessageType());
          switch (rtmpPacket.getHeader().getMessageType()) {
            case ABORT:
              rtmpSessionInfo.getChunkStreamInfo(((Abort) rtmpPacket).getChunkStreamId())
                  .clearStoredChunks();
              break;
            case USER_CONTROL_MESSAGE:
              UserControl user = (UserControl) rtmpPacket;
              switch (user.getType()) {
                case STREAM_BEGIN:
                  break;
                case PING_REQUEST:
                  ChunkStreamInfo channelInfo =
                      rtmpSessionInfo.getChunkStreamInfo(ChunkStreamInfo.RTMP_CID_PROTOCOL_CONTROL);
                  Log.d(TAG, "handleRxPacketLoop(): Sending PONG reply..");
                  UserControl pong = new UserControl(user, channelInfo);
                  sendRtmpPacket(pong);
                  break;
                case STREAM_EOF:
                  Log.i(TAG, "handleRxPacketLoop(): Stream EOF reached, closing RTMP writer...");
                  break;
                default:
                  // Ignore...
                  break;
              }
              break;
            case WINDOW_ACKNOWLEDGEMENT_SIZE:
              WindowAckSize windowAckSize = (WindowAckSize) rtmpPacket;
              int size = windowAckSize.getAcknowledgementWindowSize();
              Log.d(TAG, "handleRxPacketLoop(): Setting acknowledgement window size: " + size);
              rtmpSessionInfo.setAcknowledgmentWindowSize(size);
              break;
            case SET_PEER_BANDWIDTH:
              rtmpSessionInfo.setAcknowledgmentWindowSize(socket.getSendBufferSize());
              int acknowledgementWindowsize = rtmpSessionInfo.getAcknowledgementWindowSize();
              ChunkStreamInfo chunkStreamInfo =
                  rtmpSessionInfo.getChunkStreamInfo(ChunkStreamInfo.RTMP_CID_PROTOCOL_CONTROL);
              Log.d(TAG, "handleRxPacketLoop(): Send acknowledgement window size: "
                  + acknowledgementWindowsize);
              sendRtmpPacket(new WindowAckSize(acknowledgementWindowsize, chunkStreamInfo));
              // Set socket option. This line could produce bps calculation problems.
              //socket.setSendBufferSize(acknowledgementWindowsize);
              break;
            case COMMAND_AMF0:
              handleRxInvoke((Command) rtmpPacket);
              break;
            default:
              Log.w(TAG, "handleRxPacketLoop(): Not handling unimplemented/unknown packet of type: "
                  + rtmpPacket.getHeader().getMessageType());
              break;
          }
        }
      } catch (EOFException eof) {
        Thread.currentThread().interrupt();
      } catch (IOException e) {
        connectCheckerRtmp.onConnectionFailedRtmp("Error reading packet: " + e.getMessage());
        Log.e(TAG, "Caught SocketException while reading/decoding packet, shutting down: "
            + e.getMessage());
        Thread.currentThread().interrupt();
      }
    }
  }

  private void handleRxInvoke(Command invoke) {
    String commandName = invoke.getCommandName();
    switch (commandName) {
      case "_error":
        try {
          String description = ((AmfString) ((AmfObject) invoke.getData().get(1)).getProperty(
              "description")).getValue();
          Log.i(TAG, description);
          if (description.contains("reason=authfail") || description.contains("reason=nosuchuser")) {
            connectCheckerRtmp.onAuthErrorRtmp();
            connected = false;
            synchronized (connectingLock) {
              connectingLock.notifyAll();
            }
          } else if (user != null && password != null
              && description.contains("challenge=") && description.contains("salt=") //adobe response
              || description.contains("nonce=")) { //llnw response
            onAuth = true;
            try {
              shutdown(false);
            } catch (Exception e) {
              e.printStackTrace();
            }
            rtmpSessionInfo.reset();
            if (!tlsEnabled) {
              socket = new Socket(host, port);
            } else {
              socket = CreateSSLSocket.createSSlSocket(host, port);
              if (socket == null) throw new IOException("Socket creation failed");
            }
            inputStream = new BufferedInputStream(socket.getInputStream());
            outputStream = new BufferedOutputStream(socket.getOutputStream());
            Log.d(TAG, "connect(): socket connection established, doing handshake...");
            handshake(inputStream, outputStream);
            rxPacketHandler = new Thread(new Runnable() {
              @Override
              public void run() {
                handleRxPacketLoop();
              }
            });
            rxPacketHandler.start();
            if (description.contains("challenge=") && description.contains("salt=")) { //create adobe auth
              String salt = Util.getSalt(description);
              String challenge = Util.getChallenge(description);
              String opaque = Util.getOpaque(description);
              Log.i(TAG, "sending adobe auth response");
              sendConnect(getAdobeAuthUserResult(user, password, salt, challenge, opaque));
            } else if (description.contains("nonce=")){ //create llnw auth
              String nonce = Util.getNonce(description);
              Log.i(TAG, "sending llnw auth response");
              sendConnect(getLlnwAuthUserResult(user, password, nonce, appName));
            }
          } else if (description.contains("code=403")) {
            if (user != null && password != null) {
              // few servers close connection after send code=403
              if (socket == null || !socket.getKeepAlive()) {
                establishConnection();
              }
              if (description.contains("authmod=adobe")) {
                Log.i(TAG, "sending auth mode adobe");
                sendConnect("?authmod=adobe&user=" + user);
                return;
              } else if (description.contains("authmod=llnw")) {
                Log.i(TAG, "sending auth mode llnw");
                sendConnect("?authmod=llnw&user=" + user);
                return;
              }
            }
            connectCheckerRtmp.onAuthErrorRtmp();
            connected = false;
            synchronized (connectingLock) {
              connectingLock.notifyAll();
            }
          } else {
            connectCheckerRtmp.onConnectionFailedRtmp(description);
            connected = false;
            synchronized (connectingLock) {
              connectingLock.notifyAll();
            }
          }
        } catch (Exception e) {
          connectCheckerRtmp.onConnectionFailedRtmp(e.getMessage());
          connected = false;
          synchronized (connectingLock) {
            connectingLock.notifyAll();
          }
        }
        break;
      case "_result":
        // This is the result of one of the methods invoked by us
        String method = rtmpSessionInfo.takeInvokedCommand(invoke.getTransactionId());
        if (method == null) {
          Log.i(TAG, "'_result' message received for unknown method: ");
          return;
        }
        Log.i(TAG, "handleRxInvoke: Got result for invoked method: " + method);
        if ("connect".equals(method)) {
          if (onAuth) {
            connectCheckerRtmp.onAuthSuccessRtmp();
            onAuth = false;
          }
          // Capture server ip/pid/id information if any
          // We can now send createStream commands
          connected = true;
          synchronized (connectingLock) {
            connectingLock.notifyAll();
          }
        } else if ("createStream".contains(method)) {
          // Get stream id
          currentStreamId = (int) ((AmfNumber) invoke.getData().get(1)).getValue();
          Log.d(TAG, "handleRxInvoke(): Stream ID to publish: " + currentStreamId);
          if (streamName != null && publishType != null) {
            fmlePublish();
          }
        } else if ("releaseStream".contains(method)) {
          Log.d(TAG, "handleRxInvoke(): 'releaseStream'");
        } else if ("FCPublish".contains(method)) {
          Log.d(TAG, "handleRxInvoke(): 'FCPublish'");
        } else {
          Log.w(TAG, "handleRxInvoke(): '_result' message received for unknown method: " + method);
        }
        break;
      case "onBWDone":
        Log.d(TAG, "handleRxInvoke(): 'onBWDone'");
        break;
      case "onFCPublish":
        Log.d(TAG, "handleRxInvoke(): 'onFCPublish'");
        break;
      case "onStatus":
        String code =
            ((AmfString) ((AmfObject) invoke.getData().get(1)).getProperty("code")).getValue();
        Log.d(TAG, "handleRxInvoke(): onStatus " + code);
        switch (code) {
          case "NetStream.Publish.Start":
            onMetaData();
            // We can now publish AV data
            publishPermitted = true;
            synchronized (publishLock) {
              publishLock.notifyAll();
            }
            break;
          case "NetConnection.Connect.Rejected":
            netConnectionDescription =
                ((AmfString) ((AmfObject) invoke.getData().get(1)).getProperty(
                    "description")).getValue();
            publishPermitted = false;
            synchronized (publishLock) {
              publishLock.notifyAll();
            }
            break;
          case "NetStream.Publish.BadName":
            connectCheckerRtmp.onConnectionFailedRtmp("BadName received, endpoint in use.");
            break;
          default:
            connectCheckerRtmp.onConnectionFailedRtmp("Unknown on status received");
            break;
        }
        break;
      default:
        Log.e(TAG, "handleRxInvoke(): Unknown/unhandled server invoke: " + invoke);
        break;
    }
  }

  private long getConnectionTimeoutMs() {
    final long defaultTimeoutMs = 5_000;
    if (user != null && password != null) {
      // Authorized connection may take about 3 times longer than a regular, that's why the timeout is longer.
      return 3 * defaultTimeoutMs;
    } else {
      return defaultTimeoutMs;
    }
  }

  @Override
  public void setVideoResolution(int width, int height) {
    videoWidth = width;
    videoHeight = height;
  }

  @Override
  public void setAuthorization(String user, String password) {
    this.user = user;
    this.password = password;
  }

  public void setLogs(boolean enable) {
    isEnableLogs = enable;
  }
}
