// Turns the sorted voxels into bones: splits the strands apart, finds each pod and egg clump and the strand it
// hangs from, and cuts the arms and strands into segments that can bend.
const { N26 } = require('./classify');

const BAND = 6;
const RIM_SECTORS = 16;
const EGGS_KEPT = 30;

// Picks the egg clumps to keep: groups of three or four close together, the groups spread out from each other,
// the fullest spots first.
function pickEggGroups(eggs, X, Y, Z, n, want, stuck) {
  const mid = [];
  for (let k = 0; k < n; k++) {
    const i = eggs.of[k]; if (i < 0) continue;
    const m = mid[i] || (mid[i] = [0, 0, 0, 0]); m[0] += X[k]; m[1] += Y[k]; m[2] += Z[k]; m[3]++;
  }
  const c = mid.map(m => [m[0] / m[3], m[1] / m[3], m[2] / m[3], m[3]]);
  const d = (a, b) => Math.hypot(a[0] - b[0], (a[1] - b[1]) * 0.8, a[2] - b[2]);
  const R = 17, APART = 30;
  const free = new Set(c.map((_, i) => i).filter(i => stuck.has(i)));
  const keep = new Set();
  while (keep.size < want && free.size) {
    let best = -1, bs = -1;
    for (const i of free) {
      let s = 0; for (const j of free) if (j !== i && d(c[i], c[j]) < R) s++;
      const score = s * 1000 + c[i][3];
      if (score > bs) { bs = score; best = i; }
    }
    const near = [...free].filter(j => j !== best && d(c[best], c[j]) < R).sort((a, b) => d(c[best], c[a]) - d(c[best], c[b]));
    const group = [best, ...near.slice(0, Math.min(3, want - keep.size - 1))];
    for (const i of group) keep.add(i);
    for (const i of [...free]) if (group.some(g => d(c[g], c[i]) < APART) || group.includes(i)) free.delete(i);
  }
  return keep;
}
const ARM_SEGS = 4;

function hash(x, y, z, s) {
  let h = (x * 374761393 + y * 668265263 + z * 2147483647 + s * 1274126177) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}

function buildRig(M, C) {
  const { n, X, Y, Z, lab, label, find } = C;
  const P = C.P;

  // ---------------- strands: split apart from the top down, a slice at a time
  const strandOf = new Int32Array(n).fill(-1);
  let nStr = 0;
  {
    const byY = new Map();
    for (let k = 0; k < n; k++) if (label[k] === null && lab[k] === 100) { if (!byY.has(Y[k])) byY.set(Y[k], []); byY.get(Y[k]).push(k); }
    const ys = [...byY.keys()].sort((a, b) => b - a);
    for (const y of ys) {
      const slice = byY.get(y);
      const inSlice = new Set(slice);
      const seen = new Set();
      for (const s0 of slice) {
        if (seen.has(s0)) continue;
        // 2D component in this slice
        const comp = []; const st = [s0]; seen.add(s0);
        while (st.length) {
          const k = st.pop(); comp.push(k);
          for (let a = -1; a <= 1; a++) for (let c = -1; c <= 1; c++) {
            const j = find(X[k] + a, y, Z[k] + c);
            if (j !== undefined && inSlice.has(j) && !seen.has(j)) { seen.add(j); st.push(j); }
          }
        }
        // who is above it
        const above = new Map();
        const aboveVox = [];
        for (const k of comp) for (let a = -1; a <= 1; a++) for (let c = -1; c <= 1; c++) {
          const j = find(X[k] + a, y + 1, Z[k] + c);
          if (j !== undefined && strandOf[j] >= 0) { above.set(strandOf[j], (above.get(strandOf[j]) || 0) + 1); aboveVox.push(j); }
        }
        if (above.size === 0) { const id = nStr++; for (const k of comp) strandOf[k] = id; }
        else if (above.size === 1) { const id = above.keys().next().value; for (const k of comp) strandOf[k] = id; }
        else {
          // strands grown together into a sheet: each voxel keeps the strand right above it (nearest)
          for (const k of comp) {
            let bd = 1e9, bi = -1;
            for (const j of aboveVox) { const d = (X[j] - X[k]) ** 2 + (Z[j] - Z[k]) ** 2; if (d < bd) { bd = d; bi = strandOf[j]; } }
            strandOf[k] = bi;
          }
        }
      }
    }
  }
  // small bits join the strand they touch most, repeat
  const MIN_STRAND = 400;
  for (let pass = 0; pass < 6; pass++) {
    const size = new Map();
    for (let k = 0; k < n; k++) if (strandOf[k] >= 0) size.set(strandOf[k], (size.get(strandOf[k]) || 0) + 1);
    const small = new Set([...size].filter(([, s]) => s < MIN_STRAND).map(([i]) => i));
    if (!small.size) break;
    const touch = new Map();
    for (let k = 0; k < n; k++) {
      const s = strandOf[k]; if (!small.has(s)) continue;
      for (const [a, b, c] of N26) {
        const j = find(X[k] + a, Y[k] + b, Z[k] + c);
        if (j === undefined || strandOf[j] < 0 || strandOf[j] === s || small.has(strandOf[j]) && size.get(strandOf[j]) < size.get(s)) continue;
        const m = touch.get(s) || new Map(); m.set(strandOf[j], (m.get(strandOf[j]) || 0) + 1); touch.set(s, m);
      }
    }
    // no neighbour: nearest by the middle
    const mid = new Map();
    for (let k = 0; k < n; k++) if (strandOf[k] >= 0) { const s = strandOf[k]; const m = mid.get(s) || [0, 0, 0, 0]; m[0] += X[k]; m[1] += Y[k]; m[2] += Z[k]; m[3]++; mid.set(s, m); }
    const into = new Map();
    for (const s of small) {
      const m = touch.get(s);
      if (m && m.size) into.set(s, [...m].sort((a, b) => b[1] - a[1])[0][0]);
      else {
        const a = mid.get(s); let bd = 1e18, bi = -1;
        for (const [t, b] of mid) {
          if (t === s || small.has(t)) continue;
          const d = (a[0] / a[3] - b[0] / b[3]) ** 2 + ((a[1] / a[3] - b[1] / b[3]) * 0.3) ** 2 + (a[2] / a[3] - b[2] / b[3]) ** 2;
          if (d < bd) { bd = d; bi = t; }
        }
        into.set(s, bi);
      }
    }
    for (let k = 0; k < n; k++) if (into.has(strandOf[k])) strandOf[k] = into.get(strandOf[k]);
  }
  // number the strands 0.. by angle then radius
  {
    const mid = new Map();
    for (let k = 0; k < n; k++) if (strandOf[k] >= 0) { const s = strandOf[k]; const m = mid.get(s) || [0, 0, 0]; m[0] += X[k]; m[1] += Z[k]; m[2]++; mid.set(s, m); }
    const order = [...mid].map(([s, m]) => [s, Math.atan2(m[1] / m[2], m[0] / m[2])]).sort((a, b) => a[1] - b[1]);
    const ren = new Map(); order.forEach(([s], i) => ren.set(s, i));
    for (let k = 0; k < n; k++) if (strandOf[k] >= 0) strandOf[k] = ren.get(strandOf[k]);
    nStr = order.length;
  }

  // ---------------- pods and egg clumps: each lump on its own
  function lumps(code, minSize) {
    const of = new Int32Array(n).fill(-1); let cnt = 0;
    for (let k = 0; k < n; k++) {
      if (label[k] !== null || lab[k] !== code || of[k] >= 0) continue;
      const st = [k]; of[k] = cnt;
      while (st.length) {
        const i = st.pop();
        for (const [a, b, c] of N26) {
          const j = find(X[i] + a, Y[i] + b, Z[i] + c);
          if (j !== undefined && label[j] === null && lab[j] === code && of[j] < 0) { of[j] = cnt; st.push(j); }
        }
      }
      cnt++;
    }
    // tiny lumps: nearest bigger one
    const sz = new Array(cnt).fill(0), mid = [];
    for (let k = 0; k < n; k++) if (of[k] >= 0) { sz[of[k]]++; const m = mid[of[k]] || (mid[of[k]] = [0, 0, 0]); m[0] += X[k]; m[1] += Y[k]; m[2] += Z[k]; }
    mid.forEach((m, i) => { m[0] /= sz[i]; m[1] /= sz[i]; m[2] /= sz[i]; });
    const into = new Array(cnt).fill(-1);
    for (let i = 0; i < cnt; i++) {
      if (sz[i] >= minSize) continue;
      let bd = 1e18, bi = -1;
      for (let j = 0; j < cnt; j++) { if (sz[j] < minSize) continue; const d = (mid[i][0] - mid[j][0]) ** 2 + (mid[i][1] - mid[j][1]) ** 2 + (mid[i][2] - mid[j][2]) ** 2; if (d < bd) { bd = d; bi = j; } }
      into[i] = bd < 12 * 12 ? bi : -2;   // too far from any: becomes strand stuff
    }
    const keep = []; for (let i = 0; i < cnt; i++) if (sz[i] >= minSize) keep.push(i);
    const ren = new Map(); keep.forEach((i, r) => ren.set(i, r));
    let loose = 0;
    for (let k = 0; k < n; k++) {
      if (of[k] < 0) continue;
      let i = of[k];
      if (into[i] === -2) { of[k] = -1; lab[k] = 100; loose++; continue; }
      if (into[i] >= 0) i = into[i];
      of[k] = ren.get(i);
    }
    return { of, count: keep.length, loose };
  }
  const pods = lumps(200, 60);
  const eggs = lumps(300, 60);
  // 1.1: floating lumps that touch nothing else of him are taken off: only the one big piece of him stays
  const gone = new Uint8Array(n);
  let floatingOff = 0;
  {
    const comp = new Int32Array(n).fill(-1); const sizes = [];
    for (let s = 0; s < n; s++) {
      if (comp[s] >= 0) continue;
      const id = sizes.length; let size = 0; const st = [s]; comp[s] = id;
      while (st.length) {
        const k = st.pop(); size++;
        for (const [a, b, c] of N26) { const j = find(X[k] + a, Y[k] + b, Z[k] + c); if (j !== undefined && comp[j] < 0) { comp[j] = id; st.push(j); } }
      }
      sizes.push(size);
    }
    const big = sizes.indexOf(Math.max(...sizes));
    for (let k = 0; k < n; k++) if (comp[k] !== big) { gone[k] = 1; floatingOff++; if (eggs.of[k] >= 0) eggs.of[k] = -1; }
  }
  // 1.1: fewer egg clumps (about a third of them), kept in clear groups; the rest are taken off the model
  {
    // only clumps really stuck on to a strand or an arm can stay (a clump on its own would float)
    const stuck = new Set();
    for (let k = 0; k < n; k++) {
      if (eggs.of[k] < 0 || stuck.has(eggs.of[k])) continue;
      for (const [a, b, c] of N26) {
        const j = find(X[k] + a, Y[k] + b, Z[k] + c);
        if (j !== undefined && !gone[j] && (strandOf[j] >= 0 || (label[j] === null && lab[j] >= 0 && lab[j] < 8))) { stuck.add(eggs.of[k]); break; }
      }
    }
    const keep = pickEggGroups(eggs, X, Y, Z, n, EGGS_KEPT, stuck);
    let removed = 0;
    for (let k = 0; k < n; k++) if (eggs.of[k] >= 0 && !keep.has(eggs.of[k])) { eggs.of[k] = -1; gone[k] = 1; removed++; }
    const ren = new Map(); [...keep].sort((a, b) => a - b).forEach((i, r) => ren.set(i, r));
    for (let k = 0; k < n; k++) if (eggs.of[k] >= 0) eggs.of[k] = ren.get(eggs.of[k]);
    eggs.removed = eggs.count - keep.size; eggs.removedVox = removed;
    eggs.count = keep.size;
  }
  // loose bits turned into strand stuff: give them to the nearest strand voxel's strand
  for (let pass = 0; pass < 40; pass++) {
    let left = 0;
    for (let k = 0; k < n; k++) {
      if (label[k] !== null || lab[k] !== 100 || strandOf[k] >= 0) continue;
      let got = -1;
      for (const [a, b, c] of N26) { const j = find(X[k] + a, Y[k] + b, Z[k] + c); if (j !== undefined && strandOf[j] >= 0) { got = strandOf[j]; break; } }
      if (got >= 0) strandOf[k] = got; else left++;
    }
    if (!left) break;
  }

  const fixed = takeBack(C, strandOf, pods, gone);

  // ---------------- 1.9.3: every part in one piece, before any bones are made. A bit of a strand (or of a pod, an
  // egg clump, an arm) that only touches the rest of its part through some other part goes with the part it touches
  // most: left where it was, it would float off when the two swing apart, and a strand in pieces would hang its top
  // from nothing. Repeated until every part is one piece.
  let earlyBits = 0, earlyVox = 0;
  {
    const partOf = k => {
      if (gone[k]) return null;
      if (label[k] !== null) return 'body';
      if (pods.of[k] >= 0) return 'P' + pods.of[k];
      if (eggs.of[k] >= 0) return 'E' + eggs.of[k];
      if (strandOf[k] >= 0) return 'S' + strandOf[k];
      if (lab[k] >= 0 && lab[k] < 8) return 'A' + lab[k];
      return null;
    };
    const setPart = (k, p, from) => {
      pods.of[k] = -1; eggs.of[k] = -1; strandOf[k] = -1;
      const c = p[0], i = p === 'body' ? -1 : +p.slice(1);
      if (p === 'body') { label[k] = label[from]; lab[k] = -1; }
      else if (c === 'P') { pods.of[k] = i; lab[k] = 200; }
      else if (c === 'E') { eggs.of[k] = i; lab[k] = 300; }
      else if (c === 'S') { strandOf[k] = i; lab[k] = 100; }
      else { lab[k] = i; }
    };
    for (let pass = 0; pass < 20; pass++) {
      const part = new Array(n);
      for (let k = 0; k < n; k++) part[k] = partOf(k);
      const comp = new Int32Array(n).fill(-1);
      const comps = [];
      for (let s0 = 0; s0 < n; s0++) {
        if (comp[s0] >= 0 || part[s0] === null || part[s0] === 'body') continue;
        const g = part[s0], id = comps.length, ks = [s0];
        comp[s0] = id;
        for (let q = 0; q < ks.length; q++) {
          const k = ks[q];
          for (const [a, b, c] of N26) {
            const j = find(X[k] + a, Y[k] + b, Z[k] + c);
            if (j !== undefined && comp[j] < 0 && part[j] === g) { comp[j] = id; ks.push(j); }
          }
        }
        comps.push({ g, ks });
      }
      const biggest = new Map();
      comps.forEach((c, i) => { const b = biggest.get(c.g); if (b === undefined || comps[b].ks.length < c.ks.length) biggest.set(c.g, i); });
      let changed = 0;
      comps.forEach((c, i) => {
        if (biggest.get(c.g) === i) return;
        const touch = new Map(), via = new Map();
        for (const k of c.ks) for (const [a, b, d] of N26) {
          const j = find(X[k] + a, Y[k] + b, Z[k] + d);
          if (j === undefined || part[j] === null || part[j] === c.g) continue;
          touch.set(part[j], (touch.get(part[j]) || 0) + 1);
          if (!via.has(part[j])) via.set(part[j], j);
        }
        if (!touch.size) return;
        const into = [...touch].sort((a, b) => b[1] - a[1] || (a[0] < b[0] ? -1 : 1))[0][0];
        for (const k of c.ks) setPart(k, into, via.get(into));
        changed++; earlyVox += c.ks.length;
      });
      earlyBits += changed;
      if (!changed) break;
    }
  }

  // ---------------- bones
  const bones = [];
  const boneIndex = new Map();
  const addBone = (name, parent, pivot, extra = {}) => { boneIndex.set(name, bones.length); bones.push({ name, parent, pivot: pivot.map(v => Math.round(v * 100) / 100), ...extra }); return bones.length - 1; };
  const boneOf = new Int32Array(n).fill(-1);

  // bell group. 1.1: the dome is cut into bands by height (so it can squeeze in more at the rim than at the top,
  // like a real jellyfish), and the rim into sectors round the edge (so a ripple can run round it)
  let crownY = 0;
  for (let k = 0; k < n; k++) if (label[k] === 'crown') crownY = Math.max(crownY, Y[k]);
  let bellLow = 1e9;
  for (let k = 0; k < n; k++) if (label[k] === 'bell') bellLow = Math.min(bellLow, Y[k]);
  const NBANDS = Math.floor((crownY - bellLow) / BAND) + 1;
  const bandOf = y => Math.max(0, Math.min(NBANDS - 1, Math.floor((crownY - y) / BAND)));
  const bandDefs = [];
  for (let i = 0; i < NBANDS; i++) {
    const b = addBone('bell_' + i, null, [0, crownY, 0]);
    bandDefs.push({ bone: b, top: Math.min(crownY, crownY - i * BAND), bottom: i === NBANDS - 1 ? bellLow : crownY - (i + 1) * BAND + 1 });
  }
  const sectorOf = (x, z) => ((Math.floor((Math.atan2(z, x) + Math.PI) / (2 * Math.PI) * RIM_SECTORS) % RIM_SECTORS) + RIM_SECTORS) % RIM_SECTORS;
  const sectorDefs = [];
  for (let i = 0; i < RIM_SECTORS; i++) {
    const b = addBone('rim_' + i, null, [0, 134, 0]);
    sectorDefs.push({ bone: b, angle: Math.round(((i + 0.5) / RIM_SECTORS * 2 * Math.PI - Math.PI) * 1000) / 1000 });
  }
  addBone('crown', null, [0, crownY, 0]);
  for (let i = 0; i < 5; i++) addBone('spot_' + i, null, [0, crownY, 0]);
  for (let k = 0; k < n; k++) {
    if (label[k] === null) continue;
    if (label[k] === 'bell') boneOf[k] = boneIndex.get('bell_' + bandOf(Y[k]));
    else if (label[k] === 'rim') boneOf[k] = boneIndex.get('rim_' + sectorOf(X[k], Z[k]));
    else boneOf[k] = boneIndex.get(label[k]);
  }

  // centres of a set of voxels, and the middle of a thin slab of them
  const centre = ks => { const m = [0, 0, 0]; for (const k of ks) { m[0] += X[k]; m[1] += Y[k]; m[2] += Z[k]; } return m.map(v => v / Math.max(1, ks.length)); };

  // arms: split by height into segments, hung from the rim sector they come out under
  const armDefs = [];
  const armSegOf = [];
  for (let a = 0; a < 8; a++) {
    const ks = []; for (let k = 0; k < n; k++) if (label[k] === null && lab[k] === a && !gone[k]) ks.push(k);
    let y0 = 1e9, y1 = -1e9; for (const k of ks) { y0 = Math.min(y0, Y[k]); y1 = Math.max(y1, Y[k]); }
    const cuts = []; for (let s = 0; s <= ARM_SEGS; s++) cuts.push(y1 + 1 - (y1 + 1 - y0) * s / ARM_SEGS);
    const segOf = k => { for (let s = 0; s < ARM_SEGS; s++) if (Y[k] >= cuts[s + 1]) return s; return ARM_SEGS - 1; };
    armSegOf[a] = segOf;
    const joints = [];
    const names = [];
    for (let s = 0; s < ARM_SEGS; s++) {
      const top = ks.filter(k => segOf(k) === s && Y[k] >= Math.floor(cuts[s]) - 3);
      const piv = centre(top.length ? top : ks.filter(k => segOf(k) === s));
      const nm = `arm_${a}_${s}`;
      addBone(nm, s === 0 ? 'rim_' + sectorOf(piv[0], piv[2]) : `arm_${a}_${s - 1}`, piv);
      joints.push(piv); names.push(nm);
    }
    for (const k of ks) boneOf[k] = boneIndex.get(`arm_${a}_${segOf(k)}`);
    const tipK = ks.filter(k => Y[k] <= y0 + 3);
    const all = centre(ks);
    armDefs.push({ bones: names.map(b => boneIndex.get(b)), joints, tip: centre(tipK), centre: all, top: y1, bottom: y0,
      angle: Math.round(Math.atan2(joints[0][2], joints[0][0]) * 1000) / 1000 });
  }

  // strands. Each is cut into segments about 12 to 20 blocks long so it bends smoothly. A strand hangs from the
  // rim, or from the glowing vase in the middle, or (for the ones that branch off lower down) from the strand it
  // grows out of, so it swings with it.
  const strandInfo = [];
  for (let s = 0; s < nStr; s++) {
    const ks = []; for (let k = 0; k < n; k++) if (strandOf[k] === s && !gone[k]) ks.push(k);
    if (!ks.length) continue;
    let y0 = 1e9, y1 = -1e9; for (const k of ks) { y0 = Math.min(y0, Y[k]); y1 = Math.max(y1, Y[k]); }
    const len = y1 - y0 + 1;
    const segs = len >= 70 ? 6 : len >= 50 ? 5 : len >= 35 ? 4 : len >= 22 ? 3 : len >= 12 ? 2 : 1;
    const cuts = []; for (let q = 0; q <= segs; q++) cuts.push(y1 + 1 - len * q / segs);
    const segOf = k => { for (let q = 0; q < segs; q++) if (Y[k] >= cuts[q + 1]) return q; return segs - 1; };
    strandInfo.push({ id: strandInfo.length, s, ks, y0, y1, segs, cuts, segOf });
  }
  const infoOfStrand = new Map(strandInfo.map(I => [I.s, I]));
  const strandDefs = new Array(strandInfo.length);
  const segOfStrand = new Int32Array(n).fill(-1);
  const made = new Set();
  let branched = 0;
  for (const I of [...strandInfo].sort((a, b) => b.y1 - a.y1 || a.id - b.id)) {
    const { id, ks, y0, y1, segs, cuts, segOf } = I;
    const names = []; const joints = [];
    for (let q = 0; q < segs; q++) {
      const top = ks.filter(k => segOf(k) === q && Y[k] >= Math.floor(cuts[q]) - 2);
      const piv = centre(top.length ? top : ks.filter(k => segOf(k) === q));
      const nm = `strand_${id}_${q}`;
      let parent = `strand_${id}_${q - 1}`;
      if (q === 0) {
        if (y1 >= 118 && Math.hypot(piv[0], piv[2]) < 26) parent = 'spot_4';
        else if (y1 >= 118) parent = 'rim_' + sectorOf(piv[0], piv[2]);
        else {
          // a branch: the strand it touches most near its top (one already hung, so higher up)
          const touch = new Map();
          for (const k of ks) {
            if (Y[k] < y1 - 3) continue;
            for (let a = -2; a <= 2; a++) for (let b = -2; b <= 2; b++) for (let c = -2; c <= 2; c++) {
              const j = find(X[k] + a, Y[k] + b, Z[k] + c);
              if (j === undefined || gone[j] || strandOf[j] < 0 || strandOf[j] === I.s) continue;
              const J = infoOfStrand.get(strandOf[j]);
              if (!J || !made.has(J.id)) continue;
              const bn = `strand_${J.id}_${J.segOf(j)}`;
              touch.set(bn, (touch.get(bn) || 0) + 1);
            }
          }
          if (touch.size) { parent = [...touch].sort((a, b) => b[1] - a[1])[0][0]; branched++; }
          else parent = 'rim_' + sectorOf(piv[0], piv[2]);
        }
      }
      addBone(nm, parent, piv);
      names.push(nm); joints.push(piv);
    }
    made.add(id);
    for (const k of ks) { boneOf[k] = boneIndex.get(`strand_${id}_${segOf(k)}`); segOfStrand[k] = id; }
    const bot = ks.filter(k => Y[k] <= y0 + 2);
    strandDefs[id] = { bones: names.map(b => boneIndex.get(b)), joints, bottom: centre(bot), top: y1, low: y0, size: ks.length };
  }

  // each block of an arm or strand goes to the piece it lies across from, cut where the chain bends (the plane through
  // each joint halfway between the two pieces' ways), not by height: cut flat across a slanting piece, blocks side by
  // side ended up far apart along the chain, so they came apart as soon as it bent
  let recut = 0;
  const chainsMade = [...armDefs.map(D => [D.bones, [...D.joints, D.tip]]),
    ...strandDefs.map(D => [D.bones, [...D.joints, D.bottom]])];
  for (const [bs, pts] of chainsMade) {
    const m = bs.length;
    if (m < 2) continue;
    const planes = [];
    for (let j = 1; j < m; j++) {
      const a = pts[j - 1], b = pts[j], c = pts[j + 1];
      const u = [b[0] - a[0], b[1] - a[1], b[2] - a[2]], w = [c[0] - b[0], c[1] - b[1], c[2] - b[2]];
      const lu = Math.hypot(...u) || 1, lw = Math.hypot(...w) || 1;
      planes.push([b, [u[0] / lu + w[0] / lw, u[1] / lu + w[1] / lw, u[2] / lu + w[2] / lw]]);
    }
    const mine = new Map(bs.map((b, i) => [b, i]));
    for (let k = 0; k < n; k++) {
      if (gone[k] || !mine.has(boneOf[k])) continue;
      let s = 0;
      for (const [p, nv] of planes) {
        if ((X[k] + 0.5 - p[0]) * nv[0] + (Y[k] + 0.5 - p[1]) * nv[1] + (Z[k] + 0.5 - p[2]) * nv[2] < 0) break;
        s++;
      }
      if (bs[s] !== boneOf[k]) { boneOf[k] = bs[s]; recut++; }
    }
  }

  // pods and eggs hang from the strand segment they touch most (or the nearest one)
  function hang(lump, prefix, list) {
    for (let i = 0; i < lump.count; i++) {
      const ks = []; for (let k = 0; k < n; k++) if (lump.of[k] === i) ks.push(k);
      const touch = new Map();
      for (const k of ks) for (const [a, b, c] of N26) {
        const j = find(X[k] + a, Y[k] + b, Z[k] + c);
        if (j !== undefined && boneOf[j] >= 0 && bones[boneOf[j]].name.startsWith('strand_')) touch.set(boneOf[j], (touch.get(boneOf[j]) || 0) + 1);
      }
      const c = centre(ks);
      let par;
      if (touch.size) par = [...touch].sort((a, b) => b[1] - a[1])[0][0];
      else {
        // nearest strand voxel above-ish
        let bd = 1e18;
        for (let k = 0; k < n; k++) {
          if (boneOf[k] < 0 || !bones[boneOf[k]].name.startsWith('strand_')) continue;
          const d = (X[k] - c[0]) ** 2 + (Y[k] - c[1]) ** 2 + (Z[k] - c[2]) ** 2;
          if (d < bd) { bd = d; par = boneOf[k]; }
        }
      }
      let top = -1e9; for (const k of ks) top = Math.max(top, Y[k]);
      const topK = ks.filter(k => Y[k] >= top - 1);
      const tc = centre(topK);
      let rad = 0; for (const k of ks) rad = Math.max(rad, Math.hypot(X[k] - c[0], Y[k] - c[1], Z[k] - c[2]));
      const nm = `${prefix}_${i}`;
      const b = addBone(nm, bones[par].name, [tc[0], top, tc[2]]);
      for (const k of ks) boneOf[k] = b;
      list.push({ bone: b, centre: c.map(v => Math.round(v * 10) / 10), radius: Math.round(rad * 10) / 10, size: ks.length });
    }
  }
  const podDefs = [], eggDefs = [];
  hang(pods, 'pod', podDefs);
  hang(eggs, 'egg', eggDefs);

  // ---------------- pods and egg clumps hang from the piece of strand or arm they touch most, held at the middle of
  // where they touch it (so they swing and swell about that spot and never come off it). The game hangs each where
  // that strand or arm is drawn at that spot (see BellRig.frame), so it stays on it however the strand bends.
  const contacts = (b) => {
    const touch = new Map(), sum = new Map();
    for (let k = 0; k < n; k++) {
      if (boneOf[k] !== b || gone[k]) continue;
      for (const [a, c, d] of N26) {
        const j = find(X[k] + a, Y[k] + c, Z[k] + d);
        if (j === undefined || gone[j] || boneOf[j] < 0 || boneOf[j] === b) continue;
        if (!/^(strand|arm)_/.test(bones[boneOf[j]].name)) continue;
        const o = boneOf[j];
        touch.set(o, (touch.get(o) || 0) + 1);
        const m = sum.get(o) || [0, 0, 0, 0]; m[0] += X[k]; m[1] += Y[k]; m[2] += Z[k]; m[3]++; sum.set(o, m);
      }
    }
    return { touch, sum };
  };
  for (const list of [podDefs, eggDefs]) for (const D of list) {
    const b = D.bone;
    const { touch, sum } = contacts(b);
    if (!touch.size) continue;   // touches no strand or arm: keeps the nearest strand (found when it was made)
    const order = [...touch].sort((a, c) => c[1] - a[1] || a[0] - c[0]);
    const par = order[0][0];
    bones[b].parent = bones[par].name;
    const m = sum.get(par);
    bones[b].pivot = [m[0] / m[3] + 0.5, m[1] / m[3] + 0.5, m[2] / m[3] + 0.5].map(v => Math.round(v * 100) / 100);
  }

  // anything left (should be nothing): the rim
  // anything left over (loose bits): the bone of a neighbour, else the rim
  let orphans = 0;
  for (let pass = 0; pass < 30; pass++) {
    const set = [];
    for (let k = 0; k < n; k++) {
      if (boneOf[k] >= 0 || gone[k]) continue;
      for (const [a, b, c] of N26) { const j = find(X[k] + a, Y[k] + b, Z[k] + c); if (j !== undefined && boneOf[j] >= 0) { set.push([k, boneOf[j]]); break; } }
    }
    if (!set.length) break;
    for (const [k, b] of set) boneOf[k] = b;
  }
  // whatever is still left touches nothing of him at all: a floating bit, taken off
  for (let k = 0; k < n; k++) if (boneOf[k] < 0 && !gone[k]) { gone[k] = 1; orphans++; }

  // ---------------- 1.9.3: what each lower strand really hangs from. Many of the middle ones hang from a pod (the
  // glowing balls), some from the end of an arm; before, each was hung from whatever strand it touched most, often
  // only by a block or two, so it came away from the pod or arm it was really stuck to. Now it hangs from the
  // strand, arm or pod its top touches most, and turns about the middle of where it touches it.
  const chainKey = b => { const w = bones[b].name.split('_'); return w[0] === 'strand' || w[0] === 'arm' ? w[0] + '_' + w[1] : bones[b].name; };
  const comesFrom = (name, key) => {
    for (let nm = name, guard = 0; nm && guard < 400; nm = bones[boneIndex.get(nm)].parent, guard++) if (chainKey(boneIndex.get(nm)) === key) return true;
    return false;
  };
  const strandBones = new Map();
  for (let k = 0; k < n; k++) {
    if (gone[k] || boneOf[k] < 0) continue;
    const w = bones[boneOf[k]].name.split('_');
    if (w[0] !== 'strand') continue;
    if (!strandBones.has(+w[1])) strandBones.set(+w[1], []);
    strandBones.get(+w[1]).push(k);
  }
  let rehung = 0;
  for (const I of strandInfo) {
    if (I.y1 >= 118) continue;
    const ks = strandBones.get(I.id) || [];
    if (!ks.length) continue;
    let top = -1e9; for (const k of ks) top = Math.max(top, Y[k]);
    const myKey = 'strand_' + I.id;
    const touch = new Map(), sum = new Map();
    for (const k of ks) {
      if (Y[k] < top - 3) continue;
      const seen = new Set();
      for (let a = -2; a <= 2; a++) for (let b = -2; b <= 2; b++) for (let c = -2; c <= 2; c++) {
        const j = find(X[k] + a, Y[k] + b, Z[k] + c);
        if (j === undefined || gone[j] || boneOf[j] < 0) continue;
        const o = boneOf[j];
        if (!/^(strand|arm|pod)_/.test(bones[o].name) || chainKey(o) === myKey) continue;
        touch.set(o, (touch.get(o) || 0) + 1);
        if (!seen.has(o)) { seen.add(o); const m = sum.get(o) || [0, 0, 0, 0]; m[0] += X[k]; m[1] += Y[k]; m[2] += Z[k]; m[3]++; sum.set(o, m); }
      }
    }
    const order = [...touch].sort((a, b) => b[1] - a[1] || a[0] - b[0]);
    const pick = order.find(([o]) => !comesFrom(bones[o].name, myKey));
    if (!pick) continue;
    const seg0 = boneIndex.get(`strand_${I.id}_0`);
    if (bones[seg0].parent !== bones[pick[0]].name) rehung++;
    bones[seg0].parent = bones[pick[0]].name;
    const m = sum.get(pick[0]);
    const J = strandDefs[I.id].joints;
    J[0] = [m[0] / m[3], Math.max(m[1] / m[3], J[1] ? J[1][1] + 1 : -1e9), m[2] / m[3]];
  }

  // ---------------- bones in an order where each comes after the one it hangs from (a pod before the strand that
  // hangs from it), otherwise as they were made
  {
    const nb = bones.length;
    const done = new Array(nb).fill(false), order = [];
    while (order.length < nb) {
      let moved = false;
      for (let i = 0; i < nb; i++) {
        if (done[i]) continue;
        const p = bones[i].parent;
        if (p !== null && p !== undefined && !done[boneIndex.get(p)]) continue;
        done[i] = true; order.push(i); moved = true;
        break;
      }
      if (!moved) throw new Error('the bones hang from each other in a ring');
    }
    const to = new Int32Array(nb);
    order.forEach((o, i) => { to[o] = i; });
    const nbones = order.map(o => bones[o]);
    bones.length = 0; nbones.forEach(b => bones.push(b));
    boneIndex.clear(); bones.forEach((b, i) => boneIndex.set(b.name, i));
    for (let k = 0; k < n; k++) if (boneOf[k] >= 0) boneOf[k] = to[boneOf[k]];
    for (const D of bandDefs) D.bone = to[D.bone];
    for (const D of sectorDefs) D.bone = to[D.bone];
    for (const D of armDefs) D.bones = D.bones.map(b => to[b]);
    for (const D of strandDefs) D.bones = D.bones.map(b => to[b]);
    for (const D of [...podDefs, ...eggDefs]) D.bone = to[D.bone];
  }

  // ---------------- the egg clumps are left off him: each keeps its place on its strand or arm (where it breaks off
  // and falls from), but none of its blocks
  let eggBlocks = 0;
  for (let k = 0; k < n; k++) {
    if (!gone[k] && boneOf[k] >= 0 && bones[boneOf[k]].name.startsWith('egg_')) { gone[k] = 1; eggBlocks++; }
  }

  // a light set into an arm's skin lights the arm, not the strand that runs past it, or it swings off the arm with
  // the strand: one with three or more blocks of an arm round it goes to that arm, again and again until none is left
  // (inside a patch of lights a light only has the patch round it until the edge has gone), but only near the arm's
  // own copper or lime, so a strand's own stripe of lights that just touches a loop stays with the strand
  let armLights = 0;
  const chainOf = bones.map(b => b.name.split('_').slice(0, 2).join('_'));
  const isArmBone = bones.map(b => b.name.startsWith('arm_'));
  const isStrandBone = bones.map(b => b.name.startsWith('strand_'));
  const nearSkin = (k, chain) => {
    for (let a = -2; a <= 2; a++) for (let b = -2; b <= 2; b++) for (let c = -2; c <= 2; c++) {
      const j = C.find(X[k] + a, Y[k] + b, Z[k] + c);
      if (j === undefined || gone[j] || boneOf[j] < 0 || chainOf[boneOf[j]] !== chain) continue;
      if (ARM_SKIN.test(C.P[C.PAL[j]])) return true;
    }
    return false;
  };
  const loopEdge = (k, chain) => {
    for (const [a, b, c] of N26) {
      const j = C.find(X[k] + a, Y[k] + b, Z[k] + c);
      if (j === undefined || gone[j] || boneOf[j] < 0 || chainOf[boneOf[j]] !== chain) continue;
      if (C.P[C.PAL[j]] === 'lime_stained_glass') return true;
    }
    return false;
  };
  for (let pass = 0; pass < 40; pass++) {
    const moveTo = [];
    for (let k = 0; k < n; k++) {
      if (gone[k] || boneOf[k] < 0 || !isStrandBone[boneOf[k]] || !LIGHT.test(C.P[C.PAL[k]])) continue;
      const by = new Map();
      for (const [a, b, c] of N26) {
        const j = C.find(X[k] + a, Y[k] + b, Z[k] + c);
        if (j === undefined || gone[j] || boneOf[j] < 0 || !isArmBone[boneOf[j]]) continue;
        by.set(boneOf[j], (by.get(boneOf[j]) || 0) + 1);
      }
      let best = -1, most = 0;
      for (const [b, m] of by) if (m > most) { best = b; most = m; }
      // (right against a loop's lime, one block of the arm is enough: the light fills the loop)
      if (best >= 0 && (most >= 3 || loopEdge(k, chainOf[best])) && nearSkin(k, chainOf[best])) moveTo.push([k, best]);
    }
    for (const [k, b] of moveTo) { boneOf[k] = b; armLights++; }
    if (!moveTo.length) break;
  }

  // every strand in one piece again: a bit of a strand that only touched the rest of it through lights that went to
  // an arm goes with what it touches most (left as it was, it would float off on its own)
  let strandBitsMoved = 0;
  {
    const seen = new Uint8Array(n);
    const byChain = new Map();
    for (let k = 0; k < n; k++) {
      if (gone[k] || boneOf[k] < 0 || !isStrandBone[boneOf[k]]) continue;
      const c = chainOf[boneOf[k]];
      if (!byChain.has(c)) byChain.set(c, []);
      byChain.get(c).push(k);
    }
    for (const [c, ks] of byChain) {
      const pieces = [];
      for (const k0 of ks) {
        if (seen[k0]) continue;
        const piece = [k0];
        seen[k0] = 1;
        for (let q = 0; q < piece.length; q++) {
          const k = piece[q];
          for (const [a, b, cc] of N26) {
            const j = C.find(X[k] + a, Y[k] + b, Z[k] + cc);
            if (j === undefined || seen[j] || gone[j] || boneOf[j] < 0 || chainOf[boneOf[j]] !== c) continue;
            seen[j] = 1;
            piece.push(j);
          }
        }
        pieces.push(piece);
      }
      if (pieces.length < 2) continue;
      pieces.sort((p, q) => q.length - p.length);
      for (const piece of pieces.slice(1)) {
        const by = new Map();
        for (const k of piece) for (const [a, b, cc] of N26) {
          const j = C.find(X[k] + a, Y[k] + b, Z[k] + cc);
          if (j === undefined || gone[j] || boneOf[j] < 0 || chainOf[boneOf[j]] === c) continue;
          by.set(boneOf[j], (by.get(boneOf[j]) || 0) + 1);
        }
        let best = -1, most = 0;
        for (const [b, m] of by) if (m > most && !bones[b].name.startsWith('egg_')) { best = b; most = m; }
        if (best < 0) continue;
        for (const k of piece) boneOf[k] = best;
        strandBitsMoved += piece.length;
      }
    }
  }

  // a strand lying along an arm (most of what it touches outside itself is that arm) moves with the arm: each of its
  // pieces that lies against the arm follows the piece of the arm it touches most; the rest hang from those as
  // before. Hung from the arm's root only, it was left hanging when the arm lifted. And a piece held to its own strand
  // only by a thin neck, but grown on to a pod or another strand all down its side, moves with that (hung from its
  // own strand it swung away from it and tore the sheet). Only something hung before it can carry it, so no two
  // pieces ever carry each other
  let followers = 0, necks = 0;
  {
    const F6 = [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]];
    const faces = strandDefs.map(() => new Map());
    const touch = strandDefs.map(D => D.bones.map(() => new Map()));
    const strandOfBone = new Map();
    strandDefs.forEach((D, s) => D.bones.forEach((b, i) => strandOfBone.set(b, [s, i])));
    for (let k = 0; k < n; k++) {
      if (gone[k] || boneOf[k] < 0 || !strandOfBone.has(boneOf[k])) continue;
      const [s, i] = strandOfBone.get(boneOf[k]);
      const own = chainOf[boneOf[k]];
      for (const [a, b, c] of F6) {
        const j = C.find(X[k] + a, Y[k] + b, Z[k] + c);
        if (j === undefined || gone[j] || boneOf[j] < 0 || chainOf[boneOf[j]] === own) continue;
        faces[s].set(chainOf[boneOf[j]], (faces[s].get(chainOf[boneOf[j]]) || 0) + 1);
      }
      for (const [a, b, c] of N26) {
        const j = C.find(X[k] + a, Y[k] + b, Z[k] + c);
        if (j === undefined || gone[j] || boneOf[j] < 0 || !isArmBone[boneOf[j]]) continue;
        touch[s][i].set(boneOf[j], (touch[s][i].get(boneOf[j]) || 0) + 1);
      }
    }
    // per piece: faces against the rest of its own strand, and against each bone outside it
    const ownFaces = strandDefs.map(D => D.bones.map(() => 0));
    const outFaces = strandDefs.map(D => D.bones.map(() => new Map()));
    for (let k = 0; k < n; k++) {
      if (gone[k] || boneOf[k] < 0 || !strandOfBone.has(boneOf[k])) continue;
      const [s, i] = strandOfBone.get(boneOf[k]);
      for (const [a, b, c] of F6) {
        const j = C.find(X[k] + a, Y[k] + b, Z[k] + c);
        if (j === undefined || gone[j] || boneOf[j] < 0 || boneOf[j] === boneOf[k]) continue;
        if (chainOf[boneOf[j]] === chainOf[boneOf[k]]) ownFaces[s][i]++;
        else outFaces[s][i].set(boneOf[j], (outFaces[s][i].get(boneOf[j]) || 0) + 1);
      }
    }
    // a chain is worked out after every chain whose first bone comes before its own
    const firstOf = new Map();
    for (const D of [...armDefs, ...strandDefs]) firstOf.set(chainOf[D.bones[0]], D.bones[0]);
    const before = (b, first) => {
      const name = bones[b].name;
      if (name.startsWith('egg_')) return false;
      if (isArmBone[b] || isStrandBone[b]) return firstOf.get(chainOf[b]) < first;
      if (!name.startsWith('pod_')) return true;
      const p = bones[b].parent == null ? -1 : boneIndex.get(bones[b].parent);
      return p < 0 || !(isArmBone[p] || isStrandBone[p]) || firstOf.get(chainOf[p]) < first;
    };
    strandDefs.forEach((D, s) => {
      const follow = D.bones.map(() => -1);
      let all = 0, arm = null, most = 0;
      for (const [c, m] of faces[s]) { all += m; if (c.startsWith('arm_') && m > most) { arm = c; most = m; } }
      const top = bones[D.bones[0]].parent == null ? -1 : boneIndex.get(bones[D.bones[0]].parent);
      if (arm && most >= 0.5 * all && top >= 0 && chainOf[top] === arm) {
        D.bones.forEach((b, i) => {
          let best = -1, bm = 7;
          for (const [ab, m] of touch[s][i]) if (chainOf[ab] === arm && m > bm) { best = ab; bm = m; }
          follow[i] = best;
        });
        // carried all the way down to the last piece that lies on the arm (a piece in between left to hang would be
        // pulled out long between the two when the arm lifts): one that doesn't touch it goes with the arm's
        // nearest piece
        const A = armDefs.find(A => chainOf[A.bones[0]] === arm);
        const last = follow.reduce((l, f, i) => (f >= 0 ? i : l), -1);
        const mid = (J, i, end) => J[i].map((v, a) => (v + (i + 1 < J.length ? J[i + 1] : end)[a]) / 2);
        for (let i = 0; i < last; i++) {
          if (follow[i] >= 0) continue;
          const sm = mid(D.joints, i, D.bottom);
          let best = -1, bd = Infinity;
          A.bones.forEach((ab, g) => {
            const am = mid(A.joints, g, A.tip);
            const d = (am[0] - sm[0]) ** 2 + (am[1] - sm[1]) ** 2 + (am[2] - sm[2]) ** 2;
            if (d < bd) { bd = d; best = ab; }
          });
          follow[i] = best;
        }
      }
      D.bones.forEach((b, i) => {
        if (i === 0 || follow[i] >= 0) return;
        const own = ownFaces[s][i];
        let out = 0;
        for (const m of outFaces[s][i].values()) out += m;
        if (process.env.HB_NONECK || own >= 40 || out <= 3 * Math.max(own, 1)) return;
        let best = -1, bm = Math.max(20, own);
        for (const [ob, m] of outFaces[s][i]) if (m > bm && before(ob, D.bones[0])) { best = ob; bm = m; }
        if (best >= 0) { follow[i] = best; necks++; }
      });
      if (follow.some(f => f >= 0)) { D.follow = follow; followers++; }
    });
  }

  // ---------------- the dome's shape, for the inside: the inner wall's radius at each height, and its outside
  const inner = {}, outer = {};
  for (let k = 0; k < n; k++) {
    if (label[k] !== 'bell' && label[k] !== 'rim') continue;
    const r = Math.hypot(X[k], Z[k]);
    if (r < 15) continue;
    outer[Y[k]] = Math.max(outer[Y[k]] || 0, r);
  }
  // inner wall: the smallest radius of shell voxels that are within 5 of the outside at that height
  for (let k = 0; k < n; k++) {
    if (label[k] !== 'bell' && label[k] !== 'rim') continue;
    const r = Math.hypot(X[k], Z[k]);
    if (outer[Y[k]] && r > outer[Y[k]] - 6) inner[Y[k]] = Math.min(inner[Y[k]] || 1e9, r);
  }
  const profile = [];
  for (let y = 132; y <= crownY; y++) profile.push([y, Math.round((inner[y] || 0) * 10) / 10, Math.round((outer[y] || 0) * 10) / 10]);

  return {
    bones, boneOf, gone,
    rig: {
      version: 1,
      note: 'Hollowbell rig: model space is blocks at size 1, y 0 is the ground under his strands, x and z are centred on the crown.',
      crownY, rimY: 132,
      bones: bones.map(b => ({ name: b.name, parent: b.parent, pivot: b.pivot })),
      arms: armDefs, strands: strandDefs, pods: podDefs, eggs: eggDefs,
      spots: [0, 1, 2, 3, 4].map(i => spotDef(C, boneIndex.get('spot_' + i), 'spot_' + i)),
      crown: spotDef(C, boneIndex.get('crown'), 'crown'),
      dome: profile,
      bands: bandDefs, sectors: sectorDefs,
    },
    stats: {
      recut, podBitsBack: fixed.podBits, armBitsBack: fixed.armBits, armLights, strandBitsMoved, followers, necks, eggBlocks,
      rehung,
      piecesMoved: earlyBits, piecesMovedBlocks: earlyVox, strands: strandDefs.length, pods: podDefs.length,
      eggs: eggDefs.length, branches: branched, eggsTakenOff: eggs.removed, floatingTakenOff: floatingOff + orphans,
      loosePods: pods.loose, looseEggs: eggs.loose,
    },
  };
}

// Bits of a pod or an arm that the strands took when the build was sorted, given back:
//  - the glass head of each pod (its shell, eyes and glowing bottom): the strands round it took whole sides of it,
//    so the pod was drawn cut in half and its other half swung off with the strand;
//  - the lime loops' glowing middles and the copper of an arm: a strand that touched a loop ran along its middle,
//    so the lights came off the arm as soon as it moved. A strand bit lying between two bits of the same arm
//    (within a few blocks either way) is part of the arm's skin.
const SHELL = new RegExp('^(white_stained_glass|glass|light_gray_stained_glass|tinted_glass|black_concrete|'
  + 'yellow_stained_glass|lime_stained_glass|ochre_froglight|shroomlight|sea_lantern)$');
const HEAD = /^(white_stained_glass|glass|tinted_glass|black_concrete)$/;
const ARM_SKIN = /^(weathered_copper|oxidized_copper|lime_stained_glass)$/;
const LIGHT = /^(verdant_froglight|ochre_froglight|pearlescent_froglight|sea_lantern|shroomlight|glowstone)$/;
const POD_HEAD_R = 9;

function takeBack(C, strandOf, pods, gone) {
  const { n, X, Y, Z, lab, label, find } = C;
  const mat = k => C.P[C.PAL[k]];
  const isArm = k => label[k] === null && lab[k] >= 0 && lab[k] < 8 && !gone[k];
  const isStrand = k => strandOf[k] >= 0 && !gone[k];
  let podBits = 0, armBits = 0;

  // pod heads: the middle of each pod's glass, then its shell flooded back in from the pod, within its head
  const sum = [];
  for (let k = 0; k < n; k++) {
    const i = pods.of[k];
    if (i < 0 || gone[k] || !HEAD.test(mat(k))) continue;
    const s = sum[i] || (sum[i] = [0, 0, 0, 0]);
    s[0] += X[k]; s[1] += Y[k]; s[2] += Z[k]; s[3]++;
  }
  const front = [];
  const takenFrom = new Int32Array(n).fill(-1);
  for (let k = 0; k < n; k++) if (pods.of[k] >= 0 && !gone[k]) front.push(k);
  while (front.length) {
    const k = front.pop();
    const i = pods.of[k], s = sum[i];
    if (!s) continue;
    for (const [a, b, c] of N26) {
      const j = find(X[k] + a, Y[k] + b, Z[k] + c);
      if (j === undefined || !isStrand(j) || !SHELL.test(mat(j))) continue;
      if (Math.hypot(X[j] - s[0] / s[3], Y[j] - s[1] / s[3], Z[j] - s[2] / s[3]) > POD_HEAD_R) continue;
      takenFrom[j] = strandOf[j];
      strandOf[j] = -1; pods.of[j] = i; lab[j] = 200; podBits++;
      front.push(j);
    }
  }
  // (where a strand runs on past a pod through the glass of its head, the glass it can't do without stays the strand's,
  // or the strand beyond it would be cut off)
  for (let pass = 0; pass < 12; pass++) {
    const comp = new Int32Array(n).fill(-1), size = [], strandOfComp = [];
    for (let k0 = 0; k0 < n; k0++) {
      if (!isStrand(k0) || comp[k0] >= 0) continue;
      const id = size.length, ks = [k0];
      comp[k0] = id;
      for (let q = 0; q < ks.length; q++) {
        const k = ks[q];
        for (const [a, b, c] of N26) {
          const j = find(X[k] + a, Y[k] + b, Z[k] + c);
          if (j === undefined || comp[j] >= 0 || !isStrand(j) || strandOf[j] !== strandOf[k0]) continue;
          comp[j] = id;
          ks.push(j);
        }
      }
      size.push(ks.length); strandOfComp.push(strandOf[k0]);
    }
    const biggest = new Map();
    size.forEach((sz, id) => {
      const s = strandOfComp[id];
      if (!biggest.has(s) || size[biggest.get(s)] < sz) biggest.set(s, id);
    });
    const back = [];
    for (let k = 0; k < n; k++) {
      if (comp[k] < 0 || biggest.get(strandOf[k]) === comp[k]) continue;
      for (const [a, b, c] of N26) {
        const j = find(X[k] + a, Y[k] + b, Z[k] + c);
        if (j !== undefined && takenFrom[j] === strandOf[k] && pods.of[j] >= 0) back.push(j);
      }
    }
    for (const j of back) {
      if (pods.of[j] < 0) continue;
      strandOf[j] = takenFrom[j]; pods.of[j] = -1; lab[j] = 100; podBits--;
    }
    if (!back.length) break;
  }

  // arm skin: copper and lime touching the arm, then anything of a strand lying between two bits of one arm
  const DIRS = [];
  for (let a = -1; a <= 1; a++) for (let b = -1; b <= 1; b++) for (let c = -1; c <= 1; c++) {
    if (a > 0 || (a === 0 && b > 0) || (a === 0 && b === 0 && c >= 0)) DIRS.push([a, b, c]);
  }
  const armAlong = (k, d, sign) => {
    for (let s = 1; s <= 3; s++) {
      const j = find(X[k] + d[0] * s * sign, Y[k] + d[1] * s * sign, Z[k] + d[2] * s * sign);
      if (j === undefined) return -1;
      if (isArm(j)) return lab[j];
      if (!isStrand(j)) return -1;
    }
    return -1;
  };
  for (let pass = 0; pass < 8; pass++) {
    const set = [];
    for (let k = 0; k < n; k++) {
      if (!isStrand(k)) continue;
      let arm = -1;
      if (ARM_SKIN.test(mat(k))) {
        for (const [a, b, c] of N26) {
          const j = find(X[k] + a, Y[k] + b, Z[k] + c);
          if (j !== undefined && isArm(j)) { arm = lab[j]; break; }
        }
      }
      for (const d of DIRS) {
        if (arm >= 0) break;
        const up = armAlong(k, d, 1);
        if (up >= 0 && armAlong(k, d, -1) === up) arm = up;
      }
      if (arm >= 0) set.push([k, arm]);
    }
    for (const [k, a] of set) { strandOf[k] = -1; lab[k] = a; armBits++; }
    if (!set.length) break;
  }
  // what's left of a strand that lies along an arm nearly all its length (a loop's middle the rule above missed) is
  // the arm's too
  const along = new Map();
  for (let k = 0; k < n; k++) {
    if (!isStrand(k)) continue;
    const s = along.get(strandOf[k]) || { all: 0, by: new Map() };
    s.all++;
    for (const [a, b, c] of N26) {
      const j = find(X[k] + a, Y[k] + b, Z[k] + c);
      if (j !== undefined && isArm(j)) { s.by.set(lab[j], (s.by.get(lab[j]) || 0) + 1); break; }
    }
    along.set(strandOf[k], s);
  }
  // (and a "strand" that is hardly any bone or calcite, touching an arm, is the arm's own end: its copper, loop and
  // lights going on down to the ground)
  const pale = new Map();
  for (let k = 0; k < n; k++) {
    if (isStrand(k) && /^(bone_block|calcite)$/.test(mat(k))) pale.set(strandOf[k], (pale.get(strandOf[k]) || 0) + 1);
  }
  for (const [s, o] of along) {
    const best = [...o.by].sort((p, q) => q[1] - p[1])[0];
    if (!best || (best[1] < 0.6 * o.all && (pale.get(s) || 0) > o.all / 3)) continue;
    for (let k = 0; k < n; k++) if (strandOf[k] === s && !gone[k]) { strandOf[k] = -1; lab[k] = best[0]; armBits++; }
  }
  return { podBits, armBits };
}

function spotDef(C, bone, name) {
  const ks = []; for (let k = 0; k < C.n; k++) if (C.label[k] === name) ks.push(k);
  const c = [0, 0, 0]; for (const k of ks) { c[0] += C.X[k]; c[1] += C.Y[k]; c[2] += C.Z[k]; }
  c.forEach((v, i) => c[i] = v / ks.length);
  let r = 0; for (const k of ks) r = Math.max(r, Math.hypot(C.X[k] - c[0], C.Y[k] - c[1], C.Z[k] - c[2]));
  return { bone, centre: c.map(v => Math.round(v * 10) / 10), radius: Math.round(r * 10) / 10, size: ks.length };
}

module.exports = { buildRig };
