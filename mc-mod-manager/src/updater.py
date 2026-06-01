"""Check for updates and download new mod versions."""

from __future__ import annotations

import shutil
from pathlib import Path
from typing import Callable, Optional

import requests

from . import modrinth_api
from .mod_scanner import ModInfo


def check_updates(
    mods: list[ModInfo],
    loader_value: str,
    game_version: str,
    progress: Optional[Callable[[int, int], None]] = None,
) -> None:
    """
    Fill in Modrinth data and update status for each ModInfo in-place.
    progress(current, total) is called after each mod is processed.
    """
    # Step 1: batch hash lookup
    hashes = [m.sha512 for m in mods if m.sha512]
    hash_to_mod: dict[str, ModInfo] = {m.sha512: m for m in mods if m.sha512}
    lookup = modrinth_api.lookup_hashes(hashes)

    for sha, version_obj in lookup.items():
        mod = hash_to_mod.get(sha)
        if mod is None:
            continue
        mod.modrinth_version_id = version_obj.get("id", "")
        mod.modrinth_project_id = version_obj.get("project_id", "")
        mod.modrinth_project_url = (
            f"https://modrinth.com/mod/{mod.modrinth_project_id}"
        )
        installed_version_number = version_obj.get("version_number", "")

        # Step 2: find latest compatible version
        loaders = [loader_value] if loader_value and loader_value != "unknown" else []
        gv = [game_version] if game_version else []
        latest = modrinth_api.get_latest_version(
            mod.modrinth_project_id, loaders, gv
        )
        if latest is None:
            mod.status = "up_to_date"
            mod.latest_version_number = installed_version_number
        else:
            mod.latest_version_number = latest.get("version_number", "")
            mod.latest_version_id = latest.get("id", "")
            url, filename = modrinth_api.get_download_info(latest)
            mod.download_url = url
            mod.download_filename = filename
            if latest["id"] == mod.modrinth_version_id:
                mod.status = "up_to_date"
            else:
                mod.status = "outdated"

    # Mark mods not found on Modrinth
    for mod in mods:
        if mod.status == "unknown":
            mod.status = "not_on_modrinth"

    if progress:
        progress(len(mods), len(mods))


def download_update(
    mod: ModInfo,
    mods_dir: Path,
    progress: Optional[Callable[[int, int], None]] = None,
) -> bool:
    """
    Download the latest version of `mod` into `mods_dir`, remove the old file.
    Returns True on success.
    """
    if not mod.download_url:
        return False

    target_name = mod.download_filename or Path(mod.download_url).name
    target_path = mods_dir / target_name
    tmp_path = target_path.with_suffix(".tmp")

    try:
        with requests.get(mod.download_url, stream=True, timeout=60) as r:
            r.raise_for_status()
            total = int(r.headers.get("content-length", 0))
            downloaded = 0
            with open(tmp_path, "wb") as f:
                for chunk in r.iter_content(chunk_size=65536):
                    f.write(chunk)
                    downloaded += len(chunk)
                    if progress and total:
                        progress(downloaded, total)

        # Remove old file and rename tmp
        old = mod.file_path
        if old.exists() and old != target_path:
            old.unlink()
        shutil.move(str(tmp_path), str(target_path))

        # Update mod info
        mod.file_path = target_path
        mod.file_name = target_name
        mod.version = mod.latest_version_number
        mod.modrinth_version_id = mod.latest_version_id
        mod.status = "up_to_date"
        return True
    except Exception as e:
        if tmp_path.exists():
            tmp_path.unlink(missing_ok=True)
        raise e
