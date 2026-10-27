#pragma once
#include "PathRules.h"
#include <memory>

namespace fonesi {
enum class RuntimeState : int { EMPTY = 0, CONFIGURED = 1, ENABLED = 2, FAILED = 3 };
void configureRules(std::shared_ptr<const PathRules> rules);
bool enableHooks();
RuntimeState runtimeState();
std::string runtimeFailure();
}
