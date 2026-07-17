#!/usr/bin/env bash

set +e

run_instrumentation() {
  adb wait-for-device

  for _ in $(seq 1 60); do
    boot_completed=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    if [ "$boot_completed" = "1" ] && adb shell test -d /sdcard/Android 2>/dev/null; then
      break
    fi
    sleep 2
  done

  adb shell test -d /sdcard/Android || {
    echo "Android shared storage did not become ready."
    return 1
  }

  ./gradlew --no-parallel :core:data-sync:connectedDebugAndroidTest || return $?

  # Do not let two connected-test tasks compete for the same emulator shell.
  adb wait-for-device
  adb shell true || return $?
  ./gradlew --no-parallel :app:connectedDebugAndroidTest
}

export -f run_instrumentation
timeout --signal=TERM 18m bash -c run_instrumentation 2>&1 | tee instrumentation-check.log
result=${PIPESTATUS[0]}

if [ "$result" -ne 0 ]; then
  failure_tail=$(tail -n 120 instrumentation-check.log)
  failure_tail=${failure_tail//'%'/'%25'}
  failure_tail=${failure_tail//$'\r'/'%0D'}
  failure_tail=${failure_tail//$'\n'/'%0A'}
  echo "::error title=Compose instrumentation failed::$failure_tail"
fi

if [ "$result" -eq 124 ]; then
  echo "::error title=Compose instrumentation timed out::The serial connected-test commands exceeded their 18 minute execution limit after the emulator booted."
fi

exit "$result"
