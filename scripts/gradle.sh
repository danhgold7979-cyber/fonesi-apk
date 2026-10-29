#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
tools_dir="${FONESI_TOOLS_DIR:-/workspace/.fonesi-tools}"
export JAVA_HOME="$tools_dir/jdk-21.0.13+9"
export ANDROID_HOME="$tools_dir/android-sdk"
export ANDROID_USER_HOME="$tools_dir/android-user"
export GRADLE_USER_HOME="$tools_dir/gradle-cache"
export PATH="$JAVA_HOME/bin:$PATH"
if [[ ! -x "$JAVA_HOME/bin/javac" ]]; then
    echo 'Run python3 scripts/bootstrap_android.py --accept-sdk-license first.' >&2
    exit 1
fi
# Java does not automatically honor HTTPS_PROXY. Pass only host/port, never credentials.
proxy_options="$(python3 - <<'PY'
import os, urllib.parse
proxy = urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY', os.environ.get('https_proxy', '')))
if proxy.hostname:
    if proxy.username or proxy.password:
        raise SystemExit('Authenticated proxy requires supported Gradle proxy configuration')
    host = proxy.hostname
    port = proxy.port or 80
    print(f'-Dhttps.proxyHost={host} -Dhttps.proxyPort={port} -Dhttp.proxyHost={host} -Dhttp.proxyPort={port} -Dhttp.nonProxyHosts=localhost|127.*')
PY
)"
export GRADLE_OPTS="${GRADLE_OPTS:-} $proxy_options"
if [[ -f /etc/ssl/certs/java/cacerts ]]; then
    export GRADLE_OPTS="$GRADLE_OPTS -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi
cd "$project_dir"
# bootstrap_android.sh installs and pins this distribution. Prefer it so each CI run does not
# redownload Gradle through the wrapper and fail on a transient services.gradle.org reset.
if [[ -x "$tools_dir/gradle-8.13/bin/gradle" ]]; then
    exec "$tools_dir/gradle-8.13/bin/gradle" "$@"
fi
if [[ -x ./gradlew ]]; then
    exec ./gradlew "$@"
fi
echo "Pinned Gradle 8.13 is missing; run bootstrap_android.py first." >&2
exit 1
