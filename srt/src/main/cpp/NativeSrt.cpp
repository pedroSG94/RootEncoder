#include "NativeSrt.h"
#include <srt/srt.h>
#include <android/log.h>
#include <cstring>
#include <arpa/inet.h>

#define LOG_TAG "NativeSrt"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jint JNICALL
Java_com_pedro_srtreceiver_SrtServerSocket_nativeInit(JNIEnv *env, jobject thiz) {
    int result = srt_startup();
    if (result < 0) {
        LOGE("srt_startup failed: %s", srt_getlasterror_str());
        return -1;
    }
    LOGI("SRT initialized successfully");
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_pedro_srtreceiver_SrtServerSocket_nativeStartServer(JNIEnv *env, jobject thiz, jint port) {
    SRTSOCKET server_fd = srt_create_socket();
    if (server_fd == SRT_INVALID_SOCK) {
        LOGE("srt_create_socket failed: %s", srt_getlasterror_str());
        return -1;
    }

    int yes = 1;
    srt_setsockopt(server_fd, 0, SRTO_RCVSYN, &yes, sizeof(yes));
    int latency = 120;
    srt_setsockopt(server_fd, 0, SRTO_LATENCY, &latency, sizeof(latency));
    int tsbpd = 1;
    srt_setsockopt(server_fd, 0, SRTO_TSBPDMODE, &tsbpd, sizeof(tsbpd));

    struct sockaddr_in sa;
    memset(&sa, 0, sizeof(sa));
    sa.sin_family = AF_INET;
    sa.sin_port = htons(port);
    sa.sin_addr.s_addr = INADDR_ANY;

    if (srt_bind(server_fd, (struct sockaddr *) &sa, sizeof(sa)) == SRT_ERROR) {
        LOGE("srt_bind failed on port %d: %s", port, srt_getlasterror_str());
        srt_close(server_fd);
        return -1;
    }

    if (srt_listen(server_fd, 1) == SRT_ERROR) {
        LOGE("srt_listen failed: %s", srt_getlasterror_str());
        srt_close(server_fd);
        return -1;
    }

    LOGI("SRT server started on port %d, fd=%d", port, server_fd);
    return server_fd;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_pedro_srtreceiver_SrtServerSocket_nativeAccept(JNIEnv *env, jobject thiz, jint serverFd) {
    struct sockaddr_storage client_addr;
    int addr_len = sizeof(client_addr);

    SRTSOCKET client_fd = srt_accept(serverFd, (struct sockaddr *) &client_addr, &addr_len);
    if (client_fd == SRT_INVALID_SOCK) {
        LOGE("srt_accept failed: %s", srt_getlasterror_str());
        return -1;
    }
    LOGI("Client accepted, fd=%d", client_fd);
    return client_fd;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_pedro_srtreceiver_SrtServerSocket_nativeRecv(JNIEnv *env, jobject thiz, jint socketFd,
                                                      jbyteArray buffer) {
    jsize bufferSize = env->GetArrayLength(buffer);
    jbyte *bufferPtr = env->GetByteArrayElements(buffer, nullptr);
    int received = srt_recvmsg(socketFd, (char *) bufferPtr, bufferSize);
    env->ReleaseByteArrayElements(buffer, bufferPtr, 0);

    if (received == SRT_ERROR) {
        int error = srt_getlasterror(nullptr);
        if (error != SRT_EASYNCRCV) LOGE("srt_recvmsg failed: %s", srt_getlasterror_str());
        return -1;
    }
    return received;
}

extern "C" JNIEXPORT void JNICALL
Java_com_pedro_srtreceiver_SrtServerSocket_nativeClose(JNIEnv *env, jobject thiz, jint socketFd) {
    if (socketFd >= 0) {
        srt_close(socketFd);
        LOGI("Server socket %d closed", socketFd);
    }
}

void srtLogHandler(void *opaque, int level, const char *file, int line, const char *area,
                   const char *message) {
    LOGI(" Detail Data : [%s] %s %s", file, message, area);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_pedro_srt_utils_SrtSocket_nativeOpen(JNIEnv *env, jobject thiz) {

    srt_startup();
    SRTSOCKET sock = srt_create_socket();
    if (sock == SRT_INVALID_SOCK) return -1;

    int yes = 1;
    srt_setsockopt(sock, 0, SRTO_REUSEADDR, &yes, sizeof(yes));

    int linger = 0;
    srt_setsockopt(sock, 0, SRTO_LINGER, &linger, sizeof(linger));
    srt_setsockopt(sock, 0, SRTO_SENDER, &yes, sizeof(yes));

    int transtype = SRTT_LIVE;
    srt_setsockopt(sock, 0, SRTO_TRANSTYPE, &transtype, sizeof(transtype));

    srt_setsockopt(sock, 0, SRTO_TLPKTDROP, &yes, sizeof(yes));

    int latency = 120; // 120ms
    srt_setsockopt(sock, 0, SRTO_LATENCY, &latency, sizeof(latency));
    srt_setsockopt(sock, 0, SRTO_PEERLATENCY, &latency, sizeof(latency));

    int tsbpd = 1;
    srt_setsockopt(sock, 0, SRTO_TSBPDMODE, &tsbpd, sizeof(tsbpd));

    int sndtimeo = 1000; //  giây
    srt_setsockopt(sock, 0, SRTO_SNDTIMEO, &sndtimeo, sizeof(sndtimeo));

    LOGI("Native SRT socket opened and optimized for LIVE: %d", sock);
    return (jint) sock;
}

// mode Listener
extern "C" JNIEXPORT jint JNICALL
Java_com_pedro_srt_utils_SrtSocket_nativeBindAndListen(JNIEnv *env, jobject thiz, jint sock,
                                                       jint port) {
    struct sockaddr_in sa;
    memset(&sa, 0, sizeof(sa));
    sa.sin_family = AF_INET;
    sa.sin_port = htons(port);
    sa.sin_addr.s_addr = INADDR_ANY; // Nghe trên mọi IP của điện thoại

    if (srt_bind((SRTSOCKET) sock, (struct sockaddr *) &sa, sizeof(sa)) == SRT_ERROR) {
        LOGE("SRT Bind failed: %s", srt_getlasterror_str());
        return -1;
    }
    if (srt_listen((SRTSOCKET) sock, 1) == SRT_ERROR) {
        LOGE("SRT Listen failed: %s", srt_getlasterror_str());
        return -1;
    }
    LOGI("SRT Server listening on port %d", port);
    return 0;
}

// wait for Accept
extern "C" JNIEXPORT jint JNICALL
Java_com_pedro_srt_utils_SrtSocket_nativeAccept(JNIEnv *env, jobject thiz, jint serverSock) {
    struct sockaddr_storage client_addr;
    int addr_len = sizeof(client_addr);

    LOGI("SRT waiting for APP to connect...");
    SRTSOCKET clientSock = srt_accept((SRTSOCKET) serverSock, (struct sockaddr *) &client_addr,
                                      &addr_len);

    if (clientSock == SRT_INVALID_SOCK) {
        LOGE("SRT Accept failed: %s", srt_getlasterror_str());
        return -1;
    }
    LOGI("APP connected! Client FD: %d", clientSock);
    return (jint) clientSock;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_pedro_srt_utils_SrtSocket_nativeConnect(JNIEnv *env, jobject thiz, jint sock, jstring host,
                                                 jint port, jstring streamId) {

    const char *nativeHost = env->GetStringUTFChars(host, 0);
    const char *nativeStreamId = env->GetStringUTFChars(streamId, 0);

    srt_setsockopt((SRTSOCKET) sock, 0, SRTO_STREAMID, nativeStreamId, strlen(nativeStreamId));

    struct sockaddr_in sa;
    memset(&sa, 0, sizeof(sa));
    sa.sin_family = AF_INET;
    sa.sin_port = htons(port);

    inet_pton(AF_INET, nativeHost, &sa.sin_addr);

    int result = srt_connect((SRTSOCKET) sock, (struct sockaddr *) &sa, sizeof(sa));

    srt_setloghandler(nullptr, srtLogHandler);
    srt_setloglevel(srt_logging::LogLevel::debug);

    if (result == SRT_ERROR) {
        LOGE("SRT Connect failed: %s", srt_getlasterror_str());
    } else {
        LOGI("SRT Connect success!");
    }

    env->ReleaseStringUTFChars(host, nativeHost);
    env->ReleaseStringUTFChars(streamId, nativeStreamId);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_pedro_srt_utils_SrtSocket_nativeWrite(JNIEnv *env, jobject thiz, jint sock,
                                               jbyteArray data) {
    jsize len = env->GetArrayLength(data);
    jbyte *buffer = env->GetByteArrayElements(data, nullptr);

    int result = srt_sendmsg((SRTSOCKET) sock, (const char *) buffer, len, -1, 0);

    if (result == SRT_ERROR) {
        LOGE("srt_sendmsg failed: %s", srt_getlasterror_str());
    }
    env->ReleaseByteArrayElements(data, buffer, 0);

    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_pedro_srt_utils_SrtSocket_nativeRead(JNIEnv *env, jobject thiz, jint sock,
                                              jbyteArray data) {
    jsize len = env->GetArrayLength(data);
    jbyte *buffer = env->GetByteArrayElements(data, nullptr);
    int result = srt_recvmsg((SRTSOCKET) sock, (char *) buffer, len);
    env->ReleaseByteArrayElements(data, buffer, 0);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_pedro_srt_utils_SrtSocket_nativeClose(JNIEnv *env, jobject thiz, jint sock) {
    if (sock >= 0) {
        srt_close((SRTSOCKET) sock);
        LOGI("Native socket %d closed", sock);
    }
}
