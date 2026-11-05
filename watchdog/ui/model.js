'use strict';

/*
 * Pure presentation models shared by the browser and dependency-free Node tests.
 * No values are inferred when the watchdog did not report them.
 */
(function exposeModel(root, factory) {
  const model = factory();
  if (typeof module === 'object' && module.exports) module.exports = model;
  if (root) root.WatchdogModel = model;
}(typeof globalThis !== 'undefined' ? globalThis : this, function modelFactory() {
  const MISSING = 'not reported';
  const RANGE_LABELS = Object.freeze({ '5m': '5 minutes', '1h': '1 hour', '6h': '6 hours', '24h': '24 hours' });
  const ROUTES = Object.freeze([
    'overview', 'system', 'storage', 'network', 'containers', 'logs',
    'alerts', 'actions', 'settings', 'more', 'cpu', 'thermal',
  ]);

  function escapeHTML(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function finite(value) {
    if (value === null || value === undefined || value === '' || typeof value === 'boolean') return null;
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  }

  function clamp(value, minimum, maximum) {
    const number = finite(value);
    return number === null ? minimum : Math.min(maximum, Math.max(minimum, number));
  }

  function formatNumber(value, digits = 1) {
    const number = finite(value);
    return number === null ? MISSING : number.toFixed(digits);
  }

  function formatInteger(value) {
    const number = finite(value);
    return number === null ? MISSING : Math.round(number).toLocaleString('en-US');
  }

  function formatPercent(value, digits = 1) {
    const number = finite(value);
    return number === null ? MISSING : `${number.toFixed(digits)}%`;
  }

  function formatBytes(value, digits = 1) {
    let number = finite(value);
    if (number === null || number < 0) return MISSING;
    const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB'];
    let index = 0;
    while (number >= 1024 && index < units.length - 1) {
      number /= 1024;
      index += 1;
    }
    const precision = number >= 100 || index === 0 ? 0 : digits;
    return `${number.toFixed(precision)} ${units[index]}`;
  }

  function formatRate(value) {
    const bytes = formatBytes(value);
    return bytes === MISSING ? MISSING : `${bytes}/s`;
  }

  function formatDuration(value) {
    const input = finite(value);
    if (input === null) return MISSING;
    const seconds = Math.max(0, Math.floor(input));
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    if (days) return `${days}d ${hours}h`;
    if (hours) return `${hours}h ${minutes}m`;
    if (minutes) return `${minutes}m ${seconds % 60}s`;
    return `${seconds}s`;
  }

  function formatCompactDuration(milliseconds) {
    const input = finite(milliseconds);
    if (input === null) return MISSING;
    if (input >= 0 && input < 1000) return '<1s';
    return formatDuration(input / 1000);
  }

  function epoch(value) {
    if (typeof value === 'number') return Number.isFinite(value) ? value : null;
    if (!value) return null;
    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? parsed : null;
  }

  function formatTime(value) {
    const time = epoch(value);
    return time === null ? MISSING : new Date(time).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }

  function formatDateTime(value) {
    const time = epoch(value);
    return time === null ? MISSING : new Date(time).toLocaleString();
  }

  function formatTemperature(value, signed = false) {
    const number = finite(value);
    if (number === null) return MISSING;
    return `${signed && number > 0 ? '+' : ''}${number.toFixed(1)}°C`;
  }

  function eventTimestamp(event) {
    return event && event.ts ? event.ts : (event && event.alert ? event.alert.since : '');
  }

  function routeFromHash(hash) {
    const route = String(hash || '').replace(/^#\/?/, '').split(/[?&]/)[0];
    return ROUTES.includes(route) ? route : 'overview';
  }

  function textMatches(query, ...values) {
    const needle = String(query || '').trim().toLocaleLowerCase();
    if (!needle) return true;
    return values.flat(Infinity).join(' ').toLocaleLowerCase().includes(needle);
  }

  function boundedLineLimit(value) {
    const parsed = Math.round(finite(value) === null ? 300 : finite(value));
    return Math.min(2000, Math.max(10, parsed));
  }

  /* Every timestamp is represented, including timestamps with no value for a series. */
  function historyPoints(samples, read) {
    const getter = typeof read === 'function' ? read : (sample) => sample && sample.temps ? sample.temps[read] : null;
    return (Array.isArray(samples) ? samples : []).map((sample, sourceIndex) => {
      const time = epoch(sample && sample.ts);
      const raw = getter(sample || {});
      const value = finite(raw);
      return { epochMillis: time === null ? -1 : time, value, sourceIndex };
    }).filter((point) => point.epochMillis >= 0).sort((a, b) => a.epochMillis - b.epochMillis);
  }

  function historySummary(samples, read) {
    const valid = historyPoints(samples, read).filter((point) => point.value !== null);
    const values = valid.map((point) => point.value);
    if (!values.length) {
      return { count: 0, first: null, latest: null, minimum: null, average: null, maximum: null, change: null, firstAt: null, latestAt: null };
    }
    const total = values.reduce((sum, value) => sum + value, 0);
    return {
      count: values.length,
      first: values[0],
      latest: values[values.length - 1],
      minimum: Math.min(...values),
      average: total / values.length,
      maximum: Math.max(...values),
      change: values.length > 1 ? values[values.length - 1] - values[0] : null,
      firstAt: valid[0].epochMillis,
      latestAt: valid[valid.length - 1].epochMillis,
    };
  }

  /* Missing values and backwards time split runs; durations are never extrapolated over gaps. */
  function contiguousThresholdRun(samples, read, thresholdValue) {
    const threshold = finite(thresholdValue);
    const empty = { currentMillis: null, longestMillis: null, currentStartMillis: null };
    if (threshold === null) return empty;
    let runStart = null;
    let runEnd = null;
    let longest = null;
    let previousTimestamp = null;

    function finishRun() {
      if (runStart !== null && runEnd !== null) {
        const duration = Math.max(0, runEnd - runStart);
        longest = longest === null ? duration : Math.max(longest, duration);
      }
      runStart = null;
      runEnd = null;
    }

    historyPoints(samples, read).forEach((point) => {
      const connected = point.value !== null &&
        (previousTimestamp === null || point.epochMillis > previousTimestamp);
      if (!connected) {
        finishRun();
        previousTimestamp = null;
        return;
      }
      if (point.value >= threshold) {
        if (runStart === null) runStart = point.epochMillis;
        runEnd = point.epochMillis;
        longest = longest === null ? 0 : Math.max(longest, runEnd - runStart);
      } else {
        finishRun();
      }
      previousTimestamp = point.epochMillis;
    });
    return {
      currentMillis: runStart === null || runEnd === null ? null : Math.max(0, runEnd - runStart),
      longestMillis: longest,
      currentStartMillis: runStart,
    };
  }

  function validThreshold(value) {
    const number = finite(value);
    return number !== null && number > 0 ? number : null;
  }

  function warningThreshold(sensor) {
    return validThreshold(sensor && sensor.warn);
  }

  function criticalThreshold(sensor) {
    if (!sensor) return null;
    const limits = sensor.limits || {};
    const candidates = [sensor.crit, limits.crit_c, limits.emergency_c]
      .map(validThreshold).filter((value) => value !== null);
    return candidates.length ? Math.min(...candidates) : null;
  }

  function criticalHeadroom(sensor) {
    const current = finite(sensor && sensor.c);
    const limit = criticalThreshold(sensor);
    return current === null || limit === null ? null : { sensor, limitC: limit, headroomC: limit - current };
  }

  const CATEGORY_LABELS = Object.freeze({
    cpu_package: 'CPU package', cpu_core: 'CPU cores', memory: 'Memory', nvme: 'NVMe',
    sata: 'SATA / SMART', board: 'Board', other: 'Other components', firmware_zone: 'Firmware thermal zones',
  });
  const CATEGORY_ORDER = Object.freeze(Object.keys(CATEGORY_LABELS));

  function inferSensorCategory(temp) {
    const explicit = String(temp && temp.category || '').trim().toLowerCase();
    if (explicit === 'dimm') return 'memory';
    if (explicit === 'hdd') return 'sata';
    if (CATEGORY_ORDER.includes(explicit) && explicit !== 'firmware_zone') return explicit;
    const identity = `${temp && temp.key || ''} ${temp && temp.label || ''} ${temp && temp.device || ''}`.toLowerCase();
    if ((temp && temp.key === 'cpu') || identity.includes('package')) return 'cpu_package';
    if (identity.includes('cpu_core') || (identity.includes('core') && identity.includes('cpu'))) return 'cpu_core';
    if (identity.includes('dimm') || identity.includes('memory')) return 'memory';
    if (identity.includes('nvme')) return 'nvme';
    if (String(temp && temp.source || '').toLowerCase() === 'smart' || identity.includes('/dev/sd') || identity.includes('hdd')) return 'sata';
    if (identity.includes('board') || identity.includes('acpitz')) return 'board';
    return 'other';
  }

  function normalizeSensor(temp) {
    if (!temp || !String(temp.key || '').trim() || finite(temp.c) === null) return null;
    return {
      ...temp,
      key: String(temp.key),
      label: String(temp.label || temp.key),
      c: finite(temp.c),
      category: inferSensorCategory(temp),
      logicalCpus: Array.from(new Set((temp.cpus || []).map(finite).filter((id) => id !== null && id >= 0))).sort((a, b) => a - b),
      alarms: (temp.alarms || []).filter((alarm) => String(alarm || '').trim()),
    };
  }

  function normalizeZone(zone) {
    if (!zone || !String(zone.key || '').trim()) return null;
    return {
      ...zone,
      label: String(zone.label || zone.key),
      c: finite(zone.c),
      category: 'firmware_zone',
      alarms: [],
      logicalCpus: [],
    };
  }

  function sensorHealth(sensor) {
    const criticalAlarm = (sensor && sensor.alarms || []).some((alarm) => {
      const name = String(alarm).toLowerCase();
      return name === 'alarm' || name === 'temp_alarm' || name.includes('crit') || name.includes('emerg');
    });
    const current = finite(sensor && sensor.c);
    const critical = criticalThreshold(sensor);
    const warning = warningThreshold(sensor);
    if (criticalAlarm || (current !== null && critical !== null && current >= critical)) return 'critical';
    if ((sensor && sensor.alarms || []).length || (current !== null && warning !== null && current >= warning)) return 'warning';
    return 'normal';
  }

  function selectCPUTemperature(temps) {
    const list = Array.isArray(temps) ? temps : [];
    return list.find((temp) => temp && temp.key === 'cpu') || list.find((temp) => {
      const identity = `${temp && temp.label || ''} ${temp && temp.key || ''}`.toLowerCase();
      return identity.includes('cpu') || identity.includes('package');
    }) || null;
  }

  function selectThermalSensor(sensors, requestedKey) {
    const list = Array.isArray(sensors) ? sensors : [];
    return list.find((sensor) => sensor.key === requestedKey) ||
      list.find((sensor) => sensor.category === 'cpu_package') ||
      list.find((sensor) => sensor.category !== 'firmware_zone') || list[0] || null;
  }

  function physicalCoreId(sensor) {
    const keyMatch = /^cpu_core_(\d+)$/.exec(sensor && sensor.key || '');
    if (keyMatch) return Number(keyMatch[1]);
    for (const value of [sensor && sensor.sensor, sensor && sensor.label]) {
      const match = /(?:^|\D)(\d+)$/.exec(String(value || ''));
      if (match) return Number(match[1]);
    }
    return null;
  }

  function smartForSensor(sensor, smart) {
    if (!sensor || !sensor.device) return null;
    return (smart || []).find((drive) => drive.device === sensor.device ||
      (sensor.category === 'nvme' && String(drive.device || '').startsWith(`${sensor.device}n`))) || null;
  }

  function isThermalAlert(alert) {
    const id = String(alert && alert.id || '').toLowerCase();
    const prose = `${alert && alert.title || ''} ${alert && alert.detail || ''}`.toLowerCase();
    return id.startsWith('temp') || id.startsWith('thermal') || id.startsWith('fan') ||
      ['temperature', 'thermal', 'overheat', 'throttl', 'cooling', 'fan rpm'].some((term) => prose.includes(term));
  }

  function sortedEvents(events, thermalOnly = false) {
    return (Array.isArray(events) ? events : []).filter((event) => !thermalOnly || isThermalAlert(event && event.alert)).slice().sort((a, b) => {
      const aTime = epoch(eventTimestamp(a));
      const bTime = epoch(eventTimestamp(b));
      return (bTime === null ? -Infinity : bTime) - (aTime === null ? -Infinity : aTime) ||
        String(b && b.alert && b.alert.id || '').localeCompare(String(a && a.alert && a.alert.id || ''));
    });
  }

  function buildThermalModel(stats) {
    const source = stats || {};
    const physical = (source.temps || []).map(normalizeSensor).filter(Boolean);
    const zones = (source.thermal_zones || []).map(normalizeZone).filter(Boolean);
    const sensors = physical.concat(zones);
    const logicalCores = source.cpu && source.cpu.per_core_detail || [];
    const cores = physical.filter((sensor) => sensor.category === 'cpu_core').map((sensor) => {
      const associated = sensor.logicalCpus.map((id) => logicalCores.find((core) => core.id === id)).filter(Boolean);
      const loads = associated.map((core) => finite(core.usage)).filter((value) => value !== null);
      const frequencies = associated.map((core) => finite(core.freq_mhz)).filter((value) => value !== null && value > 0);
      return {
        sensor,
        physicalCoreId: physicalCoreId(sensor),
        loadPct: loads.length ? loads.reduce((sum, value) => sum + value, 0) / loads.length : null,
        minFreqMHz: frequencies.length ? Math.min(...frequencies) : null,
        maxFreqMHz: frequencies.length ? Math.max(...frequencies) : null,
      };
    }).sort((a, b) => (a.physicalCoreId === null ? Infinity : a.physicalCoreId) -
      (b.physicalCoreId === null ? Infinity : b.physicalCoreId) || a.sensor.key.localeCompare(b.sensor.key));

    const hottest = physical.length ? physical.reduce((a, b) => a.c >= b.c ? a : b) : null;
    const cpuPackage = physical.find((sensor) => sensor.key === 'cpu') || physical.find((sensor) => sensor.category === 'cpu_package') || null;
    const hottestCore = cores.length ? cores.map((core) => core.sensor).reduce((a, b) => a.c >= b.c ? a : b) : null;
    const headroom = physical.map(criticalHeadroom).filter(Boolean).sort((a, b) => a.headroomC - b.headroomC)[0] || null;
    const coreValues = cores.map((core) => core.sensor.c);
    const activeThermalAlerts = (source.alerts || []).filter((alert) => alert.active !== false && isThermalAlert(alert));
    const sensorStates = physical.map(sensorHealth);
    const health = !physical.length && !zones.length && !activeThermalAlerts.length ? 'no_data' :
      sensorStates.includes('critical') || activeThermalAlerts.some((alert) => alert.severity === 'critical') ? 'critical' :
      sensorStates.includes('warning') || activeThermalAlerts.some((alert) => alert.severity === 'warning') ? 'warning' : 'normal';

    const nvmeGroupsMap = new Map();
    physical.filter((sensor) => sensor.category === 'nvme').forEach((sensor) => {
      const id = sensor.device || String(sensor.key).split('_sensor_')[0] || sensor.key;
      if (!nvmeGroupsMap.has(id)) nvmeGroupsMap.set(id, []);
      nvmeGroupsMap.get(id).push(sensor);
    });
    const nvmeGroups = Array.from(nvmeGroupsMap, ([id, readings]) => {
      const index = id.split('nvme')[1] ? id.split('nvme')[1].replace(/\D/g, '') : '';
      return {
        id,
        label: id.startsWith('/dev/') ? id : index ? `NVMe ${index}` : id,
        sensors: readings.slice().sort((a, b) => Number(a.key.includes('_sensor_')) - Number(b.key.includes('_sensor_'))),
      };
    }).sort((a, b) => a.id.localeCompare(b.id));

    const pickerGroups = CATEGORY_ORDER.map((category) => ({
      category,
      label: CATEGORY_LABELS[category],
      sensors: sensors.filter((sensor) => sensor.category === category),
    })).filter((group) => group.sensors.length);

    return {
      physical, zones, sensors, cores, pickerGroups, nvmeGroups, hottest, cpuPackage, hottestCore,
      criticalHeadroom: headroom,
      coreSpreadC: coreValues.length >= 2 ? Math.max(...coreValues) - Math.min(...coreValues) : null,
      activeThermalAlerts,
      health,
    };
  }

  function fanHistorySummary(samples, key) {
    if (!key) return { points: [], minimumRpm: null, maximumRpm: null, count: 0 };
    const read = (sample) => sample && sample.fans && sample.fans[key] ? sample.fans[key].rpm : null;
    const points = historyPoints(samples, read);
    const summary = historySummary(samples, read);
    return {
      points,
      minimumRpm: summary.minimum === null ? null : Math.round(summary.minimum),
      maximumRpm: summary.maximum === null ? null : Math.round(summary.maximum),
      count: summary.count,
    };
  }

  return Object.freeze({
    MISSING, RANGE_LABELS, ROUTES, CATEGORY_LABELS,
    escapeHTML, finite, clamp, formatNumber, formatInteger, formatPercent, formatBytes, formatRate,
    formatDuration, formatCompactDuration, formatTemperature, formatTime, formatDateTime, epoch,
    eventTimestamp, routeFromHash, textMatches, boundedLineLimit, historyPoints, historySummary,
    contiguousThresholdRun, validThreshold, warningThreshold, criticalThreshold, criticalHeadroom,
    inferSensorCategory, sensorHealth, selectCPUTemperature, selectThermalSensor, physicalCoreId,
    smartForSensor, isThermalAlert, sortedEvents, buildThermalModel, fanHistorySummary,
  });
}));
