import json
import os
from pathlib import Path

_CONFIG_DIR = Path(os.getenv("APPDATA", Path.home() / ".config")) / "MCModManager"
_CONFIG_FILE = _CONFIG_DIR / "config.json"

_DEFAULTS = {
    "minecraft_dir": str(Path(os.getenv("APPDATA", Path.home())) / ".minecraft"),
    "theme": "dark",
    "auto_check_updates": False,
}


def load() -> dict:
    _CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    if _CONFIG_FILE.exists():
        try:
            with open(_CONFIG_FILE, "r", encoding="utf-8") as f:
                data = json.load(f)
            return {**_DEFAULTS, **data}
        except Exception:
            pass
    return dict(_DEFAULTS)


def save(cfg: dict) -> None:
    _CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    with open(_CONFIG_FILE, "w", encoding="utf-8") as f:
        json.dump(cfg, f, indent=2, ensure_ascii=False)
