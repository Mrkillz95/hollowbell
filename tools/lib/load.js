// Loads the build, drops the ground patch and maps every block to its Java name.
const path = require('path');
const { readBuild } = require('./build');
const { javaName, isGlass } = require('./names');
const { Grid } = require('./grid');

// Blocks that only the ground patch is made of. None of them appear above y 6.
const GROUND_ONLY = new Set(['end_stone', 'end_bricks', 'moss_block', 'stained_hardened_clay[color=green]']);
// The grey trails across the ground patch.
const TRAIL = 'concrete[color=silver]';
// The pale stuff the strands (and the calcite middle of the ground) are made of.
const PALE = new Set(['bone_block', 'calcite', 'verdant_froglight', 'sea_lantern', 'oxidized_cut_copper']);
const GROUND_TOP = 5;     // y 0-5 is the ground patch
const CHECK_TOP = 7;      // ground scraps poke up to y 7

function load(buildDir) {
  const { names, vox } = readBuild(buildDir);
  const raw = new Grid();
  for (let i = 0; i < vox.length; i += 4) raw.set(vox[i], vox[i + 1], vox[i + 2], vox[i + 3] + 1);

  // Work down from the top of the ground scraps: a pale block is kept only if the block right above it is
  // kept (so a strand carries on down to the ground and the calcite middle between strands goes); anything
  // else (copper, glass: the arm tips) is kept if it touches a kept block above it.
  const g = new Grid();
  raw.forEach((x, y, z, p) => {
    const n = names[p];
    if (GROUND_ONLY.has(n)) return;
    if (y > CHECK_TOP) g.set(x, y, z, p + 1);
  });
  let dropped = 0, keptLow = 0;
  for (let y = CHECK_TOP; y >= 0; y--) {
    for (let x = -128; x < 128; x++) for (let z = -128; z < 128; z++) {
      const v = raw.get(x, y, z);
      if (!v) continue;
      const n = names[v - 1];
      if (GROUND_ONLY.has(n) || n === TRAIL) { dropped++; continue; }
      let keep = false;
      if (PALE.has(n)) keep = g.get(x, y + 1, z) > 0;
      else for (let a = -1; a <= 1 && !keep; a++) for (let b = -1; b <= 1 && !keep; b++) if (g.get(x + a, y + 1, z + b)) keep = true;
      if (keep) { g.set(x, y, z, v); keptLow++; } else dropped++;
    }
  }
  // Java names, fail loudly on anything unknown
  const java = names.map(javaName);
  const palette = [...new Set(java)];
  const remap = java.map(j => palette.indexOf(j));
  g.a.forEach((v, i) => { if (v) g.a[i] = remap[v - 1] + 1; });
  return { grid: g, palette, glass: palette.map(isGlass), bedrockNames: names, dropped, keptLow };
}

module.exports = { load, GROUND_TOP };
