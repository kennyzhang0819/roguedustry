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
cp build/Roguedustry.jar "$data/mods/"
echo "Installed into $data/mods"
java -Duser.language=en -Duser.country=US -jar libs/Mindustry.jar
