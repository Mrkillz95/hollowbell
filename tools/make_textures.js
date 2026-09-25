#!/usr/bin/env node
// Draws the mod's own item and armor textures (plain pixel art, 16x16 items, 64x32 armor layers).
// node tools/make_textures.js
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const ROOT = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'hollowbell', 'textures');
const CRC = []; for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; CRC[n] = c >>> 0; }
const crc = b => { let c = 0xffffffff; for (const x of b) c = CRC[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); };
function png(file, w, h, px) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const p = px[y * w + x] || [0, 0, 0, 0]; raw.set(p.length === 4 ? p : [...p, 255], y * (w * 4 + 1) + 1 + x * 4); }
  const ih = Buffer.alloc(13); ih.writeUInt32BE(w, 0); ih.writeUInt32BE(h, 4); ih[8] = 8; ih[9] = 6;
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ih), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]));
}
// draw from a picture made of letters
function art(rows, pal) { const px = []; rows.forEach((r, y) => [...r].forEach((ch, x) => { px[y * 16 + x] = pal[ch] || [0, 0, 0, 0]; })); return px; }
const P = {
  B: [229, 225, 207], c: [205, 206, 200], O: [82, 162, 132], o: [60, 120, 98], W: [108, 153, 110], L: [112, 185, 25], l: [160, 220, 90],
  Y: [248, 197, 39], y: [255, 230, 140], G: [62, 68, 71], g: [142, 142, 134], K: [20, 21, 25], T: [21, 137, 145], t: [90, 190, 200],
  V: [229, 244, 228], D: [40, 60, 50], n: [130, 90, 60], w: [233, 236, 236],
};
const items = {
  stinger: [
    '..............Ll',
    '.............LlL',
    '............BlL.',
    '...........Bc...',
    '..........Bc....',
    '.........cB.....',
    '........Bc......',
    '.......Bc.......',
    '......cB........',
    '.....Bc.........',
    '....oO..........',
    '...OOo..........',
    '..oOW...........',
    '.oOo............',
    'WoO.............',
    'oW..............'],
  bell_glass: [
    '................',
    '.....tTTTt......',
    '....tlLLLLt.....',
    '...tLlwLLLLt....',
    '..tLLwLLLLLLt...',
    '..TLLLLLLLLLT...',
    '..TLLLLLLlLLLT..',
    '..TLLLLLLLLLLT..',
    '...TLLLLLLLLT...',
    '...TLLLLLLLT....',
    '....TLLLLLT.....',
    '.....TLLLT......',
    '......TTT.......',
    '................',
    '................',
    '................'],
  hollowbell_pod: [
    '................',
    '......gggg......',
    '....gwwwwwwg....',
    '...gwwwwwwwwg...',
    '..gwwKwwwwKwwg..',
    '..gwKKwwwwKKwg..',
    '..gwwwwwwwwwwg..',
    '..gwwwwKKwwwwg..',
    '..gwwwwwwwwwwg..',
    '...gwwwwwwwwg...',
    '...gLLLLLLLLg...',
    '....LlLLLlLL....',
    '.....LLYYLL.....',
    '......LYYL......',
    '.......YY.......',
    '................'],
  hollowbell_codex: [
    '................',
    '..oOOOOOOOOOOo..',
    '..OTTTTTTTTTTOB.',
    '..OTtTTTTTTTTOB.',
    '..OTTTTYYTTTTOB.',
    '..OTTTYyyYTTTOB.',
    '..OTTTYyyYTTTOB.',
    '..OTTTTYYTTTTOB.',
    '..OTTLTTTTLTTOB.',
    '..OTLTTTTTTLTOB.',
    '..OTTTTTTTTTTOB.',
    '..OTTTTTTTTTTOB.',
    '..OTTTTTTTTTTOB.',
    '..oOOOOOOOOOOoB.',
    '...BBBBBBBBBBBB.',
    '................'],
  bell_glass_helmet: [
    '................', '................', '................',
    '....OOOOOOOO....', '...OLLLLLLLLO...', '...OLlLLLLLLO...', '...OLLLLLLLLO...', '...OLL....LLO...',
    '...OL......LO...', '...OO......OO...', '................', '................', '................', '................', '................', '................'],
  bell_glass_chestplate: [
    '................', '..OOO......OOO..', '..OLLO....OLLO..', '..OLLLOOOOLLLO..', '..OLLLLLLLLLLO..', '...OLlLLLLLLO...', '....OLLLLLLO....',
    '....OLLLLLLO....', '....OLLLLLLO....', '....OLLLLLLO....', '....OLLLLLLO....', '....OLLLLLLO....', '....OOOOOOOO....', '................', '................', '................'],
  bell_glass_leggings: [
    '................', '................', '....OOOOOOOO....', '....OLLLLLLO....', '....OLlLLLLO....', '....OLLOOLLO....', '....OLLOOLLO....',
    '....OLLOOLLO....', '....OLLOOLLO....', '....OLLOOLLO....', '....OLLOOLLO....', '....OOOOOOOO....', '................', '................', '................', '................'],
  bell_glass_boots: [
    '................', '................', '................', '................', '................', '................', '................',
    '...OOO....OOO...', '...OLO....OLO...', '...OLO....OLO...', '...OLO....OLO...', '..OOLO....OLOO..', '..OLLO....OLLO..', '..OOOO....OOOO..', '................', '................'],
};
for (const [n, rows] of Object.entries(items)) png(path.join(ROOT, 'item', n + '.png'), 16, 16, art(rows, P));
// armor layers: a glassy green, darker copper at the edges of each 4x4 block
for (const layer of [1, 2]) {
  const px = [];
  for (let y = 0; y < 32; y++) for (let x = 0; x < 64; x++) {
    const edge = x % 4 === 0 || y % 4 === 0;
    const spot = ((x * 7 + y * 13) % 11) === 0;
    px[y * 64 + x] = edge ? [82, 162, 132, 255] : spot ? [229, 225, 207, 255] : layer === 1 ? [112, 185, 25, 200] : [90, 170, 40, 200];
  }
  png(path.join(ROOT, 'models', 'armor', `bell_glass_layer_${layer}.png`), 64, 32, px);
}
console.log('textures written');
