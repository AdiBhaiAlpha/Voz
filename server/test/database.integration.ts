import {test} from 'node:test';import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';import {mkdtemp,rm} from 'node:fs/promises';import {tmpdir} from 'node:os';import {join} from 'node:path';import net from 'node:net';
import EmbeddedPostgres from 'embedded-postgres';
import {database,migrate} from '../src/db.js';import {buildApp} from '../src/app.js';import {deliverPush} from '../src/push.js';
import WebSocket from 'ws';import {once} from 'node:events';

test('PostgreSQL vertical slice', {timeout:120000},async t=>{
 let embedded:EmbeddedPostgres|undefined,dir:string|undefined;
 let url=process.env.TEST_DATABASE_URL;
 if(!url){
  dir=await mkdtemp(join(tmpdir(),'voz-pg-'));const probe=net.createServer();probe.listen(0,'127.0.0.1');await once(probe,'listening');const port=(probe.address() as net.AddressInfo).port;await new Promise<void>(r=>probe.close(()=>r()));
  embedded=new EmbeddedPostgres({databaseDir:join(dir,'cluster'),port,user:'voztest',password:'local-test-only',persistent:false,onLog:()=>{},onError:()=>{}});
  await embedded.initialise();await embedded.start();await embedded.createDatabase('voztest');url=`postgres://voztest:local-test-only@127.0.0.1:${port}/voztest`;
 }
 const pool=database(url);await migrate(pool);await migrate(pool);
 const pushes:any[]=[];const fixture=await buildApp({pool,turnUrls:['turn:relay.test:3478'],turnSecret:'test-only-key',workers:false,push:{send:async(...args)=>{pushes.push(args);}}});
 const {app,auth,calls}=fixture;await app.listen({host:'127.0.0.1',port:0});
 t.after(async()=>{await app.close();await pool.end();await embedded?.stop();if(dir)await rm(dir,{recursive:true,force:true});});
 let ip=1;
 const api=async(method:string,path:string,body?:unknown,token?:string)=>{const r=await app.inject({method:method as any,url:path,remoteAddress:`127.1.${Math.floor(ip/200)}.${++ip%200+1}`,headers:token?{authorization:`Bearer ${token}`}:{},...(body?{payload:body}:{})});return {status:r.statusCode,body:r.json()};};
 async function user(name:string,pushToken='fake-push-token-'+randomUUID()){
  const account=await api('POST','/v1/auth/signup',{username:name,password:'correct long password'});assert.equal(account.status,200);
  const token=account.body.accessToken;const identity=await api('POST','/v1/identity',{displayName:name},token);assert.equal(identity.status,200);
  const device=await api('POST','/v1/devices',{installationId:randomUUID(),name:'Test phone',pushToken},token);assert.equal(device.status,200);
  return {token,refresh:account.body.refreshToken,p:await auth.authenticate(token),id:identity.body.voz_id,device:device.body.id};
 }
 const a=await user('alice'),b=await user('bob'),eve=await user('eve');
 await t.test('identity registration requires authentication; server IDs unique and stable',async()=>{
  assert.equal((await api('POST','/v1/identity',{displayName:'Fake'})).status,401);
  assert.match(a.id,/^VOZ-\d{3}-\d{4}$/);assert.notEqual(a.id,b.id);
  assert.equal((await api('POST','/v1/identity',{displayName:'new name',vozId:b.id},a.token)).status,400);
  assert.equal((await api('POST','/v1/identity',{displayName:'Alice'},a.token)).body.voz_id,a.id);
  await assert.rejects(pool.query('UPDATE identities SET voz_id=$1 WHERE account_id=$2',[a.id,b.p.accountId]),(e:any)=>e.code==='23505');
 });
 await t.test('lookup is authenticated and returns verified recipient',async()=>{
  assert.equal((await api('GET',`/v1/recipients/${b.id}`)).status,401);
  assert.equal((await api('GET',`/v1/recipients/${b.id}`,undefined,a.token)).body.display_name,'bob');
 });
 const start=()=>calls.begin(a.p,b.id,randomUUID());
 await t.test('server call state, multi-device authorization, idempotency and media generations',async()=>{
  const request=randomUUID(),r=await calls.begin(a.p,b.id,request),id=r.call.id;
  assert.equal((await calls.begin(a.p,b.id,request)).duplicate,true);
  await assert.rejects(calls.begin(eve.p,b.id,randomUUID()),/BUSY/);
  await assert.rejects(calls.command(eve.p,id,randomUUID(),'accept'),/CALL_NOT_FOUND/);
  await assert.rejects(calls.command(a.p,id,randomUUID(),'accept'),/INVALID_TRANSITION/);
  await assert.rejects(calls.command(a.p,id,randomUUID(),'offer',{generation:1,sdp:'offer'}),/INVALID_TRANSITION/);
  const event=randomUUID();const accepted=await calls.command(b.p,id,event,'accept');assert.equal(accepted.call.state,'accepted');assert.equal((await calls.command(b.p,id,event,'accept')).duplicate,true);
  await assert.rejects(calls.command(b.p,id,randomUUID(),'cancel'),/INVALID_TRANSITION/);
  await assert.rejects(calls.command(b.p,id,randomUUID(),'offer',{generation:2,sdp:'forged'}),/ONLY_CALLER_OFFERS/);
  await calls.command(a.p,id,randomUUID(),'offer',{generation:2,sdp:'real-offer'});
  await calls.command(b.p,id,randomUUID(),'answer',{generation:2,sdp:'real-answer'});
  await calls.command(a.p,id,randomUUID(),'ice',{generation:2,candidate:'candidate:test',sdpMid:'0',sdpMLineIndex:0});
  await calls.command(a.p,id,randomUUID(),'restart');
  await assert.rejects(calls.command(b.p,id,randomUUID(),'ice',{generation:2,candidate:'old',sdpMid:'0',sdpMLineIndex:0}),/STALE_NEGOTIATION/);
  const replay=await calls.events(b.p,id,'0');assert.ok(replay.events.some(e=>e.type==='offer'));
  const last=replay.events.at(-1)!.seq;assert.equal((await calls.events(b.p,id,last)).events.length,0);
  await calls.command(a.p,id,randomUUID(),'end');await assert.rejects(calls.command(b.p,id,randomUUID(),'accept'),/CALL_ENDED/);
  assert.equal((await pool.query('SELECT * FROM active_call_members')).rowCount,0);
 });
 await t.test('competing call starts cannot claim the same recipient',async()=>{
  const results=await Promise.allSettled([calls.begin(a.p,b.id,randomUUID()),calls.begin(eve.p,b.id,randomUUID())]);
  assert.equal(results.filter(r=>r.status==='fulfilled').length,1);const win=results.find(r=>r.status==='fulfilled') as PromiseFulfilledResult<any>;
  const caller=win.value.call.from_account===a.p.accountId?a:eve;await calls.command(caller.p,win.value.call.id,randomUUID(),'cancel');
 });
 await t.test('reject, expiry, push validation and retries',async()=>{
  let r=await start();await deliverPush(pool,{send:async(...args)=>{pushes.push(args);}});assert.ok(pushes.some(p=>p[1].callId===r.call.id));
  await calls.command(b.p,r.call.id,randomUUID(),'reject');
  r=await start();await pool.query("UPDATE calls SET expires_at=now()-interval '1 second' WHERE id=$1",[r.call.id]);await calls.expire();
  assert.equal((await calls.get(a.p,r.call.id)).reason,'unanswered');
  await assert.rejects(calls.command(b.p,r.call.id,randomUUID(),'accept'),/CALL_ENDED/);
 });
 await t.test('device revocation forbids accepting and session rotation detects refresh replay',async()=>{
  const c=await user('carol');const rotated=await api('POST','/v1/auth/refresh',{refreshToken:c.refresh});assert.equal(rotated.status,200);
  assert.equal((await api('GET','/v1/me',undefined,c.token)).status,401);
  assert.equal((await api('POST','/v1/auth/refresh',{refreshToken:c.refresh})).status,401);
  assert.equal((await api('GET','/v1/me',undefined,rotated.body.accessToken)).status,401);
  await api('DELETE',`/v1/devices/${c.device}`,undefined,c.token); // old token cannot revoke anything
  const d=await user('david');assert.equal((await api('DELETE',`/v1/devices/${d.device}`,undefined,d.token)).status,200);assert.equal((await api('GET','/v1/me',undefined,d.token)).status,401);
 });
 await t.test('authenticated websocket delivers invitations and supports reconnect replay',async()=>{
  const port=(app.server.address() as net.AddressInfo).port;
  const ws=new WebSocket(`ws://127.0.0.1:${port}/v1/signal`);await once(ws,'open');
  const ready=once(ws,'message');ws.send(JSON.stringify({type:'auth',accessToken:b.token}));assert.equal(JSON.parse(String((await ready)[0])).type,'ready');
  const incoming=once(ws,'message');const r=await start();const message=JSON.parse(String((await incoming)[0]));assert.equal(message.call.id,r.call.id);
  ws.close();await once(ws,'close');assert.equal((await calls.get(b.p,r.call.id)).state,'ringing');
  assert.ok((await calls.events(b.p,r.call.id,'0')).events.length);await calls.command(b.p,r.call.id,randomUUID(),'reject');
 });
 await t.test('only the accepting device can send media or end an accepted call',async()=>{
  const login=await api('POST','/v1/auth/login',{username:'bob',password:'correct long password'});
  const d=await api('POST','/v1/devices',{installationId:randomUUID(),name:'Other Bob phone',pushToken:'fake-other-'+randomUUID()},login.body.accessToken);
  const other=await auth.authenticate(login.body.accessToken);
  const r=await start();await calls.command(b.p,r.call.id,randomUUID(),'accept');
  await calls.command(a.p,r.call.id,randomUUID(),'offer',{generation:2,sdp:'private-sdp'});
  await assert.rejects(calls.command(other,r.call.id,randomUUID(),'ice',{generation:2,candidate:'spoof',sdpMid:'0',sdpMLineIndex:0}),/WRONG_DEVICE/);
  await assert.rejects(calls.command(other,r.call.id,randomUUID(),'end'),/WRONG_DEVICE/);
  assert.equal((await calls.events(other,r.call.id,'0')).events.some(e=>e.type==='offer'),false);
  await calls.command(b.p,r.call.id,randomUUID(),'end');
 });
 await t.test('failed push has an explicit unavailable outcome, not endless ringing',async()=>{
  const r=await start();const sender={send:async()=>{throw Object.assign(new Error('invalid token'),{code:'messaging/registration-token-not-registered'});}};
  await deliverPush(pool,sender);await calls.failUndeliverable();
  assert.equal((await calls.get(a.p,r.call.id)).reason,'unavailable');
  // Fresh token registration restores reachability; invalid tokens were removed.
  await pool.query('UPDATE devices SET push_token=$2 WHERE id=$1',[b.device,'fresh-test-token-'+randomUUID()]);
 });
 await t.test('accepted calls with stale signaling leases release concurrency slots',async()=>{
  const r=await start();await calls.command(b.p,r.call.id,randomUUID(),'accept');
  await pool.query("UPDATE calls SET caller_seen=now()-interval '91 seconds' WHERE id=$1",[r.call.id]);await calls.expire();
  assert.equal((await calls.get(a.p,r.call.id)).state,'ended');
  assert.equal((await pool.query('SELECT * FROM active_call_members WHERE call_id=$1',[r.call.id])).rowCount,0);
 });
 await t.test('API rejects forged caller fields, unauthorized events and malformed media',async()=>{
  assert.equal((await api('POST','/v1/calls',{target:b.id,requestId:randomUUID(),callerId:eve.id},a.token)).status,400);
  const r=await start();
  assert.equal((await api('POST',`/v1/calls/${r.call.id}/events`,{eventId:randomUUID(),action:'accept'})).status,401);
  assert.equal((await api('POST',`/v1/calls/${r.call.id}/events`,{eventId:randomUUID(),action:'ice',payload:{candidate:'x'}},b.token)).status,400);
  const ice=await api('GET','/v1/ice',undefined,a.token);assert.equal(ice.status,200);assert.ok(ice.body.expiresAt>Math.floor(Date.now()/1000));assert.ok(ice.body.expiresAt<=Math.floor(Date.now()/1000)+600);
  await calls.command(a.p,r.call.id,randomUUID(),'cancel');
 });
 await t.test('account deletion revokes identity and active calls',async()=>{
  const c=await user('deletable');const call=await calls.begin(c.p,a.id,randomUUID());
  assert.equal((await api('DELETE','/v1/account',undefined,c.token)).status,200);
  assert.equal((await api('GET','/v1/me',undefined,c.token)).status,401);assert.equal((await pool.query('SELECT * FROM active_call_members WHERE call_id=$1',[call.call.id])).rowCount,0);
 });
});
