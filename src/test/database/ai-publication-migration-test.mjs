import {PGlite} from '@electric-sql/pglite';
import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
const sql=(version,down=false,shared=false)=>readFileSync(`../../../${down?'docs/rollback':'src/main/resources/db/migration'}/${version}${down?(shared?'.shared.down.sql':'.down.sql'):'.sql'}`,'utf8');
const ai='V12__ai_request_tracking',pub='V13__publication_transition';
const id=n=>`00000000-0000-0000-0000-${String(n).padStart(12,'0')}`;
for(const shared of [false,true]) {
  const db=new PGlite();
  const type=shared?'uuid':'varchar(36)';
  await db.exec(`CREATE TABLE users(id ${type} PRIMARY KEY);
    CREATE TABLE clip_projects(id ${type} PRIMARY KEY);
    CREATE TABLE script_versions(id ${type} PRIMARY KEY);
    CREATE TABLE scenes(id ${type} PRIMARY KEY);
    CREATE TABLE render_jobs(id ${type} PRIMARY KEY,project_id ${type} REFERENCES clip_projects(id));
    CREATE TABLE clips(id uuid PRIMARY KEY,render_job_id ${type} UNIQUE REFERENCES render_jobs(id));
    INSERT INTO users VALUES ('${id(1)}');INSERT INTO clip_projects VALUES ('${id(2)}');
    INSERT INTO script_versions VALUES ('${id(3)}');
    INSERT INTO render_jobs VALUES ('${id(4)}','${id(2)}');
    INSERT INTO clips VALUES ('${id(5)}','${id(4)}');
    INSERT INTO clips VALUES ('${id(6)}',NULL);`);
  if(shared) await db.exec(`CREATE TYPE ai_req_type AS ENUM ('ANALYSIS','SEGMENTATION','TTS','CAPTION','QA');
    CREATE TYPE ai_decision_type AS ENUM ('PENDING','ACCEPTED','IGNORED');
    CREATE TYPE publish_platform AS ENUM ('YOUTUBE','INTERNAL_LIBRARY');
    CREATE TABLE ai_requests (id uuid PRIMARY KEY DEFAULT gen_random_uuid(),requester_id uuid REFERENCES users(id),
      request_type ai_req_type NOT NULL,script_version_id uuid REFERENCES script_versions(id),
      scene_id uuid REFERENCES scenes(id),clip_id uuid REFERENCES clips(id), model varchar NOT NULL,
      response_data jsonb,decision ai_decision_type DEFAULT 'PENDING',cost_tokens integer DEFAULT 0,
      latency_ms integer DEFAULT 0,created_at timestamptz DEFAULT now(),
      CONSTRAINT chk_ai_target CHECK(num_nonnulls(script_version_id,scene_id,clip_id)=1));
    CREATE TABLE publications(id uuid PRIMARY KEY DEFAULT gen_random_uuid(),clip_id uuid REFERENCES clips(id),
      publisher_id uuid REFERENCES users(id),platform publish_platform NOT NULL,metadata jsonb,
      published_at timestamptz DEFAULT now(),UNIQUE(clip_id,platform));`);
  await db.exec(sql(ai));
  await db.exec(sql(pub));
  await db.exec(sql(pub,true,shared));
  await db.exec(sql(ai,true,shared));
  await db.exec(sql(ai));await db.exec(sql(pub));
  await db.exec(`INSERT INTO ai_requests(requester_id,request_type,model,call_status,response_data)
    VALUES ('${id(1)}','SEGMENTATION','provider-model','FAILED','{"error":"provider failed"}');`);
  const request=(await db.query('SELECT * FROM ai_requests')).rows[0];
  assert.equal(request.cost_tokens,null);assert.equal(request.monetary_cost,null);
  assert.equal(request.latency_ms,null);assert.equal(request.decision,'PENDING');
  await assert.rejects(db.exec('UPDATE ai_requests SET monetary_cost=0.01'));
  await assert.rejects(db.exec('UPDATE ai_requests SET input_tokens=-1'));
  await assert.rejects(db.exec(`UPDATE ai_requests SET script_version_id='${id(3)}',clip_id='${id(5)}'`));
  await assert.rejects(db.exec(sql(ai,true,shared)));await db.exec('ROLLBACK');
  await db.exec(`INSERT INTO publications(project_id,clip_id,publisher_id,platform)
    VALUES ('${id(2)}','${id(5)}','${id(1)}','INTERNAL_LIBRARY')`);
  await assert.rejects(db.exec(`INSERT INTO publications(project_id,clip_id,publisher_id,platform)
    VALUES ('${id(2)}','${id(6)}','${id(1)}','INTERNAL_LIBRARY')`));
  assert.equal((await db.query('SELECT count(*)::int n FROM publications')).rows[0].n,1);
  await db.close();
  console.log(`${shared?'shared native enums':'legacy varchar'}: AI/pub up/down/reapply/null usage/failure log/unique PASS`);
}
