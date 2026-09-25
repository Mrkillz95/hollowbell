// Reads the KillzAI Hollowbell build (34 Bedrock .mcstructure pieces placed by the
// offsets in hollowbell.mcfunction) into one voxel grid, the same way
// reference/read.js does it.
const fs = require('fs');
const path = require('path');

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

/** Bedrock name (with the colour state folded in, like read.js) for one palette entry. */
function bedrockName(b) {
  const st = b.states || {};
  const n = b.name.replace('minecraft:', '');
  if (st.pillar_axis !== undefined && st.pillar_axis !== 'y') return `${n}[axis=${st.pillar_axis}]`;
  return st.color !== undefined ? `${n}[color=${st.color}]` : n;
}

/**
 * Returns { names: [...bedrock names], vox: Int32Array(x,y,z,nameIdx)*n, bounds }.
 */
function readBuild(dir) {
  const fn = fs.readFileSync(path.join(dir, 'hollowbell.mcfunction'), 'utf8');
  const loads = [...fn.matchAll(/structure load killzai:(\S+) ~(-?\d*) ~(-?\d*) ~(-?\d*)/g)]
    .map(m => ({ id: m[1], x: +(m[2] || 0), y: +(m[3] || 0), z: +(m[4] || 0) }));
  if (loads.length !== 34) throw new Error('expected 34 pieces, found ' + loads.length);
  const names = []; const nameIdx = new Map();
  const vox = [];
  for (const L of loads) {
    const nbt = readNBT(fs.readFileSync(path.join(dir, 'structures', L.id + '.mcstructure')));
    const [sx, sy, sz] = nbt.size;
    const idx = nbt.structure.block_indices[0];
    const pal = nbt.structure.palette.default.block_palette.map(bedrockName);
    for (let x = 0; x < sx; x++) for (let y = 0; y < sy; y++) for (let z = 0; z < sz; z++) {
      const i = idx[x * sy * sz + y * sz + z];
      if (i < 0) continue;
      const n = pal[i];
      if (n === 'air' || n === 'structure_void') continue;
      if (!nameIdx.has(n)) { nameIdx.set(n, names.length); names.push(n); }
      vox.push(L.x + x, L.y + y, L.z + z, nameIdx.get(n));
    }
  }
  return { names, vox: Int32Array.from(vox) };
}

module.exports = { readBuild, readNBT };
