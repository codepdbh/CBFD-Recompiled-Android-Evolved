Necesitas tu propia ROM de **Conker's Bad Fur Day (USA)**. Los APK no incluyen el juego.

## Descargas

| Archivo | Para qué |
|---|---|
| `ConkerRecompiled-Android-v0.2.9-arm64-v8a.apk` | **Recomendado** para teléfonos de 64 bits. |
| `ConkerRecompiled-Android-v0.2.9-armeabi-v7a.apk` | ⚠️ ALPHA experimental de 32 bits, sin mods. |
| `ConkerRecompiled-Mods-v0.2.9.zip` | Mods opcionales para 64 bits: Skip Intro, Skip Any Cutscene y Cheats. Sin cambios. |
| `ConkerRecompiled-TexturasHD-v0.2.9.rtz` | 🆕 **Pack de texturas HD** (1117 texturas, hasta 1024 px). |

📖 [Guía de instalación](https://github.com/codepdbh/CBFD-Recompiled-Android-Evolved/blob/master/android/INSTALL.md). Puedes instalarla encima de la versión anterior.

## Texturas HD

1. Descarga `ConkerRecompiled-TexturasHD-v0.2.9.rtz`.
2. En la pantalla de inicio toca **Mods → Añadir** y elige el archivo, o cópialo a `ConkerRecompiled/mods/`.
3. Comprueba que esté activado en **Mods** y juega.

Pesa 337 MB y usa bastante memoria de la GPU. Si tu teléfono va lento o se cierra con el pack, desactívalo en **Mods**.

## Cambios desde la 0.2.8

- **Packs de texturas:** el juego acepta packs de RT64 (`.rtz`) como mods, también en Android.
- **Packs hechos para GLideN64:** el juego calcula el hash "Rice" de cada textura, igual que GLideN64, y la busca en el pack. Así funcionan sin volcar las texturas jugando todo el juego.
- **Convertidor:** `tools/htc_to_rtz.py` convierte una caché `.htc` de GLideN64 en un `.rtz`. Así se hizo el pack de este release.

## Validación

Ambos APK compilados. Pack probado en un Samsung S25 (Adreno 830): las texturas se reemplazan, incluidas las de paleta, y se ven en HD. Falta probarlo en teléfonos con menos memoria.

## SHA-256

```
240fa9a219c505e12fb38046c4b12ff6ef85c682183822feac8d819918915fe3  ConkerRecompiled-Android-v0.2.9-arm64-v8a.apk
3450cc4a980415d7e0109bce97eeec83d962190fa96c54b9ef22e810cdf7e54a  ConkerRecompiled-Android-v0.2.9-armeabi-v7a.apk
647147cf27221b41208b4c966c98d998eecd53d6864987e2ef492b494336cb1d  ConkerRecompiled-Mods-v0.2.9.zip
6466be4246e9c8c1f3aa7b0dd2446352c2227a47854af4b9d44142605bd0dd6b  ConkerRecompiled-TexturasHD-v0.2.9.rtz
```
