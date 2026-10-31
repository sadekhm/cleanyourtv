#!/usr/bin/env bash
# Build the launcher and its add-ons, install them on one TV with plain adb, and take screenshots.
# Nothing here is tied to one TV, one add-on or one wallpaper: everything that varies is a flag or an
# environment variable, so the same script works on a fresh TV and on this one.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
launcher_pkg=com.gathin.tvhome
launcher_apk="$repo_root/launcher/app/build/outputs/apk/release/app-release.apk"
theme_uri=content://com.gathin.tvhome.theme
# The gate .github/workflows/ci.yml applies to launcher/app/build/outputs/apk/release/app-release.apk.
apk_size_gate=589824

device=""
addons=""
launcher_mode=install
wallpaper=""
theme_config=""
captures="$repo_root/shots"
dry_run=0
# Some add-ons embed an API key at build time; the key file lives outside the repo so it can never be
# committed. The add-on's build.gradle reads it as ../../../.env.local from addons/<name>.
api_key_file="${API_KEY_FILE:-$repo_root/../.env.local}"
failures=0
# app/activity pairs, filled by build_addon and used for the screenshots at the end
installed_activities=()

usage() {
    cat <<'EOF'
Usage: ./install-tv.sh [options]

Build the launcher and any add-ons, install them on one TV with plain adb, optionally push a
wallpaper or theme config, and save screenshots of what the TV then shows.

Options:
  -d, --device <serial|host:port>  TV to install on. Order: --device, $ADB_SERIAL, $TV_HOST (with
                                   $TV_PORT, default 5555), then the only device adb reports.
  -a, --addons <a,b,c>             add-on folders under addons/ to build and install (default: none).
                                   Each one is its own Gradle project with its own gradlew.
  -l, --launcher <install|home|skip>
                                   install (default): build and install the launcher APK, leaving
                                   Home exactly as it is.
                                   home: the same, then hand Home over by running
                                   launcher/build-and-install.sh. That script needs TV_HOST and
                                   tools/venv: python3 -m venv tools/venv &&
                                   tools/venv/bin/pip install -r tools/requirements.txt
                                   skip: do not build or install the launcher at all.
  -w, --wallpaper <file>           image to push as the active wallpaper through the launcher's
                                   theme provider.
  -t, --theme-config <file>        JSON theme config to push alongside the wallpaper (default: a
                                   config that only switches the wallpaper on).
  -c, --captures <dir>             where the screenshots and the logcat go (default: shots/, which
                                   is already gitignored).
  -n, --dry-run                    print every adb and Gradle command, change nothing, exit 0.
  -h, --help                       this text.

Environment:
  ADB_SERIAL    device serial or host:port, used when --device is not given.
  TV_HOST       TV IP address; TV_PORT (default 5555) is appended when it has no port.
  API_KEY_FILE  file holding API_KEY=<key> for an add-on that embeds one
                (default: one level above the repo root, ../.env.local).
  SKIP_BUILD=1  install the launcher APK that is already built instead of building it.
EOF
}

while (( $# > 0 )); do
    case "$1" in
        -d|--device) device="${2:?--device needs a serial or host:port}"; shift 2 ;;
        -a|--addons) addons="${2:?--addons needs a comma-separated list}"; shift 2 ;;
        -l|--launcher) launcher_mode="${2:?--launcher needs install, home or skip}"; shift 2 ;;
        -w|--wallpaper) wallpaper="${2:?--wallpaper needs an image file}"; shift 2 ;;
        -t|--theme-config) theme_config="${2:?--theme-config needs a JSON file}"; shift 2 ;;
        -c|--captures) captures="${2:?--captures needs a directory}"; shift 2 ;;
        -n|--dry-run) dry_run=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown option: $1 (try --help)" >&2; exit 2 ;;
    esac
done

case "$launcher_mode" in
    install|home|skip) ;;
    *) echo "ERROR: --launcher takes install, home or skip, not '$launcher_mode'." >&2; exit 2 ;;
esac

# Bad input is cheapest to catch before a Gradle build starts.
if [[ -n "$wallpaper" && ! -f "$wallpaper" ]]; then
    echo "ERROR: no wallpaper image at $wallpaper." >&2
    exit 1
fi
if [[ -n "$theme_config" && ! -f "$theme_config" ]]; then
    echo "ERROR: no theme config at $theme_config." >&2
    exit 1
fi

# One place that knows how a command is printed for --dry-run, so the printed line is the line that
# would really run.
adb_tv() {
    if (( dry_run )); then
        printf '  adb -s %s %s\n' "$device" "$*"
        return 0
    fi
    adb -s "$device" "$@"
}

resolve_device() {
    if [[ -n "$device" ]]; then
        return 0
    fi
    if [[ -n "${ADB_SERIAL:-}" ]]; then
        device="$ADB_SERIAL"
        return 0
    fi
    if [[ -n "${TV_HOST:-}" ]]; then
        device="$TV_HOST"
        # A bare IP is not an adb endpoint by itself; adb wants host:port.
        if [[ "$device" != *:* ]]; then
            device="$device:${TV_PORT:-5555}"
        fi
        return 0
    fi
    if (( dry_run )); then
        # Looking for a TV would mean running adb, and --dry-run must not talk to the TV at all.
        device="<device>"
        return 0
    fi
    local -a connected=()
    local serial
    while IFS= read -r serial; do
        if [[ -n "$serial" ]]; then
            connected+=("$serial")
        fi
    done < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
    if (( ${#connected[@]} != 1 )); then
        if (( ${#connected[@]} == 0 )); then
            echo "ERROR: no TV is connected to adb." >&2
        else
            echo "ERROR: more than one device is connected; adb cannot tell which TV you mean:" >&2
            printf '  %s\n' "${connected[@]}" >&2
        fi
        echo "Pass --device <serial|host:port>, or set ADB_SERIAL, or set TV_HOST." >&2
        exit 1
    fi
    device="${connected[0]}"
}

# Without adb nothing below works, and a missing binary would otherwise surface as a confusing
# "no TV is connected".
if (( ! dry_run )) && ! command -v adb >/dev/null 2>&1; then
    echo "ERROR: adb is not on PATH. Install Android platform-tools and put its folder on PATH." >&2
    exit 1
fi

resolve_device

if [[ "$device" == *:* ]]; then
    # A network TV is only an adb device after a connect; USB serials are already there.
    if (( dry_run )); then
        printf '  adb connect %s\n' "$device"
    else
        adb connect "$device"
    fi
fi
# get-state answers only when adb can reach the TV; without this check the next command would fail
# with whatever the shell happens to print, not with what went wrong.
if ! adb_tv get-state; then
    echo "ERROR: adb cannot reach $device. Check the TV's debugging prompt, the address, or a USB cable." >&2
    exit 1
fi
echo "Using ADB device $device"

if (( dry_run )); then
    echo "Dry run: nothing below is executed and the TV is left untouched."
fi

# Add-ons that embed an API key read their build.gradle for it, so the check follows the build file
# instead of a list of names here.
addon_needs_api_key() {
    local gradle_file="$1/app/build.gradle"
    [[ -f "$gradle_file" ]] && grep -Eq 'API_KEY|\.env\.local' "$gradle_file"
}

check_api_key() {
    if [[ ! -f "$api_key_file" ]] || ! grep -Eq '^API_KEY=[A-Za-z0-9]+$' "$api_key_file"; then
        echo "ERROR: this add-on embeds API_KEY at build time, and $api_key_file does not hold a valid" >&2
        echo "API_KEY=<key> line. Add one (the file stays outside the repo):" >&2
        echo "  printf 'API_KEY=your-key-here\\n' >> '$api_key_file'" >&2
        echo "The value is never printed by this script and is never written inside the repo." >&2
        return 1
    fi
}

build_gradle() {   # project dir, then the Gradle tasks
    local dir="$1"
    shift
    if (( dry_run )); then
        printf '  (cd %s && ./gradlew --no-daemon %s)\n' "$dir" "$*"
        return 0
    fi
    ( cd "$dir" && ./gradlew --no-daemon "$@" )
}

install_apk() {
    local apk="$1" output
    if (( dry_run )); then
        printf '  adb -s %s install -r %s\n' "$device" "$apk"
        return 0
    fi
    output="$(adb -s "$device" install -r "$apk")"
    printf '%s\n' "$output"
    case "$output" in
        *Success*) return 0 ;;
        *)
            echo "ERROR: adb install -r $apk failed (see the line above)." >&2
            return 1 ;;
    esac
}

addon_app_id() {
    sed -n "s/.*applicationId[[:space:]]*['\"]\([^'\"]*\)['\"].*/\1/p" "$1/app/build.gradle" | head -n 1
}

# The activity to open: the one that claims the launcher tile, which is not always the first one in
# the manifest (a settings or boot receiver can come before it).
addon_activity() {
    awk '
        /^[[:space:]]*<activity([[:space:]>]|$)/ {
            inside = 1
            filtered = 0
            name = ""
            if (match($0, /android:name="[^"]*"/)) {
                name = substr($0, RSTART + 14, RLENGTH - 15)
            }
            next
        }
        inside && /<intent-filter/ { filtered = 1 }
        inside && !filtered && /^[[:space:]]*android:name="/ {
            line = $0
            sub(/.*android:name="/, "", line)
            sub(/".*/, "", line)
            name = line
            next
        }
        inside && filtered && /android.intent.category.(LEANBACK_)?LAUNCHER/ && name != "" {
            print name
            exit
        }
        /<\/activity>/ { inside = 0 }
    ' "$1/app/src/main/AndroidManifest.xml"
}

build_addon() {
    local name="$1" dir="$repo_root/addons/$name" apk app_id activity
    if [[ ! -d "$dir" ]]; then
        echo "ERROR: no add-on folder at addons/$name." >&2
        return 1
    fi
    if [[ ! -x "$dir/gradlew" ]]; then
        echo "Skipping add-on '$name': addons/$name has no gradlew, so it is not a Gradle project of its" >&2
        echo "own; nothing was built or installed for it." >&2
        return 0
    fi
    if addon_needs_api_key "$dir"; then
        if ! check_api_key; then
            if (( dry_run )); then
                echo "Dry run: a real run would skip add-on '$name'." >&2
                return 0
            fi
            echo "Skipping add-on '$name'; the others still build." >&2
            return 1
        fi
    fi
    if [[ -z "$(addon_app_id "$dir")" ]]; then
        echo "ERROR: could not read applicationId from addons/$name/app/build.gradle." >&2
        return 1
    fi
    app_id="$(addon_app_id "$dir")"
    apk="$dir/app/build/outputs/apk/release/app-release.apk"
    echo "Building and testing add-on $name ($app_id)..."
    build_gradle "$dir" :app:testDebugUnitTest :app:assembleRelease
    install_apk "$apk" || return 1

    # Screenshots are named after the app, and `am start` needs the real activity, not a guess.
    activity="$(addon_activity "$dir")"
    [[ -n "$activity" ]] || activity=".MainActivity"
    installed_activities+=("$app_id/$activity")
    return 0
}

build_launcher() {
    if [[ "${SKIP_BUILD:-}" == 1 ]]; then
        if [[ ! -f "$launcher_apk" ]]; then
            echo "ERROR: SKIP_BUILD=1 but there is no $launcher_apk; build it first (tools/docker-build.sh)." >&2
            return 1
        fi
        echo "SKIP_BUILD=1: using the launcher APK that is already built."
        return 0
    fi
    echo "Building the launcher..."
    build_gradle "$repo_root/launcher" :app:assembleRelease
}

check_launcher_size() {
    if (( dry_run )); then
        return 0
    fi
    [[ -f "$launcher_apk" ]] || return 0
    local size
    size="$(wc -c < "$launcher_apk" | tr -d '[:space:]')"
    if (( size > apk_size_gate )); then
        echo "WARNING: the launcher APK is $size bytes and the CI size gate is $apk_size_gate bytes" >&2
        # Two separate facts: a big APK still installs, but it cannot be merged on this fork.
        echo "WARNING: the CI size gate on this fork will fail on it, though the install below goes ahead." >&2
    fi
}

# The provider writes as the launcher, which is the only way in on TVs where the shell cannot write
# Android/data; the staged copy in /data/local/tmp is readable by the shell that runs content.
write_theme_file() {
    local source="$1" relative="$2" staged output
    staged="/data/local/tmp/cyt-theme-${relative//\//-}"
    if (( dry_run )); then
        printf '  adb -s %s push %s %s\n' "$device" "$source" "$staged"
        printf '  adb -s %s shell "content write --uri %s/%s < %s; rm -f %s"\n' \
            "$device" "$theme_uri" "$relative" "$staged" "$staged"
        return 0
    fi
    adb -s "$device" push "$source" "$staged" >/dev/null
    output="$(adb -s "$device" shell "content write --uri $theme_uri/$relative < $staged; rc=\$?; rm -f $staged; exit \$rc")"
    case "$output" in
        *"Unsupported operation"*|*"Error:"*|*"Permission Denial:"*)
            echo "TV refused the launcher theme write for $relative: $output" >&2
            return 1 ;;
    esac
}

apply_theme() {
    if [[ -z "$wallpaper" && -z "$theme_config" ]]; then
        return 0
    fi
    echo "Applying the theme through the launcher's theme provider..."
    if [[ -n "$wallpaper" ]]; then
        # Wallpapers of an earlier theme stay behind otherwise and keep showing in a rotation.
        if (( dry_run )); then
            printf '  adb -s %s shell content delete --uri %s/wallpapers\n' "$device" "$theme_uri"
        else
            adb -s "$device" shell content delete --uri "$theme_uri/wallpapers" >/dev/null
        fi
        write_theme_file "$wallpaper" wallpaper.jpg
    fi
    if [[ -n "$theme_config" ]]; then
        write_theme_file "$theme_config" config.json
    else
        # A single image: rotation off, so the TV does not cycle through the theme's other pictures.
        local default_config
        if (( dry_run )); then
            printf '  (write {"wallpaper":true,"wallpaper_rotation_minutes":0} to a temp file, then push it as config.json)\n'
        else
            default_config="$(mktemp)"
            printf '%s\n' '{"wallpaper":true,"wallpaper_rotation_minutes":0}' > "$default_config"
            write_theme_file "$default_config" config.json
            rm -f "$default_config"
        fi
    fi
    # The launcher only rereads the provider when the revision changes, so push a fresh one.
    if (( dry_run )); then
        printf '  (write a fresh revision id to a temp file, then push it as theme_revision)\n'
    else
        local revision_file
        revision_file="$(mktemp)"
        printf '%s' "$(uuidgen 2>/dev/null || date +%s)" > "$revision_file"
        write_theme_file "$revision_file" theme_revision
        rm -f "$revision_file"
    fi
}

capture_screen() {
    local name="$1"
    if (( dry_run )); then
        printf '  adb -s %s exec-out screencap -p > %s/%s.png\n' "$device" "$captures" "$name"
        return 0
    fi
    adb -s "$device" exec-out screencap -p > "$captures/$name.png"
}

# A screen that will not open is worth a warning, not the end of the run: the other screens and the
# logcat are still worth having.
open_screen() {
    local component="$1"
    if (( dry_run )); then
        printf '  adb -s %s shell am start -W -n %s\n' "$device" "$component"
        return 0
    fi
    adb -s "$device" shell am start -W -n "$component" \
        || echo "WARNING: could not open $component; its screenshot shows whatever the TV has on screen." >&2
}

# Screens need a moment to draw their first real frame; without the wait the capture is an empty
# background on a cold start.
wait_for_screen() {
    if (( dry_run )); then
        return 0
    fi
    sleep 10
}

capture_screens() {
    local addon_component
    echo "Waking the TV..."
    adb_tv shell input keyevent KEYCODE_WAKEUP
    adb_tv shell input keyevent KEYCODE_HOME
    # Empty arrays need the guard on the old bash macOS ships, where "${a[@]}" is an unbound variable.
    if (( ${#installed_activities[@]} > 0 )); then
        for addon_component in "${installed_activities[@]}"; do
            echo "Opening $addon_component and capturing its screen..."
            open_screen "$addon_component"
            wait_for_screen
            capture_screen "${addon_component%%/*}"
        done
    fi
    echo "Opening the launcher and capturing its screen..."
    open_screen "$launcher_pkg/.MainActivity"
    wait_for_screen
    capture_screen "$launcher_pkg"
    # Crashing and lifecycle lines are the ones worth keeping; app-specific log tags change with the
    # app, so they do not belong in this script.
    if (( dry_run )); then
        printf '  adb -s %s logcat -d -s AndroidRuntime ActivityManager %s > %s/logcat.txt\n' \
            "$device" "$launcher_pkg" "$captures"
    else
        adb -s "$device" logcat -d -s AndroidRuntime ActivityManager "$launcher_pkg" > "$captures/logcat.txt"
    fi
}

# Add-ons first: the launcher reads them through a signature-protected ContentProvider, so the
# add-on's signature permission has to exist before the launcher is installed.
if [[ -n "$addons" ]]; then
    IFS=',' read -r -a selected_addons <<< "$addons"
    for name in "${selected_addons[@]}"; do
        name="${name// /}"
        [[ -n "$name" ]] || continue
        if ! build_addon "$name"; then
            failures=$((failures + 1))
        fi
    done
fi

if [[ "$launcher_mode" == skip ]]; then
    echo "Launcher left alone (--launcher skip)."
else
    build_launcher
    check_launcher_size
    # The APK is built once; the handover below only needs the one that now exists.
    install_apk "$launcher_apk"
fi

# The theme goes in before the launcher is started, so the first frame it draws already has it.
apply_theme

if [[ "$launcher_mode" == home ]]; then
    handover_host="${TV_HOST:-}"
    if [[ -z "$handover_host" && "$device" == *:* ]]; then
        handover_host="${device%%:*}"
    fi
    if [[ -z "$handover_host" || "$handover_host" == "<device>" ]]; then
        if (( dry_run )); then
            handover_host="<TV_HOST>"
        else
            echo "ERROR: --launcher home delegates the Home handover to launcher/build-and-install.sh, which" >&2
            echo "needs TV_HOST set to the TV's IP address." >&2
            exit 1
        fi
    fi
    if (( ! dry_run )) && [[ ! -x "$repo_root/tools/venv/bin/python" && ! -x "$repo_root/tools/venv/Scripts/python.exe" ]]; then
        echo "ERROR: --launcher home needs tools/venv; make one first:" >&2
        echo "  python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2
        exit 1
    fi
    echo "Handing Home over to the launcher..."
    if (( dry_run )); then
        printf '  TV_HOST=%s SKIP_BUILD=1 %s/launcher/build-and-install.sh\n' "$handover_host" "$repo_root"
    else
        TV_HOST="$handover_host" SKIP_BUILD=1 "$repo_root/launcher/build-and-install.sh"
    fi
fi

if (( dry_run )); then
    printf '  mkdir -p %s\n' "$captures"
else
    mkdir -p "$captures"
fi
capture_screens

if (( failures > 0 )); then
    echo "Finished with $failures problem(s) above; the screenshots are in $captures." >&2
    exit 1
fi
echo "Done. Screenshots and logs: $captures"
