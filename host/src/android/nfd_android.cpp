// nativefiledialog-extended's API for Android, which has no native file dialog: RT64 and
// RecompFrontend call it for Load ROM, installing mods and the like.
// TODO: open the system's file picker (Storage Access Framework) through the activity.
// Until then every dialog is cancelled.

#include <cstdlib>

#include "nfd.h"

namespace {
    const char* last_error = nullptr;

    nfdresult_t unavailable() {
        last_error = "File dialogs aren't available on Android yet.";
        return NFD_CANCEL;
    }
}

extern "C" {

nfdresult_t NFD_Init(void) {
    return NFD_OKAY;
}

void NFD_Quit(void) {}

void NFD_FreePathN(nfdnchar_t* filePath) {
    std::free(filePath);
}

nfdresult_t NFD_OpenDialogN(nfdnchar_t** outPath, const nfdnfilteritem_t*, nfdfiltersize_t, const nfdnchar_t*) {
    *outPath = nullptr;
    return unavailable();
}

nfdresult_t NFD_OpenDialogMultipleN(const nfdpathset_t** outPaths, const nfdnfilteritem_t*, nfdfiltersize_t, const nfdnchar_t*) {
    *outPaths = nullptr;
    return unavailable();
}

nfdresult_t NFD_SaveDialogN(nfdnchar_t** outPath, const nfdnfilteritem_t*, nfdfiltersize_t, const nfdnchar_t*, const nfdnchar_t*) {
    *outPath = nullptr;
    return unavailable();
}

nfdresult_t NFD_PickFolderN(nfdnchar_t** outPath, const nfdnchar_t*) {
    *outPath = nullptr;
    return unavailable();
}

const char* NFD_GetError(void) {
    return last_error;
}

void NFD_ClearError(void) {
    last_error = nullptr;
}

nfdresult_t NFD_PathSet_GetCount(const nfdpathset_t*, nfdpathsetsize_t* count) {
    *count = 0;
    return NFD_OKAY;
}

nfdresult_t NFD_PathSet_GetPathN(const nfdpathset_t*, nfdpathsetsize_t, nfdnchar_t** outPath) {
    *outPath = nullptr;
    return NFD_ERROR;
}

void NFD_PathSet_FreePathN(const nfdnchar_t* filePath) {
    std::free((void*)filePath);
}

void NFD_PathSet_Free(const nfdpathset_t*) {}

}
