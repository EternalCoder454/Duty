#!/usr/bin/env bash
# Stage the built mod jars and install them into the test modpack.
#
# Removes any duty-* jar already in the pack before copying, so a renamed or dropped module
# cannot leave a stale jar behind loading alongside its replacement -- which looks exactly
# like a mod bug from in-game.
#
# Build first; this only copies:
#   $env:TMP="C:\gtmp"; $env:TEMP="C:\gtmp"; .\gradlew.bat build
#   bash tools/deploy.sh


# Replacing a jar under a running game is how you get "ZipFile invalid LOC header": the JVM
# reads a file that changed beneath it, and the failure surfaces as a NoClassDefFoundError in
# whichever mod happened to be loading. Refuse rather than corrupt a live session.
if tasklist 2>/dev/null | awk '/javaw\.exe/{found=1} END{exit !found}'; then
    echo "REFUSING TO DEPLOY: Minecraft appears to be running (javaw.exe)."
    echo "Close the game first, or re-run with FORCE=1 if you are sure."
    [ "${FORCE:-0}" = "1" ] || exit 1
    echo "FORCE=1 set; continuing anyway."
fi
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STAGE="$ROOT/install-mods"

# "Eternally Dutified" is the clean pack: Duty, Sodium, Lithium, Sodium Extra, and the handful
# of tools that make testing bearable (spark, a zoom, a keybind search). Nothing else, so a
# measurement is about Duty rather than about whatever else happened to be installed.
#
# The old kitchen-sink pack is still one variable away:
#   DUTY_PACK="/c/.../Eternally Planetary(1)/minecraft" bash tools/deploy.sh
PACK="${DUTY_PACK:-/c/Users/Zachary Smith/AppData/Roaming/PrismLauncher/instances/Eternally Dutified/minecraft}"

# Refuse a pack this branch does not build for.
#
# There is a "Duty Testing 1.21.1" instance in the same folder, and these jars declare
# [26.1.2,26.2) -- dropping them into it fails at load with a dependency error that reads like a
# mod problem rather than a deployment mistake. The checkers already refuse another version's
# Minecraft jar for the same reason; this is that rule applied to the other end of the pipeline.
EXPECTED_MC="$(sed -nE 's/^minecraft_version=(.*)$/\1/p' "$ROOT/gradle.properties" | tr -d '\r')"
PACK_MANIFEST="$(dirname "$PACK")/mmc-pack.json"
if [ -f "$PACK_MANIFEST" ]; then
    PACK_MC="$(python -c "
import json, io, sys
d = json.load(io.open(sys.argv[1], encoding='utf-8'))
for c in d.get('components', []):
    if c.get('uid') == 'net.minecraft':
        print(c.get('version', '')); break
" "$PACK_MANIFEST" 2>/dev/null | tr -d '\r')"
    if [ -n "$PACK_MC" ] && [ "$PACK_MC" != "$EXPECTED_MC" ]; then
        echo "REFUSING TO DEPLOY: this branch builds for Minecraft $EXPECTED_MC,"
        echo "but $(basename "$(dirname "$PACK")") is Minecraft $PACK_MC."
        echo "Check out the matching branch, or set DUTY_PACK to the right instance."
        exit 1
    fi
fi

# Only these ship. duty-framework is nested inside each via JarJar and must not be installed
# separately; duty-annotations and fixerupper-mixin-ap are build-time only.
# Deliberately the four modules, not duty-all. Duty ships two install shapes: these four
# jars, or the single duty-all jar that nests them. They carry the same mod ids, so having
# both present is a duplicate-mod error rather than a double dose of anything. The pack uses
# the separate jars because that is what it already has installed.
MODULES=(duty-memory duty-client duty-fixerupper duty-server duty-essentials duty-innovative)

# Liteminer lives in its own repository next door and depends on duty-framework. It used to be
# installed on every run, because it was forgotten once and that was the fix. The clean pack is
# performance mods only, and a veinminer is not one -- so it is opt-in now:
#   WITH_LITEMINER=1 bash tools/deploy.sh
# The sweep below still removes any liteminer jar unconditionally, so turning this off actually
# takes it out of the pack rather than leaving the last build behind.
if [ "${WITH_LITEMINER:-0}" = "1" ]; then
    LITEMINER_JAR="$(ls "$(dirname "$0")/../../Liteminer/neoforge/versions/26.1.2/build/libs/"*.jar 2>/dev/null     | grep -viE 'sources|javadoc' | head -1)"
else
    LITEMINER_JAR=""
fi
VERSION="0.1.0"

[ -d "$PACK/mods" ] || { echo "No mods folder at: $PACK/mods"; exit 1; }

echo "==> staging into $STAGE"
mkdir -p "$STAGE"
rm -f "$STAGE"/duty-*.jar
for m in "${MODULES[@]}"; do
  src="$ROOT/$m/build/libs/$m-$VERSION.jar"
  [ -f "$src" ] || { echo "  MISSING build output: $src"; exit 1; }
  cp "$src" "$STAGE/"
  echo "  $m-$VERSION.jar  ($(du -k "$src" | cut -f1) KB)"
done

echo "==> removing stale duty jars from the pack"
found=0
for old in "$PACK/mods"/duty-*.jar "$PACK/mods"/liteminer-*.jar; do
  [ -e "$old" ] || continue
  echo "  - $(basename "$old")"
  rm -f "$old"
  found=1
done
[ "$found" -eq 0 ] && echo "  (none)"

echo "==> installing"
status=0
for m in "${MODULES[@]}"; do
  jar="$m-$VERSION.jar"
  cp "$STAGE/$jar" "$PACK/mods/$jar"
  a=$(sha256sum "$STAGE/$jar" | cut -d' ' -f1)
  b=$(sha256sum "$PACK/mods/$jar" | cut -d' ' -f1)
  if [ "$a" = "$b" ]; then
    echo "  OK   $jar"
  else
    echo "  HASH MISMATCH $jar"
    status=1
  fi
done

echo
echo "Installed. After launching, confirm mixins actually applied:"
echo "  grep -c 'from duty_' \"$PACK/logs/debug.log\""
if [ "${WITH_LITEMINER:-0}" != "1" ]; then
    echo "  --   Liteminer not included (WITH_LITEMINER=1 to add it)"
elif [ -n "$LITEMINER_JAR" ] && [ -f "$LITEMINER_JAR" ]; then
    cp "$LITEMINER_JAR" "$PACK/mods/" && echo "  OK   $(basename "$LITEMINER_JAR")"
else
    echo "  --   WITH_LITEMINER=1 but no build found; build it in ../Liteminer first"
fi

exit $status
