#!/usr/bin/env node
// Draws the converted model (src/main/resources/hollowbell/hollowbell_model.bin) to PNG so it can be checked
// against the pictures of the build in reference/. Writes to reference/converted/:
//   front, side, top, iso: block colours, glass see-through (blended)
//   cutaway: the front half cut away
//   parts_front, parts_iso: every part in its own colour
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const { writePng } = require('./lib/png');
const { blockColour, partColour } = require('./lib/view');

const ROOT = path.join(__dirname, '..');
const RES = path.join(ROOT, 'src', 'main', 'resources', 'hollowbell');
const OUT = path.join(ROOT, 'reference', 'converted');

function readModel(file = path.join(RES, 'hollowbell_model.bin')) {
  const b = zlib.gunzipSync(fs.readFileSync(file));
  let p = 0;
  const i32 = () => { const v = b.readInt32BE(p); p += 4; return v; };
  const utf = () => { const n = b.readInt16BE(p); p += 2; const s = b.toString('utf8', p, p + n); p += n; return s; };
  if (b.toString('ascii', 0, 4) !== 'HBEL') throw new Error('not a Hollowbell model');
  p = 4; i32();
  const np = i32(); const palette = []; for (let i = 0; i < np; i++) palette.push(utf());
  const nb = i32(); const bones = [];
  for (let i = 0; i < nb; i++) {
    const name = utf(); const n = i32();
    const v = [];
    for (let j = 0; j < n; j++) v.push([b.readInt16BE(p + j * 9), b.readInt16BE(p + j * 9 + 2), b.readInt16BE(p + j * 9 + 4), b.readInt16BE(p + j * 9 + 6), b[p + j * 9 + 8], 255]);
    p += n * 9;
    if (b[p++]) { for (let j = 0; j < n; j++) v[j][5] = b[p + j]; p += n; }
    bones.push({ name, v });
  }
  return { palette, bones };
}

// A small ray-free renderer: every voxel is splatted as a square; glass is blended over what's behind it.
function draw(file, items, proj, S, opts = {}) {
  const P = [];
  let umin = 1e9, umax = -1e9, vmin = 1e9, vmax = -1e9;
  for (const it of items) {
    const [u, w, d] = proj(it[0], it[1], it[2]);
    P.push([u, w, d, it]);
    umin = Math.min(umin, u); umax = Math.max(umax, u); vmin = Math.min(vmin, w); vmax = Math.max(vmax, w);
  }
  const W = Math.ceil((umax - umin + 1) * S) + 8, H = Math.ceil((vmax - vmin + 1) * S) + 8;
  const zb = new Float32Array(W * H).fill(Infinity);
  const col = new Float32Array(W * H * 3);
  const bg = opts.bg || [24, 26, 30];
  for (let k = 0; k < W * H; k++) col.set(bg, k * 3);
  let dmin = 1e9, dmax = -1e9;
  for (const q of P) { dmin = Math.min(dmin, q[2]); dmax = Math.max(dmax, q[2]); }
  const cs = Math.ceil(S);
  // opaque first (nearest wins), then glass from far to near blended on top
  const opaque = P.filter(q => !q[3][4]);
  const clear = P.filter(q => q[3][4]).sort((a, b) => b[2] - a[2]);
  const shade = d => 1 - 0.5 * (d - dmin) / (dmax - dmin + 1e-6);
  for (const [u, w, d, it] of opaque) {
    const px = Math.floor((u - umin) * S) + 4, py = Math.floor((vmax - w) * S) + 4;
    for (let a = 0; a < cs; a++) for (let b = 0; b < cs; b++) {
      const k = (py + b) * W + px + a;
      if (d < zb[k]) { zb[k] = d; const t = shade(d); col[k * 3] = it[3][0] * t; col[k * 3 + 1] = it[3][1] * t; col[k * 3 + 2] = it[3][2] * t; }
    }
  }
  for (const [u, w, d, it] of clear) {
    const px = Math.floor((u - umin) * S) + 4, py = Math.floor((vmax - w) * S) + 4;
    const al = it[5];
    for (let a = 0; a < cs; a++) for (let b = 0; b < cs; b++) {
      const k = (py + b) * W + px + a;
      if (d < zb[k]) { const t = shade(d); for (let c = 0; c < 3; c++) col[k * 3 + c] = col[k * 3 + c] * (1 - al) + it[3][c] * t * al; }
    }
  }
  const rgb = Buffer.alloc(W * H * 3);
  for (let k = 0; k < W * H * 3; k++) rgb[k] = Math.max(0, Math.min(255, Math.round(col[k])));
  writePng(file, W, H, rgb);
  console.log('wrote', path.relative(ROOT, file), W + 'x' + H);
}

const A = Math.PI / 5, E = Math.PI / 7;
const PROJ = {
  front: (x, y, z) => [x, y, -z],
  side: (x, y, z) => [-z, y, -x],
  top: (x, y, z) => [x, -z, -y],
  iso: (x, y, z) => {
    const X = x * Math.cos(A) - z * Math.sin(A);
    const Z = x * Math.sin(A) + z * Math.cos(A);
    return [X, y * Math.cos(E) - Z * Math.sin(E), -(Z * Math.cos(E) + y * Math.sin(E))];
  },
};

function main() {
  fs.mkdirSync(OUT, { recursive: true });
  const m = readModel();
  const cols = m.palette.map(blockColour);
  const glassAlpha = m.palette.map(n => /tinted_glass/.test(n) ? 0.85 : /stained_glass/.test(n) ? 0.55 : /glass/.test(n) ? 0.25 : 0);
  const body = [], parts = [];
  const kinds = {};
  m.bones.forEach((b, bi) => {
    const kind = b.name.replace(/_\d+$/, '').replace(/_\d+$/, '');
    kinds[kind] = (kinds[kind] || 0) + 1;
    const pc = b.name === 'bell' ? [70, 150, 120] : b.name === 'rim' ? [200, 200, 200] : b.name === 'crown' ? [255, 210, 0]
      : b.name.startsWith('spot_') ? [255, 110, 0] : b.name.startsWith('pod_') ? [255, 0, 255] : b.name.startsWith('egg_') ? [140, 70, 20] : partColour(bi);
    for (const v of b.v) {
      const g = glassAlpha[v[3]];
      const a = v[5] / 255;
      const it = [v[0], v[1], v[2], cols[v[3]], g > 0 || a < 1, g > 0 ? g * a : a];
      body.push(it);
      parts.push([v[0], v[1], v[2], pc, false, 1]);
    }
  });
  draw(path.join(OUT, 'front.png'), body, PROJ.front, 3);
  draw(path.join(OUT, 'side.png'), body, PROJ.side, 3);
  draw(path.join(OUT, 'top.png'), body, PROJ.top, 3);
  draw(path.join(OUT, 'iso.png'), body, PROJ.iso, 3);
  draw(path.join(OUT, 'cutaway.png'), body.filter(v => v[2] >= 0), (x, y, z) => [x, y, z], 3);
  draw(path.join(OUT, 'parts_front.png'), parts, PROJ.front, 2.5);
  draw(path.join(OUT, 'parts_iso.png'), parts, PROJ.iso, 2.5);
  console.log('bones by kind:', JSON.stringify(kinds));
}

if (require.main === module) main();
module.exports = { readModel, draw, PROJ, OUT };
