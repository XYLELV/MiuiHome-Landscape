"""Entry point — run directly or via PyInstaller bundle."""
import sys
import os

# Allow importing src package when run as a script
sys.path.insert(0, os.path.dirname(__file__))

from src.ui.app import run

if __name__ == "__main__":
    run()
