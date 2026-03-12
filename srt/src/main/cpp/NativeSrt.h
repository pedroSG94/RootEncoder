#ifndef NATIVE_SRT_H
#define NATIVE_SRT_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL
Java_com_pedro_srtreceiver_SrtServerSocket_nativeInit(JNIEnv *env, jobject thiz);

JNIEXPORT jint JNICALL
Java_com_pedro_srtreceiver_SrtServerSocket_nativeStartServer(JNIEnv *env, jobject thiz, jint port);

JNIEXPORT jint JNICALL
Java_com_pedro_srtreceiver_SrtServerSocket_nativeAccept(JNIEnv *env, jobject thiz, jint serverFd);

JNIEXPORT jint JNICALL
Java_com_pedro_srtreceiver_SrtServerSocket_nativeRecv(JNIEnv *env, jobject thiz, jint socketFd,
                                                      jbyteArray buffer);

JNIEXPORT void JNICALL
Java_com_pedro_srtreceiver_SrtServerSocket_nativeClose(JNIEnv *env, jobject thiz, jint socketFd);

#ifdef __cplusplus
}
#endif

#endif // NATIVE_SRT_H
