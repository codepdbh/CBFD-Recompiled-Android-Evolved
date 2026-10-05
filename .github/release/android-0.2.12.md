Necesitas tu propia ROM de **Conker's Bad Fur Day (USA)**. Los APK no incluyen el juego.

## Descargas

| Archivo | Para qué |
|---|---|
| `ConkerRecompiled-Android-v0.2.12-arm64-v8a.apk` | **Recomendado** para teléfonos de 64 bits. |
| `ConkerRecompiled-Android-v0.2.12-armeabi-v7a.apk` | ⚠️ ALPHA experimental de 32 bits, sin mods. |
| `ConkerRecompiled-Mods-v0.2.12.zip` | Mods opcionales para 64 bits: Skip Intro, Skip Any Cutscene y Cheats. Sin cambios. |

El **pack de texturas HD** no cambió: descárgalo de la [v0.2.11](https://github.com/codepdbh/CBFD-Recompiled-Android-Evolved/releases/tag/android-v0.2.11).

📖 [Guía de instalación](https://github.com/codepdbh/CBFD-Recompiled-Android-Evolved/blob/master/android/INSTALL.md). Puedes instalarla encima de la versión anterior.

## Cambios desde la 0.2.11

- **Mali sin "dual source blending"** (por ejemplo, Mali-G52 y Mali-G57 con drivers antiguos): el renderizador mezclaba las transparencias con una función que estas GPU no tienen. Eso podía cerrar el juego (*device lost*) o dejar las texturas blancas. Ahora, en esas GPU, el juego usa shaders y una mezcla de transparencias que sí soportan.

## Validación

Ambos APK compilados. En un Samsung S25 (Adreno 830) se probó el modo sin "dual source blending", forzado a propósito, y las transparencias, el humo, las sombras y los textos se veían bien. También se probó el modo normal. **Falta confirmarlo en Mali reales.** Si te sigue fallando, envía el informe desde la pantalla de inicio después de jugar con esta versión.

## SHA-256

```
f0402ec9ec37db9c653f88f85df9eba04e4ce48d4713dc6206d3fc78edecbf3b  ConkerRecompiled-Android-v0.2.12-arm64-v8a.apk
e2b362acb0ec6ba492719e9d97ada5cfeb6c9928a0f21062cd10f4ecd95da9f2  ConkerRecompiled-Android-v0.2.12-armeabi-v7a.apk
647147cf27221b41208b4c966c98d998eecd53d6864987e2ef492b494336cb1d  ConkerRecompiled-Mods-v0.2.12.zip
```
