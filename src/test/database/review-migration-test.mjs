import {PGlite} from '@electric-sql/pglite';
import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
const up=readFileSync('../../main/resources/db/migration/V11__explicit_review_decisions.sql','utf8');
const down=readFileSync('../../../docs/rollback/V11__explicit_review_decisions.down.sql','utf8');
const id=n=>`00000000-0000-0000-0000-${String(n).padStart(12,'0')}`;
for(const type of ['uuid','varchar(36)']) {
  const db=new PGlite();
  await db.exec(`CREATE TABLE clip_projects(id ${type} PRIMARY KEY);
    INSERT INTO clip_projects VALUES ('${id(1)}'),('${id(2)}');
    CREATE TABLE script_versions(id ${type} PRIMARY KEY,project_id ${type} REFERENCES clip_projects(id));
    INSERT INTO script_versions VALUES ('${id(3)}','${id(1)}'),('${id(4)}','${id(2)}');
    CREATE TABLE render_jobs(id ${type} PRIMARY KEY,project_id ${type} REFERENCES clip_projects(id));
    INSERT INTO render_jobs VALUES ('${id(5)}','${id(1)}'),('${id(6)}','${id(2)}');
    CREATE TABLE clips(id uuid PRIMARY KEY,render_job_id ${type} REFERENCES render_jobs(id));
    INSERT INTO clips VALUES ('${id(7)}','${id(5)}'),('${id(8)}','${id(6)}');
    CREATE TABLE status_histories(id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
      project_id ${type} REFERENCES clip_projects(id) ON DELETE CASCADE,
      from_status text,to_status text,note text,created_at timestamp DEFAULT now());
    INSERT INTO status_histories(project_id,from_status,to_status,note) VALUES
      ('${id(1)}','DRAFT','SCRIPT_REVIEW','legacy note');`);
  const before=(await db.query('SELECT * FROM status_histories')).rows;
  await db.exec(up);
  await db.exec(`INSERT INTO status_histories(project_id,from_status,to_status,note)
    VALUES ('${id(1)}','DRAFT','SCRIPT_REVIEW','old BE rollout compatibility')`);
  assert.equal((await db.query('SELECT target_id FROM status_histories')).rows[0].target_id,id(1));
  await db.exec(down);
  assert.equal((await db.query('SELECT * FROM status_histories')).rows.length,2);
  assert.deepEqual((await db.query('SELECT * FROM status_histories ORDER BY note')).rows.find(r=>r.note==='legacy note'),before[0]);
  await db.exec(up);
  const review=(target,targetId,decision='APPROVED')=>`INSERT INTO status_histories
    (project_id,from_status,to_status,event_type,target_type,target_id,decision,reason) VALUES
    ('${id(1)}','CLIP_REVIEW','CLIP_REVIEW','REVIEW_DECISION','${target}','${targetId}',${decision===null?'NULL':`'${decision}'`},'explicit reason')`;
  await db.exec(review('CLIP',id(7)));
  await db.exec(review('SCRIPT',id(3),'REJECTED'));
  await assert.rejects(db.exec(review('CLIP',id(8))));
  await assert.rejects(db.exec(review('SCRIPT',id(4))));
  await assert.rejects(db.exec(review('CLIP',id(7),null)));
  await assert.rejects(db.exec('UPDATE status_histories SET note = \'overwrite\''));
  await assert.rejects(db.exec('DELETE FROM status_histories'));
  await assert.rejects(db.exec('TRUNCATE status_histories'));
  await assert.rejects(db.exec(`DELETE FROM clip_projects WHERE id='${id(1)}'`));
  await assert.rejects(db.exec(`DELETE FROM clips WHERE id='${id(7)}'`));
  await assert.rejects(db.exec(`DELETE FROM script_versions WHERE id='${id(3)}'`));
  await assert.rejects(db.exec(down));
  await db.exec('ROLLBACK');
  assert.equal((await db.query('SELECT count(*)::int n FROM status_histories')).rows[0].n,4);
  await db.close();
  console.log(`${type}: review binding/same-state event/append-only/guarded rollback PASS`);
}
