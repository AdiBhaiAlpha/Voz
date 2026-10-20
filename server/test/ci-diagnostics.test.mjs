import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, mkdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
const script = fileURLToPath(new URL('../../.github/scripts/android-check.sh', import.meta.url));
function fixture(body, lint) {
  const cwd = mkdtempSync(join(tmpdir(), 'voz-ci-diagnostic-'));
  try {
    writeFileSync(join(cwd, 'gradlew'), `#!/bin/sh\n${body}\n`, {mode: 0o700});
    if (lint) {
      mkdirSync(join(cwd, 'app/build/reports'), {recursive:true});
      writeFileSync(join(cwd, 'app/build/reports/lint-results-debug.xml'), lint);
    }
    return spawnSync('bash', [script, 'synthetic-test-task'], {cwd, encoding:'utf8'});
  } finally { rmSync(cwd, {recursive:true, force:true}); }
}

test('CI diagnostics preserve failed Gradle exit and omit task progress annotations', {skip:process.platform==='win32'}, () => {
  const result = fixture("echo '> Task :app:compileDebugKotlin'\necho 'e: file.kt:5: compiler failure'\nexit 7");
  assert.equal(result.status, 7);
  const annotations = result.stdout.split('\n').filter(s => s.startsWith('::error '));
  assert.equal(annotations.length, 1);
  assert.ok(annotations[0].includes('compiler failure'));
  assert.ok(!annotations[0].includes('> Task'));
});

test('CI emits all lint errors in one annotation rather than losing errors after ten', {skip:process.platform==='win32'}, () => {
  const xml = `<issues>${Array.from({length:15}, (_, i) => `<issue id="Error${i}" severity="Error" message="synthetic issue"><location file="file.kt" line="${i+1}"/></issue>`).join('')}<issue id="WarningOnly" severity="Warning" message="warning"/></issues>`;
  const result = fixture("echo '> Task :app:lintDebug FAILED'\nexit 1", xml);
  assert.equal(result.status, 1);
  const annotations = result.stdout.split('\n').filter(s => s.startsWith('::error '));
  assert.equal(annotations.length, 1);
  for (let i=0; i<15; i++) assert.ok(annotations[0].includes(`[Error${i}]`));
  assert.ok(!annotations[0].includes('WarningOnly'));
  assert.ok(annotations[0].includes('%0A'));
});

test('successful Gradle execution does not manufacture a failure annotation', {skip:process.platform==='win32'}, () => {
  const result = fixture("echo 'BUILD SUCCESSFUL'\nexit 0");
  assert.equal(result.status, 0);
  assert.ok(!result.stdout.includes('::error'));
});

const reportGuard = fileURLToPath(new URL('../../.github/scripts/assert-android-tests.py', import.meta.url));
function checkReport(xml) {
  const cwd = mkdtempSync(join(tmpdir(), 'voz-android-report-'));
  try {
    const reports = join(cwd,'app/build/outputs/androidTest-results/connected/device');
    mkdirSync(reports, {recursive:true});
    if (xml) writeFileSync(join(reports,'TEST-fixture.xml'), xml);
    return spawnSync('python3', [reportGuard,'instrumented'], {cwd, encoding:'utf8'});
  } finally { rmSync(cwd, {recursive:true, force:true}); }
}
const requiredCases = [
  ['com.example.online.NativeVoicePeerIntegrationTest','twoNativePeersNegotiateAndExchangeAudioRtp'],
  ['com.example.telecom.OnlineRegistrationUiTest','newIdentityRequiresDisplayNameAndCredentials'],
];
function syntheticReport(child='') {
  return `<testsuite>${requiredCases.map(([clazz,name]) => `<testcase classname="${clazz}" name="${name}">${child}</testcase>`).join('')}</testsuite>`;
}
test('Android report guard confirms both expected non-skipped cases', {skip:process.platform==='win32'}, () => {
  const result = checkReport(syntheticReport());
  assert.equal(result.status, 0, result.stdout+result.stderr);
  assert.ok(result.stdout.includes('2 actual cases; 0 failures; 0 skipped'));
});
test('Android report guard rejects absent, skipped, failed or wrong-class test evidence', {skip:process.platform==='win32'}, () => {
  for (const xml of ['', '<testsuite/>', syntheticReport('<skipped/>'),
    syntheticReport('<failure/>'), '<testsuite><testcase classname="Other" name="notTheRequiredTests"/></testsuite>']) {
    const result = checkReport(xml);
    assert.equal(result.status, 1, result.stdout+result.stderr);
    assert.ok(result.stdout.includes('::error'));
  }
});
