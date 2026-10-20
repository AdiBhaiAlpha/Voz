import { initializeApp, applicationDefault, getApps } from 'firebase-admin/app';
import { getMessaging } from 'firebase-admin/messaging';
import { tx } from './db.js';
export function firebasePush() {
    if (!process.env.GOOGLE_APPLICATION_CREDENTIALS)
        return undefined;
    if (!getApps().length)
        initializeApp({ credential: applicationDefault() });
    return { async send(token, data, ttl) { await getMessaging().send({ token, data, android: { priority: 'high', ttl, collapseKey: data.callId } }); } };
}
export async function deliverPush(pool, sender) {
    return tx(pool, async (c) => {
        const jobs = (await c.query(`SELECT o.*,d.push_token,c.expires_at,c.state,d.revoked_at FROM push_outbox o
   JOIN devices d ON d.id=o.device_id JOIN calls c ON c.id=o.call_id
   WHERE o.delivered_at IS NULL AND o.failed_at IS NULL AND o.next_attempt<=now()
   ORDER BY o.id LIMIT 10 FOR UPDATE OF o SKIP LOCKED`)).rows;
        for (const job of jobs) {
            const ttl = new Date(job.expires_at).getTime() - Date.now();
            if (job.state !== 'ringing' || ttl <= 0 || job.revoked_at || !job.push_token) {
                await c.query('UPDATE push_outbox SET failed_at=now() WHERE id=$1', [job.id]);
                continue;
            }
            try {
                let deadline;
                try {
                    await Promise.race([
                        sender.send(job.push_token, { kind: 'voz.incoming', callId: job.call_id, expiresAt: new Date(job.expires_at).toISOString() }, ttl),
                        new Promise((_, reject) => { deadline = setTimeout(() => reject(new Error('PUSH_TIMEOUT')), 5000); })
                    ]);
                }
                finally {
                    if (deadline)
                        clearTimeout(deadline);
                }
                await c.query('UPDATE push_outbox SET delivered_at=now(),attempts=attempts+1 WHERE id=$1', [job.id]);
            }
            catch (e) {
                const invalid = ['messaging/registration-token-not-registered', 'messaging/invalid-registration-token'].includes(e.code);
                if (invalid)
                    await c.query('UPDATE devices SET push_token=NULL WHERE id=$1', [job.device_id]);
                await c.query(`UPDATE push_outbox SET attempts=attempts+1,next_attempt=now()+interval '3 seconds',
     failed_at=CASE WHEN attempts>=2 OR $2 THEN now() ELSE NULL END WHERE id=$1`, [job.id, invalid]);
            }
        }
        return jobs.length;
    });
}
