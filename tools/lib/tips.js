// The ends of his eight big arms, all made the same way. The build's arms run down into the ground, so the ground
// cut left each one ending blunt and ragged in its own way. Each is cut back across the arm where it is still whole,
// and a new end is grown from there: round, narrowing smoothly to a rounded point over the same length for every arm
// (in step with the arm's own thickness), with the arm's own blocks and lights carried on down into it.
const { key, index, N26 } = require('./tidy');

const STEP = 1;        // blocks per slice along the arm
const LONG = 2.6;      // the new end is this many times the arm's radius long
const ROUND = 0.5;     // how full the end stays before it narrows (higher: blunter)

function sub(a, b) { return [a[0] - b[0], a[1] - b[1], a[2] - b[2]]; }
function dot(a, b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
function len(a) { return Math.hypot(a[0], a[1], a[2]); }
function norm(a) { const l = len(a) || 1; return [a[0] / l, a[1] / l, a[2] / l]; }

/** how far along the chain (joints then tip) a point is, by its nearest point on it */
function along(chain, cum, p) {
  let best = Infinity, s = 0;
  for (let i = 0; i + 1 < chain.length; i++) {
    const a = chain[i], d = sub(chain[i + 1], a), L = len(d);
    const t = Math.max(0, Math.min(1, dot(sub(p, a), d) / (L * L)));
    const q = [a[0] + d[0] * t, a[1] + d[1] * t, a[2] + d[2] * t];
    const e = len(sub(p, q));
    if (e < best) { best = e; s = cum[i] + t * L; }
  }
  return s;
}

/** the end's radius at fraction u (0 at the cut, 1 at the point): full, then narrowing to a rounded point */
function radiusAt(r0, u) {
  if (u <= 0) return r0;
  if (u >= 1) return 0;
  const k = Math.max(0, (u - ROUND * 0.3) / (1 - ROUND * 0.3));
  return r0 * Math.pow(Math.cos(k * Math.PI / 2), 0.75) * (1 - 0.08 * u);
}

/** a steady 0..1 for a block, so the same block always gets the same look */
function hash01(x, y, z) {
  let h = Math.imul(x, 73856093) ^ Math.imul(y, 19349663) ^ Math.imul(z, 83492791);
  h = Math.imul(h ^ (h >>> 13), 0x5bd1e995);
  h ^= h >>> 15;
  return (h >>> 0) / 4294967296;
}

function armEnds(vox, rig, palette, pal) {
  // every end is made of the same: the arms' two coppers (in the share they have along the arms), a bone fleck here
  // and there on the outside, and one lime glass loop, like the loops up the arms
  const WEATHERED = pal('minecraft:weathered_copper'), OXIDIZED = pal('minecraft:oxidized_copper');
  const BONE = pal('minecraft:bone_block'), LOOP = pal('minecraft:lime_stained_glass');
  const at = index(vox);
  const report = [];
  rig.arms.forEach((A, a) => {
    const bones = new Set(A.bones);
    const last = A.bones[A.bones.length - 1];
    const chain = [...A.joints, A.tip];
    const cum = [0];
    for (let i = 1; i < chain.length; i++) cum.push(cum[i - 1] + len(sub(chain[i], chain[i - 1])));
    const mine = [];
    for (let i = 0; i < vox.length; i++) if (bones.has(vox[i][4])) mine.push(i);
    // how far each block is from the arm's root, going through the arm itself (so a curling end is followed round,
    // not measured straight): from the blocks at its root, by steps of 1, 1.4 and 1.7 to its neighbours
    const sOf = new Map();
    const mineAt = new Map(mine.map(i => [key(vox[i][0], vox[i][1], vox[i][2]), i]));
    const buckets = [];
    const put = (i, d10) => { (buckets[d10] || (buckets[d10] = [])).push(i); };
    for (const i of mine)
      if (along(chain, cum, [vox[i][0] + 0.5, vox[i][1] + 0.5, vox[i][2] + 0.5]) < 2) { sOf.set(i, 0); put(i, 0); }
    for (let d10 = 0; d10 < buckets.length; d10++) {
      const bk = buckets[d10];
      if (!bk) continue;
      for (const i of bk) {
        if (sOf.get(i) * 10 < d10 - 0.5) continue;
        const [x, y, z] = vox[i];
        for (const [dx, dy, dz] of N26) {
          const j = mineAt.get(key(x + dx, y + dy, z + dz));
          if (j === undefined) continue;
          const w = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
          const nd = d10 + (w === 1 ? 10 : w === 2 ? 14 : 17);
          if (!sOf.has(j) || sOf.get(j) * 10 > nd) { sOf.set(j, nd / 10); put(j, nd); }
        }
      }
    }
    let sEnd = 0, farthest = null;
    for (const i of mine) {
      if (!sOf.has(i)) sOf.set(i, 0);
      if (sOf.get(i) > sEnd) { sEnd = sOf.get(i); farthest = vox[i]; }
    }
    // slices along the arm: where its middle is and how thick it is
    const slices = new Map();
    for (const i of mine) {
      const k = Math.floor(sOf.get(i) / STEP);
      const e = slices.get(k) || { n: 0, x: 0, y: 0, z: 0 };
      e.n++; e.x += vox[i][0] + 0.5; e.y += vox[i][1] + 0.5; e.z += vox[i][2] + 0.5;
      slices.set(k, e);
    }
    const mid = k => { const e = slices.get(k); return e ? [e.x / e.n, e.y / e.n, e.z / e.n] : null; };
    // (the arm's outside: most of a slice's blocks lie within this of its middle; an arm may be hollow inside)
    const radii = new Map();
    for (const i of mine) {
      const k = Math.floor(sOf.get(i) / STEP), c = mid(k);
      const r = len(sub([vox[i][0] + 0.5, vox[i][1] + 0.5, vox[i][2] + 0.5], c));
      if (!radii.has(k)) radii.set(k, []);
      radii.get(k).push(r);
    }
    for (const l of radii.values()) l.sort((p, q) => p - q);
    const rad = k => { const l = radii.get(k); return l ? l[Math.floor(l.length * 0.9)] + 0.5 : 0; };
    const kEnd = Math.floor(sEnd / STEP);
    // how thick the arm is a little way up from its end, where it's whole
    const refs = [];
    for (let k = kEnd - 45; k <= kEnd - 25; k++) if (slices.has(k)) refs.push(rad(k));
    refs.sort((p, q) => p - q);
    const rRef = refs.length ? refs[refs.length >> 1] : rad(kEnd - 10);
    // the arm across, at a cut: where its middle is, which way it runs, and its own two widths at right angles (not
    // always round: some are flattened)
    const across = kCut => {
      const c0 = mid(kCut), cUp = mid(kCut - 5) || mid(kCut - 3);
      const d = norm(sub(c0, cUp));
      let e1 = norm(Math.abs(d[1]) < 0.9 ? [d[2], 0, -d[0]] : [1, 0, 0]);
      e1 = norm(sub(e1, d.map(v => v * dot(e1, d))));
      let e2 = [d[1] * e1[2] - d[2] * e1[1], d[2] * e1[0] - d[0] * e1[2], d[0] * e1[1] - d[1] * e1[0]];
      const ring = [];
      for (const i of mine) {
        const r = sub([vox[i][0] + 0.5, vox[i][1] + 0.5, vox[i][2] + 0.5], c0);
        if (Math.abs(dot(r, d)) < 3) ring.push([dot(r, e1), dot(r, e2)]);
      }
      let sxx = 0, syy = 0, sxy = 0;
      for (const [u, v] of ring) { sxx += u * u; syy += v * v; sxy += u * v; }
      const th = 0.5 * Math.atan2(2 * sxy, sxx - syy);
      const a1 = [Math.cos(th), Math.sin(th)];
      const f1 = e1.map((v, i) => v * a1[0] + e2[i] * a1[1]), f2 = e1.map((v, i) => -v * a1[1] + e2[i] * a1[0]);
      e1 = f1; e2 = f2;
      const pct = (l, q) => { l.sort((p, q2) => p - q2); return l.length ? l[Math.floor((l.length - 1) * q)] : 0; };
      // (its middle across from its two sides, not the average block: some arms are hollow or lopsided inside)
      const us = ring.map(([u, v]) => u * a1[0] + v * a1[1]), vs = ring.map(([u, v]) => -u * a1[1] + v * a1[0]);
      const u0 = pct([...us], 0.015), u1 = pct([...us], 0.985), v0 = pct([...vs], 0.015), v1 = pct([...vs], 0.985);
      const cu = (u0 + u1) / 2, cv = (v0 + v1) / 2;
      for (let i = 0; i < 3; i++) c0[i] += e1[i] * cu + e2[i] * cv;
      const ra = Math.max(2, (u1 - u0) / 2 + 0.6), rb = Math.max(2, (v1 - v0) / 2 + 0.6);
      return { c0, d, e1, e2, ra, rb, T: LONG * (ra + rb) / 2 };
    };
    // cut where the new end, grown on from there, comes back to about where the arm ended (its length is in step with
    // the arm's thickness at the cut, so that's found twice)
    let kCut = Math.floor((sEnd - LONG * rRef) / STEP);
    let X = across(kCut);
    kCut = Math.floor((sEnd - X.T) / STEP);
    X = across(kCut);
    const { c0, d, e1, e2, ra, rb } = X, Tg = X.T, r0 = Math.max(ra, rb);
    // off goes everything of the arm past the cut, across the arm there
    const gone = new Set();
    for (const i of mine) {
      const p = [vox[i][0] + 0.5, vox[i][1] + 0.5, vox[i][2] + 0.5];
      const t = dot(sub(p, c0), d), sI = sOf.get(i);
      if ((t > 0 && sI > kCut * STEP - r0) || sI > kCut * STEP + 2) gone.add(i);
      else if (t > -4 && sI > kCut * STEP - 2 * r0) {
        // (a bit of the arm just up from the cut that sticks out past where the new end starts)
        const rel = sub(p, c0), ua = dot(rel, e1) / ra, ub = dot(rel, e2) / rb;
        if (ua * ua + ub * ub > 1.35 * 1.35) gone.add(i);
      }
    }
    // what the arm is made of just up from the cut, to carry on down the new end: by place across the arm
    const src = [];
    for (const i of mine) {
      if (gone.has(i)) continue;
      const p = [vox[i][0] + 0.5, vox[i][1] + 0.5, vox[i][2] + 0.5];
      const t = dot(sub(p, c0), d);
      if (t > -14 && t <= 0) src.push([p, vox[i][3], t]);
    }
    const look = new Map();
    for (const [p, pal] of src) look.set(key(Math.floor(p[0]), Math.floor(p[1]), Math.floor(p[2])), pal);
    const copyFrom = (p, t, rHere) => {
      // straight back up the arm, out to its own surface: the same spot of the pattern, a band further up
      const back = 1 + (Math.floor(t) % 12);
      const rel = sub(p, [c0[0] + d[0] * t, c0[1] + d[1] * t, c0[2] + d[2] * t]);
      const sc = rHere > 0.5 ? Math.min(3, r0 / rHere) : 1;
      for (let grow = 0; grow < 4; grow++) {
        const q = [c0[0] - d[0] * (back + grow) + rel[0] * sc, c0[1] - d[1] * (back + grow) + rel[1] * sc,
          c0[2] - d[2] * (back + grow) + rel[2] * sc];
        for (const [dx, dy, dz] of [[0, 0, 0], ...N26]) {
          const v = look.get(key(Math.floor(q[0]) + dx, Math.floor(q[1]) + dy, Math.floor(q[2]) + dz));
          if (v !== undefined) return v;
        }
      }
      return undefined;
    };
    // the new end
    const added = [];
    const R = Math.ceil(Math.max(ra, rb) + 2), Tn = Math.ceil(Tg + 2);
    const base = [Math.floor(c0[0]), Math.floor(c0[1]), Math.floor(c0[2])];
    const reach = Tn + R;
    const filled = new Set();
    for (let dx = -reach; dx <= reach; dx++) for (let dy = -reach; dy <= reach; dy++) for (let dz = -reach; dz <= reach; dz++) {
      const x = base[0] + dx, y = base[1] + dy, z = base[2] + dz;
      const p = [x + 0.5, y + 0.5, z + 0.5];
      const t = dot(sub(p, c0), d);
      if (t <= -0.5 || t > Tg) continue;
      const rel = sub(p, [c0[0] + d[0] * t, c0[1] + d[1] * t, c0[2] + d[2] * t]);
      const rHere = radiusAt(r0, t / Tg);
      const f = rHere / r0, ua = dot(rel, e1) / (ra * f), ub = dot(rel, e2) / (rb * f);
      if (f <= 0 || ua * ua + ub * ub > 1) continue;
      const k = key(x, y, z);
      const j = at.get(k);
      if (j !== undefined && !gone.has(j)) continue;
      const outside = Math.sqrt(ua * ua + ub * ub) > 1 - 1.5 / Math.max(1, rHere);
      const u = t / Tg, h = hash01(x, y, z);
      let block = h < 0.58 ? WEATHERED : OXIDIZED;
      if (outside && Math.abs(u - 0.34) < 0.6 / Tg * 1.2) block = LOOP;
      else if (outside && hash01(z, x, y) < 0.04) block = BONE;
      added.push([x, y, z, block, last, 255]);
      filled.add(k);
    }
    report.push({ arm: a, r: +r0.toFixed(1), long: +Tg.toFixed(1), cut: gone.size, grown: added.length,
      point: c0.map((v, i) => +(v + d[i] * Tg).toFixed(1)), oldTip: A.tip.map(v => +v.toFixed(1)), end: farthest.slice(0, 3), sEnd: +sEnd.toFixed(1) });
    // the arm's own blocks past the cut go, the new end goes on
    let kept = 0;
    for (let i = 0; i < vox.length; i++) if (!gone.has(i)) vox[kept++] = vox[i];
    vox.length = kept;
    for (const v of added) vox.push(v);
    // (rebuilt the place index for the next arm)
    at.clear();
    for (let i = 0; i < vox.length; i++) at.set(key(vox[i][0], vox[i][1], vox[i][2]), i);
  });
  // nothing of an arm left loose: only each arm's one piece stays
  const armOf = new Map();
  rig.arms.forEach((A, a) => A.bones.forEach(b => armOf.set(b, a)));
  const comp = new Int32Array(vox.length).fill(-1), sizes = [], whose = [];
  for (let s0 = 0; s0 < vox.length; s0++) {
    const a = armOf.get(vox[s0][4]);
    if (a === undefined || comp[s0] >= 0) continue;
    const id = sizes.length, st = [s0];
    comp[s0] = id;
    let size = 0;
    while (st.length) {
      const i = st.pop(); size++;
      const [x, y, z] = vox[i];
      for (const [dx, dy, dz] of N26) {
        const j = at.get(key(x + dx, y + dy, z + dz));
        if (j !== undefined && comp[j] < 0 && armOf.get(vox[j][4]) === a) { comp[j] = id; st.push(j); }
      }
    }
    sizes.push(size); whose.push(a);
  }
  const biggest = new Map();
  sizes.forEach((sz, id) => { if (!biggest.has(whose[id]) || sizes[biggest.get(whose[id])] < sz) biggest.set(whose[id], id); });
  let kept = 0, loose = 0;
  for (let i = 0; i < vox.length; i++) {
    if (comp[i] >= 0 && biggest.get(whose[comp[i]]) !== comp[i]) { loose++; continue; }
    vox[kept++] = vox[i];
  }
  vox.length = kept;
  report.loose = loose;
  return report;
}

module.exports = { armEnds };
