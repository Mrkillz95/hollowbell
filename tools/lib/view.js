// Draws voxels to PNG from a few directions, with simple depth shading (like reference/render.js).
const { writePng } = require('./png');

const COL = {
  bone_block: [229, 225, 207], oxidized_copper: [82, 162, 132], weathered_copper: [108, 153, 110],
  calcite: [223, 224, 220], end_stone: [219, 222, 158], glass: [200, 225, 235],
  end_stone_bricks: [218, 224, 162], verdant_froglight: [229, 244, 228], glowstone: [255, 218, 116],
  sea_lantern: [172, 200, 190], oxidized_cut_copper: [80, 154, 126], weathered_cut_copper: [109, 145, 107],
  honeycomb_block: [229, 148, 29], dripstone_block: [134, 107, 92], tinted_glass: [44, 38, 46],
  moss_block: [89, 109, 45], ochre_froglight: [251, 245, 207], shroomlight: [240, 146, 70],
};
const DYE = { white: [233, 236, 236], lime: [112, 185, 25], yellow: [248, 197, 39], light_gray: [142, 142, 134],
  gray: [62, 68, 71], cyan: [21, 137, 145], black: [20, 21, 25], green: [84, 109, 27] };
const TERRA = { green: [76, 83, 42], cyan: [86, 91, 91], light_gray: [135, 106, 97] };

function blockColour(java) {
  const n = java.replace('minecraft:', '');
  let m;
  if ((m = n.match(/^([a-z_]+?)_(stained_glass|concrete)$/))) return DYE[m[1]] || [255, 0, 255];
  if ((m = n.match(/^([a-z_]+?)_terracotta$/))) return TERRA[m[1]] || [255, 0, 255];
  return COL[n] || [255, 0, 255];
}

/** pts: flat array x,y,z,r,g,b. proj(x,y,z) -> [u, v, depth] (smaller depth = nearer). */
function view(file, pts, proj, S = 3, bg = [24, 26, 30]) {
  const P = [];
  let umin = 1e9, umax = -1e9, vmin = 1e9, vmax = -1e9;
  for (let i = 0; i < pts.length; i += 6) {
    const [u, w, d] = proj(pts[i], pts[i + 1], pts[i + 2]);
    P.push(u, w, d, i);
    if (u < umin) umin = u; if (u > umax) umax = u; if (w < vmin) vmin = w; if (w > vmax) vmax = w;
  }
  const W = Math.ceil((umax - umin + 1) * S) + 8, H = Math.ceil((vmax - vmin + 1) * S) + 8;
  const zb = new Float32Array(W * H).fill(Infinity);
  const id = new Int32Array(W * H).fill(-1);
  let dmin = 1e9, dmax = -1e9;
  const cs = Math.ceil(S);
  for (let i = 0; i < P.length; i += 4) {
    const d = P[i + 2]; if (d < dmin) dmin = d; if (d > dmax) dmax = d;
    const px = Math.floor((P[i] - umin) * S) + 4, py = Math.floor((vmax - P[i + 1]) * S) + 4;
    for (let a = 0; a < cs; a++) for (let b = 0; b < cs; b++) {
      const k = (py + b) * W + px + a; if (d < zb[k]) { zb[k] = d; id[k] = P[i + 3]; }
    }
  }
  const rgb = Buffer.alloc(W * H * 3);
  for (let k = 0; k < W * H; k++) {
    let c = bg;
    if (id[k] >= 0) {
      const t = 1 - 0.55 * (zb[k] - dmin) / (dmax - dmin + 1e-6);
      const r = (k % W < W - 1 && zb[k + 1] - zb[k] > 3) || (k + W < W * H && zb[k + W] - zb[k] > 3) ? 0.7 : 1;
      const j = id[k];
      c = [pts[j + 3], pts[j + 4], pts[j + 5]].map(q => Math.min(255, q * t * r));
    }
    rgb[k * 3] = c[0]; rgb[k * 3 + 1] = c[1]; rgb[k * 3 + 2] = c[2];
  }
  writePng(file, W, H, rgb);
  return [W, H];
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

// A set of clearly different colours for showing which part each voxel went to.
function partColour(k) {
  const h = (k * 0.61803398875) % 1, s = 0.65, l = 0.55;
  const f = n => { const q = (n + h * 12) % 12; return l - s * Math.min(l, 1 - l) * Math.max(-1, Math.min(q - 3, 9 - q, 1)); };
  return [f(0) * 255, f(8) * 255, f(4) * 255].map(Math.round);
}

module.exports = { view, PROJ, blockColour, partColour };
