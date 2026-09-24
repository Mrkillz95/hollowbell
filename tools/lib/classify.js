// Sorts every voxel of the build into a part: the bell, rim, crown, the five glowing spots,
// the 8 arms, the strands, the pods and the egg clumps.
//
// What the build is made of (found by looking at it, see DESIGN.md "Guesses"):
//  - y 132+ is the bell: a thin double dome of copper ribs and glass, the rim band at y 132-146.
//  - Inside the dome hangs a hollow glowing vase (glowstone and yellow glass) under the crown, and four
//    yellow glass balls around it that show through the dome as the glowing spots. The vase is the fifth spot.
//  - The 8 arms are hollow copper tubes (weathered and oxidized copper, not cut) with bone speckles and
//    lime loops, rooted under the rim.
//  - The strands are bone and calcite with verdant froglight and oxidized cut copper bits.
//  - The pods are hollow glass balls (white and clear glass, sea lantern inside, black and tinted glass eyes,
//    a lime and yellow bottom) hanging in the middle.
//  - The egg clumps are grey concrete lumps with honeycomb, dripstone and cyan terracotta.
const { Grid } = require('./grid');

const RIM_Y = 132;
const RIM_TOP = 146;

const N26 = [];
for (let a = -1; a <= 1; a++) for (let b = -1; b <= 1; b++) for (let c = -1; c <= 1; c++) if (a || b || c) N26.push([a, b, c]);
const N6 = [[0, -1, 0], [0, 1, 0], [0, 0, -1], [0, 0, 1], [-1, 0, 0], [1, 0, 0]];

function classify(M) {
  const g = M.grid;
  const P = M.palette.map(s => s.replace('minecraft:', ''));
  const is = re => P.map(n => re.test(n));
  const YELLOWISH = is(/^(glowstone|yellow_stained_glass|ochre_froglight|shroomlight)$/);
  const ARM_COPPER = is(/^(weathered_copper|oxidized_copper)$/);
  const POD_MAT = is(/^(white_stained_glass|glass|sea_lantern|black_concrete|tinted_glass|lime_stained_glass|yellow_stained_glass|light_gray_stained_glass|ochre_froglight|shroomlight)$/);
  const EGG_MAT = is(/^(gray_concrete|light_gray_concrete|honeycomb_block|dripstone_block|cyan_terracotta|shroomlight|tinted_glass)$/);
  const STRAND_MAT = is(/^(bone_block|calcite|verdant_froglight|oxidized_cut_copper|sea_lantern|light_gray_stained_glass|glass|white_stained_glass)$/);
  const PALE = is(/^(bone_block|calcite)$/);
  const LIME = is(/^lime_stained_glass$/);

  // every voxel: its position and palette, in one list; label per voxel
  const idx = [];
  g.forEach((x, y, z, p, i) => idx.push(i));
  const n = idx.length;
  const at = new Map(); idx.forEach((gi, k) => at.set(gi, k));
  const X = new Int16Array(n), Y = new Int16Array(n), Z = new Int16Array(n), PAL = new Int16Array(n);
  idx.forEach((gi, k) => { const [x, y, z] = Grid.unidx(gi); X[k] = x; Y[k] = y; Z[k] = z; PAL[k] = g.a[gi] - 1; });
  const find = (x, y, z) => Grid.inside(x, y, z) ? at.get(Grid.idx(x, y, z)) : undefined;
  const R = k => Math.hypot(X[k], Z[k]);

  // label: string part names, filled in stages
  const label = new Array(n).fill(null);

  // ---- the crown: the cap on top
  for (let k = 0; k < n; k++) {
    if ((Y[k] >= 196 && R(k) <= 16) || (Y[k] >= 190 && R(k) <= 16 && YELLOWISH[PAL[k]])) label[k] = 'crown';
  }
  // ---- the vase (fifth spot) and the four balls: the yellow stuff inside the dome, found by flood fill
  // from the vase's glowstone, never crossing into the dome shell (which is kept out by radius and height)
  const inner = k => YELLOWISH[PAL[k]] && label[k] === null && Y[k] >= 118 && Y[k] <= 192;
  {
    let start = -1;
    for (let k = 0; k < n; k++) if (P[PAL[k]] === 'glowstone' && Y[k] === 150 && R(k) < 22) { start = k; break; }
    if (start < 0) throw new Error('could not find the glowing vase');
    const st = [start]; const seen = new Set([start]);
    const found = [];
    while (st.length) {
      const k = st.pop(); found.push(k);
      for (const [a, b, c] of N26) {
        const j = find(X[k] + a, Y[k] + b, Z[k] + c);
        if (j === undefined || seen.has(j) || !inner(j)) continue;
        // the dome shell is at least this far out at each height; the balls stay inside it
        seen.add(j); st.push(j);
      }
    }
    // the vase is the middle; the balls are the rest, sorted into four by angle
    const balls = found.filter(k => R(k) > 23);
    for (const k of found) if (R(k) <= 23) label[k] = 'spot_4';
    const quads = [[], [], [], []];
    // the balls sit at about -155, -65, 25 and 115 degrees
    const centres = [-155, -65, 25, 115].map(d => d * Math.PI / 180);
    for (const k of balls) {
      const a = Math.atan2(Z[k], X[k]);
      let best = 0, bd = 1e9;
      centres.forEach((c, i) => { const d = Math.abs(Math.atan2(Math.sin(a - c), Math.cos(a - c))); if (d < bd) { bd = d; best = i; } });
      quads[best].push(k);
      label[k] = 'spot_' + best;
    }
    // the yellow patches of the dome right above each ball glow with it: part of the same spot
    for (let k = 0; k < n; k++) {
      if (label[k] !== null || !YELLOWISH[PAL[k]] || Y[k] < 170 || Y[k] > 195) continue;
      const r = R(k); if (r < 24 || r > 52) continue;
      const a = Math.atan2(Z[k], X[k]);
      centres.forEach((c, i) => { if (Math.abs(Math.atan2(Math.sin(a - c), Math.cos(a - c))) < 0.42) label[k] = 'spot_' + i; });
    }
  }
  // bits of other blocks inside the balls (the balls are speckled) go with the ball round them
  for (let pass = 0; pass < 3; pass++) {
    const set = [];
    for (let k = 0; k < n; k++) {
      if (label[k] !== null || Y[k] < 118 || Y[k] > 192) continue;
      const cnt = {};
      for (const [a, b, c] of N26) { const j = find(X[k] + a, Y[k] + b, Z[k] + c); if (j !== undefined && label[j] && label[j].startsWith('spot_')) cnt[label[j]] = (cnt[label[j]] || 0) + 1; }
      const best = Object.entries(cnt).sort((a, b) => b[1] - a[1])[0];
      if (best && best[1] >= 4) set.push([k, best[0]]);
    }
    for (const [k, L] of set) label[k] = L;
  }
  // ---- bell and rim
  for (let k = 0; k < n; k++) {
    if (label[k] !== null || Y[k] < RIM_Y) continue;
    const pal = P[PAL[k]];
    if (Y[k] <= 137 || (Y[k] <= RIM_TOP && R(k) >= 70 && /weathered|bone|calcite/.test(pal))) label[k] = 'rim';
    else label[k] = 'bell';
  }

  // ---- below the rim: arms, strands, pods, egg clumps, by a race outward from seeds of each
  const below = k => label[k] === null;
  const lab = new Int32Array(n).fill(-1);       // -1 none; 0-7 arm; 100 strand; 200 pod; 300 egg
  const since = new Uint8Array(n);              // arm label: steps since the last copper
  let front = [];
  // arm roots: arm copper just under the rim, far out, sorted by angle into 8 by finding the gaps
  {
    const roots = [];
    for (let k = 0; k < n; k++) if (below(k) && ARM_COPPER[PAL[k]] && Y[k] >= 122 && R(k) >= 50) roots.push(k);
    const ang = k => Math.atan2(Z[k], X[k]);
    const sorted = roots.map(k => [ang(k), k]).sort((a, b) => a[0] - b[0]);
    // the 8 biggest gaps in angle split them
    const gaps = [];
    for (let i = 0; i < sorted.length; i++) {
      const a0 = sorted[i][0], a1 = i + 1 < sorted.length ? sorted[i + 1][0] : sorted[0][0] + 2 * Math.PI;
      gaps.push([a1 - a0, i]);
    }
    gaps.sort((a, b) => b[0] - a[0]);
    const cuts = gaps.slice(0, 8).map(g => g[1]).sort((a, b) => a - b);
    let arm = 0;
    const armOfRank = new Array(sorted.length);
    for (let i = 0; i < sorted.length; i++) {
      // the group after cut c
      let gi = cuts.findIndex(c => i <= c);
      if (gi < 0) gi = 0;
      armOfRank[i] = gi;
    }
    // number the arms by angle, starting from +x going round
    const groupAng = new Array(8).fill(0).map(() => [0, 0]);
    sorted.forEach(([a, k], i) => { const G = groupAng[armOfRank[i]]; G[0] += Math.cos(a); G[1] += Math.sin(a); });
    const order = groupAng.map((G, i) => [((Math.atan2(G[1], G[0]) + 2 * Math.PI) % (2 * Math.PI)), i]).sort((a, b) => a[0] - b[0]);
    const rename = new Array(8); order.forEach(([, i], j) => { rename[i] = j; });
    sorted.forEach(([a, k], i) => { lab[k] = rename[armOfRank[i]]; front.push(k); });
    arm = 8;
  }
  // other seeds
  // distance from arm copper and lime (so a strand seed is never a speckle or a loop middle of an arm)
  const nearArm = new Uint8Array(n).fill(255);
  {
    let f = [];
    for (let k = 0; k < n; k++) if (below(k) && (ARM_COPPER[PAL[k]] || (LIME[PAL[k]] && R(k) > 32))) { nearArm[k] = 0; f.push(k); }
    for (let d = 1; d <= 4 && f.length; d++) {
      const nf = [];
      for (const k of f) for (const [a, b, c] of N26) {
        const j = find(X[k] + a, Y[k] + b, Z[k] + c);
        if (j !== undefined && nearArm[j] === 255) { nearArm[j] = d; nf.push(j); }
      }
      f = nf;
    }
  }
  for (let k = 0; k < n; k++) {
    if (!below(k) || lab[k] >= 0) continue;
    const pal = P[PAL[k]];
    if (pal === 'black_concrete') { lab[k] = 200; front.push(k); }
    else if (pal === 'honeycomb_block' || pal === 'dripstone_block') { lab[k] = 300; front.push(k); }
    else if (PALE[PAL[k]] && nearArm[k] === 255) { lab[k] = 100; front.push(k); }
  }
  const allowed = (L, j, s) => {
    const p = PAL[j];
    if (L < 8) return ARM_COPPER[p] || s < (LIME[p] || /white_stained|light_gray_stained|glass$/.test(P[p]) ? 6 : 3);
    if (L === 100) return STRAND_MAT[p];
    if (L === 200) return POD_MAT[p];
    if (L === 300) return EGG_MAT[p];
    return false;
  };
  while (front.length) {
    const nf = [];
    for (const k of front) {
      const L = lab[k];
      for (const [a, b, c] of N26) {
        const j = find(X[k] + a, Y[k] + b, Z[k] + c);
        if (j === undefined || !below(j) || lab[j] >= 0) continue;
        const s = L < 8 ? (ARM_COPPER[PAL[j]] ? 0 : since[k] + 1) : 0;
        if (!allowed(L, j, s)) continue;
        lab[j] = L; since[j] = s; nf.push(j);
      }
    }
    front = nf;
  }
  // whatever no race reached goes to the nearest labelled neighbour (repeat until all are in)
  for (let pass = 0; pass < 60; pass++) {
    let left = 0; const set = [];
    for (let k = 0; k < n; k++) {
      if (!below(k) || lab[k] >= 0) continue;
      let got = -1;
      for (const [a, b, c] of N26) { const j = find(X[k] + a, Y[k] + b, Z[k] + c); if (j !== undefined && below(j) && lab[j] >= 0) { got = lab[j]; break; } }
      if (got >= 0) set.push([k, got]); else left++;
    }
    for (const [k, L] of set) lab[k] = L;
    if (!left) break;
  }
  // floating bits with no neighbour at all: nearest by distance
  const floating = [];
  for (let k = 0; k < n; k++) if (below(k) && lab[k] < 0) floating.push(k);
  if (floating.length) {
    const labelled = []; for (let k = 0; k < n; k++) if (below(k) && lab[k] >= 0) labelled.push(k);
    for (const k of floating) {
      let bd = 1e18, bl = 100;
      for (const j of labelled) { const d = (X[j] - X[k]) ** 2 + (Y[j] - Y[k]) ** 2 + (Z[j] - Z[k]) ** 2; if (d < bd) { bd = d; bl = lab[j]; } }
      lab[k] = bl;
    }
  }
  return { n, X, Y, Z, PAL, P, label, lab, find, floating: floating.length };
}

module.exports = { classify, N26, N6, RIM_Y };
