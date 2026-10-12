Necesitas tu propia ROM de **Conker's Bad Fur Day (USA)**. Los APK no incluyen el juego.

## Descargas

| Archivo | Para qué |
|---|---|
| `ConkerRecompiled-Android-v0.2.13-arm64-v8a.apk` | **Recomendado** para teléfonos de 64 bits. |
| `ConkerRecompiled-Android-v0.2.13-armeabi-v7a.apk` | ⚠️ ALPHA experimental de 32 bits, sin mods. |
| `ConkerRecompiled-Mods-v0.2.13.zip` | Mods opcionales para 64 bits: Skip Intro, Skip Any Cutscene y Cheats. Sin cambios. |

El **pack de texturas HD** no cambió: descárgalo de la [v0.2.11](https://github.com/codepdbh/CBFD-Recompiled-Android-Evolved/releases/tag/android-v0.2.11).

📖 [Guía de instalación](https://github.com/codepdbh/CBFD-Recompiled-Android-Evolved/blob/master/android/INSTALL.md). Puedes instalarla encima de la versión anterior.

## Cambios desde la 0.2.12

- **Adreno 6xx con driver 512.530** (por ejemplo, moto g84 / Adreno 619): el driver no puede crear el "ubershader" del renderizador, y el juego se cerraba al llegar al título. Ahora usa solo los shaders específicos de cada efecto, esperando a que estén listos; si alguno no se puede crear, ese dibujo se omite en vez de cerrar el juego.
- **El preset "Equilibrado" se adapta a la GPU:** 960p en GPU potentes (Adreno 640 o superior, Mali-G6xx/G7xx, Immortalis, Xclipse) y 480p en las demás. En un Redmi Note 8 (Adreno 610), 960p costaba 50-65 ms por cuadro (11-14 FPS); 480p, unos 6 ms.
- **Informes:** dicen qué GPU detectó la app y qué resolución eligió. Tras perder la GPU, el error de consultas ya no se repite cientos de veces.

## Problemas conocidos

- **Adreno 610/619 con drivers antiguos:** el menú de pausa muestra el fondo con colores corruptos. Estos drivers se cierran al copiar la imagen a la memoria del juego, así que esa copia se omite.
- **Mali-G52:** en escenas con más de 224 texturas, algunas se ven incorrectas, y en algunos teléfonos todavía puede perderse la GPU.

## Validación

Ambos APK compilados. Probado en un Redmi Note 8 (Adreno 610): arranca y juega a 30-44 FPS en 480p. También se simuló un ubershader que no se puede crear, y el juego dibujó bien con los shaders específicos.

## SHA-256

```
eb02d39dbf4a0db2d86bac891a0fbed488e698eb6cd730b8ec1be1728bf22f79  ConkerRecompiled-Android-v0.2.13-arm64-v8a.apk
3f70ef164a2333995b8a1781b58a1331bb369d30bec882bcf5cecfb869be4a1b  ConkerRecompiled-Android-v0.2.13-armeabi-v7a.apk
647147cf27221b41208b4c966c98d998eecd53d6864987e2ef492b494336cb1d  ConkerRecompiled-Mods-v0.2.13.zip
```
