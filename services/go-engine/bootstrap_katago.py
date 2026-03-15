from __future__ import annotations

import os
import platform
import shutil
import stat
import tempfile
import urllib.request
import zipfile
from dataclasses import dataclass
from pathlib import Path


KATAGO_VERSION = "v1.16.4"
KATAGO_RELEASE_BASE = f"https://github.com/lightvector/KataGo/releases/download/{KATAGO_VERSION}"
MODEL_FILENAME = "kata1-b18c384nbt-s9996604416-d4316597426.bin.gz"
MODEL_URL = (
    "https://media.katagotraining.org/uploaded/networks/models/kata1/"
    + MODEL_FILENAME
)

ENGINE_RELEASES = {
    "Linux": {
        "archive_name": f"katago-{KATAGO_VERSION}-eigen-linux-x64.zip",
        "archive_root": f"katago-{KATAGO_VERSION}-eigen-linux-x64",
        "binary_name": "katago",
    },
    "Windows": {
        "archive_name": f"katago-{KATAGO_VERSION}-eigen-windows-x64.zip",
        "archive_root": f"katago-{KATAGO_VERSION}-eigen-windows-x64",
        "binary_name": "katago.exe",
    },
}


@dataclass(frozen=True)
class InstallPlan:
    install_dir: Path
    archive_path: Path
    archive_root: str
    binary_name: str
    binary_path: Path
    config_path: Path
    model_path: Path
    engine_url: str
    model_url: str


def default_install_plan(base_dir: Path, system_name: str | None = None) -> InstallPlan:
    system = (system_name or platform.system()).strip()
    release = ENGINE_RELEASES.get(system)
    if release is None:
        raise ValueError(f"unsupported platform for KataGo bootstrap: {system}")
    install_dir = Path(base_dir) / ".katago"
    archive_name = release["archive_name"]
    binary_name = release["binary_name"]
    return InstallPlan(
        install_dir=install_dir,
        archive_path=install_dir / archive_name,
        archive_root=release["archive_root"],
        binary_name=binary_name,
        binary_path=install_dir / binary_name,
        config_path=install_dir / "default_gtp.cfg",
        model_path=install_dir / MODEL_FILENAME,
        engine_url=f"{KATAGO_RELEASE_BASE}/{archive_name}",
        model_url=MODEL_URL,
    )


def download_file(url: str, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(url) as response, destination.open("wb") as output:
        shutil.copyfileobj(response, output)


def ensure_assets(
    plan: InstallPlan,
    downloader=download_file,
) -> InstallPlan:
    plan.install_dir.mkdir(parents=True, exist_ok=True)
    if not plan.binary_path.exists() or not plan.config_path.exists():
        if not plan.archive_path.exists():
            downloader(plan.engine_url, plan.archive_path)
        _extract_engine_archive(plan)
    if not plan.model_path.exists():
        downloader(plan.model_url, plan.model_path)
    _mark_binary_executable(plan.binary_path)
    if plan.archive_path.exists():
        plan.archive_path.unlink()
    return plan


def _extract_engine_archive(plan: InstallPlan) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
        temp_path = Path(temp_dir)
        with zipfile.ZipFile(plan.archive_path) as archive:
            archive.extractall(temp_path)
        extracted_root = _resolve_extracted_root(temp_path, plan)
        _copy_required_file(extracted_root / plan.binary_name, plan.binary_path)
        _copy_required_file(extracted_root / "default_gtp.cfg", plan.config_path)


def _resolve_extracted_root(temp_path: Path, plan: InstallPlan) -> Path:
    expected_root = temp_path / plan.archive_root
    if expected_root.exists():
        return expected_root
    binary_matches = list(temp_path.rglob(plan.binary_name))
    if not binary_matches:
        raise FileNotFoundError(f"missing extracted KataGo root: {plan.archive_root}")
    for binary_match in binary_matches:
        candidate = binary_match.parent
        if (candidate / "default_gtp.cfg").exists():
            return candidate
    raise FileNotFoundError(f"missing required KataGo config beside {plan.binary_name}")


def _copy_required_file(source: Path, destination: Path) -> None:
    if not source.exists():
        raise FileNotFoundError(f"missing required KataGo asset: {source.name}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)


def _mark_binary_executable(binary_path: Path) -> None:
    if os.name == "nt" or not binary_path.exists():
        return
    current_mode = binary_path.stat().st_mode
    binary_path.chmod(
        current_mode
        | stat.S_IXUSR
        | stat.S_IXGRP
        | stat.S_IXOTH
        | stat.S_IRUSR
        | stat.S_IWUSR
        | stat.S_IRGRP
        | stat.S_IROTH
    )


def main() -> None:
    base_dir = Path(os.getenv("KATAGO_BASE_DIR", Path(__file__).resolve().parent))
    plan = ensure_assets(default_install_plan(base_dir))
    print(f"KataGo bootstrap ready: {plan.binary_path}")
    print(f"Config: {plan.config_path}")
    print(f"Model: {plan.model_path}")


if __name__ == "__main__":
    main()
