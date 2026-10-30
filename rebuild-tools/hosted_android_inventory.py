"""Read-only hosted Android inventory. Never installs, executes SDK tools or reads licenses."""
import hashlib
import json
import os
from pathlib import Path

WRAPPER_SHA = "81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f"
SCRIPT_SHA = "734b3879d3501dce471cf0522d3bcbafe76873d9fc5129345b67fb43bd15e933"
ZIP_SHA = "20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78"


def properties(path):
    result = {}
    if path.is_file():
        for line in path.read_text(encoding="utf-8").splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                key, value = line.split("=", 1)
                result[key.strip()] = value.strip().strip('"')
    return result


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None


def inspect(repo, environment):
    sdk_raw = environment.get("ANDROID_HOME") or environment.get("ANDROID_SDK_ROOT")
    sdk = Path(sdk_raw) if sdk_raw else None
    jdk_raw = environment.get("JAVA_HOME_17_X64")
    jdk = Path(jdk_raw) if jdk_raw else None
    platform = sdk / "platforms/android-36" if sdk else None
    build_tools = sdk / "build-tools/36.0.0" if sdk else None
    wrapper = properties(repo / "gradle/wrapper/gradle-wrapper.properties")
    wrapper_verified = (sha(repo / "gradle/wrapper/gradle-wrapper.jar") == WRAPPER_SHA and
                        sha(repo / "gradlew") == SCRIPT_SHA and
                        wrapper.get("distributionSha256Sum") == ZIP_SHA and
                        wrapper.get("distributionUrl") == r"https\://services.gradle.org/distributions/gradle-8.13-bin.zip")
    platform_props = properties(platform / "source.properties") if platform else {}
    tool_props = properties(build_tools / "source.properties") if build_tools else {}
    java_props = properties(jdk / "release") if jdk else {}
    tools = {name: bool(build_tools and (build_tools / name).is_file() and os.access(build_tools / name, os.X_OK)) for name in ("aapt2", "apksigner", "zipalign")}
    sdk_consistent = not (environment.get("ANDROID_HOME") and environment.get("ANDROID_SDK_ROOT") and
                          Path(environment["ANDROID_HOME"]).resolve() != Path(environment["ANDROID_SDK_ROOT"]).resolve())
    checks = {
        "wrapper_verified": wrapper_verified,
        "sdk_roots_consistent": sdk_consistent,
        "platform_api36_installed": platform_props.get("AndroidVersion.ApiLevel") == "36" and bool(platform and (platform / "android.jar").is_file()),
        "build_tools_36_installed": tool_props.get("Pkg.Revision") == "36.0.0" and all(tools.values()),
        "jdk17_installed": java_props.get("JAVA_VERSION", "").startswith("17.") and bool(jdk and (jdk / "bin/java").is_file() and (jdk / "bin/javac").is_file() and os.access(jdk / "bin/java", os.X_OK) and os.access(jdk / "bin/javac", os.X_OK)),
    }
    return {
        "purpose": "READ_ONLY_INVENTORY_NOT_COMPILE",
        "image_os": environment.get("ImageOS"), "image_version": environment.get("ImageVersion"),
        "sdk_root": sdk_raw, "jdk17_root": jdk_raw,
        "platform": {key: platform_props.get(key) for key in ("Pkg.Desc", "Pkg.Revision", "AndroidVersion.ApiLevel")},
        "build_tools": {"revision": tool_props.get("Pkg.Revision"), "files": tools},
        "jdk17": {key: java_props.get(key) for key in ("JAVA_VERSION", "IMPLEMENTOR")},
        "wrapper_jar_sha256": sha(repo / "gradle/wrapper/gradle-wrapper.jar"),
        "wrapper_script_sha256": sha(repo / "gradlew"),
        "candidate": {"agp": "8.11.1", "gradle": "8.13", "compile_sdk": 36, "build_tools": "36.0.0", "java": 17},
        "checks": checks, "status": "READY_FOR_BOUNDED_COMPILE_REVIEW" if all(checks.values()) else "NOT_READY",
        "sdk_install_attempted": False, "sdk_license_action": False, "gradle_executed": False,
    }


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[1]
    print(json.dumps(inspect(root, os.environ), indent=2, sort_keys=True))
