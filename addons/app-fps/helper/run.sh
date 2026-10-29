#!/system/bin/sh
# Fixed-purpose shell helper. No network listener, arbitrary commands or app-supplied paths.
set -u
export LC_ALL=C
base=/data/local/tmp/app-fps-helper
cd "$base" || exit 1
mkdir run.lock 2>/dev/null || exit 1
trap '' HUP
echo "$$" > helper.pid
collecting=0
previous_layer=
previous_count=
previous_time=
stop_capture() {
    if [ "$collecting" = 1 ]; then
        timeout 4 dumpsys SurfaceFlinger --timestats -disable >/dev/null 2>&1
        collecting=0
    fi
    previous_layer=; previous_count=; previous_time=
}
send_sample() {
    timeout 4 am broadcast -a com.lionlev.tv.fps.SAMPLE -n com.lionlev.tv.fps/.SampleReceiver \
        --es fps "$1" --es app "$2" >/dev/null 2>&1
}
cleanup() {
    stop_capture
    send_sample -1 com.lionlev.tv.fps
    rm -f helper.pid live.txt stats.txt state.txt selected.txt
    rmdir run.lock 2>/dev/null
    echo stopped
}
trap cleanup EXIT
trap 'exit 143' TERM
trap 'exit 130' INT
home=$(timeout 4 cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | awk -F/ '/^[a-zA-Z0-9_.]+\// && !found {print $1; found=1}')
failures=0
echo "started pid=$$"
while :; do
    if ! timeout 4 content query --uri content://com.lionlev.tv.fps.state/state >state.txt 2>/dev/null \
        || ! grep -q 'enabled=[01]' state.txt; then
        stop_capture
        failures=$((failures+1))
        [ "$failures" -ge 3 ] && exit 1
        sleep 3
        continue
    fi
    failures=0
    if ! grep -q 'enabled=1' state.txt; then
        stop_capture
        sleep 5
        continue
    fi
    awake=$(timeout 4 dumpsys power | awk '/mWakefulness=/ && !found {sub(/.*mWakefulness=/, ""); print $1; found=1}')
    if [ "$awake" != Awake ]; then
        stop_capture
        send_sample -1 com.lionlev.tv.fps
        sleep 5
        continue
    fi
    component=$(timeout 4 dumpsys activity activities | awk '/mResumedActivity:|topResumedActivity=/ {
        for (i=1;i<=NF;i++) if (!found && $i ~ /^[a-zA-Z0-9_.]+\/[a-zA-Z0-9_.$]+$/) {print $i; found=1}
    }')
    app=${component%%/*}
    case "$component" in *'/'*) ;; *) app= ;; esac
    case "$app" in ''|com.gathin.tvhome|com.lionlev.tv.fps|"$home")
        stop_capture; send_sample -1 com.lionlev.tv.fps; sleep 1; continue ;;
    esac
    class=${component#*/}
    case "$class" in .*) component="$app/$app$class" ;; esac
    if [ "$collecting" = 0 ]; then
        timeout 4 dumpsys SurfaceFlinger --timestats -enable >/dev/null 2>&1
        collecting=1
    fi
    before=$(cut -d ' ' -f 1 /proc/uptime)
    timeout 4 dumpsys SurfaceFlinger --list >live.txt
    timeout 4 dumpsys SurfaceFlinger --timestats -dump >stats.txt
    after=$(cut -d ' ' -f 1 /proc/uptime)
    awk -v component="$component" -f select-layer.awk live.txt stats.txt >selected.txt
    layer=$(cut -f 1 selected.txt)
    count=$(cut -f 2 selected.txt)
    now=$(awk -v a="$before" -v b="$after" 'BEGIN {printf "%.3f", (a+b)/2}')
    value=-1
    if [ -n "$layer" ] && [ "$layer" = "$previous_layer" ] && [ -n "$previous_count" ]; then
        value=$(awk -v n="$count" -v p="$previous_count" -v t="$now" -v old="$previous_time" 'BEGIN {
            if (n>=p && t>old) {rate=(n-p)/(t-old); if (rate<=1000) {printf "%.1f",rate;exit}}
            print -1
        }')
    fi
    # Recheck the foreground immediately before publishing; never carry an old app onto Home.
    current=$(timeout 4 dumpsys activity activities | awk '/mResumedActivity:|topResumedActivity=/ {
        for (i=1;i<=NF;i++) if (!found && $i ~ /^[a-zA-Z0-9_.]+\/[a-zA-Z0-9_.$]+$/) {print $i; found=1}
    }')
    current_app=${current%%/*}
    [ "$current_app" != "$app" ] && value=-1
    send_sample "$value" "$app"
    previous_layer=$layer; previous_count=$count; previous_time=$now
    sleep 1
done
