@echo off
echo [1/3] Installing dependencies...
pip install -r requirements.txt
pip install pyinstaller

echo [2/3] Building exe...
pyinstaller ^
  --onefile ^
  --windowed ^
  --name "MCModManager" ^
  --icon "icon.ico" ^
  --add-data "src;src" ^
  main.py

echo [3/3] Done! Check the dist\ folder for MCModManager.exe
pause
