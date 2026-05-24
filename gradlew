#!/usr/bin/env sh
set -eu
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
printf '%s
' 'Gradle is not installed and the sandbox cannot download the official wrapper distribution.' >&2
printf '%s
' 'Install Gradle 9.1+ or generate a standard wrapper with: gradle wrapper --gradle-version 9.1.0' >&2
exit 127
