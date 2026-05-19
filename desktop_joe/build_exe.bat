@echo off
cd /d %~dp0
pyinstaller --noconfirm --clean --onefile --windowed --name JoeDesktop ^
  --exclude-module numpy ^
  --exclude-module sounddevice ^
  --exclude-module speech_recognition ^
  --exclude-module openpyxl ^
  main.py
