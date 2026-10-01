El juego no viene incluido: necesitas tu propia ROM de **Conker's Bad Fur Day (USA)**.

## Descargas

| Archivo | Para quÃ© |
|---|---|
| `ConkerRecompiled-Android-v0.2.6-arm64-v8a.apk` | VersiÃ³n recomendada para telÃ©fonos de 64 bits. |
| `ConkerRecompiled-Android-v0.2.6-armeabi-v7a.apk` | ALPHA experimental para telÃ©fonos de 32 bits, sin mods. |
| `ConkerRecompiled-Mods-v0.2.6.zip` | Mods opcionales para 64 bits: Skip Intro, Skip Any Cutscene y Cheats. Mismos mods de 0.2.5. |

[GuÃ­a de instalaciÃ³n](https://github.com/codepdbh/CBFD-Recompiled-Android-Evolved/blob/main/android/INSTALL.md).

## Cambios desde 0.2.5

- Se detectan los pipelines Vulkan invÃ¡lidos antes de enviarlos al driver. Los informes identifican el pipeline que fallÃ³ y el resultado de Vulkan.
- Si los shaders fallan con color de alta precisiÃ³n o MSAA, se reintenta el arranque con color estÃ¡ndar y sin MSAA.
- Un cambio de MSAA rechazado por el driver conserva los shaders y la configuraciÃ³n anteriores.
- Android deja de crear pipelines exclusivos de ray tracing que no utiliza.
- Se comprueban todas las funciones necesarias de descriptor indexing y el soporte del formato de color de alta precisiÃ³n. TambiÃ©n se reconocen funciones incorporadas en Vulkan 1.2.
- Mali utiliza colas de prioridad normal. Se conserva el ajuste existente de Qualcomm para grabaciÃ³n de pantalla.
- Los informes incluyen mÃ¡s datos del driver y de las funciones de Vulkan.
- Los parches de RT64 incluyen las fuentes auxiliares necesarias para compilar desde una copia limpia del repositorio.

## ValidaciÃ³n y estado

CompilaciÃ³n de ambos APK y pruebas de manejo de errores con fallos simulados. **TodavÃ­a falta confirmar el arranque y el juego en los Samsung, realme y Xiaomi de los reportes.** Esta versiÃ³n no garantiza corregir todos los fallos de sus drivers.

Si sigue fallando, comparte el informe desde la pantalla de inicio. Si el informe termina al inicializar el heap, un log completo de `adb logcat -b all` puede mostrar el motivo del cierre.

## SHA-256

```
16c7fdff08a615a79978a3b4417802c89fcf4150394a6f3225ee01c49ba52c20  ConkerRecompiled-Android-v0.2.6-arm64-v8a.apk
2ff5d96e99348105db265a7424602c49e1e14774cf7f0c2b6fb07d1269c5821b  ConkerRecompiled-Android-v0.2.6-armeabi-v7a.apk
647147cf27221b41208b4c966c98d998eecd53d6864987e2ef492b494336cb1d  ConkerRecompiled-Mods-v0.2.6.zip
```
