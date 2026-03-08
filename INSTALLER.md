# Installer and Update Workflow

## What was added

- `packageLauncherAppImage`: builds a portable app folder with `jpackage`.
- `packageLauncherInstaller`: builds Windows `msi` installer with `jpackage`.
- `packageLauncherZip`: builds a distributable zip from the app-image.
- `scripts/build-msi.ps1`: one-command MSI build script with prerequisite checks.
- MSI now includes `--win-shortcut-prompt` (asks user if desktop shortcut should be created).

## Commands

1. Portable app image:

```powershell
.\gradlew.bat packageLauncherAppImage
```

2. Windows installer (MSI default):

```powershell
.\gradlew.bat packageLauncherInstaller
```

3. MSI with explicit version:

```powershell
.\gradlew.bat packageLauncherInstaller "-PinstallerVersion=1.2.3"
```

4. MSI using helper script:

```powershell
.\scripts\build-msi.ps1 -Version 1.2.3
```

5. Zip package for distribution:

```powershell
.\gradlew.bat packageLauncherZip
```

## Output location

All artifacts are written under:

`build/installer/output`

## Versioning for future updates

- Keep a stable app version per release:
  - Edit `version` in `build.gradle.kts`, or
  - Override at build time: `-PinstallerVersion=1.2.3`
- The installer uses a fixed `--win-upgrade-uuid`, so newer installers upgrade existing installs instead of creating parallel apps.

## Windows requirement for MSI

`jpackage` needs **WiX Toolset** in `PATH` to produce `msi`.

You must install WiX Toolset v3 (which provides `candle.exe` and `light.exe`) for MSI packaging.

If WiX is not installed yet, you can still ship:

- `packageLauncherAppImage` (portable folder), or
- `packageLauncherZip` (portable zip).

## Manual steps you must do

1. Install JDK 21+ and ensure `jpackage.exe` is available in PATH.
2. Install WiX Toolset v3 and ensure both `candle.exe` and `light.exe` are available in PATH.
3. Open a new terminal and verify:
   - `where.exe jpackage`
   - `where.exe candle`
   - `where.exe light`
4. Run:
   - `.\scripts\build-msi.ps1 -Version 1.2.3`

## Optional icon

Installer icon priority:

1. `src/main/resources/com/semencraft/semencraftlauncherjavafx/assets/circular-blue.ico`
2. `src/main/resources/com/semencraft/semencraftlauncherjavafx/assets/icon.ico` (fallback)

Launcher runtime icon files used by the app:

- `circular-black.bmp` (window/stage icon + top bar icon + launcher sidebar icon)
- `square-blue.bmp` (taskbar icon)

PNG fallbacks are also supported:

- `circular-black.png`
- `square-blue.png`

## Language and install-finish behavior

- App default locale is set to Spanish (`es-ES`).
- MSI can prompt for desktop shortcut creation.
- `jpackage` MSI does **not** provide a built-in checkbox to "open app after install".
  - To support "open at finish", we would need a custom WiX flow (non-default jpackage resources).

## Credits

- Launcher and installer credits: **Khel Palacios**
