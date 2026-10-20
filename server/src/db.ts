import {Pool, type PoolClient} from 'pg';
import {readFile} from 'node:fs/promises';
export function database(url: string) { return new Pool({connectionString:url,max:15, connectionTimeoutMillis:5000, statement_timeout:10000}); }
export async function tx<T>(pool:Pool, fn:(c:PoolClient)=>Promise<T>):Promise<T> {
 const c=await pool.connect();try{await c.query('BEGIN');const r=await fn(c);await c.query('COMMIT');return r;}catch(e){await c.query('ROLLBACK');throw e;}finally{c.release();}
}
export async function migrate(pool:Pool) {
 await tx(pool,async c=>{
  await c.query("SELECT pg_advisory_xact_lock(80219811)");
  await c.query('CREATE TABLE IF NOT EXISTS schema_migrations(version text PRIMARY KEY, applied_at timestamptz DEFAULT now())');
  if(!(await c.query("SELECT 1 FROM schema_migrations WHERE version='001'")).rowCount){
   await c.query(await readFile(new URL('../migrations/001_initial.sql',import.meta.url),'utf8'));
   await c.query("INSERT INTO schema_migrations(version) VALUES('001')");
  }
 });
}
