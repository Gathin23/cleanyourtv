# Measurement research protocol

Status: proposed experiment, not a validation result. No reference measurements
have been collected. The shipped companion remains a processed-video compatibility
probe; its JSON is not a calibration profile. This protocol separates software
simulation, camera repeatability and emitted-light accuracy.

## 1. Establish a bounded claim before collecting data

The first candidate claim is relative SDR colour estimation for one named phone,
camera/lens, OS/browser version, TV/panel, picture preset and input path. Do not
generalise to other phones, panels, HDR, apps or HDMI inputs. Record software commit,
pattern RGB codes, render size, patch area/background, viewing geometry, ambient
conditions and TV warm-up duration. Keep device serials and network identifiers out
of published evidence. Changing any pipeline component requires revalidation.

Choose the target white, transfer function, peak luminance and viewing conditions
before fitting. App sRGB codes are stimuli, not proof of the light a TV emits.
The current eleven compatibility patches alone are not a characterisation dataset.

## 2. Camera-control feasibility gate

The W3C [Image Capture working draft](https://www.w3.org/TR/image-capture/)
defines exposure, white-balance and focus capabilities, constraints and settings.
An API name or advertised mode does not establish support on a particular phone.
The [Media Capture specification](https://www.w3.org/TR/mediacapture-streams/)
distinguishes requested constraints from current settings.

For each camera, inspect capabilities after permission, select only supported
controls, apply constraints and read settings back. Record requested and observed
values separately; reject missing, clamped or changing values for a fixed-control
experiment. Repeat after Stop/Start and background/restore. Verify exposure, gain,
focus and white balance remain fixed across dark, bright and coloured stimuli.
Failure leaves that configuration in compatibility-only mode. Manual UI controls
must not be added merely because a synthetic track advertises them.

If browser controls cannot be verified, evaluate a separate native capture prototype
with documented camera APIs and RAW availability on the target device. RAW is not
automatically calibrated either: black level, sensor response, lens shading and
spectral response still need characterisation. No native adapter is implemented yet.

## 3. Reference acquisition and split

Use an independently calibrated reference instrument suitable for the panel's
spectrum. Record its model, calibration date, integration settings and uncertainty;
characterise a tristimulus meter against a suitable spectroradiometer if necessary.
Do not use the phone under test as its own reference. No instrument is selected or
claimed available in this project.

Proposed first study: 125 RGB-grid patches (five prespecified channel levels),
21 neutral steps and 24 prespecified off-grid held-out colours. Freeze exact RGB
lists, split membership, capture order seed and analysis code in a study manifest
before collection. Reserve the off-grid set and a subset of neutral steps entirely
from model selection. Add an independently acquired later-session holdout.

Collect three independent repeats per patch, with fixed geometry and matched meter
and camera sampling locations. Interleave reference whites and blacks to detect
drift; randomise within blocks to expose adaptation/order effects. Pair instrument
XYZ/luminance with phone aggregates only after settling. Set settling and drift
rejection limits during a separate pilot, then freeze them before the main study.
Record rejected captures and reasons, not just successful samples. Control reflections,
clipping, automatic brightness limiting, PWM/scan aliasing and screen nonuniformity.
Camera temporal stability alone cannot rule these out.

## 4. Fit, evaluate and decide

Fit camera-to-XYZ only on the training split. Select model complexity using a separate
validation split; never tune on the final holdout. Compare a simple baseline with the
candidate and publish residuals by luminance and hue, not only a global average.
Compute colour differences only after a documented XYZ-to-Lab conversion with a
fixed reference white; never treat camera RGB as Lab or measured sRGB.

Use [CIEDE2000](https://www.cie.co.at/publications/colorimetry-part-6-ciede2000-colour-difference-formula-1)
for the colour-difference metric and verify its implementation against
[Sharma, Wu and Dalal's supplementary test pairs](https://hajim.rochester.edu/ece/sites/gsharma/ciede2000/).
The metric is not itself a display certification or a universal acceptability limit.

Proposed engineering go/no-go limits, to preregister before collection: held-out
median Delta E00 <= 2, 95th percentile <= 4 and maximum <= 6, plus relative luminance
error <= 5% for patches above the instrument's declared low-light floor. These are
project targets, not standards or achieved results. Report absolute errors and
uncertainty below that floor rather than divide by near-zero luminance. Require
the limits in every independent session, account for instrument uncertainty and
report confidence intervals by patch/session, not by treating adjacent video frames
as independent experiments. If uncertainty crosses a limit, the result is inconclusive.

Repeat on a separately held-out phone/TV pair before considering any cross-device
claim. A failed pair stays unsupported. Publish the manifest, sanitised aggregates,
analysis version, exclusions and failures. Until these gates pass, keep
`colourMeasurementValidated: false`; simulations must never change it.

## 5. Correction is a separate gate

No automatic TV-control adapter is currently implemented or validated. For each
candidate manufacturer, require official documented read/write interfaces, a scoped
permission model and exact supported models/firmware before implementation. Save
original values, constrain writes to ordinary picture controls, read back each write,
test interrupted transactions and verify restoration. Never use service menus, root,
hidden commands or assume an Android app can correct other apps or HDMI inputs.

Validate before/after emitted light on held-out patches using the independent meter.
Do not ship automatic correction merely because estimation passed. Without a safe,
documented and reversible interface, remain in user-guided adjustment mode.

## Evidence labels

- Unit/DOM simulation: verifies software logic only.
- Browser with synthetic camera: verifies browser integration, not a sensor or panel.
- Emulator: verifies Android UI/runtime in that emulator, not TV hardware.
- Physical-device capture: establishes only the recorded device/path behaviour.
- Reference study: may support only the prespecified claim if its gates pass.

The real-TV screenshot and tested-on requirements in [VALIDATION.md](VALIDATION.md)
remain independent upstream contribution gates and cannot be replaced by this plan.
