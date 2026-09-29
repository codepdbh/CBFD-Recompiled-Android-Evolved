// Android glue. stdout and stderr go to logcat, under the ConkerRecomp tag, and to the log
// file the app names in CONKER_LOG_FILE (ConkerRecompiled/logs/game.log), so players can send
// it; a crash adds its signal and backtrace there (main.cpp calls
// conker_android_redirect_output). The window build is started by SDL's SDLActivity; the
// headless build is a library the test app starts by calling run() with the command line
// main.cpp takes.

#include <csignal>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <thread>
#include <vector>

#include <android/log.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <jni.h>
#include <pthread.h>
#include <unistd.h>
#include <unwind.h>

namespace {
    constexpr const char* log_tag = "ConkerRecomp";
    int log_fd = -1;

    void write_all(int fd, const char* text, size_t length) {
        while (length > 0) {
            ssize_t written = write(fd, text, length);
            if (written <= 0) {
                return;
            }
            text += written;
            length -= (size_t)written;
        }
    }

    void write_text(const char* text) {
        if (log_fd >= 0) {
            write_all(log_fd, text, strlen(text));
        }
    }

    // The crash report: which signal, where, and the backtrace, into the log file. Then the
    // system's own handler runs as usual (a tombstone, the app closing).
    struct sigaction previous_actions[NSIG];
    constexpr int crash_signals[] = { SIGSEGV, SIGBUS, SIGABRT, SIGILL, SIGFPE };

    struct Backtrace {
        uintptr_t frames[64];
        int count = 0;
    };

    _Unwind_Reason_Code unwind_frame(_Unwind_Context* context, void* arg) {
        auto* trace = static_cast<Backtrace*>(arg);
        uintptr_t pc = _Unwind_GetIP(context);
        if (pc != 0 && trace->count < 64) {
            trace->frames[trace->count++] = pc;
        }
        return trace->count < 64 ? _URC_NO_REASON : _URC_END_OF_STACK;
    }

    void crash_handler(int sig, siginfo_t* info, void* context) {
        // Let the output thread write out what the game printed last (an abort's message).
        usleep(150000);
        char line[512];
        char thread[32] = {};
        pthread_getname_np(pthread_self(), thread, sizeof(thread));
        snprintf(line, sizeof(line), "\n*** Crash: signal %d (%s) at address %p, thread \"%s\"\n",
            sig, strsignal(sig), info != nullptr ? info->si_addr : nullptr, thread);
        write_text(line);
        Backtrace trace;
        _Unwind_Backtrace(unwind_frame, &trace);
        for (int i = 0; i < trace.count; i++) {
            Dl_info symbol{};
            if (dladdr((void*)trace.frames[i], &symbol) != 0 && symbol.dli_fname != nullptr) {
                const char* library = strrchr(symbol.dli_fname, '/');
                snprintf(line, sizeof(line), "  #%02d %s+0x%zx %s\n", i,
                    library != nullptr ? library + 1 : symbol.dli_fname,
                    (size_t)(trace.frames[i] - (uintptr_t)symbol.dli_fbase),
                    symbol.dli_sname != nullptr ? symbol.dli_sname : "");
            }
            else {
                snprintf(line, sizeof(line), "  #%02d 0x%zx\n", i, (size_t)trace.frames[i]);
            }
            write_text(line);
        }
        if (log_fd >= 0) {
            fsync(log_fd);
        }
        sigaction(sig, &previous_actions[sig], nullptr);
        raise(sig);
    }

    void install_crash_handler() {
        struct sigaction action{};
        action.sa_sigaction = crash_handler;
        action.sa_flags = SA_SIGINFO | SA_RESETHAND;
        sigemptyset(&action.sa_mask);
        for (int sig : crash_signals) {
            sigaction(sig, &action, &previous_actions[sig]);
        }
    }
}

#if defined(__arm__)
// 32-bit ARM's allocator only aligns to 8 bytes, but hlslpp's vectors and matrices (RT64's math)
// are 16-byte aligned, and the compiler loads them with instructions that fault or misread when
// they aren't (distorted geometry, bus errors). Every C++ allocation 16-byte aligned, as on arm64.
#include <malloc.h>
#include <new>

namespace {
    void* aligned_new(size_t size) {
        void* memory = memalign(16, size != 0 ? size : 1);
        if (memory == nullptr) {
            throw std::bad_alloc();
        }
        return memory;
    }
}

void* operator new(size_t size) { return aligned_new(size); }
void* operator new[](size_t size) { return aligned_new(size); }
void* operator new(size_t size, const std::nothrow_t&) noexcept { return memalign(16, size != 0 ? size : 1); }
void* operator new[](size_t size, const std::nothrow_t&) noexcept { return memalign(16, size != 0 ? size : 1); }
void operator delete(void* memory) noexcept { free(memory); }
void operator delete[](void* memory) noexcept { free(memory); }
void operator delete(void* memory, size_t) noexcept { free(memory); }
void operator delete[](void* memory, size_t) noexcept { free(memory); }
void operator delete(void* memory, const std::nothrow_t&) noexcept { free(memory); }
void operator delete[](void* memory, const std::nothrow_t&) noexcept { free(memory); }
#endif

// Sends everything written to stdout and stderr to logcat and the log file, a line at a time.
void conker_android_redirect_output() {
    static bool redirected = false;
    if (redirected) {
        return;
    }
    redirected = true;
    if (const char* path = getenv("CONKER_LOG_FILE")) {
        log_fd = open(path, O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0660);
    }
    install_crash_handler();
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
                    line += '\n';
                    write_text(line.c_str());
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
