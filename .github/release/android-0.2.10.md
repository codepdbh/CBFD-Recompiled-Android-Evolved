Necesitas tu propia ROM de **Conker's Bad Fur Day (USA)**. Los APK no incluyen el juego.

## Descargas

| Archivo | Para qué |
|---|---|
| `ConkerRecompiled-Android-v0.2.10-arm64-v8a.apk` | **Recomendado** para teléfonos de 64 bits. |
| `ConkerRecompiled-Android-v0.2.10-armeabi-v7a.apk` | ⚠️ ALPHA experimental de 32 bits, sin mods. |
| `ConkerRecompiled-Mods-v0.2.10.zip` | Mods opcionales para 64 bits: Skip Intro, Skip Any Cutscene y Cheats. Sin cambios. |

El **pack de texturas HD** no cambió: descárgalo de la [v0.2.9](https://github.com/codepdbh/CBFD-Recompiled-Android-Evolved/releases/tag/android-v0.2.9).

📖 [Guía de instalación](https://github.com/codepdbh/CBFD-Recompiled-Android-Evolved/blob/master/android/INSTALL.md). Puedes instalarla encima de la versión anterior.

## Cambios desde la 0.2.9

- **Adreno 6xx con Vulkan 1.1** (por ejemplo, Adreno 642L y Adreno 619 con el driver 512.530): el juego se cerraba al empezar porque fallaban los pipelines gráficos del renderizador. Ahora estos Adreno usan arrays de 1024 texturas, numerados en cada fotograma, como ya hacían Mali y PowerVR. Es un intento: todavía no está confirmado en esos teléfonos.
- **Informes:** si un pipeline gráfico del renderizador falla, el informe dice cuál.

## Validación

Ambos APK compilados. Probado en un Samsung S25 (Adreno 830), con y sin el pack HD. **Falta confirmarlo en Adreno 6xx reales.** Si te sigue fallando, envía el informe desde la pantalla de inicio después de jugar con esta versión.

## SHA-256

```
f75b4ad73637211caef34222ba64850efbfd763bb59225b6d3d886f4276e6a99  ConkerRecompiled-Android-v0.2.10-arm64-v8a.apk
76345687244247c8a7f003f60cec50baf6f7ad4d6e31925d4245ce071952f83f  ConkerRecompiled-Android-v0.2.10-armeabi-v7a.apk
647147cf27221b41208b4c966c98d998eecd53d6864987e2ef492b494336cb1d  ConkerRecompiled-Mods-v0.2.10.zip
```
