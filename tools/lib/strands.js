// The strands drawn again, all alike: each one a round tentacle laid along its own joints (the way it hangs and bends
// in the game stays as it was), thick at the top and thinning evenly to a point, in the build's blocks: bone and
// calcite in a slow twist, a copper band now and then, and froglight dots. As built they were ragged ribbons, some
// grown together into wide sheets, with loose specks; this way every strand is one clean piece.
//
// Nothing else changes: the arms, the bell, the pods and the rest keep every block, and a strand never takes a block
// that something else has. Each strand is joined on to what it hangs from, and each pod to the strand it hangs on.

const { key, index } = require('./tidy');

/** radius at the top of a strand hung from the rim, an arm or the glowing vase; one hung from a pod or a strand */
const TOP = 2.6, TOP_LOW = 2.0;
/** radius at the very end */
const END = 1.0;
/** a copper band every so many blocks along a strand, and a froglight dot every so many */
const BAND = 14, DOT = 6;

function sub(a, b) { return [a[0] - b[0], a[1] - b[1], a[2] - b[2]]; }
function dot(a, b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
function cross(a, b) { return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]; }
function len(a) { return Math.hypot(a[0], a[1], a[2]); }
function norm(a) { const l = len(a) || 1; return [a[0] / l, a[1] / l, a[2] / l]; }

/**
 * vox: [x, y, z, pal, bone, alpha] (changed in place); rig: the rig (strands, pods); bones: names and parents;
 * pal: name to palette index. Returns how many blocks the strands had before and have now, how many were joined on
 * and held, and how many of their points were moved round things or cut short.
 */
function rebuildStrands(vox, rig, bones, pal) {
  const isStrand = bones.map(b => b.name.startsWith('strand_'));
  const boneIndex = new Map(bones.map((b, i) => [b.name, i]));
  const before = vox.filter(v => isStrand[v[4]]).length;
  // everything but the strands stays
  let kept = 0;
  for (const v of vox) if (!isStrand[v[4]]) vox[kept++] = v;
  vox.length = kept;
  const taken = index(vox);
  const P = {
    bone: pal('minecraft:bone_block'), calcite: pal('minecraft:calcite'),
    band: pal('minecraft:oxidized_cut_copper'), light: pal('minecraft:verdant_froglight'),
  };
  // what is laid: per block, the strand piece it belongs to, how far along the strand and round it it is, and how far
  // inside the tentacle (a block two strands both want goes to the one it lies deeper in)
  const laid = new Map();
  const lay = (x, y, z, bone, s, ang, depth, strand) => {
    const k = key(x, y, z);
    if (taken.has(k)) return;
    const o = laid.get(k);
    if (o && o.depth >= depth) return;
    laid.set(k, { x, y, z, bone, s, ang, depth, strand });
  };
  // a round tube from a to b, radius ra to rb, its blocks given to bone; s0 is how far along the strand a is
  const tube = (a, b, ra, rb, bone, s0, strand, frame) => {
    const d = sub(b, a), l = len(d);
    const lo = [0, 1, 2].map(i => Math.floor(Math.min(a[i], b[i]) - Math.max(ra, rb) - 1));
    const hi = [0, 1, 2].map(i => Math.ceil(Math.max(a[i], b[i]) + Math.max(ra, rb) + 1));
    for (let x = lo[0]; x <= hi[0]; x++) for (let y = lo[1]; y <= hi[1]; y++) for (let z = lo[2]; z <= hi[2]; z++) {
      const p = [x, y, z], ap = sub(p, a);
      const t = l > 1e-6 ? Math.max(0, Math.min(1, dot(ap, d) / (l * l))) : 0;
      const c = [a[0] + d[0] * t, a[1] + d[1] * t, a[2] + d[2] * t];
      const off = sub(p, c), r = ra + (rb - ra) * t, dist = len(off);
      if (dist > r) continue;
      lay(x, y, z, bone, s0 + l * t, Math.atan2(dot(off, frame[1]), dot(off, frame[0])), r - dist, strand);
    }
  };
  // a frame round a piece's way, kept turning the same way down the strand (so the twist runs on evenly)
  const frameFor = (d, prev) => {
    const w = norm(d);
    let u = prev ? sub(prev[0], w.map(v => v * dot(prev[0], w))) : sub([1, 0, 0], w.map(v => v * w[0]));
    if (len(u) < 1e-3) u = sub([0, 0, 1], w.map(v => v * w[2]));
    u = norm(u);
    return [u, cross(w, u)];
  };

  // what a strand goes round: the arms, and the pods that aren't its own (as built, many strands ran into an arm or a
  // pod and out again; drawn through it, the bit beyond came loose as soon as the arm moved off)
  const isArm = bones.map(b => b.name.startsWith('arm_'));
  const isPod = bones.map(b => b.name.startsWith('pod_'));
  const ownPods = new Map();
  for (const D of rig.pods) {
    const pb = bones[D.bone].parent == null ? -1 : boneIndex.get(bones[D.bone].parent);
    if (pb >= 0 && isStrand[pb]) ownPods.set(D.bone, pb);
  }
  const ball = r => {
    const out = [], R = Math.ceil(r);
    for (let a = -R; a <= R; a++) for (let b = -R; b <= R; b++) for (let c = -R; c <= R; c++) {
      if (a * a + b * b + c * c <= r * r) out.push([a, b, c]);
    }
    return out;
  };
  const inTheWay = (p, r, mine, parentBone) => {
    let n = 0;
    for (const [a, b, c] of ball(r + 0.5)) {
      const j = taken.get(key(Math.round(p[0]) + a, Math.round(p[1]) + b, Math.round(p[2]) + c));
      if (j === undefined) continue;
      const bb = vox[j][4];
      if (bb === parentBone) continue;
      if (isArm[bb] || (isPod[bb] && !mine.has(ownPods.get(bb)))) n++;
    }
    return n;
  };
  // the nearest place round p, at most 10 blocks off, where a strand that thick is clear of all that
  const SHELLS = [];
  for (let R = 1; R <= 10; R++) {
    const sh = [];
    for (let a = -R; a <= R; a++) for (let b = -R; b <= R; b++) for (let c = -R; c <= R; c++) {
      const d = Math.hypot(a, b, c);
      if (d > R - 0.5 && d <= R + 0.5) sh.push([a, b, c, d]);
    }
    sh.sort((u, v) => u[3] - v[3]);
    SHELLS.push(sh);
  }
  const clear = (p, r, mine, parentBone) => {
    for (const sh of SHELLS) for (const [a, b, c] of sh) {
      const q = [p[0] + a, p[1] + b, p[2] + c];
      if (!inTheWay(q, r, mine, parentBone)) return [a, b, c];
    }
    return null;
  };

  const STEP = 0.5;
  const ends = [];
  let moved = 0, shortened = 0;
  rig.strands.forEach((D, si) => {
    const pts = [...D.joints, D.bottom];
    const parent = bones[D.bones[0]].parent;
    const parentBone = parent == null ? -1 : boneIndex.get(parent);
    const low = parent && (parent.startsWith('pod_') || parent.startsWith('strand_') || parent.startsWith('egg_'));
    const top = low ? TOP_LOW : TOP;
    const mine = new Set(D.bones);
    const lens = [];
    for (let i = 0; i + 1 < pts.length; i++) lens.push(len(sub(pts[i + 1], pts[i])));
    const total = lens.reduce((a, b) => a + b, 0);
    const radius = s => END + (top - END) * Math.pow(1 - Math.min(1, s / Math.max(1, total)), 0.85);
    // the points along it, every half block
    const at = [];
    const frames = [];
    let frame = null;
    for (let i = 0, s0 = 0; i < lens.length; s0 += lens[i], i++) {
      frame = frameFor(sub(pts[i + 1], pts[i]), frame);
      frames.push(frame);
      const n = Math.max(1, Math.ceil(lens[i] / STEP));
      for (let k = 0; k < n; k++) {
        const t = k / n, d = sub(pts[i + 1], pts[i]);
        const p = [pts[i][0] + d[0] * t, pts[i][1] + d[1] * t, pts[i][2] + d[2] * t];
        at.push({ p, s: s0 + lens[i] * t, seg: i });
      }
    }
    at.push({ p: pts[pts.length - 1], s: total, seg: lens.length - 1 });
    // where it runs into something: moved aside to where it's clear, and the strand eased over to there and back.
    // Each stretch where it runs into things is moved one way only (two ways at once, it would tear in two)
    const EASE = 12;
    const hit = at.map(A => A.s >= 3 && inTheWay(A.p, radius(A.s), mine, parentBone) > 0);
    const runs = [];
    hit.forEach((h, i) => {
      if (!h) return;
      const r = runs[runs.length - 1];
      if (r && i - r[1] <= 2 * EASE) r[1] = i; else runs.push([i, i]);
    });
    const shift = at.map(() => [0, 0, 0]);
    // (the way that needs the least moving for the whole stretch, of the ways round level with the ground and
    // half way up or down; one it can't get clear by any of them, it ends just above, coming to its point there)
    const WAYS = [];
    for (let k = 0; k < 16; k++) {
      const a = k * Math.PI / 8;
      WAYS.push([Math.cos(a), 0, Math.sin(a)]);
      WAYS.push(norm([Math.cos(a), 0.7, Math.sin(a)]));
    }
    let end = at.length;
    for (const [i0, i1] of runs) {
      let best = null, bestNeed = null, bestMax = Infinity;
      for (const way of WAYS) {
        const need = [];
        let worst = 0;
        for (let i = i0; i <= i1 && worst < bestMax; i++) {
          if (!hit[i]) continue;
          let t = 1;
          const r = radius(at[i].s);
          while (t <= 16 && inTheWay(at[i].p.map((v, a) => v + way[a] * t), r, mine, parentBone)) t += 0.5;
          need.push([i, t]);
          worst = Math.max(worst, t);
        }
        if (worst < bestMax) { bestMax = worst; best = way; bestNeed = need; }
      }
      if (bestMax > 16) { end = Math.max(1, i0 - EASE); break; }
      for (let i = Math.max(0, i0 - EASE); i <= Math.min(at.length - 1, i1 + EASE); i++) {
        let m = 0;
        for (const [j, t] of bestNeed) m = Math.max(m, t * Math.max(0, 1 - Math.abs(j - i) / (EASE + 1)));
        const v = best.map(x => x * m);
        if (len(v) > len(shift[i])) shift[i] = v;
      }
    }
    if (end < at.length) {
      // cut short: it thins to its point where it now ends
      const cutAt = at[end - 1].s;
      at.length = end;
      shortened++;
      for (const A of at) A.r = END + (top - END) * Math.pow(1 - Math.min(1, A.s / Math.max(1, cutAt)), 0.85);
    }
    at.forEach((A, i) => {
      if (len(shift[i]) > 0) moved++;
      A.q = [A.p[0] + shift[i][0], A.p[1] + shift[i][1], A.p[2] + shift[i][2]];
    });
    // the tentacle: a ball at each point, as thick as it is there
    for (const A of at) {
      const r = A.r !== undefined ? A.r : radius(A.s), f = frames[A.seg];
      const c = [Math.round(A.q[0]), Math.round(A.q[1]), Math.round(A.q[2])];
      for (const [a, b, e] of ball(r + 1)) {
        const x = c[0] + a, y = c[1] + b, z = c[2] + e;
        const off = [x - A.q[0], y - A.q[1], z - A.q[2]], dist = len(off);
        if (dist > r) continue;
        lay(x, y, z, D.bones[A.seg], A.s, Math.atan2(dot(off, f[1]), dot(off, f[0])), r - dist, si);
      }
    }
    ends.push({ si, top: at[0].q, parent, r: top, bone: D.bones[0], frame: frames[0] });
    D.drawn = at;
  });

  // each strand joined on to what it hangs from: a short piece of it from its top to the nearest block of that
  const byBone = new Map();
  for (const v of vox) {
    if (!byBone.has(v[4])) byBone.set(v[4], []);
    byBone.get(v[4]).push(v);
  }
  let joined = 0;
  // the nearest point to p on the middle line of strand piece b, as drawn
  const onLine = (b, p) => {
    const S = rig.strands.find(S => S.bones.includes(b));
    const i = S.bones.indexOf(b);
    let best = null, bd = Infinity;
    for (const A of S.drawn) {
      if (A.seg !== i) continue;
      const d = len(sub(A.q, p));
      if (d < bd) { bd = d; best = A.q; }
    }
    return best;
  };
  const nearestOf = (b, p) => {
    let best = null, bd = Infinity;
    for (const v of byBone.get(b) || []) {
      const dd = (v[0] - p[0]) ** 2 + (v[1] - p[1]) ** 2 + (v[2] - p[2]) ** 2;
      if (dd < bd) { bd = dd; best = v; }
    }
    return best;
  };
  for (const E of ends) {
    const pb = E.parent == null ? -1 : boneIndex.get(E.parent);
    if (pb < 0) continue;
    let from;
    if (isStrand[pb]) from = onLine(pb, E.top);
    else {
      const v = nearestOf(pb, E.top);
      if (!v) continue;
      from = [v[0], v[1], v[2]];
    }
    tube(from, E.top, E.r * 0.8, E.r, E.bone, 0, E.si, E.frame);
    joined++;
  }
  // and each pod held on to the strand it hangs from: a short stalk from where it touches the strand to the strand's
  // middle line there
  let stalks = 0;
  for (const D of rig.pods) {
    const pb = bones[D.bone].parent == null ? -1 : boneIndex.get(bones[D.bone].parent);
    if (pb < 0 || !isStrand[pb]) continue;
    const c = onLine(pb, bones[D.bone].pivot.map(v => v - 0.5));
    const pod = nearestOf(D.bone, c);
    if (!pod) continue;
    const S = rig.strands.find(S => S.bones.includes(pb));
    tube([pod[0], pod[1], pod[2]], c, 1.6, 1.6, pb, 0, rig.strands.indexOf(S), frameFor(sub(c, pod), null));
    stalks++;
  }

  // the blocks: bone and calcite in a slow twist round it, a copper band every BAND blocks, a froglight dot every DOT
  // blocks on its outside, each a quarter turn on from the last
  for (const L of laid.values()) {
    const turn = L.ang + L.s * 0.06;
    const sector = Math.floor(((turn % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI) / (Math.PI / 3));
    let p = sector % 3 === 0 ? P.calcite : P.bone;
    const along = L.s % BAND;
    if (L.s > 4 && along < 1) p = P.band;
    const k = Math.round(L.s / DOT);
    if (L.depth < 1 && Math.abs(L.s - k * DOT) < 0.75) {
      const want = k * Math.PI / 2 + L.strand;
      const da = Math.atan2(Math.sin(L.ang - want), Math.cos(L.ang - want));
      if (Math.abs(da) < 0.45) p = P.light;
    }
    vox.push([L.x, L.y, L.z, p, L.bone, 255]);
  }
  for (const D of rig.strands) { delete D.drawn; }
  return { before, after: laid.size, joined, stalks, moved, shortened };
}

module.exports = { rebuildStrands };
