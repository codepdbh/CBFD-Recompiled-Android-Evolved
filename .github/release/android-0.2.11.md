Necesitas tu propia ROM de **Conker's Bad Fur Day (USA)**. Los APK no incluyen el juego.

## Descargas

| Archivo | Para qué |
|---|---|
| `ConkerRecompiled-Android-v0.2.11-arm64-v8a.apk` | **Recomendado** para teléfonos de 64 bits. |
| `ConkerRecompiled-Android-v0.2.11-armeabi-v7a.apk` | ⚠️ ALPHA experimental de 32 bits, sin mods. |
| `ConkerRecompiled-Mods-v0.2.11.zip` | Mods opcionales para 64 bits: Skip Intro, Skip Any Cutscene y Cheats. Sin cambios. |
| `ConkerRecompiled-TexturasHD-v0.2.11.rtz` | **Pack de texturas HD** (1117 texturas, hasta 1024 px). El mismo de la v0.2.9. |

📖 [Guía de instalación](https://github.com/codepdbh/CBFD-Recompiled-Android-Evolved/blob/master/android/INSTALL.md). Puedes instalarla encima de la versión anterior.

### Instalar las texturas HD

1. Descarga `ConkerRecompiled-TexturasHD-v0.2.11.rtz`.
2. En la pantalla de inicio toca **Mods → Añadir** y elige el archivo, o cópialo a `ConkerRecompiled/mods/`.
3. Comprueba que esté activado en **Mods** y juega.

Pesa 337 MB y usa bastante memoria de la GPU. Si tu teléfono va lento o se cierra con el pack, desactívalo en **Mods**.

## Cambios desde la 0.2.10

- **El preset "Equilibrado" dibuja a 960p (4x)** en vez de a la resolución completa de la pantalla. En el teléfono se ve casi igual, la GPU trabaja menos de la mitad y el teléfono se calienta menos. **"Calidad"** sigue usando la resolución completa de la pantalla con antialiasing.
- **Registro de rendimiento:** cada 5 segundos el informe anota los FPS del juego, los dibujados y los mostrados, y el tiempo de CPU y GPU. Así los informes de lentitud traen datos.

## Sobre los 120 FPS

Conker funciona a 30 cuadros por segundo. Para mostrar 60 o 120, RT64 dibuja cuadros intermedios: a 120 FPS, la GPU dibuja cada cuadro del juego 4 veces. En las pruebas, incluso un teléfono de gama alta (Snapdragon 8 Elite) se calentó tras unos minutos, bajó la velocidad de la GPU y pasó de 120 a 90 y luego a 60 FPS, lo que se siente como tirones.

**Para jugar largo rato, recomendamos 60 FPS:** son estables, el teléfono se calienta menos y gasta menos batería.

## Validación

Ambos APK compilados. Probado en un Samsung S25 (Adreno 830), con y sin el pack HD.

## SHA-256

```
78824bdbea5fe8468f49c99ff5711a74fe870ce475d19a4f5903d491a262f36d  ConkerRecompiled-Android-v0.2.11-arm64-v8a.apk
256e30e9154566792ca7b103f81eea3d4189cc6857cfa6018c9642c177a6be40  ConkerRecompiled-Android-v0.2.11-armeabi-v7a.apk
647147cf27221b41208b4c966c98d998eecd53d6864987e2ef492b494336cb1d  ConkerRecompiled-Mods-v0.2.11.zip
6466be4246e9c8c1f3aa7b0dd2446352c2227a47854af4b9d44142605bd0dd6b  ConkerRecompiled-TexturasHD-v0.2.11.rtz
```
