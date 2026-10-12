Necesitas tu propia ROM de **Conker's Bad Fur Day (USA)**. Los APK no incluyen el juego.

## Descargas

| Archivo | Para qué |
|---|---|
| `ConkerRecompiled-Android-v0.2.15-arm64-v8a.apk` | **Recomendado** para teléfonos de 64 bits. |
| `ConkerRecompiled-Android-v0.2.15-armeabi-v7a.apk` | ⚠️ ALPHA experimental de 32 bits, sin mods ni drivers de GPU. |
| `ConkerRecompiled-Mods-v0.2.15.zip` | Mods opcionales para 64 bits: Skip Intro, Skip Any Cutscene y Cheats. Sin cambios. |

El **pack de texturas HD** no cambió: descárgalo de la [v0.2.11](https://github.com/codepdbh/CBFD-Recompiled-Android-Evolved/releases/tag/android-v0.2.11).

📖 [Guía de instalación](https://github.com/codepdbh/CBFD-Recompiled-Android-Evolved/blob/master/android/INSTALL.md). Puedes instalarla encima de la versión anterior.

## Drivers de GPU: ahora sí cargan, y se descargan desde la app

- **Arreglo importante:** en la v0.2.14 los drivers alternativos no llegaban a cargarse, porque Android no extraía las bibliotecas que adrenotools necesita. Ahora sí: en un S25, Turnip aparece como `Turnip Adreno (TM) 830v2`, con Vulkan 1.4.
- **Descargar de GitHub:** en **Ajustes → Driver de GPU → Cambiar driver → Descargar de GitHub**. Primero eliges un repositorio y luego el ZIP de una de sus versiones; la app lo descarga, lo instala y lo deja elegido. Repositorios incluidos:
  - **Adreno:** K11MCH1/AdrenoToolsDrivers y StevenMXZ/Adreno-Tools-Drivers (Turnip).
  - **Mali (PanVK, experimental):** FristOneRR (Mali-G57), LukeValen (Mali-G52) y wonderkast02 (Mali-G720).
  - **Cualquier otro:** escribe `usuario/repositorio`.
- **Recomendación según tu GPU:** la app detecta la GPU y te dice qué tipo de driver conviene. Para Xclipse (Exynos 2200 o posterior) y PowerVR no se conocen drivers alternativos.
- **Seguridad:** si el driver no reconoce tu GPU, el juego usa el del sistema. Si una partida con el driver falla (la GPU deja de responder o no muestra la imagen), la pantalla de inicio te ofrece volver al del sistema.
- **Informes más limpios:** un error de la pantalla que se repetía miles de veces ahora se anota solo tres veces.

## Validación

Ambos APK compilados. En un Samsung S25 (Adreno 830) se probó la descarga e instalación desde GitHub del `turnip_a8xx.zip` de K11MCH1. El driver carga, pero esa versión, beta para Adreno 8xx, pierde la GPU al mostrar la imagen; la app lo detectó y ofreció volver al del sistema, que funciona normal. **Falta confirmar un driver alternativo estable**, por ejemplo Turnip en un Adreno 6xx/7xx.

## SHA-256

```
ecb3fa8b7cf45c1f17bdb2d1047cc0279af8a5d6995bd7983468ff3a9e8cb178  ConkerRecompiled-Android-v0.2.15-arm64-v8a.apk
e0c0b038c888b820c546597bc35df804043d05614dc2be0b8d4d38e6aaa6aeb5  ConkerRecompiled-Android-v0.2.15-armeabi-v7a.apk
647147cf27221b41208b4c966c98d998eecd53d6864987e2ef492b494336cb1d  ConkerRecompiled-Mods-v0.2.15.zip
```
