/** Inspect the authorized workflow context without exporting any secret values.
 * A repository integration may not list secrets via REST, while an owner-run
 * workflow is authorized to consume its own secret context. Output is metadata
 * only; this script never sends credentials to another service or writes them.
 */
import { pathToFileURL } from 'node:url';
import { assess } from './autonomy.mjs';
export function actionsCapabilities(secrets = {}, variables = {}) {
  const configuration = {...variables, ...secrets};
  const inventory = assess(configuration);
  return {
    source: 'authorized-github-actions-context',
    secretNames: Object.keys(secrets).filter(k => k !== 'GITHUB_TOKEN').sort(),
    variableNames: Object.keys(variables).sort(),
    credentialPresenceOnly: inventory.credentialPresenceOnly,
    configuredCapabilitiesOnly: Object.fromEntries(Object.entries(inventory.checks)
      .filter(([key]) => !key.startsWith('local'))),
    productionReady: false,
    note: 'Names/presence only. No credential values, account validity, deployment or device evidence.',
  };
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const report = actionsCapabilities(JSON.parse(process.env.ACTIONS_SECRET_INVENTORY || '{}'),
      JSON.parse(process.env.ACTIONS_VARIABLE_INVENTORY || '{}'));
    const message = JSON.stringify(report).replaceAll('%','%25').replaceAll('\r','%0D').replaceAll('\n','%0A');
    console.log(`::notice title=Authorized Actions capability inventory::${message}`);
  } catch {
    // Do not let JSON parser error messages echo the source secret document.
    console.error('::error::Capability inventory could not be decoded; no values were emitted.');
    process.exitCode = 1;
  }
}
