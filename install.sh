#!/usr/bin/env bash
# ./install.sh            build the release APK, install it on the TV and make it the HOME screen
# ./install.sh --restore  switch back to the stock Google TV launcher (this app stays installed)
#
# The TV is $TV or tv.address in local.properties (host:port of ADB over Wi-Fi, e.g. 192.168.1.20:5555).
# adb is $ADB, else the one on PATH, else $ANDROID_HOME (or sdk.dir)/platform-tools/adb.
set -euo pipefail
cd "$(dirname "$0")"

prop() { sed -n "s/^$1=//p" local.properties 2>/dev/null | tail -1; }

SDK=${ANDROID_HOME:-$(prop sdk.dir)}
ADB=${ADB:-$(command -v adb || echo "$SDK/platform-tools/adb")}
TV=${TV:-$(prop tv.address)}
[[ -n $TV ]] || { echo "Set TV=<host>:5555 or tv.address in local.properties" >&2; exit 1; }
PKG=com.chroniac.tvhome
STOCK=com.google.android.tvlauncher
APK=app/build/outputs/apk/release/app-release.apk

"$ADB" connect "$TV" >/dev/null
adb_tv() { "$ADB" -s "$TV" "$@"; }

if [[ ${1:-} == --restore ]]; then
    adb_tv shell pm enable "$STOCK"
    adb_tv shell cmd package set-home-activity "$STOCK/.MainActivity"
else
    # Offline: every dependency is cached after the first build, and AGP's SDK metadata fetch
    # from dl.google.com can stall for minutes. Run ./gradlew assembleRelease once online first.
    ./gradlew -q --offline assembleRelease
    adb_tv install -r --no-incremental "$APK"
    # Full AOT: Compose code is interpreted/JIT-ed otherwise, which shows up as jank on a slow TV SoC.
    adb_tv shell cmd package compile -m speed -f "$PKG"
    # Usage access for the confidant ranks (no system dialog exists for it on Android TV).
    adb_tv shell appops set "$PKG" GET_USAGE_STATS allow
    adb_tv shell cmd package set-home-activity "$PKG/.HomeActivity"
    # The stock launcher's HOME filter has priority 2 (system app); a third-party app is capped
    # at 0, so set-home-activity alone never wins. Disabling it is the only non-root way.
    adb_tv shell pm disable-user --user 0 "$STOCK"
    # Disabling does not always kill a running instance (e.g. one woken by our own install).
    adb_tv shell am force-stop "$STOCK"
fi

adb_tv shell input keyevent KEYCODE_HOME
adb_tv shell cmd package resolve-activity --brief \
    -a android.intent.action.MAIN -c android.intent.category.HOME --user 0 | tail -1
