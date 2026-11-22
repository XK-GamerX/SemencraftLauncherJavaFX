# Installer and Update Workflow

## What was added

- `packageLauncherAppImage`: builds a portable app folder with `jpackage`.
- `packageLauncherInstaller`: builds Windows `msi` installer with `jpackage`.
- `packageLauncherZip`: builds a distributable zip from the app-image.
- `scripts/build-msi.ps1`: one-command MSI build script with prerequisite checks.
- MSI now includes `--win-shortcut-prompt` (asks user if desktop shortcut should be created).
- Launcher modpacks now update from GitHub at runtime (no MSI rebuild needed for each mod/resourcepack change).

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

## Instalador: version anterior detectada

El MSI usa un `main.wxs` propio (`installer/wix/`, pasado a jpackage con `--resource-dir`):

- Cada build genera un ProductCode nuevo, asi que una build nueva siempre reemplaza a la instalada
  (aunque tenga el mismo numero de version).
- Si ya hay un Semencraft Launcher instalado (version mas vieja, igual o mas nueva), aparece el paso
  "Version anterior detectada": el instalador la desinstala por completo y despues instala la nueva
  en la misma carpeta. `%APPDATA%\.semencraft` (mundos, ajustes, mods) no se toca.
- Recuerda la carpeta de instalacion (`HKCU\Software\Semencraft\Semencraft Launcher`) para las
  siguientes actualizaciones.
- Textos en espanol (`MsiInstallerStrings_es.wxl`; el `_en.wxl` es solo respaldo) y casilla
  "Iniciar Semencraft Launcher" al terminar.

## Actualizaciones del launcher (GitHub Releases)

Al abrir, el launcher consulta `releases/latest` de `XK-GamerX/SemencraftLauncherJavaFX` y, si el tag es
mayor que su version (`build.gradle.kts` -> `launcher-version.properties`), pregunta si actualizar ahora o
mas tarde. "Mas tarde" deja el aviso en la campana de notificaciones.

Para publicar una actualizacion:

1. Subir `version` en `build.gradle.kts` (por ejemplo `1.2.1`).
2. `.\scripts\build-msi.ps1 -Version 1.2.1`
3. Crear la release en GitHub con tag `v1.2.1` y adjuntar el `.msi` generado.

"Actualizar ahora" descarga el MSI (verifica tamano y el SHA-256 que publica GitHub), lo ejecuta con
`msiexec /passive SEMENCRAFT_RELAUNCH=1` y cierra el launcher; el MSI desinstala la version anterior y
vuelve a abrir el launcher al terminar. Las instalaciones anteriores a 1.2.0 no tienen este actualizador:
hay que instalar 1.2.0 a mano una vez.

## Windows requirement for MSI

`jpackage` needs **WiX Toolset** in `PATH` to produce `msi`.

You must install WiX Toolset v3 (which provides `candle.exe` and `light.exe`) for MSI packaging.

## Java 21 for end users

- The MSI/app-image generated with `jpackage` should already bundle the Java 21 runtime used by the launcher.
- For a normal end user install, this is the safest path because it avoids extra prerequisite errors.
- If you run the launcher manually outside the packaged MSI/app-image and Java 21 is missing, the launcher now offers the Oracle MSI fallback:
  - `https://download.oracle.com/java/21/latest/jdk-21_windows-x64_bin.msi`

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

## Modpack updates without rebuilding MSI

- The installed launcher synchronizes modpacks from:
  - `https://github.com/XK-GamerX/SemencraftModpacks`
- This means you can update mods/resourcepacks in the repo and users receive changes automatically.
- Local cache path used by launcher:
  - `%APPDATA%\\.semencraft\\launcher\\modpacks-github-cache\\SemencraftModpacks`
- Practical recommendation:
  - First launch should have internet available to warm the cache.

## Credits

- Launcher and installer credits: **Khel Palacios**

## Imagenes y retoques del instalador

- `installer/wix/dialog.bmp` (493x312, bienvenida y final) y `installer/wix/banner.bmp` (493x58, cabecera de cada paso)
  se generan con `java design/installer/InstallerArt.java` desde la raiz del proyecto, a partir de `bg.png`, el logo y el icono.
  `main.wxs` las toma de la carpeta indicada en la variable de entorno `SEMENCRAFT_WIX_RES` (la tarea Gradle la define sola).
- Despues de jpackage, `installer/patch-msi.ps1` oculta los botones "Atras" y "Cancelar" desactivados de las pantallas finales.
  Si generas el MSI llamando a jpackage a mano, ejecuta ese script sobre el `.msi` al final.
