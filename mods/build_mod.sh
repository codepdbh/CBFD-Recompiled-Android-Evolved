#!/bin/sh
# Builds a mod into its .nrm file. Run on Linux, macOS or in WSL, from the repo root:
#
#   sh mods/build_mod.sh mods/skip_cutscenes
#
# Compiles the mod's src/*.c for MIPS with clang, links with ld.lld as the mod template
# does (or GNU ld, mips-linux-gnu-ld, where there's no lld), then runs
# N64Recomp's RecompModTool with the mod's mod.toml. The .nrm ends up in the mod's
# build/ folder; copy it into the game's mods folder (the launcher's Mods menu can
# open it) to install it. mods/syms/ (committed) must match the game build
# (recomp/run.sh regenerates it).
set -e
MOD_DIR=$1
ROOT=$(pwd)
# A clang with the MIPS target. Apple's has none: on macOS use Homebrew's LLVM
# (brew install llvm), or name one with CLANG=.
if [ -z "$CLANG" ]; then
    CLANG=clang
    if [ "$(uname -s)" = Darwin ] && command -v brew >/dev/null 2>&1; then
        CLANG="$(brew --prefix)/opt/llvm/bin/clang"
    fi
fi
CFLAGS="-target mips -mips2 -mabi=32 -O2 -G0 -mno-abicalls -mno-odd-spreg -mno-check-zero-division \
    -fomit-frame-pointer -ffast-math -fno-unsafe-math-optimizations -fno-builtin-memset -funsigned-char \
    -fno-builtin-sinf -fno-builtin-cosf -ffunction-sections -nostdinc -D_LANGUAGE_C -DMIPS \
    -Wall -Wno-incompatible-library-redeclaration -Wno-unused-parameter -Wno-unknown-pragmas \
    -Werror=section -Wno-visibility \
    -I mods/include -I conker/conker/include/2.0L -I conker/conker/include/2.0L/PR"

# ld.lld, as GNU ld refuses a call to a game function (a jal to an address outside the
# mod's 256 MB region, as the symbol is left unresolved for RecompModTool): Skip Intro
# makes one. Name another linker with LD=.
if [ -z "$LD" ]; then
    LD=mips-linux-gnu-ld
    for lld in ld.lld "$(dirname "$CLANG")/ld.lld"; do
        if command -v "$lld" >/dev/null 2>&1; then LD=$lld; break; fi
    done
    if [ "$(uname -s)" = Darwin ] && command -v brew >/dev/null 2>&1 && [ -x "$(brew --prefix)/opt/lld/bin/ld.lld" ]; then
        LD="$(brew --prefix)/opt/lld/bin/ld.lld"
    fi
fi
case "$LD" in
    *lld*) LDFLAGS=--no-nmagic ;;
    *) LDFLAGS= ;;
esac

mkdir -p "$MOD_DIR/build"
OBJS=""
for src in "$MOD_DIR"/src/*.c; do
    obj="$MOD_DIR/build/$(basename "${src%.c}").o"
    "$CLANG" $CFLAGS -c "$src" -o "$obj"
    OBJS="$OBJS $obj"
done
"$LD" $OBJS -nostdlib -T mods/mod.ld -Map "$MOD_DIR/build/mod.map" \
    --unresolved-symbols=ignore-all --emit-relocs -e 0 -gc-sections $LDFLAGS -o "$MOD_DIR/build/mod.elf"
cd "$MOD_DIR"
"$ROOT/tools/N64Recomp/build/RecompModTool" mod.toml build
ls -la build/*.nrm
