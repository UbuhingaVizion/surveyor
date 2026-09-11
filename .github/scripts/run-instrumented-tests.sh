#!/usr/bin/env sh
# Runs the instrumented tests and, on failure, dumps logcat before exiting with the test result.
set +e

./gradlew connectedRapidproDebugAndroidTest -PabiFilters=x86_64
RC=$?

if [ "$RC" != "0" ]; then
  echo "===== CRASH BUFFER ====="
  adb logcat -d -b crash -v threadtime || true
  echo "===== ANDROIDRUNTIME / Surveyor / ActivityManager ====="
  adb logcat -d -v threadtime -s AndroidRuntime:E Surveyor:V ActivityManager:I Instrumentation:I | tail -n 400 || true
  echo "===== FULL LOGCAT (tail) ====="
  adb logcat -d -v threadtime | tail -n 500 || true
  adb logcat -d -v threadtime > logcat.txt 2>&1 || true
fi

exit $RC
