# Agua y correcciones gráficas — Android 0.2.7

## Resultado

Se reprodujeron los cuadros y franjas del agua en el Samsung SM-S938B conectado (Adreno 830, Android 16). El defecto desaparecía sin MSAA, incluso a 120 FPS, y reaparecía con MSAA2X/4X. La revisión que preserva la profundidad con LOAD/STORE y utiliza posiciones estándar de MSAA fue confirmada en el teléfono por el usuario, con MSAA2X a 120 FPS.

La ROM original, la ausencia de mods y desactivar MSAA fueron pruebas independientes anteriores comunicadas por el usuario. Las pruebas actuales se realizaron sobre su instalación existente; no demuestran que todas las causas gráficas en otros dispositivos estén resueltas.

## Implementación

- `recomp/plume.patch`: Android conserva la profundidad en los pases de lectura (LOAD/STORE en vez de NONE). Los drivers móviles pueden corromper la profundidad MSAA en esa ruta de NONE.
- `recomp/rt64.patch`: Android genera MSAA con el número de muestras solicitado y posiciones estándar del driver.
- Plume conserva además el patrón personalizado de profundidad en VkImageMemoryBarrier y VkRenderPassBeginInfo. La especificación indica que omitir el patrón en las transiciones deja el contenido indefinido: https://docs.vulkan.org/spec/latest/chapters/synchronization.html#synchronization-image-layout-transitions
- Diagnósticos permanentes: soporte de dual-source blending y máscaras de muestras MSAA de color, profundidad y posiciones personalizadas.
- Se retiró el volcado temporal de estados de dibujos utilizado para el diagnóstico.

La corrección de posiciones personalizadas por sí sola y la prueba con posiciones estándar sin LOAD/STORE no eliminaron todo el defecto; la validación satisfactoria corresponde a la combinación final.

## Upstream

Se incorporaron únicamente los cambios gráficos de los siguientes commits de sciaschi/CBFD-Recompiled, conservando las modificaciones Android:

- `5ba037e2f0e588e9ce931c7ca90eb7863e2ce459`: generación de coordenadas de reflexión del microcódigo CBFD antes de reemplazar los normales con iluminación.
- `03ae2e56831b892060c18f286ebff0bee7e865f7`: luces puntuales calculadas después de transformar el vértice al espacio de clip.

No se atribuye a estas correcciones upstream el arreglo del agua. No se realizó un merge completo.

## Validación y datos

- Compilación completa de los APK ARM64 y ARMv7.
- Parches comprobados sobre un índice limpio de los commits originales de las dependencias.
- Prueba de hardware de la escena inicial del agua con MSAA2X y 120 FPS; confirmación del usuario tras mover la cámara/recorrer la zona.
- El teléfono anuncia máscaras 0x7 para muestras de color, profundidad y posiciones personalizadas (1, 2 y 4 muestras).
- Copias previas de partidas y ajustes, capturas y logs de las pruebas: `host/build-android/water-test/` (ignorado por Git). Las partidas no se publican.
- Se mantienen los ajustes originales del teléfono: MSAA2X y 120 FPS. Instalaciones con `adb install -r`, sin borrar datos.
- Versiones Android: 0.2.7, código base 9; APK ARM64 92, ARMv7 91.
- ARMv7 continúa siendo ALPHA sin mods. Falta comprobar el comportamiento en los Mali y Adreno de los informes originales.
