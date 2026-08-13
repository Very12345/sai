#include <jni.h>
#include <errno.h>
#include <pty.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <unistd.h>

#define EXPORT __attribute__((visibility("default")))

static char **copy_strings(JNIEnv *env, jobjectArray values) {
    const jsize count = (*env)->GetArrayLength(env, values);
    char **result = calloc((size_t) count + 1u, sizeof(char *));
    if (result == NULL) return NULL;
    for (jsize index = 0; index < count; index++) {
        jstring value = (jstring) (*env)->GetObjectArrayElement(env, values, index);
        const char *utf = (*env)->GetStringUTFChars(env, value, NULL);
        if (utf == NULL) return result;
        result[index] = strdup(utf);
        (*env)->ReleaseStringUTFChars(env, value, utf);
        (*env)->DeleteLocalRef(env, value);
    }
    return result;
}

static void free_strings(char **values) {
    if (values == NULL) return;
    for (size_t index = 0; values[index] != NULL; index++) free(values[index]);
    free(values);
}

EXPORT JNIEXPORT jlongArray JNICALL
Java_com_phoneagent_runtime_NativePtyBridge_start(
        JNIEnv *env, jobject ignored, jobjectArray arguments, jobjectArray environment,
        jint columns, jint rows) {
    (void) ignored;
    char **argv = copy_strings(env, arguments);
    char **envp = copy_strings(env, environment);
    if (argv == NULL || argv[0] == NULL || envp == NULL) {
        free_strings(argv);
        free_strings(envp);
        return NULL;
    }
    struct winsize size = {
        .ws_row = (unsigned short) rows,
        .ws_col = (unsigned short) columns,
        .ws_xpixel = 0,
        .ws_ypixel = 0,
    };
    int descriptor = -1;
    const pid_t child = forkpty(&descriptor, NULL, NULL, &size);
    if (child == 0) {
        execve(argv[0], argv, envp);
        _exit(127);
    }
    free_strings(argv);
    free_strings(envp);
    if (child < 0) return NULL;

    const jlong result_values[2] = {(jlong) child, (jlong) descriptor};
    jlongArray result = (*env)->NewLongArray(env, 2);
    if (result != NULL) (*env)->SetLongArrayRegion(env, result, 0, 2, result_values);
    return result;
}

EXPORT JNIEXPORT jint JNICALL
Java_com_phoneagent_runtime_NativePtyBridge_read(
        JNIEnv *env, jobject ignored, jint descriptor, jbyteArray destination) {
    (void) ignored;
    const jsize length = (*env)->GetArrayLength(env, destination);
    jbyte *bytes = (*env)->GetByteArrayElements(env, destination, NULL);
    if (bytes == NULL) return -ENOMEM;
    const ssize_t count = read(descriptor, bytes, (size_t) length);
    (*env)->ReleaseByteArrayElements(env, destination, bytes, 0);
    return count < 0 ? -errno : (jint) count;
}

EXPORT JNIEXPORT jint JNICALL
Java_com_phoneagent_runtime_NativePtyBridge_write(
        JNIEnv *env, jobject ignored, jint descriptor, jbyteArray source) {
    (void) ignored;
    const jsize length = (*env)->GetArrayLength(env, source);
    jbyte *bytes = (*env)->GetByteArrayElements(env, source, NULL);
    if (bytes == NULL) return -ENOMEM;
    const ssize_t count = write(descriptor, bytes, (size_t) length);
    (*env)->ReleaseByteArrayElements(env, source, bytes, JNI_ABORT);
    return count < 0 ? -errno : (jint) count;
}

EXPORT JNIEXPORT jint JNICALL
Java_com_phoneagent_runtime_NativePtyBridge_resize(
        JNIEnv *env, jobject ignored, jint descriptor, jint columns, jint rows) {
    (void) env;
    (void) ignored;
    const struct winsize size = {
        .ws_row = (unsigned short) rows,
        .ws_col = (unsigned short) columns,
        .ws_xpixel = 0,
        .ws_ypixel = 0,
    };
    return ioctl(descriptor, TIOCSWINSZ, &size) == 0 ? 0 : -errno;
}

EXPORT JNIEXPORT jint JNICALL
Java_com_phoneagent_runtime_NativePtyBridge_waitFor(
        JNIEnv *env, jobject ignored, jint process_id) {
    (void) env;
    (void) ignored;
    int status = 0;
    if (waitpid(process_id, &status, 0) < 0) return -errno;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

EXPORT JNIEXPORT void JNICALL
Java_com_phoneagent_runtime_NativePtyBridge_terminate(
        JNIEnv *env, jobject ignored, jint process_id) {
    (void) env;
    (void) ignored;
    if (process_id > 0) kill(process_id, SIGTERM);
}

EXPORT JNIEXPORT void JNICALL
Java_com_phoneagent_runtime_NativePtyBridge_close(
        JNIEnv *env, jobject ignored, jint descriptor) {
    (void) env;
    (void) ignored;
    if (descriptor >= 0) close(descriptor);
}
