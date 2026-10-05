Necesitas tu propia ROM de **Conker's Bad Fur Day (USA)**. Los APK no incluyen el juego.

## Descargas

| Archivo | Para qué |
|---|---|
| `ConkerRecompiled-Android-v0.2.8-arm64-v8a.apk` | **Recomendado** para teléfonos de 64 bits. |
| `ConkerRecompiled-Android-v0.2.8-armeabi-v7a.apk` | ⚠️ ALPHA experimental de 32 bits, sin mods. |
| `ConkerRecompiled-Mods-v0.2.8.zip` | Mods opcionales para 64 bits: Skip Intro, Skip Any Cutscene y Cheats. Sin cambios. |

📖 [Guía de instalación](https://github.com/codepdbh/CBFD-Recompiled-Android-Evolved/blob/master/android/INSTALL.md). Puedes instalarla encima de la versión anterior.

## Cambios desde la 0.2.7

- **Snapdragon 8 Gen 3 (Adreno 750):** el juego se cerraba al iniciar con *"Unable to find compatible graphics device"*, porque el driver no puede crear los shaders que copian la imagen a la memoria del juego. Ahora el juego arranca sin esas copias, como ya pasaba en los Adreno antiguos.
- **Mali (por ejemplo, Mali-G57) y PowerVR:** usan arrays de texturas más pequeños, numerados en cada fotograma. Es un intento de arreglar las texturas blancas que dejaban el juego injugable.
- **Adreno sin descriptor indexing (por ejemplo, Adreno 619):** también usan arrays de texturas más pequeños, un intento de arreglar los pipelines gráficos que fallaban y cerraban el juego.
- **Informes:** el asunto del correo y el título del issue empiezan con la versión, por ejemplo `[v0.2.8]`. Si el último informe es de una versión anterior, la app pide jugar una vez con la nueva antes de enviarlo.

## Validación

Ambos APK compilados. En un Samsung S25 (Adreno 830) se simularon a la vez un driver sin descriptor indexing, arrays de 1024 texturas y el fallo de los shaders de copia: el juego arrancó y se veía bien. El modo normal también se probó. **Falta confirmarlo en Adreno 750, Adreno 619, Mali y PowerVR reales.** Si te sigue fallando, envía el informe desde la pantalla de inicio después de jugar con esta versión.

## SHA-256

```
ccab1f7667dbe717e58370fab27d7c1eb233e6f9f350f230b32a97d70cffba62  ConkerRecompiled-Android-v0.2.8-arm64-v8a.apk
b8ce00a244cdd8083b3e71d75c086514a9c5d6d18a06bccd5a0d8116b7c1690b  ConkerRecompiled-Android-v0.2.8-armeabi-v7a.apk
647147cf27221b41208b4c966c98d998eecd53d6864987e2ef492b494336cb1d  ConkerRecompiled-Mods-v0.2.8.zip
```
