Necesitas tu propia ROM de **Conker's Bad Fur Day (USA)**. Los APK no incluyen el juego.

## Descargas

| Archivo | Para qué |
|---|---|
| `ConkerRecompiled-Android-v0.2.14-arm64-v8a.apk` | **Recomendado** para teléfonos de 64 bits. |
| `ConkerRecompiled-Android-v0.2.14-armeabi-v7a.apk` | ⚠️ ALPHA experimental de 32 bits, sin mods ni drivers de GPU. |
| `ConkerRecompiled-Mods-v0.2.14.zip` | Mods opcionales para 64 bits: Skip Intro, Skip Any Cutscene y Cheats. Sin cambios. |

El **pack de texturas HD** no cambió: descárgalo de la [v0.2.11](https://github.com/codepdbh/CBFD-Recompiled-Android-Evolved/releases/tag/android-v0.2.11).

📖 [Guía de instalación](https://github.com/codepdbh/CBFD-Recompiled-Android-Evolved/blob/master/android/INSTALL.md). Puedes instalarla encima de la versión anterior.

## Novedad: drivers de GPU personalizados

En **Ajustes → Driver de GPU** puedes importar un driver Vulkan alternativo, por ejemplo **Turnip** para Adreno. El ZIP debe estar en formato adrenotools, con `meta.json`; los de [K11MCH1/AdrenoToolsDrivers](https://github.com/K11MCH1/AdrenoToolsDrivers/releases) sirven. Puede mejorar el rendimiento o arreglar errores gráficos, igual que en NFS Most Wanted Android Evolved.

- **Pasos:** toca **Cambiar driver → Importar ZIP**, elige el archivo y juega. El driver queda seleccionado.
- **Si el driver no reconoce tu GPU**, el juego usa automáticamente el del sistema y lo anota en el informe.
- **Si el juego falla de otra forma**, vuelve a **Driver del sistema** en el mismo menú.
- **Solo en el APK de 64 bits.** El informe del juego dice qué driver se usó.

## Validación

Ambos APK compilados. En un Redmi Note 8 (Adreno 610), el driver del sistema funciona igual que antes. Turnip v26.0.0 R8 se importa y carga, pero no reconoce esa GPU, así que el juego vuelve solo al driver del sistema. **Falta confirmar un driver personalizado funcionando** en un teléfono que Turnip soporte.

## SHA-256

```
b101cb0164464cc52fc4a6960d793edbece703119dbef298f3347687de5e0ae3  ConkerRecompiled-Android-v0.2.14-arm64-v8a.apk
ce45a81b8198f76b40781bd4d0ed64e7213ffb48bf3b736f48b0893b5440ec04  ConkerRecompiled-Android-v0.2.14-armeabi-v7a.apk
647147cf27221b41208b4c966c98d998eecd53d6864987e2ef492b494336cb1d  ConkerRecompiled-Mods-v0.2.14.zip
```
