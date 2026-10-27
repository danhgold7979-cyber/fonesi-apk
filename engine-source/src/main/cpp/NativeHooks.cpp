#include "NativeHooks.h"
#include <dobby.h>
#include <algorithm>
#include <atomic>
#include <cerrno>
#include <cstdarg>
#include <cstdio>
#include <dirent.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <mutex>
#include <stdexcept>
#include <sys/stat.h>
#include <unistd.h>
#include <vector>

namespace fonesi {
namespace {
std::atomic<RuntimeState> state{RuntimeState::EMPTY};
std::shared_ptr<const PathRules> rules;
std::mutex activation;
std::string failure;

// Each call owns its replacement string, including both arguments of rename/renameat.
class MappedPath {
    const char* original_;
    std::optional<std::string> mapped_;
    bool valid_ = true;
public:
    explicit MappedPath(const char* path) : original_(path) {
        if (path == nullptr || state.load(std::memory_order_acquire) != RuntimeState::ENABLED) return;
        try { mapped_ = std::atomic_load(&rules)->redirect(path); }
        catch (...) { valid_ = false; errno = EINVAL; }
    }
    explicit operator bool() const { return valid_; }
    const char* get() const { return mapped_ ? mapped_->c_str() : original_; }
};

bool requiresMode(int flags) {
    return (flags & O_CREAT) != 0
#ifdef O_TMPFILE
        || (flags & O_TMPFILE) == O_TMPFILE
#endif
        ;
}

#define OPEN_HOOK(name) \
static int (*original_##name)(const char*, int, ...); \
static int routed_##name(const char* path, int flags, ...) { \
    mode_t mode = 0; const bool withMode = requiresMode(flags); \
    if (withMode) { va_list args; va_start(args, flags); mode = static_cast<mode_t>(va_arg(args, int)); va_end(args); } \
    MappedPath mapped(path); if (!mapped) return -1; \
    return withMode ? original_##name(mapped.get(), flags, mode) : original_##name(mapped.get(), flags); \
}
OPEN_HOOK(open)
OPEN_HOOK(open64)

#define OPENAT_HOOK(name) \
static int (*original_##name)(int, const char*, int, ...); \
static int routed_##name(int fd, const char* path, int flags, ...) { \
    mode_t mode = 0; const bool withMode = requiresMode(flags); \
    if (withMode) { va_list args; va_start(args, flags); mode = static_cast<mode_t>(va_arg(args, int)); va_end(args); } \
    MappedPath mapped(path); if (!mapped) return -1; \
    return withMode ? original_##name(fd, mapped.get(), flags, mode) : original_##name(fd, mapped.get(), flags); \
}
OPENAT_HOOK(openat)
OPENAT_HOOK(openat64)

#define ONE_PATH_INT(name, parameters, arguments) \
static int (*original_##name) parameters; \
static int routed_##name parameters { MappedPath mapped(path); if (!mapped) return -1; return original_##name arguments; }
ONE_PATH_INT(access, (const char* path, int mode), (mapped.get(), mode))
ONE_PATH_INT(stat, (const char* path, struct stat* info), (mapped.get(), info))
ONE_PATH_INT(lstat, (const char* path, struct stat* info), (mapped.get(), info))
ONE_PATH_INT(fstatat, (int fd, const char* path, struct stat* info, int flags), (fd, mapped.get(), info, flags))
ONE_PATH_INT(mkdir, (const char* path, mode_t mode), (mapped.get(), mode))
ONE_PATH_INT(unlink, (const char* path), (mapped.get()))
ONE_PATH_INT(unlinkat, (int fd, const char* path, int flags), (fd, mapped.get(), flags))
ONE_PATH_INT(rmdir, (const char* path), (mapped.get()))
ONE_PATH_INT(chmod, (const char* path, mode_t mode), (mapped.get(), mode))
ONE_PATH_INT(creat, (const char* path, mode_t mode), (mapped.get(), mode))
ONE_PATH_INT(creat64, (const char* path, mode_t mode), (mapped.get(), mode))
ONE_PATH_INT(mkdirat, (int fd, const char* path, mode_t mode), (fd, mapped.get(), mode))
ONE_PATH_INT(faccessat, (int fd, const char* path, int mode, int flags), (fd, mapped.get(), mode, flags))
ONE_PATH_INT(remove, (const char* path), (mapped.get()))
ONE_PATH_INT(chdir, (const char* path), (mapped.get()))
ONE_PATH_INT(truncate, (const char* path, off_t length), (mapped.get(), length))
ONE_PATH_INT(__open_2, (const char* path, int flags), (mapped.get(), flags))
ONE_PATH_INT(__openat_2, (int fd, const char* path, int flags), (fd, mapped.get(), flags))

#define FOPEN_HOOK(name) \
static FILE* (*original_##name)(const char*, const char*); \
static FILE* routed_##name(const char* path, const char* mode) { \
    MappedPath mapped(path); if (!mapped) return nullptr; return original_##name(mapped.get(), mode); \
}
FOPEN_HOOK(fopen)
FOPEN_HOOK(fopen64)
static DIR* (*original_opendir)(const char*);
static DIR* routed_opendir(const char* path) {
    MappedPath mapped(path); if (!mapped) return nullptr;
    return original_opendir(mapped.get());
}
static int (*original_rename)(const char*, const char*);
static int routed_rename(const char* from, const char* to) {
    MappedPath first(from), second(to); if (!first || !second) return -1;
    return original_rename(first.get(), second.get());
}
static int (*original_renameat)(int, const char*, int, const char*);
static int routed_renameat(int firstFd, const char* from, int secondFd, const char* to) {
    MappedPath first(from), second(to); if (!first || !second) return -1;
    return original_renameat(firstFd, first.get(), secondFd, second.get());
}
static ssize_t (*original_readlink)(const char*, char*, size_t);
static ssize_t routed_readlink(const char* path, char* buffer, size_t size) {
    MappedPath mapped(path); if (!mapped) return -1;
    return original_readlink(mapped.get(), buffer, size);
}
static ssize_t (*original_readlinkat)(int, const char*, char*, size_t);
static ssize_t routed_readlinkat(int fd, const char* path, char* buffer, size_t size) {
    MappedPath mapped(path); if (!mapped) return -1;
    return original_readlinkat(fd, mapped.get(), buffer, size);
}

struct Hook { const char* name; void* replacement; void** original; void* address = nullptr; bool optional = false; };
#define HOOK(name) Hook{#name, reinterpret_cast<void*>(routed_##name), reinterpret_cast<void**>(&original_##name)}
}

void configureRules(std::shared_ptr<const PathRules> value) {
    std::lock_guard<std::mutex> guard(activation);
    if (!value || state.load() != RuntimeState::EMPTY) throw std::logic_error("Routing cannot change within a guest process");
    std::atomic_store(&rules, std::move(value));
    state.store(RuntimeState::CONFIGURED, std::memory_order_release);
}

bool enableHooks() {
    std::lock_guard<std::mutex> guard(activation);
    if (state.load() != RuntimeState::CONFIGURED) return state.load() == RuntimeState::ENABLED;
    std::vector<Hook> hooks{HOOK(open), HOOK(openat), HOOK(__open_2), HOOK(__openat_2), HOOK(fopen), HOOK(opendir),
        HOOK(access), HOOK(stat), HOOK(lstat), HOOK(fstatat), HOOK(mkdir), HOOK(unlink), HOOK(unlinkat),
        HOOK(rmdir), HOOK(chmod), HOOK(creat), HOOK(mkdirat), HOOK(faccessat), HOOK(remove), HOOK(chdir), HOOK(truncate),
        HOOK(rename), HOOK(renameat), HOOK(readlink), HOOK(readlinkat)};
    // Android ARM64 often aliases open64 to open. Never patch the same entry point twice.
    auto largeOpen = HOOK(open64); largeOpen.optional = true; hooks.push_back(largeOpen);
    auto largeOpenat = HOOK(openat64); largeOpenat.optional = true; hooks.push_back(largeOpenat);
    auto largeFopen = HOOK(fopen64); largeFopen.optional = true; hooks.push_back(largeFopen);
    auto largeCreat = HOOK(creat64); largeCreat.optional = true; hooks.push_back(largeCreat);
    std::vector<void*> addresses;
    for (auto& hook : hooks) {
        hook.address = dlsym(RTLD_DEFAULT, hook.name);
        if (!hook.address) {
            if (hook.optional) continue;
            failure = std::string("Required libc symbol unavailable: ") + hook.name;
            state.store(RuntimeState::FAILED); return false;
        }
        if (std::find(addresses.begin(), addresses.end(), hook.address) != addresses.end()) { hook.address = nullptr; continue; }
        addresses.push_back(hook.address);
    }
    std::vector<void*> installed;
    for (const auto& hook : hooks) if (hook.address) {
        if (DobbyHook(hook.address, hook.replacement, hook.original) != 0) {
            bool restored = true;
            for (auto it = installed.rbegin(); it != installed.rend(); ++it) restored = DobbyDestroy(*it) == 0 && restored;
            // Even a successful rollback does not authorize retry or claim guest readiness.
            failure = std::string("Native hook failed: ") + hook.name + (restored ? "; rollback completed" : "; rollback incomplete");
            state.store(RuntimeState::FAILED, std::memory_order_release); return false;
        }
        installed.push_back(hook.address);
    }
    state.store(RuntimeState::ENABLED, std::memory_order_release);
    return true;
}

RuntimeState runtimeState() { return state.load(std::memory_order_acquire); }
std::string runtimeFailure() {
    std::lock_guard<std::mutex> guard(activation);
    return failure.empty() ? "Native routing is not configured" : failure;
}
}
