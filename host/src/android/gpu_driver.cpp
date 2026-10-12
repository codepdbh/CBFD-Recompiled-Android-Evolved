// Custom Vulkan drivers on Android (Turnip and the like, in adrenotools' package format), as in
// codepdbh/nfsmw-android's launcher. The app passes the driver the player chose (GpuDrivers.java)
// through the environment; plume opens Vulkan through here (plume_vulkan.cpp).

#include <adrenotools/driver.h>
#include <android/log.h>
#include <dlfcn.h>
#include <cstdio>
#include <cstdlib>
#include <string>

extern "C" __attribute__((visibility("default"))) void *conker_android_open_vulkan() {
    const char *directory = getenv("CONKER_GPU_DRIVER_DIR");
    const char *library = getenv("CONKER_GPU_DRIVER_NAME");
    if ((library == nullptr) || (*library == '\0')) {
        return nullptr; // The system's driver (volk's own loader).
    }

    const char *hooks = getenv("CONKER_NATIVE_LIB_DIR");
    const char *temp = getenv("CONKER_TMP_DIR");
    if ((hooks == nullptr) || (*hooks == '\0') || (directory == nullptr) || (*directory == '\0')) {
        fprintf(stderr, "GPU driver %s: missing paths, using the system's driver.\n", library);
        return nullptr;
    }

    std::string path(directory);
    if (path.back() != '/') {
        path += '/';
    }

    void *handle = adrenotools_open_libvulkan(RTLD_NOW, ADRENOTOOLS_DRIVER_CUSTOM, ((temp != nullptr) && (*temp != '\0')) ? temp : nullptr,
        hooks, path.c_str(), library, nullptr, nullptr);
    fprintf(stderr, "GPU driver %s: %s.\n", library, (handle != nullptr) ? "loaded" : "couldn't be loaded, using the system's driver");
    __android_log_print((handle != nullptr) ? ANDROID_LOG_INFO : ANDROID_LOG_ERROR, "ConkerRecomp", "Custom Vulkan driver %s%s: %s",
        path.c_str(), library, (handle != nullptr) ? "loaded" : "failed");
    return handle;
}
