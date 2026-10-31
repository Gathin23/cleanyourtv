import { qrSvg } from './qr.js';
import { inspectCapabilities, exportReport } from './probe.js';
import { createCameraController, captureFrames, pixelSample, sampleRegion, previewRegion } from './capture.js';
import { PATCHES, presenterState, patchRectangle, patchFromFragment, patchLink } from './patterns.js';

const $ = id => document.getElementById(id);
const params = new URLSearchParams(location.search), english = params.get('lang') === 'en';
const say = (es, en) => english ? en : es;
const homeLinks = [...document.querySelectorAll('a[href="?"]')];
const tvLinks = [...document.querySelectorAll('.tv-link')];
let selectedPatch = patchFromFragment(location.hash);
const updateLinks = patch => {
  selectedPatch = patch;
  homeLinks.forEach(link => { link.href = patchLink('phone', english, patch.id); });
  tvLinks.forEach(link => { link.href = patchLink('tv', english, patch.id); });
};
const rememberPatch = view => {
  try { history.replaceState(history.state, '', patchLink(view, english, selectedPatch.id)); } catch { /* Handoff still works without history access. */ }
};
if (english) {
  document.documentElement.lang = 'en';
  document.querySelectorAll('[data-en]').forEach(element => { element.textContent = element.dataset.en; });
}
$('language').textContent = english ? 'ES' : 'EN';
$('language').addEventListener('click', () => {
  location.href = patchLink(params.get('view'), !english, selectedPatch.id);
});
updateLinks(selectedPatch);

if (params.get('view') === 'tv') setupPresenter();
else setupPhone();

function setupPresenter() {
  document.body.dataset.view = 'tv'; $('phone-view').hidden = true; $('tv-view').hidden = false;
  const state = presenterState(performance.now());
  let handoffOpen = false, qrUrl = null;
  const setHandoffOpen = open => {
    handoffOpen = open; $('handoff-panel').hidden = !open;
    $('handoff-toggle').setAttribute('aria-expanded', String(open));
    $('handoff-toggle').textContent = open ? say('Cerrar enlace', 'Close link') : say('Enlace al móvil', 'Phone link');
  };
  state.index = PATCHES.indexOf(selectedPatch);
  const render = () => {
    const patch = PATCHES[state.index], rect = patchRectangle(innerWidth, innerHeight);
    updateLinks(patch);
    $('tv-patch').hidden = handoffOpen;
    const handoffUrl = new URL(patchLink('phone', english, patch.id), location.href);
    handoffUrl.username = ''; handoffUrl.password = '';
    const phoneUrl = handoffUrl.href;
    if ($('phone-link').href !== phoneUrl) $('handoff-status').textContent = '';
    $('phone-link').href = phoneUrl; $('phone-link').textContent = phoneUrl;
    if (handoffOpen && qrUrl !== phoneUrl) {
      qrUrl = phoneUrl;
      try {
        $('phone-qr').src = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(qrSvg(phoneUrl))}`;
        $('phone-qr').hidden = false;
        $('qr-status').textContent = say('Escanea con el móvil para abrir este patrón.', 'Scan with your phone to open this patch.');
      } catch {
        $('phone-qr').hidden = true;
        $('phone-qr').src = '';
        $('qr-status').textContent = say('QR no disponible: usa el enlace de abajo.', 'QR unavailable: use the link below.');
      }
    }
    $('handoff-patch').textContent = `${patch.id} · RGB ${patch.rgb.join(' / ')}`;
    Object.assign($('tv-patch').style, { width: `${rect.size}px`, height: `${rect.size}px`,
      left: `${rect.left}px`, top: `${rect.top}px`, backgroundColor: state.paused ? '#252525' : `rgb(${patch.rgb.join(',')})` });
    $('tv-patch-id').textContent = state.paused ? say('En pausa', 'Paused') : patch.id;
    $('tv-code').textContent = state.paused ? say('Pulsa una tecla para volver.', 'Press a key to resume.') : `RGB ${patch.rgb.join(' / ')} · ${innerWidth} × ${innerHeight}`;
    $('tv-status').textContent = say('Confirmación manual en el móvil · Sin sincronización automática', 'Manually confirm the ID on the phone · No automatic synchronisation');
  };
  const move = delta => { if (handoffOpen && $('handoff-panel').contains(document.activeElement)) $('handoff-toggle').focus(); setHandoffOpen(false); state.move(delta, performance.now()); render(); rememberPatch('tv'); };
  $('handoff-toggle').addEventListener('click', () => {
    setHandoffOpen(!handoffOpen); state.pause(); render();
  });
  const restorePatch = () => {
    state.index = PATCHES.indexOf(patchFromFragment(location.hash));
    state.pause(); render();
  };
  window.addEventListener('hashchange', restorePatch);
  window.addEventListener('popstate', restorePatch);
  $('copy-phone-link').addEventListener('click', async () => {
    const link = $('phone-link').href;
    try {
      if (!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable');
      await navigator.clipboard.writeText(link);
      if ($('phone-link').href === link) $('handoff-status').textContent = say('Enlace copiado para este patrón. No se actualiza si cambias de patrón.', 'Link copied for this patch. It will not update if you change patches.');
    } catch {
      if ($('phone-link').href === link) $('handoff-status').textContent = say('Selecciona y copia el enlace visible, o ábrelo. La copia automática no está disponible.', 'Select and copy the visible link, or open it. Automatic copying is unavailable.');
    }
  });
  $('previous').addEventListener('click', () => move(-1));
  $('next').addEventListener('click', () => move(1));
  $('fullscreen').addEventListener('click', async () => {
    try { await document.documentElement.requestFullscreen?.(); } catch { /* Some TV browsers do not expose fullscreen. */ }
    move(0);
  });
  document.addEventListener('keydown', event => {
    if ((event.key === 'Enter' || event.key === ' ') && event.target instanceof Element &&
      event.target.closest('button,a,input,select,textarea,[contenteditable]')) return;
    const directions = { ArrowRight: 1, ArrowUp: 1, ArrowLeft: -1, ArrowDown: -1 };
    if (event.key in directions) { event.preventDefault(); move(directions[event.key]); }
    else if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); move(0); }
    else if (event.key === 'Escape' && handoffOpen) { setHandoffOpen(false); render(); $('handoff-toggle').focus(); }
    else if (event.key === 'Escape' && !document.fullscreenElement) location.href = patchLink('phone', english, selectedPatch.id);
  });
  document.addEventListener('visibilitychange', () => { if (document.hidden) { state.pause(); render(); } });
  window.addEventListener('resize', render);
  let interval = null;
  const startTimer = () => {
    if (interval === null) interval = setInterval(() => { state.tick(performance.now()); render(); }, 1000);
  };
  window.addEventListener('pagehide', () => {
    clearInterval(interval); interval = null; state.pause(); render();
  });
  window.addEventListener('pageshow', () => {
    if (patchFromFragment(location.hash).id !== selectedPatch.id) restorePatch();
    startTimer();
  });
  startTimer();
  render();
}

function setupPhone() {
  $('camera-jump').addEventListener('click', event => {
    event.preventDefault(); $('camera-section').scrollIntoView();
    $('camera-section').focus({ preventScroll: true });
  });
  let ready = false, capturing = false, abort = null, report = null;
  const preview = $('preview'), canvas = document.createElement('canvas');
  const previewBox = preview.parentElement, reticle = previewBox.querySelector('.reticle');
  const updateOverlay = () => {
    reticle.hidden = true;
    if (!ready || !preview.videoWidth || !preview.videoHeight || !previewBox.clientWidth || !previewBox.clientHeight) return;
    const region = previewRegion(preview.videoWidth, preview.videoHeight, previewBox.clientWidth, previewBox.clientHeight);
    Object.assign(reticle.style, Object.fromEntries(Object.entries(region).map(([key, value]) => [key, `${value}px`])));
    reticle.hidden = false;
  };
  preview.addEventListener('resize', updateOverlay);
  const resizeObserver = typeof ResizeObserver === 'function' ? new ResizeObserver(updateOverlay) : null;
  resizeObserver?.observe(previewBox);
  window.addEventListener('resize', updateOverlay);
  canvas.width = 96; canvas.height = 96;
  const context = canvas.getContext('2d', { willReadFrequently: true });
  const errors = {
    secure_origin_required: say('Abre esta página mediante HTTPS para usar la cámara. Una dirección HTTP de la red local no sirve.', 'Open this page over trusted HTTPS to use the camera. A local-network HTTP address will not work.'),
    camera_unavailable: say('Este navegador no ofrece acceso a la cámara. Prueba Safari o Chrome actualizados.', 'This browser does not offer camera access. Try an up-to-date Safari or Chrome.'),
    permission_denied: say('Acceso a la cámara denegado. Puedes permitirlo en los ajustes del sitio y volver a intentarlo.', 'Camera access was denied. You can allow it in site settings and try again.'),
    camera_missing: say('No se ha encontrado una cámara disponible.', 'No camera was found.'),
    camera_busy: say('La cámara está ocupada. Cierra otras aplicaciones que la estén usando.', 'The camera is busy. Close other applications using it.'),
    preview_failed: say('No se ha podido iniciar la vista de cámara. Detén la cámara y vuelve a intentarlo.', 'The camera preview could not start. Stop the camera and try again.')
  };
  const updateButtons = () => {
    $('start').disabled = ready || capturing;
    $('capture').disabled = !ready || capturing || !$('confirm').checked;
    $('stop').disabled = !ready && !capturing && $('start').disabled === false;
  };
  const status = (message, error = false) => { $('camera-status').textContent = message; $('camera-status').classList.toggle('error', error); };
  const modeRows = { exposureMode: 'exposure-state', whiteBalanceMode: 'white-balance-state', focusMode: 'focus-state' };
  const showCameraReadiness = observations => {
    const observation = inspectCapabilities(observations);
    const labels = { exposureMode: say('exposición', 'exposure'), whiteBalanceMode: say('balance de blancos', 'white balance'), focusMode: say('enfoque', 'focus') };
    const modes = { manual: say('Manual', 'Manual'), continuous: say('Continuo (automático)', 'Continuous (automatic)'),
      'single-shot': say('Automático puntual', 'Single-shot (automatic)'), none: say('Ninguno indicado', 'None reported') };
    $('controls-state').textContent = observation.manualControls.length
      ? observation.manualControls.map(name => labels[name]).join(', ')
      : say('No indicada / desconocida', 'Not reported / unknown');
    for (const [name, id] of Object.entries(modeRows)) {
      $(id).textContent = modes[observation.settings[name]] ?? say('Desconocido / no expuesto', 'Unknown / not exposed');
    }
    return observation;
  };
  const controller = createCameraController({ mediaDevices: navigator.mediaDevices, secureContext: isSecureContext,
    onChange(state) {
      ready = state.status === 'ready';
      updateOverlay();
      if (state.status !== 'ready') {
        abort?.abort(); $('confirm').checked = false;
        $('controls-state').textContent = state.status === 'requesting'
          ? say('Esperando permiso', 'Awaiting permission') : say('Cámara apagada', 'Camera off');
        for (const id of Object.values(modeRows)) $(id).textContent = $('controls-state').textContent;
      }
      $('preview-placeholder').hidden = ready;
      updateButtons();
      if (state.status === 'requesting') {
        $('start').disabled = true; $('stop').disabled = false;
        status(say('Esperando permiso para usar la cámara…', 'Waiting for camera permission…'));
      } else if (state.status === 'error') status(errors[state.error] ?? errors.preview_failed, true);
      else if (ready) {
        showCameraReadiness(controller.observations());
        status(say('Cámara activa. Confirma el ID del patrón antes de capturar.', 'Camera active. Confirm the patch ID before capturing.'));
      } else status(say('La cámara está apagada.', 'The camera is off.'));
    }
  });
  for (const patch of PATCHES) {
    const option = document.createElement('option'); option.value = patch.id; option.textContent = `${patch.id} · RGB ${patch.rgb.join('/')}`;
    $('patch').append(option);
  }
  $('patch').value = selectedPatch.id;
  $('confirm').checked = false;
  $('start').addEventListener('click', () => { void controller.start(preview); });
  const stop = () => { abort?.abort(); capturing = false; controller.stop(); $('confirm').checked = false; updateButtons(); };
  $('stop').addEventListener('click', stop);
  $('confirm').addEventListener('change', () => {
    if (!$('confirm').checked && capturing) {
      abort?.abort();
      status(say('Captura cancelada. Confirma de nuevo el ID del patrón.', 'Capture cancelled. Confirm the patch ID again.'));
    }
    updateButtons();
  });
  const changePatch = () => {
    abort?.abort(); $('confirm').checked = false; report = null;
    $('results').hidden = true; $('report-preview').hidden = true; $('download').disabled = true;
    const patch = PATCHES.find(p => p.id === $('patch').value) || PATCHES[4];
    $('patch').value = patch.id; updateLinks(patch); updateButtons();
    status(say('Patrón seleccionado. Comprueba el ID y RGB en la tele y confirma antes de capturar.', 'Patch selected. Check the ID and RGB on the TV and confirm before capturing.'));
  };
  $('patch').addEventListener('change', () => { changePatch(); rememberPatch('phone'); });
  const restorePatch = () => {
    if (location.hash === '#camera-section') return;
    $('patch').value = patchFromFragment(location.hash).id; changePatch();
  };
  window.addEventListener('hashchange', restorePatch);
  window.addEventListener('popstate', restorePatch);
  $('capture').addEventListener('click', async () => {
    if (!ready || capturing || !$('confirm').checked || !context) return;
    capturing = true; abort = new AbortController(); const signal = abort.signal;
    const patchId = $('patch').value;
    report = null; $('results').hidden = true; $('download').disabled = true; $('report-preview').hidden = true;
    updateButtons(); status(say('Capturando. Mantén el móvil quieto…', 'Capturing. Hold the phone still…'));
    try {
      const settingsBeforeCapture = showCameraReadiness(controller.observations()).settings;
      const quality = await captureFrames({ video: preview, signal, readFrame() {
        const region = sampleRegion(preview.videoWidth, preview.videoHeight);
        context.drawImage(preview, region.x, region.y, region.width, region.height, 0, 0, 96, 96);
        return pixelSample(context.getImageData(0, 0, 96, 96).data);
      } });
      if (signal.aborted || !ready) return;
      const observations = controller.observations();
      showCameraReadiness(observations);
      report = exportReport({ patchId, presentationConfirmed: true, capabilities: observations, settingsBeforeCapture, quality });
      $('frame-count').textContent = String(quality.sampleCount);
      $('duration').textContent = `${(quality.durationMs / 1000).toFixed(2)} s`;
      $('deviation').textContent = Math.max(...quality.stdDevRgb).toFixed(2);
      $('warnings').replaceChildren();
      const messages = {
        insufficient_frames: say('Hay pocos fotogramas distintos. Repite la prueba con buena iluminación y la cámara activa.', 'Too few distinct frames. Repeat with the camera active and suitable lighting.'),
        clipping_observed: say('Se han observado píxeles en los extremos del rango. Comprueba el patrón y la exposición.', 'Some pixels reached the code endpoints. Check the patch and exposure.'),
        temporal_variation: say('La señal ha variado durante la captura. Revisa movimiento, exposición y parpadeo.', 'The signal varied during capture. Check movement, exposure and flicker.'),
        camera_settings_changed: say('Los ajustes de cámara difieren entre las observaciones inicial y final. Esto puede afectar a la muestra.', 'Camera settings differed between the start and end observations. This may affect the sample.')
      };
      const notes = report.quality.warnings.length ? report.quality.warnings.map(code => messages[code]) :
        [say('No se han activado las alertas de variación o recorte en esta muestra. Esto no demuestra precisión del color.', 'No variation or clipping warning was triggered in this sample. This does not establish colour accuracy.')];
      notes.push(say('Los ajustes solo se leen al inicio y al final de la captura. Los valores iguales o desconocidos no demuestran un bloqueo de cámara.', 'Settings are read only at capture start and end. Matching or unknown settings do not establish a camera lock.'));
      notes.forEach(message => { const li = document.createElement('li'); li.textContent = message; $('warnings').append(li); });
      $('results').hidden = false;
      status(say('Captura terminada. Revisa las observaciones y el informe.', 'Capture finished. Review the observations and report.'));
    } catch {
      if (!signal.aborted) status(say('No se han podido obtener fotogramas válidos. Reinicia la cámara y repite la captura.', 'No usable frames were obtained. Restart the camera and capture again.'), true);
    } finally {
      if (abort?.signal === signal) { capturing = false; abort = null; updateButtons(); }
    }
  });
  $('review').addEventListener('click', () => {
    if (!report) return;
    $('report-preview').textContent = JSON.stringify(report, null, 2);
    $('report-preview').hidden = false; $('download').disabled = false;
  });
  $('download').addEventListener('click', () => {
    if (!report || $('report-preview').hidden) return;
    const url = URL.createObjectURL(new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' }));
    const link = document.createElement('a'); link.href = url; link.download = 'tv-tune-observation.json'; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  });
  document.addEventListener('visibilitychange', () => { if (document.hidden) stop(); });
  window.addEventListener('pagehide', () => { stop(); resizeObserver?.disconnect(); });
  window.addEventListener('pageshow', () => { restorePatch(); resizeObserver?.observe(previewBox); updateOverlay(); });
  if (!isSecureContext || !navigator.mediaDevices?.getUserMedia) {
    $('start').disabled = true;
    status(!isSecureContext ? errors.secure_origin_required : errors.camera_unavailable, true);
  }
}
