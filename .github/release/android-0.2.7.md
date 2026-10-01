Necesitas tu propia ROM de **Conker's Bad Fur Day (USA)**. Los APK no incluyen el juego.

## Descargas

- **arm64-v8a**: recomendado para teléfonos de 64 bits.
- **armeabi-v7a**: ALPHA experimental de 32 bits, sin mods.
- **Mods**: Skip Intro, Skip Any Cutscene y Cheats para 64 bits. Sin cambios respecto a 0.2.6.

## Cambios desde 0.2.6

- Se conserva la profundidad al pasar por los dibujos de transparencias y decals en Android. Corrige los cuadros y franjas del agua reproducidos con MSAA en el Samsung de prueba.
- Android utiliza las posiciones estándar de MSAA del driver; mantiene el antialiasing y las opciones de FPS.
- Vulkan conserva el estado de posiciones personalizadas de profundidad en las barreras y los render passes para las plataformas que las utilizan.
- Se incorporan dos correcciones de [upstream](https://github.com/sciaschi/CBFD-Recompiled): generación de coordenadas para superficies reflectantes y cálculo de luces puntuales de Conker.
- Los informes incluyen soporte de blending de doble fuente y límites de muestras MSAA.
- Se mantienen las mejoras de compatibilidad y protección frente a pipelines inválidos de 0.2.6.

## Validación

Ambos APK compilados. Agua comprobada en Samsung SM-S938B / Adreno 830 / Android 16, con MSAA2X a 120 FPS, después de reproducir el defecto. La corrección fue confirmada en el teléfono por el usuario. Sigue pendiente verificarla en otras GPU; el APK de 32 bits continúa siendo experimental.

[Guía de instalación](https://github.com/codepdbh/CBFD-Recompiled-Android-Evolved/blob/main/android/INSTALL.md). Puedes instalar la actualización encima de la versión anterior.

Los hashes de los archivos están en `SHA256SUMS.txt`.
