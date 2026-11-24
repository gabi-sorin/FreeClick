#!/bin/bash
# Compile the pure-Java core with the JDK only (no Android) and run the unit tests.
set -e
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS _JAVA_OPTIONS
python3 tools/gen_docs.py
rm -rf build/test && mkdir -p build/test
javac -Xlint:-options -source 8 -target 8 -encoding UTF-8 -d build/test \
  $(find src/com/freeclick/core test/com/freeclick/core -name '*.java')
java -Djava.awt.headless=true -cp build/test com.freeclick.core.T com.freeclick.core.CoreTests com.freeclick.core.EngineTests com.freeclick.core.ColorTests com.freeclick.core.WorkspaceTests com.freeclick.core.StorageTests com.freeclick.core.GoldenTests com.freeclick.core.V16Tests com.freeclick.core.V17Tests com.freeclick.core.V18Tests com.freeclick.core.ScriptTests com.freeclick.core.ImageTests com.freeclick.core.V22Tests com.freeclick.core.V24Tests com.freeclick.core.V25Tests com.freeclick.core.FuzzTests

# GlobalActions values must equal the real Android constants.
JAR=${ANDROID_JAR:-/home/claude/dl/android-34.jar}
if [ -f "$JAR" ]; then
  javap -constants -cp "$JAR" android.accessibilityservice.AccessibilityService \
    | grep -oE 'GLOBAL_ACTION_(BACK|HOME|RECENTS|NOTIFICATIONS|QUICK_SETTINGS|POWER_DIALOG|TOGGLE_SPLIT_SCREEN|LOCK_SCREEN|TAKE_SCREENSHOT) = [0-9]+' \
    | sed 's/GLOBAL_ACTION_//' | sort > build/test/android_globals.txt
  grep -oE 'public static final int [A-Z_]+ = [0-9]+' src/com/freeclick/core/GlobalActions.java \
    | sed 's/public static final int //' | sort > build/test/our_globals.txt
  if diff build/test/android_globals.txt build/test/our_globals.txt >/dev/null; then
    echo "  PASS GlobalActions constants match android.jar"
  else
    echo "  FAIL GlobalActions constants differ:"; diff build/test/android_globals.txt build/test/our_globals.txt; exit 1
  fi
fi

# Android-layer helpers: compile against android.jar (stubs) and run the static, Android-free parts.
if [ -f "$JAR" ] && [ -d build/apk/classes ]; then
  mkdir -p build/test-app
  javac -Xlint:-options -nowarn -encoding UTF-8 -source 8 -target 8 -cp "build/apk/classes:build/test:$JAR" \
    -d build/test-app test/com/freeclick/app/*.java
  java -cp "build/test-app:build/apk/classes:build/test:$JAR" com.freeclick.core.T com.freeclick.app.AppLogicTests
fi

# Static checks of the Android layer (manifest, intents, exported components, API levels...).
if [ -f "$JAR" ] && [ -d build/apk/gen ]; then
  API24=${ANDROID24_JAR:-/home/claude/dl/android-24.jar}
  if [ -f "$API24" ]; then
    rm -rf build/api24 && mkdir -p build/api24
    javac -Xmaxerrs 1000 -nowarn -encoding UTF-8 -source 8 -target 8 -bootclasspath "$API24" -d build/api24 \
      $(find src build/apk/gen -name '*.java') 2>&1 | grep -E "error:" | sed 's/: error: .*//' | sort -u > build/api24_errors.txt || true
  fi
  API24_ERRORS_FILE=build/api24_errors.txt python3 tools/check.py
fi

# Extra fuzz rounds with other seeds (FUZZ_SEEDS="1 2 3" ./test.sh)
for seed in ${FUZZ_SEEDS:-}; do
  java -Dfuzz.seed=$seed -cp build/test com.freeclick.core.T com.freeclick.core.FuzzTests | grep -E "RESULT|FAIL"
done
