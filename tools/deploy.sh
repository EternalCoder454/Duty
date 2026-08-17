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
PACK="${DUTY_PACK:-/c/Users/Zachary Smith/AppData/Roaming/PrismLauncher/instances/Eternally Planetary(1)/minecraft}"

# Only these ship. duty-core is nested inside each via JarJar and must not be installed
# separately; duty-annotations and fixerupper-mixin-ap are build-time only.
# Deliberately the four modules, not duty-all. Duty ships two install shapes: these four
# jars, or the single duty-all jar that nests them. They carry the same mod ids, so having
# both present is a duplicate-mod error rather than a double dose of anything. The pack uses
# the separate jars because that is what it already has installed.
MODULES=(duty-memory duty-client duty-fixerupper duty-server duty-essentials)
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
for old in "$PACK/mods"/duty-*.jar; do
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
exit $status
