@echo off
cd /d "%~dp0"

echo Starting Smart Pantry backend...
echo Current folder: %cd%
echo.

if not exist ".venv\Scripts\python.exe" (
    echo Virtual environment was not found.
    echo Creating .venv...
    python -m venv .venv

    if errorlevel 1 (
        echo Failed to create the virtual environment.
        pause
        exit /b 1
    )
)

echo Installing required packages...
".venv\Scripts\python.exe" -m pip install -r requirements.txt

if errorlevel 1 (
    echo Failed to install the required packages.
    pause
    exit /b 1
)

".venv\Scripts\python.exe" -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

echo.
echo The server stopped or failed to start.
pause