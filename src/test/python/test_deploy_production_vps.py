import importlib.util
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[3] / "tools" / "deploy_production_vps.py"
SPEC = importlib.util.spec_from_file_location("deploy_production_vps", MODULE_PATH)
DEPLOY = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(DEPLOY)


class DeployScriptTest(unittest.TestCase):
    def test_remote_job_is_detached_locked_and_commit_pinned(self):
        script = DEPLOY.remote_deploy_script(
            "/opt/chinese-chess",
            "main",
            "a" * 40,
            "http://127.0.0.1:18388/online",
            "https://www.xiangqiarena.com/online",
            "/tmp/test.status",
        )
        self.assertIn("flock -n", script)
        self.assertIn("git reset --hard " + "a" * 40, script)
        self.assertIn("docker compose build app", script)
        self.assertIn("docker compose up -d --no-deps app", script)
        self.assertIn("State.Health", script)
        self.assertIn('"$SOURCE_URL/api/site/bootstrap"', script)
        self.assertNotIn("\x01", script)
        self.assertIn("STAGE=public", script)
        self.assertIn("source_sha", script)
        self.assertIn("public_sha", script)
        self.assertIn("docker image tag", script)

    def test_paths_and_release_ids_are_safe(self):
        self.assertEqual("abc-123", DEPLOY.safe_release_id("abc-123"))
        self.assertTrue(DEPLOY.remote_paths("abc")[2].endswith(".status"))
        with self.assertRaises(ValueError):
            DEPLOY.safe_release_id("")


if __name__ == "__main__":
    unittest.main()
