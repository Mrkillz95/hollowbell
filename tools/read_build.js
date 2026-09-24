// Reads the KillzAI Hollowbell .mcstructure pieces, assembles them, and dumps
// block counts + a compact voxel file (vox.bin) for rendering.
const fs = require('fs');
const path = require('path');

const PACK = require('path').join(__dirname, '..', 'build-source');
const NAME = process.argv[2] || 'hollowbell';
const OUT = __dirname;

function readNBT(buf) {
  let p = 0;
  const u8 = () => buf[p++];
  const i16 = () => { const v = buf.readInt16LE(p); p += 2; return v; };
  const i32 = () => { const v = buf.readInt32LE(p); p += 4; return v; };
  const i64 = () => { const v = buf.readBigInt64LE(p); p += 8; return v; };
  const f32 = () => { const v = buf.readFloatLE(p); p += 4; return v; };
  const f64 = () => { const v = buf.readDoubleLE(p); p += 8; return v; };
  const str = () => { const n = buf.readUInt16LE(p); p += 2; const s = buf.toString('utf8', p, p + n); p += n; return s; };
  function payload(t) {
    switch (t) {
      case 1: return (u8() << 24) >> 24;
      case 2: return i16();
      case 3: return i32();
      case 4: return i64();
      case 5: return f32();
      case 6: return f64();
      case 7: { const n = i32(); const a = buf.subarray(p, p + n); p += n; return a; }
      case 8: return str();
      case 9: {
        const et = u8(); const n = i32();
        if (et === 3) { const a = new Int32Array(n); for (let i = 0; i < n; i++) { a[i] = buf.readInt32LE(p); p += 4; } return a; }
        const a = []; for (let i = 0; i < n; i++) a.push(payload(et)); return a;
      }
      case 10: {
        const o = {};
        for (;;) { const tt = u8(); if (tt === 0) break; const k = str(); o[k] = payload(tt); }
        return o;
      }
      case 11: { const n = i32(); const a = new Int32Array(n); for (let i = 0; i < n; i++) { a[i] = buf.readInt32LE(p); p += 4; } return a; }
      case 12: { const n = i32(); const a = []; for (let i = 0; i < n; i++) a.push(i64()); return a; }
      default: throw new Error('bad tag ' + t + ' at ' + p);
    }
  }
  const t = u8(); str(); return payload(t);
}

const fn = fs.readFileSync(path.join(PACK, NAME + '.mcfunction'), 'utf8');
const loads = [...fn.matchAll(/structure load killzai:(\S+) ~(-?\d*) ~(-?\d*) ~(-?\d*)/g)]
  .map(m => ({ id: m[1], x: +(m[2] || 0), y: +(m[3] || 0), z: +(m[4] || 0) }));
console.log('pieces', loads.length);

const blocks = new Map(); // key -> palette name
const names = []; const nameIdx = new Map();
const vox = []; // x,y,z,nameIdx
const counts = {};
let minX = 1e9, minY = 1e9, minZ = 1e9, maxX = -1e9, maxY = -1e9, maxZ = -1e9;

for (const L of loads) {
  const nbt = readNBT(fs.readFileSync(path.join(PACK, 'structures', L.id + '.mcstructure')));
  const [sx, sy, sz] = nbt.size;
  const idx = nbt.structure.block_indices[0];
  const pal = nbt.structure.palette.default.block_palette.map(b => {
    const st = b.states || {};
    const keys = Object.keys(st).filter(k => !/facing|direction|orientation|axis|rotation|weirdo|upside|pillar|wall_connection|hanging|attached|lit$|open_bit|persistent|update|age|stage/i.test(k));
    const extra = keys.map(k => `${k}=${st[k] instanceof Buffer ? st[k][0] : st[k]}`).join(',');
    return b.name.replace('minecraft:', '') + (extra ? `[${extra}]` : '');
  });
  for (let x = 0; x < sx; x++) for (let y = 0; y < sy; y++) for (let z = 0; z < sz; z++) {
    const i = idx[x * sy * sz + y * sz + z];
    if (i < 0) continue;
    const n = pal[i];
    if (n === 'air' || n === 'structure_void') continue;
    const gx = L.x + x, gy = L.y + y, gz = L.z + z;
    if (!nameIdx.has(n)) { nameIdx.set(n, names.length); names.push(n); }
    vox.push(gx, gy, gz, nameIdx.get(n));
    counts[n] = (counts[n] || 0) + 1;
    if (gx < minX) minX = gx; if (gy < minY) minY = gy; if (gz < minZ) minZ = gz;
    if (gx > maxX) maxX = gx; if (gy > maxY) maxY = gy; if (gz > maxZ) maxZ = gz;
  }
}
console.log('voxels', vox.length / 4);
console.log('bounds x', minX, maxX, 'y', minY, maxY, 'z', minZ, maxZ);
const sorted = Object.entries(counts).sort((a, b) => b[1] - a[1]);
for (const [n, c] of sorted) console.log(String(c).padStart(8), n);
fs.writeFileSync(path.join(OUT, NAME + '_vox.bin'), Buffer.from(new Int32Array(vox).buffer));
fs.writeFileSync(path.join(OUT, NAME + '_names.json'), JSON.stringify({ names, bounds: [minX, minY, minZ, maxX, maxY, maxZ] }));
