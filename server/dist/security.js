import { randomBytes, createHash, scrypt as callback, timingSafeEqual, createHmac } from 'node:crypto';
import { promisify } from 'node:util';
import { z } from 'zod';
const scrypt = promisify(callback);
let activeKdfs = 0;
async function derive(password, salt) {
    requireThat(activeKdfs < 4, 'AUTH_BUSY', 503);
    activeKdfs++;
    try {
        return await scrypt(password, salt, 64);
    }
    finally {
        activeKdfs--;
    }
}
export const secret = () => randomBytes(32).toString('base64url');
export const hash = (s) => createHash('sha256').update(s).digest('hex');
export async function passwordHash(password) { const salt = randomBytes(16).toString('hex'); return `${salt}:${(await derive(password, salt)).toString('hex')}`; }
export async function verify(password, encoded) {
    const [salt, expected] = (encoded || 'dummy:').split(':');
    const actual = await derive(password, salt);
    return !!expected && expected.length === 128 && timingSafeEqual(actual, Buffer.from(expected, 'hex'));
}
export const displayName = z.string().trim().min(2).max(50).refine(s => /[\p{L}\p{N}]/u.test(s) && !/[\p{Cc}\p{Cf}]/u.test(s));
export const vozId = z.string().regex(/^VOZ-\d{3}-\d{4}$/);
export const uuid = z.uuid();
export class Fault extends Error {
    status;
    code;
    constructor(status, code) {
        super(code);
        this.status = status;
        this.code = code;
    }
}
export function requireThat(ok, code, status = 409) { if (!ok)
    throw new Fault(status, code); }
export function turnCredentials(account, urls, key, now = Math.floor(Date.now() / 1000)) {
    requireThat(urls.length && key, 'TURN_NOT_CONFIGURED', 503);
    const expiresAt = now + 600, username = `${expiresAt}:${account}`;
    return { expiresAt, iceServers: [{ urls, username, credential: createHmac('sha1', key).update(username).digest('base64') }] };
}
