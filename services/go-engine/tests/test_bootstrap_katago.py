import pathlib
import sys
import tempfile
import unittest
import zipfile
from io import BytesIO


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
            "https://media.katagotraining.org/uploaded/networks/models/kata1/kata1-b10c128-s1141046784-d204142634.bin.gz",
            plan.model_url,
        )
        self.assertEqual(base_dir / ".katago" / "katago", plan.binary_path)
        self.assertEqual(base_dir / ".katago" / "default_gtp.cfg", plan.config_path)
        self.assertEqual(
            base_dir / ".katago" / "kata1-b10c128-s1141046784-d204142634.bin.gz",
            plan.model_path,
        )


class DownloadFileTest(unittest.TestCase):
    def test_download_file_sends_user_agent(self):
        seen = {}

        class FakeResponse(BytesIO):
            def __enter__(self):
                return self

            def __exit__(self, exc_type, exc, tb):
                self.close()

        def fake_urlopen(request):
            seen["user_agent"] = request.headers.get("User-agent")
            return FakeResponse(b"ok")

        original = bootstrap_katago.urllib.request.urlopen
        try:
            bootstrap_katago.urllib.request.urlopen = fake_urlopen
            with tempfile.TemporaryDirectory() as tmp:
                target = pathlib.Path(tmp) / "download.bin"
                bootstrap_katago.download_file("https://example.com/test.bin", target)
                self.assertEqual(b"ok", target.read_bytes())
        finally:
            bootstrap_katago.urllib.request.urlopen = original

        self.assertTrue(seen.get("user_agent"))


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

    def test_extracts_assets_even_without_archive_root_directory(self):
        with tempfile.TemporaryDirectory() as tmp:
            base_dir = pathlib.Path(tmp)
            plan = bootstrap_katago.default_install_plan(base_dir, system_name="Linux")
            plan.install_dir.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(plan.archive_path, "w") as archive:
                archive.writestr("katago", "#!/bin/sh\necho ok\n")
                archive.writestr("default_gtp.cfg", "rules = chinese\n")
            plan.model_path.write_bytes(b"model-data")

            bootstrap_katago.ensure_assets(plan)

            self.assertTrue(plan.binary_path.exists())
            self.assertTrue(plan.config_path.exists())


if __name__ == "__main__":
    unittest.main()
