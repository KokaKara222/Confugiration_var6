#!/usr/bin/env bash
set -e
mkdir -p build
kotlinc src/main/kotlin/Main.kt -include-runtime -d build/stage1.jar
java -jar build/stage1.jar "$@"
