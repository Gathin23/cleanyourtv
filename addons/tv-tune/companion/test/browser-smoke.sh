#!/usr/bin/env bash
# Reproducible UI check. Uses a synthetic camera, never a physical device.
set -euo pipefail

tv_tune_base="${1:-http://127.0.0.1:8765}"
tv_tune_session="tv-tune-smoke-$$"
tv_tune_output=$(mktemp -d /private/tmp/tv-tune-browser.XXXXXX)
export AGENT_BROWSER_ARGS='--use-fake-device-for-media-stream,--use-fake-ui-for-media-stream'
browser() { agent-browser --session "$tv_tune_session" "$@"; }
trap 'browser close >/dev/null 2>&1 || true' EXIT

browser open "$tv_tune_base/?lang=en"
browser eval '(() => { if (document.querySelector("#preview").srcObject) throw Error("Camera started without consent"); return "No automatic camera start"; })()'
browser click '#start'
browser wait --text 'Camera active'
browser check '#confirm'
browser click '#capture'
browser wait --text 'Capture finished'
browser click '#review'
browser eval '(() => { const r = JSON.parse(document.querySelector("#report-preview").textContent); if (r.quality.sampleCount !== 45 || r.colourMeasurementValidated !== false) throw Error("Invalid capture report"); return "45 simulated frames, no calibration claim"; })()'
browser download '#download' "$tv_tune_output/report.json"
node --input-type=module -e 'import fs from "node:fs"; const r=JSON.parse(fs.readFileSync(process.argv[1],"utf8")); if(r.status!=="experimental"||r.colourMeasurementValidated!==false||!r.quality)throw Error("Invalid downloaded report"); console.log("Downloaded aggregate report verified");' "$tv_tune_output/report.json"
browser click '#stop'
browser eval '(() => { if (document.querySelector("#preview").srcObject || document.querySelector("#confirm").checked) throw Error("Stop did not clear stream and confirmation"); return "Camera stopped and confirmation cleared"; })()'
browser set viewport 390 844
browser eval '(() => { if (document.documentElement.scrollWidth > innerWidth) throw Error("Mobile horizontal overflow"); return "Mobile layout fits"; })()'
browser screenshot "$tv_tune_output/mobile-simulated.png" --full
browser a11y --tags wcag2a,wcag2aa --json > "$tv_tune_output/accessibility.json"
node --input-type=module -e 'import fs from "node:fs"; const r=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));if(!r.success||r.data.counts.violations!==0)throw Error("Accessibility violations");console.log("Automated A/AA check: zero violations; manual-review items remain in report");' "$tv_tune_output/accessibility.json"
browser open "$tv_tune_base/?view=tv&lang=en"
browser set viewport 1920 1080
browser press ArrowRight
browser eval '(() => { const e=document.querySelector("#tv-patch"),r=e.getBoundingClientRect();if(document.querySelector("#tv-patch-id").textContent!=="grey-192"||getComputedStyle(e).backgroundColor!=="rgb(192, 192, 192)"||Math.abs(r.width*r.height-207360)>50)throw Error("Incorrect TV patch");return "TV patch ID, RGB and area verified"; })()'
browser screenshot "$tv_tune_output/tv-browser.png"
browser errors
printf 'Browser verification artifacts: %s\n' "$tv_tune_output"
