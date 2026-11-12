-- P5 TEST ONLY. Approved 2026-11-05. Never production reservation or migration.
DO $$ DECLARE t text; k text; BEGIN
 FOREACH t IN ARRAY ARRAY['users','workspaces','clip_projects','script_versions','status_histories','render_jobs'] LOOP
  SELECT format_type(atttypid,atttypmod) INTO k FROM pg_attribute WHERE attrelid=t::regclass AND attname='id' AND NOT attisdropped;
  IF k IS DISTINCT FROM 'character varying(36)' THEN RAISE EXCEPTION 'P5 prerequisite drift %.id: %',t,k; END IF;
 END LOOP;
 FOREACH t IN ARRAY ARRAY['compositions','clips','publications'] LOOP
  SELECT format_type(atttypid,atttypmod) INTO k FROM pg_attribute WHERE attrelid=t::regclass AND attname='id' AND NOT attisdropped;
  IF k IS DISTINCT FROM 'uuid' THEN RAISE EXCEPTION 'P5 prerequisite drift %.id: %',t,k; END IF;
 END LOOP;
 IF to_regclass('stem_briefs') IS NULL OR to_regclass('stem_assessments') IS NULL THEN RAISE EXCEPTION 'P5 needs P3 test schema'; END IF;
END $$;

CREATE TABLE stem_p5_snapshots (
 id varchar(36) PRIMARY KEY,
 stage varchar(20) NOT NULL CHECK(stage IN ('SUBMISSION','APPROVAL','RENDER_INPUT','CLIP','PUBLICATION')),
 project_id varchar(36) NOT NULL REFERENCES clip_projects(id),
 version_id varchar(36) NOT NULL REFERENCES script_versions(id),
 workspace_id varchar(36) NOT NULL REFERENCES workspaces(id),
 actor_id varchar(36) REFERENCES users(id),
 composition_id uuid REFERENCES compositions(id), composition_revision integer CHECK(composition_revision>=1),
 job_id varchar(36) REFERENCES render_jobs(id), clip_id uuid REFERENCES clips(id), publication_id uuid REFERENCES publications(id),
 decision_id varchar(36) REFERENCES status_histories(id), assessment_id varchar(36) REFERENCES stem_assessments(id),
 parent_id varchar(36) REFERENCES stem_p5_snapshots(id), approval_id varchar(36) REFERENCES stem_p5_snapshots(id),
 semantic_hash varchar(64) NOT NULL CHECK(semantic_hash ~ '^[0-9a-f]{64}$'),
 contract_hash varchar(64) NOT NULL CHECK(contract_hash ~ '^[0-9a-f]{64}$'),
 input_hash varchar(64) CHECK(input_hash ~ '^[0-9a-f]{64}$'),
 classification varchar(12) NOT NULL CHECK(classification IN ('CLASSIFIED','UNKNOWN')),
 assurance varchar(24) NOT NULL CHECK(assurance IN ('UNKNOWN','BINDING_ONLY','TEST_ONLY_SYNTHETIC')),
 payload jsonb NOT NULL CHECK(jsonb_typeof(payload)='object' AND octet_length(payload::text)<=16777216),
 frozen_input jsonb CHECK(jsonb_typeof(frozen_input)='object' AND octet_length(frozen_input::text)<=8388608),
 dedupe_key varchar(64) NOT NULL UNIQUE CHECK(dedupe_key ~ '^[0-9a-f]{64}$'),
 created_at timestamp NOT NULL DEFAULT current_timestamp,
 CHECK(stage<>'APPROVAL' OR decision_id IS NOT NULL),
 CHECK(stage<>'RENDER_INPUT' OR (job_id IS NOT NULL AND frozen_input IS NOT NULL AND input_hash IS NOT NULL)),
 CHECK(stage<>'CLIP' OR (clip_id IS NOT NULL AND job_id IS NOT NULL)),
 CHECK(stage<>'PUBLICATION' OR (clip_id IS NOT NULL AND publication_id IS NOT NULL))
);
CREATE UNIQUE INDEX stem_p5_job_unique ON stem_p5_snapshots(job_id) WHERE stage='RENDER_INPUT';
CREATE UNIQUE INDEX stem_p5_clip_unique ON stem_p5_snapshots(clip_id) WHERE stage='CLIP';
CREATE UNIQUE INDEX stem_p5_publication_unique ON stem_p5_snapshots(publication_id) WHERE stage='PUBLICATION';
CREATE UNIQUE INDEX stem_p5_decision_unique ON stem_p5_snapshots(decision_id) WHERE stage='APPROVAL';
CREATE INDEX stem_p5_target_idx ON stem_p5_snapshots(project_id,version_id,stage);

CREATE TABLE stem_p5_compatibility (
 id varchar(36) PRIMARY KEY,
 approval_id varchar(36) NOT NULL REFERENCES stem_p5_snapshots(id),
 before_id varchar(36) NOT NULL REFERENCES stem_p5_snapshots(id),
 after_id varchar(36) NOT NULL REFERENCES stem_p5_snapshots(id),
 proof_version varchar(128) NOT NULL,
 result varchar(16) NOT NULL CHECK(result IN ('COMPATIBLE','INCOMPATIBLE','UNKNOWN')),
 proof jsonb NOT NULL CHECK(jsonb_typeof(proof)='object' AND octet_length(proof::text)<=65536),
 created_at timestamp NOT NULL DEFAULT current_timestamp,
 UNIQUE(approval_id,before_id,after_id,proof_version)
);
CREATE FUNCTION stem_p5_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'P5 snapshot/proof is append-only' USING ERRCODE='23514'; END $$;
CREATE TRIGGER stem_p5_snapshots_immutable BEFORE UPDATE OR DELETE ON stem_p5_snapshots FOR EACH ROW EXECUTE FUNCTION stem_p5_append_only();
CREATE TRIGGER stem_p5_proofs_immutable BEFORE UPDATE OR DELETE ON stem_p5_compatibility FOR EACH ROW EXECUTE FUNCTION stem_p5_append_only();
DO $$ DECLARE t text; r text; BEGIN
 FOREACH t IN ARRAY ARRAY['stem_p5_snapshots','stem_p5_compatibility'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('REVOKE ALL ON TABLE %I FROM PUBLIC',t);
  FOREACH r IN ARRAY ARRAY['anon','authenticated'] LOOP
   IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=r) THEN EXECUTE format('REVOKE ALL ON TABLE %I FROM %I',t,r); END IF;
  END LOOP;
 END LOOP;
END $$;
