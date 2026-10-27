/* Filmes e Séries: catálogo (Cinemeta), detalhes, e fontes dos add-ons de stream do Stremio.
   Depende de el, esc, focus, showPage, firstOf, cur, page e TV definidos em home.html. */

var CINEMETA = 'https://v3-cinemeta.strem.io';
var STREMIO = 'com.stremio.one', NUVIO = 'com.nuvio.tv';
var ADDON_COLORS = ['#533afd', '#0F7A45', '#F47521', '#ea2261', '#2b8cff', '#9b6829', '#f96bee', '#14b8a6'];
var TINTS2 = ['#3b2a4f', '#22324f', '#4b3426', '#1c3f30', '#432230', '#2c2850', '#2f3b4f', '#4a3a24'];

var M = {
  type: 'all', loaded: false, cache: {}, hero: null,
  detail: null, season: null, video: null,
  streams: [], addonFilter: 'all', qualFilter: 'all', langFilter: 'all', req: 0, from: 'movies'
};

/* ---------- rede (feita no lado nativo; responde em onFetch) ---------- */
var pending = {}, seq = 0;
function getJson(url){
  return new Promise(function(resolve){
    var id = 'r' + (++seq);
    pending[id] = resolve;
    TV.fetch(id, url);
    setTimeout(function(){ if (pending[id]) { delete pending[id]; resolve(null); } }, 45000);
  });
}
window.onFetch = function(id, ok, text){
  var cb = pending[id]; if (!cb) return;
  delete pending[id];
  if (!ok) return cb(null);
  try { cb(JSON.parse(text)); } catch (e) { cb(null); }
};
function cached(url){
  if (M.cache[url]) return Promise.resolve(M.cache[url]);
  return getJson(url).then(function(j){ if (j) M.cache[url] = j; return j; });
}

/* ---------- imagens ---------- */
function bigBg(m){ return m && m.background ? m.background.replace('/background/medium/', '/background/large/') : ''; }
function posterOf(m){ return m && m.poster ? m.poster.replace('/poster/small/', '/poster/medium/') : ''; }
function img(src, cls){ var i = el('img', cls); i.alt = ''; i.referrerPolicy = 'no-referrer'; i.onerror = function(){ i.style.display = 'none'; }; if (src) i.src = src; return i; }

/* ---------- catálogo ---------- */
function catalogUrl(type){
  if (type === 'anime') return CINEMETA + '/catalog/series/top/genre=Animation.json';
  return CINEMETA + '/catalog/' + (type === 'series' ? 'series' : 'movie') + '/top.json';
}
window.mEnter = function(){
  loadWatched();
  if (!M.loaded) { M.loaded = true; loadCatalog(); }
  loadContinue();
};
/* Assistido até o fim (marca de check). Lido de novo a cada abertura de página. */
var WATCHED = {};
var PROGRESS = {};
function loadWatched(){
  WATCHED = {}; try { JSON.parse(TV.watched()).forEach(function(v){ WATCHED[v] = 1; }); } catch (e) {}
  PROGRESS = {}; try { PROGRESS = JSON.parse(TV.progress()) || {}; } catch (e) {}
}
/* Barrinha de quanto já foi visto (episódio ou filme começado e não terminado). */
function progressBar(id){
  var p = PROGRESS[id]; if (!p || !p[1] || isWatched(id)) return null;
  var b = el('span', 'bar'), i = el('i'); i.style.width = Math.max(3, Math.min(100, 100 * p[0] / p[1])) + '%'; b.appendChild(i); return b;
}
/* Segurar OK num episódio ou filme: marca/desmarca como assistido. */
window.mToggleWatched = function(e){
  var id = e.dataset.mact === 'episode' ? e.dataset.vid : (e.dataset.mact === 'open' && e.dataset.type === 'movie' ? e.dataset.id : null);
  if (!id) return false;
  if (isWatched(id)) { TV.unmarkWatched(id); delete WATCHED[id]; toast('Desmarcado como assistido'); }
  else { TV.markWatched(id); WATCHED[id] = 1; toast('Marcado como assistido'); }
  var th = e.querySelector('.th') || e, old = th.querySelector('.seen');
  if (old) old.remove();
  if (isWatched(id)) th.appendChild(checkMark());
  e.classList.toggle('watched', isWatched(id) && e.dataset.mact === 'episode');
  var bar = th.querySelector('.bar'); if (bar && isWatched(id)) bar.remove();
  return true;
};
/* Item do Continuar que já terminou: se for série, vira "Próximo episódio". */
window.mNextOf = function(r){
  try {
    var p = JSON.parse(r.play);
    if (p.type !== 'series' || !p.vid || !(r.dur > 0 && r.pos >= r.dur)) return null;
    var se = p.vid.split(':');
    return {type: p.type, id: p.id, vid: p.vid, label: 'T' + se[1] + ':E' + (+se[2] + 1)};
  } catch (e) { return null; }
};
/* Um card por título no Continuar (o mais recente), como na Netflix. skipId: título já mostrado no destaque. */
window.mOnePerTitle = function(list, skipId){
  var seen = {};
  if (skipId) seen[skipId] = 1;
  return list.filter(function(r){
    var id = null;
    try { id = r.play ? JSON.parse(r.play).id : null; } catch (e) {}
    if (!id) return true;
    if (seen[id]) return false;
    seen[id] = 1; return true;
  });
};
window.mTitleId = function(r){ try { return r && r.play ? JSON.parse(r.play).id || null : null; } catch (e) { return null; } };
window.mPlayNext = function(n){ window.returnTo = {type: n.type, id: n.id, vid: n.vid}; playNext(n.type, n.id, n.vid); };
/* Filme terminado sai do Continuar. */
window.mFinishedMovie = function(r){ try { var p = JSON.parse(r.play); return p.type === 'movie' && r.dur > 0 && r.pos >= r.dur; } catch (e) { return false; } };
function isWatched(id){ return !!WATCHED[id]; }
function checkMark(){ var s = el('span', 'seen'); s.innerHTML = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>'; return s; }
function posterCard(m, i){
  var c = el('div', 'f tile poster');
  c.dataset.mact = 'open'; c.dataset.type = m.type === 'series' ? 'series' : 'movie'; c.dataset.id = m.id;
  c.style.backgroundColor = TINTS2[i % TINTS2.length];
  c.appendChild(img(posterOf(m)));
  if (m.imdbRating) c.appendChild(el('span', 'score', '★ ' + esc(m.imdbRating)));
  if (m.type !== 'series' && isWatched(m.id)) c.appendChild(checkMark());
  var pb = m.type !== 'series' && progressBar(m.id); if (pb) c.appendChild(pb);
  c.appendChild(el('div', 'cap', esc(m.name)));
  return c;
}

/* Abas Filmes, Séries e Animes: grade de pôsteres que carrega mais ao descer,
   com ordenação (catálogos do Cinemeta) e gênero. */
var SORTS = [['top', 'Mais assistidos'], ['new', 'Lançamentos'], ['rating', 'Mais bem avaliados'], ['mine', 'Assistidos por você'],
  ['svc:nfx', 'Top Netflix'], ['svc:amp', 'Top Prime Video'], ['svc:dnp', 'Top Disney+'], ['svc:hbm', 'Top HBO Max'], ['svc:atp', 'Top Apple TV+'], ['svc:cru', 'Top Crunchyroll']];
var SVC_ADDON = 'https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club';
var GENRES = [['all', 'Todos'], ['Action', 'Ação'], ['Adventure', 'Aventura'], ['Animation', 'Animação'], ['Biography', 'Biografia'],
  ['Comedy', 'Comédia'], ['Crime', 'Crime'], ['Documentary', 'Documentário'], ['Drama', 'Drama'], ['Family', 'Família'],
  ['Fantasy', 'Fantasia'], ['History', 'História'], ['Horror', 'Terror'], ['Mystery', 'Mistério'], ['Romance', 'Romance'],
  ['Sci-Fi', 'Ficção científica'], ['Sport', 'Esporte'], ['Thriller', 'Suspense'], ['War', 'Guerra'], ['Western', 'Faroeste']];
M.sort = 'top'; M.genre = 'all';
function label(list, v){ var o = list.filter(function(x){ return x[0] === v; })[0]; return o ? o[1] : ''; }
function gridUrl(g){
  var ctype = g.type === 'anime' ? 'series' : g.type, parts = [];
  if (g.sort.indexOf('svc:') === 0) {
    // Catálogo "mais assistidos" de cada serviço (add-on Streaming Catalogs); Crunchyroll só tem séries.
    var code = g.sort.slice(4);
    if (code === 'cru' && ctype === 'movie') return null;
    return SVC_ADDON + '/catalog/' + ctype + '/' + code + (g.skip ? '/skip=' + g.skip : '') + '.json';
  }
  if (g.sort === 'new') parts.push('genre=' + g.year);
  else if (g.type === 'anime') parts.push('genre=Animation');
  else if (g.genre !== 'all') parts.push('genre=' + encodeURIComponent(g.genre));
  if (g.skip) parts.push('skip=' + g.skip);
  var id = g.sort === 'rating' ? 'imdbRating' : (g.sort === 'new' ? 'year' : 'top');
  return CINEMETA + '/catalog/' + ctype + '/' + id + (parts.length ? '/' + parts.join('&') : '') + '.json';
}
function updateGridHeader(){
  var t = M.type;
  document.getElementById('mGridTitle').textContent = {movie: 'Filmes', series: 'Séries', anime: 'Animes'}[t] + ' · ' + label(SORTS, M.sort);
  document.getElementById('gSortVal').textContent = label(SORTS, M.sort);
  document.getElementById('gGenreVal').textContent = label(GENRES, M.genre);
  document.getElementById('gGenreSel').style.display = (t === 'anime' || M.sort === 'mine') ? 'none' : '';
}
function loadGrid(reset){
  var t = M.type, grid = document.getElementById('mGrid');
  if (reset) {
    M.grid = {type: t, sort: M.sort, genre: t === 'anime' ? 'Animation' : M.genre, skip: 0, year: new Date().getFullYear(),
              loading: false, done: false, seen: {}, n: 0, empty: 0};
    grid.innerHTML = '<div class="msg">Carregando…</div>'; grid.style.transform = '';
    document.getElementById('mGridCount').textContent = '';
    updateGridHeader();
    if (M.sort === 'mine') return loadMine();
  }
  var g = M.grid;
  if (g.loading || g.done) return;
  g.loading = true;
  var gu = gridUrl(g);
  if (!gu) { g.loading = false; g.done = true; grid.innerHTML = '<div class="msg">O Crunchyroll só tem séries e animes.</div>'; return; }
  cached(gu).then(function(j){
    if (M.grid !== g) return;
    g.loading = false;
    var metas = (j && j.metas) || [];
    if (g.skip === 0 && !g.n) grid.innerHTML = '';
    if (!metas.length) {
      // Lançamentos: acabou o ano, segue para o anterior (até 3 anos atrás).
      if (g.sort === 'new' && g.year > 1950) { g.year--; g.skip = 0; return loadGrid(false); }
      g.done = true;
      if (!g.n) grid.innerHTML = '<div class="msg">Nada encontrado com esse filtro.</div>';
      return;
    }
    g.skip += metas.length;
    metas.forEach(function(m){
      if (g.seen[m.id]) return;
      // Em Lançamentos o gênero é filtrado aqui (o catálogo por ano não filtra gênero).
      var svc = g.sort.indexOf('svc:') === 0, gs = m.genres || m.genre || [];
      if ((g.sort === 'new' || svc) && g.genre !== 'all' && g.type !== 'anime' && gs.indexOf(g.genre) < 0) return;
      if (svc && g.type === 'anime' && gs.length && gs.indexOf('Animation') < 0 && gs.indexOf('Anime') < 0) return;
      g.seen[m.id] = 1;
      grid.appendChild(posterCard(m, g.n++));
    });
    document.getElementById('mGridCount').textContent = g.n + ' títulos';
    // Filtro que descarta muito: busca mais até encher pelo menos 3 linhas.
    if (g.n < 21 && ++g.empty < 8) loadGrid(false);
    else if (!g.n) grid.innerHTML = '<div class="msg">Nada encontrado com esse filtro.</div>';
  });
}
/* Assistidos por você: o histórico (Stremio, Nuvio, Player da TV) achado no catálogo, só do tipo da aba. */
function loadMine(){
  var g = M.grid, grid = document.getElementById('mGrid'), list = [];
  g.done = true;
  try { list = JSON.parse(TV.recents()); } catch (e) {}
  list = list.filter(function(r){ return r.pkg === STREMIO || r.pkg === NUVIO || r.play; });
  var want = g.type === 'anime' ? 'series' : g.type, found = [], left = list.length;
  if (!left) { grid.innerHTML = '<div class="msg">Você ainda não assistiu nada desse tipo.</div>'; return; }
  list.forEach(function(r, i){
    findByTitle(r.title).then(function(hit){
      return hit && hit.type === want ? cached(CINEMETA + '/meta/' + hit.type + '/' + hit.id + '.json') : null;
    }).then(function(j){
      if (M.grid !== g) return;
      var m = j && j.meta;
      if (m && (g.type !== 'anime' || (m.genres || m.genre || []).indexOf('Animation') >= 0)) found.push({i: i, m: m});
      if (--left === 0) {
        grid.innerHTML = '';
        var seen = {};
        found.sort(function(a, b){ return a.i - b.i; }).forEach(function(f){
          if (seen[f.m.id]) return; seen[f.m.id] = 1;
          grid.appendChild(posterCard(f.m, g.n++));
        });
        document.getElementById('mGridCount').textContent = g.n + ' títulos';
        if (!g.n) grid.innerHTML = '<div class="msg">Você ainda não assistiu nada desse tipo.</div>';
      }
    });
  });
}
var baseFocus = focus;
focus = function(e){
  baseFocus(e);
  if (e && e.parentNode && e.parentNode.id === 'mGrid' && M.grid) {
    var kids = e.parentNode.children, i = Array.prototype.indexOf.call(kids, e);
    if (i > kids.length - 22) loadGrid(false);
  }
};

function loadCatalog(){
  var t = M.type, rowType = 'movie';
  document.getElementById('mAll').style.display = t === 'all' ? 'flex' : 'none';
  document.getElementById('mGridWrap').style.display = t === 'all' ? 'none' : 'flex';
  if (t !== 'all') return loadGrid(true);
  var titles = {all: 'Filmes populares', movie: 'Filmes populares', series: 'Séries populares', anime: 'Animes populares'};
  document.getElementById('mRowTitle').textContent = titles[t];
  var pop = document.getElementById('mPop');
  pop.innerHTML = '<div class="msg">Carregando…</div>'; pop.style.transform = '';
  var heroUrl = catalogUrl('movie');
  cached(catalogUrl(rowType)).then(function(j){
    if (t !== M.type) return;
    var metas = (j && j.metas) || [];
    pop.innerHTML = '';
    if (!metas.length) { pop.innerHTML = '<div class="msg">Não foi possível carregar o catálogo. Confira a internet da TV.</div>'; return; }
    metas.slice(0, 30).forEach(function(m, i){ pop.appendChild(posterCard(m, i)); });
    if (heroUrl === catalogUrl(rowType)) setHero(metas);
    else cached(heroUrl).then(function(h){ if (t === M.type) setHero((h && h.metas) || []); });
  });
}
function setHero(metas){
  var m = metas.filter(function(x){ return x.background; })[0] || metas[0];
  if (!m) return;
  M.hero = {type: m.type === 'series' ? 'series' : 'movie', id: m.id, meta: m};
  var art = document.getElementById('mHeroArt');
  art.onerror = function(){ art.onerror = null; if (m.background) art.src = m.background; };
  art.src = bigBg(m);
  document.getElementById('mHeroTitle').textContent = m.name;
  document.getElementById('mHeroDesc').textContent = m.description || '';
  var bits = [m.releaseInfo || m.year, (m.genres || m.genre || []).slice(0, 2).join(', '), m.runtime].filter(Boolean);
  document.getElementById('mHeroMeta').textContent = bits.join(' · ');
  var im = document.getElementById('mHeroImdb');
  if (m.imdbRating) { im.textContent = 'IMDb ' + m.imdbRating; im.style.display = 'flex'; } else im.style.display = 'none';
}

/* Continue assistindo: itens do histórico que vieram do Stremio ou do Nuvio */
function loadContinue(){
  var list = [];
  try { list = JSON.parse(TV.recents()); } catch (e) {}
  list = mOnePerTitle(list.filter(function(r){ return (r.pkg === STREMIO || r.pkg === NUVIO || r.play) && !(r.play && mFinishedMovie(r)); })).slice(0, 8);
  var box = document.getElementById('mCont'), empty = document.getElementById('mContEmpty');
  var was = cur && box.contains(cur) ? Array.prototype.indexOf.call(box.children, cur) : -1;
  box.innerHTML = ''; box.style.transform = '';
  document.getElementById('mContVp').style.display = list.length ? '' : 'none';
  empty.style.display = list.length ? 'none' : 'flex';
  list.forEach(function(r){
    var c = el('div', 'f tile cw');
    c.dataset.mact = 'resume'; c.dataset.title = r.title;
    if (r.play) c.dataset.play = r.play;
    var nx = r.play && mNextOf(r);
    if (nx) { c.dataset.next = JSON.stringify(nx); c.appendChild(img(r.backdrop || r.art || '')); c.appendChild(el('span', 'cap', '<b class="clamp1">' + esc(r.title) + '</b><span>Próximo episódio · ' + nx.label + '</span>')); box.appendChild(c); return; }
    c.appendChild(img(r.backdrop || r.art || ''));
    var app = r.play ? 'Player da TV' : (r.pkg === NUVIO ? 'Nuvio' : 'Stremio'), left = r.dur > 0 && r.pos > 0 ? Math.round((r.dur - r.pos) / 60000) : 0;
    c.appendChild(el('span', 'cap', '<b class="clamp1">' + esc(r.title) + '</b><span>' + app + (left > 0 ? ' · restam ' + left + ' min' : '') + '</span>'));
    if (r.dur > 0 && r.pos > 0) { var b = el('span', 'bar'), i = el('i'); i.style.width = Math.min(100, 100 * r.pos / r.dur) + '%'; b.appendChild(i); c.appendChild(b); }
    box.appendChild(c);
  });
  if (was >= 0 && box.children.length) focus(box.children[Math.min(was, box.children.length - 1)]);
}

/* Acha o título no Cinemeta pelo nome (para itens do histórico) */
function findByTitle(title){
  var q = String(title).replace(/\s*[-:|]\s*(S\d+\s*E\d+|T\d+\s*E\d+|Ep(is[oó]dio|isode)?\s*\d+).*$/i, '').trim();
  var enc = encodeURIComponent(q);
  return cached(CINEMETA + '/catalog/movie/top/search=' + enc + '.json').then(function(a){
    var m = a && a.metas && a.metas[0];
    if (m && norm(m.name).indexOf(norm(q).slice(0, 8)) === 0) return {type: 'movie', id: m.id};
    return cached(CINEMETA + '/catalog/series/top/search=' + enc + '.json').then(function(b){
      var s = b && b.metas && b.metas[0];
      return s ? {type: 'series', id: s.id} : (m ? {type: 'movie', id: m.id} : null);
    });
  });
}
function norm(x){ return String(x || '').toLowerCase().replace(/[^a-z0-9]/g, ''); }

/* ---------- detalhes ---------- */
function openDetail(type, id, from){
  loadWatched();
  M.from = from || page;
  M.detail = null; M.video = null;
  document.getElementById('dTitle').textContent = 'Carregando…';
  ['dDesc', 'dMeta', 'dKind'].forEach(function(x){ document.getElementById(x).textContent = ''; });
  document.getElementById('dGenres').innerHTML = ''; document.getElementById('dCast').innerHTML = '';
  document.getElementById('dEps').innerHTML = ''; document.getElementById('dSeasons').innerHTML = '';
  document.getElementById('dArt').removeAttribute('src');
  document.getElementById('dLogo').style.display = 'none';
  document.getElementById('dEpisodes').style.display = 'none';
  showPage('detail');
  cached(CINEMETA + '/meta/' + type + '/' + id + '.json').then(function(j){
    var m = j && j.meta;
    if (!m) { document.getElementById('dTitle').textContent = 'Não foi possível carregar'; return; }
    M.detail = {type: type, id: id, meta: m};
    renderDetail();
  });
}
function renderDetail(){
  var m = M.detail.meta, isSeries = M.detail.type === 'series';
  var art = document.getElementById('dArt');
  art.onerror = function(){ art.onerror = null; if (m.background) art.src = m.background; };
  art.src = bigBg(m);
  document.getElementById('dKind').textContent = isSeries ? 'SÉRIE' : 'FILME';
  var im = document.getElementById('dImdb');
  if (m.imdbRating) { im.textContent = 'IMDb ' + m.imdbRating; im.style.display = 'flex'; } else im.style.display = 'none';
  var seasons = seasonsOf(m);
  var bits = [m.releaseInfo || m.year, isSeries && seasons.length ? seasons.length + (seasons.length > 1 ? ' temporadas' : ' temporada') : m.runtime].filter(Boolean);
  if (!isSeries && isWatched(m.id || M.detail.id)) bits.push('✓ Assistido');
  document.getElementById('dMeta').textContent = bits.join(' · ');
  var title = document.getElementById('dTitle'), logo = document.getElementById('dLogo');
  title.textContent = m.name;
  if (m.logo) { logo.onload = function(){ logo.style.display = ''; title.style.display = 'none'; }; logo.onerror = function(){ logo.style.display = 'none'; title.style.display = ''; }; logo.src = m.logo; }
  else { logo.style.display = 'none'; title.style.display = ''; }
  var g = document.getElementById('dGenres'); g.innerHTML = '';
  (m.genres || m.genre || []).slice(0, 4).forEach(function(x){ g.appendChild(el('span', 'gpill', esc(x))); });
  document.getElementById('dDesc').textContent = m.description || '';
  var hasTrailer = (m.trailerStreams && m.trailerStreams.length) || (m.trailers && m.trailers.length);
  document.getElementById('dTrailer').style.display = hasTrailer ? 'flex' : 'none';
  renderCast(m);
  if (isSeries && seasons.length) {
    document.getElementById('dEpisodes').style.display = 'flex';
    renderSeasons(seasons);
    pickSeason(seasons[0], true);
  } else {
    document.getElementById('dEpisodes').style.display = 'none';
    document.getElementById('dPlayLabel').textContent = 'Assistir';
  }
}
function seasonsOf(m){
  var set = {};
  (m.videos || []).forEach(function(v){ if (v.season > 0) set[v.season] = 1; });
  return Object.keys(set).map(Number).sort(function(a, b){ return a - b; });
}
function renderSeasons(seasons){
  var box = document.getElementById('dSeasons'); box.innerHTML = ''; box.style.transform = '';
  seasons.forEach(function(n){
    var b = el('div', 'f season', 'Temporada ' + n);
    b.dataset.mact = 'season'; b.dataset.season = n;
    box.appendChild(b);
  });
}
function pickSeason(n, first){
  loadWatched();
  M.season = n;
  Array.prototype.forEach.call(document.querySelectorAll('#dSeasons .season'), function(b){ b.classList.toggle('sel', +b.dataset.season === n); });
  var eps = (M.detail.meta.videos || []).filter(function(v){ return v.season === n; }).sort(function(a, b){ return (a.episode || a.number) - (b.episode || b.number); });
  var box = document.getElementById('dEps'); box.innerHTML = ''; box.style.transform = '';
  eps.forEach(function(v){
    var e = el('div', 'f tile ep');
    e.dataset.mact = 'episode'; e.dataset.vid = v.id;
    var th = el('span', 'th');
    th.appendChild(epImg(v));
    th.appendChild(el('span', 'code', 'E' + (v.episode || v.number)));
    if (isWatched(v.id)) { th.appendChild(checkMark()); e.classList.add('watched'); }
    var pb = progressBar(v.id); if (pb) th.appendChild(pb);
    e.appendChild(th);
    e.appendChild(el('b', '', esc(v.name || v.title || ('Episódio ' + (v.episode || v.number)))));
    box.appendChild(e);
  });
  if (first && eps[0]) { M.video = eps[0]; document.getElementById('dPlayLabel').textContent = 'Assistir T' + n + ':E' + (eps[0].episode || eps[0].number); }
}
// Miniatura do episódio: se a do Cinemeta falhar (404 em muitas temporadas novas),
// tenta TVmaze e, para anime, Kitsu; por último usa o fundo do título.
function epImg(v){
  var i = img(), d = M.detail, tried = false;
  i.onerror = function(){
    if (tried) { i.style.display = 'none'; return; }
    tried = true;
    altThumbs(d).then(function(map){
      var u = map[v.season + ':' + (v.episode || v.number)] || bigBg(d.meta);
      if (u) i.src = u; else i.style.display = 'none';
    });
  };
  if (v.thumbnail) i.src = v.thumbnail; else i.onerror();
  return i;
}
function altThumbs(d){
  if (d._thumbs) return d._thumbs;
  var map = {}, id = d.id;
  var maze = cached('https://api.tvmaze.com/lookup/shows?imdb=' + id).then(function(s){
    return s && s.id ? cached('https://api.tvmaze.com/shows/' + s.id + '/episodes') : null;
  }).then(function(eps){
    (eps || []).forEach(function(e){ if (e.image) map[e.season + ':' + e.number] = e.image.original || e.image.medium; });
  }).catch(function(){});
  var genres = d.meta.genres || d.meta.genre || [];
  var kitsu = genres.indexOf('Animation') < 0 ? Promise.resolve() : cached('https://arm.haglund.dev/api/v2/imdb?id=' + id + '&include=kitsu').then(function(list){
    var ks = (list || []).map(function(x){ return x.kitsu; }).filter(Boolean);
    return Promise.all(ks.slice(0, 6).map(function(k, si){
      return Promise.all([0, 20, 40].map(function(off){
        return cached('https://kitsu.io/api/edge/episodes?filter%5BmediaId%5D=' + k + '&page%5Blimit%5D=20&page%5Boffset%5D=' + off + '&sort=number');
      })).then(function(pages){
        pages.forEach(function(p){ ((p && p.data) || []).forEach(function(e){
          var a = e.attributes, t = a.thumbnail && a.thumbnail.original, key = (si + 1) + ':' + a.number;
          if (t && !map[key]) map[key] = t;
        }); });
      });
    }));
  }).catch(function(){});
  d._thumbs = Promise.all([maze, kitsu]).then(function(){ return map; });
  return d._thumbs;
}
function renderCast(m){
  var people = (m.app_extras && m.app_extras.cast) || (m.cast || []).map(function(n){ return {name: n}; });
  drawCast(people);
  enrichCast(m, M.detail.type, M.detail.id);
}
/* Fotos do elenco: TVmaze para séries (elenco com personagem e foto), Wikipédia para o resto. */
function enrichCast(m, type, id){
  var want = M.detail;
  var byWiki = function(people){
    people.slice(0, 9).forEach(function(p, i){
      if (p.photo) return;
      cached('https://pt.wikipedia.org/api/rest_v1/page/summary/' + encodeURIComponent(p.name.replace(/ /g, '_'))).then(function(j){
        var src = j && j.thumbnail && j.thumbnail.source;
        var ok = src && /ator|atriz|actor|actress|dublador|seiyu|voice|cantor|singer|comedian|humorista|apresentador/i.test(j.description || j.extract || '');
        if (ok) return src;
        return cached('https://en.wikipedia.org/api/rest_v1/page/summary/' + encodeURIComponent(p.name.replace(/ /g, '_'))).then(function(k){
          var s2 = k && k.thumbnail && k.thumbnail.source;
          return s2 && /actor|actress|voice|singer|comedian|presenter|seiyu/i.test(k.description || k.extract || '') ? s2 : null;
        });
      }).then(function(src){
        if (!src || M.detail !== want) return;
        p.photo = src;
        setAvatar(i, src);
      });
    });
  };
  if (type !== 'series') return byWiki((m.cast || []).map(function(n){ return {name: n}; }));
  cached('https://api.tvmaze.com/lookup/shows?imdb=' + id).then(function(show){
    if (!show || !show.id) return null;
    return cached('https://api.tvmaze.com/shows/' + show.id + '/cast');
  }).then(function(cast){
    if (M.detail !== want) return;
    if (cast && cast.length) {
      var people = cast.map(function(c){ return {name: c.person.name, character: c.character && c.character.name, photo: c.person.image && (c.person.image.medium || c.person.image.original)}; });
      drawCast(people);
      byWiki(people);
    } else byWiki((m.cast || []).map(function(n){ return {name: n}; }));
  });
}
function setAvatar(i, src){
  var av = document.querySelectorAll('#dCast .av')[i]; if (!av) return;
  var im = img(src); im.style.cssText = 'width:100%;height:100%;object-fit:cover';
  im.onload = function(){ av.textContent = ''; av.appendChild(im); av.style.overflow = 'hidden'; };
}
function drawCast(people){
  var box = document.getElementById('dCast'); box.innerHTML = '';
  people.slice(0, 9).forEach(function(p, i){
    var a = el('div', 'actor'), av = el('span', 'av');
    av.textContent = initials(p.name); av.style.background = 'linear-gradient(135deg,' + TINTS2[i % TINTS2.length] + ',' + ADDON_COLORS[i % ADDON_COLORS.length] + ')';
    a.appendChild(av);
    a.appendChild(el('b', '', esc(p.name)));
    if (p.character) a.appendChild(el('span', '', esc(p.character)));
    box.appendChild(a);
  });
  if (!people.length) box.appendChild(el('span', 'msg', 'Elenco não informado.'));
  people.slice(0, 9).forEach(function(p, i){ if (p.photo) setAvatar(i, p.photo); });
}
function initials(n){ return String(n || '?').split(/\s+/).filter(Boolean).slice(0, 2).map(function(w){ return w[0]; }).join('').toUpperCase(); }

/* ---------- fontes ---------- */
function openSources(){
  var d = M.detail; if (!d) return;
  var m = d.meta, isSeries = d.type === 'series';
  var vid = isSeries ? (M.video && M.video.id) : d.id;
  if (!vid) return;
  // O botão dos detalhes passa a apontar para o episódio escolhido.
  if (isSeries && M.video) document.getElementById('dPlayLabel').textContent = 'Assistir T' + M.video.season + ':E' + (M.video.episode || M.video.number);
  M.streams = []; M.addonFilter = 'all'; M.qualFilter = 'all'; M.langFilter = 'all'; closeDrop();
  M.recs = null; M.recsStarted = false; M.subsRaw = fetchSubsRaw(d.type, vid);
  var req = ++M.req;
  document.getElementById('sArt').src = bigBg(m) || m.background || '';
  var poster = document.getElementById('sPoster'); poster.src = posterOf(m) || m.poster || '';
  document.getElementById('sTitle').textContent = m.name;
  document.getElementById('sSub').textContent = isSeries && M.video ? 'T' + M.video.season + ' · E' + (M.video.episode || M.video.number) + ' · ' + (M.video.name || M.video.title || '') : [m.releaseInfo || m.year, m.runtime].filter(Boolean).join(' · ');
  document.getElementById('sAddons').innerHTML = ''; updateSelects();
  document.getElementById('sList').innerHTML = '<div class="msg">Procurando fontes nos seus add-ons…</div>';
  document.getElementById('sCount').textContent = '';
  showPage('sources', false);
  focus(document.querySelector('#sourcesPage [data-mact="back"]'));
  var addons = [];
  try { addons = JSON.parse(TV.addons()); } catch (e) {}
  if (!addons.length) { document.getElementById('sList').innerHTML = '<div class="msg">Nenhum add-on de fontes configurado.</div>'; return; }
  var left = addons.length, next = 0;
  function worker(){
    if (req !== M.req || next >= addons.length) return;
    var ai = next++, a = addons[ai];
    var base = a.url.replace(/\/manifest\.json$/, '');
    if (addonDown(a.url)) { left--; renderSources(left === 0); worker(); return; }
    withTimeout(getJson(base + '/stream/' + d.type + '/' + encodeURIComponent(vid).replace(/%3A/g, ':') + '.json'), 12000).then(function(j){
      addonResult(a.url, !!j);
      if (req !== M.req) return;
      ((j && j.streams) || []).forEach(function(s){ var p = parseStream(s, a.name, ai); if (p) { p.addonUrl = a.url; p.vid = vid; M.streams.push(p); } });
      left--;
      renderSources(left === 0);
      worker();
    });
  }
  for (var w = 0; w < 8; w++) worker();
}
/* Um add-on lento não segura a tela: resposta depois do limite vale como falha. */
function withTimeout(p, ms){ return Promise.race([p, new Promise(function(r){ setTimeout(function(){ r(null); }, ms); })]); }
/* Desvio automático: add-on que falha 3 vezes seguidas fica de fora por 6 horas e depois é testado de novo. */
function addonHealth(){ try { return JSON.parse(localStorage.getItem('addonHealth') || '{}'); } catch (e) { return {}; } }
function addonDown(url){ var h = addonHealth()[url]; return !!(h && h.until && h.until > Date.now()); }
function addonResult(url, ok){
  var all = addonHealth(), h = all[url] || {fails: 0};
  if (ok) h = {fails: 0}; else { h.fails++; if (h.fails >= 3) { h.until = Date.now() + 6 * 3600e3; h.fails = 0; } }
  all[url] = h;
  try { localStorage.setItem('addonHealth', JSON.stringify(all)); } catch (e) {}
}
function parseStream(s, addon, ai){
  if (!s.infoHash && !s.url) return null;
  if (s.url && /^https?:\/\/(www\.)?youtube\./.test(s.url)) return null;
  // Add-ons that answer with a placeholder video when their config is broken (MediaFusion does).
  // Vídeos falsos de aviso ("faça login", configuração inválida) também ficam de fora.
  if (s.url && /\/static\/exceptions\/|invalid_config|\/exceptions\/[a-z_]+\.mp4|\/(signin|login|register|error|expired)\.mp4(\?|$)/i.test(s.url)) return null;
  var text = [s.name, s.title, s.description, s.behaviorHints && s.behaviorHints.filename].filter(Boolean).join('\n');
  var q = /2160p|\b4k\b|uhd/i.test(text) ? '4K' : /1080p/i.test(text) ? '1080p' : /720p/i.test(text) ? '720p' : /480p|\bsd\b/i.test(text) ? '480p' : '—';
  var hdr = /dolby ?vision|\bdv\b/i.test(text) ? 'DV' : /hdr/i.test(text) ? 'HDR' : /remux/i.test(text) ? 'REMUX' : /blu-?ray|bdrip/i.test(text) ? 'BluRay' : /web-?dl|webrip|\bweb\b/i.test(text) ? 'WEB' : '';
  var size = (text.match(/💾\s*([\d.,]+\s*[GMK]i?B)/i) || text.match(/\b([\d.,]+\s*(GB|MB|GiB|MiB))\b/i) || [])[1] || '';
  var seeds = (text.match(/👤\s*(\d+)/) || [])[1] || '';
  // Episódio errado (alguns add-ons devolvem o S02E01 quando se pede o S01E01): fica de fora.
  if (M.detail && M.detail.type === 'series' && M.video) {
    var se = text.match(/\bs(\d{1,2})[ ._-]?e(\d{1,3})\b/i);
    if (se && (+se[1] !== +M.video.season || +se[2] !== +(M.video.episode || M.video.number))) return null;
  }
  // "DUAL" sozinho não é dublado (em anime costuma ser japonês + inglês): só conta em add-on brasileiro.
  var bhint = (s.behaviorHints && s.behaviorHints.bingeGroup) || '';
  var dub = /dublad|pt-?br|portugu|🇧🇷|nacional/i.test(text + ' ' + bhint) || (/\bdual\b/i.test(text) && /^brazuca torrents$|bestcine/i.test(addon));
  var lines = String(s.title || s.description || '').split('\n');
  var name = (s.behaviorHints && s.behaviorHints.filename) || lines.slice(0, 2).join(' · ') || s.name || 'Fonte';
  return {raw: s, addon: addon, color: ADDON_COLORS[ai % ADDON_COLORS.length], q: q, hdr: hdr, size: size, seeds: seeds ? +seeds : -1, dub: dub, name: name, torrent: !!s.infoHash};
}
var QRANK = {'4K': 4, '1080p': 3, '720p': 2, '480p': 1, '—': 0};
function renderSources(done){
  var all = M.streams.slice().sort(function(a, b){ return (QRANK[b.q] - QRANK[a.q]) || (b.seeds - a.seeds); });
  var addonsSeen = {}; all.forEach(function(s){ addonsSeen[s.addon] = (addonsSeen[s.addon] || 0) + 1; });
  var list = all.filter(function(s){
    if (M.addonFilter !== 'all' && s.addon !== M.addonFilter) return false;
    if (M.qualFilter !== 'all' && s.q !== M.qualFilter) return false;
    if (M.langFilter === 'dub' && !s.dub) return false;
    return true;
  });
  var focusedKey = cur && document.getElementById('sList').contains(cur) ? cur.dataset.idx + (cur.classList.contains('rec') ? 'r' : '') : null;
  var focusChip = cur && document.getElementById('sAddons').contains(cur) ? cur.dataset.val : null;

  var chips = document.getElementById('sAddons'); chips.innerHTML = '';
  [['all', 'Todas', all.length]].concat(Object.keys(addonsSeen).map(function(k){ return [k, k, addonsSeen[k]]; })).forEach(function(c){
    var b = el('div', 'f chip' + (M.addonFilter === c[0] ? ' sel' : ''), esc(c[1]) + '<span style="font-size:14px;font-weight:700;opacity:.75">' + c[2] + '</span>');
    b.dataset.mact = 'addon'; b.dataset.val = c[0];
    chips.appendChild(b);
  });
  M.lastAll = all;
  updateSelects();
  document.getElementById('sCount').textContent = all.length + ' opções de ' + Object.keys(addonsSeen).length + ' add-ons' + (done ? '' : ' · buscando…');

  var box = document.getElementById('sList'); box.innerHTML = ''; box.style.transform = '';
  // Links que o teste mostrou fora do ar vão para o fim.
  list.sort(function(a, b){ return (a.alive === false) - (b.alive === false); });
  // Recomendadas no topo (só com os filtros em "todas", para não esconder o que você filtrou).
  var showRecs = M.recs && M.recs.length && M.addonFilter === 'all' && M.qualFilter === 'all';
  if (showRecs) M.recs.forEach(function(rc){ if (M.langFilter !== 'dub' || rc.s.dub) box.appendChild(srcRow(rc.s, rc)); });
  else if (done && !M.recs && M.recsStarted) box.appendChild(el('div', 'msg', '★ Testando as melhores fontes…'));
  if (!list.length) box.appendChild(el('div', 'msg', done ? (all.length ? 'Nenhuma fonte com esse filtro.' : 'Nenhuma fonte encontrada nos seus add-ons.') : 'Procurando fontes nos seus add-ons…'));
  list.slice(0, 80).forEach(function(s){ box.appendChild(srcRow(s, null)); });
  if (done && !M.recsStarted) { M.recsStarted = true; computeRecs(M.req); }

  if (focusChip) {
    var again = document.querySelector('#sourcesPage .chip[data-val="' + focusChip + '"]');
    if (again) return focus(again);
  }
  if (focusedKey) {
    var isRec = focusedKey.slice(-1) === 'r', key = isRec ? focusedKey.slice(0, -1) : focusedKey;
    var back = box.querySelector('.src' + (isRec ? '.rec' : ':not(.rec)') + '[data-idx="' + key + '"]') || box.querySelector('.f');
    if (back) return focus(back);
  }
  if (page === 'sources' && cur && cur.dataset.mact === 'back' && box.querySelector('.f')) focus(box.querySelector('.f'));
}
/* Uma linha da lista de fontes; rec = {title, why} quando é recomendada. */
function srcRow(s, rec){
  var r = el('div', 'f src' + (rec ? ' rec' : '') + (rec && /Assistir agora/.test(rec.title) ? ' top' : ''));
  r.dataset.mact = 'play'; r.dataset.idx = M.streams.indexOf(s);
  r.appendChild(el('span', 'ql' + (s.q === '4K' ? ' k4' : ''), '<b>' + s.q + '</b><span>' + esc(s.hdr) + '</span>'));
  var tags = [];
  if (s.size) tags.push('<span>💾 ' + esc(s.size) + '</span>');
  if (s.seeds >= 0) tags.push('<span>👤 ' + s.seeds + '</span>');
  if (s.dub) tags.push('<span>PT-BR</span>');
  if (s.syncSub) tags.push('<span class="ok">Legenda sincronizada</span>');
  if (s.alive === true) tags.push('<span class="ok">No ar ✓</span>');
  if (s.alive === false) tags.push('<span class="bad">Fora do ar</span>');
  tags.push('<span>' + (s.torrent ? 'Torrent' : 'Link direto') + '</span>');
  var head = rec ? '<span class="badge">★ ' + esc(rec.title) + '<em>' + esc(rec.why) + '</em></span>' : '';
  r.appendChild(el('span', 'body', head + '<b>' + esc(s.name) + '</b><span class="tags">' + tags.join('') + '</span>'));
  r.appendChild(el('span', 'ad', '<i style="background:' + s.color + '"></i>' + esc(s.addon)));
  return r;
}

function playStream(s){
  var raw = s.raw, title = (M.detail && M.detail.meta.name) || 'video', d = M.detail;
  rememberVersion(s);
  if (d) window.returnTo = {type: d.type, id: d.id, vid: s.vid || d.id};
  // Link direto: player da própria TV. Se ele não conseguir tocar, a mesma fonte vai para o Stremio.
  if (!raw.infoHash && raw.url && d) {
    var m = d.meta, isSeries = d.type === 'series', v = M.video;
    var hdr = (raw.behaviorHints && raw.behaviorHints.proxyHeaders && raw.behaviorHints.proxyHeaders.request) || {};
    var payload = {
      url: raw.url,
      title: m.name,
      sub: isSeries && v ? 'T' + v.season + ' · E' + (v.episode || v.number) + (v.name ? ' · ' + v.name : '') : [m.releaseInfo || m.year, m.runtime].filter(Boolean).join(' · '),
      art: bigBg(m) || m.background || '',
      key: d.type + ':' + (s.vid || d.id),
      headers: JSON.stringify(hdr),
      stream: JSON.stringify(raw), addon: s.addonUrl || '', meta: CINEMETA + '/manifest.json',
      type: d.type, id: d.id, vid: s.vid || d.id,
      dub: !!s.dub
    };
    toast('Buscando legendas…');
    Promise.all([fetchSubs(d.type, s.vid || d.id), fetchSkips(d, v)]).then(function(res){
      var subs = res[0];
      payload.skips = JSON.stringify(res[1] || []);
      if (s.syncSub) subs = [s.syncSub].concat(subs.filter(function(x){ return x.url !== s.syncSub.url; }));
      payload.subs = JSON.stringify(subs);
      if (!TV.playDirect(JSON.stringify(payload))) playInStremioOrMagnet(s);
    });
    return;
  }
  playInStremioOrMagnet(s);
}
function playInStremioOrMagnet(s){
  var raw = s.raw, title = (M.detail && M.detail.meta.name) || 'video', d = M.detail;
  if (s.syncSub) {
    raw = JSON.parse(JSON.stringify(raw));
    raw.subtitles = [{id: 'sync-ptbr', url: s.syncSub.url, lang: 'por'}].concat(raw.subtitles || []);
  }
  // Stremio's own player link plays exactly this source; the magnet below is only a fallback.
  if (d && TV.playInStremio(JSON.stringify(raw), s.addonUrl || '', CINEMETA + '/manifest.json', d.type, d.id, s.vid || d.id)) return;
  if (raw.infoHash) {
    var trackers = (raw.sources || []).filter(function(x){ return x.indexOf('tracker:') === 0; }).slice(0, 10).map(function(x){ return '&tr=' + encodeURIComponent(x.slice(8)); }).join('');
    var magnet = 'magnet:?xt=urn:btih:' + raw.infoHash + '&dn=' + encodeURIComponent(title) + trackers;
    if (!TV.openUri(magnet, STREMIO)) toast('Instale o Stremio para tocar torrents.');
    return;
  }
  if (!TV.openUri(raw.url, STREMIO) && !TV.openUri(raw.url, '')) toast('Nenhum player abriu este link.');
}
function openInNuvio(){
  var d = M.detail; if (!d) { TV.launch(NUVIO); return; }
  var path = 'stremio:///detail/' + d.type + '/' + d.id + (d.type === 'series' && M.video ? '/' + M.video.id : '');
  if (!TV.openUri(path, NUVIO)) TV.launch(NUVIO);
}
function toast(t){
  var n = el('div', '', esc(t));
  n.style.cssText = 'position:absolute;left:50%;bottom:72px;transform:translateX(-50%);z-index:9;background:rgba(10,14,36,0.92);border:1px solid rgba(255,255,255,0.3);border-radius:28px;padding:16px 28px;font-size:20px;font-weight:600';
  document.getElementById('stage').appendChild(n);
  setTimeout(function(){ n.remove(); }, 3500);
}

/* ---------- ações e voltar ---------- */
window.mAct = function(e){
  var a = e.dataset.mact;
  if (a === 'search') return startTyping();
  if (a === 'type') {
    searchInput().value = '';
    M.type = e.dataset.type;
    Array.prototype.forEach.call(document.querySelectorAll('#mTabs .tab'), function(t){ t.classList.toggle('sel', t === e); });
    loadCatalog();
    return;
  }
  if (a === 'heroPlay' || a === 'heroInfo') {
    if (!M.hero) return;
    openDetail(M.hero.type, M.hero.id, 'movies');
    if (a === 'heroPlay' && M.hero.type === 'movie') waitDetail(openSources);
    return;
  }
  if (a === 'open') return openDetail(e.dataset.type, e.dataset.id, 'movies');
  if (a === 'resume' && e.dataset.next) { return mPlayNext(JSON.parse(e.dataset.next)); }
  if (a === 'resume' && e.dataset.play) { setReturnFromPlay(e.dataset.play); return TV.playDirect(e.dataset.play); }
  if (a === 'resume') {
    findByTitle(e.dataset.title).then(function(r){ if (r) openDetail(r.type, r.id, 'movies'); else toast('Não encontrei esse título no catálogo.'); });
    return;
  }
  if (a === 'season') return pickSeason(+e.dataset.season);
  if (a === 'episode') {
    var v = (M.detail.meta.videos || []).filter(function(x){ return x.id === e.dataset.vid; })[0];
    if (v) { M.video = v; openSources(); }
    return;
  }
  if (a === 'dPlay') return openSources();
  if (a === 'trailer') {
    var m = M.detail && M.detail.meta, yt = m && ((m.trailerStreams && m.trailerStreams[0] && m.trailerStreams[0].ytId) || (m.trailers && m.trailers[0] && m.trailers[0].source));
    if (yt) TV.openUri('https://www.youtube.com/watch?v=' + yt, 'com.google.android.youtube.tv');
    return;
  }
  if (a === 'nuvio') return openInNuvio();
  if (a === 'addon') { M.addonFilter = e.dataset.val; return renderSources(true); }
  if (a === 'dropdown') return openDrop(e);
  if (a === 'gmenu') {
    var isSort = e.dataset.kind === 'sort';
    return openMenu(e, 'gDrop', isSort ? SORTS : GENRES, isSort ? M.sort : M.genre, function(v){
      if (isSort) M.sort = v; else M.genre = v;
      loadGrid(true);
    });
  }
  if (a === 'play') { var s = M.streams[+e.dataset.idx]; if (s) playStream(s); return; }
  if (a === 'back') return mBack();
};
function waitDetail(fn){
  var n = 0, t = setInterval(function(){ if (M.detail || ++n > 100) { clearInterval(t); if (M.detail) fn(); } }, 100);
}
window.mBack = function(){
  if (page === 'sources') { M.req++; return showPage(M.detail && M.from !== 'skip' ? 'detail' : 'movies'); }
  if (page === 'detail') return showPage('movies');
  showPage('home');
};

/* Teste por adb (ver WebHomeActivity.onNewIntent): "movies", "detail:series:tt..." ou "sources:movie:tt..." */
window.debugOpen = function(s){
  var p = String(s).split(':');
  if (p[0] === 'movies') return showPage('movies');
  if (p[0] === 'games') return showPage('games');
  if (p[0] === 'watched') { TV.markWatched(p.slice(1).join(':')); return; }
  if (p[0] === 'unwatched') { TV.unmarkWatched(p.slice(1).join(':')); return; }
  if (p[0] === 'season') return pickSeason(+p[1]);
  if (p[0] === 'next') return playNext(p[1], p[2], p.slice(3).join(':'));
  if (p[0] === 'ended') return showEnded(p[1], p[2], p.slice(3).join(':'));
  if (p[0] === 'title') return goTitle(p[1], p[2], p.slice(3).join(':'));
  if (p[0] === 'navlog') { window.NAVLOG = true; return; }
  if (p[0] === 'ep') {
    openDetail('series', p[1], 'movies');
    waitDetail(function(){
      var v = (M.detail.meta.videos || []).filter(function(x){ return +x.season === +p[2] && +(x.episode || x.number) === +p[3]; })[0];
      if (v) { M.video = v; openSources(); }
      if (v && p[4]) { var n = 0, t = setInterval(function(){
        var s = M.streams.filter(function(x){ return !x.torrent && x.addon === p[4]; })[0];
        if (s || ++n > 60) { clearInterval(t); if (s) playStream(s); }
      }, 500); }
    });
    return;
  }
  if (p[0] === 'gopen') { var b = document.getElementById(p[1] === 'genre' ? 'gGenreSel' : 'gSortSel'); focus(b); mAct(b); return; }
  if (p[0] === 'gset') { if (p[1] === 'svc') p.splice(1, 2, 'svc:' + p[2]); M.sort = p[1]; M.genre = p[2] || 'all'; loadGrid(true); return; }
  if (p[0] === 'direct') {
    openDetail(p[1], p[2], 'movies');
    waitDetail(function(){
      openSources();
      var n = 0, t = setInterval(function(){
        var s = M.streams.filter(function(x){ return !x.torrent && (!p[3] || x.addon === p[3]); })[+(p[4] || 0)];
        if (s || ++n > 60) { clearInterval(t); if (s) { console.log('debug direct ' + s.addon + ' ' + s.raw.url); playStream(s); } }
      }, 1000);
    });
    return;
  }
  if (p[0] === 'search') { showPage('movies'); searchInput().value = p.slice(1).join(':'); runSearch(searchInput().value); setTimeout(function(){ var f = document.querySelector('#mGrid .f'); if (f) focus(f); }, 4000); return; }
  if (p[0] === 'tab') { showPage('movies'); var t = document.querySelector('#mTabs [data-type="' + p[1] + '"]'); if (t) { focus(t); mAct(t); } return; }
  if (p[0] === 'detail') return openDetail(p[1], p[2], 'movies');
  if (p[0] === 'sources') { openDetail(p[1], p[2], 'movies'); waitDetail(openSources); }
};


/* ---------- busca ---------- */
function searchInput(){ return document.getElementById('mSearch'); }
function startTyping(){
  var box = document.getElementById('mSearchBox'), inp = searchInput();
  box.classList.add('typing');
  inp.focus();
  try { inp.setSelectionRange(inp.value.length, inp.value.length); } catch (e) {}
}
var searchTimer = null, searchSeq = 0;
searchInput().addEventListener('input', function(){
  clearTimeout(searchTimer);
  var q = searchInput().value.trim();
  searchTimer = setTimeout(function(){ if (q.length >= 2) runSearch(q); }, 500);
});
searchInput().addEventListener('blur', function(){ document.getElementById('mSearchBox').classList.remove('typing'); });

function runSearch(q){
  var mine = ++searchSeq, enc = encodeURIComponent(q);
  M.type = 'search';
  M.grid = {type: 'search', done: true, loading: false, n: 0, seen: {}};
  Array.prototype.forEach.call(document.querySelectorAll('#mTabs .tab'), function(t){ t.classList.remove('sel'); });
  document.getElementById('mAll').style.display = 'none';
  document.getElementById('mGridWrap').style.display = 'flex';
  document.getElementById('mGridTitle').textContent = 'Resultados para “' + q + '”';
  document.getElementById('mGridCount').textContent = 'buscando…';
  var grid = document.getElementById('mGrid');
  grid.innerHTML = '<div class="msg">Buscando…</div>'; grid.style.transform = '';
  Promise.all([
    cached(CINEMETA + '/catalog/movie/top/search=' + enc + '.json'),
    cached(CINEMETA + '/catalog/series/top/search=' + enc + '.json')
  ]).then(function(r){
    if (mine !== searchSeq) return;
    var a = (r[0] && r[0].metas) || [], b = (r[1] && r[1].metas) || [], out = [], seen = {};
    // intercala filmes e séries para os dois aparecerem no topo
    for (var i = 0; i < Math.max(a.length, b.length); i++) {
      [a[i], b[i]].forEach(function(m){ if (m && !seen[m.id]) { seen[m.id] = 1; out.push(m); } });
    }
    grid.innerHTML = '';
    if (!out.length) { grid.innerHTML = '<div class="msg">Nada encontrado para “' + esc(q) + '”.</div>'; document.getElementById('mGridCount').textContent = '0 títulos'; return; }
    out.forEach(function(m, i){ grid.appendChild(posterCard(m, i)); });
    M.grid.n = out.length;
    document.getElementById('mGridCount').textContent = out.length + ' títulos';
  });
}
/* Saindo do campo: Enter/OK ou para baixo vão para os resultados; para cima fica na barra. */
window.mSearchDone = function(dir){
  var inp = searchInput(), q = inp.value.trim();
  clearTimeout(searchTimer);
  inp.blur();
  if (q.length >= 2 && (M.type !== 'search' || document.getElementById('mGridTitle').textContent.indexOf(q) < 0)) runSearch(q);
  var box = document.getElementById('mSearchBox');
  if (dir === 'U') return focus(box);
  var tryFocus = function(n){
    var first = document.querySelector('#mGrid .f');
    if (first && M.type === 'search') focus(first);
    else if (n > 0) setTimeout(function(){ tryFocus(n - 1); }, 300);
    else focus(box);
  };
  tryFocus(dir === 'D' || dir === null ? 20 : 0);
};
/* Voltar com busca ativa: limpa e volta para o Tudo. */
window.mSearchClear = function(){
  var inp = searchInput();
  if (document.activeElement === inp) { inp.blur(); focus(document.getElementById('mSearchBox')); return true; }
  if (M.type !== 'search') return false;
  inp.value = ''; searchSeq++;
  var all = document.querySelector('#mTabs [data-type="all"]');
  M.type = 'all';
  Array.prototype.forEach.call(document.querySelectorAll('#mTabs .tab'), function(t){ t.classList.toggle('sel', t === all); });
  loadCatalog();
  focus(document.getElementById('mSearchBox'));
  return true;
};


/* ---------- legendas dos add-ons ---------- */
var LANGS = [
  [/^(pob|pt-?br|pt_br|por-?br|portuguese \(br\)|portugu[eê]s \(br\)|brazil)/i, 'pt-BR', 'Português (BR)'],
  [/^(por|pt|portuguese|portugu[eê]s)$/i, 'pt', 'Português'],
  [/^(eng|en|english|ingl[eê]s)$/i, 'en', 'Inglês'],
  [/^(spa|es|spanish|espa[nñ]ol)$/i, 'es', 'Espanhol']
];
var LANG_MAX = {'pt-BR': 8, 'pt': 4, 'en': 4, 'es': 2};
function normLang(l){
  l = String(l || '').trim();
  for (var i = 0; i < LANGS.length; i++) if (LANGS[i][0].test(l)) return {code: LANGS[i][1], name: LANGS[i][2], rank: i};
  return null;
}
/* Consulta todos os add-ons de legenda em paralelo; responde com o que chegou em até 6 s. */
function fetchSubs(type, vid){
  var addons = [];
  try { addons = JSON.parse(TV.subtitleAddons()); } catch (e) {}
  if (!addons.length) return Promise.resolve([]);
  var got = [];
  var all = addons.map(function(a){
    var base = a.url.replace(/\/manifest\.json$/, '');
    return getJson(base + '/subtitles/' + type + '/' + encodeURIComponent(vid).replace(/%3A/g, ':') + '.json').then(function(j){
      ((j && j.subtitles) || []).forEach(function(x){ if (x && x.url) got.push({url: x.url, lang: x.lang || x.lang_code, addon: a.name}); });
    });
  });
  var timeout = new Promise(function(res){ setTimeout(res, 6000); });
  return Promise.race([Promise.all(all), timeout]).then(function(){
    var seen = {}, count = {}, out = [];
    got.forEach(function(x){
      var L = normLang(x.lang);
      if (!L || seen[x.url]) return;
      count[L.code] = (count[L.code] || 0) + 1;
      if (count[L.code] > LANG_MAX[L.code]) return;
      seen[x.url] = 1;
      out.push({url: x.url, lang: L.code, rank: L.rank, label: L.name + ' · ' + x.addon + ' ' + count[L.code]});
    });
    out.sort(function(a, b){ return a.rank - b.rank; });
    // Sem nenhuma em português: a nossa, traduzida da primeira em inglês (o player traduz).
    // Várias de origem: se uma falhar, o player tenta a próxima no mesmo idioma.
    var hasPt = out.some(function(x){ return /^pt/.test(x.lang); }), en = out.filter(function(x){ return x.lang === 'en'; }).slice(0, 3);
    if (!hasPt) out.unshift.apply(out, en.map(function(x, k){ return {url: 'translate:' + x.url, lang: 'pt-BR', label: 'Português · tradução automática ' + (k + 1)}; }));
    return out.map(function(x){ return {url: x.url, lang: x.lang, label: x.label}; });
  });
}


/* ---------- seletores Qualidade e Idioma (rodapé da tela de fontes) ---------- */
var QUAL_OPTS = [['all', 'Todas'], ['4K', '4K'], ['1080p', '1080p'], ['720p', '720p'], ['480p', '480p']];
var LANG_OPTS = [['all', 'Todos'], ['dub', 'Dublado / PT-BR']];
function updateSelects(){
  var q = QUAL_OPTS.filter(function(o){ return o[0] === M.qualFilter; })[0], l = LANG_OPTS.filter(function(o){ return o[0] === M.langFilter; })[0];
  document.getElementById('sQualVal').textContent = q ? q[1] : 'Todas';
  document.getElementById('sLangVal').textContent = l ? (l[0] === 'dub' ? 'Dublado' : l[1]) : 'Todos';
}
var drop = null;
/* Lista suspensa genérica: opts [[valor, rótulo, contagem?]]; onPick(valor) ao confirmar. */
function openMenu(btn, boxId, opts, current, onPick){
  var box = document.getElementById(boxId);
  box.innerHTML = '';
  var list = el('div', 'dlist');
  opts.forEach(function(o){
    var d = el('div', 'dopt' + (o[0] === current ? ' sel' : ''), '<span class="dot"></span>' + esc(o[1]) + (o[2] != null ? '<span class="n">' + o[2] + '</span>' : ''));
    d.dataset.val = o[0];
    list.appendChild(d);
  });
  box.appendChild(list);
  box.style.left = btn.offsetLeft + 'px';
  box.style.display = 'flex';
  drop = {box: box, list: list, btn: btn, onPick: onPick, items: Array.prototype.slice.call(list.children), i: Math.max(0, opts.map(function(o){ return o[0]; }).indexOf(current))};
  btn.classList.remove('on');
  drawDrop();
}
function drawDrop(){
  drop.items.forEach(function(d, i){ d.classList.toggle('on', i === drop.i); });
  // listas longas (gêneros) rolam dentro da caixa
  var it = drop.items[drop.i], view = drop.box.clientHeight - 20, m = drop.list.style.transform.match(/-?[\d.]+/), shift = m ? -parseFloat(m[0]) : 0;
  if (it.offsetTop < shift) shift = it.offsetTop; else if (it.offsetTop + it.offsetHeight > shift + view) shift = it.offsetTop + it.offsetHeight - view;
  drop.list.style.transform = 'translateY(' + (-Math.max(0, shift)) + 'px)';
}
function closeDrop(){
  if (!drop) return false;
  var btn = drop.btn;
  drop.box.style.display = 'none';
  drop = null;
  focus(btn);
  return true;
}
function openDrop(btn){
  var kind = btn.dataset.kind, all = M.lastAll || [];
  var opts = (kind === 'qual' ? QUAL_OPTS : LANG_OPTS).map(function(o){
    return [o[0], o[1], o[0] === 'all' ? all.length : all.filter(function(s){ return kind === 'qual' ? s.q === o[0] : s.dub; }).length];
  });
  openMenu(btn, 'sDrop', opts, kind === 'qual' ? M.qualFilter : M.langFilter, function(v){
    if (kind === 'qual') M.qualFilter = v; else M.langFilter = v;
    renderSources(true);
  });
}
window.mModalKey = function(ev){
  if (!drop) return false;
  var k = ev.key;
  if (k === 'ArrowUp') { drop.i = Math.max(0, drop.i - 1); drawDrop(); }
  else if (k === 'ArrowDown') { drop.i = Math.min(drop.items.length - 1, drop.i + 1); drawDrop(); }
  else if (k === 'Enter' || k === ' ' || ev.keyCode === 23) {
    var v = drop.items[drop.i].dataset.val, pick = drop.onPick, btn = drop.btn;
    closeDrop();
    pick(v);
    if (document.body.contains(btn)) focus(btn);
  }
  return true;
};
window.mModalBack = function(){ return closeDrop() || (page === 'detail' && hideEnded()); };


/* ---------- fontes recomendadas ---------- */
/* Legendas com o nome da versão (para casar com o arquivo da fonte). */
function fetchSubsRaw(type, vid){
  var addons = [];
  try { addons = JSON.parse(TV.subtitleAddons()); } catch (e) {}
  var got = [];
  var all = addons.map(function(a){
    var base = a.url.replace(/\/manifest\.json$/, '');
    return getJson(base + '/subtitles/' + type + '/' + encodeURIComponent(vid).replace(/%3A/g, ':') + '.json').then(function(j){
      ((j && j.subtitles) || []).forEach(function(x){
        var L = normLang(x.lang || x.lang_code);
        if (!x.url || !L || (L.code !== 'pt-BR' && L.code !== 'pt')) return;
        var name = x.subtitleFileName || x.movieReleaseName || x.title || x.id || '';
        got.push({url: x.url, lang: L.code, addon: a.name, name: String(name)});
      });
    });
  });
  return Promise.race([Promise.all(all), new Promise(function(r){ setTimeout(r, 12000); })]).then(function(){ return got; });
}
/* Grupo, origem, resolução e serviço de um nome de release. */
function relInfo(n){
  n = String(n || '').toLowerCase().replace(/\.(mkv|mp4|avi|srt|vtt|ass)$/i, '');
  var g = (n.match(/-([a-z0-9]{2,})(?:\[[^\]]*\])?$/) || [])[1] || '';
  var src = /web-?dl|webdl/.test(n) ? 'webdl' : /webrip/.test(n) ? 'webrip' : /blu-?ray|bdrip|brrip|bdremux/.test(n) ? 'bluray' : /hdtv/.test(n) ? 'hdtv' : /dvdrip|dvd/.test(n) ? 'dvd' : '';
  var res = (n.match(/(2160|1080|720|480)p/) || [])[1] || '';
  var svc = (n.match(/\b(nf|amzn|dsnp|hmax|atvp|max|pmtp|hulu|cr)\b/) || [])[1] || '';
  return {g: g, src: src, res: res, svc: svc, flat: n.replace(/[^a-z0-9]/g, '')};
}
function matchScore(a, b){
  if (a.flat && a.flat === b.flat) return 10;
  var sc = 0;
  if (a.g && a.g === b.g) sc += 3;
  if (a.src && a.src === b.src) sc += 2;
  if (a.res && a.res === b.res) sc += 1;
  if (a.svc && a.svc === b.svc) sc += 1;
  return sc;
}
var ADDON_TRUST = {'FrostStream': 3, 'HdHub': 2.5, 'BestCine': 2.2, 'Torrentio': 2, 'Torrentio Brazuca': 2, 'TorrentsDB': 1.5, 'Comet | ElfHosted | TORRENT': 1.5, 'NoTorrent': 1, 'MediaFusion | ElfHosted': 1};
var QPREF = {'1080p': 3, '4K': 2.2, '720p': 2, '480p': 1, '—': 0.5};
function sizeGB(s){
  var m = String(s.size || '').match(/([\d.,]+)\s*(G|M)/i);
  if (!m) return 0;
  var v = parseFloat(m[1].replace(',', '.'));
  return /m/i.test(m[2]) ? v / 1024 : v;
}
function baseScore(s){
  var sc = (ADDON_TRUST[s.addon] || 1) + (QPREF[s.q] || 0);
  if (s.torrent) sc += Math.min(3, Math.log(Math.max(1, s.seeds)) / Math.log(10) * 1.5) - (sizeGB(s) > 15 ? 1.5 : 0);
  else sc += s.alive === true ? 5 : 1.5;  // link direto testado e no ar: toca na própria TV, sem Stremio
  if (s.syncSub) sc += 1;
  if (s.name && /cam|hdcam|telesync|\bts\b/i.test(s.name)) sc -= 4;  // gravação de cinema
  return sc;
}
var probeCbs = {}, probeSeq = 0;
window.onProbe = function(id, ok, ms){ var cb = probeCbs[id]; delete probeCbs[id]; if (cb) cb(ok, ms); };
function probeStream(s){
  return new Promise(function(res){
    var id = 'p' + (++probeSeq), raw = s.raw;
    var hdr = (raw.behaviorHints && raw.behaviorHints.proxyHeaders && raw.behaviorHints.proxyHeaders.request) || {};
    probeCbs[id] = function(ok, ms){ s.alive = ok; s.ms = ms; console.log('probe ' + ok + ' ' + ms + 'ms ' + s.addon + ' ' + String(raw.url).slice(0, 90)); res(s); };
    TV.probe(id, raw.url, JSON.stringify(hdr));
    setTimeout(function(){ if (probeCbs[id]) { delete probeCbs[id]; s.alive = false; res(s); } }, 15000);
  });
}
/* Escolhe: Assistir agora, Melhor dublada e Legenda sincronizada. */
function computeRecs(req){
  Promise.all([M.subsRaw, ensureVersion()]).then(function(all){
    var subs = all[0];
    if (req !== M.req) return;
    // 1) casar cada fonte com a melhor legenda PT-BR da mesma versão
    var infos = subs.map(function(x){ return {x: x, r: relInfo(x.name)}; });
    M.streams.forEach(function(s){
      var r = relInfo(s.raw.behaviorHints && s.raw.behaviorHints.filename || s.name), best = null, bs = 0;
      infos.forEach(function(i){ var sc = matchScore(r, i.r); if (sc > bs || (sc === bs && best && i.x.lang === 'pt-BR' && best.lang !== 'pt-BR')) { bs = sc; best = i.x; } });
      if (best && bs >= 5) s.syncSub = {url: best.url, lang: best.lang, label: (best.lang === 'pt-BR' ? 'Português (BR)' : 'Português') + ' · sincronizada'};
    });
    // 2) testar os melhores links diretos (até 6, em paralelo)
    var direct = M.streams.filter(function(s){ return !s.torrent; }).sort(function(a, b){ return baseScore(b) - baseScore(a); });
    var sv = sameVersion();
    var pick = (sv && !sv.torrent ? [sv] : []).concat(direct.slice(0, 4), direct.filter(function(s){ return s.dub; }).slice(0, 3)).filter(function(s, i, arr){ return arr.indexOf(s) === i; }).slice(0, 7);
    renderSources(true);
    return Promise.all(pick.map(probeStream));
  }).then(function(){
    if (req !== M.req) return;
    var alive = function(s){ return s.torrent ? s.seeds >= 15 : s.alive === true; };
    var pool = M.streams.filter(alive).sort(function(a, b){ return baseScore(b) - baseScore(a); });
    var recs = [], used = [];
    function add(s, title, why){
      if (!s) return;
      var have = recs.filter(function(r){ return r.s === s; })[0];
      if (have) { have.title += ' · ' + title; return; }
      recs.push({s: s, title: title, why: why}); used.push(s);
    }
    function why(s){
      if (!s.torrent) return 'link testado agora' + (s.ms ? ' (' + (s.ms / 1000).toFixed(1).replace('.', ',') + ' s)' : '') + ' · toca na TV';
      return s.seeds + ' pessoas compartilhando · abre no Stremio';
    }
    var same = sameVersion();
    if (same && (same.torrent || same.alive === true)) {
      var prev = loadVersion();
      add(same, 'Mesma versão do episódio anterior', 'mesma fonte e qualidade do E' + prev.ep + (same.torrent ? ' · abre no Stremio' : ' · link testado agora'));
    }
    var nowS = pool.filter(function(s){ return !s.dub; })[0] || pool[0];
    add(nowS, 'Assistir agora', nowS ? why(nowS) : '');
    var dubS = pool.filter(function(s){ return s.dub; })[0];
    add(dubS, 'Melhor dublada', dubS ? why(dubS) : '');
    var syncS = pool.filter(function(s){ return s.syncSub && !s.dub; })[0];
    add(syncS, 'Legenda sincronizada', syncS ? 'legenda PT-BR da mesma versão do arquivo' : '');
    M.recs = recs;
    renderSources(true);
  });
}


/* ---------- mesma versão entre episódios (como o "binge group" do Stremio) ---------- */
function versionKey(){ return M.detail && M.detail.type === 'series' ? 'version:' + M.detail.id : null; }
function versionOf(s){
  var r = relInfo(s.raw.behaviorHints && s.raw.behaviorHints.filename || s.name);
  var bg = (s.raw.behaviorHints && s.raw.behaviorHints.bingeGroup) || '';
  // Alguns add-ons põem o título do episódio na etiqueta; o servidor ("Servidor-Athena") é o que importa.
  var server = (bg.match(/servidor-[a-z0-9]+/i) || [''])[0].toLowerCase();
  // Palavras do nome sem números (episódio, tamanho): o "jeito" do nome se repete entre episódios da mesma versão.
  var words = String(s.raw.behaviorHints && s.raw.behaviorHints.filename || s.name || '').toLowerCase()
    .replace(/\bs\d+e\d+\b|\b\d+([.,]\d+)?\s*(gb|mb)\b|\d+/g, ' ').split(/[^a-zÀ-ÿ]+/).filter(function(w){ return w.length > 1; });
  return {addon: s.addon, bg: bg, server: server, g: r.g, src: r.src, res: r.res || s.q, q: s.q, dub: !!s.dub, words: words};
}
function rememberVersion(s){
  var k = versionKey(); if (!k) return;
  var v = versionOf(s);
  v.ep = M.video ? (M.video.episode || M.video.number) : '';
  v.season = M.video ? M.video.season : '';
  try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) {}
}
function loadVersion(){
  var k = versionKey(); if (!k) return null;
  try { return JSON.parse(localStorage.getItem(k) || 'null'); } catch (e) { return null; }
}
/* A fonte deste episódio que é da mesma versão usada no anterior (ou null). */
function sameVersion(){
  var prev = loadVersion();
  if (!prev || (M.video && +prev.ep === +(M.video.episode || M.video.number) && +prev.season === +M.video.season)) return null;
  var best = null, bestSc = 0;
  M.streams.forEach(function(s){
    if (s.addon !== prev.addon || s.alive === false) return;
    var v = versionOf(s), sc = 0;
    if (prev.bg && v.bg === prev.bg) sc += 10;               // mesma etiqueta de versão do add-on
    if (prev.server && v.server === prev.server) sc += 6;     // mesmo servidor (BestCine e parecidos)
    if (prev.g && v.g === prev.g) sc += 5;                   // mesmo grupo de release
    if (prev.src && v.src === prev.src) sc += 1;
    if (prev.words && prev.words.length >= 4) {
      var a = {}, inter = 0, union = {};
      prev.words.forEach(function(w){ a[w] = 1; union[w] = 1; });
      v.words.forEach(function(w){ if (a[w]) inter++; union[w] = 1; });
      if (inter / Object.keys(union).length >= 0.8) sc += 6;     // nome quase igual fora os números
    }
    if (v.q === prev.q) sc += 2;
    if (v.dub === prev.dub) sc += 1; else sc -= 4;
    if (sc > bestSc) { bestSc = sc; best = s; }
  });
  return bestSc >= 7 ? best : null;
}


/* Sem versão guardada (assistido antes deste recurso): recupera do histórico do player.
   O link de reserva para o Stremio (stremio:///player/...) traz a fonte inteira compactada. */
function ensureVersion(){
  var k = versionKey();
  var saved = loadVersion();
  if (!k || (saved && saved.words) || !window.DecompressionStream) return Promise.resolve();
  var list = [];
  try { list = JSON.parse(TV.recents()); } catch (e) {}
  var name = norm(M.detail.meta.name);
  var hit = list.filter(function(r){ return r.play && norm(r.title) === name; })[0];
  if (!hit) return Promise.resolve();
  var play = {};
  try { play = JSON.parse(hit.play); } catch (e) { return Promise.resolve(); }
  var parts = String(play.fallback || '').replace('stremio:///player/', '').split('/');
  if (parts.length < 6) return Promise.resolve();
  var addonUrl = decodeURIComponent(parts[1]), epId = decodeURIComponent(parts[5]);
  var addons = [];
  try { addons = JSON.parse(TV.addons()); } catch (e) {}
  var addon = (addons.filter(function(a){ return a.url === addonUrl; })[0] || {}).name;
  if (!addon) return Promise.resolve();
  var bin = atob(decodeURIComponent(parts[0])), bytes = new Uint8Array(bin.length);
  for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  var ds = new Response(new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate')));
  return ds.text().then(function(txt){
    var raw = JSON.parse(txt), keep = M.video;
    M.video = null;   // o pacote é de outro episódio: sem o filtro de episódio aqui
    var p = parseStream(raw, addon, 0) || {raw: raw, addon: addon, name: '', q: '—', dub: false};
    M.video = keep;
    var v = versionOf(p), se = epId.split(':');
    v.season = se[1] || ''; v.ep = se[2] || '';
    try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) {}
    console.log('version from history ' + addon + ' ' + v.bg + ' E' + v.ep);
  }).catch(function(e){ console.log('version history failed ' + e); });
}

/* ---------- navegação por seções (fontes e detalhes) ----------
   Chamado por move() antes da busca pelo item mais próximo: devolve o destino, false para ficar
   parado, ou null para seguir a regra geral. */
window.mRoute = function(c, dir){
  var $ = function(id){ return document.getElementById(id); };
  if (page === 'sources') {
    var list = $('sList'), chips = $('sAddons'), qual = $('sQualSel');
    var foot = [qual, $('sLangSel'), $('sNuvioFoot')], fi = foot.indexOf(c);
    var listIn = list.querySelector('.f') ? entryOf(list) : null;
    var back = document.querySelector('#sourcesPage [data-mact="back"]'), side = $('sNuvioSide');
    if (c.parentNode === chips) {
      if (dir === 'D') return listIn || qual;
      if (dir === 'U') return false;
      if (dir === 'L') return back;                       // primeira aba: vai para Voltar
    }
    if (c.parentNode === list) {
      if (dir === 'U') return entryOf(chips);             // do topo da lista para a aba ativa
      if (dir === 'D') return qual;                       // do fim da lista para o rodapé
      if (dir === 'L') return side;                       // ← vai para a coluna da esquerda, não para o menu
    }
    if (fi >= 0) {
      if (dir === 'U') return listIn || entryOf(chips);
      if (dir === 'D') return false;
      if (dir === 'L') return fi > 0 ? foot[fi - 1] : side;
      if (dir === 'R') return fi < foot.length - 1 ? foot[fi + 1] : false;
    }
    if (c === back) { if (dir === 'D') return side; if (dir === 'R') return entryOf(chips); if (dir === 'U') return false; }
    if (c === side) { if (dir === 'D') return qual; if (dir === 'U') return back; if (dir === 'R') return listIn || qual; }
  }
  if (page === 'detail' && $('dEnd').style.display !== 'none') {
    var endRow = $('dEndRow');
    if (c.parentNode === endRow) { if (dir === 'U') return $('dPlay'); if (dir === 'D') return false; }
    if ($('dInfo').contains(c) && dir === 'D') return entryOf(endRow);
  }
  if (page === 'detail') {
    var info = $('dInfo'), seasons = $('dSeasons'), eps = $('dEps');
    var hasEps = $('dEpisodes').style.display !== 'none' && eps.querySelector('.f');
    var hdrBack = document.querySelector('#detailPage [data-mact="back"]');
    var epIn = function(){
      var curEp = M.video && eps.querySelector('[data-vid="' + M.video.id + '"]');
      return eps._last && eps._last.parentNode === eps ? eps._last : (curEp || eps.querySelector('.f'));
    };
    if (info.contains(c)) {
      if (dir === 'D') return hasEps ? entryOf(seasons) : false;
      if (dir === 'U') return hdrBack;
    }
    if (c === hdrBack && dir === 'D') return $('dPlay');
    if (c.parentNode === seasons) {
      if (dir === 'D') return hasEps ? epIn() : false;
      if (dir === 'U') return $('dPlay');
    }
    if (c.parentNode === eps) {
      if (dir === 'U') return entryOf(seasons);
      if (dir === 'D') return false;
    }
  }
  return null;
};


/* ---------- voltar para o título, próximo episódio e fim ---------- */
window.returnTo = null;
function setReturnFromPlay(playJson){
  try { var p = JSON.parse(playJson); if (p.id) window.returnTo = {type: p.type, id: p.id, vid: p.vid}; } catch (e) {}
}
/* Itens do Stremio/Nuvio no histórico: acha o título pelo nome para voltar a ele depois. */
function setReturnFromTitle(title){
  findByTitle(title).then(function(r){ if (r) window.returnTo = {type: r.type, id: r.id, vid: r.id}; });
}
/* Chamado pela Home ao voltar de um player (o nosso, o Stremio ou o Nuvio). */
window.mResume = function(){
  loadWatched();
  var p = '';
  try { p = TV.pendingOpen(); } catch (e) {}
  if (p) {
    var a = p.split(':'), kind = a[0], type = a[1], id = a[2], vid = a.slice(3).join(':');
    window.returnTo = null;
    if (kind === 'next') return playNext(type, id, vid);
    if (kind === 'ended') return showEnded(type, id, vid);
  }
  if (window.returnTo) { var r = window.returnTo; window.returnTo = null; goTitle(r.type, r.id, r.vid); }
};
function withDetail(type, id, then){
  if (M.detail && M.detail.id === id) { if (page !== 'detail') showPage('detail'); return then(); }
  openDetail(type, id, 'movies');
  waitDetail(then);
}
function videoById(vid){ return ((M.detail && M.detail.meta.videos) || []).filter(function(x){ return x.id === vid; })[0]; }
/* Abre a página do título, já na temporada e no episódio que estava tocando. */
function goTitle(type, id, vid){
  withDetail(type, id, function(){
    var v = videoById(vid);
    if (v) {
      pickSeason(v.season);
      M.video = v;
      document.getElementById('dPlayLabel').textContent = 'Assistir T' + v.season + ':E' + (v.episode || v.number);
      var ep = document.querySelector('#dEps [data-vid="' + v.id + '"]');
      if (ep) { document.getElementById('dEps')._last = ep; focus(ep); return; }
    }
    focus(document.getElementById('dPlay'));
  });
}
function nextVideo(vid){
  var vs = ((M.detail && M.detail.meta.videos) || []).filter(function(x){ return x.season > 0; })
    .sort(function(a, b){ return (a.season - b.season) || ((a.episode || a.number) - (b.episode || b.number)); });
  for (var i = 0; i < vs.length - 1; i++) if (vs[i].id === vid) return vs[i + 1];
  return null;
}
/* Próximo episódio com a mesma fonte: pergunta só ao add-on da versão em uso (rápido);
   se não achar a mesma versão no ar, abre a lista de fontes do próximo episódio. */
function playNext(type, id, vid){
  withDetail(type, id, function(){
    var nv = nextVideo(vid);
    if (!nv) return showEnded(type, id, vid);
    pickSeason(nv.season);
    M.video = nv;
    toast('Abrindo T' + nv.season + ':E' + (nv.episode || nv.number) + '…');
    var prev = loadVersion(), addons = [];
    try { addons = JSON.parse(TV.addons()); } catch (e) {}
    var a = prev && addons.filter(function(x){ return x.name === prev.addon; })[0];
    if (!a) return openSources();
    var base = a.url.replace(/\/manifest\.json$/, ''), ai = addons.indexOf(a);
    getJson(base + '/stream/' + type + '/' + encodeURIComponent(nv.id).replace(/%3A/g, ':') + '.json').then(function(j){
      M.streams = [];
      ((j && j.streams) || []).forEach(function(x){ var ps = parseStream(x, a.name, ai); if (ps) { ps.addonUrl = a.url; ps.vid = nv.id; M.streams.push(ps); } });
      var same = sameVersion();
      if (!same) return openSources();
      if (same.torrent) return playStream(same);
      probeStream(same).then(function(s2){ if (s2.alive) playStream(s2); else openSources(); });
    });
  });
}
/* Fim de filme ou da série: sugestões do mesmo gênero na página do título. */
function showEnded(type, id, vid){
  withDetail(type, id, function(){
    var m = M.detail.meta, genres = m.genres || m.genre || [];
    var anime = type === 'series' && genres.indexOf('Animation') >= 0;
    var g = anime ? 'Animation' : genres[0];
    var url = CINEMETA + '/catalog/' + type + '/top' + (g ? '/genre=' + encodeURIComponent(g) : '') + '.json';
    var box = document.getElementById('dEnd'), row = document.getElementById('dEndRow');
    document.getElementById('dEndTitle').textContent = 'Você terminou ' + m.name + '. Que tal assistir ' + (anime ? 'outro anime' : type === 'series' ? 'outra série' : 'outro filme') + '?';
    row.innerHTML = '<div class="msg">Carregando sugestões…</div>'; row.style.transform = '';
    box.style.display = 'flex';
    cached(url).then(function(j){
      row.innerHTML = '';
      ((j && j.metas) || []).filter(function(x){ return x.id !== id; }).slice(0, 14).forEach(function(x, i){ row.appendChild(posterCard(x, i)); });
      var first = row.querySelector('.f'); if (first) focus(first);
    });
  });
}
function hideEnded(){
  var box = document.getElementById('dEnd');
  if (!box || box.style.display === 'none') return false;
  box.style.display = 'none';
  focus(document.getElementById('dPlay'));
  return true;
}

/* ---------- abertura e encerramento (anime) pelo AniSkip ---------- */
/* IMDb → MyAnimeList (ARM) → tempos no AniSkip. Devolve [{op:[s,e], ed:[s,e], len}] em segundos;
   o player escolhe a entrada com a duração do episódio. */
function fetchSkips(d, v){
  var m = d.meta, genres = m.genres || m.genre || [];
  if (d.type !== 'series' || genres.indexOf('Animation') < 0 || !v) return Promise.resolve([]);
  var ep = v.episode || v.number;
  var work = cached('https://arm.haglund.dev/api/v2/imdb?id=' + d.id + '&include=myanimelist').then(function(list){
    // Uma entrada do MAL por temporada (na ordem do ARM): usar só a da temporada certa,
    // senão S2E5 pode receber os tempos de S1E5 (durações quase iguais).
    var all = (list || []).map(function(x){ return x.myanimelist; }).filter(Boolean);
    var si = (v.season || 1) - 1;
    var ids = all.length === 1 ? all : all[si] ? [all[si]] : [];
    return Promise.all(ids.map(function(mal){
      return cached('https://api.aniskip.com/v2/skip-times/' + mal + '/' + ep + '?types=op&types=ed&types=mixed-op&types=mixed-ed&episodeLength=0').then(function(r){
        if (!r || !r.found) return null;
        var out = {op: null, ed: null, len: 0};
        (r.results || []).forEach(function(x){
          var iv = [x.interval.startTime, x.interval.endTime];
          if (/op/.test(x.skipType)) out.op = iv; else if (/ed/.test(x.skipType)) out.ed = iv;
          out.len = x.episodeLength || out.len;
        });
        return out;
      });
    }));
  }).then(function(all){ return (all || []).filter(Boolean); });
  // Anime sem dados no AniSkip: um marcador vazio avisa o player para descobrir pela legenda.
  var mark = function(l){ return l && l.length ? l : [{anime: true}]; };
  return Promise.race([work, new Promise(function(r){ setTimeout(function(){ r([]); }, 6000); })]).then(mark, function(){ return mark([]); });
}
