// Iz uiautomator dump-a (argv[2]) vraća "x y" centra N-tog dugmeta (argv[3], od 0) u traci filtera (resource-id "filteri").
const fs = require('fs');
const s = fs.readFileSync(process.argv[2], 'utf8');
const n = parseInt(process.argv[3] || '1', 10);
const granice = b => b.match(/\d+/g).map(Number);
const f = s.match(/resource-id="[^"]*:id\/filteri"[^>]*bounds="([^"]+)"/);
if (!f) { console.log(''); process.exit(0); }
const [fx1, fy1, fx2, fy2] = granice(f[1]);
const dugmad = [...s.matchAll(/<node [^>]*text="([^"]+)"[^>]*bounds="([^"]+)"/g)]
  .map(m => granice(m[2])).filter(([x1, y1, x2, y2]) => y1 >= fy1 && y2 <= fy2 && x1 >= fx1)
  .sort((a, b) => a[0] - b[0]);
const d = dugmad[n];
console.log(d ? Math.round((d[0] + d[2]) / 2) + ' ' + Math.round((d[1] + d[3]) / 2) : '');
