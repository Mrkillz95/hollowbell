#!/usr/bin/env bash
# Waits for the cloud session to push a jar into release/, then installs it.
# Checks GitHub every 5 minutes. Exits after installing.
cd "$(dirname "$0")"
while true; do
  if git fetch -q origin main 2>/dev/null; then
    jar=$(git ls-tree --name-only origin/main release/ 2>/dev/null | grep -E 'release/hollowbell-.*\.jar$' | sort -V | tail -1)
    if [ -n "$jar" ]; then
      echo "$(date '+%H:%M') found $jar"
      powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./install.ps1
      exit $?
    fi
  fi
  sleep 300
done
