# Conker's Bad Fur Day: Recompiled — Android Evolved

Guía de instalación para Android.

> **El juego no viene incluido.** Necesitas tu propia copia de la ROM de
> Conker's Bad Fur Day **de Estados Unidos (USA)** para N64.

## Requisitos

- **Android 11 o superior.**
- **Procesador de 64 bits (ARM64 / arm64-v8a).** Casi todos los teléfonos de los
  últimos años lo son. Para teléfonos de **32 bits (ARMv7)** hay una versión
  **ALPHA experimental** (ver abajo).
- **GPU con Vulkan 1.1.**
- Unos **300 MB libres**: la ROM ocupa 64 MB y el juego guarda una copia verificada.
- Probado en un **Galaxy S25 Ultra** (Snapdragon 8 Elite) y en un **Redmi Note 8**
  (Snapdragon 665, Adreno 610; también la versión de 32 bits). Los teléfonos con
  **MediaTek Helio/Dimensity (GPU Mali)** deberían funcionar, pero todavía no se
  han probado.

## Qué descargar

De la página de [Releases](https://github.com/codepdbh/CBFD-Recompiled-Android-Evolved/releases):

| Archivo | Para qué |
|---|---|
| `ConkerRecompiled-Android-vX.Y.Z-arm64-v8a.apk` | El juego para teléfonos de 64 bits. **Usa esta.** |
| `ConkerRecompiled-Android-vX.Y.Z-armeabi-v7a.apk` | **ALPHA** para teléfonos de 32 bits. Ver abajo. |
| `ConkerRecompiled-Mods-vX.Y.Z.zip` | Mods opcionales: Skip Intro, Skip Any Cutscene y Cheats. |

**¿Cuál descargo?** Casi siempre la **arm64-v8a**. Si no sabes cuál es tu
teléfono, instala una app como *CPU-Z* y mira "Instruction set" o "Kernel
Architecture": si dice `arm64`/`aarch64`, usa arm64-v8a.

### Versión de 32 bits (ALPHA)

La APK `armeabi-v7a` es **experimental y no se ha probado en ningún teléfono**:
puede ir muy lenta, verse mal o cerrarse. Además:

- **No tiene mods**: el sistema de mods necesita un procesador de 64 bits.
- Necesita igualmente **Android 11 y Vulkan 1.1**, que pocos teléfonos de 32 bits tienen.
- Si tu teléfono es de 64 bits, **no la uses**: la arm64-v8a es mejor en todo.

## Instalación

1. **Descarga la APK** en el teléfono.
2. **Ábrela e instálala.** Si Android lo pide, permite instalar apps desde esa
   fuente: *Ajustes → Aplicaciones → Acceso especial → Instalar apps desconocidas*,
   y activa el navegador o el gestor de archivos que usaste.
3. **Abre "Conker Recompiled".** La primera vez te pide **"Permitir acceso a todos
   los archivos"**: actívalo. El juego lo necesita para guardar todo en la
   carpeta `ConkerRecompiled` de tu memoria interna.
4. En la pantalla de inicio, toca **Elegir ROM** y selecciona tu ROM
   (`.z64`, `.n64` o `.v64`). Se copia sola a la carpeta del juego.
5. Toca **▶ JUGAR**.

La primera vez que se ve cada efecto o escena puede haber una pausa breve mientras
se preparan los gráficos. Es normal y no vuelve a pasar en esa escena.

## La ROM

- Tiene que ser **Conker's Bad Fur Day (USA)**. La versión europea (PAL) y otras
  regiones no funcionan.
- Los parches de ROM que solo cambian gráficos, audio o texto (por ejemplo, la
  versión sin censura) funcionan en la versión de PC; en Android todavía no se han
  probado.
- No hace falta copiar la ROM a mano: **Elegir ROM** lo hace por ti. Si prefieres
  copiarla tú, ponla como `ConkerRecompiled/rom.z64` y abre el juego.

## Qué hay en la carpeta del juego

Todo queda en **`Memoria interna/ConkerRecompiled/`**
(`/storage/emulated/0/ConkerRecompiled/`):

```
ConkerRecompiled/
├── rom.z64                  ← la ROM que elegiste (el juego la verifica y guarda una copia)
├── conker.n64.us.1.0.z64    ← la copia verificada que usa el juego
├── mods/                    ← aquí van los mods (.nrm) y los packs de texturas (.rtz)
├── saves/                   ← tus partidas guardadas
│   └── copias/              ← las "copias de partida" del menú de pausa
├── general.json, graphics.json, sound.json, controls.json   ← ajustes
├── touch_layout.json        ← posición y tamaño de los botones táctiles
└── assets/, .rt64/, ...     ← archivos internos del juego (no los toques)
```

**Para hacer una copia de seguridad de tus partidas,** copia la carpeta
`ConkerRecompiled/saves` a otro lugar.

## Mods (opcional)

Los mods son archivos `.nrm`, y los packs de texturas, `.rtz`. Dos formas de instalarlos:

- **Desde el juego (recomendado):** en la pantalla de inicio toca **Mods →
  Añadir** y selecciona los archivos. Se activan solos.
- **A mano:** descomprime `ConkerRecompiled-Mods-vX.Y.Z.zip` y copia los `.nrm` a
  **`ConkerRecompiled/mods/`**. Luego actívalos en **Mods** en la pantalla de inicio.

Mods incluidos:

- **Skip Intro:** empieza directo en el menú, sin los avisos, logos ni la
  escena de la motosierra.
- **Skip Any Cutscene:** L salta cualquier cinemática, aunque no la hayas visto antes.
- **Cheats:** vida infinita, vidas infinitas y dinero al máximo. Cada uno se
  activa en sus opciones: menú de pausa → Menú avanzado → Mods → Configure.

### Texturas HD

Los packs de texturas de RT64 (`.rtz`) se instalan como cualquier mod. Los packs
hechos para GLideN64 (un archivo `.htc`) se convierten en un PC con Python
(`pip install pillow`):

```
python tools/htc_to_rtz.py CONKER.BFD_HIRESTEXTURES.htc conker_hires_textures.rtz --max-size 1024
```

`--max-size` reduce las texturas más grandes: los teléfonos tienen mucha menos
memoria para texturas que un PC. Usa `--max-size 0` para dejarlas intactas.

## Controles

- **Botones táctiles:** aparecen solos. Toca **✎** (arriba al centro) para moverlos
  y cambiarles el tamaño. **💾** abre las copias de partida y **☰** el menú de pausa.
- **Mandos Bluetooth o USB** (Xbox, PlayStation, 8BitDo…): se reconocen solos y
  los botones táctiles se ocultan mientras hay uno conectado.
- **Teclado:** funciona con los controles de PC (WASD para moverte, Espacio = A…).
- Para **remapear** un mando o el teclado: menú de pausa → **Menú avanzado**.
- **Apuntar moviendo el teléfono:** mantén R para mirar y gira el teléfono.
  Se ajusta en **Ajustes**.

## Menú de pausa (☰)

Continuar, copias de partida, mover los botones, menú avanzado y salir al inicio,
más ajustes rápidos: calidad gráfica, cuadros por segundo, volumen, transparencia
de los botones, giroscopio y vibración.

**Copias de partida:** guardan tu **último guardado del juego** (el del último
checkpoint) en uno de 3 espacios, y al cargar una el juego se reinicia en ella.
No son estados instantáneos como en un emulador: este port no se puede congelar
en un segundo cualquiera.

## Si algo va mal

**Envía un informe:** después de un cierre o un error, abre la app y en la
pantalla de inicio toca **"📋 ¿Problemas? Enviar el informe del último juego"**.
Puedes mandarlo **por correo** o como **issue en GitHub**: el informe se copia solo
y solo tienes que pegarlo. Incluye tu modelo de teléfono y lo que pasó en el
último juego, no tu ROM ni tus partidas. También está en
`ConkerRecompiled/logs/game.log`.

- **Va lento o se calienta:** en **Ajustes** elige calidad **Fluido** y
  **30 o 60 cuadros por segundo**.
- **"Error de la GPU":** el juego ofrece reiniciar desde tu último guardado.
  Si pasa seguido, baja la calidad gráfica.
- **Grabar la pantalla:** funciona en teléfonos que permiten a los juegos usar la
  GPU con prioridad alta, como los Snapdragon recientes. En otros, grabar puede
  causar el "Error de la GPU" de arriba.
- **No encuentra la ROM:** comprueba que sea la versión **USA** y vuelve a
  elegirla con **Elegir ROM**.

## Desinstalar

Al desinstalar la app, la carpeta `ConkerRecompiled` **no se borra**: tus partidas
y ajustes siguen ahí. Bórrala a mano si ya no la quieres.
