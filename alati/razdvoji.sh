#!/bin/sh
# Razdvaja serija.txt (iz AB.sacuvajSve()) na pojedinačne JSON fajlove u assets/autoputevi/.
# Upotreba: sh alati/razdvoji.sh ~/Downloads/serija.txt
cilj="$(dirname "$0")/../app/src/main/assets/autoputevi"
while IFS="$(printf '\t')" read -r ime json; do
  [ -n "$ime" ] && printf '%s' "$json" > "$cilj/$ime" && echo "$ime"
done < "$1"
