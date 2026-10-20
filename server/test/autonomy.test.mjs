import test from 'node:test';
import assert from 'node:assert/strict';
import { assess, publicHostname, publicIPv4 } from '../scripts/autonomy.mjs';

test('rejects sample, static, local, temporary and sandbox hostnames', () => {
  for (const host of ['', 'localhost', '127.0.0.1', 'voice.example.com', 'example.org',
    'vozip.local', 'foo.test', 'foo.invalid', '443-id.e2b.app', 'foo.trycloudflare.com',
    'test.ngrok-free.app', 'foo.loca.lt', 'adibhaialpha.github.io',
    'voice.test.com@evil.com', 'voice.domain.com/path', '-bad.domain.com']) {
    assert.equal(publicHostname(host), false, host);
  }
  assert.equal(publicHostname('voice.valid-domain.dev'), true);
});

test('rejects private, documentation, CGNAT, benchmarking and multicast IPv4', () => {
  for (const ip of ['', 'bad', '::1', '0.0.0.0', '10.0.0.1', '100.64.0.1',
    '100.127.255.255', '127.0.0.1', '169.254.1.1', '172.16.0.1', '172.31.0.1',
    '192.168.0.1', '192.0.0.1', '192.0.2.1', '198.18.0.1', '198.19.255.255',
    '198.51.100.1', '203.0.113.10', '224.0.0.1', '255.255.255.255']) {
    assert.equal(publicIPv4(ip), false, ip);
  }
  assert.equal(publicIPv4('1.1.1.1'), true);
  assert.equal(publicIPv4('100.128.0.1'), true);
});

test('GitHub credentials do not imply hosting, FCM, SSH or production readiness', () => {
  const result = assess({GH_TOKEN: 'synthetic-secret-not-real'});
  assert.equal(result.status, 'blocked');
  assert.equal(result.credentialPresenceOnly.github, true);
  assert.equal(result.credentialPresenceOnly.authorizedSshTarget, false);
  assert.equal(result.productionReady, false);
  assert.ok(!JSON.stringify(result).includes('synthetic-secret-not-real'));
});

test('even complete syntax-level configuration never reports a deployment or call', () => {
  const result = assess({
    VOZ_HOST: 'voice.valid-domain.dev', VOZ_SERVICE_URL: 'https://voice.valid-domain.dev',
    TURN_HOST: 'turn.valid-domain.dev', TURN_PUBLIC_IP: '1.1.1.1',
    FCM_APPLICATION_ID: 'synthetic-id', FCM_API_KEY: 'synthetic-key',
    FCM_PROJECT_ID: 'synthetic-project', FCM_SENDER_ID: 'synthetic-sender',
    FCM_CREDENTIALS_PATH: '/synthetic/credentials.json',
  }, {java:true, docker:true, adb:true}, () => true);
  assert.deepEqual(result.blockers, []);
  assert.equal(result.status, 'configured_unverified');
  assert.equal(result.productionReady, false);
  assert.ok(result.notVerified.includes('two_device_media'));
});

test('service origin must not contain credentials, query, path or plaintext', () => {
  for (const origin of ['http://voice.valid-domain.dev', 'https://localhost',
    'https://voice.valid-domain.dev/token', 'https://voice.valid-domain.dev?key=secret',
    'https://user:pass@voice.valid-domain.dev', 'https://voice.valid-domain.dev#fragment']) {
    const result = assess({VOZ_SERVICE_URL:origin});
    assert.equal(result.checks.serviceOriginConfiguration, false);
    assert.ok(!JSON.stringify(result).includes(origin));
  }
});

test('an unreadable Firebase credential path is not usable authentication', () => {
  const result = assess({GOOGLE_APPLICATION_CREDENTIALS: '/missing/file.json'}, {}, () => false);
  assert.equal(result.checks.firebaseServerFileReadable, false);
  assert.ok(result.blockers.includes('FCM_CONFIGURATION_UNAVAILABLE'));
});

test('authorized Actions inventory exposes names/presence, never secret or variable values', async () => {
  const {actionsCapabilities} = await import('../scripts/actions-capabilities.mjs');
  const report = actionsCapabilities({GITHUB_TOKEN:'synthetic-gh-value',
    CLOUDFLARE_API_TOKEN:'synthetic-cloud-value', CUSTOM_JSON:'{"private_key":"synthetic-private-value"}'},
    {VOZ_HOST:'voice.valid-domain.dev', INTERNAL_VALUE:'synthetic-variable-value'});
  assert.deepEqual(report.secretNames, ['CLOUDFLARE_API_TOKEN','CUSTOM_JSON']);
  assert.equal(report.credentialPresenceOnly.cloudflare, true);
  assert.equal(report.productionReady, false);
  for (const value of ['synthetic-gh-value','synthetic-cloud-value','synthetic-private-value',
    'synthetic-variable-value','voice.valid-domain.dev']) assert.ok(!JSON.stringify(report).includes(value));
});
