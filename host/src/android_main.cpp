// Android glue. stdout and stderr go to logcat, under the ConkerRecomp tag (main.cpp calls
// conker_android_redirect_output). The window build is started by SDL's SDLActivity; the
// headless build is a library the test app starts by calling run() with the command line
// main.cpp takes.

#include <cstdio>
#include <string>
#include <thread>
#include <vector>

#include <android/log.h>
#include <jni.h>
#include <unistd.h>

namespace {
    constexpr const char* log_tag = "ConkerRecomp";
}

// Sends everything written to stdout and stderr to logcat, a line at a time.
void conker_android_redirect_output() {
    static bool redirected = false;
    if (redirected) {
        return;
    }
    redirected = true;
    int fds[2];
    if (pipe(fds) != 0) {
        return;
    }
    std::setvbuf(stdout, nullptr, _IONBF, 0);
    std::setvbuf(stderr, nullptr, _IONBF, 0);
    dup2(fds[1], STDOUT_FILENO);
    dup2(fds[1], STDERR_FILENO);
    std::thread([read_fd = fds[0]] {
        std::string line;
        char buffer[512];
        ssize_t count;
        while ((count = read(read_fd, buffer, sizeof(buffer))) > 0) {
            for (ssize_t i = 0; i < count; i++) {
                if (buffer[i] == '\n') {
                    __android_log_write(ANDROID_LOG_INFO, log_tag, line.c_str());
                    line.clear();
                }
                else {
                    line += buffer[i];
                }
            }
        }
    }).detach();
}

#if !defined(CONKER_RT64)
int conker_main(int argc, char** argv);

extern "C" JNIEXPORT jint JNICALL
Java_com_codepdbh_cbfdrecomp_NativeBridge_run(JNIEnv* env, jclass, jobjectArray args) {
    std::vector<std::string> strings{ "ConkerRecomp" };
    jsize count = env->GetArrayLength(args);
    for (jsize i = 0; i < count; i++) {
        auto arg = (jstring)env->GetObjectArrayElement(args, i);
        const char* chars = env->GetStringUTFChars(arg, nullptr);
        strings.emplace_back(chars);
        env->ReleaseStringUTFChars(arg, chars);
        env->DeleteLocalRef(arg);
    }
    std::vector<char*> argv;
    for (std::string& s : strings) {
        argv.push_back(s.data());
    }
    argv.push_back(nullptr);

    int result = conker_main((int)strings.size(), argv.data());
    std::printf("[android] conker_main returned %d\n", result);
    return result;
}
#endif
