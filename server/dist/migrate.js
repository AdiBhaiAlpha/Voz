import { database, migrate } from './db.js';
const pool = database(process.env.DATABASE_URL || '');
try {
    await migrate(pool);
}
finally {
    await pool.end();
}
