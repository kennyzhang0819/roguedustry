#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
./build.sh
if [ -n "$APPDATA" ]; then
  data="$APPDATA/Mindustry"
else
  data="${XDG_DATA_HOME:-$HOME/.local/share}/Mindustry"
fi
mkdir -p "$data/mods"
rm -f "$data/mods/Roguedustry.jar"
cp build/MindustrySurvivors.jar "$data/mods/"
echo "Installed into $data/mods"
SURVIVORS_DEV=1 SURVIVORS_NOMUSIC=1 SURVIVORS_QUIET=1 java -Duser.language=en -Duser.country=US -jar libs/Mindustry.jar
