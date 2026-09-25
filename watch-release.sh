#!/usr/bin/env bash
# Waits for the cloud session to push a NEW jar into release/, then installs it.
# Checks GitHub every 5 minutes. Exits after installing.
cd "$(dirname "$0")"
newest() { git ls-tree --name-only origin/main release/ 2>/dev/null | grep -E 'release/hollowbell-.*\.jar$' | sort -V | tail -1; }
git fetch -q origin main 2>/dev/null
start=$(newest)
echo "$(date '+%H:%M') waiting for a jar newer than ${start:-none}"
while true; do
  sleep 300
  if git fetch -q origin main 2>/dev/null; then
    jar=$(newest)
    if [ -n "$jar" ] && [ "$jar" != "$start" ]; then
      echo "$(date '+%H:%M') found $jar"
      powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./install.ps1
      exit $?
    fi
  fi
done
