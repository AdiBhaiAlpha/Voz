import {test} from 'node:test';import assert from 'node:assert/strict';
import {passwordHash,verify,turnCredentials,displayName} from '../src/security.js';
import {createHmac} from 'node:crypto';
test('scrypt hashes salted passwords and rejects incorrect credentials',async()=>{const a=await passwordHash('correct passphrase');const b=await passwordHash('correct passphrase');assert.notEqual(a,b);assert.ok(await verify('correct passphrase',a));assert.equal(await verify('incorrect',a),false);assert.equal(await verify('unknown'),false);});
test('names require visible letters/numbers and reject controls',()=>{for(const s of ['',' ','--','a','a\u0000b'])assert.equal(displayName.safeParse(s).success,false);assert.equal(displayName.parse(' রহিম '),'রহিম');});
test('TURN credentials bind identity and expire after ten minutes',()=>{const result=turnCredentials('account',['turn:relay.test:3478'],'test-secret',1000);assert.equal(result.expiresAt,1600);assert.equal(result.iceServers[0]?.username,'1600:account');assert.equal(result.iceServers[0]?.credential,createHmac('sha1','test-secret').update('1600:account').digest('base64'));assert.throws(()=>turnCredentials('a',[],''));});
