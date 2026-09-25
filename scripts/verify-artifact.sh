#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
version="$(sed -n 's/.*<revision>\([0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\)<\/revision>.*/\1/p' pom.xml | head -n 1)"
[[ -n "$version" ]] || { echo 'Missing semantic Maven revision' >&2; exit 1; }
artifact="target/AIlex-$version.jar"
[[ -f "$artifact" ]] || { echo "Missing distributable $artifact" >&2; exit 1; }
entries="$(jar tf "$artifact")"
descriptor="$(unzip -p "$artifact" plugin.yml)"
grep -Fxq "version: '$version'" <<<"$descriptor"
grep -Fxq 'nl/hauntedmc/ailex/AIlexPlugin.class' <<<"$entries"
grep -Fxq 'org/sqlite/JDBC.class' <<<"$entries"
grep -Fxq 'com/mysql/cj/jdbc/Driver.class' <<<"$entries"
grep -Fxq 'knowledge/README.md' <<<"$entries"
services="$(unzip -p "$artifact" META-INF/services/java.sql.Driver)"
grep -Fxq 'org.sqlite.JDBC' <<<"$services"
grep -Fxq 'com.mysql.cj.jdbc.Driver' <<<"$services"
if grep -Eq '^(net/citizensnpcs/|com/github/retrooper/packetevents/|org/bukkit/|io/papermc/paper/)' <<<"$entries"; then
  echo 'Plugin jar contains Citizens, PacketEvents, or provided Paper classes' >&2
  exit 1
fi
echo "Artifact audit passed: $artifact"
