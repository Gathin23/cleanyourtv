/* Jogos: a biblioteca do PC (Steam + Hydra) e o "Jogar na TV" pelo HDMI.
 * O programa Central de Jogos no PC manda files/games.json e files/game_status.json pela conexão ADB;
 * daqui os pedidos vão por TV.gameRequest (o PC lê do logcat). Ver C:\tvh\pcagent\agent.py. */
var G = {games: [], pc: '', status: null, focus: null, poll: null, waiting: null, statusAt: 0};
var HDMI_OPTS = [['com.tcl.tvinput/.TvPassThroughService/HW15', 'HDMI 1'], ['com.tcl.tvinput/.TvPassThroughService/HW16', 'HDMI 2'], ['com.tcl.tvinput/.TvPassThroughService/HW17', 'HDMI 3']];

function gq(id){ return document.getElementById(id); }
function gEsc(s){ return String(s == null ? '' : s).replace(/[&<>"]/g, function(c){ return {'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'}[c]; }); }
function gEl(tag, cls, html){ var e = document.createElement(tag); if (cls) e.className = cls; if (html != null) e.innerHTML = html; return e; }
function gImg(src, fallback){
  var i = gEl('img'); i.alt = ''; i.referrerPolicy = 'no-referrer';
  i.onerror = function(){ if (fallback && i.src !== fallback) { i.src = fallback; fallback = null; } else i.style.display = 'none'; };
  if (src) i.src = src; else if (fallback) { i.src = fallback; fallback = null; }
  return i;
}
// O PC foi encontrado no HDMI 1 (a TV registra sinal 1920x1080 nessa entrada); dá para trocar em 'PC na entrada'.
function hdmi(){ try { return localStorage.getItem('gameHdmi') || HDMI_OPTS[0][0]; } catch (e) { return HDMI_OPTS[0][0]; } }
function hdmiLabel(){ var h = hdmi(); var o = HDMI_OPTS.filter(function(x){ return x[0] === h; })[0]; return o ? o[1] : 'Escolher'; }

/* "há 2 dias", "ontem", "hoje" */
function ago(sec){
  if (!sec) return 'Ainda não jogado';
  var d = Math.floor((Date.now() / 1000 - sec) / 86400);
  if (d <= 0) return 'Jogado hoje';
  if (d === 1) return 'Jogado ontem';
  if (d < 30) return 'Jogado há ' + d + ' dias';
  var m = Math.round(d / 30);
  return m < 12 ? 'Jogado há ' + m + (m > 1 ? ' meses' : ' mês') : 'Jogado há mais de um ano';
}
function hours(min){
  if (!min) return '';
  if (min < 60) return min + ' min jogados';
  var h = Math.round(min / 60);
  return h.toLocaleString('pt-BR') + (h > 1 ? ' horas' : ' hora') + ' jogadas';
}
function srcName(g){ return g.src === 'steam' ? 'Steam' : 'Hydra'; }

function readPc(){
  var lib = null, st = null;
  try { lib = JSON.parse(TV.pcFile('games.json') || 'null'); } catch (e) {}
  try { st = JSON.parse(TV.pcFile('game_status.json') || 'null'); } catch (e) {}
  return {lib: lib, st: st};
}

/* Monta a lista só quando ela muda: refazer as capas toda vez deixava a aba lenta para abrir. */
function load(){
  var r = readPc(), sig = JSON.stringify(r.lib && r.lib.games || []);
  G.pc = (r.lib && r.lib.pc) || '';
  G.status = r.st;
  if (sig === G.sig) { drawStatus(); drawHero(G.focus); return; }
  G.sig = sig;
  G.games = (r.lib && r.lib.games) || [];
  render();
}
// Prepara a aba logo depois que a tela inicial abre, para entrar nela ser imediato.
setTimeout(function(){ try { if (!G.sig) load(); } catch (e) {} }, 2500);

window.gEnter = function(){
  load();
  var first = G.games.filter(function(g){ return g.last; })[0] || G.games[0];
  if (first) { G.focus = first; drawHero(first); }
  // Pede ao PC a lista mais nova (chega em alguns segundos e a página se atualiza sozinha).
  request({a: 'refresh'});
  startPoll(2000);
};

function request(o){ o.t = Date.now(); try { TV.gameRequest(JSON.stringify(o)); } catch (e) {} }

function startPoll(ms){
  clearInterval(G.poll);
  G.poll = setInterval(function(){
    if (page !== 'games') { clearInterval(G.poll); G.poll = null; return; }
    var r = readPc(), changed = false;
    var lib = (r.lib && r.lib.games) || [];
    var sig = JSON.stringify(lib);
    if (sig !== G.sig) { G.sig = sig; G.games = lib; G.pc = (r.lib && r.lib.pc) || G.pc; changed = true; }
    if (JSON.stringify(r.st) !== JSON.stringify(G.status)) { G.status = r.st; onStatus(); }
    if (changed) render(true);
  }, ms);
}

function onStatus(){
  var st = G.status || {};
  // Qualquer mudança depois do pedido é a resposta do PC (os relógios da TV e do PC não batem).
  if (G.waiting && JSON.stringify(st) !== G.waiting.prev) {
    if (st.msg) { gToast(st.msg); hideOpening(); G.waiting = null; }
    else if (st.running && st.running.phase !== 'loading') { finishOpening(); G.waiting = null; }
    else if (st.running) { G.waiting.loading = true; G.expect = st.running.expect || 45; }   // o PC respondeu: segue carregando
  }
  drawStatus();
  drawHero(G.focus);
}

function running(){ return G.status && G.status.running; }

function drawStatus(){
  var pill = gq('gPc'), txt = gq('gPcTxt');
  var run = running();
  pill.classList.toggle('live', !!run);
  if (run) txt.textContent = 'Jogando no PC: ' + run.title;
  else if (G.games.length) txt.textContent = (G.pc || 'PC') + ' · conectado';
  else txt.textContent = 'PC desconectado';
  gq('gHdmiVal').textContent = hdmiLabel();
}

function render(keep){
  var recent = G.games.filter(function(g){ return g.last; }).slice(0, 12);
  var all = G.games.slice().sort(function(a, b){ return a.title.localeCompare(b.title, 'pt-BR'); });
  gq('gEmpty').style.display = G.games.length ? 'none' : 'flex';
  gq('gRows').style.display = G.games.length ? 'flex' : 'none';
  gq('gAllCount').textContent = all.length + (all.length === 1 ? ' jogo' : ' jogos');
  fill('gRecent', recent, cover);
  fill('gAll', all, capsule);
  if (!keep || !G.focus) G.focus = recent[0] || all[0] || null;
  else G.focus = G.games.filter(function(g){ return g.id === G.focus.id; })[0] || G.focus;
  drawStatus();
  drawHero(G.focus);
}

function fill(id, list, make){
  var box = gq(id), was = cur && box.contains(cur) ? cur.dataset.id : null;
  box.innerHTML = ''; box.style.transform = '';
  list.forEach(function(g){ box.appendChild(make(g)); });
  if (was) { var again = box.querySelector('[data-id="' + was + '"]'); if (again) focus(again); }
}

/* Capa vertical (jogados recentemente) */
function cover(g){
  var c = gEl('div', 'f tile gcover');
  c.dataset.gact = 'play'; c.dataset.id = g.id;
  c.appendChild(gImg(g.cover, g.header));
  if (!g.cover) c.appendChild(gEl('div', 'cap', '<b class="clamp2">' + gEsc(g.title) + '</b>'));
  c.appendChild(gEl('span', 'gsrc ' + g.src, srcName(g)));
  return c;
}
/* Cápsula horizontal (biblioteca) */
function capsule(g){
  var c = gEl('div', 'f tile gcap');
  c.dataset.gact = 'play'; c.dataset.id = g.id;
  c.appendChild(gImg(g.header, g.hero));
  c.appendChild(gEl('div', 'cap', '<b class="clamp1">' + gEsc(g.title) + '</b><span>' + gEsc(ago(g.last)) + '</span>'));
  return c;
}

/* O destaque acompanha o jogo com foco, como na Steam. */
var heroTimer = null;
window.gFocus = function(e){
  if (page !== 'games' || !e || !e.dataset || !e.dataset.id) return;
  var g = G.games.filter(function(x){ return x.id === e.dataset.id; })[0];
  if (!g || g === G.focus) return;
  G.focus = g;
  clearTimeout(heroTimer);
  heroTimer = setTimeout(function(){ drawHero(g); }, 180);
};

function drawHero(g){
  var art = gq('gHeroArt'), logo = gq('gLogo'), title = gq('gTitle');
  if (!g) { title.style.display = ''; title.textContent = 'Seus jogos do PC'; logo.style.display = 'none'; art.style.display = 'none'; gq('gMeta').innerHTML = ''; gq('gDesc').textContent = ''; return; }
  var bg = g.hero || g.header || '';
  if (art.dataset.src !== bg) {
    art.dataset.src = bg; art.style.opacity = 0; art.style.display = '';
    art.onload = function(){ art.style.opacity = 1; };
    art.onerror = function(){ if (g.header && art.src !== g.header) art.src = g.header; else art.style.display = 'none'; };
    art.src = bg;
  }
  // Logo do jogo; sem logo, o nome.
  title.textContent = g.title; title.style.display = 'none';
  logo.style.display = '';
  logo.onerror = function(){ logo.style.display = 'none'; title.style.display = ''; };
  if (g.logo) logo.src = g.logo; else logo.onerror();
  var run = running(), isRun = run && run.id === g.id;
  var bits = ['<span class="gsrc ' + g.src + '">' + srcName(g) + '</span>', '<span>' + gEsc(isRun ? 'Jogando agora no PC' : ago(g.last)) + '</span>'];
  if (g.mins) bits.push('<span>' + gEsc(hours(g.mins)) + '</span>');
  gq('gMeta').innerHTML = bits.join('<i></i>');
  gq('gDesc').textContent = g.desc || '';
  gq('gPlayTxt').textContent = isRun ? 'Voltar ao jogo' : (run ? 'Jogar este' : 'Jogar na TV');
  gq('gStop').style.display = run ? 'flex' : 'none';
}

window.gAct = function(e){
  var a = e.dataset.gact;
  if (a === 'play') {
    var g = e.dataset.id ? G.games.filter(function(x){ return x.id === e.dataset.id; })[0] : G.focus;
    if (g) play(g);
    return;
  }
  if (a === 'hdmi') return pickHdmi(e);
  if (a === 'stop') { request({a: 'restore'}); gToast('Devolvendo a tela ao PC…'); return; }
  if (a === 'refresh') { request({a: 'refresh'}); gToast('Atualizando a lista do PC…'); return; }
};

function pickHdmi(btn, then){
  openMenu(btn, 'gDrop', HDMI_OPTS, hdmi(), function(v){
    try { localStorage.setItem('gameHdmi', v); } catch (e) {}
    drawStatus();
    if (then) then();
  });
}

function play(g){
  var run = running();
  if (run && run.id === g.id) return switchInput();   // já está rodando: só volta para a entrada do PC
  if (run) { gToast('Feche ' + run.title + ' antes de abrir outro jogo.'); return; }
  if (!hdmi()) {                        // primeira vez: em qual HDMI o PC está?
    gToast('Em qual entrada HDMI o PC está ligado?');
    return pickHdmi(gq('gHdmi'), function(){ play(g); });
  }
  G.waiting = {id: g.id, prev: JSON.stringify(G.status || {})};
  showOpening(g);
  request({a: 'launch', id: g.id, hdmi: hdmi()});
  // Sem resposta do PC em 20 s: o programa provavelmente não está rodando.
  setTimeout(function(){
    if (G.waiting && G.waiting.id === g.id && !G.waiting.loading) { hideOpening(); G.waiting = null; gToast('O PC não respondeu. O programa Central de Jogos está aberto no PC?'); }
  }, 20000);
}
function switchInput(){
  try { TV.openUri('content://android.media.tv/passthrough/' + encodeURIComponent(hdmi()), ''); } catch (e) {}
}

/* Tela cheia de carregamento: arte do jogo ao fundo, logo e "Carregando o jogo…", até a TV ir para o HDMI. */
function showOpening(g){
  var bg = gq('gOpenBg'), logo = gq('gOpenLogo'), title = gq('gOpenTitle');
  bg.style.opacity = 0;
  bg.onload = function(){ bg.style.opacity = 1; };
  bg.onerror = function(){ if (g.header && bg.src !== g.header) bg.src = g.header; };
  bg.src = g.hero || g.header || '';
  title.textContent = g.title; title.style.display = 'none';
  logo.style.display = '';
  logo.onerror = function(){ logo.style.display = 'none'; title.style.display = ''; };
  if (g.logo) logo.src = g.logo; else logo.onerror();
  gq('gOpenState').textContent = 'Carregando ' + g.title + '…';
  gq('gOpening').style.display = 'block';
  // Barra: anda no ritmo do tempo que o jogo levou da última vez e para em 95% até o jogo aparecer.
  G.expect = 45; var t0 = Date.now();
  clearInterval(G.barTimer);
  setBar(0);
  G.barTimer = setInterval(function(){
    var f = (Date.now() - t0) / 1000 / G.expect;
    setBar(Math.min(95, f < 1 ? f * 90 : 90 + 5 * (1 - Math.exp(-(f - 1) * 2))));
  }, 250);
}
function setBar(p){ gq('gOpenBar').style.width = p + '%'; gq('gOpenPct').textContent = Math.floor(p) + '%'; }
/* O jogo apareceu: completa a barra; a TV troca para o HDMI logo em seguida. */
function finishOpening(){
  clearInterval(G.barTimer);
  setBar(100);
  gq('gOpenState').textContent = 'Pronto!';
  setTimeout(hideOpening, 2500);
}
function hideOpening(){ clearInterval(G.barTimer); gq('gOpening').style.display = 'none'; }
window.gModalBack = function(){
  if (gq('gOpening').style.display === 'block') { hideOpening(); G.waiting = null; return true; }
  return false;
};

function gToast(t){ if (window.toast) toast(t); }
