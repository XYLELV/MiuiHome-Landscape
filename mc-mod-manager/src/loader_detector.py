from enum import Enum
from pathlib import Path


class ModLoader(Enum):
    FORGE = "forge"
    NEOFORGE = "neoforge"
    FABRIC = "fabric"
    QUILT = "quilt"
    UNKNOWN = "unknown"

    def display(self) -> str:
        return {
            "forge": "Forge",
            "neoforge": "NeoForge",
            "fabric": "Fabric",
            "quilt": "Quilt",
            "unknown": "Unknown",
        }[self.value]

    def color(self) -> str:
        return {
            "forge": "#C07A27",
            "neoforge": "#7B5EA7",
            "fabric": "#DBB382",
            "quilt": "#C272C3",
            "unknown": "#888888",
        }[self.value]


def detect(minecraft_dir: Path) -> tuple[ModLoader, str]:
    """Return (loader, mc_game_version) by inspecting the versions directory."""
    versions_dir = minecraft_dir / "versions"
    if not versions_dir.exists():
        return ModLoader.UNKNOWN, ""

    candidates: list[tuple[str, str]] = []  # (version_dir_name, priority_key)
    for vd in versions_dir.iterdir():
        if not vd.is_dir():
            continue
        name_lower = vd.name.lower()
        if "neoforge" in name_lower:
            candidates.append((vd.name, "neoforge"))
        elif "forge" in name_lower:
            candidates.append((vd.name, "forge"))
        elif "fabric" in name_lower:
            candidates.append((vd.name, "fabric"))
        elif "quilt" in name_lower:
            candidates.append((vd.name, "quilt"))

    if not candidates:
        return ModLoader.UNKNOWN, ""

    # prefer neoforge > forge > fabric > quilt
    priority = {"neoforge": 0, "forge": 1, "fabric": 2, "quilt": 3}
    candidates.sort(key=lambda c: priority.get(c[1], 99))
    chosen_name, loader_key = candidates[0]

    # extract MC game version (e.g. "1.20.1" from "1.20.1-forge-47.2.0")
    game_version = _extract_game_version(chosen_name)

    return ModLoader(loader_key), game_version


def _extract_game_version(version_str: str) -> str:
    """Best-effort extraction of the Minecraft game version from a version folder name."""
    import re
    # Patterns like: 1.20.1-forge-..., fabric-loader-...-1.20.1, 1.21.4
    match = re.search(r"(\d+\.\d+(?:\.\d+)?)", version_str)
    return match.group(1) if match else ""
