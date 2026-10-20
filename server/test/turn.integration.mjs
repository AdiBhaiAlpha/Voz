/** Actual coturn process and relay packets, NOT WebRTC/audio or public hosting.
 * The test-only server binds loopback and permits loopback peers solely for the
 * two local relay clients. Production Compose's private/loopback denial remains.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawn, spawnSync } from 'node:child_process';
import { setTimeout as delay } from 'node:timers/promises';
import { turnCredentials } from '../src/security.ts';

const image = 'coturn/coturn:4.6.3'; // Same version as production Compose.
test('real coturn allocations, relay transport and credential expiry', {timeout:180000}, async t => {
  assert.equal(spawnSync('docker', ['info'], {stdio:'ignore', timeout:10000}).status, 0,
    'A real Docker engine is required; there is no mock/skip substitute');
  const dir = mkdtempSync(join(tmpdir(), 'voz-turn-fixture-'));
  const container = `voz-turn-test-${randomBytes(6).toString('hex')}`;
  const secret = randomBytes(32).toString('hex');
  let server;
  try {
    const cert = spawnSync('openssl', ['req', '-x509', '-newkey', 'rsa:2048', '-nodes',
      '-keyout', join(dir,'key.pem'), '-out', join(dir,'cert.pem'), '-days', '1',
      '-subj', '/CN=localhost'], {stdio:'ignore', timeout:15000});
    assert.equal(cert.status, 0, 'Could not generate isolated fixture TLS certificate');
    writeFileSync(join(dir,'server.conf'), [
      'listening-ip=127.0.0.1', 'relay-ip=127.0.0.1', 'listening-port=34780',
      'tls-listening-port=34781', 'min-port=49210', 'max-port=49310',
      'realm=voz-ci-fixture', 'use-auth-secret', `static-auth-secret=${secret}`,
      'fingerprint', 'no-cli', 'no-dtls', 'no-multicast-peers', 'allow-loopback-peers',
      'denied-peer-ip=10.0.0.0-10.255.255.255', 'user-quota=32', 'total-quota=64',
      'cert=/fixture/cert.pem', 'pkey=/fixture/key.pem', 'pidfile=/tmp/voz-turn.pid',
      'log-file=stdout', 'simple-log', 'verbose',
    ].join('\n')+'\n', {mode:0o600});
    server = spawn('docker', ['run', '--rm', '--name', container, '--network', 'host',
      '--user', `${process.getuid()}:${process.getgid()}`, '--entrypoint', 'turnserver',
      '--mount', `type=bind,src=${dir},dst=/fixture,readonly`, image, '-c', '/fixture/server.conf'],
      {stdio:['ignore','pipe','pipe']});
    let output = '';
    await new Promise((resolve,reject) => {
      const timer = setTimeout(() => reject(Error('coturn startup deadline: '+output.slice(-2000))), 90000);
      const read = data => {
        output += data.toString();
        if (/UDP listener opened on:.*127\.0\.0\.1/.test(output)) { clearTimeout(timer); resolve(); }
      };
      server.stdout.on('data', read); server.stderr.on('data', read);
      server.once('error', error => {clearTimeout(timer);reject(error);});
      server.once('exit', code => {clearTimeout(timer);reject(Error(`coturn exited ${code}: ${output.slice(-2000)}`));});
    });
    assert.equal(spawnSync('docker', ['exec', '-d', container, 'turnutils_peer',
      '-L', '127.0.0.1', '-p', '34801'], {stdio:'ignore', timeout:5000}).status, 0,
      'Could not start the real UDP echo peer for TLS transport testing');
    const credential = (now = Math.floor(Date.now()/1000), signingSecret = secret) =>
      turnCredentials('relay-fixture', ['turn:127.0.0.1:34780'], signingSecret, now).iceServers[0];
    const client = (creds, flags = []) => new Promise((resolve,reject) => {
      const serverLogOffset = output.length;
      const args = ['exec', '--user', `${process.getuid()}:${process.getgid()}`, container,
        'turnutils_uclient', '-v', '-c', '-n', '5', '-m', '2', '-p', '34780',
        '-u', creds.username, '-w', creds.credential, ...flags, '127.0.0.1'];
      const child = spawn('docker', args, {stdio:['ignore','pipe','pipe']});
      let log = '';
      const timer = setTimeout(() => {child.kill();reject(Error('TURN client deadline: '+log.slice(-2500)));}, 20000);
      child.stdout.on('data', b => {log += b;}); child.stderr.on('data', b => {log += b;});
      child.once('error', error => {clearTimeout(timer);reject(error);});
      child.once('exit', code => {clearTimeout(timer);resolve({code,log,username:creds.username,serverLog:output.slice(serverLogOffset)});});
    });
    const relaySucceeded = result => {
      assert.equal(result.code, 0, result.log.slice(-3000));
      const stats = [...result.log.matchAll(/tot_send_msgs=(\d+), tot_recv_msgs=(\d+)/g)].at(-1);
      assert.ok(stats, 'No real relay packet counters: '+result.log.slice(-3000));
      assert.ok(Number(stats[1]) >= 10, 'No two-client relay traffic');
      assert.equal(Number(stats[2]), Number(stats[1]), 'Loopback fixture lost relayed packets');
    };
    const rejected = (result, code) => {
      assert.notEqual(result.code, 0, 'Rejected request was unexpectedly accepted');
      // Coturn's uclient consumes repeated 401 challenges internally and may
      // print only 'Cannot complete Allocation'. Require the actual server's
      // matching-user rejection as well as the client's failed exit status.
      const matchingServerLines = result.serverLog.split('\n')
        .filter(line => line.includes(`user <${result.username}>`)).join('\n');
      const evidence = result.log+'\n'+matchingServerLines;
      assert.match(evidence, new RegExp(`(?:error\\s*${code}|${code}\\s*\\()`,'i'), evidence.slice(-3500));
    };
    await t.test('UDP control relays real packets between two allocated clients', async () => {
      relaySucceeded(await client(credential(), ['-y']));
    });
    await t.test('TCP control relays real packets', async () => {
      relaySucceeded(await client(credential(), ['-y', '-t']));
    });
    await t.test('TLS control relays packets using fixture certificate (not public PKI)', async () => {
      // Echo-peer mode installs permissions before sending; uclient's -y TLS
      // setup can send its first packets before the second client is ready.
      // Retain the strict zero-loss assertion rather than accepting that race.
      relaySucceeded(await client(credential(), ['-t', '-S', '-p', '34781',
        '-E', '/fixture/cert.pem', '-e', '127.0.0.1', '-r', '34801']));
    });
    await t.test('incorrect HMAC is rejected with Unauthorized', async () => {
      rejected(await client(credential(undefined, 'wrong-fixture-secret'), ['-y']), 401);
    });
    await t.test('previously accepted credentials expire for a NEW allocation; fresh ones recover', async () => {
      const short = credential(Math.floor(Date.now()/1000)-590); // Real expiry in ~10s.
      relaySucceeded(await client(short, ['-y']));
      const expires = Number(short.username.split(':')[0]);
      await delay(Math.max(0, (expires+2)*1000-Date.now()));
      rejected(await client(short, ['-y']), 401);
      relaySucceeded(await client(credential(), ['-y']));
    });
    await t.test('denied private peer permission is rejected', async () => {
      rejected(await client(credential(), ['-e', '10.0.0.1', '-r', '9']), 403);
    });
  } finally {
    spawnSync('docker', ['rm', '--force', container], {stdio:'ignore', timeout:15000});
    server?.kill();
    rmSync(dir, {recursive:true, force:true});
  }
});
