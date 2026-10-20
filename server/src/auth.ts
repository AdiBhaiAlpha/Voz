import type {Pool,PoolClient} from 'pg';
import {randomUUID,randomInt} from 'node:crypto';
import {tx} from './db.js';
import {Fault,requireThat,secret,hash,passwordHash,verify,displayName} from './security.js';
export interface Principal {accountId:string; sessionId:string; deviceId:string|null;}
export class Auth {
 constructor(public pool:Pool){}
 async issue(c:PoolClient,accountId:string) {
  const accessToken=secret(),refreshToken=secret(),id=randomUUID();
  await c.query("INSERT INTO sessions(id,account_id,access_hash,refresh_hash,access_expires,expires) VALUES($1,$2,$3,$4,now()+interval '15 minutes',now()+interval '30 days')",[id,accountId,hash(accessToken),hash(refreshToken)]);
  return {accessToken,refreshToken,expiresIn:900};
 }
 async signup(username:string,password:string){
  const encoded=await passwordHash(password);
  return tx(this.pool,async c=>{
   const id=randomUUID();
   const result=await c.query('INSERT INTO accounts VALUES($1,$2,$3,now()) ON CONFLICT(username) DO NOTHING RETURNING id',[id,username,encoded]);
   requireThat(result.rowCount,'USERNAME_UNAVAILABLE');return this.issue(c,id);
  });
 }
 async login(username:string,password:string){
  const row=(await this.pool.query('SELECT * FROM accounts WHERE username=$1',[username])).rows[0];
  requireThat(await verify(password,row?.password_hash),'INVALID_CREDENTIALS',401);
  return tx(this.pool,c=>this.issue(c,row.id));
 }
 async authenticate(token:string):Promise<Principal>{
  requireThat(token.length>=32 && token.length<=128,'UNAUTHORIZED',401);
  const s=(await this.pool.query(`SELECT s.* FROM sessions s LEFT JOIN devices d ON d.id=s.device_id
   WHERE access_hash=$1 AND s.revoked_at IS NULL AND access_expires>now() AND expires>now()
   AND (s.device_id IS NULL OR d.revoked_at IS NULL)`,[hash(token)])).rows[0];
  requireThat(s,'UNAUTHORIZED',401);return {accountId:s.account_id,sessionId:s.id,deviceId:s.device_id};
 }
 async refresh(token:string){
  const h=hash(token);
  const result=await tx(this.pool,async c=>{
   const s=(await c.query('SELECT * FROM sessions WHERE refresh_hash=$1 FOR UPDATE',[h])).rows[0];
   if(!s){
    await c.query('UPDATE sessions SET revoked_at=now() WHERE id=(SELECT session_id FROM consumed_refresh WHERE token_hash=$1)',[h]);
    return null;
   }
   if(s.revoked_at || new Date(s.expires).getTime()<=Date.now())return null;
   if(s.device_id && !(await c.query('SELECT 1 FROM devices WHERE id=$1 AND revoked_at IS NULL',[s.device_id])).rowCount)return null;
   const accessToken=secret(),refreshToken=secret();
   await c.query('INSERT INTO consumed_refresh(token_hash,session_id) VALUES($1,$2)',[h,s.id]);
   await c.query("UPDATE sessions SET access_hash=$2,refresh_hash=$3,access_expires=now()+interval '15 minutes' WHERE id=$1",[s.id,hash(accessToken),hash(refreshToken)]);
   return {accessToken,refreshToken,expiresIn:900};
  });requireThat(result,'REFRESH_REJECTED',401);return result;
 }
 async identity(p:Principal,name:string){
  displayName.parse(name);
  return tx(this.pool,async c=>{
   await c.query('SELECT id FROM accounts WHERE id=$1 FOR UPDATE',[p.accountId]);
   const existing=(await c.query('SELECT * FROM identities WHERE account_id=$1',[p.accountId])).rows[0];
   if(existing)return existing;
   for(let n=0;n<100;n++){
    const digits=String(randomInt(10_000_000)).padStart(7,'0'),id=`VOZ-${digits.slice(0,3)}-${digits.slice(3)}`;
    const r=await c.query('INSERT INTO identities VALUES($1,$2,$3) ON CONFLICT(voz_id) DO NOTHING RETURNING *',[p.accountId,id,name]);
    if(r.rowCount)return r.rows[0];
   }throw new Fault(503,'ID_CAPACITY');
  });
 }
 async device(p:Principal,input:{installationId:string;name:string;pushToken?:string|null}){
  return tx(this.pool,async c=>{
   // One registration per installation per account. No client-supplied server device ID.
   await c.query('SELECT id FROM accounts WHERE id=$1 FOR UPDATE',[p.accountId]);
   const existing=(await c.query('SELECT * FROM devices WHERE account_id=$1 AND installation_id=$2',[p.accountId,input.installationId])).rows[0];
   requireThat(!p.deviceId || p.deviceId===existing?.id,'SESSION_ALREADY_BOUND');
   const id=existing?.id || randomUUID();
   if(input.pushToken){
    // A token cannot be transferred between accounts by an unverified client.
    const owner=(await c.query('SELECT account_id FROM devices WHERE push_token=$1 AND revoked_at IS NULL',[input.pushToken])).rows[0];
    requireThat(!owner || owner.account_id===p.accountId,'DEVICE_TOKEN_IN_USE');
   }
   const r=await c.query(`INSERT INTO devices(id,account_id,installation_id,name,push_token) VALUES($1,$2,$3,$4,$5)
    ON CONFLICT(account_id,installation_id) DO UPDATE SET name=$4,push_token=$5,last_seen=now(),revoked_at=NULL RETURNING id`,[id,p.accountId,input.installationId,input.name,input.pushToken===undefined ? existing?.push_token || null : input.pushToken]);
   await c.query('UPDATE sessions SET device_id=$2 WHERE id=$1',[p.sessionId,id]);return r.rows[0];
  });
 }
}
