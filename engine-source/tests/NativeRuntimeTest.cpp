#include "NativeHooks.h"
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <stdexcept>

namespace fs = std::filesystem;
void check(bool condition, const char* scenario) { if (!condition) throw std::runtime_error(scenario); }
std::string readFile(const std::string& path) {
    int fd = open(path.c_str(), O_RDONLY);
    check(fd >= 0, "open read");
    char bytes[64]{}; auto count = read(fd, bytes, sizeof(bytes)); close(fd);
    check(count >= 0, "read"); return std::string(bytes, static_cast<std::size_t>(count));
}
void writeFile(const fs::path& path, const std::string& value) { std::ofstream output(path); output << value; check(output.good(), "write fixture"); }

void policyChecks() {
    using fonesi::PathRules;
    PathRules policy("/owned", {{"/data/app/account", "/owned/A"}, {"/data/app/account/cache", "/owned/cache"}});
    check(policy.redirect("/data/app/account/file") == "/owned/A/file", "directory boundary mapping");
    check(policy.redirect("/data/app/account/cache/file") == "/owned/cache/file", "longest prefix wins");
    check(!policy.redirect("/data/app/account_other/file"), "neighbour is not mapped");
    check(!policy.redirect("/prefix/data/app/account/file"), "substring is not mapped");
    check(!policy.redirect("relative/file"), "relative path preserves dirfd semantics");
    check(policy.redirect("/data/app/account/cache/../file") == "/owned/A/file", "dot segments normalized before mapping");
    check(!policy.redirect("/data/app/account/../peer/file"), "traversal does not inherit previous prefix");
    for (const auto& mappings : std::vector<std::vector<std::pair<std::string, std::string>>>{
        {{"/", "/owned/A"}}, {{"/data/app/account", "/owned_other/A"}},
        {{"/data/app/account", "/owned/A"}, {"/data/app/account/", "/owned/B"}}}) {
        bool rejected = false;
        try { PathRules invalid("/owned", mappings); } catch (const std::invalid_argument&) { rejected = true; }
        check(rejected, "invalid/ambiguous policy must be rejected");
    }
}

void childChecks(const fs::path& root, const std::string& name) {
    auto original = (root / "visible").string(); auto owned = root / name;
    fonesi::configureRules(std::make_shared<const fonesi::PathRules>(root.string(),
        std::vector<std::pair<std::string, std::string>>{{original, owned.string()}}));
    check(fonesi::runtimeState() == fonesi::RuntimeState::CONFIGURED, "no eager hooks during configure");
    check(readFile(original + "/marker") == "ORIGINAL", "original readable before enabling");
    check(fonesi::enableHooks(), "libc hooks installed");
    check(fonesi::runtimeState() == fonesi::RuntimeState::ENABLED, "enabled state follows successful installation");
    check(readFile(original + "/marker") == name, "native open reads selected guest data");
    check(readFile((root / "visible_neighbour/marker").string()) == "NEIGHBOUR", "real neighbour file untouched");
    FILE* stream = fopen((original + "/marker").c_str(), "r"); check(stream != nullptr, "fopen routing");
    char content[32]{}; check(fread(content, 1, name.size(), stream) == name.size(), "fread routing"); fclose(stream);
    check(std::string(content) == name, "fopen sees selected guest");
    std::ifstream input(original + "/marker"); std::string text; input >> text;
    check(text == name, "C++ file stream/large-file API routing");
    const auto created = original + "/created";
    int fd = open(created.c_str(), O_CREAT | O_EXCL | O_WRONLY, 0600); check(fd >= 0, "open create mode");
    check(write(fd, name.data(), name.size()) == static_cast<ssize_t>(name.size()), "write created"); close(fd);
    struct stat info{}; check(stat(created.c_str(), &info) == 0 && (info.st_mode & 0777) == 0600, "stat/private create permissions");
    check(rename(created.c_str(), (original + "/renamed").c_str()) == 0, "rename maps both paths with independent lifetime");
    check(readFile(original + "/renamed") == name, "renamed data preserved");
    check(access((original + "/renamed").c_str(), F_OK) == 0, "access routing");
    check(mkdir((original + "/folder").c_str(), 0700) == 0, "mkdir routing");
    int directory = open((original + "/folder").c_str(), O_RDONLY | O_DIRECTORY); check(directory >= 0, "open directory");
    fd = openat(directory, "relative", O_CREAT | O_WRONLY, 0600); check(fd >= 0, "relative openat retains real directory"); close(fd);
    check(unlinkat(directory, "relative", 0) == 0, "relative unlinkat routing"); close(directory);
    check(rmdir((original + "/folder").c_str()) == 0, "rmdir routing");
    bool rejected = false;
    try { fonesi::configureRules(std::make_shared<const fonesi::PathRules>(root.string(),
        std::vector<std::pair<std::string,std::string>>{{original, (root / "other").string()}})); }
    catch (const std::logic_error&) { rejected = true; }
    check(rejected, "account cannot switch inside active native process");
}

int main() {
    try {
        policyChecks();
        char directory[] = "/tmp/fonesi-native-test.XXXXXX";
        auto allocated = mkdtemp(directory); check(allocated != nullptr, "temporary directory");
        fs::path root(allocated);
        for (const auto& name : {"visible", "visible_neighbour", "A", "B"}) fs::create_directory(root / name);
        writeFile(root / "visible/marker", "ORIGINAL"); writeFile(root / "visible_neighbour/marker", "NEIGHBOUR");
        writeFile(root / "A/marker", "A"); writeFile(root / "B/marker", "B");
        for (const auto& name : {"A", "B"}) {
            auto child = fork(); check(child >= 0, "fork isolated test process");
            if (child == 0) {
                try { childChecks(root, name); _exit(0); }
                catch (const std::exception& error) { std::cerr << "Child native test failed: " << error.what() << '\n'; _exit(1); }
            }
            int status = 0; check(waitpid(child, &status, 0) == child && WIFEXITED(status) && WEXITSTATUS(status) == 0,
                "native child exited successfully without suppressed crash");
        }
        check(readFile((root / "visible/marker").string()) == "ORIGINAL", "original account unaffected by both native children");
        check(!fs::exists(root / "visible/renamed"), "guest write never reaches original");
        check(readFile((root / "A/renamed").string()) == "A" && readFile((root / "B/renamed").string()) == "B", "two guest outputs remain independent");
        fs::remove_all(root); // Only this test's fresh mkdtemp directory.
        std::cout << "PASS: host libc routing, two guest processes and original data preservation. Android runtime not executed.\n";
        return 0;
    } catch (const std::exception& error) { std::cerr << "FAIL: " << error.what() << '\n'; return 1; }
}
