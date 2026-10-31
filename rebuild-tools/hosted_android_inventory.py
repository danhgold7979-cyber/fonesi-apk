"""Read-only hosted Android inventory. Never installs, executes SDK tools or reads licenses."""
import hashlib
import json
import os
import platform as host_platform
import re
import stat
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


RUNTIME_ENTRY_LIMIT = 128
RUNTIME_METADATA_LIMIT = 65536
RUNTIME_KEYS = frozenset({"AndroidVersion.ApiLevel", "SystemImage.Abi", "SystemImage.TagId", "Pkg.Revision"})
IMAGE_FILES = ("system.img", "ramdisk.img", "userdata.img", "kernel-ranchu", "kernel-ranchu-64", "kernel-qemu")


def runtime_node(path, kind, executable=False):
    """lstat only: missing is distinct from unreadable, symlink or unexpected type."""
    try:
        info = path.lstat()
    except FileNotFoundError:
        return {"status": "ABSENT", "reason": "not_found"}
    except OSError:
        return {"status": "UNKNOWN", "reason": "stat_failed"}
    expected = stat.S_ISDIR(info.st_mode) if kind == "directory" else stat.S_ISREG(info.st_mode)
    if not expected:
        return {"status": "UNKNOWN", "reason": "unexpected_type_or_symlink"}
    if kind == "file" and info.st_size <= 0:
        return {"status": "UNKNOWN", "reason": "empty_file"}
    if executable and not os.access(path, os.X_OK):
        return {"status": "UNKNOWN", "reason": "not_executable"}
    return {"status": "PRESENT"}


def runtime_properties(path):
    """Known metadata only; no execution, expansion, arbitrary text dump or unbounded read."""
    state = runtime_node(path, "file")
    if state["status"] != "PRESENT":
        return state, {}
    try:
        before = path.lstat()
        if before.st_size > RUNTIME_METADATA_LIMIT:
            return {"status": "UNKNOWN", "reason": "metadata_size_limit"}, {}
        # Read-only, no symlink leaf, no blocking FIFO if the file changes after lstat.
        if not hasattr(os, "O_NOFOLLOW") or not hasattr(os, "O_NONBLOCK"):
            return {"status": "UNKNOWN", "reason": "safe_read_flags_unavailable"}, {}
        fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
        with os.fdopen(fd, "rb") as source:
            current = os.fstat(source.fileno())
            if not stat.S_ISREG(current.st_mode) or (current.st_dev, current.st_ino) != (before.st_dev, before.st_ino):
                return {"status": "UNKNOWN", "reason": "metadata_changed"}, {}
            data = source.read(RUNTIME_METADATA_LIMIT + 1)
        if len(data) > RUNTIME_METADATA_LIMIT:
            return {"status": "UNKNOWN", "reason": "metadata_size_limit"}, {}
        text = data.decode("utf-8", "strict")
        result = {}
        for line in text.splitlines():
            if "=" not in line or line.lstrip().startswith("#"):
                continue
            key, value = line.split("=", 1)
            key, value = key.strip(), value.strip()
            if key in RUNTIME_KEYS:
                if key in result or len(value) > 512:
                    return {"status": "UNKNOWN", "reason": "duplicate_or_overbudget_metadata"}, {}
                result[key] = value
        return {"status": "PRESENT"}, result
    except (OSError, UnicodeError, ValueError):
        return {"status": "UNKNOWN", "reason": "metadata_unreadable"}, {}


def runtime_inventory(sdk, machine, kvm_path=Path("/dev/kvm")):
    """Installed files only. Never starts a runtime or changes compile acceptance."""
    report = {"purpose": "READ_ONLY_RUNTIME_INVENTORY", "host_machine": machine,
              "emulator": {"status": "UNKNOWN", "reason": "sdk_unavailable"},
              "images_status": "UNKNOWN", "images": [], "scan_complete": False,
              "summary": "INCOMPLETE_INVENTORY", "sdk_tools_executed": False,
              "runtime_started": False}
    try:
        info = kvm_path.lstat()
        report["kvm"] = {"status": "PRESENT" if stat.S_ISCHR(info.st_mode) else "UNKNOWN",
                         "character_device": stat.S_ISCHR(info.st_mode),
                         "mode": oct(stat.S_IMODE(info.st_mode)),
                         "read_access": os.access(kvm_path, os.R_OK),
                         "write_access": os.access(kvm_path, os.W_OK)}
    except FileNotFoundError:
        report["kvm"] = {"status": "ABSENT", "reason": "not_found"}
    except OSError:
        report["kvm"] = {"status": "UNKNOWN", "reason": "stat_failed"}
    if sdk is None or runtime_node(sdk, "directory")["status"] != "PRESENT":
        return report
    emulator_dir = runtime_node(sdk / "emulator", "directory")
    if emulator_dir["status"] == "ABSENT":
        report["emulator"] = emulator_dir
    elif emulator_dir["status"] == "PRESENT":
        binary = runtime_node(sdk / "emulator/emulator", "file", executable=True)
        metadata_state, metadata = runtime_properties(sdk / "emulator/source.properties")
        known = metadata_state["status"] == "PRESENT" and bool(metadata.get("Pkg.Revision"))
        report["emulator"] = {"status": binary["status"] if binary["status"] != "PRESENT" else ("PRESENT" if known else "UNKNOWN"),
                              "binary": binary, "metadata_status": metadata_state, "metadata": metadata}
    else:
        report["emulator"] = emulator_dir
    image_root = sdk / "system-images"
    root_state = runtime_node(image_root, "directory")
    report["images_status"] = root_state["status"]
    if root_state["status"] == "ABSENT":
        report["scan_complete"] = True
        report["summary"] = "NO_API26_CANDIDATE_IN_COMPLETE_SCAN"
        return report
    if root_state["status"] != "PRESENT":
        return report
    complete, seen = True, 0
    packages = []
    def walk(directory, depth, parts):
        nonlocal complete, seen
        if seen >= RUNTIME_ENTRY_LIMIT:
            complete = False
            return
        try:
            with os.scandir(directory) as entries:
                bounded = []
                for entry in entries:
                    seen += 1
                    if seen > RUNTIME_ENTRY_LIMIT:
                        complete = False
                        return
                    if not re.fullmatch(r"[A-Za-z0-9_.-]{1,128}", entry.name):
                        complete = False
                        continue
                    bounded.append(entry.name)
            for name in sorted(bounded):
                path = directory / name
                if runtime_node(path, "directory")["status"] != "PRESENT":
                    complete = False
                    continue
                nested = parts + [name]
                if depth == 2:
                    packages.append((path, nested))
                else:
                    walk(path, depth + 1, nested)
        except OSError:
            complete = False
    walk(image_root, 0, [])
    for path, parts in packages:
        metadata_state, metadata = runtime_properties(path / "source.properties")
        files = {name: runtime_node(path / name, "file") for name in IMAGE_FILES}
        valid = (metadata_state["status"] == "PRESENT"
                 and bool(metadata.get("Pkg.Revision"))
                 and metadata.get("AndroidVersion.ApiLevel", "").isdigit()
                 and parts[0] == "android-" + metadata.get("AndroidVersion.ApiLevel", "")
                 and parts[1] == metadata.get("SystemImage.TagId")
                 and parts[2] == metadata.get("SystemImage.Abi"))
        if not valid or any(value["status"] == "UNKNOWN" for value in files.values()):
            complete = False
        required = all(files[name]["status"] == "PRESENT" for name in ("system.img", "ramdisk.img", "userdata.img"))
        kernel = any(files[name]["status"] == "PRESENT" for name in IMAGE_FILES[3:])
        if not required or not kernel:
            complete = False
        compatible = machine in ("x86_64", "AMD64") and metadata.get("SystemImage.Abi") in ("x86", "x86_64")
        candidate = (valid and required and kernel and compatible
                     and not any(value["status"] == "UNKNOWN" for value in files.values())
                     and metadata.get("AndroidVersion.ApiLevel") == "26")
        report["images"].append({"path": "/".join(parts), "metadata_status": metadata_state,
                                 "metadata": metadata, "files": files, "api26_candidate": candidate})
    report["scan_complete"] = complete
    if not complete:
        report["summary"] = "INCOMPLETE_INVENTORY"
    elif any(image["api26_candidate"] for image in report["images"]):
        report["summary"] = "API26_CANDIDATE_FILES_PRESENT"
    else:
        report["summary"] = "NO_API26_CANDIDATE_IN_COMPLETE_SCAN"
    return report


def optional_runtime_inventory(sdk, consistent):
    # Runtime inspection cannot turn a valid compile inventory into an exception on path errors.
    try:
        selected = sdk.resolve(strict=True) if sdk and consistent else None
    except (OSError, RuntimeError):
        selected = None
    return runtime_inventory(selected, host_platform.machine())


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
        "runtime_inventory": optional_runtime_inventory(sdk, sdk_consistent),
    }


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[1]
    print(json.dumps(inspect(root, os.environ), indent=2, sort_keys=True))
