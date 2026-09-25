#!/usr/bin/env node
// Before / after pictures of a change to the model:  node tools/compare.js <old model.bin> <name>
// Writes reference/converted/<name>_before.png and <name>_after.png (the current model), from the same angle,
// plus a close look at the arms and strands (<name>_before_close.png / _after_close.png).
const path = require('path');
const { readModel, draw, PROJ, OUT } = require('./render');
const { blockColour } = require('./lib/view');

function items(m) {
  const cols = m.palette.map(blockColour);
  const glassAlpha = m.palette.map(n => /tinted_glass/.test(n) ? 0.85 : /stained_glass/.test(n) ? 0.55 : /glass/.test(n) ? 0.25 : 0);
  const out = [];
  for (const b of m.bones) for (const v of b.v) { const g = glassAlpha[v[3]]; out.push([v[0], v[1], v[2], cols[v[3]], g > 0, g > 0 ? g : 1]); }
  return out;
}

const [oldFile, name] = process.argv.slice(2);
if (!oldFile || !name) { console.log('node tools/compare.js <old model.bin> <name>'); process.exit(1); }
const before = items(readModel(oldFile)), after = items(readModel());
draw(path.join(OUT, `${name}_before.png`), before, PROJ.iso, 3);
draw(path.join(OUT, `${name}_after.png`), after, PROJ.iso, 3);
// under the bell, a quarter of him, close
const close = v => v[1] < 130 && v[0] > -10 && v[2] < 10;
draw(path.join(OUT, `${name}_before_close.png`), before.filter(close), PROJ.front, 5);
draw(path.join(OUT, `${name}_after_close.png`), after.filter(close), PROJ.front, 5);
