package net.ossrs.rtmp;

/**
 * Created by pedro on 25/01/17.
 */

public interface ConnectChecker {

    void onConnectionSuccess();

    void onConnectionFailed();

    void onDisconnect();
}
