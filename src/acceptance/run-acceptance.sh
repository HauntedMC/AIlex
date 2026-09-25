#!/usr/bin/env bash
set -euo pipefail

artifact="${AILEX_ARTIFACT:?Missing AIlex artifact}"
paper_version="${AILEX_PAPER_VERSION:?Missing Paper runtime version}"
paper_build="${AILEX_PAPER_BUILD:?Missing Paper runtime build}"
paper_sha256="${AILEX_PAPER_SHA256:?Missing Paper runtime checksum}"
work_directory="${AILEX_ACCEPTANCE_WORK_DIRECTORY:-$(mktemp -d)}"
keep_work_directory="${AILEX_ACCEPTANCE_KEEP_WORK_DIRECTORY:-false}"
paper_pid=""
paper_input_fd=""

fail() { echo "AIlex acceptance failure: $*" >&2; exit 1; }
wait_for_log() {
  local expected=$1 deadline=$((SECONDS + 180))
  while ((SECONDS < deadline)); do
    grep -Eq -- "$expected" "$work_directory/paper/paper.log" 2>/dev/null && return
    if grep -Eq 'Exception in thread|Error occurred while enabling|Could not load plugin' "$work_directory/paper/paper.log" 2>/dev/null; then
      fail "Paper reported a plugin failure while waiting for $expected"
    fi
    sleep 1
  done
  fail "Timed out waiting for $expected"
}
# ShellCheck cannot see that EXIT invokes this function.
# shellcheck disable=SC2317
cleanup() {
  local result=$?
  if [[ -n "$paper_input_fd" ]]; then printf 'stop\n' >&"$paper_input_fd" || true; fi
  if [[ -n "$paper_pid" ]]; then
    local deadline=$((SECONDS + 30))
    while kill -0 "$paper_pid" 2>/dev/null && ((SECONDS < deadline)); do sleep 1; done
    kill "$paper_pid" 2>/dev/null || true
    wait "$paper_pid" 2>/dev/null || true
  fi
  if [[ "$result" -ne 0 && -f "$work_directory/paper/paper.log" ]]; then tail -n 180 "$work_directory/paper/paper.log" >&2; fi
  if [[ "$keep_work_directory" == true || -n "${AILEX_ACCEPTANCE_WORK_DIRECTORY:-}" ]]; then
    echo "AIlex acceptance logs retained in $work_directory" >&2
  else
    rm -rf "$work_directory"
  fi
  exit "$result"
}
trap cleanup EXIT

for command in curl sha256sum java; do command -v "$command" >/dev/null || fail "Missing $command"; done
[[ -f "$artifact" ]] || fail "Missing jar: $artifact"
mkdir -p "$work_directory/paper/plugins"
paper_url="https://fill-data.papermc.io/v1/objects/$paper_sha256/paper-$paper_version-$paper_build.jar"
curl --fail --silent --show-error --location --output "$work_directory/paper/paper.jar" "$paper_url"
[[ "$(sha256sum "$work_directory/paper/paper.jar" | cut -d ' ' -f1)" == "$paper_sha256" ]] || fail 'Paper runtime checksum mismatch'
citizens_url="https://ci.citizensnpcs.co/job/Citizens2/4255/artifact/dist/target/Citizens-2.0.44-b4255.jar"
citizens_sha256="ab6cfed31112a225fab3982c0040ecc7af9843942111c4f803ddda95c474e8cf"
packetevents_url="https://github.com/retrooper/packetevents/releases/download/v2.13.0/packetevents-spigot-2.13.0.jar"
packetevents_sha256="6d9ece0d87ee727a79a20b7ffbd432021609c6f52bafcb654fc2d3e9b6f064c5"
curl --fail --silent --show-error --location --output "$work_directory/paper/plugins/Citizens.jar" "$citizens_url"
[[ "$(sha256sum "$work_directory/paper/plugins/Citizens.jar" | cut -d ' ' -f1)" == "$citizens_sha256" ]] || fail 'Citizens checksum mismatch'
curl --fail --silent --show-error --location --output "$work_directory/paper/plugins/packetevents.jar" "$packetevents_url"
[[ "$(sha256sum "$work_directory/paper/plugins/packetevents.jar" | cut -d ' ' -f1)" == "$packetevents_sha256" ]] || fail 'PacketEvents checksum mismatch'
cp "$artifact" "$work_directory/paper/plugins/AIlex.jar"
printf 'eula=true\n' >"$work_directory/paper/eula.txt"
printf 'server-port=0\nlevel-type=minecraft:flat\n' >"$work_directory/paper/server.properties"
mkfifo "$work_directory/paper/console.in"
(cd "$work_directory/paper" && exec "${JAVA_HOME:?Set JAVA_HOME to JDK 25}/bin/java" -Xms512M -Xmx1G -jar paper.jar --nogui <console.in >paper.log 2>&1) &
paper_pid=$!
exec {paper_input_fd}>"$work_directory/paper/console.in"
wait_for_log 'Done \('
wait_for_log 'Enabling Citizens'
wait_for_log 'Enabling packetevents'
wait_for_log 'Enabling AIlex'
[[ -d "$work_directory/paper/plugins/AIlex" ]] || fail 'Plugin did not initialize its data directory'
before_lines=$(wc -l <"$work_directory/paper/paper.log")
printf 'ailex\n' >&"$paper_input_fd"
local_deadline=$((SECONDS + 45))
while ((SECONDS < local_deadline)); do
  if tail -n "+$((before_lines + 1))" "$work_directory/paper/paper.log" | grep -Eqi 'This command can only be used by a player'; then
    echo 'AIlex Paper boot and command smoke passed.'
    exit 0
  fi
  sleep 1
done
fail 'No response to ailex command'
