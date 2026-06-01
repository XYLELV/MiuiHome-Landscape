import hashlib
import json
import zipfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

try:
    import tomllib  # Python 3.11+
except ModuleNotFoundError:
    try:
        import tomli as tomllib  # type: ignore
    except ModuleNotFoundError:
        tomllib = None  # type: ignore


@dataclass
class ModInfo:
    file_path: Path
    file_name: str
    mod_id: str = ""
    name: str = ""
    version: str = ""
    description: str = ""
    authors: list[str] = field(default_factory=list)
    sha512: str = ""
    sha1: str = ""
    # Modrinth data (filled after API call)
    modrinth_version_id: str = ""
    modrinth_project_id: str = ""
    modrinth_project_url: str = ""
    latest_version_number: str = ""
    latest_version_id: str = ""
    download_url: str = ""
    download_filename: str = ""
    # "unknown" | "up_to_date" | "outdated" | "not_on_modrinth"
    status: str = "unknown"

    @property
    def display_name(self) -> str:
        return self.name or self.mod_id or self.file_name

    @property
    def update_available(self) -> bool:
        return self.status == "outdated"


def scan_mods(mods_dir: Path) -> list[ModInfo]:
    mods_dir = Path(mods_dir)
    if not mods_dir.exists():
        return []
    results: list[ModInfo] = []
    for jar in sorted(mods_dir.glob("*.jar")):
        info = _parse_jar(jar)
        results.append(info)
    return results


def _parse_jar(jar_path: Path) -> ModInfo:
    info = ModInfo(file_path=jar_path, file_name=jar_path.name)
    _compute_hashes(jar_path, info)
    try:
        with zipfile.ZipFile(jar_path, "r") as zf:
            names = set(zf.namelist())
            if "fabric.mod.json" in names:
                _parse_fabric(zf, info)
            elif "META-INF/neoforge.mods.toml" in names:
                _parse_toml(zf, "META-INF/neoforge.mods.toml", info)
            elif "META-INF/mods.toml" in names:
                _parse_toml(zf, "META-INF/mods.toml", info)
            elif "mcmod.info" in names:
                _parse_mcmod_info(zf, info)
    except Exception:
        pass
    if not info.name:
        info.name = jar_path.stem
    return info


def _compute_hashes(path: Path, info: ModInfo) -> None:
    sha512 = hashlib.sha512()
    sha1 = hashlib.sha1()
    try:
        with open(path, "rb") as f:
            for chunk in iter(lambda: f.read(65536), b""):
                sha512.update(chunk)
                sha1.update(chunk)
        info.sha512 = sha512.hexdigest()
        info.sha1 = sha1.hexdigest()
    except OSError:
        pass


def _parse_fabric(zf: zipfile.ZipFile, info: ModInfo) -> None:
    try:
        data = json.loads(zf.read("fabric.mod.json").decode("utf-8"))
        info.mod_id = data.get("id", "")
        info.name = data.get("name", "")
        info.version = data.get("version", "")
        info.description = data.get("description", "")
        authors_raw = data.get("authors", [])
        info.authors = [
            a if isinstance(a, str) else a.get("name", str(a))
            for a in authors_raw
        ]
    except Exception:
        pass


def _parse_toml(zf: zipfile.ZipFile, entry: str, info: ModInfo) -> None:
    if tomllib is None:
        return
    try:
        raw = zf.read(entry)
        data = tomllib.loads(raw.decode("utf-8"))
        mods_list = data.get("mods", [{}])
        first = mods_list[0] if mods_list else {}
        info.mod_id = first.get("modId", "")
        info.name = first.get("displayName", "")
        info.version = first.get("version", "")
        info.description = first.get("description", "").strip()
        authors = data.get("authors", "")
        if isinstance(authors, str):
            info.authors = [a.strip() for a in authors.split(",") if a.strip()]
        elif isinstance(authors, list):
            info.authors = authors
    except Exception:
        pass


def _parse_mcmod_info(zf: zipfile.ZipFile, info: ModInfo) -> None:
    try:
        raw = json.loads(zf.read("mcmod.info").decode("utf-8"))
        entry = raw[0] if isinstance(raw, list) and raw else raw
        info.mod_id = entry.get("modid", "")
        info.name = entry.get("name", "")
        info.version = entry.get("version", "")
        info.description = entry.get("description", "")
        info.authors = entry.get("authorList", [])
    except Exception:
        pass
