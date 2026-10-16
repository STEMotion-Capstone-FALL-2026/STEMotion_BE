import {PGlite} from '@electric-sql/pglite';
import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
const db=new PGlite();
await db.exec(`CREATE TABLE ai_requests(id text PRIMARY KEY,created_at timestamptz,model text,requester_id text,
 input_tokens integer,output_tokens integer,monetary_cost numeric(20,10),cost_currency varchar(3));
 INSERT INTO ai_requests(id,model) VALUES ('old','legacy')`);
const up=readFileSync('../../main/resources/db/migration/V18__ai_pricing_provenance.sql','utf8');
const down=readFileSync('../../../docs/rollback/V18__ai_pricing_provenance.down.sql','utf8');
await db.exec(up);
assert.equal((await db.query('SELECT pricing_version FROM ai_requests')).rows[0].pricing_version,null);
await assert.rejects(db.exec("UPDATE ai_requests SET pricing_version='bad',pricing_input_per_million=-1,pricing_output_per_million=2"));
await db.exec("UPDATE ai_requests SET pricing_version='test-v1',pricing_input_per_million=1.5,pricing_output_per_million=2.5");
await assert.rejects(db.exec(down));
await db.exec('ROLLBACK');
await db.exec('UPDATE ai_requests SET pricing_version=NULL,pricing_input_per_million=NULL,pricing_output_per_million=NULL');
await db.exec(down);await db.exec(up);
assert.equal((await db.query('SELECT model FROM ai_requests')).rows[0].model,'legacy');
await db.close();console.log('V18 nullable history, rate constraints, guarded rollback and reapply PASS');
