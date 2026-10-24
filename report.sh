#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
GAME_JAR="${1:-libs/Mindustry.jar}"
SEP=":"; case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";";; esac
rm -rf build/tools && mkdir -p build/tools
javac -J-Duser.language=en --release 17 -nowarn -cp "$GAME_JAR" -d build/tools src/mindustrysurvivors/*.java tools/RunReport.java
java -Duser.language=en -cp "build/tools${SEP}${GAME_JAR}" mindustrysurvivors.RunReport
