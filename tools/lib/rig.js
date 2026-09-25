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
  for (let a = 0; a < 8; a++) {
    const ks = []; for (let k = 0; k < n; k++) if (label[k] === null && lab[k] === a && !gone[k]) ks.push(k);
    let y0 = 1e9, y1 = -1e9; for (const k of ks) { y0 = Math.min(y0, Y[k]); y1 = Math.max(y1, Y[k]); }
    const cuts = []; for (let s = 0; s <= ARM_SEGS; s++) cuts.push(y1 + 1 - (y1 + 1 - y0) * s / ARM_SEGS);
    const segOf = k => { for (let s = 0; s < ARM_SEGS; s++) if (Y[k] >= cuts[s + 1]) return s; return ARM_SEGS - 1; };
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
    stats: { strands: strandDefs.length, pods: podDefs.length, eggs: eggDefs.length, branches: branched, eggsTakenOff: eggs.removed, floatingTakenOff: floatingOff + orphans, loosePods: pods.loose, looseEggs: eggs.loose },
  };
}

function spotDef(C, bone, name) {
  const ks = []; for (let k = 0; k < C.n; k++) if (C.label[k] === name) ks.push(k);
  const c = [0, 0, 0]; for (const k of ks) { c[0] += C.X[k]; c[1] += C.Y[k]; c[2] += C.Z[k]; }
  c.forEach((v, i) => c[i] = v / ks.length);
  let r = 0; for (const k of ks) r = Math.max(r, Math.hypot(C.X[k] - c[0], C.Y[k] - c[1], C.Z[k] - c[2]));
  return { bone, centre: c.map(v => Math.round(v * 10) / 10), radius: Math.round(r * 10) / 10, size: ks.length };
}

module.exports = { buildRig };
