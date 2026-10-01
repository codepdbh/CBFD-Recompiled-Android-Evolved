# Compatibilidad Android — revisión del 1 de octubre de 2026

## Evidencia disponible

| Equipo | Evidencia | Estado del diagnóstico |
|---|---|---|
| Samsung SM-S928U, Adreno 750, Android 16, 0.2.5 (72) | Cinco errores `vkCreateComputePipelines`, resultado -13; SIGSEGV dentro de `vkCmdBindPipeline` | El código permitía enlazar pipelines con handle nulo. La causa de los errores de compilación requiere nuevos logs con nombres. |
| Samsung SM-A035M, Unisoc T616, Android 13, 0.2.5 (72) | Reintentos `mali-cmar-backe: issue_work() failed` | Problema al enviar trabajo al driver; el fragmento no permite determinar su origen. |
| realme RMX3834, Mali-G57, Android 13, 0.2.5 (72) | Log termina tras inicializar el heap | Falta el fallo, tombstone o evidencia de que Android terminó el proceso. |
| Xiaomi 23100RN82L, Mali-G52, Android 15, 0.2.4 (62) | Sin descriptor indexing; copias de framebuffer de 8/16 bits desactivadas; log termina en el heap | Falta el fallo. La ROM modificada y los mods son variables para una prueba controlada, no causas demostradas. |

## Cambios aplicados

- Vulkan informa la validez de sus pipelines. La biblioteca valida los pipelines que crea y registra su nombre al fallar; nunca se envía un pipeline inválido a `vkCmdBindPipeline`.
- También se comprueban los handles de módulos shader y layouts antes de crear pipelines de cómputo y gráficos.
- En Android, si la preparación de shaders falla con color de alta precisión o MSAA, se reintenta una vez con color estándar y sin MSAA. Si también falla, se libera el renderizador y se devuelve el error de inicialización existente.
- Los cambios de MSAA conservan los once registros de shaders anteriores hasta completar los reemplazos. Si falla la creación, se restaura la configuración y se prepara de nuevo la caché para el MSAA anterior.
- Los shaders exclusivos de ray tracing se crean únicamente cuando esa ruta está compilada y la GPU la soporta. Incluye composición, cinco filtros/histogramas y posprocesado/depuración; no se usan en la ruta raster de Android.
- Descriptor indexing requiere ahora las cinco funciones que usa la ruta: parcialmente enlazados, conteo variable, arrays en tiempo de ejecución, actualización posterior al enlace de imágenes muestreadas e indexación no uniforme de imágenes muestreadas.
- Los límites de texturas se calculan con las funciones realmente disponibles. El rango máximo queda limitado a 8192, el tamaño de la tabla de RT64; no representa una mejora de compatibilidad con acceso no uniforme en GPUs que no lo soportan.
- Descriptor indexing y scalar block layout también se detectan como funciones del núcleo de Vulkan 1.2, aunque no se anuncie la extensión correspondiente.
- Mali y otros proveedores usan prioridad normal. Se conserva la solicitud de prioridad alta para Qualcomm y su reintento con prioridad normal, como solución existente para grabación de pantalla.
- VMA recibe la menor versión entre la solicitada por la aplicación y la disponible en el dispositivo.
- El color de alta precisión automático requiere soporte del formato RGBA16 UNORM para muestreo, filtrado lineal, almacenamiento, attachment de color y blending; no se decide solamente por el tamaño del heap GPU.
- Los logs Android incluyen versión Vulkan, driver, scalar layout, indexación no uniforme y lecturas/escrituras de almacenamiento sin formato.

## Validación local

- Compilación y enlace completos de `ConkerRecomp` para ARM64 con el NDK 28.2.
- Compilación de los archivos afectados de Vulkan, aplicación y biblioteca de shaders para ARMv7.
- Prueba nativa con fallos simulados, usando las funciones reales de validación y reemplazo de MSAA: rechazo de pipelines nulos e inválidos, aceptación de pipelines válidos, restauración de los once registros tras un fallo parcial y confirmación de reemplazos exitosos.
- Los cambios pasan `git diff --check`. La compilación muestra avisos existentes sobre opciones de warnings y SSE2NEON.

La publicación Android 0.2.6 incorpora estos cambios y códigos de versión 82 (ARM64) y 81 (ARMv7). No se probó el juego en los equipos afectados. Los cambios endurecen el arranque y reducen requisitos innecesarios; no prueban que los cinco errores del Adreno hayan desaparecido.

Los parches `recomp/rt64.patch` y `recomp/plume.patch` contienen la implementación completa. Se comprobó que aplican sobre los commits originales de los submódulos con un índice Git temporal limpio. El parche RT64 también incluye las tres fuentes auxiliares que la compilación ya utilizaba localmente, pero que antes no estaban incluidas en el parche.

## Pruebas en dispositivos

1. Compilar un APK de prueba identificado como una compilación distinta de 0.2.5 (72), conservando su biblioteca sin quitar símbolos y su Build ID.
2. Probar primero con ROM US original, mods desactivados, color estándar, MSAA desactivado y resolución nativa. Después habilitar cada opción por separado.
3. En el S24 Ultra, recoger el informe completo desde el inicio para identificar el primer pipeline que falle, si todavía falla.
4. En A03 y realme, comprobar arranque y juego con prioridad normal; registrar si siguen los reintentos CMAR. Esto evalúa una hipótesis, no una solución confirmada.
5. En Mali-G52, revisar tanto el arranque como efectos que dependan de copias de framebuffer; la ruta existente sigue omitiendo algunas copias de 8/16 bits.
6. Si el log vuelve a terminar en el heap, recoger `adb logcat -b all` durante el arranque, incluyendo `AndroidRuntime`, `libc`, tombstones y mensajes de terminación por memoria de Android.

Los offsets de los crashes anteriores no deben resolverse con la biblioteca recién compilada: se necesita la biblioteca exacta del APK que produjo cada reporte.
