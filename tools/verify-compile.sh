#!/usr/bin/env bash
# Compile-check Duty without Gradle.
#
# Gradle cannot start on this machine (see tools/SelectorLoopbackCheck.java and the
# README). This script gets the same compile-time answer by building a classpath out
# of what Gradle already downloaded, and running javac directly.
#
# It checks that the code compiles and that every Minecraft symbol it names really
# exists in 26.1.2. It does NOT run mixin's annotation processor, so it cannot confirm
# an injection point will resolve at runtime. To check a mixin target by hand:
#   javap -p -cp "$TMPDIR/duty-verify/cp/minecraft.jar" <fully.qualified.Class>
#
# Usage:  bash tools/verify-compile.sh [module ...]      (default: all)

set -uo pipefail

GRADLE_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
JDK="${JAVA_HOME:-/c/Program Files/Java/jdk-25.0.4}"
JAVAC="$JDK/bin/javac.exe"
WORK="${TMPDIR:-/tmp}/duty-verify"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

MODULES=("$@")
# duty-fixerupper is excluded by default. It needs three things this script cannot
# provide: NeoForge's access transformer applied to the Minecraft jar, the mixin-config
# annotation processor, and a dozen compile-only mod jars from CurseForge. Pass it
# explicitly if you want to see how far it gets.
[ ${#MODULES[@]} -eq 0 ] && MODULES=(duty-memory duty-client)

[ -x "$JAVAC" ] || { echo "javac not found at $JAVAC -- set JAVA_HOME"; exit 1; }

echo "==> collecting classpath from $GRADLE_HOME"
rm -rf "$WORK/cp" && mkdir -p "$WORK/cp"

MC=$(ls -t "$GRADLE_HOME"/caches/neoformruntime/intermediate_results/recompile_*_output.jar 2>/dev/null | head -1)
[ -n "$MC" ] || { echo "No recompiled Minecraft jar in the NeoForm cache."; echo "Run a Gradle build once (on a machine where Gradle works) to populate it."; exit 1; }
cp "$MC" "$WORK/cp/minecraft.jar"
echo "    minecraft: $(basename "$MC")"

M2="$GRADLE_HOME/caches/modules-2/files-2.1"

# Newest version of one artifact. Matched on the artifact directory rather than the
# file name: a glob like "asm-*.jar" also swallows asm-tree and asm-analysis, and
# then version-sorting silently drops the ones actually needed.
take_newest() {
  local artifact="$1"
  local newest
  newest=$(find "$M2" -type d -name "$artifact" 2>/dev/null |
             while read -r d; do
               find "$d" -name "$artifact-*.jar" 2>/dev/null | grep -viE "sources|javadoc"
             done | sort -V | tail -1)
  if [ -n "$newest" ]; then
    cp -f "$newest" "$WORK/cp/"
  else
    echo "    warning: $artifact not in cache"
  fi
}

for artifact in sponge-mixin mixinextras-neoforge bus loader \
                asm asm-tree asm-analysis asm-commons asm-util \
                guava log4j-api joml fastutil datafixerupper brigadier authlib \
                gson commons-lang3 slf4j-api annotations oshi-core logging; do
  take_newest "$artifact"
done

# NeoForge itself: the universal jar of the highest version present.
find "$M2/net.neoforged/neoforge" -name "neoforge-*-universal.jar" 2>/dev/null |
  sort -V | tail -1 | while read -r f; do cp -f "$f" "$WORK/cp/"; done

echo "    $(ls "$WORK/cp" | wc -l) jars"
for required in minecraft.jar sponge-mixin asm-tree; do
  ls "$WORK"/cp/ | grep -q "^$required" || { echo "    missing required: $required"; exit 1; }
done

# Dist lives in a ModDevGradle-synthesized artifact that never lands in the cache.
mkdir -p "$WORK/stub/net/neoforged/api/distmarker"
cat > "$WORK/stub/net/neoforged/api/distmarker/Dist.java" <<'JAVA'
package net.neoforged.api.distmarker;
/** Stub for offline compile checks; ModDevGradle supplies the real one. */
public enum Dist { CLIENT, DEDICATED_SERVER }
JAVA

# javac here is a Windows binary, so every path handed to it has to be a Windows path.
# cygpath is the only reliable translator: a hand-rolled /c/ -> C:/ rule silently fails
# for Git Bash's virtual mounts such as /tmp, which then produces an empty classpath
# and a confusing wall of "package does not exist" errors.
if command -v cygpath >/dev/null 2>&1; then
  to_win() { cygpath -w "$1"; }
else
  to_win() { echo "$1" | sed 's|^/\([a-z]\)/|\1:/|'; }
fi
CP=$(ls "$WORK"/cp/*.jar | while read -r j; do to_win "$j"; done | tr '\n' ';')

# Files that cannot compile here for a known, non-code reason: they need either an
# access transformer applied to the Minecraft jar, or an optional mod jar that only
# Gradle pulls in. Listing them by name means a genuine new breakage still shows up,
# instead of being lost in an expected wall of errors.
EXPECTED_FAILURES="
net/dutymod/client/mixin/obe/blockentity/campfire/CampfireBlockEntityMixin
net/dutymod/client/mixin/obe/blockentity/skull/SkullBlockEntityMixin
net/dutymod/client/obe/model/BlockEntityStateModel
net/dutymod/client/mixin/obe/blockentity/compat/lootr/LootrChestBlockEntityMixin
net/dutymod/client/mixin/obe/renderer/compat/sodium/ChunkBuilderMeshingTaskMixin
net/dutymod/client/mixin/obe/renderer/compat/sodium/SodiumWorldRendererMixin
net/dutymod/client/obe/compat/emf/EMFCompat
net/dutymod/client/obe/compat/iris/IrisCompat
net/dutymod/client/obe/compat/lootr/LootrCompat
"

status=0
for module in "${MODULES[@]}"; do
  [ -d "$ROOT/$module/src/main/java" ] || { echo "==> $module: no sources, skipping"; continue; }
  echo "==> compiling $module"
  rm -rf "$WORK/src" "$WORK/out" && mkdir -p "$WORK/src" "$WORK/out"
  cp -r "$ROOT/duty-framework/src/main/java/." "$WORK/src/"
  cp -r "$ROOT/$module/src/main/java/." "$WORK/src/"
  "$JAVAC" -d "$WORK/out" -nowarn -proc:none \
        -cp "$CP" -sourcepath "$(to_win "$WORK/stub")" \
        $(find "$WORK/src" -name "*.java") > "$WORK/errors.txt" 2>&1
  rc=$?

  if [ "$rc" -eq 0 ]; then
    echo "    OK"
    continue
  fi

  # Which source files did javac actually complain about?
  failed=$(grep "error:" "$WORK/errors.txt" |
             sed -E 's|.*[\\/]src[\\/]||; s|\.java.*||' | tr '\\' '/' | sort -u)
  unexpected=""
  for f in $failed; do
    echo "$EXPECTED_FAILURES" | grep -qx "$f" || unexpected="$unexpected $f"
  done

  if [ -z "$unexpected" ]; then
    echo "    OK apart from $(echo "$failed" | wc -l) files that need Gradle"
    echo "       (access transformer, or an optional mod jar -- see EXPECTED_FAILURES)"
  else
    echo "    FAILED"
    for f in $unexpected; do echo "       $f"; done
    grep "error:" "$WORK/errors.txt" | grep -F "$(echo "$unexpected" | awk '{print $1}')" | head -5
    status=1
  fi
done

exit $status
