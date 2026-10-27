#include "PathRules.h"
#include <algorithm>
#include <set>
#include <stdexcept>

namespace fonesi {
namespace {
bool inside(std::string_view path, std::string_view prefix) {
    return path == prefix || (path.size() > prefix.size() && path.substr(0, prefix.size()) == prefix && path[prefix.size()] == '/');
}
}

std::string PathRules::normalize(std::string_view path) {
    if (path.empty() || path.front() != '/' || path.find('\0') != std::string_view::npos)
        throw std::invalid_argument("An absolute path without NUL is required");
    std::vector<std::string_view> segments;
    std::size_t start = 1;
    while (start <= path.size()) {
        const auto end = path.find('/', start);
        const auto segment = path.substr(start, end == std::string_view::npos ? path.size() - start : end - start);
        if (segment == "..") { if (!segments.empty()) segments.pop_back(); }
        else if (!segment.empty() && segment != ".") segments.push_back(segment);
        if (end == std::string_view::npos) break;
        start = end + 1;
    }
    std::string normalized;
    for (const auto segment : segments) { normalized.push_back('/'); normalized.append(segment); }
    return normalized.empty() ? "/" : normalized;
}

PathRules::PathRules(std::string ownedRoot, std::vector<std::pair<std::string, std::string>> mappings) {
    ownedRoot = normalize(ownedRoot);
    if (ownedRoot == "/" || mappings.empty()) throw std::invalid_argument("An owned root and mappings are required");
    std::set<std::string> seen;
    for (auto& entry : mappings) {
        auto from = normalize(entry.first); auto to = normalize(entry.second);
        if (from == "/" || !inside(to, ownedRoot) || !seen.insert(from).second)
            throw std::invalid_argument("Invalid or ambiguous routing rule");
        mappings_.emplace_back(std::move(from), std::move(to));
    }
    std::sort(mappings_.begin(), mappings_.end(), [](const auto& a, const auto& b) { return a.first.size() > b.first.size(); });
}

std::optional<std::string> PathRules::redirect(std::string_view path) const {
    if (path.empty() || path.front() != '/') return std::nullopt; // Relative paths retain dirfd/CWD semantics.
    const auto normalized = normalize(path);
    for (const auto& [from, to] : mappings_) if (inside(normalized, from)) return to + normalized.substr(from.size());
    return std::nullopt;
}
}
