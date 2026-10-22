#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
GAME_JAR="${1:-libs/Mindustry.jar}"
if [ ! -f "$GAME_JAR" ]; then
  mkdir -p libs
  echo "Downloading Mindustry v160.7..."
  curl -L -o libs/Mindustry.jar https://github.com/Anuken/Mindustry/releases/download/v160.7/Mindustry.jar
  GAME_JAR=libs/Mindustry.jar
fi
rm -rf build/classes && mkdir -p build/classes
javac -J-Duser.language=en --release 17 -nowarn -cp "$GAME_JAR" -d build/classes src/roguedustry/*.java
jar cf build/Roguedustry.jar mod.hjson sprites -C build/classes .
echo "Built build/Roguedustry.jar"
