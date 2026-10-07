#include <cstddef>
#include <jni.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <sys/socket.h>

extern "C" JNIEXPORT jlong JNICALL
Java_com_pedro_common_socket_java_TcpInfo_nativeGetRttMicros(
        JNIEnv*, jobject, jint fd) {
    tcp_info info{};
    socklen_t length = sizeof(info);

    if (getsockopt(fd, IPPROTO_TCP, TCP_INFO, &info, &length) != 0) {
        return -1;
    }

    constexpr size_t requiredLength =
        offsetof(tcp_info, tcpi_rtt) + sizeof(info.tcpi_rtt);
    if (length < requiredLength) {
        return -1;
    }

    if (info.tcpi_state != TCP_ESTABLISHED) {
        return -1;
    }

    return static_cast<jlong>(info.tcpi_rtt);
}
