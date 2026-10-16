"""Real rendered Compose config is checked by smoke.py; these are rejection regressions."""
import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("preflight", Path(__file__).resolve().parents[1] / "preflight.py")
module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)


class PreflightTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = Path(self.tmp.name)
        (root / "Dockerfile").write_text("FROM scratch")
        (root / "package.json").write_text("{}")
        key = "a" * 64
        self.config = {"networks": {"private": {}}, "services": {
            "ai-service": {"environment": {"AI_SERVICE_SHARED_KEY": key, "WHISPER_STARTUP_MODE": "optional"}},
            "worker": {"environment": {"RENDER_SERVICE_KEY": key, "REDIS_PASSWORD": key,
                "RENDER_CONCURRENCY": "1", "RENDER_FRAME_CONCURRENCY": "1", "USE_TTS": "false", "API_CALLBACK_URL": "https://be.example.com/api/v1/render/callback"},
                "build": {"context": str(root), "additional_contexts": {"fe": str(root)}}},
            "redis": {"environment": {"REDIS_PASSWORD": key}},
            "caddy": {"environment": {"DEPLOY_MODE": "test"}, "ports": [{"published": "80"}, {"published": "443"}]}}}

    def test_accepts_incomplete_health_only_test_mode(self):
        module.check(self.config)

    def test_rejects_public_redis_and_unsafe_mode(self):
        bad = copy.deepcopy(self.config); bad["services"]["redis"]["ports"] = [{"published": "6379"}]
        with self.assertRaisesRegex(AssertionError, "must not publish"): module.check(bad)
        self.config["services"]["caddy"]["environment"]["DEPLOY_MODE"] = "../../arbitrary"
        with self.assertRaisesRegex(AssertionError, "test or prod"): module.check(self.config)

    def test_rejects_template_keys_and_password_config_injection(self):
        self.config["services"]["redis"]["environment"]["REDIS_PASSWORD"] = "unsafe\nconfig"
        with self.assertRaisesRegex(AssertionError, "REDIS_PASSWORD"): module.check(self.config)

    def test_prod_cannot_use_smoke_configuration(self):
        self.config["services"]["caddy"]["environment"]["DEPLOY_MODE"] = "prod"
        with self.assertRaisesRegex(AssertionError, "OpenAI key"): module.check(self.config)
        self.config["services"]["ai-service"]["environment"]["OPENAI_API_KEY"] = "placeholder-not-used"
        with self.assertRaisesRegex(AssertionError, "fail-fast weights"): module.check(self.config)


if __name__ == "__main__": unittest.main()
