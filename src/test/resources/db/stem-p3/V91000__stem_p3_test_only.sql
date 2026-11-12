-- TEST ONLY M-B. Never production reservation or production profile.
DO $$ DECLARE t text; k text; BEGIN
 FOREACH t IN ARRAY ARRAY['users','workspaces','script_versions','scenes','status_histories'] LOOP
  SELECT format_type(atttypid,atttypmod) INTO k FROM pg_attribute WHERE attrelid=t::regclass AND attname='id';
  IF k IS DISTINCT FROM 'character varying(36)' THEN RAISE EXCEPTION 'P3 fresh-test prerequisite drift: %.id type %',t,k; END IF;
 END LOOP;
END $$;
CREATE TABLE stem_catalog (
 id varchar(36) PRIMARY KEY, kind varchar(30) NOT NULL CHECK(kind IN ('TOPIC','TOPIC_VERSION','SOURCE_EDITION','OUTCOME','PROFILE','POLICY','RUBRIC')),
 logical_key varchar(128) NOT NULL,version_no integer NOT NULL CHECK(version_no>0),revision integer NOT NULL DEFAULT 0 CHECK(revision>=0),
 parent_id varchar(36) REFERENCES stem_catalog(id),source_id varchar(36) REFERENCES stem_catalog(id),
 status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK(status='DRAFT'),data jsonb NOT NULL CHECK(jsonb_typeof(data)='object'),UNIQUE(kind,logical_key,version_no)
);
CREATE TABLE stem_briefs (
 id varchar(36) PRIMARY KEY,version_id varchar(36) NOT NULL UNIQUE REFERENCES script_versions(id),revision integer NOT NULL DEFAULT 0 CHECK(revision>=0),
 status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK(status='DRAFT'),readiness varchar(30) NOT NULL DEFAULT 'INCOMPLETE',payload jsonb NOT NULL CHECK(jsonb_typeof(payload)='object'),
 policy_id varchar(36) REFERENCES stem_catalog(id),topic_id varchar(36) REFERENCES stem_catalog(id),copied_from varchar(36) REFERENCES stem_briefs(id)
);
CREATE TABLE stem_evidence (
 id varchar(36) PRIMARY KEY,brief_id varchar(36) NOT NULL REFERENCES stem_briefs(id),scene_id varchar(36) REFERENCES scenes(id) ON DELETE SET NULL,
 original_scene_id varchar(128),task_key varchar(128),objective_key varchar(128),component varchar(1) NOT NULL CHECK(component IN ('S','T','E','M')),
 retired boolean NOT NULL DEFAULT false,explanation text,copied_from varchar(36),status varchar(20) NOT NULL DEFAULT 'NEEDS_REVIEW' CHECK(status='NEEDS_REVIEW'),CHECK(original_scene_id IS NOT NULL OR task_key IS NOT NULL)
);
CREATE TABLE stem_settings (
 workspace_id varchar(36) PRIMARY KEY REFERENCES workspaces(id),revision integer NOT NULL CHECK(revision>0),mode varchar(12) NOT NULL CHECK(mode IN ('OFF','ADVISORY')),
 policy_ids jsonb NOT NULL DEFAULT '[]' CHECK(jsonb_typeof(policy_ids)='array'),default_policy_id varchar(36) REFERENCES stem_catalog(id),legacy_modes jsonb NOT NULL DEFAULT '[]'
);
CREATE TABLE stem_assessments (
 id varchar(36) PRIMARY KEY,version_id varchar(36) NOT NULL REFERENCES script_versions(id),brief_id varchar(36) NOT NULL REFERENCES stem_briefs(id),decision_id varchar(36) NOT NULL UNIQUE REFERENCES status_histories(id),
 actor_id varchar(36) NOT NULL REFERENCES users(id),phase varchar(10) NOT NULL CHECK(phase IN ('SCRIPT','VIDEO')),brief_revision integer NOT NULL,script_revision integer NOT NULL,mode_revision integer NOT NULL,composition_revision integer,
 payload jsonb NOT NULL,verification_status varchar(20) NOT NULL DEFAULT 'UNVERIFIED' CHECK(verification_status='UNVERIFIED'),freshness varchar(30) NOT NULL DEFAULT 'NEEDS_REVIEW'
);
CREATE TABLE stem_validation_runs (
 id varchar(36) PRIMARY KEY,brief_id varchar(36) NOT NULL REFERENCES stem_briefs(id),phase varchar(10) NOT NULL CHECK(phase IN ('SCRIPT','VIDEO')),
 brief_revision integer NOT NULL,script_revision integer NOT NULL,mode_revision integer NOT NULL,findings jsonb NOT NULL,created_at timestamp NOT NULL DEFAULT current_timestamp
);
CREATE TABLE stem_validation_limits (actor_id varchar(36) REFERENCES users(id),version_id varchar(36) REFERENCES script_versions(id),bucket bigint NOT NULL,requests integer NOT NULL,PRIMARY KEY(actor_id,version_id));
CREATE TABLE stem_receipts (actor_id varchar(36) REFERENCES users(id),operation varchar(30),target varchar(128) NOT NULL,request_key varchar(128),request_digest varchar(64) NOT NULL,response jsonb NOT NULL,expires_at timestamp NOT NULL,PRIMARY KEY(actor_id,operation,request_key));
CREATE TABLE stem_audit (id varchar(36) PRIMARY KEY,actor_id varchar(36) REFERENCES users(id),target varchar(128) NOT NULL,event varchar(50) NOT NULL,payload jsonb NOT NULL,created_at timestamp NOT NULL DEFAULT current_timestamp);
CREATE INDEX stem_evidence_brief_idx ON stem_evidence(brief_id);
CREATE INDEX stem_assessments_version_idx ON stem_assessments(version_id);
DO $$ DECLARE t text; r text; BEGIN
 FOREACH t IN ARRAY ARRAY['stem_catalog','stem_briefs','stem_evidence','stem_settings','stem_assessments','stem_validation_runs','stem_validation_limits','stem_receipts','stem_audit'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);EXECUTE format('REVOKE ALL ON TABLE %I FROM PUBLIC',t);
  FOREACH r IN ARRAY ARRAY['anon','authenticated'] LOOP
   IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=r) THEN EXECUTE format('REVOKE ALL ON TABLE %I FROM %I',t,r); END IF;
  END LOOP;
 END LOOP;
END $$;
