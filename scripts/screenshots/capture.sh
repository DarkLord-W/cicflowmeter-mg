#!/usr/bin/env bash
# Re-take the screenshots in docs/images.
#
# Requirements: Xvfb, xfwm4, xdotool, ImageMagick (import, magick), qterminal,
# JDK 8, a built fat jar, and a sample pcap.
#
# Usage:
#   ./scripts/screenshots/capture.sh gui        # window screenshots only
#   ./scripts/screenshots/capture.sh cli        # terminal screenshots only
#   ./scripts/screenshots/capture.sh all
#
# Window screenshots need manual interaction for the dialogs: the script opens
# each one, waits, and captures. Run it interactively and press Enter between
# steps when prompted.
set -euo pipefail

REPO="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$REPO/docs/images"
WORK="${WORK:-/tmp/cicfm}"
DISPLAY_NUM="${DISPLAY_NUM:-:99}"
JAVA_HOME="${JAVA_HOME:-/path/to/jdk8}"
JAR="$REPO/build/libs/cicflowmeter-gui-4.1-enhanced.jar"
CLI_DIR="$WORK/dist/bin"

frame() {
    local src=$1 dst=$2 width=$3
    magick "$src" -bordercolor '#d0d7de' -border 1 \
        -bordercolor '#f6f8fa' -border 12 \
        -resize "${width}x>" -strip -define png:compression-level=9 \
        "$OUT/$dst"
}

start_display() {
    Xvfb "$DISPLAY_NUM" -screen 0 1400x900x24 -dpi 96 &
    sleep 2
    DISPLAY=$DISPLAY_NUM xfwm4 --compositor=off &
    sleep 2
}

start_gui() {
    DISPLAY=$DISPLAY_NUM "$JAVA_HOME/bin/java" \
        -Djava.library.path="$REPO/jnetpcap/linux/jnetpcap-1.4.r1425" \
        -Dcicflowmeter.config.dir="$WORK/config" \
        -jar "$JAR" &
    sleep 10
    local w
    w=$(DISPLAY=$DISPLAY_NUM xdotool search --name '^CICFlowMeter$' | head -1)
    DISPLAY=$DISPLAY_NUM xdotool windowsize "$w" 1280 800
    echo "$w"
}

shot_window() {
    local w=$1 name=$2 width=$3
    DISPLAY=$DISPLAY_NUM import -window "$w" "$WORK/$name-raw.png"
    frame "$WORK/$name-raw.png" "$name.png" "$width"
    echo "  $name.png"
}

# The offline panel needs its two path fields filled; xdotool types into them at
# fixed coordinates measured against the 1280x800 window.
run_offline() {
    local w=$1
    DISPLAY=$DISPLAY_NUM xdotool mousemove --window "$w" 61 9 click 1; sleep 1
    DISPLAY=$DISPLAY_NUM xdotool mousemove --window "$w" 80 32 click 1; sleep 2
    DISPLAY=$DISPLAY_NUM xdotool mousemove --window "$w" 620 609 click 1
    DISPLAY=$DISPLAY_NUM xdotool type --delay 15 "$WORK/pcaps"
    DISPLAY=$DISPLAY_NUM xdotool mousemove --window "$w" 620 657 click 1
    DISPLAY=$DISPLAY_NUM xdotool type --delay 15 "$WORK/out"
    DISPLAY=$DISPLAY_NUM xdotool mousemove --window "$w" 640 760 click 1
    sleep 25
}

cmd_gui() {
    start_display
    local w
    w=$(start_gui)
    shot_window "$w" gui-main 1280

    DISPLAY=$DISPLAY_NUM xdotool mousemove --window "$w" 61 9 click 1; sleep 1
    shot_window "$w" gui-network-menu 1280
    DISPLAY=$DISPLAY_NUM xdotool key --window "$w" Escape; sleep 1

    run_offline "$w"
    shot_window "$w" gui-offline-run 1280

    # dialogs are separate windows
    DISPLAY=$DISPLAY_NUM xdotool mousemove --window "$w" 61 9 click 1; sleep 1
    DISPLAY=$DISPLAY_NUM xdotool mousemove --window "$w" 85 32 click 1; sleep 1
    shot_window "$w" gui-offline 1280

    DISPLAY=$DISPLAY_NUM xdotool mousemove --window "$w" 762 47 click 1; sleep 2
    local d
    d=$(DISPLAY=$DISPLAY_NUM xdotool search --name 'API和CSV配置' | head -1)
    DISPLAY=$DISPLAY_NUM import -window "$d" "$WORK/gui-api-settings-raw.png"
    frame "$WORK/gui-api-settings-raw.png" gui-api-settings 900
    DISPLAY=$DISPLAY_NUM xdotool mousemove --window "$d" 317 290 click 1; sleep 1

    DISPLAY=$DISPLAY_NUM xdotool mousemove --window "$w" 1030 47 click 1; sleep 2
    d=$(DISPLAY=$DISPLAY_NUM xdotool search --name '时间规则配置' | head -1)
    DISPLAY=$DISPLAY_NUM import -window "$d" "$WORK/gui-time-rules-raw.png"
    frame "$WORK/gui-time-rules-raw.png" gui-time-rules 900
    DISPLAY=$DISPLAY_NUM xdotool mousemove --window "$d" 703 581 click 1; sleep 1

    DISPLAY=$DISPLAY_NUM xdotool mousemove --window "$w" 113 9 click 1; sleep 1
    DISPLAY=$DISPLAY_NUM xdotool mousemove --window "$w" 113 33 click 1; sleep 2
    d=$(DISPLAY=$DISPLAY_NUM xdotool search --name 'About CICFlowMeter' | head -1)
    DISPLAY=$DISPLAY_NUM import -window "$d" "$WORK/gui-about-raw.png"
    frame "$WORK/gui-about-raw.png" gui-about 900
}

# Terminal shots: each runs one command, prints it first, then waits so the
# capture lands on a settled window.
shot_terminal() {
    local name=$1 shown=$2 run=$3
    local script="$WORK/bin/$name.sh"
    cat > "$script" <<EOF
#!/bin/bash
export JAVA_HOME=$JAVA_HOME
export PATH=$CLI_DIR:\$JAVA_HOME/bin:/usr/local/bin:/usr/bin:/bin
export PS1='\$ '
cd $WORK
clear
echo '\$ $shown'
$run
sleep 300
EOF
    chmod +x "$script"
    DISPLAY=$DISPLAY_NUM qterminal --workdir "$WORK" -e "bash $script" >/dev/null 2>&1 &
    sleep 7
    local q
    q=$(DISPLAY=$DISPLAY_NUM xdotool search --name '^qterminal$' | head -1)
    DISPLAY=$DISPLAY_NUM xdotool windowsize "$q" 1100 760
    DISPLAY=$DISPLAY_NUM xdotool windowmove "$q" 10 10
    sleep 1
    DISPLAY=$DISPLAY_NUM import -window root "$WORK/root-$name.png"
    magick "$WORK/root-$name.png" -crop 1102x792+9+9 +repage "$WORK/$name-raw.png"
    frame "$WORK/$name-raw.png" "$name.png" 1100
    local pid
    pid=$(DISPLAY=$DISPLAY_NUM xdotool getwindowpid "$q" 2>/dev/null || true)
    [ -n "$pid" ] && kill "$pid" 2>/dev/null || true
    sleep 1
}

cmd_cli() {
    mkdir -p "$WORK/out" "$WORK/pcaps"
    shot_terminal cli-help "cicflowmeter-cli --help" \
        "cicflowmeter-cli --help 2>&1 | head -30"
    shot_terminal cli-capture-help "cicflowmeter-cli capture --help" \
        "cicflowmeter-cli capture --help 2>&1 | head -22"
    shot_terminal cli-analyze "cicflowmeter-cli analyze $WORK/pcaps $WORK/out" \
        "cicflowmeter-cli analyze $WORK/pcaps $WORK/out 2>&1 | grep -vE 'INFO|^\\s+at ' | tail -12"
    shot_terminal cli-status "cicflowmeter-cli status" \
        "cicflowmeter-cli status 2>&1 | grep -vE 'INFO' | head -20"
    shot_terminal health-endpoint "curl -s http://127.0.0.1:8899/health" \
        "\cicflowmeter-cli health -s -p 8899 >/dev/null 2>&1 & sleep 4; curl -s --max-time 5 http://127.0.0.1:8899/health | head -22"
}

case "${1:-all}" in
    gui) cmd_gui ;;
    cli) cmd_cli ;;
    all) cmd_gui; cmd_cli ;;
    *) echo "usage: $0 [gui|cli|all]" >&2; exit 2 ;;
esac

echo "done: $OUT"
