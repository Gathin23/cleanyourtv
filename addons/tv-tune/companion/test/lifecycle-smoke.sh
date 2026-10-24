#!/usr/bin/env bash
# Deterministically hold an old capture while starting another. Synthetic camera only.
set -euo pipefail
tv_tune_base="${1:-http://127.0.0.1:8765}"
tv_tune_session="tv-tune-lifecycle-$$"
export AGENT_BROWSER_ARGS='--use-fake-device-for-media-stream,--use-fake-ui-for-media-stream'
browser() { agent-browser --session "$tv_tune_session" "$@"; }
trap 'browser close >/dev/null 2>&1 || true' EXIT
browser open "$tv_tune_base/?lang=en"
browser click '#start'
browser wait --text 'Camera active'
browser eval '(async () => {
  const byId=id=>document.getElementById(id),original=window.setTimeout,held=[];
  const pause=ms=>new Promise(resolve=>original(resolve,ms));
  const confirm=()=>{byId("confirm").checked=true;byId("confirm").dispatchEvent(new Event("change"));};
  window.setTimeout=(fn,ms,...args)=>ms===70?held.push(()=>fn(...args)):original(fn,ms,...args);
  try {
    confirm();byId("capture").click();
    if(held.length!==1)throw Error("First capture did not reach its sampling wait");
    byId("stop").click();byId("start").click();
    for(let i=0;i<60&&!byId("camera-status").textContent.startsWith("Camera active");i++)await pause(50);
    if(!byId("camera-status").textContent.startsWith("Camera active"))throw Error("Restart failed");
    confirm();byId("capture").click();
    if(held.length!==2)throw Error("Second capture did not start");
    held.shift()();await pause(0);
    if(!byId("capture").disabled)throw Error("An old cancelled capture unlocked the active capture");
    return "Old cancellation cannot unlock a new capture";
  } finally {
    byId("stop").click();while(held.length)held.shift()();window.setTimeout=original;await pause(0);
  }
})()'
browser eval '(() => { if(document.querySelector("#controls-state").textContent!=="Camera off")throw Error("Stopped camera retains an active-controls label");return "Stopped camera controls are explicitly inactive"; })()'
browser errors
