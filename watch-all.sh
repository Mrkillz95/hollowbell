#!/usr/bin/env bash
# Waits for the cloud session to push a NEW all-in-one jar into release/, then installs it.
# Checks GitHub every 5 minutes. Exits after installing.
cd "$(dirname "$0")"
b=claude/amazing-faraday-iutdpo
newest() { git ls-tree --name-only origin/$b release/ 2>/dev/null | grep -E 'release/giants-all-.*\.jar$' | sort -V | tail -1; }
git fetch -q origin $b 2>/dev/null
start=$(newest)
echo "$(date '+%H:%M') waiting for an all-in-one jar newer than ${start:-none}"
while true; do
  sleep 300
  if git fetch -q origin $b 2>/dev/null; then
    jar=$(newest)
    if [ -n "$jar" ] && [ "$jar" != "$start" ]; then
      echo "$(date '+%H:%M') found $jar"
      powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./install-all.ps1
      exit $?
    fi
  fi
done
