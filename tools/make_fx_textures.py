# makes the big-moment kit's textures: puff (4 soft blobs), ring band, crack line. usage: make_fx_textures.py <outdir>
import sys, numpy as np
from PIL import Image
out = sys.argv[1]
rng = np.random.default_rng(7)

def noise(n, octaves=4, seed=0):
    r = np.random.default_rng(seed)
    acc = np.zeros((n, n)); amp = 1; tot = 0
    for o in range(octaves):
        k = 2 ** (o + 2)
        g = r.random((k + 1, k + 1))
        ys = np.linspace(0, k, n, endpoint=False); xs = ys
        x0 = xs.astype(int); fx = xs - x0; fx = fx * fx * (3 - 2 * fx)
        a = g[np.ix_(x0, x0)]; b = g[np.ix_(x0, x0 + 1)]; c = g[np.ix_(x0 + 1, x0)]; d = g[np.ix_(x0 + 1, x0 + 1)]
        top = a + (b - a) * fx[None, :]; bot = c + (d - c) * fx[None, :]
        acc += amp * (top + (bot - top) * fx[:, None]); tot += amp; amp *= 0.5
    return acc / tot

# puff: 64x64, four 32x32 soft round blobs with lumpy edges, white, alpha carries the shape
puff = np.zeros((64, 64, 4), np.uint8)
for v in range(4):
    n = 32
    y, x = np.mgrid[0:n, 0:n] + 0.5
    cx = cy = n / 2
    ang = np.arctan2(y - cy, x - cx)
    nz = noise(n, 4, 11 + v)
    lump = 1 + 0.18 * np.sin(ang * (3 + v) + v) + 0.25 * (nz - 0.5)
    d = np.hypot(x - cx, y - cy) / (n * 0.46) / lump
    a = np.clip(1 - d, 0, 1) ** 1.3
    a *= 0.75 + 0.5 * (nz - 0.5) + 0.25
    a = np.clip(a, 0, 1)
    shade = 0.82 + 0.18 * nz            # a little texture in the white
    rgb = np.clip(shade * 255, 0, 255)
    ox, oy = (v % 2) * 32, (v // 2) * 32
    puff[oy:oy + n, ox:ox + n, 0] = rgb; puff[oy:oy + n, ox:ox + n, 1] = rgb; puff[oy:oy + n, ox:ox + n, 2] = rgb
    puff[oy:oy + n, ox:ox + n, 3] = (a * 255).astype(np.uint8)
Image.fromarray(puff, 'RGBA').save(out + '/puff.png')

# ring: 64 wide (along the ring, tiles) x 32 tall (across: 0 inner edge .. 1 outer edge, the front)
W, H = 64, 32
v = (np.arange(H) + 0.5) / H
prof = np.where(v < 0.78, (v / 0.78) ** 1.6, np.clip(1 - (v - 0.78) / 0.22, 0, 1) ** 0.7)
nz = noise(64, 4, 5)[:H, :W]
ring = np.zeros((H, W, 4), np.uint8)
a = np.clip(prof[:, None] * (0.7 + 0.6 * (nz - 0.5)), 0, 1)
shade = np.clip(0.85 + 0.25 * (nz - 0.5), 0, 1)
for c in range(3): ring[:, :, c] = (shade * 255).astype(np.uint8)
ring[:, :, 3] = (a * 255).astype(np.uint8)
Image.fromarray(ring, 'RGBA').save(out + '/ring.png')

# crack: 32 wide (across) x 64 long (along, tiles): a dark jagged line, a lighter broken edge each side
W, H = 32, 64
crack = np.zeros((H, W, 4), np.float64)
t = np.arange(H)
wob = 3.0 * np.sin(t / H * 2 * np.pi * 2) + 2.0 * np.sin(t / H * 2 * np.pi * 5 + 1)
centre = W / 2 + wob
width = 3.2 + 1.2 * np.sin(t / H * 2 * np.pi * 3 + 0.5)
x = np.arange(W) + 0.5
d = np.abs(x[None, :] - centre[:, None]) / width[:, None]
core = np.clip(1.2 - d, 0, 1)
rim = np.clip(1 - np.abs(d - 1.6) / 0.6, 0, 1) * 0.35
nz = noise(64, 3, 9)[:H, :W]
alpha = np.clip(core + rim * (nz > 0.45), 0, 1)
col = np.where(core[:, :, None] > 0.05, np.array([22, 17, 12]), np.array([70, 58, 44]))
crack[:, :, :3] = col
crack[:, :, 3] = alpha * 255
Image.fromarray(crack.astype(np.uint8), 'RGBA').save(out + '/crack.png')
print('ok')
