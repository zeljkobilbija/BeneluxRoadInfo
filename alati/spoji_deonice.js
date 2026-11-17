/*
 * Autostrada u jednom komadu (kao AutobahnInfo, alati/kilometraza.js → spojiDeonice):
 *
 *   node alati/spoji_deonice.js            → spaja sve deonice u assets/autoputevi/*.json
 *   node alati/spoji_deonice.js --suvo     → samo izveštaj
 *
 * Sve deonice jedne autostrade postaju jedna lista, redom kojim idu. Deonica okrenuta suprotno od
 * prethodne se okreće (km, strana, plus/minus). Kilometri se nastavljaju (dodaje se vazdušna razdaljina
 * preko prekida). Gde je autostrada stvarno prekinuta (razmak > 5 km) upisuje se "prekidi":
 * [[km kraja, km nastavka]] – aplikacija tu ne spaja liniju. Ponovno pokretanje ne menja već spojeno.
 * Pokretati posle autostrade_skripta.js (ona pravi deonice iznova).
 */
'use strict';
const fs = require('fs'), path = require('path');

const ASSETS = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'autoputevi');
const suvo = process.argv.includes('--suvo');
const KX = la => 111.32 * Math.cos(la * Math.PI / 180), KY = 110.57;
const izvestaj = [];
/** Ne spajaju se (npr. prstenovi čija su dva dela dva kolovoza). */
const IZUZECI = new Set([]);

function spojiDeonice(d) {
  if (d.sekcije.length < 2 || IZUZECI.has(d.id)) return false;
  const tac = si => d.stavke.filter(x => x.si === si && x.la != null);
  const dist = (a, b) => Math.hypot((a.la - b.la) * KY, (a.lo - b.lo) * KX(a.la));
  const krajevi = si => { const a = tac(si); if (!a.length) return null;
    return [a.reduce((m, x) => x.km < m.km ? x : m), a.reduce((m, x) => x.km > m.km ? x : m)]; };
  const duzina = si => Math.max(0, ...d.stavke.filter(x => x.si === si).map(x => x.km));
  // Redosled: deonice u podacima nisu uvek poređane duž puta (A51: Marseille – Aix, Grenoble, Aix – Sisteron),
  // pa se slažu u lanac: probaju se svi početci i svaka sledeća je ona čiji je kraj najbliži,
  // a bira se lanac sa najmanjim zbirom razmaka.
  const n = d.sekcije.length, K = d.sekcije.map((_, si) => krajevi(si));
  const blizu = (a, si) => K[si] ? Math.min(dist(a, K[si][0]), dist(a, K[si][1])) : 1e9;
  let najLanac = null, najZbir = 1e18;
  for (let p = 0; p < n; p++) for (const prvaOkrenuta of [false, true]) {
    if (!K[p]) continue;
    const lanac = [[p, prvaOkrenuta]], koriscen = new Set([p]);
    let kraj = prvaOkrenuta ? K[p][0] : K[p][1], zbir = 0;
    while (lanac.length < n) {
      let nb = -1, nd = 1e18;
      for (let q = 0; q < n; q++) if (!koriscen.has(q) && blizu(kraj, q) < nd) { nd = blizu(kraj, q); nb = q; }
      if (nb < 0 || !K[nb]) { nb = [...Array(n).keys()].find(q => !koriscen.has(q)); nd = 0; }
      const ok = K[nb] ? dist(kraj, K[nb][1]) < dist(kraj, K[nb][0]) : false;
      lanac.push([nb, ok]); koriscen.add(nb); zbir += nd;
      if (K[nb]) kraj = ok ? K[nb][0] : K[nb][1];
    }
    if (zbir < najZbir) { najZbir = zbir; najLanac = lanac; }
  }
  const prekidi = [];
  let pomak = 0, prethodniKraj = null;
  const [p0, o0] = najLanac[0], s0 = d.sekcije[p0];
  const nova = { ...s0, od: o0 ? s0.do : s0.od, minus: o0 ? s0.plus : s0.minus };
  const novaSi = new Map(); // stari si → [redni broj, okreni]
  najLanac.forEach(([si, ok], i) => novaSi.set(si, [i, ok]));
  for (const [si, okreni] of najLanac) {
    const s = d.sekcije[si], L = duzina(si), kr = K[si];
    if (si !== p0) {
      const pocetak = kr ? (okreni ? kr[1] : kr[0]) : null;
      const razmak = prethodniKraj && pocetak ? dist(prethodniKraj, pocetak) : 0;
      if (razmak > 5) prekidi.push([+pomak.toFixed(1), +(pomak + razmak).toFixed(1)]);
      pomak += razmak;
      izvestaj.push(`${d.id}: + ${s.od} – ${s.do}${okreni ? ' (okrenuta)' : ''}, razmak ${razmak.toFixed(1)} km`);
    }
    nova.do = okreni ? s.od : s.do;
    nova.plus = okreni ? s.minus : s.plus;
    for (const x of d.stavke) {
      if (x.si !== si || x._gotovo) continue;
      x.km = +(pomak + (okreni ? L - x.km : x.km)).toFixed(1);
      if (okreni && x.s) x.s = x.s === '+' ? '-' : '+'; // izlazi nemaju stranu
      x._gotovo = 1;
    }
    if (kr) prethodniKraj = okreni ? kr[0] : kr[1];
    pomak += L;
  }
  for (const x of d.stavke) { delete x._gotovo; x.si = 0; }
  if (prekidi.length) nova.prekidi = prekidi;
  d.sekcije = [nova];
  d.stavke.sort((x, y) => x.km - y.km);
  return true;
}

let n = 0;
for (const f of fs.readdirSync(ASSETS).filter(f => f.endsWith('.json')).sort()) {
  const p = path.join(ASSETS, f), d = JSON.parse(fs.readFileSync(p, 'utf8'));
  if (!spojiDeonice(d)) continue;
  n++;
  const s = d.sekcije[0];
  izvestaj.push(`${d.id} → ${s.od} – ${s.do}${s.prekidi ? ', prekidi ' + JSON.stringify(s.prekidi) : ''}`);
  if (!suvo) fs.writeFileSync(p, JSON.stringify(d));
}
console.log(izvestaj.join('\n'));
console.log(`spojeno autostrada: ${n}${suvo ? ' (suvo, ništa nije snimljeno)' : ''}`);
