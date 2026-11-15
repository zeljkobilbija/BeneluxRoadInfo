/*
 * Lokalni izvor podataka umesto Overpass-a: OSM izvod savezne države sa Geofabrik-a (.osm.pbf),
 * jednom preuzet i obrađen u mali keš (alati/osm/<drzava>.json), pa je svaki autoput gotov za par sekundi.
 *
 *   node alati/interstate_skripta.js DE-I95 DE-I295     (preuzme i pripremi DE ako treba)
 *   node alati/interstate_skripta.js --osvezi CA-I5     (ponovo preuzme izvod)
 *
 * Čitač .pbf formata je ovde (bez npm paketa): BlobHeader/Blob → PrimitiveBlock → čvorovi, putevi, relacije.
 */
'use strict';
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const { Readable } = require('stream'), { pipeline } = require('stream/promises');

const DIR = path.join(__dirname, 'osm');
const DRZAVE = { AL: 'alabama', AK: 'alaska', AZ: 'arizona', AR: 'arkansas', CA: 'california', CO: 'colorado',
  CT: 'connecticut', DE: 'delaware', DC: 'district-of-columbia', FL: 'florida', GA: 'georgia', HI: 'hawaii',
  ID: 'idaho', IL: 'illinois', IN: 'indiana', IA: 'iowa', KS: 'kansas', KY: 'kentucky', LA: 'louisiana', ME: 'maine',
  MD: 'maryland', MA: 'massachusetts', MI: 'michigan', MN: 'minnesota', MS: 'mississippi', MO: 'missouri',
  MT: 'montana', NE: 'nebraska', NV: 'nevada', NH: 'new-hampshire', NJ: 'new-jersey', NM: 'new-mexico',
  NY: 'new-york', NC: 'north-carolina', ND: 'north-dakota', OH: 'ohio', OK: 'oklahoma', OR: 'oregon',
  PA: 'pennsylvania', RI: 'rhode-island', SC: 'south-carolina', SD: 'south-dakota', TN: 'tennessee', TX: 'texas',
  UT: 'utah', VT: 'vermont', VA: 'virginia', WA: 'washington', WV: 'west-virginia', WI: 'wisconsin', WY: 'wyoming' };

/**
 * Oblast → [putanja na Geofabrik-u, granica za sečenje (oznaka=vrednost) ili null].
 * Isti modul se koristi u svim aplikacijama za autoputeve (InterstateInfo, BeneluxRoadInfo, AutoroutesInfo…).
 * U SAD se seče po granici savezne države (relacije Interstate-a idu kroz više država); u Evropi svaka zemlja
 * ima svoju mrežu (NL:A, FR:A-road…), pa sečenje ne treba. Nemačka je "DEU" (DE je Delaware).
 */
const OBLASTI = {
  NL: ['europe/netherlands', null], BE: ['europe/belgium', null], LU: ['europe/luxembourg', null],
  FR: ['europe/france', null], IT: ['europe/italy', null], DEU: ['europe/germany', null],
  AT: ['europe/austria', null], CH: ['europe/switzerland', null],
};
for (const [k, s] of Object.entries(DRZAVE)) OBLASTI[k] = ['north-america/us/' + s, 'ISO3166-2=US-' + k];

// ---------- Protobuf ----------
class Pb {
  constructor(b, p = 0, e = b.length) { this.b = b; this.p = p; this.e = e; }
  more() { return this.p < this.e; }
  varint() { // kao Number (tačno do 2^53 – dovoljno za OSM ID-eve)
    const b = this.b; let x = 0, m = 1, c;
    do { c = b[this.p++]; x += (c & 0x7f) * m; m *= 128; } while (c & 0x80);
    return x;
  }
  sint() { const x = this.varint(); return x % 2 ? -(x + 1) / 2 : x / 2; }
  opseg() { const n = this.varint(), s = this.p; this.p += n; return [s, this.p]; }
  sub() { const [s, e] = this.opseg(); return new Pb(this.b, s, e); }
  tekst() { const [s, e] = this.opseg(); return this.b.toString('utf8', s, e); }
  skip(w) {
    if (w === 0) this.varint(); else if (w === 1) this.p += 8; else if (w === 2) { const n = this.varint(); this.p += n; }
    else if (w === 5) this.p += 4; else throw new Error('pbf: nepoznat tip ' + w);
  }
  nizV() { const s = this.sub(), a = []; while (s.more()) a.push(s.varint()); return a; }
  nizDelta() { const s = this.sub(), a = []; let x = 0; while (s.more()) { x += s.sint(); a.push(x); } return a; }
}

function* blokovi(datoteka) {
  const fd = fs.openSync(datoteka, 'r'), d4 = Buffer.alloc(4);
  let poz = 0;
  try {
    while (fs.readSync(fd, d4, 0, 4, poz) === 4) {
      const hl = d4.readUInt32BE(0); poz += 4;
      const hb = Buffer.alloc(hl); fs.readSync(fd, hb, 0, hl, poz); poz += hl;
      const h = new Pb(hb); let tip = '', vel = 0;
      while (h.more()) { const t = h.varint(), f = t >>> 3;
        if (f === 1) tip = h.tekst(); else if (f === 3) vel = h.varint(); else h.skip(t & 7); }
      const bb = Buffer.alloc(vel); fs.readSync(fd, bb, 0, vel, poz); poz += vel;
      if (tip !== 'OSMData') continue;
      const b = new Pb(bb); let pod = null;
      while (b.more()) { const t = b.varint(), f = t >>> 3;
        if (f === 1) { const [s, e] = b.opseg(); pod = bb.subarray(s, e); }
        else if (f === 3) { const [s, e] = b.opseg(); pod = zlib.inflateSync(bb.subarray(s, e)); }
        else b.skip(t & 7); }
      if (pod) yield pod;
    }
  } finally { fs.closeSync(fd); }
}

/** cb: { cvor(id, lat, lon, tagovi|null), put(id, refs, tagovi), rel(id, clanovi, tagovi), bezTagova } */
function citajBlok(buf, cb) {
  const p = new Pb(buf), st = [], grupe = [];
  let gran = 100, latOff = 0, lonOff = 0;
  while (p.more()) { const t = p.varint(), f = t >>> 3;
    if (f === 1) { const s = p.sub(); while (s.more()) { const tt = s.varint(); if (tt >>> 3 === 1) st.push(s.tekst()); else s.skip(tt & 7); } }
    else if (f === 2) grupe.push(p.opseg());
    else if (f === 17) gran = p.varint(); else if (f === 19) latOff = p.varint(); else if (f === 20) lonOff = p.varint();
    else p.skip(t & 7); }
  const ko = (v, off) => 1e-9 * (off + gran * v);
  const tagovi = (k, v) => { if (!k.length) return null; const o = {}; for (let i = 0; i < k.length; i++) o[st[k[i]]] = st[v[i]]; return o; };
  for (const [s, e] of grupe) {
    const g = new Pb(buf, s, e);
    while (g.more()) { const t = g.varint(), f = t >>> 3;
      if (f === 2 && cb.cvor) { // DenseNodes
        const d = g.sub(); let ids = [], la = [], lo = [], kv = null;
        while (d.more()) { const tt = d.varint(), ff = tt >>> 3;
          if (ff === 1) ids = d.nizDelta(); else if (ff === 8) la = d.nizDelta(); else if (ff === 9) lo = d.nizDelta();
          else if (ff === 10 && !cb.bezTagova) kv = d.nizV(); else d.skip(tt & 7); }
        let j = 0;
        for (let i = 0; i < ids.length; i++) {
          let tg = null;
          if (kv) { while (kv[j] !== 0 && j < kv.length) { (tg = tg || {})[st[kv[j]]] = st[kv[j + 1]]; j += 2; } j++; }
          cb.cvor(ids[i], ko(la[i], latOff), ko(lo[i], lonOff), tg);
        }
      } else if (f === 1 && cb.cvor) { // obični čvor
        const n = g.sub(); let id = 0, k = [], v = [], la = 0, lo = 0;
        while (n.more()) { const tt = n.varint(), ff = tt >>> 3;
          if (ff === 1) id = n.sint(); else if (ff === 2) k = n.nizV(); else if (ff === 3) v = n.nizV();
          else if (ff === 8) la = n.sint(); else if (ff === 9) lo = n.sint(); else n.skip(tt & 7); }
        cb.cvor(id, ko(la, latOff), ko(lo, lonOff), tagovi(k, v));
      } else if (f === 3 && cb.put) {
        const w = g.sub(); let id = 0, k = [], v = [], refs = [];
        while (w.more()) { const tt = w.varint(), ff = tt >>> 3;
          if (ff === 1) id = w.varint(); else if (ff === 2) k = w.nizV(); else if (ff === 3) v = w.nizV();
          else if (ff === 8) refs = w.nizDelta(); else w.skip(tt & 7); }
        cb.put(id, refs, tagovi(k, v));
      } else if (f === 4 && cb.rel) {
        const r = g.sub(); let id = 0, k = [], v = [], ids = [], tipovi = [];
        while (r.more()) { const tt = r.varint(), ff = tt >>> 3;
          if (ff === 1) id = r.varint(); else if (ff === 2) k = r.nizV(); else if (ff === 3) v = r.nizV();
          else if (ff === 9) ids = r.nizDelta(); else if (ff === 10) tipovi = r.nizV(); else r.skip(tt & 7); }
        cb.rel(id, ids.map((m, i) => [tipovi[i], m]), tagovi(k, v));
      } else g.skip(t & 7);
    }
  }
}

// ---------- Priprema keša ----------
// Autohof (Nemačka): kompleks kod izlaza, često ucrtan samo kao pumpa ili parking sa imenom "Autohof …"
const jeAutohof = t => /autohof/i.test(t.name || '') && !t.highway && !t.public_transport
  && (/^(fuel|parking)$/.test(t.amenity || '') || !(t.amenity || t.shop || t.tourism));
const jeServis = t => /^(services|rest_area)$/.test(t.highway || '') || jeAutohof(t);
const jePoi = t => /^(fuel|fast_food|restaurant|cafe|toilets|charging_station|shower|parking)$/.test(t.amenity || '')
  || /^(hotel|motel)$/.test(t.tourism || '') || /^(convenience|kiosk)$/.test(t.shop || '');
const POI_K = ['amenity', 'tourism', 'shop', 'brand', 'brand:wikidata', 'name', 'operator', 'capacity:hgv', 'capacity:truck'];
const izdvoji = (t, kljucevi) => { const o = {}; for (const k of kljucevi) if (t[k] != null) o[k] = t[k]; return o; };
const r7 = x => Math.round(x * 1e7) / 1e7;
const RAD = Math.PI / 180;
const metara = (a, b) => Math.hypot((a[1] - b[1]) * Math.cos(a[0] * RAD), a[0] - b[0]) * 111195;

async function preuzmiPbf(drz, osvezi, log) {
  const slug = OBLASTI[drz] && path.basename(OBLASTI[drz][0]);
  if (!slug) throw new Error('nepoznata država ' + drz);
  fs.mkdirSync(DIR, { recursive: true });
  const dat = path.join(DIR, slug + '-latest.osm.pbf');
  if (fs.existsSync(dat) && !osvezi) return dat;
  const url = `https://download.geofabrik.de/${OBLASTI[drz][0]}-latest.osm.pbf`;
  log('preuzimam ' + url);
  const t0 = Date.now(), deo = dat + '.part';
  // veza ume da pukne usred velikog fajla → nastavlja se od mesta prekida (Range)
  for (let i = 0; ; i++) {
    const ima = fs.existsSync(deo) ? fs.statSync(deo).size : 0;
    try {
      const r = await fetch(url, { headers: { 'User-Agent': 'InterstateInfo-tools/1.0', ...(ima ? { Range: `bytes=${ima}-` } : {}) } });
      if (r.status === 416) break; // već sve preuzeto
      if (!r.ok) throw new Error('Geofabrik ' + r.status);
      const nastavak = r.status === 206;
      await pipeline(Readable.fromWeb(r.body), fs.createWriteStream(deo, { flags: nastavak ? 'a' : 'w' }));
      break;
    } catch (e) {
      if (i >= 5) throw e;
      log(`prekid (${e.message}), nastavljam od ${(fs.existsSync(deo) ? fs.statSync(deo).size / 1e6 : 0).toFixed(0)} MB…`);
      await new Promise(ok => setTimeout(ok, 5000 * (i + 1)));
    }
  }
  fs.renameSync(deo, dat);
  log(`preuzeto ${(fs.statSync(dat).size / 1e6).toFixed(0)} MB za ${((Date.now() - t0) / 1000).toFixed(0)} s`);
  return dat;
}

function napraviKes(pbf, kes, log, drz) {
  const iso = OBLASTI[drz][1] && OBLASTI[drz][1].split('=');
  const t0 = Date.now();
  const mw = {}, linkovi = [], rel = [], izlazi = {}, servis = [], servisW = [], poi = [], poiW = [], mesta = [];
  const treba = new Set(), granicaW = {};
  let granicaR = null;
  for (const blok of blokovi(pbf)) citajBlok(blok, {
    cvor(id, lat, lon, t) {
      if (!t) return;
      if (t.highway === 'motorway_junction') izlazi[id] = { lat: r7(lat), lon: r7(lon), tags: t };
      if (jeServis(t)) servis.push({ type: 'node', id, tags: t, c: [r7(lat), r7(lon)], pts: [[lat, lon]] });
      if (!(jeServis(t) && !jeAutohof(t)) && jePoi(t)) poi.push({ type: 'node', id, tags: izdvoji(t, POI_K), c: [r7(lat), r7(lon)] });
      if (/^(city|town|village)$/.test(t.place || '') && t.name) mesta.push({ name: t.name, lat: r7(lat), lon: r7(lon) });
    },
    put(id, refs, t) {
      if (!t) return;
      if (t.highway === 'motorway') { mw[id] = { ref: t.ref || '', n: refs }; refs.forEach(x => treba.add(x)); }
      else if (t.highway === 'motorway_link' && (t.destination || t['destination:ref']))
        linkovi.push({ n0: refs[0], tags: izdvoji(t, ['destination', 'destination:ref']) });
      else if (jeServis(t) || jePoi(t)) {
        if (jeServis(t)) { servisW.push({ id, tags: t, n: refs }); refs.forEach(x => treba.add(x)); }
        if (!(jeServis(t) && !jeAutohof(t)) && jePoi(t)) { poiW.push({ id, tags: izdvoji(t, POI_K), n: refs }); refs.forEach(x => treba.add(x)); }
      }
    },
    rel(id, clanovi, t) {
      if (t && iso && t[iso[0]] === iso[1] && t.boundary === 'administrative')
        granicaR = clanovi.filter(c => c[0] === 1).map(c => c[1]);
      if (!t || t.route !== 'road') return;
      rel.push({ id, network: t.network || '', ref: t.ref || '',
        w: clanovi.filter(c => c[0] === 1).map(c => c[1]), r: clanovi.filter(c => c[0] === 2).map(c => c[1]) });
    },
  });
  log(`1. prolaz: ${Object.keys(mw).length} puteva autoputa, ${rel.length} relacija, ${servis.length + servisW.length} odmorišta (${((Date.now() - t0) / 1000).toFixed(0)} s)`);

  // Geofabrik izvod ima pojas oko države → autoput se seče po pravoj granici
  if (granicaR) { const g = new Set(granicaR);
    for (const blok of blokovi(pbf)) citajBlok(blok, { put(id, refs) { if (g.has(id)) granicaW[id] = refs; } }); }
  const granica = (granicaR || []).map(id => granicaW[id]).filter(Boolean);
  granica.forEach(n => n.forEach(x => treba.add(x)));
  const k = new Map();
  for (const blok of blokovi(pbf)) citajBlok(blok, { bezTagova: true,
    cvor(id, lat, lon) { if (treba.has(id)) k.set(id, [r7(lat), r7(lon)]); } });
  log(`2. prolaz: ${k.size} koordinata (${((Date.now() - t0) / 1000).toFixed(0)} s)`);

  const centar = n => { const p = n.map(x => k.get(x)).filter(Boolean); if (!p.length) return null;
    const la = p.map(x => x[0]), lo = p.map(x => x[1]);
    return { c: [r7((Math.min(...la) + Math.max(...la)) / 2), r7((Math.min(...lo) + Math.max(...lo)) / 2)], pts: p }; };
  for (const w of servisW) { const c = centar(w.n); if (c) servis.push({ type: 'way', id: w.id, tags: w.tags, c: c.c, pts: c.pts }); }
  for (const w of poiW) { const c = centar(w.n); if (c) poi.push({ type: 'way', id: w.id, tags: w.tags, c: c.c }); }

  // samo sadržaji blizu nekog odmorišta (mreža ćelija ~1 km)
  const celija = (la, lo) => Math.floor(la * 100) + '_' + Math.floor(lo * 100);
  const mreza = new Map();
  for (const s of servis) for (const q of s.pts) { const c = celija(q[0], q[1]); if (!mreza.has(c)) mreza.set(c, []); mreza.get(c).push(q); }
  const blizu = q => { const a = Math.floor(q[0] * 100), b = Math.floor(q[1] * 100);
    for (let i = -1; i <= 1; i++) for (let j = -1; j <= 1; j++)
      for (const t of mreza.get((a + i) + '_' + (b + j)) || []) if (metara(q, t) < 450) return true;
    return false; };
  const poiBlizu = poi.filter(x => blizu(x.c));

  // tačka u poligonu (parni-neparni broj preseka; segmenti ne moraju biti spojeni u prstenove)
  const seg = [];
  for (const n of granica) for (let i = 1; i < n.length; i++) { const a = k.get(n[i - 1]), b = k.get(n[i]); if (a && b) seg.push([a, b]); }
  // Granica preko vode (jezero Mičigen kod IL) često nije u izvodu → prsten ostane otvoren i
  // provera "unutra" greši (Čikago je bio "van države"). Krajevi koji vise spajaju se pravom linijom.
  const stepen = new Map();
  for (const n of granica) for (const x of [n[0], n[n.length - 1]]) stepen.set(x, (stepen.get(x) || 0) + 1);
  const vise = [...stepen].filter(([x, s]) => s % 2 && k.has(x)).map(([x]) => k.get(x));
  let spojeno = 0;
  while (vise.length > 1) {
    const a = vise.pop();
    let j = 0; for (let i = 1; i < vise.length; i++) if (metara(a, vise[i]) < metara(a, vise[j])) j = i;
    seg.push([a, vise.splice(j, 1)[0]]); spojeno++;
  }
  if (spojeno) log(`granica: spojeno ${spojeno} prekida (deo granice van izvoda)`);
  const unutra = q => { let u = false;
    for (const [a, b] of seg) if ((a[0] > q[0]) !== (b[0] > q[0]) && q[1] < a[1] + (q[0] - a[0]) * (b[1] - a[1]) / (b[0] - a[0])) u = !u;
    return u; };
  if (seg.length) {
    let van = 0;
    for (const id in mw) if (!mw[id].n.some(n => k.has(n) && unutra(k.get(n)))) { delete mw[id]; van++; }
    log(`granica: ${seg.length} segmenata, van države izbačeno ${van} puteva autoputa`);
  } else if (iso) log('granica nije nađena u izvodu – putevi se ne seku');
  const kmw = {};
  for (const id in mw) for (const n of mw[id].n) if (k.has(n)) kmw[n] = k.get(n);
  const naMw = new Set(Object.keys(kmw));
  // izlazi i odmorišta samo u državi (put preko granice nosi i izlaze susedne države)
  const uDrzavi = (la, lo) => !seg.length || unutra([la, lo]);
  const izl = {}; for (const id in izlazi) if (naMw.has(id) && uDrzavi(izlazi[id].lat, izlazi[id].lon)) izl[id] = izlazi[id];
  const servisU = servis.filter(s => uDrzavi(s.c[0], s.c[1]));
  const linkoviNaIzlazu = linkovi.filter(l => izl[l.n0]);
  servis.forEach(s => { s.pts = s.pts.map(q => [r7(q[0]), r7(q[1])]); });

  const podaci = { izvor: path.basename(pbf), datum: fs.statSync(pbf).mtime.toISOString().slice(0, 10),
    mw, k: kmw, rel, izlazi: izl, linkovi: linkoviNaIzlazu, servis: servisU, poi: poiBlizu, mesta };
  fs.writeFileSync(kes, JSON.stringify(podaci));
  log(`keš: ${path.basename(kes)} ${(fs.statSync(kes).size / 1e6).toFixed(1)} MB, sadržaja uz odmorišta ${poiBlizu.length} (ukupno ${((Date.now() - t0) / 1000).toFixed(0)} s)`);
}

async function pripremi(drz, { osvezi = false, log = console.error } = {}) {
  const pbf = await preuzmiPbf(drz, osvezi, log);
  const kes = path.join(DIR, drz.toLowerCase() + '.json');
  if (!fs.existsSync(kes) || fs.statSync(kes).mtimeMs < fs.statSync(pbf).mtimeMs) napraviKes(pbf, kes, log, drz);
}

// ---------- Upiti nad kešom (vraćaju elemente u obliku Overpass odgovora) ----------
const ucitani = {};
function ucitaj(drz) {
  if (ucitani[drz]) return ucitani[drz];
  const P = JSON.parse(fs.readFileSync(path.join(DIR, drz.toLowerCase() + '.json'), 'utf8'));
  const geom = id => ({ type: 'way', id: +id, geometry: P.mw[id].n.map(n => P.k[n]).filter(Boolean).map(([lat, lon]) => ({ lat, lon })) });
  const el = s => ({ type: s.type, id: s.id, tags: s.tags,
    ...(s.type === 'node' ? { lat: s.c[0], lon: s.c[1] } : { center: { lat: s.c[0], lon: s.c[1] } }) });
  return ucitani[drz] = {
    datum: P.datum,
    /** way(r.r)[highway=motorway] za rel[route=road][network][ref] i njihove podrelacije */
    putevi(mreza, br) {
      const r = P.rel.filter(x => x.network === mreza && x.ref === br), ids = new Set(r.map(x => x.id));
      const sve = [...r, ...P.rel.filter(x => !ids.has(x.id) && r.some(y => y.r.includes(x.id)))];
      const w = new Set(); for (const x of sve) for (const id of x.w) if (P.mw[id]) w.add(String(id));
      return [...w].map(geom);
    },
    /** svi putevi relacija jedne mreže (npr. cela BAB mreža) */
    svePutevi(mreza) { const w = new Set(); for (const x of P.rel) if (x.network === mreza) for (const id of x.w) if (P.mw[id]) w.add(String(id)); return [...w].map(geom); },
    /** way[highway=motorway][ref~"(^|;)I 95(;|$)"] */
    poOznaci(oznaka) { return Object.keys(P.mw).filter(id => P.mw[id].ref.split(';').map(s => s.trim()).includes(oznaka)).map(geom); },
    /** id-jevi puteva u relacijama čija mreža odgovara izrazu (npr. strane mreže u pograničnom delu izvoda) */
    uMrezama(re) { const w = new Set(); for (const x of P.rel) if (re.test(x.network)) for (const id of x.w) w.add(+id); return w; },
    mesto(ime) { const m = P.mesta.find(x => x.name === ime); return m ? [m] : []; },
    /** node(w.w)[highway=motorway_junction] */
    izlazi(putevi) {
      const out = new Map();
      for (const w of putevi) for (const n of P.mw[w.id].n) if (P.izlazi[n] && !out.has(n)) out.set(n, { type: 'node', id: n, ...P.izlazi[n] });
      return [...out.values()];
    },
    /** nwr[highway~services|rest_area](ju,za,se,is) */
    odmorista(ju, za, se, is) { return P.servis.filter(s => s.c[0] >= ju && s.c[0] <= se && s.c[1] >= za && s.c[1] <= is).map(el); },
    /** way(bn.j)[highway=motorway_link] – samo rampe kojima je izlaz prvi čvor (samo te skripta koristi) */
    rampe(izlazi) { const j = new Set(izlazi.map(e => e.id)); return P.linkovi.filter(l => j.has(l.n0)).map(l => ({ type: 'way', nodes: [l.n0], tags: l.tags })); },
    /** nwr(around.odmorista:350)[sadržaji] */
    usluge(odm) {
      const kljuc = new Set(odm.map(e => e.type + e.id));
      const pts = P.servis.filter(s => kljuc.has(s.type + s.id)).flatMap(s => s.pts);
      return P.poi.filter(x => pts.some(q => metara(x.c, q) < 350)).map(el);
    },
  };
}

module.exports = { blokovi, citajBlok, pripremi, ucitaj, DRZAVE, OBLASTI };
