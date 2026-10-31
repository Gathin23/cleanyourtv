#!/usr/bin/env bash
# Reproducible UI check. Uses a synthetic camera, never a physical device.
set -euo pipefail

tv_tune_base="${1:-http://127.0.0.1:8765}"
tv_tune_session="tv-tune-smoke-$$"
tv_tune_output=$(mktemp -d "${TMPDIR:-/tmp}/tv-tune-browser.XXXXXX")
export AGENT_BROWSER_ARGS='--use-fake-device-for-media-stream,--use-fake-ui-for-media-stream'
browser() { agent-browser --session "$tv_tune_session" "$@"; }
trap 'browser close >/dev/null 2>&1 || true' EXIT

browser open "$tv_tune_base/?view=tv&lang=en#patch=blue"
browser eval '(() => { if(document.querySelector("#tv-patch-id").textContent!=="blue")throw Error("Presenter ignored patch fragment");const link=new URL(document.querySelector("#phone-link").href);if(link.hash!=="#patch=blue"||link.searchParams.has("patch"))throw Error("Patch must stay in fragment");return "Patch-specific presenter link is fragment-only"; })()'
browser click '#handoff-toggle'
browser eval '(() => { if(!document.querySelector("#tv-patch").hidden||document.querySelector("#tv-patch-id").textContent!=="Paused")throw Error("Handoff chrome covers an active patch");return "Handoff hides and pauses measurement field"; })()'
browser click '#phone-link'
browser wait '#start'
browser eval '(() => { if(document.querySelector("#patch").value!=="blue"||document.querySelector("#confirm").checked||document.querySelector("#preview").srcObject)throw Error("Unsafe or mismatched phone handoff");return "Phone preselects blue without camera or confirmation"; })()'
browser click '#camera-jump'
browser eval '(() => { if(location.hash!=="#patch=blue"||document.querySelector("#patch").value!=="blue")throw Error("Camera section jump lost patch");return "Camera section preserves patch"; })()'
browser open "$tv_tune_base/?lang=en"
browser eval '(() => { if (document.querySelector("#preview").srcObject) throw Error("Camera started without consent"); return "No automatic camera start"; })()'
browser click '#start'
browser wait --text 'Camera active'
browser eval '(() => { const v=document.querySelector("#preview"),box=v.parentElement,reticle=box.querySelector(".reticle").getBoundingClientRect(),scale=Math.min(box.clientWidth/v.videoWidth,box.clientHeight/v.videoHeight);if(Math.abs(reticle.width-v.videoWidth*scale*0.2)>1||Math.abs(reticle.height-v.videoHeight*scale*0.2)>1)throw Error("Reticle does not represent sampled video pixels");return "Landscape sampling overlay verified"; })()'
browser eval '(async () => { const v=document.querySelector("#preview"),track=v.srcObject.getVideoTracks()[0];await track.applyConstraints({width:{exact:720},height:{exact:1280}});for(let i=0;i<40&&(v.videoWidth!==720||v.videoHeight!==1280);i++)await new Promise(r=>setTimeout(r,50));await new Promise(requestAnimationFrame);const box=v.parentElement,b=box.getBoundingClientRect(),r=box.querySelector(".reticle").getBoundingClientRect(),scale=Math.min(box.clientWidth/v.videoWidth,box.clientHeight/v.videoHeight),w=v.videoWidth*scale*0.2,h=v.videoHeight*scale*0.2;if(v.videoWidth!==720||v.videoHeight!==1280||Math.abs(r.width-w)>1||Math.abs(r.height-h)>1||Math.abs(r.left-b.left-box.clientLeft-(box.clientWidth-w)/2)>1||Math.abs(r.top-b.top-box.clientTop-(box.clientHeight-h)/2)>1)throw Error("Portrait sampling overlay did not follow decoded video");return "Portrait sampling overlay position and size verified"; })()'
browser check '#confirm'
browser click '#capture'
browser wait --text 'Capture finished'
browser click '#review'
browser eval '(() => { const r = JSON.parse(document.querySelector("#report-preview").textContent); if (r.quality.sampleCount !== 45 || r.colourMeasurementValidated !== false || r.capabilities.settings.width !== 720 || r.capabilities.settings.height !== 1280) throw Error("Invalid capture report"); return "45 simulated frames, current dimensions, no calibration claim"; })()'
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
browser focus '#next'
browser press Enter
browser eval '(() => { if(document.querySelector("#tv-patch-id").textContent!=="grey-240")throw Error("Enter did not activate focused Next button");return "Focused Next activates with Enter"; })()'
browser focus '#fullscreen'
browser press Enter
browser eval '(() => { if(!document.fullscreenElement)throw Error("Enter did not activate fullscreen");return "Fullscreen activates with Enter"; })()'
browser press Escape
browser focus '#fullscreen'
browser press Space
browser eval '(() => { if(!document.fullscreenElement)throw Error("Space did not activate fullscreen");return "Fullscreen activates with Space"; })()'
browser press Escape
browser focus '.tv-heading a'
browser press Enter
browser wait '#start'
browser eval '(() => { if(document.documentElement.lang!=="en"||document.querySelector("#start").textContent!=="Start camera")throw Error("Presenter return lost the selected language");return "Presenter return preserves English"; })()'
browser click '.brand'
browser wait '#start'
browser eval '(() => { if(document.documentElement.lang!=="en")throw Error("Home link lost the selected language");return "Home link preserves English"; })()'
browser errors
printf 'Browser verification artifacts: %s\n' "$tv_tune_output"
