'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const M = require('../ui/model.js');

const ts = (milliseconds) => new Date(milliseconds).toISOString();

test('escapes every HTML-significant character, including metric-like strings', () => {
  assert.equal(M.escapeHTML(`<img src=x onerror="alert('1')">&`), '&lt;img src=x onerror=&quot;alert(&#39;1&#39;)&quot;&gt;&amp;');
  assert.equal(M.escapeHTML(null), '');
});

test('formatting distinguishes zero from missing or non-finite values', () => {
  assert.equal(M.formatNumber(0), '0.0');
  assert.equal(M.formatPercent(0), '0.0%');
  assert.equal(M.formatInteger(0), '0');
  assert.equal(M.formatTemperature(0), '0.0°C');
  assert.equal(M.formatNumber(null), M.MISSING);
  assert.equal(M.formatNumber(undefined), M.MISSING);
  assert.equal(M.formatBytes(NaN), M.MISSING);
  assert.equal(M.formatBytes(-1), M.MISSING);
  assert.equal(M.formatBytes(1024), '1.0 KiB');
});

test('route parser retains all legacy hash bookmarks and safe defaults', () => {
  for (const route of M.ROUTES) assert.equal(M.routeFromHash(`#/${route}`), route);
  assert.equal(M.routeFromHash('#cpu'), 'cpu');
  assert.equal(M.routeFromHash('#/../../private'), 'overview');
  assert.equal(M.routeFromHash(''), 'overview');
});

test('log line limits are finite, integral, and bounded', () => {
  assert.equal(M.boundedLineLimit(1), 10);
  assert.equal(M.boundedLineLimit(4999), 2000);
  assert.equal(M.boundedLineLimit('400.4'), 400);
  assert.equal(M.boundedLineLimit('bad'), 300);
});

test('history points preserve timestamps with null series gaps', () => {
  const samples = [
    { ts: ts(1000), temps: { cpu: 40 } },
    { ts: ts(2000), temps: {} },
    { ts: ts(3000), temps: { cpu: 0 } },
  ];
  assert.deepEqual(M.historyPoints(samples, 'cpu').map((point) => point.value), [40, null, 0]);
  assert.deepEqual(M.historyPoints(samples, 'cpu').map((point) => point.epochMillis), [1000, 2000, 3000]);
});

test('history statistics exclude gaps but preserve valid zero samples', () => {
  const samples = [
    { ts: ts(1000), cpu: 10 }, { ts: ts(2000) }, { ts: ts(3000), cpu: 0 }, { ts: ts(4000), cpu: 20 },
  ];
  const summary = M.historySummary(samples, (sample) => sample.cpu);
  assert.equal(summary.count, 3);
  assert.equal(summary.first, 10);
  assert.equal(summary.latest, 20);
  assert.equal(summary.minimum, 0);
  assert.equal(summary.maximum, 20);
  assert.equal(summary.average, 10);
  assert.equal(summary.change, 10);
  assert.equal(historyEmptyCount(), 0);
  function historyEmptyCount() { return M.historySummary([], (sample) => sample.cpu).count; }
});

test('threshold runs do not bridge missing samples or infer duration across gaps', () => {
  const samples = [
    { ts: ts(1000), temps: { cpu: 70 } },
    { ts: ts(2000), temps: { cpu: 72 } },
    { ts: ts(3000), temps: {} },
    { ts: ts(4000), temps: { cpu: 75 } },
    { ts: ts(5000), temps: { cpu: 76 } },
    { ts: ts(4500), temps: { cpu: 77 } },
  ];
  /* historyPoints sorts samples before analysis; the missing 3000 sample still splits runs. */
  const run = M.contiguousThresholdRun(samples, 'cpu', 70);
  assert.equal(run.longestMillis, 1000);
  assert.equal(run.currentMillis, 1000);
  const stopped = M.contiguousThresholdRun(samples.slice(0, 2), 'cpu', 80);
  assert.equal(stopped.longestMillis, null);
  assert.equal(stopped.currentMillis, null);
});

test('a threshold run with one qualifying sample is zero length, not fabricated duration', () => {
  const run = M.contiguousThresholdRun([{ ts: ts(1000), cpu: 60 }], (sample) => sample.cpu, 50);
  assert.deepEqual(run, { currentMillis: 0, longestMillis: 0, currentStartMillis: 1000 });
});

test('critical thresholds use the most conservative real value and ignore zero sentinels', () => {
  const sensor = { c: 80, warn: 0, crit: 0, limits: { crit_c: 100, emergency_c: 90 } };
  assert.equal(M.warningThreshold(sensor), null);
  assert.equal(M.criticalThreshold(sensor), 90);
  assert.equal(M.criticalHeadroom(sensor).headroomC, 10);
  assert.equal(M.sensorHealth(sensor), 'normal');
  assert.equal(M.sensorHealth({ ...sensor, alarms: ['temp_emerg_alarm'] }), 'critical');
});

test('thermal model groups channels, respects missing values, and identifies core associations', () => {
  const model = M.buildThermalModel({
    temps: [
      { key: 'cpu', label: 'Package', c: 60, category: 'cpu_package', crit: 100 },
      { key: 'cpu_core_1', label: 'Core 1', c: 65, category: 'cpu_core', cpus: [2, 3], core_type: 'efficiency' },
      { key: 'nvme0', label: 'NVMe composite', c: 46, category: 'nvme', device: '/dev/nvme0' },
      { key: 'nvme0_sensor_1', label: 'Controller', c: 50, category: 'nvme', device: '/dev/nvme0' },
      { key: 'zone_not_physical', label: 'Should not occur', c: 91, category: 'other' },
    ],
    thermal_zones: [{ key: 'zone_cpu', label: 'Firmware CPU', c: 69, mode: 'enabled' }],
    cpu: { per_core_detail: [{ id: 2, usage: 20, freq_mhz: 1000 }, { id: 3, usage: 40, freq_mhz: 1200 }] },
    alerts: [],
  });
  assert.equal(model.sensors.length, 6);
  assert.equal(model.hottest.key, 'zone_not_physical');
  assert.equal(model.cpuPackage.key, 'cpu');
  assert.equal(model.cores[0].physicalCoreId, 1);
  assert.equal(model.cores[0].loadPct, 30);
  assert.equal(model.cores[0].minFreqMHz, 1000);
  assert.equal(model.nvmeGroups.length, 1);
  assert.deepEqual(model.nvmeGroups[0].sensors.map((sensor) => sensor.key), ['nvme0', 'nvme0_sensor_1']);
  assert.equal(M.selectThermalSensor(model.sensors, 'missing').key, 'cpu');
  assert.equal(M.buildThermalModel({ temps: [], thermal_zones: [], alerts: [] }).health, 'no_data');
});

test('sensor category inference retains SATA, DIMM, board, and firmware distinction', () => {
  assert.equal(M.inferSensorCategory({ key: 'dimm0', category: 'dimm' }), 'memory');
  assert.equal(M.inferSensorCategory({ key: 'hdd_sda', category: 'hdd' }), 'sata');
  assert.equal(M.inferSensorCategory({ key: 'ambient', source: 'smart', device: '/dev/sdz' }), 'sata');
  assert.equal(M.inferSensorCategory({ key: 'acpitz', label: 'Board' }), 'board');
});

test('fan history preserves zero RPM and missing keyed samples', () => {
  const summary = M.fanHistorySummary([
    { ts: ts(1000), fans: { rear: { rpm: 0, pwm: 0 } } },
    { ts: ts(2000), fans: {} },
    { ts: ts(3000), fans: { rear: { rpm: 1200, pwm: 128 } } },
  ], 'rear');
  assert.equal(summary.minimumRpm, 0);
  assert.equal(summary.maximumRpm, 1200);
  assert.equal(summary.count, 2);
  assert.deepEqual(summary.points.map((point) => point.value), [0, null, 1200]);
  assert.equal(M.fanHistorySummary([], '').minimumRpm, null);
});

test('event display time uses e.ts before legacy alert onset and sorting uses that value', () => {
  const oldOnset = { ts: '2026-01-02T00:00:00Z', kind: 'resolved', alert: { id: 'fan', title: 'Fan RPM', since: '2026-01-01T00:00:00Z' } };
  const legacy = { kind: 'fired', alert: { id: 'temp', title: 'Temperature', since: '2026-01-03T00:00:00Z' } };
  assert.equal(M.eventTimestamp(oldOnset), '2026-01-02T00:00:00Z');
  assert.equal(M.eventTimestamp(legacy), '2026-01-03T00:00:00Z');
  assert.deepEqual(M.sortedEvents([oldOnset, legacy]).map(M.eventTimestamp), [legacy.alert.since, oldOnset.ts]);
  assert.deepEqual(M.sortedEvents([oldOnset, legacy], true), [legacy, oldOnset]);
});

test('safe fixture is representative and contains only generic synthetic identities', () => {
  const fixture = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'testdata', 'ui-fixture.json'), 'utf8'));
  assert.equal(fixture.synthetic, true);
  assert.equal(fixture.stats.smart.filter((drive) => drive.type === 'sata').length, 8);
  assert.equal(fixture.stats.smart.filter((drive) => drive.type === 'nvme').length, 2);
  assert.ok(fixture.stats.temps.some((sensor) => sensor.key.includes('sensor')));
  assert.ok(fixture.stats.fans.some((fan) => fan.rpm === 0));
  assert.ok(fixture.stats.containers.some((container) => container.state === 'exited'));
  assert.ok(fixture.stats.containers.some((container) => container.health === 'unhealthy'));
  assert.ok(fixture.stats.alerts.length >= 2);
  assert.ok(fixture.events.length >= 2);
  assert.equal(JSON.stringify(fixture).includes('BEGIN PRIVATE KEY'), false);
});
