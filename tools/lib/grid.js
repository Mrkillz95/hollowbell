// A dense voxel grid for the converter: x and z from -128 to 127, y from 0 to 383.
const OX = 128, OZ = 128, SX = 256, SY = 384, SZ = 256;

class Grid {
  constructor() { this.a = new Int16Array(SX * SY * SZ); }
  static inside(x, y, z) { return x >= -OX && x < SX - OX && y >= 0 && y < SY && z >= -OZ && z < SZ - OZ; }
  static idx(x, y, z) { return ((x + OX) * SY + y) * SZ + (z + OZ); }
  static unidx(i) { const z = i % SZ - OZ; const t = (i - (z + OZ)) / SZ; const y = t % SY; const x = (t - y) / SY - OX; return [x, y, z]; }
  /** palette index + 1, or 0 for empty */
  get(x, y, z) { return Grid.inside(x, y, z) ? this.a[Grid.idx(x, y, z)] : 0; }
  set(x, y, z, v) { if (!Grid.inside(x, y, z)) throw new Error(`voxel out of range ${x} ${y} ${z}`); this.a[Grid.idx(x, y, z)] = v; }
  forEach(fn) { const a = this.a; for (let i = 0; i < a.length; i++) if (a[i]) { const [x, y, z] = Grid.unidx(i); fn(x, y, z, a[i] - 1, i); } }
}
module.exports = { Grid };
