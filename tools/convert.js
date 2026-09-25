#!/usr/bin/env node
// Hollowbell model converter.
//
//   node tools/convert.js
//
// Reads build-source/ (34 Bedrock .mcstructure pieces placed by hollowbell.mcfunction), drops the ground patch,
// maps every block to its Java name, tidies it up, sorts every voxel into a part, and writes
//   src/main/resources/hollowbell/hollowbell_model.bin   (the voxels, one list per bone)
//   src/main/resources/hollowbell/hollowbell_rig.json    (the bones and every part)
// Then run tools/render.js to draw it to PNG.
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const { load } = require('./lib/load');
const { classify } = require('./lib/classify');
const { buildRig } = require('./lib/rig');
const { isGlass } = require('./lib/names');
const { thinSpeckles, dropFloating } = require('./lib/tidy');

const ROOT = path.join(__dirname, '..');
const OUT = path.join(ROOT, 'src', 'main', 'resources', 'hollowbell');

function main() {
  const t0 = Date.now();
  const M = load(path.join(ROOT, 'build-source'));
  console.log(`read the build: ${M.dropped} ground blocks dropped, ${M.keptLow} strand ends kept`);
  const C = classify(M);
  const R = buildRig(M, C);
  console.log('parts:', JSON.stringify(R.stats));

  // one list of every voxel
  const palette = [];
  const palIdx = new Map();
  const pal = name => { if (!palIdx.has(name)) { palIdx.set(name, palette.length); palette.push(name); } return palIdx.get(name); };
  const vox = [];   // [x, y, z, pal, bone, alpha]
  for (let k = 0; k < C.n; k++) if (!R.gone[k]) vox.push([C.X[k], C.Y[k], C.Z[k], pal(M.palette[C.PAL[k]]), R.boneOf[k], 255]);
  // 1.1: a little less cluttered
  const floating = dropFloating(vox, R.bones);
  const thinned = thinSpeckles(vox, R.bones, palette, pal);
  console.log(`tidied: ${R.stats.eggsTakenOff} egg clumps and ${R.stats.floatingTakenOff + floating} floating blocks taken off, ${thinned.arm} arm speckles and ${thinned.strand} strand dots thinned out`);
  const occupied = new Map();
  const key = (x, y, z) => ((x + 512) * 1024 + y) * 1024 + (z + 512);
  for (let i = 0; i < vox.length; i++) occupied.set(key(vox[i][0], vox[i][1], vox[i][2]), i);
  const glass = palette.map(isGlass);

  // which faces show: a face is hidden by an opaque neighbour, or by the same glass, but only inside one bone;
  // where two bones meet, both faces stay (they can move apart). The one exception is the same glass running on
  // across two parts of the dome: the join between them stays clear.
  const bones = R.bones;
  const bellish = bones.map(b => /^(bell_|rim_|crown|spot_)/.test(b.name));
  const DIRS = [[0, -1, 0], [0, 1, 0], [0, 0, -1], [0, 0, 1], [-1, 0, 0], [1, 0, 0]];   // down up north south west east
  // how deep each voxel is from open air: a face buried under two or more layers of glass can't be made out,
  // so it is left out (the yellow balls are solid glass full of speckles)
  const depth = new Uint8Array(vox.length).fill(255);
  {
    let front = [];
    for (let i = 0; i < vox.length; i++) {
      const [x, y, z] = vox[i];
      for (const o of DIRS) if (!occupied.has(key(x + o[0], y + o[1], z + o[2]))) { depth[i] = 1; front.push(i); break; }
    }
    for (let d = 2; d <= 3 && front.length; d++) {
      const nf = [];
      for (const i of front) {
        const [x, y, z] = vox[i];
        for (const o of DIRS) { const j = occupied.get(key(x + o[0], y + o[1], z + o[2])); if (j !== undefined && depth[j] === 255) { depth[j] = d; nf.push(j); } }
      }
      front = nf;
    }
  }
  const faces = new Uint8Array(vox.length);
  let faceCount = 0;
  for (let i = 0; i < vox.length; i++) {
    const [x, y, z, p, b, al] = vox[i];
    let f = 0;
    for (let d = 0; d < 6; d++) {
      const o = DIRS[d];
      const j = occupied.get(key(x + o[0], y + o[1], z + o[2]));
      let show = true;
      if (j !== undefined) {
        const [, , , q, c, al2] = vox[j];
        const same = b === c;
        if (!same && bellish[b] && bellish[c] && glass[p] && q === p) show = false;
        if (same && al === 255 && al2 === 255 && (!glass[q] || (glass[p] && q === p))) show = false;
        if (same && al < 255 && al2 < 255 && !glass[q]) show = false;
        if (same && Math.min(depth[i], depth[j]) >= 3) show = false;
      }
      if (show) { f |= 1 << d; faceCount++; }
    }
    faces[i] = f;
  }
  console.log(`${vox.length} voxels, ${faceCount} faces showing`);

  // write the model: gzip( "HBEL" version palette bones[ name count voxels[x y z pal faces alpha] ] )
  const perBone = bones.map(() => []);
  for (let i = 0; i < vox.length; i++) if (faces[i]) perBone[vox[i][4]].push(i);
  const parts = [];
  const u8 = v => { const b = Buffer.alloc(1); b.writeUInt8(v); parts.push(b); };
  const i16 = v => { const b = Buffer.alloc(2); b.writeInt16BE(v); parts.push(b); };
  const i32 = v => { const b = Buffer.alloc(4); b.writeInt32BE(v); parts.push(b); };
  const utf = s => { const d = Buffer.from(s, 'utf8'); i16(d.length); parts.push(d); };
  parts.push(Buffer.from('HBEL'));
  i32(1);
  i32(palette.length); palette.forEach(utf);
  i32(bones.length);
  bones.forEach((b, bi) => {
    utf(b.name);
    const list = perBone[bi];
    i32(list.length);
    const buf = Buffer.alloc(list.length * 9);
    list.forEach((i, j) => {
      const v = vox[i];
      buf.writeInt16BE(v[0], j * 9); buf.writeInt16BE(v[1], j * 9 + 2); buf.writeInt16BE(v[2], j * 9 + 4);
      buf.writeInt16BE(v[3], j * 9 + 6); buf.writeUInt8(faces[i], j * 9 + 8);
    });
    parts.push(buf);
    // alpha only for bones that fade
    const fades = list.some(i => vox[i][5] < 255);
    u8(fades ? 1 : 0);
    if (fades) { const a = Buffer.alloc(list.length); list.forEach((i, j) => a.writeUInt8(vox[i][5], j)); parts.push(a); }
  });
  fs.mkdirSync(OUT, { recursive: true });
  const bin = zlib.gzipSync(Buffer.concat(parts), { level: 9 });
  fs.writeFileSync(path.join(OUT, 'hollowbell_model.bin'), bin);
  const rig = R.rig;
  rig.palette = palette;
  rig.voxels = vox.length; rig.faces = faceCount;
  rig.boneVoxels = perBone.map(l => l.length);
  fs.writeFileSync(path.join(OUT, 'hollowbell_rig.json'), JSON.stringify(rig));
  console.log(`wrote hollowbell_model.bin (${(bin.length / 1048576).toFixed(2)} MB) and hollowbell_rig.json: ${bones.length} bones, ${palette.length} blocks, ${((Date.now() - t0) / 1000).toFixed(1)} s`);
}

main();
