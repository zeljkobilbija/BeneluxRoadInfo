/*
 * Zvanična kilometraža (brojevi sa tabli (cippi) pored autoputa) za podatke aplikacije.
 *
 *   node alati/kilometraza.js alati/osm/milestone_nl.json NL   (pa BE i LU; table: node alati/table_km.js)
 *   Keš linija: ../EuroRoadInfo/alati/osm/<zemlja>.json; obrađuju se samo autoputevi te zemlje (id "NL-A5").
 *
 * Ulaz: tačke highway=milestone iz italy-latest.osm.pbf ({la, lo, d, r}), keš alati/osm/it.json (linije autoputeva).
 * Svaka tabla se dodeli autoputu čiji je kolovoz do 40 m od nje (ili po svom ref-u), projektuje se na liniju
 * deonice u assets/autoputevi/*.json (kroz izlaze i odmorišta) i dobija "naš" kilometar. Za svaku deonicu
 * se upisuje "kal": [[naš km, km sa table, smer ±1], …] – samo table koje se slažu sa susedima (do 4 km):
 * zvanični km = km table + smer · (naš km − naš km table). Tako se hvataju i obrnut smer i resetovanje kilometraže.
 */
'use strict';
const fs = require('fs'), path = require('path');

const ASSETS = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'autoputevi');
const ZEMLJA = (process.argv[3] || '').toUpperCase();
if (!/^(NL|BE|LU)$/.test(ZEMLJA)) { console.error('zemlja: NL, BE ili LU'); process.exit(1); }
const kes = require(path.join(__dirname, '..', '..', 'EuroRoadInfo', 'alati', 'osm', ZEMLJA.toLowerCase() + '.json'));
const table = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'))
  .map(t => ({ ...t, km: parseFloat(String(t.d).replace(',', '.')) }))
  .filter(t => isFinite(t.km));

const norm = r => r.replace(/\s+/g, '').toUpperCase();
const KX = la => 111.32 * Math.cos(la * Math.PI / 180), KY = 110.57;

// ---- 1. Segmenti kolovoza po oznaci autoputa, u mreži ćelija ~1 km ----
const mreza = new Map();
const celija = (la, lo) => Math.floor(la * 100) + '_' + Math.floor(lo * 70);
// BE: putevi nose samo E-oznake, a broj autoputa (A1…) je u relaciji
const izRelacija = new Map();
for (const r of Object.values(kes.rel || {})) {
  const ref = norm(String(r.ref || ''));
  if (/^A\d+$/.test(ref)) for (const w of r.w || []) { if (!izRelacija.has(w)) izRelacija.set(w, new Set()); izRelacija.get(w).add(ref); }
}
for (const [wid, w] of Object.entries(kes.mw)) {
  if (w.tr) continue;
  const refs = [...new Set([...(w.ref || '').split(';').map(norm).filter(r => /^A\d+$/.test(r)), ...(izRelacija.get(+wid) || [])])];
  if (!refs.length) continue;
  for (let i = 1; i < w.n.length; i++) {
    const a = kes.k[w.n[i - 1]], b = kes.k[w.n[i]];
    if (!a || !b) continue;
    const s = { a, b, refs };
    const c = celija((a[0] + b[0]) / 2, (a[1] + b[1]) / 2);
    if (!mreza.has(c)) mreza.set(c, []);
    mreza.get(c).push(s);
  }
}

/** Udaljenost (km) tačke od segmenta. */
function dSeg(la, lo, a, b) {
  const kx = KX(la);
  const ax = (a[1] - lo) * kx, ay = (a[0] - la) * KY, bx = (b[1] - lo) * kx, by = (b[0] - la) * KY;
  const dx = bx - ax, dy = by - ay, d2 = dx * dx + dy * dy;
  const t = d2 ? Math.max(0, Math.min(1, -(ax * dx + ay * dy) / d2)) : 0;
  return Math.hypot(ax + t * dx, ay + t * dy);
}

// ---- 2. Tabla → autoput(evi) ----
const poAutoputu = new Map();
let dodeljeno = 0;
for (const t of table) {
  let refs = null;
  if (/^A\s*\d+$/i.test(t.r || '')) refs = [norm(t.r)];
  else {
    const a = Math.floor(t.la * 100), b = Math.floor(t.lo * 70);
    let naj = 0.04, nr = null;
    for (let i = -1; i <= 1; i++) for (let j = -1; j <= 1; j++)
      for (const s of mreza.get((a + i) + '_' + (b + j)) || []) {
        const d = dSeg(t.la, t.lo, s.a, s.b);
        if (d < naj) { naj = d; nr = s.refs; }
      }
    refs = nr;
  }
  if (!refs) continue;
  dodeljeno++;
  for (const r of refs) { if (!poAutoputu.has(r)) poAutoputu.set(r, []); poAutoputu.get(r).push(t); }
}
console.log(`table: ${table.length}, uz autoput: ${dodeljeno}`);

// Deonice spaja alati/spoji_deonice.js (pokrenuti pre ovoga).

// ---- 3. Po deonici: projekcija na liniju kroz izlaze/odmorišta i provera sa susedima ----

const MAX_RUPA = 40;
const saTablama = [], bezTabli = [];
let ukupnoStavki = 0, pokriveno = 0;
for (const f of fs.readdirSync(ASSETS).filter(f => f.endsWith('.json'))) {
  const p = path.join(ASSETS, f);
  const d = JSON.parse(fs.readFileSync(p, 'utf8'));
  if (!d.id.startsWith(ZEMLJA + '-')) continue;
  const tabl = poAutoputu.get(norm(d.id.slice(3))) || [];
  d.sekcije.forEach((sek, si) => {
    delete sek.kal;
    const tacke = d.stavke.filter(x => x.si === si && x.la != null).sort((x, y) => x.km - y.km);
    if (tacke.length < 2) return;
    const parovi = [];
    for (const t of tabl) {
      let naj = 1e9, km = 0;
      const kx = KX(t.la);
      for (let i = 1; i < tacke.length; i++) {
        const A = tacke[i - 1], B = tacke[i];
        if ((sek.prekidi || []).some(q => q[0] >= A.km - 0.05 && q[1] <= B.km + 0.05)) continue;
        const ax = (A.lo - t.lo) * kx, ay = (A.la - t.la) * KY, bx = (B.lo - t.lo) * kx, by = (B.la - t.la) * KY;
        const dx = bx - ax, dy = by - ay, d2 = dx * dx + dy * dy;
        const u = d2 ? Math.max(0, Math.min(1, -(ax * dx + ay * dy) / d2)) : 0;
        const dd = Math.hypot(ax + u * dx, ay + u * dy);
        if (dd < naj) { naj = dd; km = A.km + u * (B.km - A.km); }
      }
      // linija kroz izlaze je gruba: tabla mora biti blizu nje, ali ne i na samom kolovozu
      if (naj <= 2.5) parovi.push([km, t.km]);
    }
    parovi.sort((x, y) => x[0] - y[0]);
    // Smer table: glasaju susedne table na DRUGIM mestima (0,3–6 km dalje) čiji nagib (Δ km table / Δ naš km)
    // je blizu ±1; tabla važi ako se bar jedna takva slaže sa njom (±0,5 km). Table sa obe strane kolovoza
    // na istom mestu ne glasaju (nemaju nagib).
    const kal = [];
    for (const [n, z] of parovi) {
      let plus = 0, minus = 0;
      for (const [qn, qz] of parovi) {
        const dn = qn - n;
        if (Math.abs(dn) < 0.3 || Math.abs(dn) > 15) continue;
        const nagib = (qz - z) / dn;
        if (Math.abs(nagib - 1) <= 0.2 || Math.abs((qz - z) - dn) <= 0.5) plus++;
        else if (Math.abs(nagib + 1) <= 0.2 || Math.abs((qz - z) + dn) <= 0.5) minus++;
      }
      if (plus === 0 && minus === 0) continue;
      if (plus === minus) continue;
      kal.push([+n.toFixed(2), +z.toFixed(1), plus > minus ? 1 : -1]);
    }
    // bliske duplikate (oba kolovoza) svesti na jednu
    const cisto = kal.filter((q, i) => i === 0 || Math.abs(q[0] - kal[i - 1][0]) > 0.15 || q[1] !== kal[i - 1][1]);
    // table mogu biti retke; km sa tabli samo gde nigde nema rupe veće od MAX_RUPA km (ni na krajevima),
    // inače bi se km računao od daleke table i mogao da odstupa (A14: 2 table na 744 km)
    const L = Math.max(0, ...d.stavke.filter(x => x.si === si).map(x => x.km));
    let rupa = cisto.length ? Math.max(cisto[0][0], L - cisto[cisto.length - 1][0]) : L;
    for (let i = 1; i < cisto.length; i++) rupa = Math.max(rupa, cisto[i][0] - cisto[i - 1][0]);
    if (cisto.length && rupa <= MAX_RUPA) { sek.kal = cisto; saTablama.push(d.id); }
    else if (cisto.length) bezTabli.push(`${d.id} (${cisto.length} tabli, rupa ${rupa.toFixed(0)} km)`);
    if (!sek.kal) return;
    for (const x of d.stavke.filter(x => x.si === si)) {
      ukupnoStavki++;
      if (cisto.some(q => Math.abs(q[0] - x.km) <= 5)) pokriveno++;
    }
  });
  fs.writeFileSync(p, JSON.stringify(d));
}
console.log(`mesta sa zvaničnim km (tabla do 5 km): ${pokriveno} / ${ukupnoStavki} (${(100 * pokriveno / ukupnoStavki).toFixed(0)} %)`);
console.log(`km sa tabli (${saTablama.length}): ${saTablama.join(', ')}`);
console.log(`table preretke, km od početka: ${bezTabli.join('; ')}`);
