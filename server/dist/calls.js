import { isDeepStrictEqual } from 'node:util';
import { randomUUID } from 'node:crypto';
import { tx } from './db.js';
import { requireThat } from './security.js';
const terminal = new Set(['reject', 'cancel', 'end']);
export class Calls {
    pool;
    online;
    publish;
    pushEnabled;
    constructor(pool, online, publish, pushEnabled) {
        this.pool = pool;
        this.online = online;
        this.publish = publish;
        this.pushEnabled = pushEnabled;
    }
    async event(c, call, p, eventId, type, payload = {}) {
        const row = (await c.query(`INSERT INTO call_events(call_id,actor,actor_device,event_id,type,payload)
   VALUES($1,$2,$3,$4,$5,$6) RETURNING *`, [call.id, p?.accountId || null, p?.deviceId || null, eventId, type, payload])).rows[0];
        return { type: 'call.event', seq: String(row.seq), call, action: type, payload };
    }
    view(call) { return call; }
    async validateDevice(c, p) {
        requireThat(p.deviceId, 'REGISTER_DEVICE', 403);
        requireThat((await c.query('SELECT 1 FROM devices d JOIN sessions s ON s.device_id=d.id WHERE d.id=$1 AND d.account_id=$2 AND d.revoked_at IS NULL AND s.id=$3 AND s.revoked_at IS NULL AND s.expires>now() AND s.access_expires>now() FOR SHARE OF d,s', [p.deviceId, p.accountId, p.sessionId])).rowCount, 'DEVICE_REVOKED', 401);
    }
    async begin(p, target, requestId) {
        const result = await tx(this.pool, async (c) => {
            await this.validateDevice(c, p);
            // Stable account ordering avoids A→B/B→A deadlocks; membership PK is a second guard.
            const recipient = (await c.query('SELECT * FROM identities WHERE voz_id=$1', [target])).rows[0];
            requireThat(recipient && recipient.account_id !== p.accountId, 'RECIPIENT_UNAVAILABLE', 404);
            const users = [p.accountId, recipient.account_id].sort();
            await c.query('SELECT id FROM accounts WHERE id=ANY($1::uuid[]) ORDER BY id FOR UPDATE', [users]);
            const old = (await c.query('SELECT * FROM call_requests WHERE account_id=$1 AND request_id=$2', [p.accountId, requestId])).rows[0];
            if (old) {
                requireThat(old.target === target, 'IDEMPOTENCY_CONFLICT');
                return { call: (await c.query('SELECT * FROM calls WHERE id=$1', [old.call_id])).rows[0], duplicate: true };
            }
            requireThat((await c.query('SELECT 1 FROM identities WHERE account_id=$1', [p.accountId])).rowCount, 'IDENTITY_REQUIRED', 403);
            requireThat(!(await c.query('SELECT 1 FROM active_call_members WHERE account_id=ANY($1::uuid[])', [users])).rowCount, 'BUSY');
            const devices = (await c.query("SELECT * FROM devices WHERE account_id=$1 AND revoked_at IS NULL AND last_seen>now()-interval '30 days'", [recipient.account_id])).rows;
            const reachable = devices.filter(d => this.online(d.id) || (this.pushEnabled && d.push_token));
            requireThat(reachable.length, 'RECIPIENT_UNAVAILABLE', 409);
            const id = randomUUID();
            const call = (await c.query("INSERT INTO calls(id,from_account,from_device,to_account,state,expires_at) VALUES($1,$2,$3,$4,'ringing',now()+interval '30 seconds') RETURNING *", [id, p.accountId, p.deviceId, recipient.account_id])).rows[0];
            for (const user of users)
                await c.query('INSERT INTO active_call_members VALUES($1,$2)', [user, id]);
            await c.query('INSERT INTO call_requests VALUES($1,$2,$3,$4)', [p.accountId, requestId, id, target]);
            for (const d of reachable)
                if (this.pushEnabled && d.push_token)
                    await c.query('INSERT INTO push_outbox(call_id,device_id) VALUES($1,$2)', [id, d.id]);
            const e = await this.event(c, call, p, requestId, 'incoming');
            return { call, event: e, duplicate: false };
        });
        if (result.event)
            this.publish(result.event);
        return { call: { ...result.call, server_time: new Date().toISOString() }, duplicate: result.duplicate };
    }
    authorized(call, p) { requireThat(call && [call.from_account, call.to_account].includes(p.accountId), 'CALL_NOT_FOUND', 404); }
    async get(p, id) {
        const call = (await this.pool.query(`SELECT c.*,now() AS server_time,(c.expires_at>now()) AS unexpired,i.voz_id AS caller_id,i.display_name AS caller_name,j.voz_id AS recipient_id,j.display_name AS recipient_name FROM calls c
   JOIN identities i ON i.account_id=c.from_account JOIN identities j ON j.account_id=c.to_account WHERE c.id=$1`, [id])).rows[0];
        this.authorized(call, p);
        return call;
    }
    async events(p, id, after) {
        const call = await this.get(p, id);
        // Other devices can see the state, never an accepted call's SDP/ICE.
        const mediaAllowed = p.deviceId === call.from_device || p.deviceId === call.to_device;
        const events = (await this.pool.query(`SELECT seq,type,payload,actor_device FROM call_events WHERE call_id=$1 AND seq>$2
   AND ($3::boolean OR type NOT IN ('offer','answer','ice','restart')) ORDER BY seq LIMIT 500`, [id, after, mediaAllowed])).rows;
        return { call, events: events.map(e => ({ ...e, seq: String(e.seq) })) };
    }
    async command(p, id, eventId, action, payload = {}) {
        const result = await tx(this.pool, async (c) => {
            await this.validateDevice(c, p);
            let call = (await c.query('SELECT * FROM calls WHERE id=$1 FOR UPDATE', [id])).rows[0];
            this.authorized(call, p);
            const prior = (await c.query('SELECT * FROM call_events WHERE call_id=$1 AND actor_device=$2 AND event_id=$3', [id, p.deviceId, eventId])).rows[0];
            if (prior) {
                requireThat(prior.type === action && isDeepStrictEqual(prior.payload, payload), 'IDEMPOTENCY_CONFLICT');
                return { call, duplicate: true };
            }
            requireThat(call.state !== 'ended', 'CALL_ENDED');
            requireThat(new Date(call.expires_at).getTime() > Date.now(), 'CALL_EXPIRED');
            const from = p.accountId === call.from_account;
            if (action === 'accept') {
                requireThat(!from && call.state === 'ringing', 'INVALID_TRANSITION');
                call = (await c.query("UPDATE calls SET state='accepted',to_device=$2,accepted_at=now(),caller_seen=now(),callee_seen=now(),expires_at=now()+interval '4 hours',revision=revision+1 WHERE id=$1 RETURNING *", [id, p.deviceId])).rows[0];
            }
            else if (terminal.has(action)) {
                requireThat(action !== 'cancel' || (from && call.state === 'ringing'), 'INVALID_TRANSITION');
                requireThat(action !== 'reject' || (!from && call.state === 'ringing'), 'INVALID_TRANSITION');
                if (call.state === 'accepted')
                    requireThat(p.deviceId === (from ? call.from_device : call.to_device), 'WRONG_DEVICE', 403);
                else if (from)
                    requireThat(p.deviceId === call.from_device, 'WRONG_DEVICE', 403);
                call = await this.finish(c, call, action);
            }
            else {
                requireThat(call.state === 'accepted', 'INVALID_TRANSITION');
                requireThat(p.deviceId === (from ? call.from_device : call.to_device), 'WRONG_DEVICE', 403);
                if (action === 'offer')
                    requireThat(from, 'ONLY_CALLER_OFFERS', 403);
                if (action === 'answer')
                    requireThat(!from, 'ONLY_RECIPIENT_ANSWERS', 403);
                if (action === 'restart')
                    requireThat(from, 'ONLY_CALLER_RESTARTS', 403);
                if (action === 'restart' || action === 'restart_request')
                    requireThat((await c.query("SELECT 1 FROM call_events WHERE call_id=$1 AND actor_device=$2 AND type=$3 AND created_at>now()-interval '1 minute' OFFSET 2 LIMIT 1", [id, p.deviceId, action])).rowCount === 0, 'RESTART_RATE_LIMIT', 429);
                if (action === 'ice')
                    requireThat((await c.query("SELECT 1 FROM call_events WHERE call_id=$1 AND actor_device=$2 AND type='ice' AND payload->>'generation'=$3 OFFSET 127 LIMIT 1", [id, p.deviceId, String(call.revision)])).rowCount === 0, 'ICE_LIMIT', 429);
                if (action === 'restart_request')
                    requireThat(!from, 'ONLY_RECIPIENT_REQUESTS_RESTART', 403);
                // Revisions order offer/answer generations and reject delayed media from older negotiations.
                if (action === 'offer') {
                    requireThat(payload.generation === call.revision, 'STALE_NEGOTIATION');
                    requireThat(!(await c.query("SELECT 1 FROM call_events WHERE call_id=$1 AND type='offer' AND payload->>'generation'=$2", [id, String(call.revision)])).rowCount, 'OFFER_ALREADY_SENT');
                }
                if (action === 'answer')
                    requireThat(!(await c.query("SELECT 1 FROM call_events WHERE call_id=$1 AND type='answer' AND payload->>'generation'=$2", [id, String(call.revision)])).rowCount, 'ANSWER_ALREADY_SENT');
                if (action === 'answer')
                    requireThat((await c.query("SELECT 1 FROM call_events WHERE call_id=$1 AND type='offer' AND payload->>'generation'=$2", [id, String(call.revision)])).rowCount, 'OFFER_REQUIRED');
                if (action === 'restart')
                    call = (await c.query('UPDATE calls SET revision=revision+1 WHERE id=$1 RETURNING *', [id])).rows[0];
                else if (action !== 'restart_request')
                    requireThat(payload.generation === call.revision, 'STALE_NEGOTIATION');
            }
            const event = await this.event(c, call, p, eventId, action, payload);
            return { call, event, duplicate: false };
        });
        if (result.event)
            this.publish(result.event);
        return { call: { ...result.call, server_time: new Date().toISOString() }, duplicate: result.duplicate };
    }
    async finish(c, call, reason) {
        const row = (await c.query("UPDATE calls SET state='ended',reason=$2,ended_at=now(),revision=revision+1 WHERE id=$1 RETURNING *", [call.id, reason])).rows[0];
        await c.query('DELETE FROM active_call_members WHERE call_id=$1', [call.id]);
        return row;
    }
    async heartbeat(p, callId) {
        if (!p.deviceId)
            return;
        await this.pool.query(`UPDATE calls SET caller_seen=CASE WHEN from_device=$1 THEN now() ELSE caller_seen END,
   callee_seen=CASE WHEN to_device=$1 THEN now() ELSE callee_seen END
   WHERE state='accepted' AND id=$2 AND (from_device=$1 OR to_device=$1)`, [p.deviceId, callId || null]);
        await this.pool.query('UPDATE devices SET last_seen=now() WHERE id=$1 AND revoked_at IS NULL', [p.deviceId]);
    }
    async failUndeliverable() {
        const events = await tx(this.pool, async (c) => {
            const rows = (await c.query(`SELECT c.* FROM calls c WHERE c.state='ringing'
    AND EXISTS(SELECT 1 FROM push_outbox o WHERE o.call_id=c.id)
    AND NOT EXISTS(SELECT 1 FROM push_outbox o WHERE o.call_id=c.id AND o.failed_at IS NULL)
    FOR UPDATE OF c SKIP LOCKED`)).rows;
            const events = [];
            for (const call of rows) {
                const devices = (await c.query('SELECT id FROM devices WHERE account_id=$1 AND revoked_at IS NULL', [call.to_account])).rows;
                if (devices.some(d => this.online(d.id)))
                    continue;
                const ended = await this.finish(c, call, 'unavailable');
                events.push(await this.event(c, ended, null, randomUUID(), 'delivery_failed'));
            }
            return events;
        });
        for (const e of events)
            this.publish(e);
    }
    async expire() {
        const events = await tx(this.pool, async (c) => {
            const rows = (await c.query("SELECT * FROM calls WHERE state<>'ended' AND (expires_at<now() OR (state='accepted' AND LEAST(caller_seen,callee_seen)<now()-interval '90 seconds')) FOR UPDATE SKIP LOCKED")).rows;
            const events = [];
            for (const call of rows) {
                const ended = await this.finish(c, call, call.state === 'ringing' ? 'unanswered' : 'expired');
                events.push(await this.event(c, ended, null, randomUUID(), 'timeout'));
            }
            return events;
        });
        for (const e of events)
            this.publish(e);
    }
    async revoke(p, deviceId) {
        const events = await tx(this.pool, async (c) => {
            const rows = (await c.query("SELECT * FROM calls WHERE (from_account=$1 OR to_account=$1) AND state<>'ended' FOR UPDATE", [p.accountId])).rows;
            const events = [];
            for (const call of rows) {
                if (deviceId && ![call.from_device, call.to_device].includes(deviceId))
                    continue;
                const ended = await this.finish(c, call, 'revoked');
                events.push(await this.event(c, ended, null, randomUUID(), 'end'));
            }
            return events;
        });
        for (const e of events)
            this.publish(e);
    }
}
