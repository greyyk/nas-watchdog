'use strict';

/* ---------- helpers ---------- */
const $ = (id) => document.getElementById(id);
const $$ = (sel) => Array.from(document.querySelectorAll(sel));
const esc = (v) => String(v ?? '')
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

const fmtBytes = (b, d = 1) => {
  b = Number(b);
  if (!isFinite(b) || b < 0) return '—';
  const u = ['B', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB'];
  let i = 0;
  while (b >= 1024 && i < u.length - 1) { b /= 1024; i++; }
  return `${b.toFixed(b >= 100 || i === 0 ? 0 : d)} ${u[i]}`;
};
const fmtRate = (b) => `${fmtBytes(b)}/s`;
const fmtNum = (v, d = 1) => (v === null || v === undefined || !isFinite(v)) ? '—' : Number(v).toFixed(d);
const fmtPct = (v, d = 1) => `${fmtNum(v, d)}%`;
const fmtDur = (s) => {
  s = Math.max(0, Math.floor(s || 0));
  const d = Math.floor(s / 86400), h = Math.floor((s % 86400) / 3600), m = Math.floor((s % 3600) / 60);
  if (d) return `${d}d ${h}h`;
  if (h) return `${h}h ${m}m`;
  if (m) return `${m}m`;
  return `${s}s`;
};
const fmtTime = (t) => t ? new Date(t).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '—';
const fmtDateTime = (t) => t ? new Date(t).toLocaleString() : '—';
const titleCase = (s) => String(s || '').replace(/(^|\s|_)\S/g, c => c.toUpperCase()).replace(/_/g, ' ');

const sevClass = (s) => s === 'critical' ? 'error' : s === 'warning' ? 'warning' : 'info';
const sevBadge = (s) => `<span class="badge badge-${sevClass(s)} badge-sm">${esc(s)}</span>`;
const okBadge = (ok, txt) => `<span class="badge ${ok ? 'badge-success' : 'badge-error'} badge-sm">${esc(txt)}</span>`;

function progressClass(pct) { return pct >= 95 ? 'progress-error' : pct >= 85 ? 'progress-warning' : 'progress-primary'; }
function tempClass(t) {
  if (t.crit > 0 && t.c >= t.crit) return 'progress-error';
  if (t.warn > 0 && t.c >= t.warn) return 'progress-warning';
  return 'progress-success';
}

function findTemp(prefix) {
  const list = (stats?.temps || []).filter(t => t.label.toLowerCase().startsWith(prefix.toLowerCase()));
  if (!list.length) return null;
  return list.reduce((a, b) => a.c > b.c ? a : b);
}
function maxTempOf(keys) {
  const list = (stats?.temps || []).filter(t => keys.some(k => t.key === k));
  if (!list.length) return null;
  return list.reduce((a, b) => a.c > b.c ? a : b);
}

/* ---------- icons ---------- */
const ICONS = {
  pulse: 'M3 12h4l2.5 7 4-14 3 7H21',
  cpu: 'M9 3v2M15 3v2M9 19v2M15 19v2M3 9h2M3 15h2M19 9h2M19 15h2M7 7h10v10H7z',
  drive: 'M4 5h16v6H4zM4 13h16v6H4zM7 8h.01M7 16h.01',
  net: 'M12 21v-7M5 14a7 7 0 0 1 14 0M8.5 14a3.5 3.5 0 0 1 7 0M12 3v4',
  box: 'M12 3l8 4.5v9L12 21l-8-4.5v-9zM12 12l8-4.5M12 12L4 7.5M12 12v9',
  file: 'M14 3H6v18h12V7zM14 3v4h4M9 12h6M9 16h6',
  bell: 'M18 8a6 6 0 1 0-12 0c0 7-3 8-3 8h18s-3-1-3-8M10 21h4',
  bolt: 'M13 2L4 14h6l-1 8 9-12h-6z',
  gear: 'M12 15.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1A1.7 1.7 0 0 0 9 19.4a1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1A1.7 1.7 0 0 0 4.6 9a1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z',
  more: 'M5 12h.01M12 12h.01M19 12h.01',
};
const icon = (name, cls = 'w-4 h-4') =>
  `<svg class="${cls}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="${ICONS[name] || ICONS.pulse}"/></svg>`;

/* ---------- state ---------- */
const ROUTES = [
  { id: 'overview', label: 'Overview', icon: 'pulse' },
  { id: 'system', label: 'System', icon: 'cpu' },
  { id: 'storage', label: 'Storage', icon: 'drive' },
  { id: 'network', label: 'Network', icon: 'net' },
  { id: 'containers', label: 'Containers', icon: 'box' },
  { id: 'logs', label: 'Logs', icon: 'file' },
  { id: 'alerts', label: 'Alerts', icon: 'bell' },
  { id: 'actions', label: 'Actions', icon: 'bolt' },
  { id: 'settings', label: 'Settings', icon: 'gear' },
];

const app = {
  page: 'overview',
  stats: null,
  hist: [],
  events: [],
  token: localStorage.getItem('wd_token') || '',
  range: localStorage.getItem('wd_range') || '1h',
  refreshMs: Number(localStorage.getItem('wd_refresh') || 10000),
  log: { source: 'journal', lines: 300, q: '', auto: false },
  pendingAction: null,
  procQ: '',
  ctrQ: '',
};
let stats = null, hist = [], events = [];

const charts = [];
function clearCharts() {
  while (charts.length) { try { charts.pop().destroy(); } catch (_) {} }
}

/* ---------- api ---------- */
async function api(path, opts = {}) {
  let url = path;
  if (app.token) url += (url.includes('?') ? '&' : '?') + 'token=' + encodeURIComponent(app.token);
  const res = await fetch(url, { cache: 'no-store', ...opts });
  if (res.status === 401) {
    $('modal-token').showModal();
    throw new Error('unauthorized');
  }
  if (!res.ok) throw new Error('HTTP ' + res.status);
  return res.json();
}

async function refresh(silent = true) {
  try {
    const s = await api('/api/v1/stats');
    stats = app.stats = s;
    const h = await api('/api/v1/history?range=' + encodeURIComponent(app.range));
    hist = app.hist = h.samples || [];
    setStatus(true, 'live · ' + fmtTime(new Date()));
    if (app.page !== 'logs') render();
    else if ($('log-body')) updateLogMeta();
    else render();
  } catch (e) {
    if (!silent) toast('error', e.message);
    setStatus(false, e.message === 'unauthorized' ? 'locked' : 'offline');
  }
}

async function loadEvents() {
  try {
    const e = await api('/api/v1/events');
    events = app.events = (e.events || []).slice().reverse();
  } catch (_) {}
}

function setStatus(ok, text) {
  const cls = ok ? 'status-success' : 'status-error';
  ['top-dot', 'side-dot'].forEach(id => { const el = $(id); if (el) el.className = 'status ' + cls; });
  const t = $('top-status'); if (t) t.textContent = text;
  const st = $('side-status'); if (st) st.textContent = text;
}

function toast(kind, msg) {
  const el = document.createElement('div');
  el.className = `alert alert-${kind} py-2 text-sm shadow-lg`;
  el.innerHTML = `<span>${esc(msg)}</span>`;
  $('toasts').appendChild(el);
  setTimeout(() => el.remove(), 5000);
}

/* ---------- charts ---------- */
function spark(id, values, color) {
  const el = $(id);
  if (!el) return;
  let data = values.filter(v => v !== null && v !== undefined);
  if (!data.length) data = [0, 0];
  if (data.length === 1) data = [data[0], data[0]];
  const u = new uPlot({
    width: el.clientWidth || 220, height: 40,
    padding: [2, 0, 0, 0],
    legend: { show: false },
    cursor: { show: false },
    scales: { x: { time: false }, y: { auto: true } },
    axes: [{ show: false }, { show: false }],
    series: [{}, { stroke: color, width: 1.5, fill: color + '20', points: { show: false } }],
  }, [data.map((_, i) => i), data], el);
  charts.push(u);
}

const AXIS = {
  stroke: '#94a3b8', grid: { stroke: 'rgba(148,163,184,.12)' },
  ticks: { stroke: 'rgba(148,163,184,.22)' }, font: '11px ui-monospace,monospace',
};

function tsData() { return hist.map(s => new Date(s.ts).getTime() / 1000); }
function line(label, color, getter, fill) {
  return {
    label, stroke: color, width: 1.7, points: { show: false },
    fill: fill ? color + '18' : undefined,
    value: (u, v) => v == null ? '--' : Number(v).toFixed(1),
    _get: getter,
  };
}

function timeChart(id, lines, opts = {}) {
  const el = $(id);
  if (!el) return;
  const xs = tsData();
  if (xs.length < 2) {
    el.innerHTML = '<div class="text-xs opacity-40 py-12 text-center">collecting history…</div>';
    return;
  }
  const data = [xs, ...lines.map(l => hist.map(l._get))];
  const series = [{}, ...lines.map(({ _get, ...rest }) => rest)];
  const u = new uPlot({
    width: el.clientWidth || 600, height: opts.height || 210,
    padding: [10, 8, 0, 8],
    cursor: { drag: { x: true, y: false } },
    legend: { show: true },
    scales: { x: { time: true } },
    axes: [AXIS, { ...AXIS, values: (_u, splits) => splits.map(v => fmtNum(v, 0) + (opts.unit || '')) }],
    series,
  }, data, el);
  charts.push(u);
  new ResizeObserver(() => u.setSize({ width: el.clientWidth || 600, height: opts.height || 210 })).observe(el);
}

/* ---------- shell ---------- */
function renderNav() {
  const nav = $('side-nav');
  if (nav) {
    nav.innerHTML = ROUTES.map(r => `
      <a href="#/${r.id}" class="flex items-center gap-3 px-3 py-2 rounded-lg text-sm
        ${app.page === r.id ? 'bg-primary/15 text-primary font-medium' : 'opacity-70 hover:bg-base-200'}">
        ${icon(r.icon)}<span>${r.label}</span>
        ${r.id === 'alerts' && stats?.alerts?.length ? `<span class="badge badge-error badge-xs ml-auto">${stats.alerts.length}</span>` : ''}
      </a>`).join('');
  }
  const bottom = $('bottom-nav');
  if (bottom) {
    const primary = ROUTES.filter(r => ['overview', 'system', 'storage', 'containers'].includes(r.id));
    bottom.innerHTML = `<div class="flex bg-base-100 border-t border-base-content/10">
      ${primary.map(r => `<a href="#/${r.id}" class="flex-1 flex flex-col items-center gap-0.5 py-2 text-[10px]
        ${app.page === r.id ? 'text-primary' : 'opacity-60'}">${icon(r.icon, 'w-5 h-5')}<span>${r.label}</span></a>`).join('')}
      <button id="more-btn" class="flex-1 flex flex-col items-center gap-0.5 py-2 text-[10px] opacity-60">${icon('more', 'w-5 h-5')}<span>More</span></button>
    </div>`;
    $('more-btn')?.addEventListener('click', () => {
      const rest = ROUTES.filter(r => !['overview', 'system', 'storage', 'containers'].includes(r.id));
      $('more-list').innerHTML = rest.map(r => `<a href="#/${r.id}" class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm hover:bg-base-200" data-close>
        ${icon(r.icon)}<span>${r.label}</span></a>`).join('');
      $('more-list').querySelectorAll('a').forEach(a => a.addEventListener('click', () => $('modal-more').close()));
      $('modal-more').showModal();
    });
  }
}

function go(page) {
  app.page = page;
  render();
  window.scrollTo(0, 0);
}

function render() {
  clearCharts();
  renderNav();
  const s = stats;
  if (!s) {
    $('view').innerHTML = `<div class="flex items-center justify-center py-32 opacity-60"><span class="loading loading-spinner"></span></div>`;
    return;
  }
  const html = PAGES[app.page] ? PAGES[app.page](s) : PAGES.overview(s);
  $('view').innerHTML = html;
  if (PAGE_AFTER[app.page]) PAGE_AFTER[app.page](s);
  $('side-host').textContent = `${s.host.hostname} · ${s.host.os || s.host.kernel}`;
  $('top-host').textContent = `${s.host.hostname} · up ${fmtDur(s.host.uptime_s)}`;
  $('side-version').textContent = `watchdog v${s.watchdog?.version || ''}`;
}

/* ---------- shared blocks ---------- */
function verdict(s) {
  const crit = (s.alerts || []).some(a => a.severity === 'critical') || s.pool.read_only;
  const warn = (s.alerts || []).some(a => a.severity === 'warning');
  if (crit) return { level: 'critical', score: 25, label: 'ALERT', text: 'Attention required', color: 'text-error' };
  if (warn) return { level: 'warning', score: 66, label: 'WARN', text: 'Running with warnings', color: 'text-warning' };
  return { level: 'ok', score: 100, label: 'OK', text: 'All systems healthy', color: 'text-success' };
}

function hero(s) {
  const v = verdict(s);
  const reasons = [];
  if (s.pool.read_only) reasons.push('pool is read-only');
  (s.alerts || []).slice(0, 3).forEach(a => reasons.push(a.title.toLowerCase()));
  if (!reasons.length) reasons.push(`pool writable`, `${(s.containers || []).filter(c => c.state === 'running').length} containers running`);
  return `
  <div class="card bg-base-100 border border-base-content/10">
    <div class="card-body p-4 md:p-5 flex flex-col sm:flex-row sm:items-center gap-4">
      <div class="radial-progress ${v.color} shrink-0" style="--value:${v.score};--size:4.4rem;--thickness:7px">
        <span class="text-sm font-bold">${v.label}</span>
      </div>
      <div class="flex-1 min-w-0">
        <div class="text-xl md:text-2xl font-semibold">${v.text}</div>
        <div class="text-sm opacity-60 truncate">${esc(reasons.join(' · '))}</div>
      </div>
      <div class="grid grid-cols-3 gap-5 text-right shrink-0">
        <div><div class="text-[10px] uppercase tracking-widest opacity-40">Uptime</div><div class="num font-semibold">${fmtDur(s.host.uptime_s)}</div></div>
        <div><div class="text-[10px] uppercase tracking-widest opacity-40">Alerts</div><div class="num font-semibold ${(s.alerts || []).length ? 'text-error' : ''}">${(s.alerts || []).length}</div></div>
        <div><div class="text-[10px] uppercase tracking-widest opacity-40">Load</div><div class="num font-semibold">${fmtNum(s.host.load1, 2)}</div></div>
      </div>
    </div>
  </div>`;
}

function alertsBlock(s) {
  const list = s.alerts || [];
  if (!list.length) return '';
  return `<div class="space-y-2">${list.map(a => `
    <div role="alert" class="alert alert-${sevClass(a.severity)} py-2">
      <div class="min-w-0">
        <div class="text-sm font-semibold">${esc(a.title)}</div>
        <div class="text-xs opacity-80 break-words">${esc(a.detail)}</div>
      </div>
    </div>`).join('')}</div>`;
}

function vital(label, value, sub, sparkId, color) {
  return `<div class="card bg-base-100 border border-base-content/10">
    <div class="card-body p-3 gap-1">
      <div class="flex items-baseline justify-between">
        <span class="text-[10px] uppercase tracking-widest opacity-40">${label}</span>
        <span class="text-[10px] opacity-40 truncate ml-2">${esc(sub || '')}</span>
      </div>
      <div class="text-2xl font-semibold num leading-none">${value}</div>
      ${sparkId ? `<div class="h-10 -mx-1" id="${sparkId}"></div>` : ''}
    </div>
  </div>`;
}

function poolBadge(s) {
  const p = s.pool;
  if (p.read_only) return '<span class="badge badge-error badge-sm">READ-ONLY</span>';
  if (p.corruption_total > 0) return '<span class="badge badge-warning badge-sm">DEGRADED</span>';
  return '<span class="badge badge-success badge-sm">HEALTHY</span>';
}

function poolCard(s) {
  const p = s.pool;
  return `
  <div class="card bg-base-100 border border-base-content/10">
    <div class="card-body p-4">
      <div class="flex items-center gap-2">
        <span class="text-[10px] uppercase tracking-widest opacity-40">Pool</span>
        ${poolBadge(s)}
        <span class="text-[10px] opacity-40 ml-auto truncate">${esc(p.device)} → ${esc(p.host_path)}</span>
      </div>
      <div class="flex justify-between items-baseline mt-2">
        <span class="text-lg font-semibold num">${fmtBytes(p.used_bytes)} <span class="text-sm opacity-40">/ ${fmtBytes(p.total_bytes)}</span></span>
        <span class="num text-sm">${fmtPct(p.used_pct)}</span>
      </div>
      <div class="h-1.5 rounded-full bg-base-200 overflow-hidden mt-1">
        <div class="h-full" style="width:${Math.min(100, p.used_pct || 0)}%;background:${(p.used_pct || 0) >= 95 ? '#ff5c5c' : (p.used_pct || 0) >= 85 ? '#f5b942' : '#3ddc97'}"></div>
      </div>
      <div class="grid grid-cols-3 gap-2 mt-3 text-center">
        <div class="rounded-box bg-base-200/50 py-2">
          <div class="text-[9px] uppercase tracking-wider opacity-40">corruption</div>
          <div class="num font-semibold ${p.corruption_total ? 'text-warning' : ''}">${p.corruption_total}</div>
        </div>
        <div class="rounded-box bg-base-200/50 py-2">
          <div class="text-[9px] uppercase tracking-wider opacity-40">scrub</div>
          <div class="font-semibold text-xs">${esc(s.btrfs?.scrub?.status || 'never')}</div>
        </div>
        <div class="rounded-box bg-base-200/50 py-2">
          <div class="text-[9px] uppercase tracking-wider opacity-40">cache hit</div>
          <div class="num font-semibold">${fmtNum(s.bcache?.hit_ratio, 0)}%</div>
        </div>
      </div>
    </div>
  </div>`;
}

function containerSummary(s) {
  const list = s.containers || [];
  const running = list.filter(c => c.state === 'running');
  const down = list.filter(c => c.state !== 'running');
  const unhealthy = list.filter(c => c.health === 'unhealthy');
  return `
  <div class="card bg-base-100 border border-base-content/10">
    <div class="card-body p-4">
      <div class="flex items-center gap-2">
        <span class="text-[10px] uppercase tracking-widest opacity-40">Containers</span>
        <span class="badge badge-ghost badge-sm ml-auto">${running.length}/${list.length}</span>
      </div>
      <div class="mt-2 space-y-1">
        ${unhealthy.map(c => `<div class="flex items-center gap-2 text-xs"><span class="badge badge-error badge-xs">unhealthy</span>${esc(c.name)}</div>`).join('')}
        ${down.slice(0, 4).map(c => `<div class="flex items-center gap-2 text-xs"><span class="badge badge-ghost badge-xs">${esc(c.state)}</span>${esc(c.name)}</div>`).join('')}
        ${!down.length && !unhealthy.length ? '<div class="text-xs opacity-50">All containers running</div>' : ''}
      </div>
    </div>
  </div>`;
}

function storageHealth(s) {
  const raid = s.raid || [];
  const smart = s.smart || [];
  const pass = smart.filter(m => m.health === 'PASSED').length;
  const hottest = smart.filter(m => m.temperature > 0).sort((a, b) => b.temperature - a.temperature)[0];
  return `
  <div class="card bg-base-100 border border-base-content/10">
    <div class="card-body p-4">
      <div class="flex items-center gap-2">
        <span class="text-[10px] uppercase tracking-widest opacity-40">Storage health</span>
      </div>
      <div class="mt-2 space-y-1.5 text-xs">
        ${raid.map(r => `<div class="flex justify-between"><span class="opacity-60">${esc(r.name)} ${esc(r.level)}</span>${r.degraded ? '<span class="badge badge-error badge-xs">degraded</span>' : `<span class="badge badge-success badge-xs">${esc(r.members || 'ok')}</span>`}</div>`).join('')}
        <div class="flex justify-between"><span class="opacity-60">SMART</span><span class="num">${pass}/${smart.length} passed</span></div>
        ${hottest ? `<div class="flex justify-between"><span class="opacity-60">hottest disk</span><span class="num">${fmtNum(hottest.temperature, 0)}°C · ${esc(hottest.device)}</span></div>` : ''}
        <div class="flex justify-between"><span class="opacity-60">EDAC errors</span><span class="num">${s.edac?.corrected || 0} CE / ${s.edac?.uncorrected || 0} UE</span></div>
        <div class="flex justify-between"><span class="opacity-60">throttle events</span><span class="num ${s.cpu?.throttle?.package_count ? 'text-warning' : ''}">${s.cpu?.throttle?.package_count || 0}</span></div>
      </div>
    </div>
  </div>`;
}

function eventsCard() {
  const list = events.slice(0, 8);
  return `
  <div class="card bg-base-100 border border-base-content/10">
    <div class="card-body p-4">
      <div class="text-[10px] uppercase tracking-widest opacity-40">Recent events</div>
      <div class="mt-2 space-y-1">
        ${list.length ? list.map(e => `<div class="flex items-center gap-2 text-xs">
          <span class="badge badge-xs ${e.kind === 'fired' ? 'badge-error' : 'badge-success'}">${esc(e.kind)}</span>
          <span class="truncate">${esc(e.alert.title)}</span>
          <span class="ml-auto opacity-40 shrink-0">${fmtTime(e.alert.since)}</span>
        </div>`).join('') : '<div class="text-xs opacity-50">No events yet</div>'}
      </div>
    </div>
  </div>`;
}

/* ---------- pages ---------- */
const PAGES = {};
const PAGE_AFTER = {};

PAGES.overview = (s) => {
  const cpuT = maxTempOf(['cpu']) || findTemp('CPU');
  const dimmT = findTemp('DIMM');
  const net = (s.net || []).filter(n => !n.virtual);
  const rx = net.reduce((a, n) => a + n.rx_rate, 0) + (s.net || []).filter(n => n.virtual).reduce((a, n) => a + n.rx_rate, 0);
  const tx = (s.net || []).reduce((a, n) => a + n.tx_rate, 0);
  const diskRead = (s.disks || []).filter(d => !/^(md|dm-|bcache)/.test(d.name)).reduce((a, d) => a + d.read_rate, 0);
  const diskWrite = (s.disks || []).filter(d => !/^(md|dm-|bcache)/.test(d.name)).reduce((a, d) => a + d.write_rate, 0);

  return `
  <div class="space-y-4">
    ${hero(s)}
    ${alertsBlock(s)}
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-3">
      ${vital('CPU', fmtPct(s.cpu.usage_pct), `${s.cpu.cores || s.cpu.threads} threads`, 'sp-cpu', '#60a5fa')}
      ${vital('Memory', fmtPct(s.memory.used_pct), `${fmtBytes(s.memory.used)} / ${fmtBytes(s.memory.total)}`, 'sp-mem', '#a78bfa')}
      ${vital('Pool', fmtPct(s.pool.used_pct), fmtBytes(s.pool.used_bytes), 'sp-pool', s.pool.read_only ? '#ff5c5c' : '#3ddc97')}
      ${vital('Load', fmtNum(s.host.load1, 2), `${fmtNum(s.host.load5, 2)} / ${fmtNum(s.host.load15, 2)}`, 'sp-load', '#f5b942')}
      ${vital('CPU temp', cpuT ? fmtNum(cpuT.c, 1) + '°C' : 'n/a', cpuT?.label || '', 'sp-cput', '#22d3ee')}
      ${vital('DIMM temp', dimmT ? fmtNum(dimmT.c, 1) + '°C' : 'n/a', dimmT?.label || '', 'sp-dimm', '#f472b6')}
      ${vital('Network', fmtRate(rx), 'rx · ' + fmtRate(tx) + ' tx', 'sp-net', '#34d399')}
      ${vital('Disk I/O', fmtRate(diskRead), fmtRate(diskWrite) + ' write', 'sp-io', '#fbbf24')}
    </div>
    <div class="grid lg:grid-cols-3 gap-4">
      ${poolCard(s)}
      ${containerSummary(s)}
      ${storageHealth(s)}
    </div>
    ${eventsCard()}
  </div>`;
};

PAGE_AFTER.overview = (s) => {
  const h = hist;
  const cpuT = maxTempOf(['cpu']);
  const dimmT = findTemp('DIMM');
  spark('sp-cpu', h.map(x => x.cpu || 0), '#60a5fa');
  spark('sp-mem', h.map(x => x.mem_pct || 0), '#a78bfa');
  spark('sp-pool', h.map(x => x.used_pct || 0), '#3ddc97');
  spark('sp-load', h.map(x => x.load1 || 0), '#f5b942');
  spark('sp-cput', h.map(x => (x.temps || {}).cpu ?? null).filter(v => v !== null), '#22d3ee');
  spark('sp-dimm', h.map(x => (x.temps || {}).dimm0 ?? null).filter(v => v !== null), '#f472b6');
  spark('sp-net', h.map(x => (x.net_rx || 0) + (x.net_tx || 0)), '#34d399');
  spark('sp-io', h.map(x => (x.disk_read || 0) + (x.disk_write || 0)), '#fbbf24');
};

PAGES.system = (s) => {
  const c = s.cpu || {};
  const m = s.memory || {};
  const cores = c.per_core_detail || [];
  const memPct = m.used_pct || 0;
  const cachedPct = m.total ? (m.cached / m.total) * 100 : 0;
  const bufPct = m.total ? (m.buffers / m.total) * 100 : 0;
  return `
  <div class="space-y-4">
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-3">
      ${vital('CPU', fmtPct(c.usage_pct), `${c.threads} threads · ${esc(c.governor || '')}`, 's-cpu', '#60a5fa')}
      ${vital('Power', fmtNum(s.power?.package_watts, 1) + ' W', 'package', 's-pwr', '#f5b942')}
      ${vital('Throttle events', String(c.throttle?.package_count ?? 0), 'since boot', 's-thr', '#ff5c5c')}
      ${vital('GPU', fmtNum((s.gpu || [])[0]?.freq_mhz, 0) + ' MHz', `${fmtNum((s.gpu || [])[0]?.rc6_ms, 0)} ms rc6`, 's-gpu', '#22d3ee')}
    </div>

    <div class="card bg-base-100 border border-base-content/10">
      <div class="card-body p-4">
        <div class="flex items-center gap-3">
          <span class="text-[10px] uppercase tracking-widest opacity-40">CPU & Memory</span>
          <div class="ml-auto flex gap-1">
            ${['5m','1h','6h','24h'].map(r => `<button class="btn btn-xs ${app.range === r ? 'btn-primary' : 'btn-ghost'}" data-range="${r}">${r}</button>`).join('')}
          </div>
        </div>
        <div id="chart-cpu"></div>
      </div>
    </div>

    <div class="card bg-base-100 border border-base-content/10">
      <div class="card-body p-4">
        <div class="text-[10px] uppercase tracking-widest opacity-40">CPU cores</div>
        <div class="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-6 gap-2 mt-3">
          ${cores.map(core => `<div class="rounded-box bg-base-200/40 p-2">
            <div class="flex justify-between text-[10px] opacity-50"><span>cpu${core.id}</span><span class="num">${fmtNum(core.freq_mhz, 0)} MHz</span></div>
            <div class="flex items-center gap-2 mt-1">
              <progress class="progress ${progressClass(core.usage)} h-1.5 flex-1" value="${core.usage}" max="100"></progress>
              <span class="num text-[11px] w-9 text-right">${fmtNum(core.usage, 0)}%</span>
            </div>
          </div>`).join('') || '<div class="text-xs opacity-50">no per-core data</div>'}
        </div>
        <div class="grid md:grid-cols-2 gap-4 mt-4">
          <div>
            <div class="text-[10px] uppercase tracking-widest opacity-40 mb-2">C-states (cpu0)</div>
            <table class="table table-xs">
              <thead><tr><th>State</th><th class="text-right">count</th><th class="text-right">residency</th><th class="text-right">latency</th></tr></thead>
              <tbody>${(c.c_states || []).map(st => `<tr><td>${esc(st.name)}</td><td class="text-right num">${st.count}</td><td class="text-right num">${fmtDur(st.time_ms / 1000)}</td><td class="text-right num">${st.latency_us}µs</td></tr>`).join('')}</tbody>
            </table>
          </div>
          <div>
            <div class="text-[10px] uppercase tracking-widest opacity-40 mb-2">Power</div>
            <table class="table table-xs">
              <tbody>${Object.entries(s.power?.parts || {}).map(([k, v]) => `<tr><td class="opacity-60">${esc(k)}</td><td class="text-right num">${fmtNum(v, 2)} W</td></tr>`).join('')}
                <tr><td class="opacity-60">package total</td><td class="text-right num font-semibold">${fmtNum(s.power?.package_watts, 2)} W</td></tr>
              </tbody>
            </table>
            <div class="text-[10px] uppercase tracking-widest opacity-40 mb-2 mt-4">GPU</div>
            ${(s.gpu || []).map(g => `<div class="text-xs flex justify-between"><span class="opacity-60">${esc(g.card)}</span><span class="num">${g.freq_mhz} / ${g.max_mhz} MHz · rc6 ${fmtDur(g.rc6_ms / 1000)}</span></div>`).join('') || '<div class="text-xs opacity-50">no GPU data</div>'}
          </div>
        </div>
      </div>
    </div>

    <div class="grid lg:grid-cols-2 gap-4">
      <div class="card bg-base-100 border border-base-content/10">
        <div class="card-body p-4">
          <div class="text-[10px] uppercase tracking-widest opacity-40">Memory</div>
          <div class="flex h-4 rounded overflow-hidden mt-3 bg-base-200">
            <div style="width:${memPct}%;background:#60a5fa"></div>
            <div style="width:${cachedPct}%;background:#34d399"></div>
            <div style="width:${bufPct}%;background:#fbbf24"></div>
          </div>
          <div class="grid grid-cols-2 gap-x-4 gap-y-1 text-xs mt-3">
            ${[['used', fmtBytes(m.used)], ['available', fmtBytes(m.available)], ['cached', fmtBytes(m.cached)], ['buffers', fmtBytes(m.buffers)], ['dirty', fmtBytes(m.dirty)], ['writeback', fmtBytes(m.writeback)], ['slab', fmtBytes(m.slab)], ['swap used', fmtBytes(m.swap_used)]].map(([k, v]) => `<div class="flex justify-between"><span class="opacity-60">${k}</span><span class="num">${v}</span></div>`).join('')}
          </div>
          <div class="grid grid-cols-2 gap-x-4 gap-y-1 text-xs mt-3 pt-3 border-t border-base-content/10">
            <div class="flex justify-between"><span class="opacity-60">oom kills</span><span class="num">${m.oom_kills}</span></div>
            <div class="flex justify-between"><span class="opacity-60">major faults</span><span class="num">${m.major_faults}</span></div>
            <div class="flex justify-between"><span class="opacity-60">swap</span><span class="num">${fmtBytes(m.swap_total)}</span></div>
            <div class="flex justify-between"><span class="opacity-60">swap pct</span><span class="num">${fmtPct(m.swap_pct)}</span></div>
          </div>
        </div>
      </div>

      <div class="card bg-base-100 border border-base-content/10">
        <div class="card-body p-4">
          <div class="text-[10px] uppercase tracking-widest opacity-40">Pressure (PSI) · avg10 / avg60 / avg300</div>
          ${[['CPU', s.pressure?.cpu], ['Memory', s.pressure?.memory], ['IO', s.pressure?.io]].map(([label, p]) => `
            <div class="mt-3">
              <div class="flex justify-between text-xs"><span class="opacity-60">${label}</span><span class="num">${fmtNum(p?.some10, 2)} / ${fmtNum(p?.some60, 2)} / ${fmtNum(p?.some300, 2)}</span></div>
              <progress class="progress ${progressClass((p?.some300 || 0) * 10)} h-1" value="${Math.min(100, (p?.some300 || 0) * 10)}" max="100"></progress>
            </div>`).join('')}
        </div>
      </div>
    </div>

    <div class="card bg-base-100 border border-base-content/10">
      <div class="card-body p-4">
        <div class="flex flex-wrap items-center gap-3">
          <span class="text-[10px] uppercase tracking-widest opacity-40">Processes</span>
          <input id="proc-q" class="input input-xs input-bordered ml-auto w-44" placeholder="filter…" value="${esc(app.procQ)}">
        </div>
        <div class="scroll-x mt-2">
          <table class="table table-xs table-zebra" id="proc-table">
            <thead><tr><th class="text-right">PID</th><th>Name</th><th>State</th><th class="text-right">CPU%</th><th class="text-right">MEM%</th><th class="text-right">RSS</th><th class="text-right">Thr</th><th>Command</th></tr></thead>
            <tbody>${(s.processes || []).map(p => `<tr>
              <td class="text-right num">${p.pid}</td><td class="font-medium">${esc(p.name)}</td><td class="opacity-60">${esc(p.state)}</td>
              <td class="text-right num ${p.cpu_pct > 50 ? 'text-warning' : ''}">${fmtNum(p.cpu_pct, 1)}</td>
              <td class="text-right num">${fmtNum(p.mem_pct, 1)}</td><td class="text-right num">${fmtBytes(p.rss)}</td>
              <td class="text-right num">${p.threads}</td><td class="opacity-40 font-mono text-[10px] max-w-[360px] truncate">${esc(p.cmdline)}</td>
            </tr>`).join('')}</tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="grid lg:grid-cols-2 gap-4">
      <div class="card bg-base-100 border border-base-content/10">
        <div class="card-body p-4">
          <div class="text-[10px] uppercase tracking-widest opacity-40">Kernel & system</div>
          <div class="grid grid-cols-2 gap-x-4 gap-y-1 text-xs mt-2">
            ${[['kernel', s.host.kernel], ['modules', s.kernel?.module_count], ['ntp', s.system?.ntp_synced ? 'synced' : 'not synced'], ['timezone', s.system?.timezone], ['failed units', (s.system?.failed_units || []).length], ['context switches', s.system?.ctxt], ['processes started', s.system?.processes], ['running / blocked', `${s.system?.procs_running} / ${s.system?.procs_blocked}`]].map(([k, v]) => `<div class="flex justify-between"><span class="opacity-60">${k}</span><span class="num truncate ml-2">${esc(v)}</span></div>`).join('')}
          </div>
          ${(s.system?.failed_units || []).length ? `<div class="mt-2 text-xs text-warning">${(s.system.failed_units).map(esc).join(', ')}</div>` : ''}
          <div class="text-[10px] uppercase tracking-widest opacity-40 mt-4">EDAC</div>
          <div class="text-xs flex justify-between"><span class="opacity-60">corrected / uncorrected</span><span class="num">${s.edac?.corrected || 0} / ${s.edac?.uncorrected || 0}</span></div>
          <div class="text-[10px] uppercase tracking-widest opacity-40 mt-4">Power supplies</div>
          ${(s.power_supplies || []).map(p => `<div class="text-xs flex justify-between"><span class="opacity-60">${esc(p.name)}</span><span class="num">${esc(p.type)} · online ${esc(p.online)}</span></div>`).join('') || '<div class="text-xs opacity-50">none</div>'}
        </div>
      </div>
      <div class="card bg-base-100 border border-base-content/10">
        <div class="card-body p-4">
          <div class="text-[10px] uppercase tracking-widest opacity-40">Thermal zones</div>
          <div class="grid grid-cols-2 gap-x-4 gap-y-1 text-xs mt-2">
            ${(s.thermal_zones || []).map(t => `<div class="flex justify-between"><span class="opacity-60">${esc(t.label)}</span><span class="num">${fmtNum(t.c, 1)}°C</span></div>`).join('') || '<div class="opacity-50">none</div>'}
          </div>
          <div class="text-[10px] uppercase tracking-widest opacity-40 mt-4">Fans</div>
          ${(s.fans || []).map(f => `<div class="flex justify-between text-xs"><span class="opacity-60">${esc(f.label)}</span><span class="num">${f.rpm} rpm${f.pwm ? ' · pwm ' + f.pwm : ''}</span></div>`).join('') || '<div class="text-xs opacity-50">none</div>'}
        </div>
      </div>
      <div class="card bg-base-100 border border-base-content/10">
        <div class="card-body p-4">
          <div class="text-[10px] uppercase tracking-widest opacity-40">Temperatures</div>
          <div class="mt-2 space-y-2">
            ${(s.temps || []).map(t => `<div>
              <div class="flex justify-between text-xs"><span class="opacity-60">${esc(t.label)}</span><span class="num">${fmtNum(t.c, 1)}°C</span></div>
              <progress class="progress ${tempClass(t)} h-1" value="${Math.min(100, t.c)}" max="100"></progress>
            </div>`).join('')}
          </div>
        </div>
      </div>
    </div>
  </div>`;
};

PAGE_AFTER.system = () => {
  timeChart('chart-cpu', [
    line('CPU %', '#60a5fa', x => x.cpu || 0, true),
    line('MEM %', '#a78bfa', x => x.mem_pct || 0, true),
    line('Load ×10', '#f5b942', x => (x.load1 || 0) * 10, false),
  ], { unit: '%' });
  const q = $('proc-q');
  if (q) q.addEventListener('input', () => {
    app.procQ = q.value;
    const term = q.value.toLowerCase();
    $$('#proc-table tbody tr').forEach(tr => {
      tr.style.display = tr.textContent.toLowerCase().includes(term) ? '' : 'none';
    });
  });
};

PAGES.storage = (s) => {
  const p = s.pool || {};
  const df = s.btrfs?.df || {};
  const sc = s.btrfs?.scrub || {};
  const b = s.bcache || {};
  const stats = p.device_stats || {};
  return `
  <div class="space-y-4">
    <div class="card bg-base-100 border border-base-content/10">
      <div class="card-body p-4">
        <div class="flex flex-wrap items-center gap-3">
          <span class="text-[10px] uppercase tracking-widest opacity-40">Pool</span>
          ${poolBadge(s)}
          <span class="text-xs opacity-50">${esc(p.device)} → ${esc(p.host_path)}</span>
          <span class="ml-auto text-xs opacity-50">${s.subvolumes} subvolumes</span>
        </div>
        <div class="flex items-center gap-4 mt-3">
          <div class="radial-progress ${s.pool.read_only ? 'text-error' : 'text-success'}" style="--value:${p.used_pct || 0};--size:3.4rem;--thickness:6px"><span class="text-[10px] num">${fmtNum(p.used_pct, 0)}%</span></div>
          <div class="flex-1">
            <div class="flex justify-between text-sm"><span class="num">${fmtBytes(p.used_bytes)}</span><span class="num opacity-50">${fmtBytes(p.total_bytes)}</span></div>
            <div class="h-2 rounded-full bg-base-200 overflow-hidden mt-1">
              <div class="h-full" style="width:${Math.min(100, p.used_pct || 0)}%;background:${(p.used_pct || 0) >= 95 ? '#ff5c5c' : (p.used_pct || 0) >= 85 ? '#f5b942' : '#3ddc97'}"></div>
            </div>
            <div class="text-xs opacity-50 mt-1">${fmtBytes(p.free_bytes)} free</div>
          </div>
        </div>
        <div class="grid grid-cols-2 md:grid-cols-5 gap-2 mt-4">
          ${['corruption_errs','write_io_errs','read_io_errs','flush_io_errs','generation_errs'].map(k => `
            <div class="rounded-box bg-base-200/50 p-2">
              <div class="text-[9px] uppercase tracking-wider opacity-50">${k.replace(/_/g,' ')}</div>
              <div class="num text-lg ${k === 'corruption_errs' && stats[k] ? 'text-warning' : ''}">${stats[k] ?? 0}</div>
            </div>`).join('')}
        </div>
      </div>
    </div>

    <div class="grid lg:grid-cols-2 gap-4">
      <div class="card bg-base-100 border border-base-content/10">
        <div class="card-body p-4">
          <div class="text-[10px] uppercase tracking-widest opacity-40">btrfs allocation</div>
          <table class="table table-xs mt-2">
            <thead><tr><th>Type</th><th>Profile</th><th class="text-right">Total</th><th class="text-right">Used</th><th class="text-right">%</th></tr></thead>
            <tbody>${Object.entries(df).map(([k, v]) => {
              const pc = v.total ? (v.used / v.total) * 100 : 0;
              return `<tr><td>${esc(k)}</td><td class="opacity-50">${esc(v.profile)}</td><td class="text-right num">${fmtBytes(v.total)}</td><td class="text-right num">${fmtBytes(v.used)}</td><td class="text-right num">${fmtNum(pc, 1)}%</td></tr>`;
            }).join('') || '<tr><td colspan="5" class="opacity-50">unavailable</td></tr>'}</tbody>
          </table>
          <div class="text-[10px] uppercase tracking-widest opacity-40 mt-4">bcache</div>
          <div class="grid grid-cols-2 gap-x-4 gap-y-1 text-xs mt-2">
            <div class="flex justify-between"><span class="opacity-60">mode</span><span class="num">${esc(b.cache_mode || '—')}</span></div>
            <div class="flex justify-between"><span class="opacity-60">state</span><span class="num">${esc(b.state || '—')}</span></div>
            <div class="flex justify-between"><span class="opacity-60">hit ratio</span><span class="num">${fmtNum(b.hit_ratio, 1)}%</span></div>
            <div class="flex justify-between"><span class="opacity-60">available</span><span class="num">${fmtNum(b.available_percent, 0)}%</span></div>
            <div class="flex justify-between"><span class="opacity-60">hits</span><span class="num">${b.cache_hits}</span></div>
            <div class="flex justify-between"><span class="opacity-60">misses</span><span class="num">${b.cache_misses}</span></div>
          </div>
        </div>
      </div>

      <div class="card bg-base-100 border border-base-content/10">
        <div class="card-body p-4">
          <div class="flex items-center gap-2">
            <span class="text-[10px] uppercase tracking-widest opacity-40">Scrub</span>
            <div class="ml-auto flex gap-1">
              <button class="btn btn-xs btn-primary" data-act="scrub_start">start</button>
              <button class="btn btn-xs btn-ghost" data-act="scrub_cancel">cancel</button>
            </div>
          </div>
          <div class="grid grid-cols-2 gap-x-4 gap-y-1 text-xs mt-2">
            <div class="flex justify-between"><span class="opacity-60">status</span><span>${esc(sc.status || '—')}</span></div>
            <div class="flex justify-between"><span class="opacity-60">started</span><span>${esc(sc.started || '—')}</span></div>
            <div class="flex justify-between"><span class="opacity-60">duration</span><span class="num">${fmtDur(sc.duration_s)}</span></div>
            <div class="flex justify-between"><span class="opacity-60">progress</span><span class="num">${fmtNum(sc.progress_pct, 1)}%</span></div>
            <div class="flex justify-between"><span class="opacity-60">csum errors</span><span class="num ${sc.csum_errors ? 'text-error' : ''}">${sc.csum_errors || 0}</span></div>
            <div class="flex justify-between"><span class="opacity-60">uncorrectable</span><span class="num ${sc.uncorrectable_errors ? 'text-error' : ''}">${sc.uncorrectable_errors || 0}</span></div>
            <div class="flex justify-between"><span class="opacity-60">corrected</span><span class="num">${sc.corrected_errors || 0}</span></div>
            <div class="flex justify-between"><span class="opacity-60">read errors</span><span class="num">${sc.read_errors || 0}</span></div>
          </div>
          <progress class="progress progress-primary w-full h-1.5 mt-2" value="${sc.progress_pct || 0}" max="100"></progress>
        </div>
      </div>
    </div>

    <div class="card bg-base-100 border border-base-content/10">
      <div class="card-body p-4">
        <div class="text-[10px] uppercase tracking-widest opacity-40">Disks</div>
        <div class="scroll-x mt-2">
          <table class="table table-xs table-zebra">
            <thead><tr><th>Disk</th><th>Model</th><th class="text-right">Size</th><th class="text-right">Read</th><th class="text-right">Write</th><th class="text-right">IOPS</th><th class="text-right">Util</th><th>Scheduler</th></tr></thead>
            <tbody>${(s.disks || []).map(d => `<tr>
              <td class="font-medium">${esc(d.name)}</td><td class="opacity-50 max-w-[180px] truncate">${esc(d.model)}</td>
              <td class="text-right num">${fmtBytes(d.size_bytes)}</td>
              <td class="text-right num">${fmtRate(d.read_rate)}</td><td class="text-right num">${fmtRate(d.write_rate)}</td>
              <td class="text-right num">${fmtNum(d.read_iops, 1)} / ${fmtNum(d.write_iops, 1)}</td>
              <td class="text-right num ${d.util_pct > 90 ? 'text-warning' : ''}">${fmtNum(d.util_pct, 0)}%</td>
              <td class="opacity-50 text-[10px]">${esc(d.scheduler || '')}</td></tr>`).join('')}</tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="space-y-3">
      ${(s.smart || []).map(m => `
      <div class="card bg-base-100 border border-base-content/10">
        <div class="card-body p-4">
          <div class="flex flex-wrap items-center gap-2">
            <span class="font-semibold">${esc(m.device)}</span>
            ${m.error ? '<span class="badge badge-ghost badge-sm">n/a</span>' : `<span class="badge ${m.health === 'PASSED' ? 'badge-success' : 'badge-error'} badge-sm">${esc(m.health)}</span>`}
            <span class="text-xs opacity-50">${esc(m.model)} · ${esc(m.serial)}</span>
            <span class="ml-auto text-xs num">${fmtNum(m.temperature, 0)}°C · ${Math.round(m.power_on_hours / 24)}d power-on</span>
          </div>
          <div class="grid grid-cols-2 md:grid-cols-5 gap-2 mt-3 text-center">
            ${[['realloc', m.reallocated], ['pending', m.pending], ['uncorrect', m.uncorrectable], ['crc', m.crc_errors], [m.percentage_used ? 'wear %' : 'media err', m.percentage_used || m.media_errors]].map(([k, v]) => `<div class="rounded-box bg-base-200/40 py-1.5"><div class="text-[9px] uppercase opacity-40">${k}</div><div class="num font-semibold text-sm">${v || 0}</div></div>`).join('')}
          </div>
          ${(m.table || []).length ? `<details class="mt-2"><summary class="cursor-pointer text-xs opacity-50">SMART attributes (${m.table.length})</summary>
            <div class="scroll-x mt-2"><table class="table table-xs">
              <thead><tr><th class="text-right">ID</th><th>Name</th><th class="text-right">Value</th><th class="text-right">Worst</th><th class="text-right">Thresh</th><th class="text-right">Raw</th><th>Failed</th></tr></thead>
              <tbody>${m.table.map(t => `<tr class="${t.failed ? 'text-error' : ''}"><td class="text-right num">${t.id}</td><td>${esc(t.name)}</td><td class="text-right num">${t.value}</td><td class="text-right num">${t.worst}</td><td class="text-right num">${t.thresh}</td><td class="text-right num">${esc(t.raw_text || t.raw_value)}</td><td>${esc(t.failed || '')}</td></tr>`).join('')}</tbody>
            </table></div></details>` : ''}
        </div>
      </div>`).join('')}
    </div>

    <div class="card bg-base-100 border border-base-content/10">
      <div class="card-body p-4">
        <details>
          <summary class="cursor-pointer text-[10px] uppercase tracking-widest opacity-40">Mounts (${(s.mounts || []).length})</summary>
          <div class="scroll-x mt-3">
            <table class="table table-xs table-zebra">
              <thead><tr><th>Target</th><th>Device</th><th>FS</th><th>Mode</th><th class="text-right">Used</th><th class="text-right">Total</th><th class="text-right">%</th></tr></thead>
              <tbody>${(s.mounts || []).map(m => `<tr>
                <td class="font-mono text-[10px]">${esc(m.target)}</td><td class="font-mono text-[10px] opacity-50">${esc(m.device)}</td>
                <td class="opacity-50">${esc(m.fstype)}</td>
                <td>${m.ro ? '<span class="badge badge-error badge-xs">ro</span>' : '<span class="badge badge-ghost badge-xs">rw</span>'}</td>
                <td class="text-right num">${fmtBytes(m.used)}</td><td class="text-right num">${fmtBytes(m.total)}</td><td class="text-right num">${fmtNum(m.used_pct, 1)}</td></tr>`).join('')}</tbody>
            </table>
          </div>
        </details>
      </div>
    </div>
  </div>`;
};

PAGES.network = (s) => {
  const phys = (s.net || []).filter(n => !n.virtual);
  const virt = (s.net || []).filter(n => n.virtual);
  const ns = s.net_stats || {};
  const ifaceRow = (n) => `<tr>
    <td class="font-medium">${esc(n.name)}</td>
    <td>${n.state === 'up' ? '<span class="badge badge-success badge-xs">up</span>' : `<span class="badge badge-ghost badge-xs">${esc(n.state || '?')}</span>`}</td>
    <td class="text-right num">${n.speed_mbps ? n.speed_mbps + ' Mb' : '—'}</td>
    <td class="text-right num">${fmtRate(n.rx_rate)}</td><td class="text-right num">${fmtRate(n.tx_rate)}</td>
    <td class="text-right num">${fmtBytes(n.rx_bytes)}</td><td class="text-right num">${fmtBytes(n.tx_bytes)}</td>
    <td class="text-right num">${n.rx_errs} / ${n.tx_errs}</td><td class="text-right num">${n.rx_drops} / ${n.tx_drops}</td>
    <td class="font-mono text-[10px] opacity-50">${esc(n.mac || '')}</td></tr>`;
  return `
  <div class="space-y-4">
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-3">
      ${vital('Established', String(ns.curr_estab ?? 0), 'TCP connections', 'n-est', '#3ddc97')}
      ${vital('Conntrack', String(ns.conntrack_used ?? 0), 'max ' + (ns.conntrack_max ?? 0), 'n-ct', '#60a5fa')}
      ${vital('Retransmits', fmtPct(ns.retrans_pct, 3), `${ns.retrans_segs || 0} segs`, 'n-ret', '#f5b942')}
      ${vital('Sockets', String(ns.sockets_used ?? 0), `${ns.tcp_in_use || 0} tcp · ${ns.tcp_time_wait || 0} tw`, 'n-sock', '#a78bfa')}
    </div>

    <div class="card bg-base-100 border border-base-content/10">
      <div class="card-body p-4">
        <div class="text-[10px] uppercase tracking-widest opacity-40">Throughput</div>
        <div id="chart-net"></div>
      </div>
    </div>

    <div class="card bg-base-100 border border-base-content/10">
      <div class="card-body p-4">
        <div class="text-[10px] uppercase tracking-widest opacity-40">Physical interfaces</div>
        <div class="scroll-x mt-2">
          <table class="table table-xs table-zebra">
            <thead><tr><th>Interface</th><th>State</th><th class="text-right">Speed</th><th class="text-right">RX/s</th><th class="text-right">TX/s</th><th class="text-right">RX total</th><th class="text-right">TX total</th><th class="text-right">Errors</th><th class="text-right">Drops</th><th>MAC</th></tr></thead>
            <tbody>${phys.map(ifaceRow).join('')}</tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="grid lg:grid-cols-2 gap-4">
      <div class="card bg-base-100 border border-base-content/10">
        <div class="card-body p-4">
          <div class="text-[10px] uppercase tracking-widest opacity-40">TCP stack</div>
          <div class="grid grid-cols-2 gap-x-4 gap-y-1 text-xs mt-2">
            ${[['established', ns.curr_estab], ['active opens', ns.active_opens], ['passive opens', ns.passive_opens], ['in segs', ns.in_segs], ['out segs', ns.out_segs], ['retrans segs', ns.retrans_segs], ['in errs', ns.in_errs], ['out rsts', ns.out_rsts], ['listen overflows', ns.listen_overflows], ['listen drops', ns.listen_drops], ['ARP entries', ns.arp_entries], ['routes', ns.routes]].map(([k, v]) => `<div class="flex justify-between"><span class="opacity-60">${k}</span><span class="num">${v ?? 0}</span></div>`).join('')}
          </div>
        </div>
      </div>
      <div class="card bg-base-100 border border-base-content/10">
        <div class="card-body p-4">
          <div class="text-[10px] uppercase tracking-widest opacity-40">Virtual interfaces</div>
          <div class="scroll-x mt-2 max-h-80 overflow-y-auto">
            <table class="table table-xs">
              <thead><tr><th>Interface</th><th>State</th><th class="text-right">RX/s</th><th class="text-right">TX/s</th><th class="text-right">RX total</th></tr></thead>
              <tbody>${virt.map(n => `<tr><td class="font-mono text-[10px]">${esc(n.name)}</td><td class="opacity-50">${esc(n.state || '?')}</td><td class="text-right num">${fmtRate(n.rx_rate)}</td><td class="text-right num">${fmtRate(n.tx_rate)}</td><td class="text-right num">${fmtBytes(n.rx_bytes)}</td></tr>`).join('')}</tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </div>`;
};

PAGE_AFTER.network = () => {
  timeChart('chart-net', [
    line('RX MB/s', '#34d399', x => (x.net_rx || 0) / 1048576, true),
    line('TX MB/s', '#fbbf24', x => (x.net_tx || 0) / 1048576, true),
  ], { unit: 'M' });
};

PAGES.containers = (s) => {
  const list = s.containers || [];
  return `
  <div class="space-y-4">
    <div class="grid grid-cols-2 lg:grid-cols-5 gap-3">
      ${vital('Running', String(list.filter(c => c.state === 'running').length), '', 'c-run', '#3ddc97')}
      ${vital('Stopped', String(list.filter(c => c.state !== 'running').length), '', 'c-stop', '#ff5c5c')}
      ${vital('Unhealthy', String(list.filter(c => c.health === 'unhealthy').length), '', 'c-unh', '#f5b942')}
      ${vital('Images', String(s.docker?.images ?? 0), '', 'c-img', '#60a5fa')}
      ${vital('Engine', s.docker?.version || '—', s.docker?.storage_driver || '', 'c-ver', '#a78bfa')}
    </div>
    <div class="card bg-base-100 border border-base-content/10">
      <div class="card-body p-4">
        <div class="flex flex-wrap items-center gap-3">
          <span class="text-[10px] uppercase tracking-widest opacity-40">Containers</span>
          <input id="ctr-q" class="input input-xs input-bordered ml-auto w-48" placeholder="filter…" value="${esc(app.ctrQ)}">
        </div>
        <div class="scroll-x mt-2">
          <table class="table table-xs table-zebra" id="ctr-table">
            <thead><tr><th>Name</th><th>State</th><th class="text-right">CPU</th><th class="text-right">Memory</th><th class="text-right">Net rx/tx</th><th class="text-right">Block r/w</th><th>Ports</th><th>Actions</th></tr></thead>
            <tbody>${list.map(c => `<tr>
              <td class="font-medium">${esc(c.name)}</td>
              <td class="whitespace-nowrap">
                <span class="badge badge-xs ${c.state === 'running' ? (c.health === 'unhealthy' ? 'badge-warning' : 'badge-success') : 'badge-error'}">${esc(c.state)}</span>
                ${c.health && c.health !== 'none' ? `<span class="badge badge-xs ${c.health === 'healthy' ? 'badge-success' : 'badge-warning'} ml-1">${esc(c.health)}</span>` : ''}
              </td>
              <td class="text-right num">${fmtNum(c.cpu_pct, 1)}%</td>
              <td class="text-right num">${fmtBytes(c.mem_usage)}</td>
              <td class="text-right num">${fmtBytes(c.net_rx)} / ${fmtBytes(c.net_tx)}</td>
              <td class="text-right num">${fmtBytes(c.block_read)} / ${fmtBytes(c.block_write)}</td>
              <td class="text-[10px] opacity-50">${(c.ports || []).join(', ') || '—'}</td>
              <td class="whitespace-nowrap">
                <button class="btn btn-xs btn-ghost" data-act="container_restart" data-target="${esc(c.name)}" title="restart">↻</button>
                ${c.state === 'running'
                  ? `<button class="btn btn-xs btn-ghost text-error" data-act="container_stop" data-target="${esc(c.name)}" title="stop">■</button>`
                  : `<button class="btn btn-xs btn-ghost text-success" data-act="container_start" data-target="${esc(c.name)}" title="start">▶</button>`}
                <button class="btn btn-xs btn-ghost" data-logs="${esc(c.name)}" title="logs">logs</button>
              </td></tr>`).join('')}</tbody>
          </table>
        </div>
      </div>
    </div>
  </div>`;
};

PAGE_AFTER.containers = () => {
  const q = $('ctr-q');
  if (q) q.addEventListener('input', () => {
    app.ctrQ = q.value;
    const term = q.value.toLowerCase();
    $$('#ctr-table tbody tr').forEach(tr => {
      tr.style.display = tr.textContent.toLowerCase().includes(term) ? '' : 'none';
    });
  });
};

PAGES.logs = (s) => {
  const sources = (s.logs || []).map(l => ({ id: l.source, label: l.source, size: l.size, matches: l.matches, path: l.path }));
  const groups = [
    { name: 'System', items: [{ id: 'journal', label: 'journal (systemd)' }, { id: 'dmesg', label: 'dmesg (kernel)' }, ...sources.filter(x => !x.id.startsWith('zettos'))] },
    { name: 'ZettOS apps', items: sources.filter(x => x.id.startsWith('zettos')) },
  ];
  return `
  <div class="grid lg:grid-cols-[280px_1fr] gap-4">
    <div class="card bg-base-100 border border-base-content/10 h-fit">
      <div class="card-body p-3">
        <div class="text-[10px] uppercase tracking-widest opacity-40 px-1">Sources</div>
        ${groups.map(g => `<div class="mt-2">
          <div class="text-[10px] uppercase tracking-wider opacity-30 px-1">${g.name}</div>
          <div class="mt-1 space-y-0.5 max-h-72 overflow-y-auto">
            ${g.items.map(it => {
              const hits = Object.values(it.matches || {}).reduce((a, b) => a + b, 0);
              return `<button class="w-full text-left px-2 py-1.5 rounded text-xs ${app.log.source === it.id ? 'bg-primary/15 text-primary' : 'hover:bg-base-200'}" data-source="${esc(it.id)}">
                <span class="truncate block">${esc(it.label)}</span>
                ${hits ? `<span class="badge badge-warning badge-xs mt-0.5">${hits}</span>` : ''}
              </button>`;
            }).join('')}
          </div>
        </div>`).join('')}
      </div>
    </div>
    <div class="card bg-base-100 border border-base-content/10">
      <div class="card-body p-3 gap-3">
        <div class="flex flex-wrap items-end gap-2">
          <label class="flex-1 min-w-40">
            <span class="text-[10px] uppercase tracking-widest opacity-40 block mb-1">Filter (regex)</span>
            <input id="log-q" class="input input-sm input-bordered w-full" value="${esc(app.log.q)}" placeholder="BTRFS|error">
          </label>
          <label class="w-24">
            <span class="text-[10px] uppercase tracking-widest opacity-40 block mb-1">Lines</span>
            <input id="log-lines" type="number" min="10" max="2000" class="input input-sm input-bordered w-full" value="${app.log.lines}">
          </label>
          <button id="log-load" class="btn btn-sm btn-primary">load</button>
          <label class="label cursor-pointer gap-2">
            <span class="text-xs opacity-60">auto</span>
            <input id="log-auto" type="checkbox" class="toggle toggle-sm toggle-primary" ${app.log.auto ? 'checked' : ''}>
          </label>
          <button id="log-copy" class="btn btn-sm btn-ghost">copy</button>
        </div>
        <div id="log-meta" class="text-[10px] opacity-40"></div>
        <pre id="log-body" class="logview bg-base-200/50 rounded-box p-3"></pre>
      </div>
    </div>
  </div>`;
};

PAGE_AFTER.logs = () => {
  bindLogControls();
  loadLog();
};

PAGES.alerts = (s) => `
  <div class="space-y-4">
    <div class="card bg-base-100 border border-base-content/10">
      <div class="card-body p-4">
        <div class="text-[10px] uppercase tracking-widest opacity-40">Active alerts</div>
        <div class="mt-2 space-y-2">
          ${(s.alerts || []).map(a => `<div role="alert" class="alert alert-${sevClass(a.severity)} py-2"><div>
            <div class="text-sm font-semibold">${esc(a.title)}</div>
            <div class="text-xs opacity-80">${esc(a.detail)}</div>
            <div class="text-[10px] opacity-50 mt-1">since ${fmtDateTime(a.since)}</div>
          </div></div>`).join('') || '<div class="text-sm opacity-50">No active alerts.</div>'}
        </div>
      </div>
    </div>
    <div class="card bg-base-100 border border-base-content/10">
      <div class="card-body p-4">
        <div class="text-[10px] uppercase tracking-widest opacity-40">Event history</div>
        <div class="scroll-x mt-2">
          <table class="table table-xs table-zebra">
            <thead><tr><th>When</th><th>Kind</th><th>Severity</th><th>Alert</th></tr></thead>
            <tbody>${events.map(e => `<tr><td class="num opacity-60">${fmtDateTime(e.alert.since)}</td>
              <td><span class="badge badge-xs ${e.kind === 'fired' ? 'badge-error' : 'badge-success'}">${esc(e.kind)}</span></td>
              <td>${sevBadge(e.alert.severity)}</td><td>${esc(e.alert.title)}</td></tr>`).join('') || '<tr><td colspan="4" class="opacity-50">no events yet</td></tr>'}</tbody>
          </table>
        </div>
      </div>
    </div>
  </div>`;

PAGES.actions = (s) => `
  <div class="space-y-4">
    <div class="alert ${s.watchdog?.actions_enabled ? 'alert-info' : 'alert-warning'} py-2">
      <span class="text-sm">${s.watchdog?.actions_enabled
        ? 'Actions are enabled. Every action requires confirmation.'
        : 'Actions are disabled. Set WD_ALLOW_ACTIONS=true to enable them.'}</span>
    </div>
    <div class="grid md:grid-cols-2 gap-4">
      <div class="card bg-base-100 border border-base-content/10"><div class="card-body p-4">
        <div class="text-[10px] uppercase tracking-widest opacity-40">Filesystem</div>
        <p class="text-xs opacity-60 mt-1">Start a scrub to verify all data against checksums. Read-only; safe but I/O heavy.</p>
        <div class="flex gap-2 mt-3">
          <button class="btn btn-sm btn-primary" data-act="scrub_start">start scrub</button>
          <button class="btn btn-sm btn-ghost" data-act="scrub_cancel">cancel scrub</button>
        </div>
      </div></div>
      <div class="card bg-base-100 border border-base-content/10"><div class="card-body p-4">
        <div class="text-[10px] uppercase tracking-widest opacity-40">Host</div>
        <p class="text-xs opacity-60 mt-1">Reboot the NAS. Only safe recovery after a btrfs transaction abort.</p>
        <div class="flex gap-2 mt-3">
          <button class="btn btn-sm btn-error" data-act="reboot">reboot</button>
          <button class="btn btn-sm btn-ghost" data-act="reboot_cancel">cancel pending reboot</button>
        </div>
      </div></div>
    </div>
    <div class="card bg-base-100 border border-base-content/10"><div class="card-body p-4">
      <div class="text-[10px] uppercase tracking-widest opacity-40">Containers</div>
      <div class="mt-2 grid md:grid-cols-2 xl:grid-cols-3 gap-2">
        ${(s.containers || []).map(c => `<div class="flex items-center gap-2 rounded-box bg-base-200/40 px-3 py-2">
          <span class="text-sm truncate">${esc(c.name)}</span>
          <span class="badge badge-xs ${c.state === 'running' ? 'badge-success' : 'badge-error'}">${esc(c.state)}</span>
          <div class="ml-auto flex gap-1">
            <button class="btn btn-xs btn-ghost" data-act="container_restart" data-target="${esc(c.name)}">restart</button>
            ${c.state === 'running'
              ? `<button class="btn btn-xs btn-ghost text-error" data-act="container_stop" data-target="${esc(c.name)}">stop</button>`
              : `<button class="btn btn-xs btn-ghost text-success" data-act="container_start" data-target="${esc(c.name)}">start</button>`}
          </div>
        </div>`).join('')}
      </div>
    </div></div>
  </div>`;

PAGES.settings = (s) => `
  <div class="max-w-2xl space-y-4">
    <div class="card bg-base-100 border border-base-content/10"><div class="card-body p-4">
      <div class="text-[10px] uppercase tracking-widest opacity-40">Connection</div>
      <label class="form-control mt-2">
        <span class="label-text text-xs opacity-60 mb-1">API token (WD_API_TOKEN)</span>
        <input id="set-token" class="input input-bordered input-sm" type="password" value="${esc(app.token)}">
      </label>
      <div class="flex gap-2 mt-3">
        <button class="btn btn-sm btn-primary" id="set-save">save</button>
        <button class="btn btn-sm btn-ghost" id="set-clear">clear token</button>
      </div>
      <div class="text-xs opacity-50 mt-3">Endpoint: <span class="font-mono">${esc(location.origin)}</span></div>
    </div></div>
    <div class="card bg-base-100 border border-base-content/10"><div class="card-body p-4">
      <div class="text-[10px] uppercase tracking-widest opacity-40">Display</div>
      <label class="form-control mt-2">
        <span class="label-text text-xs opacity-60 mb-1">Theme</span>
        <select id="set-theme" class="select select-sm select-bordered">
          ${['dim','night','light'].map(t => `<option value="${t}" ${document.documentElement.dataset.theme === t ? 'selected' : ''}>${t}</option>`).join('')}
        </select>
      </label>
      <label class="form-control mt-3">
        <span class="label-text text-xs opacity-60 mb-1">Refresh interval</span>
        <select id="set-refresh" class="select select-sm select-bordered">
          ${[[5000,'5s'],[10000,'10s'],[30000,'30s'],[60000,'60s']].map(([ms, lbl]) => `<option value="${ms}" ${app.refreshMs === ms ? 'selected' : ''}>${lbl}</option>`).join('')}
        </select>
      </label>
    </div></div>
    <div class="card bg-base-100 border border-base-content/10"><div class="card-body p-4 text-xs opacity-60">
      <div class="flex justify-between"><span>watchdog</span><span class="num">v${esc(s.watchdog?.version)}</span></div>
      <div class="flex justify-between"><span>interval</span><span class="num">${s.watchdog?.interval_s}s</span></div>
      <div class="flex justify-between"><span>auto-reboot</span><span>${s.watchdog?.reboot_enabled ? 'enabled' : 'disabled'}</span></div>
      <div class="flex justify-between"><span>actions</span><span>${s.watchdog?.actions_enabled ? 'enabled' : 'disabled'}</span></div>
      <div class="flex justify-between"><span>host</span><span>${esc(s.host.hostname)} · ${esc(s.host.os)}</span></div>
    </div></div>
  </div>`;

/* ---------- actions ---------- */
function bindActions() {
  document.body.addEventListener('click', (e) => {
    const rangeBtn = e.target.closest('[data-range]');
    if (rangeBtn) {
      app.range = rangeBtn.dataset.range;
      localStorage.setItem('wd_range', app.range);
      refresh();
      return;
    }
    const actBtn = e.target.closest('[data-act]');
    if (actBtn) {
      const action = actBtn.dataset.act;
      const target = actBtn.dataset.target || '';
      confirmAction(action, target);
      return;
    }
    const logsBtn = e.target.closest('[data-logs]');
    if (logsBtn) {
      openContainerLogs(logsBtn.dataset.logs);
    }
  });
}

function actionLabel(action, target) {
  switch (action) {
    case 'container_restart': return `Restart container ${target}`;
    case 'container_stop': return `Stop container ${target}`;
    case 'container_start': return `Start container ${target}`;
    case 'scrub_start': return 'Start btrfs scrub';
    case 'scrub_cancel': return 'Cancel btrfs scrub';
    case 'reboot': return 'Reboot the NAS';
    case 'reboot_cancel': return 'Cancel pending auto-reboot';
    default: return action;
  }
}

function confirmAction(action, target) {
  app.pendingAction = { action, target };
  $('confirm-title').textContent = actionLabel(action, target);
  $('confirm-detail').textContent = 'This will run on the NAS immediately. Continue?';
  $('modal-confirm').showModal();
}

async function runAction() {
  const pending = app.pendingAction;
  if (!pending) return;
  $('modal-confirm').close();
  try {
    const res = await api('/api/v1/actions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: pending.action, target: pending.target, confirm: true }),
    });
    toast(res.ok ? 'success' : 'error', res.message || (res.ok ? 'done' : 'failed'));
  } catch (e) {
    toast('error', e.message);
  }
  setTimeout(() => refresh(false), 1500);
}

/* ---------- logs ---------- */
function bindLogControls() {
  const q = $('log-q');
  if (q) {
    q.value = app.log.q;
    q.addEventListener('keydown', e => { if (e.key === 'Enter') loadLog(); });
  }
  const lines = $('log-lines');
  if (lines) lines.value = app.log.lines;
  const auto = $('log-auto');
  if (auto) auto.checked = app.log.auto;
  const load = $('log-load');
  if (load) load.addEventListener('click', loadLog);
  const copy = $('log-copy');
  if (copy) copy.addEventListener('click', () => {
    navigator.clipboard?.writeText($('log-body').textContent || '');
    toast('success', 'copied');
  });
  $$('[data-source]').forEach(btn => {
    btn.addEventListener('click', () => {
      app.log.source = btn.dataset.source;
      render();
    });
  });
  if (auto) auto.addEventListener('change', () => { app.log.auto = auto.checked; });
}

async function loadLog() {
  const source = app.log.source;
  const lines = app.log.lines;
  const q = (document.getElementById('log-q')?.value ?? app.log.q).trim();
  app.log.q = q;
  const body = $('log-body');
  if (!body) return;
  body.textContent = 'loading…';
  try {
    let out = [];
    if (source === 'journal') {
      const r = await api(`/api/v1/journal?lines=${lines}` + (q ? `&q=${encodeURIComponent(q)}` : ''));
      out = r.lines || [];
    } else if (source === 'dmesg') {
      const r = await api(`/api/v1/dmesg?lines=${lines}` + (q ? `&q=${encodeURIComponent(q)}` : ''));
      out = r.lines || [];
    } else {
      const r = await api(`/api/v1/logs?source=${encodeURIComponent(source)}&lines=${lines}` + (q ? `&q=${encodeURIComponent(q)}` : ''));
      out = r.lines || [];
    }
    body.textContent = out.length ? out.join('\n') : '(no matching lines)';
    body.scrollTop = body.scrollHeight;
    const src = (stats?.logs || []).find(l => l.source === source);
    $('log-meta').textContent = src ? `${src.path} · ${fmtBytes(src.size)}` : source;
  } catch (e) {
    body.textContent = 'failed: ' + e.message;
  }
}

function updateLogMeta() {
  const src = (stats?.logs || []).find(l => l.source === app.log.source);
  const el = $('log-meta');
  if (el && src) el.textContent = `${src.path} · ${fmtBytes(src.size)}`;
}

async function openContainerLogs(name) {
  $('logs-title').textContent = name + ' · logs';
  $('logs-body').textContent = 'loading…';
  $('modal-logs').showModal();
  $('logs-refresh').onclick = () => openContainerLogs(name);
  try {
    const r = await api(`/api/v1/containers/logs?name=${encodeURIComponent(name)}&tail=400`);
    $('logs-body').textContent = r.text || '(no output)';
    $('logs-body').scrollTop = $('logs-body').scrollHeight;
  } catch (e) {
    $('logs-body').textContent = 'failed: ' + e.message;
  }
}

/* ---------- boot ---------- */
function bindShell() {
  $('btn-refresh').addEventListener('click', () => refresh(false));
  $('btn-theme').addEventListener('click', () => {
    const cur = document.documentElement.dataset.theme;
    const next = cur === 'dim' ? 'night' : cur === 'night' ? 'light' : 'dim';
    document.documentElement.dataset.theme = next;
    localStorage.setItem('wd_theme', next);
  });
  $('token-save').addEventListener('click', () => {
    app.token = $('token-input').value.trim();
    localStorage.setItem('wd_token', app.token);
    $('modal-token').close();
    refresh(false);
  });
  $('confirm-cancel').addEventListener('click', () => $('modal-confirm').close());
  $('confirm-ok').addEventListener('click', runAction);
  $('logs-close').addEventListener('click', () => $('modal-logs').close());
  window.addEventListener('hashchange', () => {
    const p = location.hash.replace(/^#\/?/, '');
    if (ROUTES.some(r => r.id === p)) { app.page = p; render(); window.scrollTo(0,0); }
  });
}

function initTheme() {
  const t = localStorage.getItem('wd_theme');
  if (t) document.documentElement.dataset.theme = t;
}

function seedTokenFromURL() {
  const params = new URLSearchParams(location.search);
  const t = params.get('token');
  if (t) {
    app.token = t;
    localStorage.setItem('wd_token', t);
    params.delete('token');
    const q = params.toString();
    history.replaceState(null, '', location.pathname + (q ? '?' + q : '') + location.hash);
  }
}

async function boot() {
  initTheme();
  seedTokenFromURL();
  bindShell();
  bindActions();
  const p = location.hash.replace(/^#\/?/, '');
  if (ROUTES.some(r => r.id === p)) app.page = p;
  renderNav();
  await loadEvents();
  await refresh(false);
  setInterval(() => {
    refresh();
    if (app.page === 'overview' || app.page === 'alerts') loadEvents().then(() => { if (app.page === 'alerts') render(); });
    if (app.page === 'logs' && app.log.auto) loadLog();
  }, app.refreshMs);
}

document.addEventListener('DOMContentLoaded', boot);
