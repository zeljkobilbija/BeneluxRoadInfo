/*
 * Kilometarske table (highway=milestone) iz Geofabrik izvoda, za alati/kilometraza.js:
 *
 *   node alati/table_km.js ../EuroRoadInfo/alati/osm/netherlands-latest.osm.pbf alati/osm/milestone_nl.json  (isto BE, LU)
 *
 * Izlaz: [{id, la, lo, d (km sa table, tag distance ili pk), r (ref puta), o (operator)}]
 */
'use strict';
const fs = require('fs');
const { blokovi, citajBlok } = require('./osm_lokalno');

const [pbf, izlaz] = process.argv.slice(2);
const table = [];
for (const blok of blokovi(pbf)) citajBlok(blok, {
  cvor(id, lat, lon, t) {
    if (!t || t.highway !== 'milestone') return;
    const d = t.distance || t.pk || t['railway:position'] || '';
    if (!d) return;
    table.push({ id, la: +lat.toFixed(6), lo: +lon.toFixed(6), d, r: t.ref || '', o: t.operator || '' });
  },
  put() { }, rel() { },
});
fs.writeFileSync(izlaz, JSON.stringify(table));
console.log(`table: ${table.length} → ${izlaz}`);
