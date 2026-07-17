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

report_test_failures() {
  local report
  local found=0

  while IFS= read -r report; do
    if grep -Eq '<failure|<error' "$report"; then
      found=1
      echo "Report: $report"
      grep -E '<testsuite|<testcase|<failure|<error' "$report" | tail -n 40
    fi
  done < <(find . -path '*/build/outputs/androidTest-results/connected/*' -name '*.xml' -type f | sort)

  if [ "$found" -eq 0 ]; then
    echo "No failing instrumentation XML report was found."
  fi
}

export -f run_instrumentation
timeout --signal=TERM 18m bash -c run_instrumentation 2>&1 | tee instrumentation-check.log
result=${PIPESTATUS[0]}

if [ "$result" -ne 0 ]; then
  failure_summary=$(report_test_failures)
  if [[ "$failure_summary" == *"No failing instrumentation XML report was found."* ]]; then
    failure_summary+=$'\n\nGradle tail:\n'
    failure_summary+=$(tail -n 35 instrumentation-check.log)
  fi
  failure_summary=${failure_summary//'%'/'%25'}
  failure_summary=${failure_summary//$'\r'/'%0D'}
  failure_summary=${failure_summary//$'\n'/'%0A'}
  echo "::error title=Compose instrumentation failed::$failure_summary"
fi

if [ "$result" -eq 124 ]; then
  echo "::error title=Compose instrumentation timed out::The serial connected-test commands exceeded their 18 minute execution limit after the emulator booted."
fi

exit "$result"
