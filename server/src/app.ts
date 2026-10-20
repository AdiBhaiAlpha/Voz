import Fastify,{LogController} from 'fastify';
import websocket from '@fastify/websocket';
import rateLimit from '@fastify/rate-limit';
import {z} from 'zod';
import type {Pool} from 'pg';
import type {WebSocket} from 'ws';
import {Auth,type Principal} from './auth.js';
import {Calls,type Action} from './calls.js';
import {Fault,requireThat,displayName,vozId,uuid,turnCredentials} from './security.js';
import {deliverPush,type PushSender} from './push.js';
export interface Config {pool:Pool;turnUrls:string[];stunUrls?:string[];turnSecret:string;push?:PushSender;logger?:boolean;trustProxy?:boolean;workers?:boolean;origin?:string;metricsToken?:string;}
const credentials=z.object({username:z.string().regex(/^[a-z0-9_]{3,40}$/),password:z.string().min(12).max(128)}).strict();
const command=z.union([
 z.object({eventId:uuid,action:z.enum(['accept','reject','cancel','end','restart','restart_request']),payload:z.object({}).strict().default({})}).strict(),
 z.object({eventId:uuid,action:z.enum(['offer','answer']),payload:z.object({generation:z.number().int().positive(),sdp:z.string().min(1).max(60000)}).strict()}).strict(),
 z.object({eventId:uuid,action:z.literal('ice'),payload:z.object({generation:z.number().int().positive(),candidate:z.string().max(8192),sdpMid:z.string().max(64).nullable(),sdpMLineIndex:z.number().int().min(0).max(10)}).strict()}).strict()
]);
export async function buildApp(config:Config){
 const app=Fastify({logger:config.logger?{redact:['req.headers.authorization','req.headers.cookie'],level:'info'}:false,logController:new LogController({disableRequestLogging:true}),bodyLimit:70_000,trustProxy:config.trustProxy || false});
 await app.register(rateLimit,{max:120,timeWindow:'1 minute'});
 await app.register(websocket,{options:{maxPayload:70_000}});
 app.addHook('onResponse',async(req,reply)=>{app.log.info({requestId:req.id,route:req.routeOptions.url,status:reply.statusCode,elapsedMs:reply.elapsedTime},'request');});
 const auth=new Auth(config.pool),sockets=new Map<WebSocket,{principal:Principal;token:string;alive:boolean;count:number;window:number}>();
 const online=(device:string)=>Array.from(sockets.values()).some(s=>s.principal.deviceId===device);
 const publish=(event:any)=>{ void (async()=>{
  const media=['offer','answer','ice','restart','restart_request'].includes(event.action);
  for(const [socket,s] of sockets){
   try { await auth.authenticate(s.token); } catch { socket.close(1008,'Session expired'); continue; }
   const c=event.call,p=s.principal;
   if(![c.from_account,c.to_account].includes(p.accountId))continue;
   if(media && ![c.from_device,c.to_device].includes(p.deviceId))continue;
   if(event.action==='incoming' && p.accountId===c.from_account && p.deviceId!==c.from_device)continue;
   if(socket.readyState===1)socket.send(JSON.stringify(event));
  }
 })().catch(()=>app.log.error('event delivery failed')); };
 const calls=new Calls(config.pool,online,publish,!!config.push);
 app.setErrorHandler((error,req,reply)=>{
  if(error instanceof z.ZodError)return reply.code(400).send({error:'INVALID_INPUT'});
  if(error instanceof Fault)return reply.code(error.status).send({error:error.code});
  if((error as any).statusCode===400)return reply.code(400).send({error:'INVALID_INPUT'});
  if((error as any).statusCode===413)return reply.code(413).send({error:'PAYLOAD_TOO_LARGE'});
  if((error as any).statusCode===429)return reply.code(429).send({error:'RATE_LIMITED'});
  app.log.error({requestId:req.id,code:(error as any).code},'request failed');
  return reply.code(500).send({error:'INTERNAL_ERROR'});
 });
 const principal=async(req:any)=>{const h=req.headers.authorization;requireThat(typeof h==='string' && h.startsWith('Bearer '),'UNAUTHORIZED',401);return auth.authenticate(h.slice(7));};
 const closeAccount=(account:string,device?:string)=>{for(const [socket,s] of sockets)if(s.principal.accountId===account && (!device || device===s.principal.deviceId))socket.close(1008,'Revoked');};
 app.get('/live',async()=>({ok:true}));
 app.get('/ready',async(req,reply)=>{await config.pool.query('SELECT 1');const ready=!!config.turnSecret && config.turnUrls.length>0;return reply.code(ready?200:503).send({ok:ready});});
 app.get('/metrics',{preHandler:async(req)=>{requireThat(config.metricsToken && req.headers.authorization===`Bearer ${config.metricsToken}`,'NOT_FOUND',404);}},async()=>{
  // Counts only; never IDs, tokens, SDP, ICE addresses or caller metadata.
  const states=(await config.pool.query('SELECT state,count(*)::integer AS count FROM calls GROUP BY state')).rows;
  const pushes=(await config.pool.query('SELECT count(*) FILTER(WHERE failed_at IS NOT NULL)::integer AS failed,count(*) FILTER(WHERE delivered_at IS NOT NULL)::integer AS delivered FROM push_outbox')).rows[0];
  return {websockets:sockets.size,calls:states,push:pushes};
 });
 app.post('/v1/auth/signup',{config:{rateLimit:{max:5,timeWindow:'1 minute'}}},async req=>{const b=credentials.parse(req.body);return auth.signup(b.username,b.password);});
 app.post('/v1/auth/login',{config:{rateLimit:{max:10,timeWindow:'1 minute'}}},async req=>{const b=credentials.parse(req.body);return auth.login(b.username,b.password);});
 app.post('/v1/auth/refresh',{config:{rateLimit:{max:30,timeWindow:'1 minute'}}},async req=>auth.refresh(z.object({refreshToken:z.string().min(32).max(128)}).strict().parse(req.body).refreshToken));
 app.post('/v1/identity',async req=>{const p=await principal(req);const b=z.object({displayName}).strict().parse(req.body);return auth.identity(p,b.displayName);});
 app.get('/v1/me',async req=>{const p=await principal(req);const identity=(await config.pool.query('SELECT voz_id,display_name FROM identities WHERE account_id=$1',[p.accountId])).rows[0];return {accountId:p.accountId,deviceId:p.deviceId,identity:identity || null};});
 app.post('/v1/devices',async req=>{const p=await principal(req);const b=z.object({installationId:uuid,name:z.string().trim().min(1).max(80),pushToken:z.string().min(10).max(4096).nullable().optional()}).strict().parse(req.body);return auth.device(p,b);});
 app.get('/v1/devices',async req=>{const p=await principal(req);return {devices:(await config.pool.query('SELECT id,name,last_seen,revoked_at FROM devices WHERE account_id=$1',[p.accountId])).rows};});
 app.delete('/v1/devices/:id',async req=>{const p=await principal(req);const id=uuid.parse((req.params as any).id);await config.pool.query('UPDATE devices SET revoked_at=now(),push_token=NULL WHERE id=$1 AND account_id=$2',[id,p.accountId]);await config.pool.query('UPDATE sessions SET revoked_at=now() WHERE device_id=$1 AND account_id=$2',[id,p.accountId]);await calls.revoke(p,id);closeAccount(p.accountId,id);return {ok:true};});
 app.post('/v1/auth/logout',async req=>{const p=await principal(req);await config.pool.query('UPDATE sessions SET revoked_at=now() WHERE id=$1',[p.sessionId]);if(p.deviceId){await config.pool.query('UPDATE devices SET push_token=NULL WHERE id=$1',[p.deviceId]);await calls.revoke(p,p.deviceId);}closeAccount(p.accountId,p.deviceId || undefined);return {ok:true};});
 app.delete('/v1/account',async req=>{const p=await principal(req);await calls.revoke(p);await config.pool.query('DELETE FROM accounts WHERE id=$1',[p.accountId]);closeAccount(p.accountId);return {ok:true};});
 app.get('/v1/recipients/:id',{config:{rateLimit:{max:30,timeWindow:'1 minute'}}},async req=>{await principal(req);const id=vozId.parse((req.params as any).id);const row=(await config.pool.query('SELECT voz_id,display_name FROM identities WHERE voz_id=$1',[id])).rows[0];requireThat(row,'RECIPIENT_UNAVAILABLE',404);return row;});
 app.get('/v1/ice',async req=>{const p=await principal(req);const turn=turnCredentials(p.accountId,config.turnUrls,config.turnSecret);return {...turn,iceServers:[...(config.stunUrls?.length?[{urls:config.stunUrls}]:[]),...turn.iceServers]};});
 app.post('/v1/calls',{config:{rateLimit:{max:15,timeWindow:'1 minute'}}},async req=>{const p=await principal(req);const b=z.object({target:vozId,requestId:uuid}).strict().parse(req.body);return calls.begin(p,b.target,b.requestId);});
 app.get('/v1/calls/active',async req=>{const p=await principal(req);const row=(await config.pool.query('SELECT c.* FROM calls c JOIN active_call_members m ON m.call_id=c.id WHERE m.account_id=$1',[p.accountId])).rows[0];return {call:row || null};});
 app.get('/v1/calls/:id',async req=>calls.get(await principal(req),uuid.parse((req.params as any).id)));
 app.get('/v1/calls/:id/events',async req=>{const p=await principal(req);const after=z.string().regex(/^\d{1,18}$/).default('0').parse((req.query as any).after);return calls.events(p,uuid.parse((req.params as any).id),after);});
 app.post('/v1/calls/:id/events',async req=>{const p=await principal(req),b=command.parse(req.body);return calls.command(p,uuid.parse((req.params as any).id),b.eventId,b.action as Action,b.payload);});
 app.get('/v1/signal',{websocket:true},(socket,req)=>{
  if(req.headers.origin && req.headers.origin!==config.origin){socket.close(1008,'Origin rejected');return;}
  const timeout=setTimeout(()=>socket.close(1008,'Authenticate'),5000);
  let queue=Promise.resolve();
  socket.on('pong',()=>{const s=sockets.get(socket);if(s){s.alive=true;void auth.authenticate(s.token).then(p=>calls.heartbeat(p)).catch(()=>socket.close(1008,'Session expired'));}});
  socket.on('message',data=>{queue=queue.then(async()=>{
   try{
    const m=JSON.parse(data.toString());const state=sockets.get(socket);
    if(!state){
     const a=z.object({type:z.literal('auth'),accessToken:z.string().min(32).max(128)}).strict().parse(m);
     const p=await auth.authenticate(a.accessToken);requireThat(p.deviceId,'REGISTER_DEVICE',403);
     // One live transport per session. Android reconnects and replays durable events.
     for(const [old,s] of sockets)if(s.principal.sessionId===p.sessionId)old.close(1000,'Reconnected');
     sockets.set(socket,{principal:p,token:a.accessToken,alive:true,count:0,window:Date.now()});clearTimeout(timeout);
     socket.send(JSON.stringify({type:'ready'}));return;
    }
    if(Date.now()-state.window>60_000){state.count=0;state.window=Date.now();}
    requireThat(++state.count<=180,'RATE_LIMITED',429);
    const p=await auth.authenticate(state.token);
    if(m.type==='heartbeat'){await calls.heartbeat(p,uuid.parse(m.callId));return;}
    const input=z.object({type:z.literal('command'),callId:uuid,command}).strict().parse(m);
    const r=await calls.command(p,input.callId,input.command.eventId,input.command.action as Action,input.command.payload);
    socket.send(JSON.stringify({type:'ack',eventId:input.command.eventId,...r}));
   }catch(e){socket.send(JSON.stringify({type:'error',error:e instanceof Fault?e.code:'INVALID_INPUT'}));if(e instanceof Fault && e.status===401)socket.close(1008,'Session expired');}
  });});
  socket.on('close',()=>{clearTimeout(timeout);sockets.delete(socket);});
 });
 let working=false,pushWorking=false,sweeps=0;
 const sweep=async()=>{if(working)return;working=true;try{await calls.expire();if(config.push)await calls.failUndeliverable();
 if(++sweeps%60===0){
  await config.pool.query("DELETE FROM call_events e USING calls c WHERE e.call_id=c.id AND c.state='ended' AND c.ended_at<now()-interval '1 hour'");
  await config.pool.query("DELETE FROM calls WHERE state='ended' AND ended_at<now()-interval '30 days'");
  await config.pool.query("DELETE FROM sessions WHERE expires<now()");
 }}catch(e){app.log.error('call worker failed');}finally{working=false;}};
 const pushSweep=async()=>{if(pushWorking || !config.push)return;pushWorking=true;
  try{await deliverPush(config.pool,config.push);}catch{app.log.error('push worker failed');}finally{pushWorking=false;}
 };
 const timers:NodeJS.Timeout[]=[];
 if(config.workers!==false){timers.push(setInterval(()=>void sweep(),1000));timers.push(setInterval(()=>void pushSweep(),1000));timers.push(setInterval(()=>{for(const [socket,s] of sockets){if(!s.alive)socket.terminate();else{s.alive=false;socket.ping();}}},25_000));}
 app.addHook('onClose',async()=>{timers.forEach(clearInterval);for(const socket of sockets.keys())socket.terminate();});
 return {app,auth,calls,sweep};
}
