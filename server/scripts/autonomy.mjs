/** Offline, secret-redacted capability inventory. Configuration != deployment.
 * No provider account creation, credential export, network requests or prompts.
 */
import { accessSync, constants, statSync } from 'node:fs';
import { isIP } from 'node:net';
import { spawnSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';

export function publicHostname(value = '') {
  const host = value.toLowerCase();
  if (!/^(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z]{2,63}$/.test(host)) return false;
  return !['localhost', 'local', 'test', 'invalid', 'example', 'example.com',
    'example.net', 'example.org', 'e2b.app', 'e2b.dev', 'trycloudflare.com',
    'ngrok.io', 'ngrok.app', 'ngrok-free.app', 'loca.lt', 'github.io'].some(
    suffix => host === suffix || host.endsWith(`.${suffix}`));
}

export function publicIPv4(value = '') {
  if (isIP(value) !== 4) return false;
  const [a,b,c] = value.split('.').map(Number);
  return !(a === 0 || a === 10 || a === 127 || a >= 224 ||
    (a === 100 && b >= 64 && b <= 127) || (a === 169 && b === 254) ||
    (a === 172 && b >= 16 && b <= 31) ||
    (a === 192 && (b === 168 || (b === 0 && (c === 0 || c === 2)))) ||
    (a === 198 && (b === 18 || b === 19 || (b === 51 && c === 100))) ||
    (a === 203 && b === 0 && c === 113));
}

function readableFile(path) {
  if (!path) return false;
  try { accessSync(path, constants.R_OK); return statSync(path).isFile(); }
  catch { return false; }
}

export function assess(env = {}, tools = {}, fileExists = readableFile) {
  const present = key => Boolean(env[key]?.trim());
  let serviceOrigin = false;
  try {
    const u = new URL(env.VOZ_SERVICE_URL || '');
    serviceOrigin = u.protocol === 'https:' && !u.username && !u.password &&
      !u.search && !u.hash && u.pathname === '/' && publicHostname(u.hostname);
  } catch { /* Missing/malformed is not a deployment. */ }
  const firebaseClient = ['FCM_APPLICATION_ID', 'FCM_API_KEY', 'FCM_PROJECT_ID',
    'FCM_SENDER_ID'].every(present);
  const firebaseServer = fileExists(env.FCM_CREDENTIALS_PATH || env.GOOGLE_APPLICATION_CREDENTIALS);
  const checks = {
    durableHostConfiguration: publicHostname(env.VOZ_HOST),
    serviceOriginConfiguration: serviceOrigin,
    turnHostnameConfiguration: publicHostname(env.TURN_HOST),
    turnPublicAddressConfiguration: publicIPv4(env.TURN_PUBLIC_IP),
    firebaseClientConfiguration: firebaseClient,
    firebaseServerFileReadable: firebaseServer,
    localJavaAvailable: Boolean(tools.java),
    localDockerAvailable: Boolean(tools.docker),
    localAdbAvailable: Boolean(tools.adb),
  };
  const blockers = [];
  if (!checks.durableHostConfiguration || !checks.serviceOriginConfiguration)
    blockers.push('NO_CONFIGURED_DURABLE_SERVICE_ORIGIN');
  if (!checks.turnHostnameConfiguration || !checks.turnPublicAddressConfiguration)
    blockers.push('NO_CONFIGURED_PUBLIC_TURN_HOST');
  if (!firebaseClient || !firebaseServer) blockers.push('FCM_CONFIGURATION_UNAVAILABLE');
  if (!tools.java) blockers.push('LOCAL_ANDROID_BUILD_TOOLCHAIN_UNAVAILABLE');
  if (!tools.docker) blockers.push('LOCAL_CONTAINER_RUNTIME_UNAVAILABLE');
  if (!tools.adb) blockers.push('LOCAL_ANDROID_DEVICE_ACCESS_UNAVAILABLE');
  return {
    schemaVersion: 1, mode: 'offline-capability-inventory',
    // Even complete inputs are not proof of credentials, DNS, deployment or media.
    status: blockers.length ? 'blocked' : 'configured_unverified',
    productionReady: false, checks, blockers,
    credentialPresenceOnly: {
      github: present('GH_TOKEN') || present('GITHUB_TOKEN'),
      cloudflare: present('CLOUDFLARE_API_TOKEN') || present('CF_API_TOKEN'),
      oracle: present('OCI_CONFIG_FILE') || present('OCI_CLI_CONFIG_FILE'),
      railway: present('RAILWAY_TOKEN') || present('RAILWAY_API_TOKEN'),
      render: present('RENDER_API_KEY'), koyeb: present('KOYEB_TOKEN'),
      huggingFace: present('HF_TOKEN') || present('HUGGING_FACE_HUB_TOKEN'),
      authorizedSshTarget: present('VOZ_DEPLOY_HOST') && present('VOZ_DEPLOY_USER') &&
        fileExists(env.VOZ_SSH_KEY_FILE),
    },
    notVerified: ['provider_permissions', 'DNS_and_ownership', 'public_TLS',
      'database_durability', 'TURN_allocation', 'FCM_authentication_and_delivery',
      'Android_installation', 'two_device_media', 'Doze_and_locked_incoming'],
  };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const available = (name, args = ['--version']) => {
    const result = spawnSync(name, args, {stdio: 'ignore', timeout: 5000});
    return !result.error && result.status === 0;
  };
  const result = assess(process.env, {
    java: available('java'), docker: available('docker'), adb: available('adb', ['version']),
  });
  console.log(JSON.stringify(result, null, 2));
  process.exitCode = result.status === 'blocked' ? 2 : 0;
}
