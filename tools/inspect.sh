#!/bin/bash
JAR=""
for j in $(find ~/.gradle ~/.m2 -name '*.jar' 2>/dev/null); do
  if unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/world/entity/EntityType.class'; then JAR="$j"; break; fi
done
echo "JAR=$JAR"
while IFS='|' read -r cls pat; do
  [ -z "$cls" ] && continue
  if [ "$cls" = "find" ]; then
    echo "=== find $pat"; unzip -l "$JAR" | awk '{print $4}' | grep -iE "$pat" | head -60
  else
    echo "=== $cls ($pat)"
    if [ -n "$pat" ]; then javap -public -cp "$JAR" "$cls" 2>&1 | grep -iE "$pat" | head -80; else javap -public -cp "$JAR" "$cls" 2>&1 | head -150; fi
  fi
done < tools/inspect.txt
