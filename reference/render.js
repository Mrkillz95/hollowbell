// Renders views of an assembled KillzAI build (from read.js output) to PNG,
// and prints a height profile. Usage: node render.js hollowbell
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const NAME = process.argv[2] || 'hollowbell';
const D = __dirname;

const { names, bounds } = JSON.parse(fs.readFileSync(path.join(D, NAME + '_names.json')));
const v = new Int32Array(new Uint8Array(fs.readFileSync(path.join(D, NAME + '_vox.bin'))).buffer);
const [x0, y0, z0, x1, y1, z1] = bounds;

const COL = {
  bone_block: [229, 225, 207], oxidized_copper: [82, 162, 132], weathered_copper: [108, 153, 110],
  calcite: [223, 224, 220], end_stone: [219, 222, 158], glass: [200, 225, 235], concrete: [125, 125, 115],
  end_bricks: [218, 224, 162], verdant_froglight: [229, 244, 228], glowstone: [255, 218, 116],
  sea_lantern: [172, 200, 190], oxidized_cut_copper: [80, 154, 126], weathered_cut_copper: [109, 145, 107],
  honeycomb_block: [229, 148, 29], dripstone_block: [134, 107, 92], tinted_glass: [44, 38, 46],
  moss_block: [89, 109, 45], ochre_froglight: [251, 245, 207], shroomlight: [240, 146, 70],
  pearlescent_froglight: [245, 240, 239], amethyst_block: [133, 97, 191], prismarine: [99, 156, 151],
};
const DYE = { white: [233, 236, 236], lime: [112, 185, 25], yellow: [248, 197, 39], silver: [142, 142, 134],
  gray: [62, 68, 71], cyan: [21, 137, 145], black: [20, 21, 25], green: [84, 109, 27], light_blue: [58, 175, 217],
  blue: [53, 57, 157], purple: [121, 42, 172], magenta: [189, 68, 179], pink: [237, 141, 172], red: [160, 39, 34],
  orange: [240, 118, 19], brown: [114, 71, 40] };
const TERRA = { green: [76, 83, 42], cyan: [86, 91, 91], silver: [135, 106, 97], white: [209, 178, 161], black: [37, 22, 16], gray: [57, 42, 35], lime: [103, 117, 52], yellow: [186, 133, 35] };
function color(n) {
  const m = n.match(/^([a-z_]+)(?:\[color=([a-z_]+)\])?/);
  const b = m[1], c = m[2];
  if (b === 'stained_glass' || b === 'concrete' || b === 'wool' || b === 'concrete_powder') return DYE[c] || [200, 0, 200];
  if (b === 'stained_hardened_clay') return TERRA[c] || [150, 100, 80];
  if (COL[b]) return COL[b];
  if (/copper/.test(b)) return [90, 150, 120];
  let h = 0; for (const ch of b) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return [80 + h % 150, 80 + (h >> 8) % 150, 80 + (h >> 16) % 150];
}
const pal = names.map(color);
const glassy = names.map(n => /glass/.test(n));

function png(file, w, h, rgb) {
  const raw = Buffer.alloc((w * 3 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 3 + 1)] = 0; rgb.copy(raw, y * (w * 3 + 1) + 1, y * w * 3, (y + 1) * w * 3); }
  const crcT = []; for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; crcT[n] = c >>> 0; }
  const crc = b => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
  const chunk = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); };
  const ih = Buffer.alloc(13); ih.writeUInt32BE(w, 0); ih.writeUInt32BE(h, 4); ih[8] = 8; ih[9] = 2;
  fs.writeFileSync(file, Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ih), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]));
}

// Generic projector: for each voxel compute screen (u,v) and depth; nearest wins; shade by depth.
// Optional cut: only voxels passing filter.
function view(file, S, proj, filter, bg = [24, 26, 30]) {
  const pts = [];
  let umin = 1e9, umax = -1e9, vmin = 1e9, vmax = -1e9;
  for (let i = 0; i < v.length; i += 4) {
    const x = v[i], y = v[i + 1], z = v[i + 2];
    if (filter && !filter(x, y, z, v[i + 3])) continue;
    const [u, w, d] = proj(x, y, z);
    pts.push(u, w, d, v[i + 3]);
    if (u < umin) umin = u; if (u > umax) umax = u; if (w < vmin) vmin = w; if (w > vmax) vmax = w;
  }
  const W = Math.ceil((umax - umin + 1) * S) + 8, H = Math.ceil((vmax - vmin + 1) * S) + 8;
  const zb = new Float32Array(W * H).fill(Infinity);
  const id = new Int32Array(W * H).fill(-1);
  const dd = new Float32Array(W * H);
  let dmin = 1e9, dmax = -1e9;
  for (let i = 0; i < pts.length; i += 4) {
    const d = pts[i + 2]; if (d < dmin) dmin = d; if (d > dmax) dmax = d;
    const px = Math.floor((pts[i] - umin) * S) + 4, py = Math.floor((vmax - pts[i + 1]) * S) + 4;
    for (let a = 0; a < Math.ceil(S); a++) for (let b = 0; b < Math.ceil(S); b++) {
      const k = (py + b) * W + px + a; if (d < zb[k]) { zb[k] = d; id[k] = pts[i + 3]; }
    }
  }
  const rgb = Buffer.alloc(W * H * 3);
  for (let k = 0; k < W * H; k++) {
    let c = bg;
    if (id[k] >= 0) {
      const t = 1 - 0.55 * (zb[k] - dmin) / (dmax - dmin + 1e-6);
      // edge darkening from depth discontinuity
      const r = (k % W < W - 1 && zb[k + 1] - zb[k] > 3) || (k + W < W * H && zb[k + W] - zb[k] > 3) ? 0.7 : 1;
      c = pal[id[k]].map(q => Math.min(255, q * t * r));
    }
    rgb[k * 3] = c[0]; rgb[k * 3 + 1] = c[1]; rgb[k * 3 + 2] = c[2];
  }
  png(path.join(D, file), W, H, rgb);
  console.log('wrote', file, W, 'x', H);
}

const cx = 0, cz = -4;
const A = Math.PI / 5, E = Math.PI / 7;
const iso = (x, y, z) => {
  const X = (x - cx) * Math.cos(A) - (z - cz) * Math.sin(A);
  const Z = (x - cx) * Math.sin(A) + (z - cz) * Math.cos(A);
  return [X, y * Math.cos(E) - Z * Math.sin(E), -(Z * Math.cos(E) + y * Math.sin(E))];
};
view(NAME + '_side.png', 3, (x, y, z) => [x, y, -z]);
view(NAME + '_iso.png', 3, iso);
view(NAME + '_iso_below.png', 3, (x, y, z) => { const [a, b, c] = iso(x, -y, z); return [a, -b, c]; });
view(NAME + '_cut.png', 3, (x, y, z) => [x, y, -z], (x, y, z) => z >= cz);
view(NAME + '_top.png', 3, (x, y, z) => [x, -z, -y]);
view(NAME + '_bottom.png', 3, (x, y, z) => [x, z, y]);

// Height profile: per 6-layer band, max radius and top 3 blocks.
const band = 6, prof = {};
for (let i = 0; i < v.length; i += 4) {
  const b = Math.floor(v[i + 1] / band);
  const r = Math.hypot(v[i] - cx, v[i + 2] - cz);
  const P = prof[b] || (prof[b] = { n: 0, rmax: 0, rsum: 0, c: {} });
  P.n++; P.rmax = Math.max(P.rmax, r); P.rsum += r; P.c[names[v[i + 3]]] = (P.c[names[v[i + 3]]] || 0) + 1;
}
for (const b of Object.keys(prof).map(Number).sort((a, b) => b - a)) {
  const P = prof[b];
  const top = Object.entries(P.c).sort((a, c) => c[1] - a[1]).slice(0, 4).map(([n, c]) => `${n} ${Math.round(100 * c / P.n)}%`).join(', ');
  console.log(`y ${String(b * band).padStart(3)}-${String(b * band + band - 1).padStart(3)}  n=${String(P.n).padStart(6)} rmax=${P.rmax.toFixed(0).padStart(3)} ravg=${(P.rsum / P.n).toFixed(0).padStart(3)}  ${top}`);
}
