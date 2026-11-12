#!/usr/bin/env python3
"""
STEM API Smoke Test Script
Executes full STEM demonstration flow against any target BASE_URL.
Standard library only (no external dependencies).
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from typing import Any, Dict, List, Optional, Tuple

POLICY_MAP = {
    "PRIMARY": "00000000-0000-0000-0000-000000000005",
    "LOWER_SECONDARY": "00000000-0000-0000-0000-000000000010",
    "UPPER_SECONDARY": "00000000-0000-0000-0000-000000000017",
}

RUBRIC_MAP = {
    "PRIMARY": "00000000-0000-0000-0000-000000000004",
    "LOWER_SECONDARY": "00000000-0000-0000-0000-000000000009",
    "UPPER_SECONDARY": "00000000-0000-0000-0000-000000000016",
}

GRADE_MAP = {
    "PRIMARY": "GRADE_4",
    "LOWER_SECONDARY": "GRADE_8",
    "UPPER_SECONDARY": "GRADE_11",
}

SUBJECT_MAP = {
    "PRIMARY": "Math",
    "LOWER_SECONDARY": "Physics",
    "UPPER_SECONDARY": "Physics",
}


def http_request(
    url: str,
    method: str = "GET",
    data: Optional[Dict[str, Any]] = None,
    token: Optional[str] = None,
) -> Tuple[int, Dict[str, Any]]:
    body_bytes = None
    headers = {"Accept": "application/json"}
    if data is not None:
        body_bytes = json.dumps(data).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"

    req = urllib.request.Request(url, data=body_bytes, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            status = resp.status
            content = resp.read().decode("utf-8")
            res_json = json.loads(content) if content else {}
            return status, res_json
    except urllib.error.HTTPError as e:
        status = e.code
        content = e.read().decode("utf-8")
        try:
            res_json = json.loads(content) if content else {}
        except Exception:
            res_json = {"error": content}
        return status, res_json


class SmokeTester:
    def __init__(self, base_url: str, workspace_id: str, level: str):
        self.base_url = base_url.rstrip("/")
        self.workspace_id = workspace_id
        self.level = level
        self.results: List[Tuple[str, str, str]] = []  # (Step, Status, Note)
        self.tokens: Dict[str, str] = {}

    def log_result(self, step: str, passed: bool, note: str = ""):
        status = "PASS" if passed else "FAIL"
        self.results.append((step, status, note))
        print(f"[{status}] {step}: {note}")

    def run(self) -> bool:
        print(f"Starting STEM Smoke Test against {self.base_url} (Level: {self.level})")

        # Step 1: Login users
        if not self.step1_login_users():
            return False

        # Step 2: Import catalog demo pack & switch mode to STRICT
        if not self.step2_import_catalog_and_configure_strict():
            return False

        # Step 3: Writer creates project and submits valid brief
        project_id, version_id = self.step3_writer_create_and_submit_valid()
        if not project_id:
            return False

        # Step 4: Reviewer approves script with rubric & matching fingerprint
        if not self.step4_reviewer_approve(project_id, version_id):
            return False

        # Step 5: Verify error conditions (invalid brief blocked, stale fingerprint blocked)
        if not self.step5_verify_error_conditions():
            return False

        return True

    def step1_login_users(self) -> bool:
        roles = ["ADMIN", "WRITER", "REVIEWER", "PRODUCER"]
        all_ok = True

        for role in roles:
            user_env = f"STEM_SMOKE_{role}_USER"
            pass_env = f"STEM_SMOKE_{role}_PASS"
            username = os.getenv(user_env)
            password = os.getenv(pass_env)

            if not username or not password:
                self.log_result(f"Login {role}", False, f"Missing {user_env} or {pass_env} env var")
                all_ok = False
                continue

            status, resp = http_request(
                f"{self.base_url}/api/v1/auth/login",
                method="POST",
                data={"username": username, "password": password},
            )

            if status == 200 and "accessToken" in resp:
                self.tokens[role] = resp["accessToken"]
                self.log_result(f"Login {role}", True, f"User {username} authenticated")
            else:
                self.log_result(f"Login {role}", False, f"Status {status}: {resp.get('message', 'Login failed')}")
                all_ok = False

        return all_ok

    def step2_import_catalog_and_configure_strict(self) -> bool:
        admin_token = self.tokens.get("ADMIN")
        if not admin_token:
            self.log_result("Catalog Import", False, "No admin token")
            return False

        # Import catalog demo pack
        status, resp = http_request(
            f"{self.base_url}/api/v1/stem/catalog/import",
            method="POST",
            data={
                "pack": "stem-demo-pack.v1",
                "idempotencyKey": f"smoke-import-{int(time.time())}",
            },
            token=admin_token,
        )

        if status == 200:
            created = resp.get("created", 0)
            skipped = resp.get("skipped", 0)
            self.log_result("Catalog Import", True, f"Pack imported (created={created}, skipped={skipped})")
        else:
            self.log_result("Catalog Import", False, f"Status {status}: {resp}")
            return False

        # Get existing workspace settings to read baseRevision
        status, settings_resp = http_request(
            f"{self.base_url}/api/v1/workspaces/{self.workspace_id}/stem-settings",
            method="GET",
            token=admin_token,
        )

        base_rev = settings_resp.get("revision", 0) if status == 200 else 0
        policy_id = POLICY_MAP[self.level]

        # Configure workspace to STRICT
        status, resp = http_request(
            f"{self.base_url}/api/v1/workspaces/{self.workspace_id}/stem-settings",
            method="PUT",
            data={
                "baseRevision": base_rev,
                "mode": "STRICT",
                "policyVersionIds": [policy_id],
                "defaultPolicyVersionId": policy_id,
                "idempotencyKey": f"smoke-settings-{int(time.time())}",
            },
            token=admin_token,
        )

        if status == 200 and resp.get("mode") == "STRICT":
            self.log_result("Set STRICT Mode", True, f"Workspace set to STRICT with policy {policy_id}")
            return True
        else:
            self.log_result("Set STRICT Mode", False, f"Status {status}: {resp}")
            return False

    def step3_writer_create_and_submit_valid(self) -> Tuple[Optional[str], Optional[str]]:
        writer_token = self.tokens.get("WRITER")
        if not writer_token:
            self.log_result("Create Project", False, "No writer token")
            return None, None

        # 1. Create project with initial scene
        grade = GRADE_MAP[self.level]
        subj = SUBJECT_MAP[self.level]

        status, resp = http_request(
            f"{self.base_url}/api/v1/workspaces/{self.workspace_id}/scripts",
            method="POST",
            data={
                "title": f"STEM Smoke Demo {self.level} {int(time.time())}",
                "subject": subj,
                "gradeLevel": grade,
                "fps": 30,
                "scenes": [
                    {
                        "orderNo": 1,
                        "type": "TITLE_HERO",
                        "title": "Scene 1 Opening",
                        "narration": "Welcome to our STEM lesson",
                        "durationInFrames": 90,
                        "subtitle": "Demo Subtitle",
                        "subject": subj,
                        "gradeLevel": grade,
                        "badgeText": "STEM",
                    }
                ],
            },
            token=writer_token,
        )

        if status != 201 and status != 200:
            self.log_result("Create Project", False, f"Status {status}: {resp}")
            return None, None

        project_id = resp.get("id")
        self.log_result("Create Project", True, f"Project {project_id} created")

        # Get current version ID
        scenes = resp.get("scenes", [])
        scene_id = scenes[0].get("id") if scenes else "1"

        status, v_resp = http_request(
            f"{self.base_url}/api/v1/scripts/{project_id}/versions",
            method="GET",
            token=writer_token,
        )
        if status != 200 or not isinstance(v_resp, list) or len(v_resp) == 0:
            self.log_result("Fetch Script Version", False, f"Status {status}: {v_resp}")
            return None, None
        version_id = v_resp[0].get("id")

        # 2. Create stem brief
        status, resp = http_request(
            f"{self.base_url}/api/v1/scripts/{project_id}/versions/{version_id}/stem",
            method="POST",
            data={
                "schemaVersion": "stem.v1",
                "expectedScriptRevision": 0,
                "idempotencyKey": f"smoke-stem-create-{int(time.time())}",
            },
            token=writer_token,
        )

        if status != 200:
            self.log_result("Init STEM Brief", False, f"Status {status}: {resp}")
            return None, None
        self.log_result("Init STEM Brief", True, "STEM brief initialized")

        # 3. Save valid 6-step brief
        brief_data = self.get_valid_brief(scene_id)
        status, resp = http_request(
            f"{self.base_url}/api/v1/scripts/{project_id}/versions/{version_id}/stem",
            method="PUT",
            data={
                "schemaVersion": "stem.v1",
                "baseRevision": 0,
                "expectedScriptRevision": 0,
                "idempotencyKey": f"smoke-stem-save-{int(time.time())}",
                "brief": brief_data,
                "evidence": [],
            },
            token=writer_token,
        )

        if status != 200:
            self.log_result("Save Valid Brief", False, f"Status {status}: {resp}")
            return None, None
        self.log_result("Save Valid Brief", True, f"Valid 6-step brief saved for {self.level}")

        # 4. Validate
        status, resp = http_request(
            f"{self.base_url}/api/v1/scripts/{project_id}/versions/{version_id}/stem/validate",
            method="POST",
            data={
                "expectedBriefRevision": 1,
                "expectedScriptRevision": 0,
                "phase": "SCRIPT",
            },
            token=writer_token,
        )
        if status != 200:
            self.log_result("Validate Brief", False, f"Status {status}: {resp}")
            return None, None
        self.log_result("Validate Brief", True, "Brief validation passed")

        # 5. Submit for review
        status, resp = http_request(
            f"{self.base_url}/api/v1/scripts/{project_id}/submit-review",
            method="POST",
            data={},
            token=writer_token,
        )

        if status == 200:
            self.log_result("Submit For Review", True, "Script transitioned to IN_REVIEW in STRICT mode")
            return project_id, version_id
        else:
            self.log_result("Submit For Review", False, f"Status {status}: {resp}")
            return None, None

    def step4_reviewer_approve(self, project_id: str, version_id: str) -> bool:
        reviewer_token = self.tokens.get("REVIEWER")
        if not reviewer_token:
            self.log_result("Approve Script", False, "No reviewer token")
            return False

        # Reviewer fetches brief to get scriptFingerprint
        status, resp = http_request(
            f"{self.base_url}/api/v1/scripts/{project_id}/versions/{version_id}/stem",
            method="GET",
            token=reviewer_token,
        )

        if status != 200:
            self.log_result("Fetch Brief Fingerprint", False, f"Status {status}: {resp}")
            return False

        script_fingerprint = resp.get("scriptFingerprint")
        if not script_fingerprint:
            self.log_result("Fetch Brief Fingerprint", False, "No scriptFingerprint returned in GET brief")
            return False
        self.log_result("Fetch Brief Fingerprint", True, f"Fingerprint: {script_fingerprint[:16]}...")

        # Reviewer approves script with rubric assessment
        rubric_id = RUBRIC_MAP[self.level]
        status, resp = http_request(
            f"{self.base_url}/api/v1/scripts/{project_id}/approve",
            method="POST",
            data={
                "note": "Approved via smoke-stem demo script",
                "stemAssessment": {
                    "expectedBriefRevision": 1,
                    "expectedScriptRevision": 0,
                    "expectedCompositionRevision": None,
                    "expectedModeRevision": 1,
                    "rubricVersionId": rubric_id,
                    "criteria": [
                        {
                            "key": "CRIT-STEM-01",
                            "judgment": "ADEQUATE",
                            "rationale": "Clear evidence in script",
                            "evidenceIds": [],
                        }
                    ],
                    "idempotencyKey": f"smoke-approve-{int(time.time())}",
                    "expectedFingerprint": script_fingerprint,
                },
            },
            token=reviewer_token,
        )

        if status == 200:
            self.log_result("Approve Script", True, "Script successfully approved with matching fingerprint")
            return True
        else:
            self.log_result("Approve Script", False, f"Status {status}: {resp}")
            return False

    def step5_verify_error_conditions(self) -> bool:
        writer_token = self.tokens.get("WRITER")
        reviewer_token = self.tokens.get("REVIEWER")
        if not writer_token or not reviewer_token:
            self.log_result("Verify Errors", False, "Missing tokens")
            return False

        # Error Test 1: Invalid brief blocked on submit
        # Create a new draft project
        status, resp = http_request(
            f"{self.base_url}/api/v1/workspaces/{self.workspace_id}/scripts",
            method="POST",
            data={
                "title": f"STEM Error Test {int(time.time())}",
                "subject": SUBJECT_MAP[self.level],
                "gradeLevel": GRADE_MAP[self.level],
                "fps": 30,
                "scenes": [{
                    "orderNo": 1,
                    "type": "TITLE_HERO",
                    "title": "S1",
                    "narration": "Text",
                    "durationInFrames": 90,
                    "subtitle": "Demo",
                    "subject": SUBJECT_MAP[self.level],
                    "gradeLevel": GRADE_MAP[self.level],
                    "badgeText": "STEM",
                }],
            },
            token=writer_token,
        )
        if status not in (200, 201):
            self.log_result("Error Test: Invalid Brief", False, "Failed to create error test project")
            return False

        err_project = resp.get("id")
        status, v_resp = http_request(f"{self.base_url}/api/v1/scripts/{err_project}/versions", method="GET", token=writer_token)
        err_version = v_resp[0].get("id") if status == 200 and isinstance(v_resp, list) and len(v_resp) > 0 else err_project

        http_request(
            f"{self.base_url}/api/v1/scripts/{err_project}/versions/{err_version}/stem",
            method="POST",
            data={"schemaVersion": "stem.v1", "expectedScriptRevision": 0, "idempotencyKey": f"err-stem-init-{int(time.time())}"},
            token=writer_token,
        )

        # Save invalid brief (missing OPENING_PROBLEM)
        invalid_brief = self.get_valid_brief("1")
        invalid_brief["steps"] = [s for s in invalid_brief["steps"] if s["stepKey"] != "OPENING_PROBLEM"]

        http_request(
            f"{self.base_url}/api/v1/scripts/{err_project}/versions/{err_version}/stem",
            method="PUT",
            data={
                "schemaVersion": "stem.v1",
                "baseRevision": 0,
                "expectedScriptRevision": 0,
                "idempotencyKey": f"err-save-{int(time.time())}",
                "brief": invalid_brief,
                "evidence": [],
            },
            token=writer_token,
        )

        # Try to submit for review -> must fail with 400
        status, resp = http_request(
            f"{self.base_url}/api/v1/scripts/{err_project}/submit-review",
            method="POST",
            data={},
            token=writer_token,
        )

        if status == 400 and resp.get("code") == "STEM_VALIDATION_FAILED":
            self.log_result("Error Test: Invalid Brief Blocked", True, "Submit correctly rejected (400 STEM_VALIDATION_FAILED)")
        else:
            self.log_result("Error Test: Invalid Brief Blocked", False, f"Expected 400, got status {status}: {resp}")
            return False

        # Error Test 2: Stale fingerprint rejected on approval -> 409 STEM_FINGERPRINT_STALE
        # Create a valid brief and submit it
        status, resp = http_request(
            f"{self.base_url}/api/v1/workspaces/{self.workspace_id}/scripts",
            method="POST",
            data={
                "title": f"STEM Stale FP Test {int(time.time())}",
                "subject": SUBJECT_MAP[self.level],
                "gradeLevel": GRADE_MAP[self.level],
                "fps": 30,
                "scenes": [{
                    "orderNo": 1,
                    "type": "TITLE_HERO",
                    "title": "S1",
                    "narration": "Text",
                    "durationInFrames": 90,
                    "subtitle": "Demo",
                    "subject": SUBJECT_MAP[self.level],
                    "gradeLevel": GRADE_MAP[self.level],
                    "badgeText": "STEM",
                }],
            },
            token=writer_token,
        )
        fp_project = resp.get("id")
        status, v_resp = http_request(f"{self.base_url}/api/v1/scripts/{fp_project}/versions", method="GET", token=writer_token)
        fp_version = v_resp[0].get("id") if status == 200 and isinstance(v_resp, list) and len(v_resp) > 0 else fp_project

        http_request(
            f"{self.base_url}/api/v1/scripts/{fp_project}/versions/{fp_version}/stem",
            method="POST",
            data={"schemaVersion": "stem.v1", "expectedScriptRevision": 0, "idempotencyKey": f"fp-stem-init-{int(time.time())}"},
            token=writer_token,
        )
        http_request(
            f"{self.base_url}/api/v1/scripts/{fp_project}/versions/{fp_version}/stem",
            method="PUT",
            data={
                "schemaVersion": "stem.v1",
                "baseRevision": 0,
                "expectedScriptRevision": 0,
                "idempotencyKey": f"fp-save-{int(time.time())}",
                "brief": self.get_valid_brief("1"),
                "evidence": [],
            },
            token=writer_token,
        )
        http_request(
            f"{self.base_url}/api/v1/scripts/{fp_project}/submit-review",
            method="POST",
            data={},
            token=writer_token,
        )

        # Approve with mismatched stale fingerprint -> must return 409
        status, resp = http_request(
            f"{self.base_url}/api/v1/scripts/{fp_project}/approve",
            method="POST",
            data={
                "note": "Approval with fake stale fingerprint",
                "stemAssessment": {
                    "expectedBriefRevision": 1,
                    "expectedScriptRevision": 0,
                    "expectedCompositionRevision": None,
                    "expectedModeRevision": 1,
                    "rubricVersionId": RUBRIC_MAP[self.level],
                    "criteria": [{"key": "CRIT-STEM-01", "judgment": "ADEQUATE", "rationale": "OK", "evidenceIds": []}],
                    "idempotencyKey": f"stale-approve-{int(time.time())}",
                    "expectedFingerprint": "stale-fingerprint-mismatch-12345",
                },
            },
            token=reviewer_token,
        )

        if status == 409 and resp.get("code") == "STEM_FINGERPRINT_STALE":
            self.log_result("Error Test: Stale Fingerprint Blocked", True, "Approval correctly rejected (409 STEM_FINGERPRINT_STALE)")
            return True
        else:
            self.log_result("Error Test: Stale Fingerprint Blocked", False, f"Expected 409, got status {status}: {resp}")
            return False

    def get_valid_brief(self, scene_id: str) -> Dict[str, Any]:
        policy_id = POLICY_MAP[self.level]
        grade = GRADE_MAP[self.level]
        subj = SUBJECT_MAP[self.level]

        outcome_map = {
            "PRIMARY": ["00000000-0000-0000-0000-000000000003", "00000000-0000-0000-0000-000000000013"],
            "LOWER_SECONDARY": ["00000000-0000-0000-0000-000000000008", "00000000-0000-0000-0000-000000000018"],
            "UPPER_SECONDARY": ["00000000-0000-0000-0000-000000000015", "00000000-0000-0000-0000-000000000025"],
        }
        outcomes = outcome_map[self.level]
        subjects = ["Math", "Science"] if self.level == "PRIMARY" else ["Physics", "Math"]

        return {
            "policyVersionId": policy_id,
            "topicVersionId": None,
            "gradeKey": grade,
            "subjectKeys": subjects,
            "outcomeIds": outcomes,
            "scopeOfClip": f"STEM Smoke Demo Video for {self.level}",
            "context": {
                "primary": {"key": "IN_CLASS_SCREEN", "condition": "Screen available", "adaptations": "Pause for discussion"},
                "secondary": [],
            },
            "distributionChannels": ["DEPARTMENT_LIBRARY"],
            "objectives": [
                {
                    "key": "obj-1",
                    "outcomeId": outcomes[0],
                    "learnerAction": "Conduct observation and measurement",
                    "observableEvidence": "Completed measurement sheet",
                }
            ],
            "studentTasks": [
                {
                    "key": "task-1",
                    "learnerAction": "Assemble model and record metrics",
                    "teacherSupport": "Supervise safety protocols",
                    "materials": "Sensors and worksheets",
                    "safety": "Standard electrical and thermal safety guidelines",
                    "assessmentEvidence": "Group report",
                    "executionContextKey": "IN_CLASS_SCREEN",
                }
            ],
            "contributions": [
                {
                    "component": "S",
                    "explanation": "Scientific inquiry into energy",
                    "objectiveKeys": ["obj-1"],
                    "taskKeys": ["task-1"],
                }
            ],
            "safetyNotes": "Follow lab safety protocols. Teacher supervision required.",
            "rawLegacy": None,
            "steps": [
                {"stepKey": "OPENING_PROBLEM", "activityKey": "act-open"},
                {"stepKey": "BACKGROUND_KNOWLEDGE", "activityKey": "act-bg"},
                {"stepKey": "PROPOSE_SOLUTION", "activityKey": "act-prop"},
                {"stepKey": "SELECT_SOLUTION", "activityKey": "act-sel"},
                {"stepKey": "BUILD_TEST", "activityKey": "act-build"},
                {"stepKey": "CLOSING_SHARE_REFLECT", "activityKey": "act-close"},
            ],
        }

    def print_summary(self) -> int:
        print("\n" + "=" * 70)
        print(f"STEM SMOKE TEST SUMMARY (Target: {self.base_url}, Level: {self.level})")
        print("=" * 70)
        print(f"{'STEP':<40} | {'STATUS':<6} | {'NOTE'}")
        print("-" * 70)
        failed_count = 0
        for step, status, note in self.results:
            print(f"{step:<40} | {status:<6} | {note}")
            if status != "PASS":
                failed_count += 1
        print("=" * 70)

        if failed_count == 0:
            print("OVERALL RESULT: ALL PASS\n")
            return 0
        else:
            print(f"OVERALL RESULT: {failed_count} STEPS FAILED\n")
            return 1


def main():
    parser = argparse.ArgumentParser(description="STEM API Smoke Test")
    parser.add_argument("--base-url", required=True, help="Base URL of backend (e.g. http://127.0.0.1:18080)")
    parser.add_argument("--workspace", required=True, help="Workspace ID to test with")
    parser.add_argument(
        "--level",
        default="PRIMARY",
        choices=["PRIMARY", "LOWER_SECONDARY", "UPPER_SECONDARY"],
        help="Educational level to smoke test (default: PRIMARY)",
    )
    parser.add_argument(
        "--i-understand-this-writes-demo-data",
        action="store_true",
        help="Confirmation flag indicating demo data will be written",
    )

    args = parser.parse_args()

    if not getattr(args, "i_understand_this_writes_demo_data", False):
        print("ERROR: Must specify --i-understand-this-writes-demo-data to run this smoke test script.")
        sys.exit(2)

    tester = SmokeTester(args.base_url, args.workspace, args.level)
    success = tester.run()
    exit_code = tester.print_summary()
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
