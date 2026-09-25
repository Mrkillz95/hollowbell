// 1.1: makes him a little less cluttered, while keeping him clearly the KillzAI build.
//  - thinSpeckles: about half of the single bone and calcite dots in the copper of his arms go back to copper
//    (never the lime loops or the pale middles inside them), and about half of the single stray bits in his
//    strands (froglight, copper, glass) go back to the bone or calcite round them.
//  - dropFloating: little lumps that touch nothing else of him are taken off.

const N6 = [[0, -1, 0], [0, 1, 0], [0, 0, -1], [0, 0, 1], [-1, 0, 0], [1, 0, 0]];
const N26 = [];
for (let a = -1; a <= 1; a++) for (let b = -1; b <= 1; b++) for (let c = -1; c <= 1; c++) if (a || b || c) N26.push([a, b, c]);
const key = (x, y, z) => ((x + 512) * 1024 + y) * 1024 + (z + 512);

function hash(x, y, z) {
  let h = (x * 374761393 + y * 668265263 + z * 1274126177) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}

function index(vox) { const m = new Map(); vox.forEach((v, i) => m.set(key(v[0], v[1], v[2]), i)); return m; }

function thinSpeckles(vox, bones, palette, pal) {
  const at = index(vox);
  const name = i => palette[vox[i][3]].replace('minecraft:', '');
  const kindOf = b => bones[b].name.split('_')[0];
  const COPPER = /^(weathered_copper|oxidized_copper)$/, PALE = /^(bone_block|calcite)$/;
  const STRAY = /^(verdant_froglight|oxidized_cut_copper|sea_lantern|glass|light_gray_stained_glass|white_stained_glass)$/;
  const changes = [];
  let arm = 0, strand = 0;
  for (let i = 0; i < vox.length; i++) {
    const [x, y, z, , b] = vox[i];
    const kind = kindOf(b);
    const n = name(i);
    if (kind === 'arm' && PALE.test(n)) {
      // a dot: its six sides are copper (or open air), and no lime glass anywhere near
      let copper = 0, other = 0, lime = false;
      const counts = {};
      for (const [a, c, e] of N6) {
        const j = at.get(key(x + a, y + c, z + e));
        if (j === undefined) continue;
        const m = name(j);
        if (COPPER.test(m)) { copper++; counts[m] = (counts[m] || 0) + 1; } else other++;
      }
      for (let a = -2; a <= 2 && !lime; a++) for (let c = -2; c <= 2 && !lime; c++) for (let e = -2; e <= 2 && !lime; e++) {
        const j = at.get(key(x + a, y + c, z + e));
        if (j !== undefined && name(j) === 'lime_stained_glass') lime = true;
      }
      if (lime || copper < 2 || other > 0) continue;
      if (hash(x, y, z) < 0.5) {
        const to = Object.entries(counts).sort((p, q) => q[1] - p[1])[0][0];
        changes.push([i, pal('minecraft:' + to)]); arm++;
      }
    } else if (kind === 'strand' && STRAY.test(n)) {
      // a single stray bit: nothing of the same block touches it, and bone or calcite is all round it
      let same = false, pale = 0;
      const counts = {};
      for (const [a, c, e] of N26) {
        const j = at.get(key(x + a, y + c, z + e));
        if (j === undefined) continue;
        const m = name(j);
        if (m === n) same = true;
        if (PALE.test(m)) { pale++; counts[m] = (counts[m] || 0) + 1; }
      }
      if (same || pale < 4) continue;
      if (hash(x, y, z) < 0.5) {
        const to = Object.entries(counts).sort((p, q) => q[1] - p[1])[0][0];
        changes.push([i, pal('minecraft:' + to)]); strand++;
      }
    }
  }
  for (const [i, p] of changes) vox[i][3] = p;
  return { arm, strand };
}

/** keeps only the one big piece of him (26-connected): every lump that touches nothing else of him is taken off */
function dropFloating(vox, bones) {
  const at = index(vox);
  const comp = new Int32Array(vox.length).fill(-1);
  const sizes = [];
  for (let s = 0; s < vox.length; s++) {
    if (comp[s] >= 0) continue;
    const id = sizes.length; let size = 0;
    const st = [s]; comp[s] = id;
    while (st.length) {
      const i = st.pop(); size++;
      const [x, y, z] = vox[i];
      for (const [a, c, e] of N26) {
        const j = at.get(key(x + a, y + c, z + e));
        if (j !== undefined && comp[j] < 0) { comp[j] = id; st.push(j); }
      }
    }
    sizes.push(size);
  }
  let dropped = 0;
  const biggest = Math.max(...sizes);
  const big = sizes.map(sz => sz === biggest);
  for (let i = vox.length - 1; i >= 0; i--) if (!big[comp[i]]) { vox.splice(i, 1); dropped++; }
  const others = sizes.filter(sz => sz !== biggest).sort((a, b) => b - a);
  console.log(`  floating lumps taken off: ${others.length} (biggest ${others[0] || 0} blocks)`);
  return dropped;
}

module.exports = { thinSpeckles, dropFloating };
