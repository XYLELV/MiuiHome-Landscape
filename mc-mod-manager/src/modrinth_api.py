"""Thin wrapper around the Modrinth REST API v2."""

import time
from typing import Any, Optional

import requests

BASE = "https://api.modrinth.com/v2"
HEADERS = {
    "User-Agent": "MCModManager/1.0 (github.com/xylelv/miuihome-landscape)",
}
_SESSION = requests.Session()
_SESSION.headers.update(HEADERS)


def _get(path: str, **kwargs) -> Any:
    r = _SESSION.get(f"{BASE}{path}", timeout=15, **kwargs)
    r.raise_for_status()
    return r.json()


def _post(path: str, **kwargs) -> Any:
    r = _SESSION.post(f"{BASE}{path}", timeout=15, **kwargs)
    r.raise_for_status()
    return r.json()


def lookup_hashes(sha512_hashes: list[str]) -> dict[str, Any]:
    """
    Returns a dict mapping sha512_hash -> Modrinth version object.
    Hashes not found on Modrinth are absent from the result.
    """
    if not sha512_hashes:
        return {}
    try:
        return _post(
            "/version_files",
            json={"hashes": sha512_hashes, "algorithm": "sha512"},
        )
    except Exception:
        return {}


def get_project(project_id: str) -> Optional[dict]:
    try:
        return _get(f"/project/{project_id}")
    except Exception:
        return None


def get_latest_version(
    project_id: str,
    loaders: list[str],
    game_versions: list[str],
) -> Optional[dict]:
    """Return the newest version matching the given loaders/game_versions, or None."""
    try:
        params: dict[str, Any] = {}
        if loaders:
            import json
            params["loaders"] = json.dumps(loaders)
        if game_versions:
            import json
            params["game_versions"] = json.dumps(game_versions)
        versions = _get(f"/project/{project_id}/version", params=params)
        return versions[0] if versions else None
    except Exception:
        return None


def get_download_info(version: dict) -> tuple[str, str]:
    """Extract (download_url, filename) from a version object."""
    for file in version.get("files", []):
        if file.get("primary", False):
            return file["url"], file["filename"]
    files = version.get("files", [])
    if files:
        return files[0]["url"], files[0]["filename"]
    return "", ""
