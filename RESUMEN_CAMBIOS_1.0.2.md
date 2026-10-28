# Nuevos cambios desde el ultimo changelog

Este archivo contiene solo los cambios nuevos hechos despues del ultimo changelog entregado.

## Backend y datos

- Se migro la parte principal del launcher desde el flujo viejo basado en GitHub JSON hacia Supabase.
- Se conectaron usuarios, equipos, invitaciones, notificaciones e iconos de equipo con Supabase.
- Se preparo almacenamiento para skins e iconos usando Supabase Storage.

## Skin Manager y skins

- Se implemento `Skin Manager` funcional con subida de skins a la cuenta del usuario.
- Se agrego guardado del tipo de cuerpo `Clasica` o `Delgada`.
- Se corrigio el error de Supabase cuando todavia no existia `meta.json`.
- Se corrigio la sobreescritura de `skin.png` para no fallar al actualizar una skin ya existente.
- Se corrigio el bug visual donde la skin parecia revertirse al cambiar de pestana hasta reiniciar el launcher.
- Se reemplazo la preview simple por una vista de personaje completo y una vista de textura.
- Se ajustaron varias veces el tamano, centrado y overlay de la preview de skin.

## Mod de Semencraft

- Se implemento el sistema para que el mod lea la configuracion runtime escrita por el launcher.
- Se implementaron skins personalizadas visibles dentro de Minecraft para quienes usan el mod/modpack.
- Se agrego formato de chat tipo `nombrejugador ≫ mensaje`.
- El nombre del jugador usa el color del equipo.
- El simbolo `≫` se muestra en gris.
- Al hacer hover sobre el nombre en el chat, aparece el nombre del equipo con tooltip estilo Minecraft.
- Se corrigio un crash del mod en servidor dedicado separando logica visual de cliente fuera del codigo comun.

## Bot y Discord

- Se corrigieron problemas de sincronizacion del bot.
- Se evitaron duplicaciones de canales/recursos de equipo.
- Se reforzo el manejo de roles y categorias de equipos en Discord.

## Interfaz del launcher

- Se ajusto la version visible a `Fabric 1.21.8`.
- Se pulieron varias fuentes dinamicas para que carguen correctamente donde corresponde.
- Se mejoro el badge de notificaciones para que no tape la campana y se actualice sin abrir el panel.
- Se anadieron cabezas de jugador e iconos de equipo en distintas vistas del launcher.
- Se mejoraron detalles visuales en `Tu equipo`, invitaciones y `Skin Manager`.

## Instalador

- Se actualizo la version del launcher a `1.0.3`.
- Se genero un nuevo instalador MSI:
  - `Semencraft Launcher-1.0.3.msi`

## Archivo generado

- `C:\Users\IK\IdeaProjects\SemencraftLauncherJavaFX\build\installer\output\Semencraft Launcher-1.0.3.msi`
