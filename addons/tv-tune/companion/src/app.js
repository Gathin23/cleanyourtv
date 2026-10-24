import { inspectCapabilities, exportReport } from './probe.js';
import { createCameraController, captureFrames, pixelSample, sampleRegion, previewRegion } from './capture.js';
import { PATCHES, presenterState, patchRectangle } from './patterns.js';

const $ = id => document.getElementById(id);
const params = new URLSearchParams(location.search), english = params.get('lang') === 'en';
const say = (es, en) => english ? en : es;
if (english) {
  document.documentElement.lang = 'en';
  document.querySelectorAll('[data-en]').forEach(element => { element.textContent = element.dataset.en; });
}
$('language').textContent = english ? 'ES' : 'EN';
$('language').addEventListener('click', () => {
  params.set('lang', english ? 'es' : 'en'); location.search = params.toString();
});
document.querySelectorAll('.tv-link').forEach(link => { if (english) link.href = '?view=tv&lang=en'; });

if (params.get('view') === 'tv') setupPresenter();
else setupPhone();

function setupPresenter() {
  document.body.dataset.view = 'tv'; $('phone-view').hidden = true; $('tv-view').hidden = false;
  const state = presenterState(performance.now());
  const render = () => {
    const patch = PATCHES[state.index], rect = patchRectangle(innerWidth, innerHeight);
    Object.assign($('tv-patch').style, { width: `${rect.size}px`, height: `${rect.size}px`,
      left: `${rect.left}px`, top: `${rect.top}px`, backgroundColor: state.paused ? '#252525' : `rgb(${patch.rgb.join(',')})` });
    $('tv-patch-id').textContent = state.paused ? say('En pausa', 'Paused') : patch.id;
    $('tv-code').textContent = state.paused ? say('Pulsa una tecla para volver.', 'Press a key to resume.') : `RGB ${patch.rgb.join(' / ')} · ${innerWidth} × ${innerHeight}`;
    $('tv-status').textContent = say('Confirmación manual en el móvil · Sin sincronización automática', 'Manually confirm the ID on the phone · No automatic synchronisation');
  };
  const move = delta => { state.move(delta, performance.now()); render(); };
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
    else if (event.key === 'Escape' && !document.fullscreenElement) location.href = english ? '?lang=en' : '?';
  });
  document.addEventListener('visibilitychange', () => { if (document.hidden) { state.pause(); render(); } });
  window.addEventListener('resize', render);
  const interval = setInterval(() => { state.tick(performance.now()); render(); }, 1000);
  window.addEventListener('pagehide', () => clearInterval(interval), { once: true });
  render();
}

function setupPhone() {
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
  const controller = createCameraController({ mediaDevices: navigator.mediaDevices, secureContext: isSecureContext,
    onChange(state) {
      ready = state.status === 'ready';
      updateOverlay();
      if (state.status !== 'ready') {
        abort?.abort(); $('confirm').checked = false;
        $('controls-state').textContent = state.status === 'requesting'
          ? say('Esperando permiso', 'Awaiting permission') : say('Cámara apagada', 'Camera off');
      }
      $('preview-placeholder').hidden = ready;
      updateButtons();
      if (state.status === 'requesting') {
        $('start').disabled = true; $('stop').disabled = false;
        status(say('Esperando permiso para usar la cámara…', 'Waiting for camera permission…'));
      } else if (state.status === 'error') status(errors[state.error] ?? errors.preview_failed, true);
      else if (ready) {
        const observation = inspectCapabilities(controller.observations());
        const labels = { exposureMode: say('exposición', 'exposure'), whiteBalanceMode: say('balance de blancos', 'white balance'), focusMode: say('enfoque', 'focus') };
        $('controls-state').textContent = observation.manualControls.length
          ? `${say('Manual disponible', 'Manual available')}: ${observation.manualControls.map(name => labels[name]).join(', ')}`
          : say('No disponibles o no expuestos', 'Unavailable or not exposed');
        status(say('Cámara activa. Confirma el ID del patrón antes de capturar.', 'Camera active. Confirm the patch ID before capturing.'));
      } else status(say('La cámara está apagada.', 'The camera is off.'));
    }
  });
  for (const patch of PATCHES) {
    const option = document.createElement('option'); option.value = patch.id; option.textContent = `${patch.id} · RGB ${patch.rgb.join('/')}`;
    $('patch').append(option);
  }
  $('patch').value = 'grey-128';
  $('start').addEventListener('click', () => { void controller.start(preview); });
  const stop = () => { abort?.abort(); capturing = false; controller.stop(); $('confirm').checked = false; updateButtons(); };
  $('stop').addEventListener('click', stop);
  $('confirm').addEventListener('change', updateButtons);
  $('patch').addEventListener('change', () => { abort?.abort(); $('confirm').checked = false; report = null; $('results').hidden = true; updateButtons(); });
  $('capture').addEventListener('click', async () => {
    if (!ready || capturing || !$('confirm').checked || !context) return;
    capturing = true; abort = new AbortController(); const signal = abort.signal;
    const patchId = $('patch').value;
    report = null; $('results').hidden = true; $('download').disabled = true; $('report-preview').hidden = true;
    updateButtons(); status(say('Capturando. Mantén el móvil quieto…', 'Capturing. Hold the phone still…'));
    try {
      const quality = await captureFrames({ video: preview, signal, readFrame() {
        const region = sampleRegion(preview.videoWidth, preview.videoHeight);
        context.drawImage(preview, region.x, region.y, region.width, region.height, 0, 0, 96, 96);
        return pixelSample(context.getImageData(0, 0, 96, 96).data);
      } });
      if (signal.aborted || !ready) return;
      report = exportReport({ patchId, presentationConfirmed: true, capabilities: controller.observations(), quality });
      $('frame-count').textContent = String(quality.sampleCount);
      $('duration').textContent = `${(quality.durationMs / 1000).toFixed(2)} s`;
      $('deviation').textContent = Math.max(...quality.stdDevRgb).toFixed(2);
      $('warnings').replaceChildren();
      const messages = {
        insufficient_frames: say('Hay pocos fotogramas distintos. Repite la prueba con buena iluminación y la cámara activa.', 'Too few distinct frames. Repeat with the camera active and suitable lighting.'),
        clipping_observed: say('Se han observado píxeles en los extremos del rango. Comprueba el patrón y la exposición.', 'Some pixels reached the code endpoints. Check the patch and exposure.'),
        temporal_variation: say('La señal ha variado durante la captura. Revisa movimiento, exposición y parpadeo.', 'The signal varied during capture. Check movement, exposure and flicker.')
      };
      const notes = quality.warnings.length ? quality.warnings.map(code => messages[code]) :
        [say('No se han activado las alertas de variación o recorte en esta muestra. Esto no demuestra precisión del color.', 'No variation or clipping warning was triggered in this sample. This does not establish colour accuracy.')];
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
  if (!isSecureContext || !navigator.mediaDevices?.getUserMedia) {
    $('start').disabled = true;
    status(!isSecureContext ? errors.secure_origin_required : errors.camera_unavailable, true);
  }
}
