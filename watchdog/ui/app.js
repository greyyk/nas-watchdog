'use strict';

/* ---------- DOM, formatting, and original line icons ---------- */
const M = window.WatchdogModel;
const $ = (id) => document.getElementById(id);
const esc = M.escapeHTML;
const text = (value, fallback = M.MISSING) => value === null || value === undefined || String(value) === '' ? fallback : String(value);
const fmtN = M.formatNumber;
const fmtI = M.formatInteger;
const fmtPct = M.formatPercent;
const fmtB = M.formatBytes;
const fmtR = M.formatRate;
const fmtT = M.formatTemperature;
const fmtDur = M.formatDuration;

const ICONS = {
  overview: 'M3 12h4l2.5 7 4-14 3 7h4.5',
  system: 'M9 3v2M15 3v2M9 19v2M15 19v2M3 9h2M3 15h2M19 9h2M19 15h2M7 7h10v10H7zM10 10h4v4h-4z',
  storage: 'M4 5h16v6H4zM4 13h16v6H4zM7 8h.01M7 16h.01M17 8h1M17 16h1',
  network: 'M12 20v-6M5 14a7 7 0 0 1 14 0M8.5 14a3.5 3.5 0 0 1 7 0M12 3v4M5 5l2 2M19 5l-2 2',
  containers: 'm12 3 8 4.5v9L12 21l-8-4.5v-9zM12 12l8-4.5M12 12 4 7.5M12 12v9',
  logs: 'M14 3H6v18h12V7zM14 3v4h4M9 12h6M9 16h6',
  alerts: 'M18 8a6 6 0 1 0-12 0c0 7-3 8-3 8h18s-3-1-3-8M10 21h4',
  actions: 'M13 2 4 14h6l-1 8 9-12h-6z',
  settings: 'M4 7h10M18 7h2M4 17h2M10 17h10M14 4v6M6 14v6',
  more: 'M5 12h.01M12 12h.01M19 12h.01',
  cpu: 'M9 3v2M15 3v2M9 19v2M15 19v2M3 9h2M3 15h2M19 9h2M19 15h2M7 7h10v10H7zM10 10h4v4h-4z',
  thermal: 'M14 14.8V5a4 4 0 0 0-8 0v9.8a6 6 0 1 0 8 0ZM10 7v9M10 18h.01M15 7h4M15 11h3',
  arrow: 'M5 12h14M13 6l6 6-6 6',
  back: 'M19 12H5M11 18l-6-6 6-6',
  copy: 'M8 8h12v12H8zM4 16V4h12',
  fan: 'M12 12c-5-6-1-10 3-7 2 2 0 5-3 7ZM12 12c6-5 10-1 7 3-2 2-5 0-7-3ZM12 12c5 6 1 10-3 7-2-2 0-5 3-7ZM12 12c-6 5-10 1-7-3 2-2 5 0 7 3Z',
};
function icon(name, className = '') {
  return `<svg class="${esc(className)}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="${ICONS[name] || ICONS.overview}"/></svg>`;
}

const ROUTES = {
  overview: { title: 'Overview', kicker: 'SERVER INSTRUMENT', icon: 'overview', subtitle: 'Current health and essential telemetry' },
  system: { title: 'System', kicker: 'HOST TELEMETRY', icon: 'system', subtitle: 'Compute, memory, pressure, and processes' },
  storage: { title: 'Storage', kicker: 'DATA INTEGRITY', icon: 'storage', subtitle: 'Pool, block devices, SMART, and mounts' },
  network: { title: 'Network', kicker: 'THROUGHPUT & STACK', icon: 'network', subtitle: 'Interfaces, throughput, sockets, and counters', back: '#/more', backLabel: 'More' },
  containers: { title: 'Containers', kicker: 'SERVICE INSTRUMENT', icon: 'containers', subtitle: 'Runtime state, resource usage, and output' },
  logs: { title: 'Logs', kicker: 'SYSTEM OUTPUT', icon: 'logs', subtitle: 'Journal, kernel, and configured log sources', back: '#/more', backLabel: 'More' },
  alerts: { title: 'Alerts', kicker: 'WATCHDOG SIGNALS', icon: 'alerts', subtitle: 'Active conditions and the server event buffer', back: '#/more', backLabel: 'More' },
  actions: { title: 'Actions', kicker: 'EXPLICIT HOST CONTROL', icon: 'actions', subtitle: 'Confirmed requests only; completion is never assumed', back: '#/more', backLabel: 'More' },
  settings: { title: 'Settings', kicker: 'LOCAL CONSOLE', icon: 'settings', subtitle: 'Connection, appearance, and refresh cadence', back: '#/more', backLabel: 'More' },
  more: { title: 'More', kicker: 'ALL INSTRUMENTS', icon: 'more', subtitle: 'Additional monitoring and console sections' },
  cpu: { title: 'CPU detail', kicker: 'COMPUTE INSTRUMENT', icon: 'cpu', subtitle: 'Utilisation history, thermal envelope, and per-thread load', back: '#/system', backLabel: 'System' },
  thermal: { title: 'Thermal detail', kicker: 'THERMAL INSTRUMENT', icon: 'thermal', subtitle: 'Sensors, thresholds, cooling, and keyed history', back: '#/system', backLabel: 'System' },
};
const MOBILE_ROUTES = ['overview', 'system', 'storage', 'containers', 'more'];
const SECONDARY_MORE_ROUTES = ['network', 'logs', 'alerts', 'actions', 'settings'];
const RANGES = ['5m', '1h', '6h', '24h'];
const STORE = {
  get(key, fallback = '') { try { return localStorage.getItem(key) ?? fallback; } catch (_) { return fallback; } },
  set(key, value) { try { localStorage.setItem(key, String(value)); return true; } catch (_) { return false; } },
  remove(key) { try { localStorage.removeItem(key); } catch (_) {} },
};

const storedRange = STORE.get('wd_range', '1h');
const storedRefresh = Number(STORE.get('wd_refresh', '10000'));
const app = {
  route: M.routeFromHash(location.hash),
  stats: null,
  hist: [],
  histRange: RANGES.includes(storedRange) ? storedRange : '1h',
  historyByRange: new Map(),
  events: [],
  token: STORE.get('wd_token', ''),
  range: RANGES.includes(storedRange) ? storedRange : '1h',
  refreshMs: [5000, 10000, 30000, 60000].includes(storedRefresh) ? storedRefresh : 10000,
  theme: ['dark', 'light', 'system'].includes(STORE.get('wd_theme', 'dark')) ? STORE.get('wd_theme', 'dark') : 'dark',
  status: 'connecting',
  statusDetail: 'Connecting',
  receivedAt: 0,
  error: '',
  historyError: '',
  eventsError: '',
  refreshing: false,
  rangeBusy: false,
  deferredRender: false,
  lastError: '',
  procQ: '',
  ctrQ: '',
  thermalKey: 'cpu',
  sampleVisible: { cpu: false, thermal: false },
  sampleLimits: { cpu: 100, thermal: 100 },
  pendingAction: null,
  actionBusy: false,
  actionMessage: '',
  log: {
    source: 'journal', lines: M.boundedLineLimit(STORE.get('wd_log_lines', '300')), q: '', auto: false,
    cache: new Map(), errors: new Map(), revision: 0, pendingKey: '', lastGoodKey: '',
  },
  containerLog: { name: '', text: '', error: '', revision: 0, pending: false },
};
const routeScroll = new Map();
let freshnessTimer = null;

/* ---------- shared render primitives ---------- */
function toneForSeverity(severity) {
  return severity === 'critical' ? 'danger' : severity === 'warning' ? 'warning' : 'info';
}
function tag(label, tone = '', extra = '') {
  return `<span class="tag ${esc(tone)}" ${extra}>${esc(label)}</span>`;
}
function healthTag(ok, good = 'Healthy', bad = 'Attention') {
  return tag(ok ? good : bad, ok ? 'good' : 'danger');
}
function sectionHeading(title, detail = '') {
  return `<div class="section-heading"><h2>${esc(title)}</h2>${detail ? `<p>${esc(detail)}</p>` : ''}</div>`;
}
function readout(label, value, supporting = '', options = {}) {
  const tone = ['danger', 'warning', 'good', 'info'].includes(options.tone) ? options.tone : '';
  const bar = options.percent === null || options.percent === undefined ? '' :
    `<div class="metric-bar ${tone === 'good' ? 'safe' : ''}" role="img" aria-label="${esc(label)} ${esc(value)}"><span style="--value:${M.clamp(options.percent, 0, 100)}%;--bar-color:var(--wd-${tone === 'danger' ? 'red' : tone === 'warning' ? 'amber' : tone === 'good' ? 'green' : 'blue'})"></span></div>`;
  return `<div class="readout"><div class="readout-label">${esc(label)}</div><div class="readout-value ${tone}">${esc(value)}</div><div class="readout-support">${esc(supporting)}</div>${bar}</div>`;
}
function readoutGrid(items, columns = 4) {
  const className = columns === 3 ? 'three' : columns === 4 ? 'four' : '';
  return `<div class="readout-grid ${className}">${items.map((item) => readout(...item)).join('')}</div>`;
}
function kv(label, value, wide = false) {
  return `<div class="kv${wide ? ' wide' : ''}"><dt>${esc(label)}</dt><dd>${esc(value)}</dd></div>`;
}
function kvGrid(entries) {
  return `<dl class="kv-grid">${entries.map(([label, value, wide]) => kv(label, value, wide)).join('')}</dl>`;
}
function meter(value, tone = 'blue', label = 'Usage') {
  const numeric = M.finite(value);
  if (numeric === null) return '<div class="metric-bar" aria-label="Usage not reported"><span style="--value:0%"></span></div>';
  return `<div class="metric-bar ${tone === 'green' ? 'safe' : ''}" role="img" aria-label="${esc(label)} ${esc(fmtPct(numeric))}"><span style="--value:${M.clamp(numeric, 0, 100)}%;--bar-color:var(--wd-${tone === 'red' ? 'red' : tone === 'amber' ? 'amber' : tone === 'green' ? 'green' : 'blue'})"></span></div>`;
}
function temperatureTone(sensor) {
  const state = M.sensorHealth(sensor);
  return state === 'critical' ? 'danger' : state === 'warning' ? 'warning' : '';
}
function poolTone(pool) {
  return pool && pool.read_only ? 'danger' : (pool && M.finite(pool.used_pct) >= 95) ? 'danger' : (pool && M.finite(pool.used_pct) >= 85) ? 'warning' : 'good';
}
function poolStatus(pool) {
  if (!pool) return tag(M.MISSING);
  if (pool.read_only) return tag('Read-only', 'danger');
  if (M.finite(pool.corruption_total) > 0) return tag('Errors reported', 'warning');
  return tag('Writable', 'good');
}
function alertCard(alert) {
  const tone = toneForSeverity(alert.severity);
  return `<article class="alert-card ${tone}" role="alert"><span class="alert-icon" aria-hidden="true">${alert.severity === 'critical' ? '!' : alert.severity === 'warning' ? '△' : 'i'}</span><div class="alert-body"><h3>${esc(text(alert.title, 'Watchdog alert'))}</h3><p>${esc(text(alert.detail))}</p><span class="alert-time">Active since ${esc(M.formatDateTime(alert.since))} · ${esc(text(alert.severity, 'info'))}</span></div></article>`;
}
function notice(title, detail, tone = 'warning') {
  return `<div class="notice ${esc(tone)}"><span class="notice-symbol" aria-hidden="true">${tone === 'danger' ? '!' : tone === 'good' ? '✓' : 'i'}</span><div><h2>${esc(title)}</h2><p>${esc(detail)}</p></div></div>`;
}
function rangeControls() {
  return `<div class="range-control" role="group" aria-label="History range">${RANGES.map((range) => `<button type="button" class="range-button" data-range="${range}" aria-pressed="${app.range === range}" ${app.rangeBusy ? 'disabled' : ''}>${range}</button>`).join('')}</div>`;
}
function emptyState(message, compact = false) {
  return `<p class="empty-state${compact ? ' compact' : ''}">${esc(message)}</p>`;
}
function chartSummary(summary, unit, formatter = (value) => `${fmtN(value)}${unit}`) {
  if (!summary.count) return '<div class="chart-summary">No valid samples in this range. Missing readings remain gaps.</div>';
  return `<div class="chart-summary"><span>Samples <strong>${summary.count}</strong></span><span>Latest <strong>${esc(formatter(summary.latest))}</strong></span><span>Minimum <strong>${esc(formatter(summary.minimum))}</strong></span><span>Average <strong>${esc(formatter(summary.average))}</strong></span><span>Peak <strong>${esc(formatter(summary.maximum))}</strong></span></div>`;
}
function sampleSection(kind, points, unit) {
  const valid = (points || []).filter((point) => point.value !== null).slice().reverse();
  const visible = !!app.sampleVisible[kind];
  const limit = app.sampleLimits[kind] || 100;
  const shown = valid.slice(0, limit);
  return `<div><button id="samples-${kind}-toggle" class="button secondary full-width" type="button" data-samples="${kind}" aria-expanded="${visible}" aria-controls="samples-${kind}" ${valid.length ? '' : 'disabled'}>${visible ? 'Hide timestamped samples' : 'Read timestamped samples'}</button><div id="samples-${kind}" ${visible ? '' : 'hidden'}>${visible ? `<p class="section-description">Latest exact samples first. Up to 100 are added at a time; missing readings are not fabricated.</p>${valid.length ? `<table class="sample-table"><caption>${valid.length} valid readings · ${esc(M.RANGE_LABELS[app.range])}</caption><thead><tr><th scope="col">Timestamp</th><th scope="col">Exact value</th></tr></thead><tbody>${shown.map((point) => `<tr><td>${esc(M.formatDateTime(point.epochMillis))}</td><td>${esc(fmtN(point.value))}${esc(unit)}</td></tr>`).join('')}</tbody></table>${shown.length < valid.length ? `<button id="samples-${kind}-more" class="button secondary full-width" type="button" data-more-samples="${kind}">Show 100 more · ${valid.length - shown.length} remaining</button>` : ''}` : emptyState('No valid timestamped samples are available.', true)}` : ''}</div></div>`;
}
function physicalDiskRates(stats) {
  const disks = (stats.disks || []).filter((disk) => !/^(md|dm-|bcache)/.test(String(disk.name || '')));
  if (!disks.length || disks.some((disk) => M.finite(disk.read_rate) === null || M.finite(disk.write_rate) === null)) return { read: null, write: null };
  return disks.reduce((totals, disk) => ({ read: totals.read + disk.read_rate, write: totals.write + disk.write_rate }), { read: 0, write: 0 });
}
function networkRates(stats, physicalOnly = false) {
  const interfaces = (stats.net || []).filter((iface) => !physicalOnly || !iface.virtual);
  if (!interfaces.length || interfaces.some((iface) => M.finite(iface.rx_rate) === null || M.finite(iface.tx_rate) === null)) return { rx: null, tx: null };
  return interfaces.reduce((totals, iface) => ({ rx: totals.rx + iface.rx_rate, tx: totals.tx + iface.tx_rate }), { rx: 0, tx: 0 });
}
function combinedRate(rates, first, second) {
  return M.finite(rates[first]) === null || M.finite(rates[second]) === null ? null : rates[first] + rates[second];
}

/* ---------- uPlot lifecycle; every observer and frame is released ---------- */
const chartRecords = [];
function themeColors() {
  const style = getComputedStyle(document.documentElement);
  const get = (name) => style.getPropertyValue(`--wd-${name}`).trim();
  return { text: get('text'), muted: get('muted'), line: get('line'), grid: get('grid'), green: get('green'), amber: get('amber'), red: get('red'), blue: get('blue'), bg: get('bg') };
}
function clearCharts() {
  while (chartRecords.length) {
    const record = chartRecords.pop();
    if (record.observer) record.observer.disconnect();
    if (record.frame) cancelAnimationFrame(record.frame);
    try { record.chart.destroy(); } catch (_) {}
  }
}
function chartData(lines) {
  const timestamps = M.historyPoints(app.hist, () => 0);
  const aligned = [timestamps.map((point) => point.epochMillis / 1000)];
  const bySource = new Map(timestamps.map((point, index) => [point.sourceIndex, index]));
  lines.forEach((definition) => {
    const values = Array(timestamps.length).fill(null);
    M.historyPoints(app.hist, definition.get).forEach((point) => {
      const index = bySource.get(point.sourceIndex);
      if (index !== undefined) values[index] = point.value;
    });
    aligned.push(values);
  });
  return aligned;
}
function timeChart(id, definitions, options = {}) {
  const element = $(id);
  if (!element) return;
  const data = chartData(definitions);
  const hasValues = data.slice(1).some((series) => series.some((value) => value !== null));
  if (data[0].length < 2 || !hasValues || typeof window.uPlot !== 'function') {
    element.innerHTML = `<div class="chart-empty">${data[0].length < 2 ? 'Collecting timestamped history… at least two samples are required.' : 'No valid samples are reported for this series in the selected range.'}</div>`;
    return;
  }
  const colors = themeColors();
  const compact = !!options.compact;
  const height = options.height || (compact ? 70 : 230);
  const axis = {
    stroke: colors.muted,
    grid: { stroke: colors.grid, width: 1 },
    ticks: { stroke: colors.line },
    font: '11px ui-monospace, SFMono-Regular, Menlo, monospace',
  };
  const thresholds = options.thresholds || [];
  const allValues = data.slice(1).flat().filter((value) => value !== null).concat(thresholds.map((threshold) => threshold.value));
  let yRange;
  if (options.yRange) {
    yRange = () => options.yRange;
  } else {
    yRange = () => {
      const minimum = allValues.length ? Math.min(...allValues) : 0;
      const maximum = allValues.length ? Math.max(...allValues) : 1;
      const lower = options.zeroMin === false ? minimum - 2 : Math.min(0, minimum);
      return [lower, maximum === lower ? lower + 1 : maximum + (options.headroom ?? 4)];
    };
  }
  const plugins = thresholds.length ? [{
    hooks: {
      draw: [(chart) => {
        const context = chart.ctx;
        const ratio = window.devicePixelRatio || 1;
        let labelRow = chart.bbox.top + 7 * ratio;
        context.save();
        [...thresholds].sort((a, b) => a.value - b.value).forEach((threshold) => {
          const y = Math.round(chart.valToPos(threshold.value, 'y', true));
          if (y < chart.bbox.top || y > chart.bbox.top + chart.bbox.height) return;
          context.strokeStyle = threshold.tone === 'critical' ? colors.red : colors.amber;
          context.fillStyle = context.strokeStyle;
          context.lineWidth = ratio;
          context.setLineDash([5 * ratio, 4 * ratio]);
          context.beginPath();
          context.moveTo(chart.bbox.left, y);
          context.lineTo(chart.bbox.left + chart.bbox.width, y);
          context.stroke();
          if (!compact) {
            context.font = `${10 * ratio}px ui-monospace, monospace`;
            context.textAlign = 'right';
            const labelY = Math.max(y - 7 * ratio, labelRow + 10 * ratio);
            context.fillText(threshold.label, chart.bbox.left + chart.bbox.width - 8 * ratio, labelY);
            labelRow = labelY + 12 * ratio;
          }
        });
        context.restore();
      }],
    },
  }] : [];
  const chart = new window.uPlot({
    width: Math.max(120, element.clientWidth || 600), height,
    padding: compact ? [4, 0, 0, 0] : [12, 10, 0, 8],
    legend: { show: !compact && definitions.length > 0, live: !compact },
    cursor: compact ? { show: false } : { drag: { x: true, y: false }, focus: { prox: 24 } },
    scales: { x: { time: true }, y: { auto: false, range: yRange } },
    axes: compact ? [{ show: false }, { show: false }] : [axis, { ...axis, values: (_chart, splits) => splits.map((value) => `${fmtN(value, 0)}${options.unit || ''}`) }],
    series: [{}, ...definitions.map((definition) => ({
      label: definition.label,
      stroke: definition.color === 'green' ? colors.green : definition.color === 'amber' ? colors.amber : definition.color === 'red' ? colors.red : definition.color === 'text' ? colors.text : colors.blue,
      width: compact ? 1.6 : 1.8,
      spanGaps: false,
      points: { show: false },
      fill: definition.fill ? `${colors.blue}16` : undefined,
      value: (_chart, value) => value === null ? M.MISSING : `${fmtN(value)}${options.unit || ''}`,
    }))],
    plugins,
  }, data, element);
  const record = { chart, observer: null, frame: 0 };
  if (typeof ResizeObserver === 'function') {
    record.observer = new ResizeObserver(() => {
      if (record.frame) cancelAnimationFrame(record.frame);
      record.frame = requestAnimationFrame(() => {
        record.frame = 0;
        if (!element.isConnected) return;
        chart.setSize({ width: Math.max(120, element.clientWidth || 600), height });
      });
    });
    record.observer.observe(element);
  }
  chartRecords.push(record);
}

/* ---------- polling and JSON API ---------- */
class APIError extends Error {
  constructor(message, status = 0) { super(message); this.name = 'APIError'; this.status = status; }
}
let tokenDialogOpen = false;
async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  headers.set('Accept', 'application/json');
  if (app.token) headers.set('Authorization', `Bearer ${app.token}`);
  const response = await fetch(path, { cache: 'no-store', ...options, headers });
  const raw = await response.text();
  let payload = null;
  if (raw) {
    try { payload = JSON.parse(raw); } catch (_) { payload = null; }
  }
  if (response.status === 401) {
    setConnection('locked', 'API token required');
    if (!tokenDialogOpen) {
      tokenDialogOpen = true;
      $('token-error').textContent = 'Enter a valid token to reconnect.';
      $('modal-token').showModal();
      requestAnimationFrame(() => $('token-input').focus());
    }
    throw new APIError('API token required or no longer valid.', 401);
  }
  if (!response.ok) {
    const serverMessage = payload && (payload.error || payload.message);
    const plainMessage = !payload && raw && !/^\s*</.test(raw) ? raw.trim().slice(0, 500) : '';
    throw new APIError(text(serverMessage || plainMessage, `Request failed with HTTP ${response.status}.`), response.status);
  }
  if (!payload || typeof payload !== 'object') throw new APIError('Watchdog returned an invalid JSON response.', response.status);
  return payload;
}

let pollTimer = null;
let refreshActive = false;
let refreshQueued = false;
let refreshRevision = 0;
let appliedRevision = 0;

function updateRefreshControls() {
  const button = $('btn-refresh');
  if (!button) return;
  button.disabled = refreshActive;
  button.setAttribute('aria-busy', String(refreshActive));
  button.setAttribute('aria-label', refreshActive ? 'Refreshing telemetry' : 'Refresh telemetry');
  const label = button.querySelector('.desktop-label');
  if (label) label.textContent = refreshActive ? 'Refreshing…' : 'Refresh';
}
function schedulePoll() {
  if (pollTimer) clearTimeout(pollTimer);
  const delay = document.hidden ? Math.max(60000, app.refreshMs) : app.refreshMs;
  pollTimer = setTimeout(async () => {
    if (!document.hidden) {
      await refresh(true);
      if (app.route === 'logs' && app.log.auto) await loadLog();
    }
    schedulePoll();
  }, delay);
}
function markUpdate() {
  if (isEditingData()) {
    app.deferredRender = true;
    updateChrome();
    return;
  }
  render({ preserve: true });
}
function isEditingData() {
  const active = document.activeElement;
  if (!active || !$('view').contains(active)) return false;
  if (active.matches('textarea, select')) return true;
  return active.matches('input') && !['checkbox', 'radio', 'button', 'submit', 'range'].includes(active.type);
}
async function refresh(silent = true) {
  if (document.hidden && !silent) return;
  if (refreshActive) { refreshQueued = true; return; }
  refreshActive = true;
  refreshQueued = false;
  const revision = ++refreshRevision;
  const requestedRange = app.range;
  updateRefreshControls();
  const needEvents = ['overview', 'alerts', 'thermal', 'more'].includes(app.route);
  const requests = [api('/api/v1/stats'), api(`/api/v1/history?range=${encodeURIComponent(requestedRange)}`)];
  if (needEvents) requests.push(api('/api/v1/events'));
  const results = await Promise.allSettled(requests);
  const [statsResult, historyResult, eventsResult] = results;
  if (revision > appliedRevision) {
    appliedRevision = revision;
    if (statsResult.status === 'fulfilled') {
      app.stats = statsResult.value;
      const availableSources = allLogSources(app.stats);
      if (!availableSources.some((source) => source.id === app.log.source)) {
        app.log.source = 'journal';
        app.log.revision += 1;
        if (app.log.controller) app.log.controller.abort();
        app.log.controller = null;
        app.log.pendingKey = '';
      }
      app.receivedAt = Date.now();
      app.error = '';
      setConnection('live', 'Live');
    } else {
      app.error = statsResult.reason instanceof Error ? statsResult.reason.message : 'Telemetry request failed.';
      if (statsResult.reason && statsResult.reason.status !== 401) setConnection('offline', 'Offline');
    }
    if (historyResult.status === 'fulfilled') {
      const samples = Array.isArray(historyResult.value.samples) ? historyResult.value.samples : [];
      app.historyByRange.set(requestedRange, samples);
      while (app.historyByRange.size > RANGES.length) app.historyByRange.delete(app.historyByRange.keys().next().value);
    }
    if (historyResult.status === 'fulfilled' && requestedRange === app.range) {
      app.hist = app.historyByRange.get(requestedRange) || [];
      app.histRange = requestedRange;
      app.historyError = '';
      app.rangeBusy = false;
    } else if (historyResult.status === 'rejected') {
      app.historyError = historyResult.reason instanceof Error ? historyResult.reason.message : 'History request failed.';
      app.rangeBusy = false;
    }
    if (eventsResult) {
      if (eventsResult.status === 'fulfilled') {
        app.events = M.sortedEvents(eventsResult.value.events || []);
        app.eventsError = '';
      } else {
        app.eventsError = eventsResult.reason instanceof Error ? eventsResult.reason.message : 'Event request failed.';
      }
    }
  }
  refreshActive = false;
  updateRefreshControls();
  if (!silent && app.error) toast('error', 'Refresh failed', app.error);
  markUpdate();
  if (refreshQueued) {
    refreshQueued = false;
    queueMicrotask(() => refresh(true));
  } else {
    schedulePoll();
  }
}
function setConnection(status, detail) {
  app.status = status;
  app.statusDetail = detail;
  updateChromeStatus();
}
function updateChromeStatus() {
  for (const id of ['top-dot', 'side-dot']) $(id).dataset.state = app.status;
  let label = app.statusDetail;
  if (app.status === 'live' && app.stats) {
    const seconds = app.receivedAt ? Math.max(0, Math.floor((Date.now() - app.receivedAt) / 1000)) : 0;
    label = seconds < 5 ? 'Live · just now' : `Live · received ${seconds}s ago`;
    if (app.historyError) label += ' · history delayed';
  }
  $('top-status').textContent = label;
  $('side-status').textContent = label;
}
function clearTelemetry() {
  appliedRevision = ++refreshRevision;
  app.stats = null;
  app.hist = [];
  app.historyByRange.clear();
  app.events = [];
  app.receivedAt = 0;
  app.error = '';
  app.historyError = '';
  app.eventsError = '';
  app.log.cache.clear();
  app.log.errors.clear();
  app.log.lastGoodKey = '';
  app.log.revision += 1;
  app.containerLog.revision += 1;
  app.containerLog.text = '';
  app.containerLog.error = '';
  setConnection('connecting', 'Connecting');
  clearCharts();
  $('view').innerHTML = '<div class="loading-state" role="status"><span class="loader" aria-hidden="true"></span><span>Connecting with the updated settings…</span></div>';
}
function toast(kind, title, detail = '') {
  const element = document.createElement('div');
  element.className = `toast-item ${kind === 'error' ? 'error' : kind === 'success' ? 'success' : ''}`;
  const strong = document.createElement('strong');
  strong.textContent = title;
  element.appendChild(strong);
  if (detail) {
    const span = document.createElement('span');
    span.textContent = detail;
    element.appendChild(span);
  }
  $('toasts').appendChild(element);
  setTimeout(() => element.remove(), 6000);
}

/* ---------- shell rendering and state restoration ---------- */
function activeNavRoute(route) {
  if (route === 'cpu') return 'system';
  if (route === 'thermal') return 'system';
  if (SECONDARY_MORE_ROUTES.includes(route)) return 'more';
  return route;
}
function navLink(route, mobile = false) {
  const current = activeNavRoute(app.route) === route;
  const selected = mobile ? activeNavRoute(app.route) === route : app.route === route;
  const count = route === 'alerts' && app.stats ? (app.stats.alerts || []).length : 0;
  return `<a href="#/${route}" class="${mobile ? 'bottom-link' : 'side-link'}" ${selected ? 'aria-current="page"' : ''}>${icon(ROUTES[route].icon)}<span>${esc(ROUTES[route].title)}</span>${!mobile && count ? `<span class="nav-count">${count}</span>` : ''}</a>`;
}
function renderNav() {
  const groups = [
    ['Observe', ['overview', 'system', 'storage', 'network', 'containers']],
    ['Diagnostics', ['cpu', 'thermal', 'logs', 'alerts']],
    ['Control', ['actions', 'settings']],
  ];
  $('side-nav').innerHTML = groups.map(([label, routes]) => `<div class="nav-group"><div class="nav-group-label">${label}</div>${routes.map((route) => navLink(route)).join('')}</div>`).join('');
  $('bottom-nav').innerHTML = MOBILE_ROUTES.map((route) => navLink(route, true)).join('');
  $('bottom-nav').hidden = ['cpu', 'thermal'].includes(app.route);
  if (app.route === 'settings') $('top-settings').setAttribute('aria-current', 'page');
  else $('top-settings').removeAttribute('aria-current');
}
function routeSubtitle() {
  const metadata = ROUTES[app.route];
  if (!app.stats) return `${metadata.subtitle} · ${app.statusDetail}`;
  const host = text(app.stats.host && app.stats.host.hostname, 'hostname not reported');
  const sample = M.formatTime(app.stats.ts);
  const age = app.receivedAt ? Math.max(0, Math.floor((Date.now() - app.receivedAt) / 1000)) : null;
  const freshness = app.status === 'live' ? (age !== null && age >= 5 ? `received ${age}s ago` : 'current') : `last known · ${app.statusDetail}`;
  return `${host} · Sample ${sample} · ${freshness}`;
}
function updateChrome() {
  const metadata = ROUTES[app.route] || ROUTES.overview;
  $('route-title').textContent = metadata.title;
  $('route-kicker').textContent = metadata.kicker;
  $('route-subtitle').textContent = routeSubtitle();
  $('heading-state').textContent = app.stats ? `${M.RANGE_LABELS[app.range]} history` : metadata.subtitle;
  const stats = app.stats;
  $('side-host').textContent = stats ? `${text(stats.host && stats.host.hostname)} · ${text(stats.host && (stats.host.os || stats.host.kernel))}` : 'Waiting for host';
  $('side-version').textContent = stats ? `Watchdog ${text(stats.watchdog && stats.watchdog.version)}` : '';
  document.title = `${metadata.title}${stats && stats.host && stats.host.hostname ? ` · ${stats.host.hostname}` : ''} | NAS Watchdog`;
  $('route-back').innerHTML = metadata.back ? `<a class="back-link" href="${metadata.back}">${icon('back')}<span>Back to ${esc(metadata.backLabel)}</span></a>` : '';
  updateChromeStatus();
}
function restoreDetails(openDetails) {
  document.querySelectorAll('#view details[data-disclosure]').forEach((details) => {
    if (openDetails.has(details.dataset.disclosure)) details.open = true;
  });
}
function render(options = {}) {
  const { preserve = true, focusHeading = false } = options;
  const active = document.activeElement;
  const view = $('view');
  const focus = preserve && active && view.contains(active) && active.id ? {
    id: active.id, start: active.selectionStart, end: active.selectionEnd,
  } : null;
  const openDetails = new Set(Array.from(view.querySelectorAll('details[data-disclosure][open]')).map((details) => details.dataset.disclosure));
  const logBody = $('log-body');
  const logScroll = preserve && logBody ? { key: logKey(app.log.source, app.log.lines, app.log.q), top: logBody.scrollTop, left: logBody.scrollLeft } : null;
  if (preserve) routeScroll.set(app.route, window.scrollY);
  clearCharts();
  app.deferredRender = false;
  updateChrome();
  renderNav();
  const stats = app.stats;
  const notices = [];
  if (app.error && stats) notices.push(notice(app.status === 'locked' ? 'Authentication required' : 'Showing last known telemetry', `${app.error} Values are not live and host actions are disabled.`, 'warning'));
  if (app.historyError) notices.push(notice('History refresh delayed', `${app.historyError} Existing history is preserved.`, 'warning'));
  if (app.eventsError && ['alerts', 'thermal', 'overview'].includes(app.route)) notices.push(notice('Event history refresh delayed', `${app.eventsError} Existing events are preserved.`, 'warning'));
  $('global-notice').innerHTML = notices.length ? `<div class="stack">${notices.join('')}</div>` : '';
  if (!stats && !['settings', 'more'].includes(app.route)) {
    view.innerHTML = `<div class="loading-state" role="status"><span class="loader" aria-hidden="true"></span><strong>${esc(app.status === 'offline' ? 'Watchdog unavailable' : app.status === 'locked' ? 'API token required' : 'Connecting to watchdog')}</strong><span>${esc(app.error || 'Waiting for the first live telemetry snapshot.')}</span><button id="empty-retry" class="button primary" type="button" ${refreshActive ? 'disabled' : ''}>${refreshActive ? 'Refreshing…' : 'Try again'}</button></div>`;
  } else {
    const page = PAGES[app.route] || PAGES.overview;
    view.innerHTML = page(stats);
  }
  restoreDetails(openDetails);
  if (PAGE_AFTER[app.route]) PAGE_AFTER[app.route](stats);
  if (logScroll && $('log-body') && logScroll.key === logKey(app.log.source, app.log.lines, app.log.q)) {
    $('log-body').scrollTop = logScroll.top;
    $('log-body').scrollLeft = logScroll.left;
  }
  updateRefreshControls();
  if (focus) {
    const next = $(focus.id);
    if (next) {
      next.focus({ preventScroll: true });
      if (typeof next.setSelectionRange === 'function' && focus.start !== null) {
        try { next.setSelectionRange(focus.start, focus.end); } catch (_) {}
      }
    }
  }
  if (focusHeading) {
    window.scrollTo({ top: 0, behavior: 'instant' });
    $('route-title').focus({ preventScroll: true });
  } else if (preserve) {
    window.scrollTo({ top: routeScroll.get(app.route) || 0, behavior: 'instant' });
  }
}
function routeChanged() {
  const current = ROUTES[app.route] ? app.route : 'overview';
  routeScroll.set(current, window.scrollY);
  app.route = M.routeFromHash(location.hash);
  app.deferredRender = false;
  if (current === 'logs' && app.route !== 'logs') {
    app.log.revision += 1;
    if (app.log.controller) app.log.controller.abort();
    app.log.controller = null;
    app.log.pendingKey = '';
  }
  render({ preserve: false, focusHeading: true });
  if (app.route === 'logs') loadLog();
  if (['overview', 'alerts', 'thermal', 'more'].includes(app.route) && !app.events.length && app.stats && app.status === 'live') refresh(true);
  schedulePoll();
}

/* ---------- Overview ---------- */
const PAGES = {};
const PAGE_AFTER = {};
function eventRow(event) {
  const alert = event.alert || {};
  const fired = event.kind === 'fired';
  return `<article class="alert-card ${fired ? toneForSeverity(alert.severity) : 'good'}"><span class="alert-icon" aria-hidden="true">${fired ? '!' : '✓'}</span><div class="alert-body"><h3>${esc(text(event.kind, 'event'))} · ${esc(text(alert.title, 'Watchdog event'))}</h3><p>${esc(text(alert.detail))}</p><span class="alert-time">${esc(M.formatDateTime(M.eventTimestamp(event)))}${event.ts ? '' : ' · legacy event uses alert start'}</span></div></article>`;
}
function cpuHero(stats) {
  const cpu = stats.cpu || {};
  const alerts = stats.alerts || [];
  const critical = alerts.some((alert) => alert.severity === 'critical') || !!(stats.pool && stats.pool.read_only);
  const warning = alerts.some((alert) => alert.severity === 'warning');
  const statement = critical ? 'Critical attention required' : alerts.length ? `${alerts.length} active ${alerts.length === 1 ? 'alert' : 'alerts'} · review recommended` : 'No active watchdog alerts';
  const poolText = stats.pool && stats.pool.read_only ? 'Pool is read-only · writes may fail' : `${cpu.threads || cpu.cores ? `${fmtI(cpu.threads || cpu.cores)} CPU threads` : 'CPU thread count not reported'} · ${stats.pool && stats.pool.read_only ? 'read-only' : 'pool state shown below'}`;
  return `<article class="signal-plate"><div class="signal-top"><span class="machine">${esc(text(stats.host && stats.host.hostname, 'NAS SERVER').toUpperCase())}</span><a href="#/cpu" aria-label="Open CPU utilisation detail">CPU DETAIL ${icon('arrow', 'inline-icon')}</a></div><h2 class="signal-statement ${critical ? 'danger' : warning ? 'warning' : ''}">${esc(statement)}</h2><p class="signal-subline">${esc(poolText)}</p><div class="hero-number"><strong>${esc(fmtN(cpu.usage_pct))}</strong><span>%</span></div><div class="hero-caption">CPU utilisation · current watchdog sample</div><div id="overview-cpu-chart" class="signal-chart" role="img" aria-label="CPU utilisation history chart for ${esc(M.RANGE_LABELS[app.range])}"></div><div class="signal-foot">${readout('Load average', fmtN(stats.host && stats.host.load1, 2), `5m ${fmtN(stats.host && stats.host.load5, 2)} · 15m ${fmtN(stats.host && stats.host.load15, 2)}`)}${readout('Host uptime', fmtDur(stats.host && stats.host.uptime_s), `Power ${stats.power && M.finite(stats.power.package_watts) !== null ? `${fmtN(stats.power.package_watts)} W` : M.MISSING}`)}</div></article>`;
}
PAGES.overview = (stats) => {
  const memory = stats.memory || {};
  const pool = stats.pool || {};
  const cpuTemp = M.selectCPUTemperature(stats.temps || []);
  const dimm = (stats.temps || []).filter((temp) => M.inferSensorCategory(temp) === 'memory').sort((a, b) => (M.finite(b.c) ?? -Infinity) - (M.finite(a.c) ?? -Infinity))[0];
  const net = networkRates(stats, true);
  const disk = physicalDiskRates(stats);
  const running = (stats.containers || []).filter((container) => container.state === 'running').length;
  const unhealthy = (stats.containers || []).filter((container) => container.health === 'unhealthy').length;
  const smart = stats.smart || [];
  const passed = smart.filter((drive) => drive.health === 'PASSED').length;
  const hottestDisk = smart.filter((drive) => M.finite(drive.temperature) !== null).sort((a, b) => b.temperature - a.temperature)[0];
  return `<div class="stack-lg"><div class="overview-hero">${cpuHero(stats)}<section class="panel"><div class="panel-pad"><div class="panel-heading"><div><h2>Watchdog signal</h2><p>Current condition, not only color</p></div>${poolStatus(pool)}</div></div><div class="divide-list">${(stats.alerts || []).length ? (stats.alerts || []).slice(0, 3).map(alertCard).join('') : '<div class="panel-pad">'+notice('Within watchdog thresholds', 'No active alerts are present in this sample.', 'good')+'</div>'}</div>${(stats.alerts || []).length ? '<div class="panel-pad"><a class="button text-button full-width" href="#/alerts">View all active alerts</a></div>' : ''}</section></div>
    <section aria-labelledby="core-telemetry"><div class="section-heading"><h2 id="core-telemetry">Core telemetry</h2><p>Current snapshot</p></div><div class="panel">${readoutGrid([
      ['Memory', fmtPct(memory.used_pct), `${fmtB(memory.used)} of ${fmtB(memory.total)}`, { percent: memory.used_pct }],
      ['Pool', fmtPct(pool.used_pct), `${fmtB(pool.used_bytes)} used`, { percent: pool.used_pct, tone: poolTone(pool) }],
      ['Network', fmtR(combinedRate(net, 'rx', 'tx')), `Physical RX ${fmtR(net.rx)} · TX ${fmtR(net.tx)}`],
      ['Disk I/O', fmtR(combinedRate(disk, 'read', 'write')), `Read ${fmtR(disk.read)} · write ${fmtR(disk.write)}`],
      ['CPU temperature', cpuTemp ? fmtT(cpuTemp.c) : M.MISSING, cpuTemp ? cpuTemp.label : 'No CPU sensor matched', { tone: cpuTemp ? temperatureTone({ ...cpuTemp, alarms: cpuTemp.alarms || [] }) : '' }],
      ['DIMM temperature', dimm ? fmtT(dimm.c) : M.MISSING, dimm ? dimm.label : 'No memory sensor matched', { tone: dimm ? temperatureTone({ ...dimm, alarms: dimm.alarms || [] }) : '' }],
      ['Containers', `${running} / ${(stats.containers || []).length}`, `${unhealthy} unhealthy · running / total`, { tone: unhealthy ? 'warning' : '' }],
      ['SMART devices', smart.length ? `${passed} / ${smart.length}` : M.MISSING, hottestDisk ? `Hottest ${fmtT(hottestDisk.temperature)} · ${hottestDisk.device}` : 'No disk temperature reported'],
    ])}</div><div class="button-row"><a class="button text-button" href="#/thermal">Inspect all thermal sensors ${icon('arrow')}</a><a class="button text-button" href="#/cpu">Inspect CPU history ${icon('arrow')}</a></div></section>
    <div class="grid-2"><section class="panel panel-pad"><div class="panel-heading"><div><h2>Storage</h2><p>${esc(text(pool.device))} → ${esc(text(pool.host_path))}</p></div>${poolStatus(pool)}</div><div class="data-card-title">${esc(fmtB(pool.used_bytes))} of ${esc(fmtB(pool.total_bytes))}</div>${meter(pool.used_pct, poolTone(pool) === 'danger' ? 'red' : poolTone(pool) === 'warning' ? 'amber' : 'green', 'Pool capacity')}${kvGrid([['Free', fmtB(pool.free_bytes)], ['Corruption', fmtI(pool.corruption_total)], ['Scrub', text(stats.btrfs && stats.btrfs.scrub && stats.btrfs.scrub.status)], ['Cache hit', stats.bcache && stats.bcache.present ? fmtPct(stats.bcache.hit_ratio) : M.MISSING]])}<a class="button text-button full-width" href="#/storage">Inspect storage</a></section>
    <section class="panel panel-pad"><div class="panel-heading"><div><h2>Services &amp; system</h2><p>${esc(text(stats.docker && stats.docker.version, 'Container engine not reported'))}</p></div>${tag(`${running} running`, unhealthy ? 'warning' : 'good')}</div>${kvGrid([['Engine images', fmtI(stats.docker && stats.docker.images)], ['EDAC CE / UE', `${fmtI(stats.edac && stats.edac.corrected)} / ${fmtI(stats.edac && stats.edac.uncorrected)}`], ['Throttle events', fmtI(stats.cpu && stats.cpu.throttle && stats.cpu.throttle.package_count)], ['NTP', stats.system ? (stats.system.ntp_synced ? 'Synchronized' : 'Not synchronized') : M.MISSING], ['Kernel', text(stats.host && stats.host.kernel), true]])}<a class="button text-button full-width" href="#/containers">View containers</a></section></div>
    <section class="panel panel-pad" aria-labelledby="recent-events"><div class="panel-heading"><div><h2 id="recent-events">Recent events</h2><p>Server event buffer · event time first</p></div><a class="button text-button" href="#/alerts">Event history</a></div><div class="divide-list">${app.events.length ? app.events.slice(0, 8).map(eventRow).join('') : emptyState('No events have been reported.', true)}</div></section></div>`;
};
PAGE_AFTER.overview = () => {
  timeChart('overview-cpu-chart', [{ label: 'CPU %', color: 'green', get: (sample) => sample.cpu, fill: true }], { compact: true, height: 80, yRange: [0, 100], unit: '%' });
};

/* ---------- System and CPU detail ---------- */
function coreCards(stats) {
  const cores = stats.cpu && stats.cpu.per_core_detail || [];
  if (!cores.length) return emptyState('Per-thread utilization is not reported in this snapshot.', true);
  return `<div class="core-grid">${cores.slice().sort((a, b) => a.id - b.id).map((core) => `<div class="core-card"><div class="core-top"><span>CPU ${esc(fmtI(core.id))}</span><span>${M.finite(core.freq_mhz) === null ? M.MISSING : `${fmtI(core.freq_mhz)} MHz`}</span></div><div class="core-bottom">${meter(core.usage, M.finite(core.usage) >= 85 ? 'amber' : 'blue', `CPU ${core.id} utilization`)}<strong>${esc(fmtPct(core.usage, 0))}</strong></div></div>`).join('')}</div>`;
}
function processCards(stats) {
  const processes = stats.processes || [];
  return `<section class="panel panel-pad" data-filter-group="processes"><div class="toolbar"><div><h2>Processes</h2><p class="quiet">Top processes supplied by watchdog</p></div><label class="field" for="proc-q"><span>Filter name, PID, state, or command</span><input id="proc-q" data-filter-input="processes" type="search" value="${esc(app.procQ)}" placeholder="Filter processes" autocomplete="off"></label></div><p id="processes-results" class="filter-results" aria-live="polite"></p><div>${processes.length ? processes.map((process) => `<article class="data-card process-card" data-filter-item="processes"><div class="data-card-header"><div class="min-w-0"><h3 class="data-card-title">${esc(text(process.name, 'Unnamed process'))}</h3><p class="data-card-subtitle">PID ${esc(fmtI(process.pid))} · state ${esc(text(process.state))}</p></div><div class="data-card-value ${M.finite(process.cpu_pct) >= 50 ? 'warning' : ''}">${esc(fmtPct(process.cpu_pct))}</div></div>${kvGrid([['CPU', fmtPct(process.cpu_pct)], ['Memory', fmtPct(process.mem_pct)], ['RSS', fmtB(process.rss)], ['Threads', fmtI(process.threads)]])}<div class="process-command"><span class="eyebrow">COMMAND</span><br>${esc(text(process.cmdline))}</div></article>`).join('') : emptyState('No process list is reported.', true)}</div></section>`;
}
function cStates(stats) {
  const states = stats.cpu && stats.cpu.c_states || [];
  if (!states.length) return emptyState('C-state residency is not reported.', true);
  return `<div class="attribute-list">${states.map((state) => `<div class="attribute-row"><div><strong>${esc(text(state.name))}</strong> <span class="attribute-id">CPU0</span></div><span>${esc(fmtI(state.count))} entries</span><div class="attribute-values"><span>Residency <strong>${esc(fmtDur((M.finite(state.time_ms) || 0) / 1000))}</strong></span><span>Latency <strong>${M.finite(state.latency_us) === null ? M.MISSING : `${fmtI(state.latency_us)} µs`}</strong></span></div></div>`).join('')}</div>`;
}
PAGES.system = (stats) => {
  const cpu = stats.cpu || {};
  const memory = stats.memory || {};
  const pressure = stats.pressure || {};
  const memPct = M.clamp(memory.used_pct, 0, 100);
  const cachedPct = M.finite(memory.total) > 0 ? M.clamp((M.finite(memory.cached) || 0) / memory.total * 100, 0, 100) : 0;
  const bufferPct = M.finite(memory.total) > 0 ? M.clamp((M.finite(memory.buffers) || 0) / memory.total * 100, 0, 100) : 0;
  return `<div class="stack-lg"><section class="panel"><div class="panel-pad panel-heading"><div><h2>Compute &amp; memory history</h2><p>Load is multiplied by ten only for visual comparison</p></div><a class="button text-button" href="#/cpu">Open CPU detail ${icon('arrow')}</a></div>${rangeControlsWrapper()}<div id="system-chart" class="chart-wrap" role="img" aria-label="CPU, memory, and scaled load history"></div></section>
    <div class="panel">${readoutGrid([
      ['CPU', fmtPct(cpu.usage_pct), `${fmtI(cpu.threads)} threads · ${text(cpu.governor, 'governor not reported')}`, { percent: cpu.usage_pct }],
      ['CPU frequency', M.finite(cpu.freq_mhz) === null ? M.MISSING : `${fmtN(cpu.freq_mhz, 0)} MHz`, `Maximum ${M.finite(cpu.max_freq_mhz) === null ? M.MISSING : `${fmtI(cpu.max_freq_mhz)} MHz`}`],
      ['Package power', M.finite(stats.power && stats.power.package_watts) === null ? M.MISSING : `${fmtN(stats.power.package_watts)} W`, 'Current reported power'],
      ['Package throttle', fmtI(cpu.throttle && cpu.throttle.package_count), `${fmtDur((M.finite(cpu.throttle && cpu.throttle.package_total_ms) || 0) / 1000)} cumulative since boot`, { tone: M.finite(cpu.throttle && cpu.throttle.package_count) > 0 ? 'warning' : '' }],
    ])}</div>
    <section class="panel panel-pad"><div class="panel-heading"><div><h2>Per-thread utilization</h2><p>Current usage and frequency</p></div><a class="button text-button" href="#/cpu">History &amp; detail</a></div>${coreCards(stats)}</section>
    <div class="grid-2"><section class="panel panel-pad"><div class="panel-heading"><div><h2>Memory</h2><p>${esc(fmtB(memory.used))} used of ${esc(fmtB(memory.total))}</p></div>${tag(fmtPct(memory.used_pct), 'info')}</div><div class="split-meter" role="img" aria-label="Memory used ${esc(fmtPct(memPct))}, cached ${esc(fmtPct(cachedPct))}, buffers ${esc(fmtPct(bufferPct))}"><span style="width:${memPct}%;background:var(--wd-blue)"></span><span style="width:${cachedPct}%;background:var(--wd-green)"></span><span style="width:${bufferPct}%;background:var(--wd-amber)"></span></div><div class="legend-row"><span class="legend-key"><i class="legend-swatch" style="--swatch:var(--wd-blue)"></i>Used</span><span class="legend-key"><i class="legend-swatch" style="--swatch:var(--wd-green)"></i>Cached</span><span class="legend-key"><i class="legend-swatch" style="--swatch:var(--wd-amber)"></i>Buffers</span></div>${kvGrid([['Total', fmtB(memory.total)], ['Used', fmtB(memory.used)], ['Free', fmtB(memory.free)], ['Available', fmtB(memory.available)], ['Cached', fmtB(memory.cached)], ['Buffers', fmtB(memory.buffers)], ['Dirty', fmtB(memory.dirty)], ['Writeback', fmtB(memory.writeback)], ['Slab', fmtB(memory.slab)], ['SReclaimable', fmtB(memory.sreclaimable)], ['Swap used', fmtB(memory.swap_used)], ['Swap total', fmtB(memory.swap_total)], ['Swap free', fmtB(memory.swap_free)], ['Swap utilization', fmtPct(memory.swap_pct)], ['OOM kills', fmtI(memory.oom_kills)], ['Page faults', fmtI(memory.page_faults)], ['Major faults', fmtI(memory.major_faults)]])}</section>
    <section class="panel panel-pad"><div class="panel-heading"><div><h2>Pressure stall information</h2><p>Some / full · avg10, avg60, avg300</p></div></div>${[['CPU', pressure.cpu], ['Memory', pressure.memory], ['I/O', pressure.io]].map(([label, line]) => `<div class="data-card"><h3>${label}</h3>${kvGrid([['Some avg10 / 60 / 300', line ? `${fmtN(line.some10, 2)} / ${fmtN(line.some60, 2)} / ${fmtN(line.some300, 2)}` : M.MISSING, true], ['Full avg10 / 60 / 300', line ? `${fmtN(line.full10, 2)} / ${fmtN(line.full60, 2)} / ${fmtN(line.full300, 2)}` : M.MISSING, true]])}${meter(line ? line.some300 * 10 : null, M.finite(line && line.some300) >= 10 ? 'amber' : 'blue', `${label} avg300`)}</div>`).join('')}</section></div>
    <div class="grid-2"><section class="panel panel-pad"><div class="panel-heading"><h2>C-states</h2><p>CPU0 · cumulative</p></div>${cStates(stats)}</section><section class="panel panel-pad"><div class="panel-heading"><h2>Power &amp; GPU</h2><p>Reported hardware counters</p></div>${kvGrid(Object.entries(stats.power && stats.power.parts || {}).map(([name, watts]) => [`Power ${name}`, `${fmtN(watts, 2)} W`]).concat([['Package total', `${fmtN(stats.power && stats.power.package_watts, 2)} W`], ['Energy counter', M.finite(stats.power && stats.power.energy_uj) === null ? M.MISSING : `${fmtI(stats.power.energy_uj)} µJ`]]))}<div class="sensor-groups">${(stats.gpu || []).length ? stats.gpu.map((gpu) => `<div class="data-card"><h3>${esc(text(gpu.card, 'GPU'))}</h3>${kvGrid([['Frequency', `${fmtI(gpu.freq_mhz)} MHz`], ['Min / max', `${fmtI(gpu.min_mhz)} / ${fmtI(gpu.max_mhz)} MHz`], ['RP0', `${fmtI(gpu.rp0_mhz)} MHz`], ['RC6 residency', fmtDur((M.finite(gpu.rc6_ms) || 0) / 1000)], ['Engines', (gpu.engines || []).length ? gpu.engines.join(', ') : M.MISSING, true]])}</div>`).join('') : emptyState('No GPU telemetry is reported.', true)}</div></section></div>
    ${processCards(stats)}
    <div class="grid-2"><section class="panel panel-pad"><div class="panel-heading"><h2>Kernel &amp; system</h2><p>Host context</p></div>${kvGrid([['Kernel', text(stats.host && stats.host.kernel), true], ['CPU model', text(stats.host && stats.host.cpu_model), true], ['Physical cores', fmtI(stats.host && stats.host.cpu_cores)], ['Kernel modules', fmtI(stats.kernel && stats.kernel.module_count)], ['Kernel command line', text(stats.kernel && stats.kernel.cmdline), true], ['NTP', stats.system ? (stats.system.ntp_synced ? 'Synchronized' : 'Not synchronized') : M.MISSING], ['Timezone', text(stats.system && stats.system.timezone)], ['Failed units', fmtI((stats.system && stats.system.failed_units || []).length)], ['Failed unit names', (stats.system && stats.system.failed_units || []).join(', ') || M.MISSING, true], ['Context switches', fmtI(stats.system && stats.system.ctxt)], ['Processes started', fmtI(stats.system && stats.system.processes)], ['Running / blocked', stats.system ? `${fmtI(stats.system.procs_running)} / ${fmtI(stats.system.procs_blocked)}` : M.MISSING], ['EDAC corrected / uncorrected', `${fmtI(stats.edac && stats.edac.corrected)} / ${fmtI(stats.edac && stats.edac.uncorrected)}`]])}</section>
    <section class="panel panel-pad"><div class="panel-heading"><div><h2>Power supplies &amp; cooling</h2><p>${(stats.temps || []).length} temperatures · ${(stats.fans || []).length} fans</p></div><a class="button text-button" href="#/thermal">Thermal detail</a></div><div class="sensor-groups">${(stats.power_supplies || []).length ? stats.power_supplies.map((supply) => `<div class="data-card"><h3>${esc(text(supply.name, 'Power supply'))}</h3>${kvGrid([['Type', text(supply.type)], ['Status', text(supply.status)], ['Online', text(supply.online)]])}</div>`).join('') : emptyState('No power-supply telemetry is reported.', true)}</div><a class="button secondary full-width" href="#/thermal">Open all sensors, zones, and fans</a></section></div></div>`;
};
function rangeControlsWrapper() { return `<div class="panel-pad">${rangeControls()}</div>`; }
PAGE_AFTER.system = () => {
  timeChart('system-chart', [
    { label: 'CPU %', color: 'blue', get: (sample) => sample.cpu, fill: true },
    { label: 'Memory %', color: 'green', get: (sample) => sample.mem_pct },
    { label: 'Load ×10', color: 'amber', get: (sample) => M.finite(sample.load1) === null ? null : sample.load1 * 10 },
  ], { unit: '%', yRange: [0, 100] });
  applyFilter('processes');
};
PAGES.cpu = (stats) => {
  const cpu = stats.cpu || {};
  const points = M.historyPoints(app.hist, (sample) => sample.cpu);
  const summary = M.historySummary(app.hist, (sample) => sample.cpu);
  const cpuTemp = M.selectCPUTemperature(stats.temps || []);
  const components = [['User', cpu.user_pct], ['System', cpu.sys_pct], ['I/O wait', cpu.iowait_pct], ['IRQ', cpu.irq_pct], ['Steal', cpu.steal_pct], ['Idle', cpu.idle_pct]];
  const thresholds = cpuTemp ? [M.warningThreshold(cpuTemp), M.criticalThreshold(cpuTemp)].filter((value, index, all) => value !== null && all.indexOf(value) === index).map((value, index) => ({ value, tone: index === 0 ? 'warning' : 'critical', label: `${index === 0 ? 'Warning' : 'Critical'} ${fmtT(value)}` })) : [];
  return `<div class="stack-lg"><article class="signal-plate"><div class="signal-top"><span>CPU UTILISATION</span><a href="#/thermal">THERMAL DETAIL ${icon('arrow')}</a></div><div class="hero-number"><strong>${esc(fmtN(cpu.usage_pct))}</strong><span>%</span></div><p class="signal-statement">${esc(text(cpu.cpu_model || stats.host && stats.host.cpu_model, 'CPU model not reported'))}</p><p class="signal-subline">${esc(fmtI(cpu.threads))} threads · ${esc(fmtI(cpu.cores))} cores · governor ${esc(text(cpu.governor))}</p></article>
    <section class="panel panel-pad chart-card"><div class="panel-heading"><div><h2>Utilisation history</h2><p>Null readings remain visible gaps</p></div><span class="section-detail">${summary.count} samples</span></div>${rangeControlsWrapper()}<div id="cpu-history-chart" class="chart-wrap" role="img" aria-label="CPU utilisation history"></div>${chartSummary(summary, '%')}</section>
    <div class="panel">${readoutGrid([['Current', fmtPct(cpu.usage_pct), 'Current watchdog snapshot'], ['Latest history', summary.latest === null ? 'No samples' : fmtPct(summary.latest), `${summary.count} valid samples`], ['Minimum', summary.minimum === null ? 'No samples' : fmtPct(summary.minimum), M.RANGE_LABELS[app.range]], ['Peak', summary.maximum === null ? 'No samples' : fmtPct(summary.maximum), M.RANGE_LABELS[app.range]]])}</div>
    ${sampleSection('cpu', points, '%')}
    ${cpuTemp ? `<section class="panel panel-pad chart-card"><div class="panel-heading"><div><h2>Thermal envelope</h2><p>${esc(cpuTemp.label)} · thresholds supplied by watchdog</p></div><span class="data-card-value ${temperatureTone({ ...cpuTemp, alarms: cpuTemp.alarms || [] })}">${esc(fmtT(cpuTemp.c))}</span></div><div id="cpu-thermal-chart" class="chart-wrap" role="img" aria-label="CPU temperature history with thresholds"></div>${chartSummary(M.historySummary(app.hist, (sample) => sample.temps && sample.temps[cpuTemp.key]), '°C', fmtT)}${kvGrid([['Warning threshold', M.warningThreshold(cpuTemp) === null ? M.MISSING : fmtT(M.warningThreshold(cpuTemp))], ['Most conservative critical', M.criticalThreshold(cpuTemp) === null ? M.MISSING : fmtT(M.criticalThreshold(cpuTemp))], ['Critical headroom', M.criticalHeadroom(cpuTemp) ? fmtT(M.criticalHeadroom(cpuTemp).headroomC, true) : M.MISSING], ['Sensor source', text(cpuTemp.source)]])}</section>` : notice('CPU temperature not reported', 'No CPU package or matching processor sensor appears in this snapshot.')}
    <section class="panel panel-pad"><div class="panel-heading"><div><h2>Utilisation composition</h2><p>Current sample</p></div></div>${kvGrid(components.map(([label, value]) => [label, fmtPct(value)]).concat([['Average frequency', M.finite(cpu.freq_mhz) === null ? M.MISSING : `${fmtN(cpu.freq_mhz)} MHz`], ['Maximum frequency', M.finite(cpu.max_freq_mhz) === null ? M.MISSING : `${fmtI(cpu.max_freq_mhz)} MHz`]]))}</section>
    <section class="panel panel-pad"><div class="panel-heading"><div><h2>Per-thread utilization</h2><p>Current usage and reported frequency</p></div></div>${coreCards(stats)}</section></div>`;
};
PAGE_AFTER.cpu = (stats) => {
  if (!stats) return;
  timeChart('cpu-history-chart', [{ label: 'CPU %', color: 'green', get: (sample) => sample.cpu, fill: true }], { unit: '%', yRange: [0, 100], height: 260 });
  const cpuTemp = M.selectCPUTemperature(stats.temps || []);
  if (cpuTemp) {
    const thresholds = [
      { value: M.warningThreshold(cpuTemp), tone: 'warning', label: `Warning ${fmtT(M.warningThreshold(cpuTemp))}` },
      { value: M.criticalThreshold(cpuTemp), tone: 'critical', label: `Critical ${fmtT(M.criticalThreshold(cpuTemp))}` },
    ].filter((threshold, index, all) => threshold.value !== null && all.findIndex((other) => other.value === threshold.value) === index);
    timeChart('cpu-thermal-chart', [{ label: cpuTemp.label, color: 'amber', get: (sample) => sample.temps && sample.temps[cpuTemp.key], fill: true }], { unit: '°C', thresholds, zeroMin: false, headroom: 5, height: 240 });
  }
};

/* ---------- Rich thermal detail ---------- */
function hardwareLimitEntries(sensor) {
  const limits = sensor.limits || {};
  return [
    ['Minimum', limits.min_c], ['Maximum', limits.max_c], ['Low critical', limits.lcrit_c],
    ['Critical', limits.crit_c], ['Emergency', limits.emergency_c],
  ];
}
function hardwareLimitSummary(sensor) {
  const parts = hardwareLimitEntries(sensor).filter(([, value]) => M.finite(value) !== null).map(([label, value]) => `${label.toLowerCase()} ${fmtT(value)}`);
  return parts.length ? `Hardware ${parts.join(' · ')}` : '';
}
function smartObservedBounds(drive) {
  if (!drive) return '';
  const minimum = M.finite(drive.temperature_min_c);
  const maximum = M.finite(drive.temperature_max_c);
  if (minimum !== null && maximum !== null) return `SMART observed ${fmtT(minimum)}–${fmtT(maximum)}`;
  if (minimum !== null) return `SMART observed minimum ${fmtT(minimum)}`;
  if (maximum !== null) return `SMART observed maximum ${fmtT(maximum)}`;
  return '';
}
function sensorSupport(sensor, stats) {
  const drive = M.smartForSensor(sensor, stats.smart || []);
  return [
    text(sensor.chip, ''), sensor.device || '', sensor.sensor ? `Channel ${sensor.sensor}` : '',
    sensor.core_type ? `${sensor.core_type} core` : '',
    sensor.logicalCpus && sensor.logicalCpus.length ? `Logical CPUs ${sensor.logicalCpus.join(', ')}` : '',
    hardwareLimitSummary(sensor), smartObservedBounds(drive),
    sensor.alarms && sensor.alarms.length ? `Alarms: ${sensor.alarms.join(', ')}` : '',
  ].filter(Boolean).join(' · ') || `${M.CATEGORY_LABELS[sensor.category]} sensor`;
}
function sensorCard(sensor, selectedKey, stats, title = sensor.label, supporting = null) {
  const tone = temperatureTone(sensor);
  const maximum = M.criticalThreshold(sensor) || M.warningThreshold(sensor) || 100;
  const percent = M.finite(sensor.c) === null ? null : sensor.c / Math.max(1, maximum) * 100;
  return `<button class="sensor-card" type="button" data-thermal-key="${esc(sensor.key)}" aria-pressed="${sensor.key === selectedKey}"><div class="sensor-card-top"><div class="min-w-0"><h3>${esc(title)}</h3><p>${esc(supporting || sensorSupport(sensor, stats))}${sensor.key === selectedKey ? ' · Showing history' : ''}</p></div><span class="sensor-card-value ${tone}">${esc(fmtT(sensor.c))}</span></div>${meter(percent, tone === 'danger' ? 'red' : tone === 'warning' ? 'amber' : 'blue', `${title} temperature relative to threshold`)}<span class="visually-hidden">${sensor.key === selectedKey ? 'Selected sensor' : 'Select this sensor for history'}</span></button>`;
}
function thermalGroup(model, category, title, selected, stats) {
  const sensors = model.physical.filter((sensor) => sensor.category === category);
  return `<section>${sectionHeading(title, `${sensors.length} ${sensors.length === 1 ? 'sensor' : 'sensors'}`)}${sensors.length ? `<div class="sensor-grid">${sensors.map((sensor) => sensorCard(sensor, selected && selected.key, stats)).join('')}</div>` : emptyState(`No ${title.toLowerCase()} sensors are reported.`, true)}</section>`;
}
function thresholdRunReadout(label, threshold, run) {
  let value = M.MISSING;
  let supporting = 'Threshold missing for this sensor';
  if (threshold !== null) {
    value = run.longestMillis === null ? 'None observed' : run.currentMillis !== null ? M.formatCompactDuration(run.currentMillis) : 'Not active now';
    supporting = run.longestMillis === null ? `No contiguous samples at or above ${fmtT(threshold)}` : `Longest contiguous period ${M.formatCompactDuration(run.longestMillis)} at or above ${fmtT(threshold)}`;
  }
  return readout(label, value, supporting, { tone: run.currentMillis !== null ? 'warning' : '' });
}
function thermalZoneCard(zone, selectedKey) {
  return `<button class="sensor-card" type="button" data-thermal-key="${esc(zone.key)}" aria-pressed="${zone.key === selectedKey}"><div class="sensor-card-top"><div><h3>${esc(zone.label)}</h3><p class="machine">${esc(zone.key)}</p></div><span class="sensor-card-value">${esc(fmtT(zone.c))}</span></div><p>Mode: ${esc(text(zone.mode))} · Policy: ${esc(text(zone.policy))}${zone.key === selectedKey ? ' · Showing history' : ''}</p></button><div class="data-card"><h3>Firmware trip points</h3>${(zone.trips || []).length ? zone.trips.map((trip) => `<div class="trip-row"><span class="quiet">Trip ${esc(fmtI(trip.index))}</span><div>${esc(text(trip.type, 'Type not reported'))}<small>Trip ${esc(fmtT(trip.temp_c))} · Hysteresis ${esc(fmtT(trip.hyst_c))}</small></div></div>`).join('') : emptyState('No valid trip points are reported.', true)}</div>`;
}
function coreClassPanel(model, coreType) {
  const matching = model.cores.filter((core) => String(core.sensor.core_type || '').toLowerCase() === coreType);
  if (!matching.length) return '';
  const hottest = matching.reduce((a, b) => a.sensor.c >= b.sensor.c ? a : b).sensor;
  const average = matching.reduce((sum, core) => sum + core.sensor.c, 0) / matching.length;
  return `<div class="panel panel-pad panel-high"><div class="panel-heading"><div><h3>${coreType === 'performance' ? 'Performance cores' : 'Efficiency cores'}</h3><p>${esc(hottest.label)} is hottest</p></div><div class="data-card-value">Peak ${esc(fmtT(hottest.c))}<br><span class="muted-value">Average ${esc(fmtT(average))}</span></div></div></div>`;
}
PAGES.thermal = (stats) => {
  const model = M.buildThermalModel(stats);
  const selected = M.selectThermalSensor(model.sensors, app.thermalKey);
  if (selected) app.thermalKey = selected.key;
  const points = selected ? M.historyPoints(app.hist, (sample) => sample.temps && sample.temps[selected.key]) : [];
  const summary = selected ? M.historySummary(app.hist, (sample) => sample.temps && sample.temps[selected.key]) : M.historySummary([], null);
  const warning = selected ? M.warningThreshold(selected) : null;
  const critical = selected ? M.criticalThreshold(selected) : null;
  const warningRun = selected ? M.contiguousThresholdRun(app.hist, (sample) => sample.temps && sample.temps[selected.key], warning) : null;
  const criticalRun = selected ? M.contiguousThresholdRun(app.hist, (sample) => sample.temps && sample.temps[selected.key], critical) : null;
  const healthLabel = model.health === 'critical' ? 'Critical attention required' : model.health === 'warning' ? 'Warning threshold active' : model.health === 'no_data' ? 'No thermal data' : 'Within reported thresholds';
  const thresholds = [];
  if (selected) {
    [
      [M.warningThreshold(selected), 'Warning', 'warning'], [M.validThreshold(selected.limits && selected.limits.max_c), 'Hardware max', 'warning'],
      [M.validThreshold(selected.crit), 'Configured critical', 'critical'], [M.validThreshold(selected.limits && selected.limits.crit_c), 'Hardware critical', 'critical'],
      [M.validThreshold(selected.limits && selected.limits.emergency_c), 'Hardware emergency', 'critical'],
    ].forEach(([value, label, tone]) => {
      if (value !== null && !thresholds.some((threshold) => threshold.value === value && threshold.tone === tone)) thresholds.push({ value, label: `${label} ${fmtT(value)}`, tone });
    });
  }
  const thermalEvents = M.sortedEvents(app.events, true).slice(0, 8);
  return `<div class="stack-lg"><article class="signal-plate high"><div class="signal-top"><span>THERMAL TELEMETRY</span>${tag(healthLabel, model.health === 'critical' ? 'danger' : model.health === 'warning' ? 'warning' : model.health === 'normal' ? 'good' : '')}</div><div class="hero-number"><strong>${esc(model.hottest ? fmtN(model.hottest.c) : '—')}</strong>${model.hottest ? '<span>°C</span>' : ''}</div><h2 class="signal-statement">${esc(model.hottest ? `Hottest component · ${model.hottest.label}` : 'No physical component temperature is available')}</h2><p class="signal-subline">${model.cpuPackage ? `Package ${fmtT(model.cpuPackage.c)}` : 'CPU package not reported'}${model.hottestCore ? ` · Hot core ${fmtT(model.hottestCore.c)} (${model.hottestCore.label})` : ''}</p><div class="signal-foot">${readout('Critical headroom', model.criticalHeadroom ? fmtT(model.criticalHeadroom.headroomC, true) : M.MISSING, model.criticalHeadroom ? `${model.criticalHeadroom.sensor.label} → ${fmtT(model.criticalHeadroom.limitC)} limit` : 'No component critical limit reported', { tone: model.criticalHeadroom && model.criticalHeadroom.headroomC <= 0 ? 'danger' : '' })}${readout('Physical core spread', model.coreSpreadC === null ? M.MISSING : fmtT(model.coreSpreadC, true), model.cores.length >= 2 ? 'Hottest minus coolest physical core' : 'At least two core sensors are required')}</div></article>
    <section class="panel panel-pad chart-card"><div class="panel-heading"><div><h2>Temperature history</h2><p>${selected ? esc(selected.label) : 'No sensors reported'}</p></div><span class="section-detail">${summary.count} valid samples</span></div>${rangeControlsWrapper()}<label class="field sensor-picker" for="thermal-sensor"><span>Sensor · grouped by component</span><select id="thermal-sensor" ${model.sensors.length ? '' : 'disabled'}>${model.pickerGroups.map((group) => `<optgroup label="${esc(group.label)}">${group.sensors.map((sensor) => `<option value="${esc(sensor.key)}" ${selected && sensor.key === selected.key ? 'selected' : ''}>${esc(sensor.label)} · ${esc(fmtT(sensor.c))}${sensor.device ? ` · ${esc(sensor.device)}` : ''}</option>`).join('')}</optgroup>`).join('')}</select></label><div id="thermal-history-chart" class="chart-wrap" role="img" aria-label="Selected temperature history with reported thresholds"></div>${chartSummary(summary, '°C', fmtT)}</section>
    ${selected ? `<div class="panel">${readoutGrid([['Current', fmtT(selected.c), selected.label], ['Latest sample', summary.latest === null ? 'No samples' : fmtT(summary.latest), `${summary.count} samples · ${M.RANGE_LABELS[app.range]}`], ['Minimum', summary.minimum === null ? 'No samples' : fmtT(summary.minimum), 'Within selected range'], ['Average', summary.average === null ? 'No samples' : fmtT(summary.average), 'Mean of valid samples'], ['Maximum', summary.maximum === null ? 'No samples' : fmtT(summary.maximum), 'Within selected range'], ['Change', summary.change === null ? 'No comparison' : fmtT(summary.change, true), 'Latest minus first sample'], ['Warning threshold', warning === null ? M.MISSING : fmtT(warning), 'Configured warning threshold'], ['Critical headroom', M.criticalHeadroom(selected) ? fmtT(M.criticalHeadroom(selected).headroomC, true) : M.MISSING, critical === null ? 'No critical limit reported' : `To most conservative ${fmtT(critical)} limit`]], 4)}</div><div class="panel">${thresholdRunReadout('Above warning', warning, warningRun)}${thresholdRunReadout('Above critical', critical, criticalRun)}<p class="panel-pad quiet">Contiguous threshold periods exclude missing samples; they do not extrapolate through gaps.</p></div>${sampleSection('thermal', points, '°C')}<section class="panel panel-pad"><div class="panel-heading"><h2>Selected sensor details</h2><p>Reported identity and limits</p></div>${kvGrid([['Key', selected.key], ['Category', M.CATEGORY_LABELS[selected.category]], ['Source', text(selected.source)], ['Chip', text(selected.chip)], ['Channel', text(selected.sensor)], ['Device', text(selected.device)], ['Core type', text(selected.core_type)], ['Logical CPUs', selected.logicalCpus && selected.logicalCpus.length ? selected.logicalCpus.join(', ') : M.MISSING], ['Alarms', selected.alarms && selected.alarms.length ? selected.alarms.join(', ') : M.MISSING, true], ...hardwareLimitEntries(selected).map(([label, value]) => [`Hardware ${label.toLowerCase()}`, fmtT(value)])])}</section>` : notice('Thermal sensors not reported', 'No normalized component temperatures or firmware zones appear in this snapshot.')}
    <section>${sectionHeading('CPU core analytics', `${model.cores.length} physical ${model.cores.length === 1 ? 'core' : 'cores'}`)}${model.cores.length ? `<div class="grid-2">${coreClassPanel(model, 'performance')}${coreClassPanel(model, 'efficiency')}</div><div class="sensor-grid">${model.cores.map((core) => {
      const supporting = [`Physical core ${core.physicalCoreId === null ? 'ID not reported' : core.physicalCoreId}`, core.sensor.core_type || '', core.sensor.logicalCpus.length ? `Logical CPUs ${core.sensor.logicalCpus.join(', ')}` : 'Logical CPU association not reported', core.loadPct === null ? '' : `Load ${fmtPct(core.loadPct)}`, core.minFreqMHz === null ? '' : core.minFreqMHz === core.maxFreqMHz ? `${fmtI(core.minFreqMHz)} MHz` : `${fmtI(core.minFreqMHz)}–${fmtI(core.maxFreqMHz)} MHz`, hardwareLimitSummary(core.sensor)].filter(Boolean).join(' · ');
      return sensorCard(core.sensor, selected && selected.key, stats, core.sensor.label, supporting);
    }).join('')}</div>` : emptyState('Per-core temperatures are not reported. The CPU package sensor remains available when present.')}</section>
    ${thermalGroup(model, 'memory', 'Memory', selected, stats)}
    <section>${sectionHeading('NVMe', `${model.nvmeGroups.length} physical ${model.nvmeGroups.length === 1 ? 'device' : 'devices'}`)}${model.nvmeGroups.length ? model.nvmeGroups.map((group) => {
      const drive = M.smartForSensor(group.sensors[0], stats.smart || []);
      return `<div class="panel panel-pad"><div class="panel-heading"><div><h3>${esc(group.label)}${drive && drive.model ? ` · ${esc(drive.model)}` : ''}</h3><p>${drive && drive.serial ? `S/N ${esc(drive.serial)}` : 'SMART identity not reported'}${!group.id.startsWith('/dev/') ? ' · composite and additional controller sensors' : ''}</p></div></div><div class="sensor-grid">${group.sensors.map((sensor) => sensorCard(sensor, selected && selected.key, stats)).join('')}</div></div>`;
    }).join('') : emptyState('No NVMe temperatures are reported.')}</section>
    ${thermalGroup(model, 'sata', 'SATA / SMART', selected, stats)}${thermalGroup(model, 'board', 'Board', selected, stats)}${model.physical.some((sensor) => sensor.category === 'other') ? thermalGroup(model, 'other', 'Other components', selected, stats) : ''}
    <section>${sectionHeading('Firmware thermal envelope', `${model.zones.length} zones`)}${model.zones.length ? `<div class="sensor-groups">${model.zones.map((zone) => thermalZoneCard(zone, selected && selected.key)).join('')}</div>` : emptyState('No firmware thermal zones are exposed by this watchdog.')}</section>
    <section>${sectionHeading('Cooling response', `${(stats.fans || []).length} fans`)}${(stats.fans || []).length ? `<div class="grid-2">${stats.fans.map((fan, index) => {
      const history = M.fanHistorySummary(app.hist, fan.key);
      return `<article class="panel panel-pad fan-card"><div class="panel-heading"><div><h3>${esc(text(fan.label || fan.key, `Fan ${index + 1}`))}</h3><p class="machine">${esc(text(fan.key, 'Fan key not reported; history cannot be correlated'))}</p></div><span class="data-card-value">${esc(fmtI(fan.rpm))} RPM</span></div><div class="readout-grid">${readout('Current speed', `${fmtI(fan.rpm)} RPM`, M.finite(fan.rpm) === 0 ? 'Zero RPM is preserved; no fan-stop cause is inferred' : 'Current fan tachometer')}${readout('PWM command', M.finite(fan.pwm) === null ? M.MISSING : `${fmtI(fan.pwm)} / 255`, M.finite(fan.pwm) === null ? 'PWM not reported' : `${fmtPct(fan.pwm / 255 * 100)} duty`)}</div><p class="quiet">${history.count ? `${M.RANGE_LABELS[app.range]} speed range ${history.minimumRpm}–${history.maximumRpm} RPM · ${history.count} samples` : `No keyed fan samples in ${M.RANGE_LABELS[app.range]}`}</p><div id="fan-chart-${index}" class="chart-wrap compact" role="img" aria-label="${esc(text(fan.label || fan.key, `Fan ${index + 1}`))} RPM history"></div></article>`;
    }).join('')}</div>` : emptyState('No fan telemetry is reported.')}</section>
    <section class="panel panel-pad"><div class="panel-heading"><div><h2>Power &amp; throttle context</h2><p>Counters are cumulative since boot</p></div></div>${kvGrid([['CPU package power', stats.power && M.finite(stats.power.package_watts) !== null ? `${fmtN(stats.power.package_watts)} W` : M.MISSING], ['Package thermal throttling', stats.cpu && stats.cpu.throttle ? `${fmtI(stats.cpu.throttle.package_count)} events · ${M.formatCompactDuration(stats.cpu.throttle.package_total_ms)} cumulative` : M.MISSING], ['Core thermal throttling', stats.cpu && stats.cpu.throttle ? `${fmtI(stats.cpu.throttle.core_count)} events · ${M.formatCompactDuration(stats.cpu.throttle.core_total_ms)} cumulative` : M.MISSING]])}<p class="quiet">A nonzero counter does not necessarily indicate current throttling.</p></section>
    <section class="panel panel-pad"><div class="panel-heading"><h2>Active thermal alerts</h2><p>${model.activeThermalAlerts.length} active</p></div><div class="divide-list">${model.activeThermalAlerts.length ? model.activeThermalAlerts.map(alertCard).join('') : emptyState('No active temperature or cooling alerts.', true)}</div></section>
    <section class="panel panel-pad"><div class="panel-heading"><h2>Recent temperature events</h2><p>Event timestamp first, alert onset fallback</p></div><div class="divide-list">${thermalEvents.length ? thermalEvents.map(eventRow).join('') : emptyState(app.eventsError ? 'Event history is unavailable; previous events could not be loaded.' : 'No recent temperature or cooling events were reported.', true)}</div></section></div>`;
};
PAGE_AFTER.thermal = (stats) => {
  if (!stats) return;
  const model = M.buildThermalModel(stats);
  const selected = M.selectThermalSensor(model.sensors, app.thermalKey);
  if (selected) {
    const thresholds = [
      [M.warningThreshold(selected), 'Warning', 'warning'], [M.validThreshold(selected.limits && selected.limits.max_c), 'Hardware max', 'warning'],
      [M.validThreshold(selected.crit), 'Critical', 'critical'], [M.validThreshold(selected.limits && selected.limits.crit_c), 'Hardware critical', 'critical'],
      [M.validThreshold(selected.limits && selected.limits.emergency_c), 'Emergency', 'critical'],
    ].filter(([value]) => value !== null).map(([value, label, tone]) => ({ value, label: `${label} ${fmtT(value)}`, tone })).filter((threshold, index, all) => all.findIndex((other) => other.value === threshold.value && other.tone === threshold.tone) === index);
    timeChart('thermal-history-chart', [{ label: selected.label, color: temperatureTone(selected) === 'danger' ? 'red' : 'amber', get: (sample) => sample.temps && sample.temps[selected.key], fill: true }], { unit: '°C', thresholds, zeroMin: false, headroom: 5, height: 250 });
  } else {
    const chart = $('thermal-history-chart');
    if (chart) chart.innerHTML = '<div class="chart-empty">No normalized temperatures or firmware zones are present.</div>';
  }
  (stats.fans || []).forEach((fan, index) => {
    if (!fan.key) return;
    timeChart(`fan-chart-${index}`, [{ label: 'RPM', color: 'blue', get: (sample) => sample.fans && sample.fans[fan.key] ? sample.fans[fan.key].rpm : null }], { compact: true, height: 58, yRange: [0, Math.max(100, M.fanHistorySummary(app.hist, fan.key).maximumRpm || 0) + 100] });
  });
};

/* ---------- Storage and network ---------- */
function actionsAvailable(stats) {
  return !!(stats && stats.watchdog && stats.watchdog.actions_enabled && app.status === 'live' && !app.actionBusy);
}
function actionUnavailableReason(stats) {
  if (app.actionBusy) return 'Another action request is being sent.';
  if (app.status !== 'live') return 'Reconnect to live telemetry before issuing an action.';
  if (!stats || !stats.watchdog || !stats.watchdog.actions_enabled) return 'Actions are disabled by the watchdog configuration.';
  return '';
}
function actionButton(action, label, stats, target = '', tone = 'secondary', enabled = true) {
  const disabled = !actionsAvailable(stats) || !enabled;
  return `<button type="button" class="button small ${tone}" data-act="${esc(action)}" data-target="${esc(target)}" ${disabled ? 'disabled' : ''} aria-disabled="${disabled}">${esc(label)}</button>`;
}
function diskCard(disk) {
  return `<article class="data-card"><div class="data-card-header"><div><h3 class="data-card-title machine">${esc(text(disk.name, 'Unnamed disk'))}</h3><p class="data-card-subtitle">${esc(text(disk.model))}${disk.serial ? ` · ${esc(disk.serial)}` : ''}</p></div><div class="data-card-value">${esc(fmtB(disk.size_bytes))}</div></div>${kvGrid([['Type', disk.rotational ? 'Rotational' : 'Non-rotational'], ['Read rate', fmtR(disk.read_rate)], ['Write rate', fmtR(disk.write_rate)], ['Read / write IOPS', `${fmtN(disk.read_iops)} / ${fmtN(disk.write_iops)}`], ['I/O time', M.finite(disk.io_ms) === null ? M.MISSING : `${fmtN(disk.io_ms)} ms`], ['Utilization', fmtPct(disk.util_pct)], ['Read bytes', fmtB(disk.read_bytes)], ['Write bytes', fmtB(disk.write_bytes)], ['Scheduler', text(disk.scheduler)]])}${meter(disk.util_pct, M.finite(disk.util_pct) >= 90 ? 'amber' : 'blue', `${disk.name} utilization`)}</article>`;
}
function smartCard(drive, index) {
  const health = drive.error ? tag('Unavailable', 'warning') : healthTag(drive.health === 'PASSED', text(drive.health, 'Health not reported'), text(drive.health, 'Health not reported'));
  const nvme = drive.type === 'nvme' || String(drive.device || '').includes('nvme');
  const counters = [
    ['Reallocated', fmtI(drive.reallocated)], ['Pending', fmtI(drive.pending)], ['Uncorrectable', fmtI(drive.uncorrectable)],
    ['CRC errors', fmtI(drive.crc_errors)], [nvme ? 'Wear used' : 'Media errors', nvme ? fmtPct(drive.percentage_used, 0) : fmtI(drive.media_errors)],
  ];
  return `<article class="panel panel-pad"><div class="data-card-header"><div class="min-w-0"><h3 class="data-card-title machine">${esc(text(drive.device, 'SMART device'))}</h3><p class="data-card-subtitle">${esc(text(drive.model))} · S/N ${esc(text(drive.serial))} · ${esc(text(drive.type, 'interface not reported'))}</p></div>${health}</div><div class="panel">${readoutGrid([['Temperature', fmtT(drive.temperature), smartObservedBounds(drive) || 'Observed bounds not reported'], ['Power-on time', M.finite(drive.power_on_hours) === null ? M.MISSING : `${Math.floor(drive.power_on_hours / 24)}d ${drive.power_on_hours % 24}h`, `${fmtI(drive.power_on_hours)} hours`], ['Health', drive.error ? M.MISSING : text(drive.health), drive.error || 'SMART overall assessment']], 3)}</div><div class="panel">${readoutGrid(counters.map(([label, value]) => [label, value, label === 'Reallocated' || label === 'Pending' || label === 'Uncorrectable' ? 'Drive health counter' : 'Reported lifetime counter']), 4)}</div>${kvGrid([['Available spare', M.finite(drive.available_spare) === null ? M.MISSING : fmtPct(drive.available_spare, 0)], ['Unsafe shutdowns', fmtI(drive.unsafe_shutdowns)], ['Data units read', fmtI(drive.data_units_read)], ['Data units written', fmtI(drive.data_units_written)]])}${Object.keys(drive.attributes || {}).length ? `<details data-disclosure="smart-map-${index}"><summary>Additional normalized SMART counters (${Object.keys(drive.attributes).length})</summary><div class="disclosure-body">${kvGrid(Object.entries(drive.attributes).map(([name, value]) => [name, fmtI(value)]))}</div></details>` : ''}${(drive.table || []).length ? `<details data-disclosure="smart-table-${index}"><summary>Full SMART attributes (${drive.table.length})</summary><div class="disclosure-body attribute-list">${drive.table.map((attribute) => `<div class="attribute-row ${attribute.failed ? 'danger' : ''}"><div><strong>${esc(text(attribute.name))}</strong> <span class="attribute-id">ID ${esc(fmtI(attribute.id))}</span></div><span>${attribute.failed ? tag(`Failed: ${attribute.failed}`, 'danger') : tag('Not failed', 'good')}</span><div class="attribute-values"><span>Normalized <strong>${esc(fmtI(attribute.value))}</strong></span><span>Worst <strong>${esc(fmtI(attribute.worst))}</strong></span><span>Threshold <strong>${esc(fmtI(attribute.thresh))}</strong></span><span>Raw <strong>${esc(text(attribute.raw_text || attribute.raw_value))}</strong></span></div></div>`).join('')}</div></details>` : emptyState('The expanded SMART attribute table is not reported for this device.', true)}</article>`;
}
function mountCard(mount) {
  return `<article class="data-card"><div class="data-card-header"><div class="min-w-0"><h3 class="data-card-title machine">${esc(text(mount.target, 'Mount target not reported'))}</h3><p class="data-card-subtitle machine">${esc(text(mount.device))}</p></div>${tag(mount.ro ? 'Read-only' : 'Read-write', mount.ro ? 'danger' : 'good')}</div>${kvGrid([['Filesystem', text(mount.fstype)], ['Used / total', `${fmtB(mount.used)} / ${fmtB(mount.total)}`], ['Free', fmtB(mount.free)], ['Utilization', fmtPct(mount.used_pct)], ['Options', text(mount.options), true]])}${meter(mount.used_pct, M.finite(mount.used_pct) >= 95 ? 'red' : M.finite(mount.used_pct) >= 85 ? 'amber' : 'blue', `${mount.target} utilization`)}</article>`;
}
PAGES.storage = (stats) => {
  const pool = stats.pool || {};
  const df = stats.btrfs && stats.btrfs.df || {};
  const scrub = stats.btrfs && stats.btrfs.scrub || {};
  const bcache = stats.bcache || {};
  const deviceStats = pool.device_stats || stats.btrfs && stats.btrfs.device_stats || {};
  const scrubRunning = /running|in.progress|scrubbing/i.test(String(scrub.status || ''));
  return `<div class="stack-lg"><section class="panel"><div class="panel-pad"><div class="panel-heading"><div><h2>Pool</h2><p>${esc(text(pool.device))} → ${esc(text(pool.host_path))}</p></div>${poolStatus(pool)}</div><div class="hero-number"><strong>${esc(fmtN(pool.used_pct))}</strong><span>%</span></div><p class="hero-caption">${esc(fmtB(pool.used_bytes))} used of ${esc(fmtB(pool.total_bytes))} · ${esc(fmtB(pool.free_bytes))} free</p>${meter(pool.used_pct, poolTone(pool) === 'danger' ? 'red' : poolTone(pool) === 'warning' ? 'amber' : 'green', 'Pool capacity')}${pool.error ? `<p class="inline-error">${esc(pool.error)}</p>` : ''}</div>${readoutGrid([['Corruption', fmtI(pool.corruption_total), 'Reported checksum corruption'], ['Subvolumes', fmtI(stats.subvolumes), 'Pool subvolume count'], ['Expected mode', pool.expected_rw ? 'Read-write' : 'No write expectation', pool.expected_rw ? 'Pool is expected to be writable' : 'Watchdog configuration'], ['Pool status', text(pool.status), pool.read_only ? 'Read-only state requires attention' : 'Current filesystem status']])}</section>
    ${(stats.raid || []).length ? `<section>${sectionHeading('RAID arrays', `${stats.raid.length} reported`)}<div class="grid-2">${stats.raid.map((raid) => `<article class="data-card"><div class="data-card-header"><div><h3>${esc(text(raid.name))}</h3><p class="data-card-subtitle">${esc(text(raid.level))} · ${esc(text(raid.members))}</p></div>${healthTag(!raid.degraded, 'Not degraded', 'Degraded')}</div>${kvGrid([['State', text(raid.state)], ['Sync', text(raid.sync)], ['Detail', text(raid.detail), true]])}</article>`).join('')}</div></section>` : ''}
    <div class="grid-2"><section class="panel panel-pad"><div class="panel-heading"><div><h2>Btrfs allocation</h2><p>Profiles and actual chunk usage</p></div></div>${Object.keys(df).length ? Object.entries(df).map(([type, allocation]) => {
      const percent = M.finite(allocation.total) > 0 ? allocation.used / allocation.total * 100 : null;
      return `<article class="data-card"><div class="data-card-header"><div><h3>${esc(type)}</h3><p class="data-card-subtitle">${esc(text(allocation.profile))}</p></div><span class="data-card-value">${esc(fmtPct(percent))}</span></div>${kvGrid([['Total', fmtB(allocation.total)], ['Used', fmtB(allocation.used)]])}${meter(percent, 'blue', `${type} chunk utilization`)}</article>`;
    }).join('') : emptyState('Btrfs allocation is unavailable.', true)}</section>
    <section class="panel panel-pad"><div class="panel-heading"><div><h2>Scrub</h2><p>Status changes must be confirmed by a later sample</p></div>${tag(text(scrub.status), scrubRunning ? 'info' : '')}</div>${kvGrid([['Status', text(scrub.status)], ['Started', text(scrub.started)], ['Duration', fmtDur(scrub.duration_s)], ['Progress', fmtPct(scrub.progress_pct)], ['Data scrubbed', fmtB(scrub.data_bytes_scrubbed)], ['Tree scrubbed', fmtB(scrub.tree_bytes_scrubbed)], ['Checksum errors', fmtI(scrub.csum_errors)], ['Verify errors', fmtI(scrub.verify_errors)], ['Uncorrectable errors', fmtI(scrub.uncorrectable_errors)], ['Corrected errors', fmtI(scrub.corrected_errors)], ['Read errors', fmtI(scrub.read_errors)]])}${meter(scrub.progress_pct, 'blue', 'Scrub progress')}<div class="button-row mobile-stack">${actionButton('scrub_start', 'Start scrub', stats, '', 'primary', !scrubRunning)}${actionButton('scrub_cancel', 'Cancel scrub', stats, '', 'secondary', scrubRunning)}</div></section></div>
    <section class="panel panel-pad"><div class="panel-heading"><div><h2>Filesystem device errors</h2><p>Current cumulative counters</p></div></div>${kvGrid(['corruption_errs', 'write_io_errs', 'read_io_errs', 'flush_io_errs', 'generation_errs'].map((key) => [key.replace(/_/g, ' '), fmtI(deviceStats[key])]))}</section>
    <section class="panel panel-pad"><div class="panel-heading"><div><h2>Bcache</h2><p>${bcache.present ? 'Cache telemetry reported' : 'No active bcache telemetry'}</p></div></div>${kvGrid([['Present', bcache.present ? 'Yes' : 'No'], ['Mode', text(bcache.cache_mode)], ['State', text(bcache.state)], ['Hit ratio', fmtPct(bcache.hit_ratio)], ['Available', fmtPct(bcache.available_percent)], ['Cache hits', fmtI(bcache.cache_hits)], ['Cache misses', fmtI(bcache.cache_misses)], ['Bypass hits', fmtI(bcache.bypass_hits)], ['Bypass misses', fmtI(bcache.bypass_misses)], ['Cache bypass hits', fmtI(bcache.cache_bypass_hits)], ['Cache bypass misses', fmtI(bcache.cache_bypass_misses)]])}</section>
    <section>${sectionHeading('Block devices', `${(stats.disks || []).length} reported`)}${(stats.disks || []).length ? `<div class="grid-2">${stats.disks.map(diskCard).join('')}</div>` : emptyState('No block-device telemetry is reported.')}</section>
    <section>${sectionHeading('SMART devices', `${(stats.smart || []).length} reported`)}<p class="section-description">Every device exposes its complete reported attribute table inside an expandable disclosure. Expansion is preserved across refresh.</p><div class="stack">${(stats.smart || []).length ? stats.smart.map(smartCard).join('') : emptyState('No SMART devices are reported.')}</div></section>
    <details class="panel panel-pad" data-disclosure="mounts"><summary>Mounts (${(stats.mounts || []).length})</summary><div class="disclosure-body"><p class="section-description">Targets, options, capacity, and read-only state are readable without horizontal tables.</p>${(stats.mounts || []).length ? stats.mounts.map(mountCard).join('') : emptyState('No mounts are reported.', true)}</div></details></div>`;
};
function interfaceCard(iface) {
  return `<article class="data-card"><div class="data-card-header"><div><h3 class="data-card-title machine">${esc(text(iface.name, 'Unnamed interface'))}</h3><p class="data-card-subtitle">${iface.virtual ? 'Virtual interface' : 'Physical interface'} · ${esc(text(iface.duplex, 'duplex not reported'))}</p></div>${tag(text(iface.state, 'state not reported'), iface.state === 'up' ? 'good' : '')}</div>${kvGrid([['Link speed', M.finite(iface.speed_mbps) === null || iface.speed_mbps <= 0 ? M.MISSING : `${fmtI(iface.speed_mbps)} Mb/s`], ['MTU', M.finite(iface.mtu) === null || iface.mtu <= 0 ? M.MISSING : fmtI(iface.mtu)], ['RX / TX rate', `${fmtR(iface.rx_rate)} / ${fmtR(iface.tx_rate)}`], ['RX / TX bytes', `${fmtB(iface.rx_bytes)} / ${fmtB(iface.tx_bytes)}`], ['RX / TX packets', `${fmtI(iface.rx_packets)} / ${fmtI(iface.tx_packets)}`], ['RX / TX errors', `${fmtI(iface.rx_errs)} / ${fmtI(iface.tx_errs)}`], ['RX / TX drops', `${fmtI(iface.rx_drops)} / ${fmtI(iface.tx_drops)}`], ['MAC address', text(iface.mac), true]])}</article>`;
}
PAGES.network = (stats) => {
  const physical = (stats.net || []).filter((iface) => !iface.virtual);
  const virtual = (stats.net || []).filter((iface) => iface.virtual);
  const network = stats.net_stats || {};
  return `<div class="stack-lg"><section class="panel"><div class="panel-pad">${readoutGrid([['Established', fmtI(network.curr_estab), 'Current TCP connections'], ['Conntrack', fmtI(network.conntrack_used), `Maximum ${fmtI(network.conntrack_max)}`], ['Retransmits', fmtPct(network.retrans_pct, 3), `${fmtI(network.retrans_segs)} retransmitted segments`], ['Sockets', fmtI(network.sockets_used), `${fmtI(network.tcp_in_use)} TCP · ${fmtI(network.tcp_time_wait)} time-wait`]])}</div>${rangeControlsWrapper()}<div id="network-chart" class="chart-wrap" role="img" aria-label="Total network receive and transmit throughput history"></div>${chartSummary(M.historySummary(app.hist, (sample) => M.finite(sample.net_rx) === null || M.finite(sample.net_tx) === null ? null : sample.net_rx + sample.net_tx), 'MiB/s', (value) => `${fmtN(value / 1048576)} MiB/s`)}</section>
    <section>${sectionHeading('Physical interfaces', `${physical.length} reported`)}${physical.length ? `<div class="grid-2">${physical.map(interfaceCard).join('')}</div>` : emptyState('No physical interfaces are reported.')}</section>
    <details class="panel panel-pad" data-disclosure="virtual-interfaces"><summary>Virtual interfaces (${virtual.length})</summary><div class="disclosure-body">${virtual.length ? virtual.map(interfaceCard).join('') : emptyState('No virtual interfaces are reported.', true)}</div></details>
    <div class="grid-2"><section class="panel panel-pad"><div class="panel-heading"><h2>TCP stack</h2><p>Cumulative protocol counters</p></div>${kvGrid([['Established', network.curr_estab], ['Active opens', network.active_opens], ['Passive opens', network.passive_opens], ['Input segments', network.in_segs], ['Output segments', network.out_segs], ['Retransmitted segments', network.retrans_segs], ['Input errors', network.in_errs], ['Output resets', network.out_rsts], ['Listen overflows', network.listen_overflows], ['Listen drops', network.listen_drops]].map(([label, value]) => [label, fmtI(value)]))}</section><section class="panel panel-pad"><div class="panel-heading"><h2>Sockets &amp; routes</h2><p>Current network namespace</p></div>${kvGrid([['TCP in use', fmtI(network.tcp_in_use)], ['TCP time-wait', fmtI(network.tcp_time_wait)], ['TCP allocated', fmtI(network.tcp_alloc)], ['Sockets used', fmtI(network.sockets_used)], ['Conntrack used', fmtI(network.conntrack_used)], ['Conntrack maximum', fmtI(network.conntrack_max)], ['ARP entries', fmtI(network.arp_entries)], ['Routes', fmtI(network.routes)]])}</section></div></div>`;
};
PAGE_AFTER.network = () => {
  timeChart('network-chart', [
    { label: 'RX MiB/s', color: 'green', get: (sample) => M.finite(sample.net_rx) === null ? null : sample.net_rx / 1048576, fill: true },
    { label: 'TX MiB/s', color: 'blue', get: (sample) => M.finite(sample.net_tx) === null ? null : sample.net_tx / 1048576 },
  ], { unit: ' MiB/s', zeroMin: true, headroom: 1, height: 240 });
};

/* ---------- Containers and logs ---------- */
function containerTone(container) {
  if (container.health === 'unhealthy') return 'danger';
  if (container.state === 'running') return container.health === 'starting' ? 'warning' : 'good';
  return '';
}
function containerCard(container, index, stats) {
  const running = container.state === 'running';
  const unavailableHealth = !container.health || container.health === 'none' ? '' : tag(container.health, container.health === 'healthy' ? 'good' : container.health === 'unhealthy' ? 'danger' : 'warning');
  return `<article class="data-card" data-filter-item="containers"><div class="data-card-header"><div class="min-w-0"><h3 class="data-card-title">${esc(text(container.name, `Container ${index + 1}`))}</h3><p class="data-card-subtitle">${esc(text(container.image))} · ID ${esc(text(container.id))}</p></div><div class="tag-row">${tag(text(container.state), containerTone(container))}${unavailableHealth}</div></div><p class="data-card-subtitle">${esc(text(container.status))} · created ${esc(text(container.created))} · ${esc(fmtI(container.restart_count))} restarts</p>${kvGrid([['CPU', fmtPct(container.cpu_pct)], ['Memory', fmtB(container.mem_usage)], ['Memory limit', fmtB(container.mem_limit)], ['Memory utilization', fmtPct(container.mem_pct)], ['Network RX / TX', `${fmtB(container.net_rx)} / ${fmtB(container.net_tx)}`], ['Block read / write', `${fmtB(container.block_read)} / ${fmtB(container.block_write)}`]])}<div class="log-meta">Ports: ${(container.ports || []).length ? container.ports.map((port) => `<code>${esc(port)}</code>`).join(' · ') : esc(M.MISSING)}</div><div class="data-card-actions">${actionButton('container_restart', 'Restart', stats, container.name, 'secondary', running)}${actionButton(running ? 'container_stop' : 'container_start', running ? 'Stop' : 'Start', stats, container.name, running ? 'secondary' : 'primary')}<button type="button" class="button small secondary" data-logs="${esc(container.name)}" ${app.status === 'live' ? '' : 'disabled'}>View logs</button></div></article>`;
}
PAGES.containers = (stats) => {
  const list = stats.containers || [];
  const engine = stats.docker || {};
  return `<div class="stack-lg">${engine.error ? notice('Container engine error', engine.error, 'warning') : ''}<section class="panel">${readoutGrid([['Running', fmtI(list.filter((container) => container.state === 'running').length), `${fmtI(list.length)} total containers`], ['Stopped / paused', fmtI(list.filter((container) => container.state !== 'running').length), `${fmtI(engine.paused)} paused reported by engine`], ['Unhealthy', fmtI(list.filter((container) => container.health === 'unhealthy').length), 'Explicit health-check status'], ['Images', fmtI(engine.images), text(engine.storage_driver)], ['Engine', text(engine.version), `API ${text(engine.api_version)}`], ['Engine CPUs', fmtI(engine.ncpu), `${fmtB(engine.mem_total)} reported memory`], ['Engine OS', text(engine.operating_system), 'Reported runtime platform'], ['Controls', actionsAvailable(stats) ? 'Available' : 'Unavailable', actionUnavailableReason(stats) || 'Confirmation required']])}</section>
    <section data-filter-group="containers"><div class="toolbar"><div><h2>Container inventory</h2><p class="quiet">Current state and recent resource counters</p></div><label class="field" for="ctr-q"><span>Filter name, image, state, status, or ports</span><input id="ctr-q" type="search" data-filter-input="containers" value="${esc(app.ctrQ)}" placeholder="Filter containers" autocomplete="off"></label></div><p id="containers-results" class="filter-results" aria-live="polite"></p><div class="grid-2">${list.length ? list.map((container, index) => containerCard(container, index, stats)).join('') : emptyState('No containers are reported by the engine.')}</div></section></div>`;
};
PAGE_AFTER.containers = () => applyFilter('containers');

function allLogSources(stats) {
  const sources = (stats && stats.logs || []).map((source) => ({ ...source, id: source.source, label: source.source }));
  return [{ id: 'journal', label: 'System journal', builtIn: true }, { id: 'dmesg', label: 'Kernel messages', builtIn: true }, ...sources];
}
function sourceHits(source) {
  return Object.values(source.matches || {}).reduce((sum, value) => sum + (M.finite(value) || 0), 0);
}
function sourceButton(source) {
  const hits = sourceHits(source);
  return `<button type="button" class="source-button" data-source="${esc(source.id)}" aria-pressed="${app.log.source === source.id}"><span>${esc(source.label)}</span>${hits ? `<small>${fmtI(hits)} pattern matches</small>` : ''}</button>`;
}
function sourceSelectOption(source) {
  return `<option value="${esc(source.id)}" ${app.log.source === source.id ? 'selected' : ''}>${esc(source.label)}</option>`;
}
PAGES.logs = (stats) => {
  const sources = allLogSources(stats);
  const key = logKey(app.log.source, app.log.lines, app.log.q);
  const cached = visibleLogEntry();
  const error = app.log.errors.get(key) || '';
  const source = sources.find((candidate) => candidate.id === app.log.source);
  const output = cached ? cached.text : app.log.pendingKey === key ? 'Loading matching lines…' : 'No output loaded for this source and filter yet.';
  const meta = source && source.path ? `${source.path} · ${fmtB(source.size)} · modified ${M.formatDateTime(source.mod_time)}${source.last_match ? ` · last match ${source.last_match}` : ''}${source.error ? ` · ${source.error}` : ''}` : `${source ? source.label : app.log.source} · live tail`;
  return `<div class="logs-layout"><aside class="panel panel-pad log-sources-desktop" aria-label="Log sources"><div class="panel-heading"><div><h2>Sources</h2><p>Server-configured inputs</p></div></div><div class="sensor-groups"><h3 class="eyebrow">System</h3>${sources.filter((source) => source.builtIn).map(sourceButton).join('')}<h3 class="eyebrow">Configured files</h3>${sources.filter((source) => !source.builtIn).map(sourceButton).join('') || '<span class="quiet">Built-ins and configured sources shown above.</span>'}</div></aside><section class="panel panel-pad"><div class="panel-heading"><div><h2>Log reader</h2><p>Requests are revision-guarded; previous output survives errors</p></div>${tag(app.log.auto ? 'Auto refresh on' : 'Auto refresh off', app.log.auto ? 'good' : '')}</div><div class="log-sources-mobile"><label class="field" for="log-source-select"><span>Source</span><select id="log-source-select">${sources.map(sourceSelectOption).join('')}</select></label><div class="source-scroll" role="group" aria-label="Quick log source selection">${sources.map(sourceButton).join('')}</div></div><div class="toolbar"><label class="field" for="log-q"><span>Filter regex</span><input id="log-q" value="${esc(app.log.q)}" placeholder="error|warn" autocomplete="off" spellcheck="false"></label><label class="field fixed" for="log-lines"><span>Line limit</span><input id="log-lines" type="number" min="10" max="2000" step="10" value="${app.log.lines}" inputmode="numeric"></label><button id="log-load" class="button primary" type="button" ${app.log.pendingKey || app.status !== 'live' ? 'disabled' : ''}>${app.log.pendingKey === key ? 'Loading…' : 'Load logs'}</button><label class="field checkbox-field" for="log-auto"><input id="log-auto" type="checkbox" ${app.log.auto ? 'checked' : ''}><span>Auto-refresh</span></label><button id="log-copy" class="button secondary" type="button" ${cached && cached.text ? '' : 'disabled'}>${icon('copy')} Copy output</button></div><p id="log-error" class="inline-error" role="alert">${esc(error)}</p><p id="log-meta" class="log-meta">${esc(meta)}</p><pre id="log-body" class="log-view" tabindex="0" aria-label="Log output; scroll horizontally for long lines">${esc(output)}</pre></section></div>`;
};
PAGE_AFTER.logs = () => {
  const key = logKey(app.log.source, app.log.lines, app.log.q);
  if (!app.log.cache.has(key) && !app.log.errors.has(key) && !app.log.pendingKey) loadLog();
};

/* ---------- Alerts, actions, settings, and More route ---------- */
PAGES.alerts = (stats) => `<div class="stack-lg"><section class="panel panel-pad"><div class="panel-heading"><div><h2>Active alerts</h2><p>${(stats.alerts || []).length} reported in the current snapshot</p></div>${(stats.alerts || []).some((alert) => alert.severity === 'critical') ? tag('Critical attention', 'danger') : (stats.alerts || []).length ? tag('Review recommended', 'warning') : tag('No active alerts', 'good')}</div><div class="divide-list">${(stats.alerts || []).length ? stats.alerts.map(alertCard).join('') : emptyState('No active watchdog alerts are reported.', true)}</div></section><section class="panel panel-pad"><div class="panel-heading"><div><h2>Event history</h2><p>Up to 200 entries retained by the server · newest first</p></div><span class="section-detail">${app.events.length} events loaded</span></div>${app.eventsError ? notice('Event history refresh delayed', app.eventsError, 'warning') : ''}<div class="divide-list">${app.events.length ? app.events.map(eventRow).join('') : emptyState('No events have been reported.', true)}</div></section></div>`;
PAGES.actions = (stats) => {
  const available = actionsAvailable(stats);
  const scrubRunning = /running|in.progress|scrubbing/i.test(String(stats.btrfs && stats.btrfs.scrub && stats.btrfs.scrub.status || ''));
  return `<div class="stack-lg">${notice(available ? 'Manual requests are available' : 'Manual requests unavailable', available ? 'Every action requires a separate confirmation. A success response confirms that the request was issued or accepted, not that the operation completed.' : actionUnavailableReason(stats), available ? 'info' : 'warning')}${app.actionMessage ? notice('Latest action response', app.actionMessage, 'info') : ''}<div class="grid-2"><section class="panel panel-pad"><div class="panel-heading"><div><h2>Filesystem</h2><p>Checksum verification can be I/O intensive</p></div>${icon('storage')}</div><p class="quiet">Start a scrub to verify pool data and metadata. Scrub start and cancellation are explicit manual requests.</p><div class="button-row mobile-stack">${actionButton('scrub_start', 'Start scrub', stats, '', 'primary', !scrubRunning)}${actionButton('scrub_cancel', 'Cancel scrub', stats, '', 'secondary', scrubRunning)}</div></section><section class="panel panel-pad"><div class="panel-heading"><div><h2>Host</h2><p>Manual reboot is independent of automatic recovery</p></div>${icon('actions')}</div><p class="quiet">A confirmed manual reboot requires actions to be enabled and live telemetry. Automatic recovery is separately configured with WD_ALLOW_REBOOT and is not an additional prerequisite.</p><div class="button-row mobile-stack">${actionButton('reboot', 'Reboot host', stats, '', 'danger-button')}${actionButton('reboot_cancel', 'Cancel pending auto-reboot', stats)}</div></section></div><section class="panel panel-pad"><div class="panel-heading"><div><h2>Container requests</h2><p>Start, stop, or restart one explicitly confirmed target</p></div></div><div class="sensor-groups">${(stats.containers || []).length ? stats.containers.map((container) => {
    const running = container.state === 'running';
    return `<article class="data-card"><div class="data-card-header"><div><h3>${esc(text(container.name))}</h3><p class="data-card-subtitle">${esc(text(container.image))}</p></div>${tag(text(container.state), containerTone(container))}</div><div class="data-card-actions">${actionButton('container_restart', 'Restart', stats, container.name, 'secondary', running)}${actionButton(running ? 'container_stop' : 'container_start', running ? 'Stop' : 'Start', stats, container.name, running ? 'secondary' : 'primary')}</div></article>`;
  }).join('') : emptyState('No containers are available for actions.', true)}</div></section></div>`;
};
PAGES.settings = (stats) => `<div class="stack-lg"><div class="grid-2"><section class="panel panel-pad"><div class="panel-heading"><div><h2>Connection</h2><p>Token authentication uses the Bearer Authorization header</p></div>${tag(app.token ? 'Token stored' : 'No token stored', app.token ? 'good' : '')}</div><label class="field" for="set-token"><span>API token (WD_API_TOKEN)</span><input id="set-token" type="password" value="" placeholder="${app.token ? 'Saved token · enter to replace' : 'Enter token if required'}" autocomplete="current-password" spellcheck="false"></label><p class="form-note">For safety, the saved token is not rendered back into the page. Saving replaces telemetry context; clearing removes both token and displayed data.</p><div class="button-row"><button id="set-save" class="button primary" type="button">Save token &amp; connect</button><button id="set-clear" class="button secondary" type="button" ${app.token ? '' : 'disabled'}>Clear token</button></div><p class="form-note">Endpoint: <code>${esc(location.origin)}</code></p></section><section class="panel panel-pad"><div class="panel-heading"><div><h2>Display &amp; refresh</h2><p>System tracks this device’s color-scheme preference</p></div></div><label class="field" for="set-theme"><span>Theme</span><select id="set-theme"><option value="dark" ${app.theme === 'dark' ? 'selected' : ''}>Dark (default)</option><option value="light" ${app.theme === 'light' ? 'selected' : ''}>Light</option><option value="system" ${app.theme === 'system' ? 'selected' : ''}>System</option></select></label><label class="field" for="set-refresh"><span>Telemetry refresh interval</span><select id="set-refresh">${[[5000, '5 seconds'], [10000, '10 seconds'], [30000, '30 seconds'], [60000, '60 seconds']].map(([value, label]) => `<option value="${value}" ${app.refreshMs === value ? 'selected' : ''}>${label}</option>`).join('')}</select></label><p class="form-note">Changing cadence reschedules the next poll immediately. Hidden tabs do not poll; becoming visible triggers one refresh.</p></section></div><section class="panel panel-pad"><div class="panel-heading"><div><h2>Watchdog configuration</h2><p>Read-only context supplied by the current snapshot</p></div></div>${stats ? kvGrid([['Watchdog version', text(stats.watchdog && stats.watchdog.version)], ['Collection interval', stats.watchdog && M.finite(stats.watchdog.interval_s) !== null ? `${fmtI(stats.watchdog.interval_s)}s` : M.MISSING], ['Manual actions', stats.watchdog && stats.watchdog.actions_enabled ? 'Enabled with confirmation' : 'Disabled'], ['Automatic recovery', stats.watchdog && stats.watchdog.reboot_enabled ? 'Enabled' : 'Disabled'], ['Automatic reboot delay', stats.watchdog && M.finite(stats.watchdog.reboot_delay_s) !== null ? `${fmtI(stats.watchdog.reboot_delay_s)}s` : M.MISSING], ['Active alert count', fmtI(stats.watchdog && stats.watchdog.alert_count)], ['Host', `${text(stats.host && stats.host.hostname)} · ${text(stats.host && stats.host.os)}`], ['Last telemetry sample', M.formatDateTime(stats.ts)]]) : emptyState('Configuration is unavailable until telemetry is connected.', true)}</section></div>`;
PAGES.more = (stats) => {
  const cards = [
    ['network', 'Interfaces, throughput, sockets, and network counters'],
    ['logs', 'System journal, kernel messages, and configured log files'],
    ['alerts', stats ? `${(stats.alerts || []).length} active alerts and general event history` : 'Active alerts and general event history'],
    ['actions', 'Manual host, filesystem, and container requests'],
    ['settings', 'Connection token, theme, and refresh cadence'],
    ['cpu', 'Dedicated utilisation history and per-thread telemetry'],
    ['thermal', 'Complete sensors, thresholds, cooling, and history'],
  ];
  return `<div class="stack-lg">${!stats ? notice('Telemetry is not connected', 'Navigation and settings remain available. Data actions unlock after a live snapshot.', 'warning') : ''}<section>${sectionHeading('Additional sections', 'All original destinations remain available')}<div class="grid-2">${cards.map(([route, description]) => `<a class="data-card" href="#/${route}"><div class="data-card-header"><div><h2>${esc(ROUTES[route].title)}</h2><p class="data-card-subtitle">${esc(description)}</p></div>${icon(ROUTES[route].icon)}</div><span class="button text-button">Open ${esc(ROUTES[route].title)} ${icon('arrow')}</span></a>`).join('')}</div></section></div>`;
};

/* ---------- Confirmed host actions ---------- */
function actionLabel(action, target) {
  switch (action) {
    case 'container_restart': return `Restart container ${target}`;
    case 'container_stop': return `Stop container ${target}`;
    case 'container_start': return `Start container ${target}`;
    case 'scrub_start': return 'Start pool scrub';
    case 'scrub_cancel': return 'Cancel pool scrub';
    case 'reboot': return 'Reboot the host';
    case 'reboot_cancel': return 'Cancel pending automatic reboot';
    default: return text(action, 'Host action');
  }
}
function confirmAction(action, target) {
  if (app.actionBusy || !actionsAvailable(app.stats)) {
    toast('error', 'Action unavailable', actionUnavailableReason(app.stats));
    return;
  }
  app.pendingAction = { action, target };
  $('confirm-title').textContent = actionLabel(action, target);
  $('confirm-detail').textContent = `Issue “${actionLabel(action, target)}” as a manual request to this NAS?`;
  $('confirm-ok').disabled = false;
  $('confirm-ok').removeAttribute('aria-busy');
  $('confirm-ok').textContent = 'Issue request';
  $('confirm-cancel').disabled = false;
  $('modal-confirm').showModal();
  requestAnimationFrame(() => $('confirm-cancel').focus());
}
async function runAction() {
  const pending = app.pendingAction;
  if (!pending || app.actionBusy || !actionsAvailable(app.stats)) return;
  app.actionBusy = true;
  $('confirm-ok').disabled = true;
  $('confirm-ok').setAttribute('aria-busy', 'true');
  $('confirm-ok').textContent = 'Issuing…';
  $('confirm-cancel').disabled = true;
  try {
    const response = await api('/api/v1/actions', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: pending.action, target: pending.target, confirm: true }),
    });
    if (response.ok !== true) throw new APIError(text(response.message, 'Watchdog rejected the action request.'));
    const result = text(response.message, 'Action request accepted.');
    app.actionMessage = `${result} Request issued; verify completion in a later telemetry sample.`;
    $('modal-confirm').close();
    app.pendingAction = null;
    toast('success', 'Request issued — not confirmed complete', result);
    setTimeout(() => { if (!document.hidden) refresh(true); }, 1500);
  } catch (error) {
    $('confirm-detail').textContent = `Request outcome could not be confirmed: ${error instanceof Error ? error.message : 'Unknown request error'}. The request may have reached the NAS; check its state before retrying.`;
    toast('error', 'Action outcome unconfirmed', error instanceof Error ? error.message : 'Unknown request error');
  } finally {
    app.actionBusy = false;
    $('confirm-ok').disabled = false;
    $('confirm-ok').removeAttribute('aria-busy');
    $('confirm-ok').textContent = 'Issue request';
    $('confirm-cancel').disabled = false;
    if (!isEditingData()) render({ preserve: true });
  }
}

/* ---------- Request-guarded logs ---------- */
function logKey(source, lines, query) {
  return JSON.stringify([String(source), M.boundedLineLimit(lines), String(query || '').trim()]);
}
function visibleLogEntry() {
  const key = logKey(app.log.source, app.log.lines, app.log.q);
  const current = app.log.cache.get(key);
  if (current) return { ...current, key, previous: false };
  if (app.log.errors.has(key) && app.log.lastGoodKey && app.log.cache.has(app.log.lastGoodKey)) {
    return { ...app.log.cache.get(app.log.lastGoodKey), key: app.log.lastGoodKey, previous: true };
  }
  return null;
}
async function copyText(value) {
  if (!value) { toast('error', 'Nothing to copy', 'No log output is loaded.'); return; }
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(value);
    } else {
      const textarea = document.createElement('textarea');
      textarea.value = value;
      textarea.setAttribute('readonly', '');
      textarea.className = 'visually-hidden';
      document.body.appendChild(textarea);
      textarea.select();
      const copied = document.execCommand && document.execCommand('copy');
      textarea.remove();
      if (!copied) throw new Error('Clipboard access is unavailable.');
    }
    toast('success', 'Copied', 'The currently displayed log output is on the clipboard.');
  } catch (error) {
    toast('error', 'Copy failed', error instanceof Error ? error.message : 'Clipboard access was denied.');
  }
}
function updateLogControls() {
  const key = logKey(app.log.source, app.log.lines, app.log.q);
  const load = $('log-load');
  if (load) {
    load.disabled = !!app.log.pendingKey || app.status !== 'live';
    load.textContent = app.log.pendingKey === key ? 'Loading…' : 'Load logs';
    load.setAttribute('aria-busy', String(app.log.pendingKey === key));
  }
  const copy = $('log-copy');
  const visible = visibleLogEntry();
  if (copy) copy.disabled = !(visible && visible.text);
}
async function loadLog() {
  if (app.route !== 'logs' || !app.stats || app.status !== 'live') return;
  const queryInput = $('log-q');
  const linesInput = $('log-lines');
  if (queryInput) app.log.q = queryInput.value.trim();
  if (linesInput) app.log.lines = M.boundedLineLimit(linesInput.value);
  if (linesInput) linesInput.value = String(app.log.lines);
  STORE.set('wd_log_lines', app.log.lines);
  const source = app.log.source;
  const key = logKey(source, app.log.lines, app.log.q);
  if (app.log.pendingKey === key) return;
  if (app.log.controller) app.log.controller.abort();
  const controller = new AbortController();
  const revision = ++app.log.revision;
  app.log.controller = controller;
  app.log.pendingKey = key;
  updateLogControls();
  const body = $('log-body');
  const previousNearBottom = !body || body.scrollHeight - body.scrollTop - body.clientHeight < 28;
  try {
    const endpoint = source === 'journal' ? '/api/v1/journal' : source === 'dmesg' ? '/api/v1/dmesg' : `/api/v1/logs?source=${encodeURIComponent(source)}`;
    const separator = endpoint.includes('?') ? '&' : '?';
    const query = app.log.q ? `&q=${encodeURIComponent(app.log.q)}` : '';
    const response = await api(`${endpoint}${separator}lines=${app.log.lines}${query}`, { signal: controller.signal });
    if (revision !== app.log.revision) return;
    const lines = Array.isArray(response.lines) ? response.lines.map((line) => String(line)) : [];
    const output = lines.length ? lines.join('\n') : '(no matching lines)';
    app.log.cache.set(key, { text: output, lines: lines.length, receivedAt: Date.now() });
    app.log.lastGoodKey = key;
    app.log.errors.delete(key);
    while (app.log.cache.size > 8) app.log.cache.delete(app.log.cache.keys().next().value);
    if (app.route === 'logs' && logKey(app.log.source, app.log.lines, app.log.q) === key && $('log-body')) {
      $('log-body').textContent = output;
      if (previousNearBottom) $('log-body').scrollTop = $('log-body').scrollHeight;
      $('log-error').textContent = '';
    }
  } catch (error) {
    if (error && error.name === 'AbortError') return;
    if (revision !== app.log.revision) return;
    const message = error instanceof Error ? error.message : 'Log request failed.';
    app.log.errors.set(key, message);
    if (app.route === 'logs' && logKey(app.log.source, app.log.lines, app.log.q) === key && $('log-error')) $('log-error').textContent = `Refresh failed: ${message} Previous output is preserved.`;
  } finally {
    if (revision === app.log.revision) {
      app.log.pendingKey = '';
      app.log.controller = null;
      updateLogControls();
    }
  }
}
function selectLogSource(source) {
  if (!source || app.log.source === source) return;
  app.log.source = source;
  render({ preserve: true });
  loadLog();
}
async function openContainerLogs(name) {
  if (!name || app.status !== 'live') return;
  const exists = app.stats && (app.stats.containers || []).some((container) => container.name === name);
  if (!exists) { toast('error', 'Container unavailable', 'The selected container is not present in the latest live inventory.'); return; }
  if (app.containerLog.controller) app.containerLog.controller.abort();
  const controller = new AbortController();
  const revision = ++app.containerLog.revision;
  if (app.containerLog.name !== name) {
    app.containerLog.name = name;
    app.containerLog.text = '';
    app.containerLog.error = '';
  }
  app.containerLog.controller = controller;
  app.containerLog.pending = true;
  $('logs-title').textContent = `${name} · logs`;
  $('logs-body').textContent = app.containerLog.text || 'Loading container output…';
  $('container-log-error').textContent = '';
  $('container-log-state').textContent = 'Loading…';
  $('logs-refresh').disabled = true;
  if (!$('modal-logs').open) $('modal-logs').showModal();
  try {
    const response = await api(`/api/v1/containers/logs?name=${encodeURIComponent(name)}&tail=400`, { signal: controller.signal });
    if (revision !== app.containerLog.revision) return;
    app.containerLog.text = response.text ? String(response.text) : '(no output)';
    app.containerLog.error = '';
    $('logs-body').textContent = app.containerLog.text;
    $('logs-body').scrollTop = $('logs-body').scrollHeight;
    $('container-log-state').textContent = 'Latest 400 lines received';
  } catch (error) {
    if (error && error.name === 'AbortError') return;
    if (revision !== app.containerLog.revision) return;
    app.containerLog.error = error instanceof Error ? error.message : 'Container log request failed.';
    $('container-log-error').textContent = `${app.containerLog.error} Previous output is preserved.`;
    $('container-log-state').textContent = 'Refresh failed';
  } finally {
    if (revision === app.containerLog.revision) {
      app.containerLog.pending = false;
      app.containerLog.controller = null;
      $('logs-refresh').disabled = false;
    }
  }
}

/* ---------- Bindings, authentication, theme, and boot ---------- */
function applyFilter(kind) {
  const input = document.querySelector(`[data-filter-input="${kind}"]`);
  const query = kind === 'processes' ? app.procQ : app.ctrQ;
  const items = Array.from(document.querySelectorAll(`[data-filter-item="${kind}"]`));
  let visible = 0;
  items.forEach((item) => {
    const match = M.textMatches(query, item.textContent);
    item.hidden = !match;
    if (match) visible += 1;
  });
  const results = $(`${kind}-results`);
  if (results) results.textContent = query ? `${visible} of ${items.length} shown · refresh is deferred while editing` : `${items.length} reported`;
  if (input && input.value !== query) input.value = query;
}
async function saveToken(token) {
  const normalized = String(token || '').trim();
  if (!normalized) { toast('error', 'Token is empty', 'Enter a token or use Clear token to remove the saved token.'); return; }
  app.token = normalized;
  if (!STORE.set('wd_token', normalized)) toast('error', 'Browser storage unavailable', 'The token is active in memory but could not be saved for future visits.');
  if ($('modal-token').open) $('modal-token').close();
  tokenDialogOpen = false;
  $('token-input').value = '';
  $('token-error').textContent = '';
  if ($('set-token')) $('set-token').value = '';
  clearTelemetry();
  await refresh(false);
}
async function clearToken() {
  app.token = '';
  STORE.remove('wd_token');
  app.pendingAction = null;
  if ($('set-token')) $('set-token').value = '';
  if ($('token-input')) $('token-input').value = '';
  clearTelemetry();
  render({ preserve: false });
  await refresh(false);
}
function setTheme(theme) {
  if (!['dark', 'light', 'system'].includes(theme)) return;
  app.theme = theme;
  document.documentElement.dataset.theme = theme;
  STORE.set('wd_theme', theme);
  render({ preserve: true });
}
function seedTokenFromURL() {
  const parameters = new URLSearchParams(location.search);
  const token = parameters.get('token');
  if (!token) return;
  app.token = token;
  STORE.set('wd_token', token);
  parameters.delete('token');
  const query = parameters.toString();
  history.replaceState(null, '', `${location.pathname}${query ? `?${query}` : ''}${location.hash}`);
}
function bindEvents() {
  $('btn-refresh').addEventListener('click', () => refresh(false));
  $('token-form').addEventListener('submit', (event) => { event.preventDefault(); saveToken($('token-input').value); });
  $('token-dismiss').addEventListener('click', () => $('modal-token').close());
  $('modal-token').addEventListener('close', () => { tokenDialogOpen = false; });
  $('confirm-cancel').addEventListener('click', () => { if (!app.actionBusy) $('modal-confirm').close(); });
  $('confirm-ok').addEventListener('click', runAction);
  $('modal-confirm').addEventListener('close', () => { if (!app.actionBusy) app.pendingAction = null; });
  $('logs-close').addEventListener('click', () => $('modal-logs').close());
  $('logs-refresh').addEventListener('click', () => { if (!app.containerLog.pending) openContainerLogs(app.containerLog.name); });
  $('logs-copy').addEventListener('click', () => copyText(app.containerLog.text));
  $('modal-logs').addEventListener('close', () => {
    app.containerLog.revision += 1;
    if (app.containerLog.controller) app.containerLog.controller.abort();
    app.containerLog.controller = null;
    app.containerLog.pending = false;
  });

  document.body.addEventListener('click', (event) => {
    const range = event.target.closest('[data-range]');
    if (range) {
      if (app.rangeBusy || app.range === range.dataset.range || !RANGES.includes(range.dataset.range)) return;
      app.range = range.dataset.range;
      STORE.set('wd_range', app.range);
      app.rangeBusy = true;
      app.hist = app.historyByRange.get(app.range) || [];
      app.histRange = app.range;
      app.historyError = '';
      app.sampleVisible.cpu = app.sampleVisible.thermal = false;
      app.sampleLimits.cpu = app.sampleLimits.thermal = 100;
      render({ preserve: true });
      refresh(true);
      return;
    }
    const action = event.target.closest('[data-act]');
    if (action) { confirmAction(action.dataset.act, action.dataset.target || ''); return; }
    const logs = event.target.closest('[data-logs]');
    if (logs) { openContainerLogs(logs.dataset.logs); return; }
    const source = event.target.closest('[data-source]');
    if (source) { selectLogSource(source.dataset.source); return; }
    const samples = event.target.closest('[data-samples]');
    if (samples) {
      const kind = samples.dataset.samples;
      if (!Object.hasOwn(app.sampleVisible, kind)) return;
      app.sampleVisible[kind] = !app.sampleVisible[kind];
      render({ preserve: true });
      return;
    }
    const moreSamples = event.target.closest('[data-more-samples]');
    if (moreSamples) {
      const kind = moreSamples.dataset.moreSamples;
      if (!Object.hasOwn(app.sampleLimits, kind)) return;
      app.sampleLimits[kind] += 100;
      render({ preserve: true });
      return;
    }
    if (event.target.closest('#log-load')) { loadLog(); return; }
    if (event.target.closest('#log-copy')) { copyText((visibleLogEntry() || {}).text || ''); return; }
    if (event.target.closest('#set-save')) { saveToken($('set-token').value); return; }
    if (event.target.closest('#set-clear')) { clearToken(); return; }
    if (event.target.closest('#empty-retry')) { refresh(false); }
  });

  document.body.addEventListener('input', (event) => {
    const input = event.target;
    if (input.id === 'proc-q') { app.procQ = input.value; applyFilter('processes'); }
    if (input.id === 'ctr-q') { app.ctrQ = input.value; applyFilter('containers'); }
    if (input.id === 'log-q') app.log.q = input.value;
    if (input.id === 'log-lines' && input.validity.valid) app.log.lines = M.boundedLineLimit(input.value);
  });
  document.body.addEventListener('change', (event) => {
    const input = event.target;
    if (input.id === 'thermal-sensor') {
      app.thermalKey = input.value;
      app.sampleVisible.thermal = false;
      app.sampleLimits.thermal = 100;
      render({ preserve: true });
    }
    if (input.id === 'log-source-select') selectLogSource(input.value);
    if (input.id === 'log-auto') {
      app.log.auto = input.checked;
      render({ preserve: true });
      if (app.log.auto) loadLog();
      schedulePoll();
    }
    if (input.id === 'log-lines') { app.log.lines = M.boundedLineLimit(input.value); input.value = String(app.log.lines); STORE.set('wd_log_lines', app.log.lines); }
    if (input.id === 'set-theme') setTheme(input.value);
    if (input.id === 'set-refresh') {
      const value = Number(input.value);
      if (![5000, 10000, 30000, 60000].includes(value)) return;
      app.refreshMs = value;
      STORE.set('wd_refresh', value);
      schedulePoll();
      toast('success', 'Refresh cadence updated', `Visible tabs refresh every ${value / 1000} seconds; hidden tabs pause polling.`);
      refresh(true);
    }
  });
  document.body.addEventListener('keydown', (event) => {
    if (event.target.id === 'log-q' && event.key === 'Enter') { event.preventDefault(); loadLog(); }
  });
  document.addEventListener('focusout', () => {
    setTimeout(() => {
      if (app.deferredRender && !isEditingData()) render({ preserve: true });
    }, 0);
  });
  window.addEventListener('hashchange', routeChanged);
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) schedulePoll();
    else { if (pollTimer) clearTimeout(pollTimer); refresh(true); }
  });
  window.matchMedia('(prefers-color-scheme: light)').addEventListener('change', () => { if (app.theme === 'system') render({ preserve: true }); });
  freshnessTimer = setInterval(() => { updateChromeStatus(); $('route-subtitle').textContent = routeSubtitle(); }, 5000);
}
async function boot() {
  seedTokenFromURL();
  document.documentElement.dataset.theme = app.theme;
  bindEvents();
  render({ preserve: false });
  await refresh(false);
  schedulePoll();
  if (app.route === 'logs') loadLog();
}

document.addEventListener('DOMContentLoaded', boot, { once: true });
