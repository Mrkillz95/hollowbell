// Cuts each piece of an arm or strand into slices across its length, for drawing it bent smoothly (BellPieces in the
// game lays each slice along one curve through the chain's joints). A slice turns as one, so where a bend opens up
// between two of them, the far side of a wide piece opens up most: the wider the piece, the thinner its slices.

// an arm curls right round as built and is laid out straight on the ground, so every slice of it turns
const ARM_SLICES = 16;
const STRAND_SLICES = 3;
const STRAND_MAX = 8;
/** the widest a slice's turn may open things apart, roughly: about this many blocks out from the middle per slice */
const WIDTH_PER_SLICE = 2;

function cutSlices(vox, rig) {
  const nb = rig.bones.length;
  const slices = new Array(nb).fill(1);
  const sliceOf = new Uint8Array(vox.length);
  const byBone = Array.from({ length: nb }, () => []);
  vox.forEach((v, i) => byBone[v[4]].push(i));
  const chains = [...rig.arms.map(D => [true, D.bones, [...D.joints, D.tip]]),
    ...rig.strands.map(D => [false, D.bones, [...D.joints, D.bottom]])];
  for (const [arm, bones, pts] of chains) {
    bones.forEach((b, s) => {
      const list = byBone[b];
      if (!list.length) return;
      const a = pts[s], c = pts[s + 1];
      const dx = c[0] - a[0], dy = c[1] - a[1], dz = c[2] - a[2];
      const l2 = Math.max(1e-4, dx * dx + dy * dy + dz * dz);
      const t = new Float64Array(list.length), side = [];
      list.forEach((i, j) => {
        const [x, y, z] = vox[i];
        const px = x + 0.5 - a[0], py = y + 0.5 - a[1], pz = z + 0.5 - a[2];
        t[j] = (px * dx + py * dy + pz * dz) / l2;
        const u = Math.max(0, Math.min(1, t[j]));
        side.push(Math.hypot(px - dx * u, py - dy * u, pz - dz * u));
      });
      side.sort((p, q) => p - q);
      const wide = side[Math.floor(side.length * 0.9)];
      const want = Math.ceil(wide / WIDTH_PER_SLICE);
      const P = arm ? ARM_SLICES : Math.max(STRAND_SLICES, Math.min(STRAND_MAX, want));
      slices[b] = P;
      list.forEach((i, j) => { sliceOf[i] = Math.max(0, Math.min(P - 1, Math.floor(t[j] * P))); });
    });
  }
  return { sliceOf, slices };
}

module.exports = { cutSlices };
