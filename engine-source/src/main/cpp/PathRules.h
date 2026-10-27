#pragma once

#include <optional>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

namespace fonesi {
/** Immutable routing policy inside one guest process; not an OS/UID sandbox. */
class PathRules {
public:
    PathRules(std::string ownedRoot, std::vector<std::pair<std::string, std::string>> mappings);
    std::optional<std::string> redirect(std::string_view path) const;
    static std::string normalize(std::string_view path);
private:
    std::vector<std::pair<std::string, std::string>> mappings_;
};
}
