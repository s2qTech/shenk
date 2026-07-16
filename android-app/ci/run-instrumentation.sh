#!/usr/bin/env bash

set +e
./gradlew connectedDebugAndroidTest 2>&1 | tee instrumentation-check.log
result=${PIPESTATUS[0]}

if [ "$result" -ne 0 ]; then
  failure_tail=$(tail -n 120 instrumentation-check.log)
  failure_tail=${failure_tail//'%'/'%25'}
  failure_tail=${failure_tail//$'\r'/'%0D'}
  failure_tail=${failure_tail//$'\n'/'%0A'}
  echo "::error title=Compose instrumentation failed::$failure_tail"
fi

exit "$result"
