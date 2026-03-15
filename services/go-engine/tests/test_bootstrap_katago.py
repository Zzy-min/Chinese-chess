import pathlib
import sys
import tempfile
import unittest
import zipfile


ROOT = pathlib.Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import bootstrap_katago  # noqa: E402


class InstallPlanTest(unittest.TestCase):
    def test_linux_defaults_use_official_urls_and_local_paths(self):
        base_dir = pathlib.Path("/tmp/go-engine")

        plan = bootstrap_katago.default_install_plan(base_dir, system_name="Linux")

        self.assertEqual(
            "https://github.com/lightvector/KataGo/releases/download/v1.16.4/katago-v1.16.4-eigen-linux-x64.zip",
            plan.engine_url,
        )
        self.assertEqual(
            "https://media.katagotraining.org/uploaded/networks/models/kata1/kata1-b18c384nbt-s9996604416-d4316597426.bin.gz",
            plan.model_url,
        )
        self.assertEqual(base_dir / ".katago" / "katago", plan.binary_path)
        self.assertEqual(base_dir / ".katago" / "default_gtp.cfg", plan.config_path)
        self.assertEqual(
            base_dir / ".katago" / "kata1-b18c384nbt-s9996604416-d4316597426.bin.gz",
            plan.model_path,
        )


class EnsureAssetsTest(unittest.TestCase):
    def test_downloads_and_extracts_missing_assets(self):
        with tempfile.TemporaryDirectory() as tmp:
            base_dir = pathlib.Path(tmp)
            plan = bootstrap_katago.default_install_plan(base_dir, system_name="Linux")

            def fake_download(url: str, destination: pathlib.Path) -> None:
                if url == plan.engine_url:
                    with zipfile.ZipFile(destination, "w") as archive:
                        archive.writestr(
                            "katago-v1.16.4-eigen-linux-x64/katago",
                            "#!/bin/sh\necho ok\n",
                        )
                        archive.writestr(
                            "katago-v1.16.4-eigen-linux-x64/default_gtp.cfg",
                            "rules = chinese\n",
                        )
                    return
                if url == plan.model_url:
                    destination.write_bytes(b"model-data")
                    return
                raise AssertionError(f"unexpected download url: {url}")

            bootstrap_katago.ensure_assets(plan, downloader=fake_download)

            self.assertTrue(plan.binary_path.exists())
            self.assertTrue(plan.config_path.exists())
            self.assertTrue(plan.model_path.exists())
            self.assertEqual(b"model-data", plan.model_path.read_bytes())


if __name__ == "__main__":
    unittest.main()
