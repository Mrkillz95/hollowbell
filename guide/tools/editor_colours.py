# (Part 12, the giant editor) Each block's average colour, from the game's own textures, for the editor's material
# sets (src/main/resources/assets/jj_giants/editor/colours.json: {"minecraft:stone": [r, g, b, emits], ...}).
# The game jar is read from the Gradle cache. usage: python tools/editor_colours.py [client jar]
import glob, io, json, os, sys, zipfile
from PIL import Image

jar = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser('~/.gradle/caches/fabric-loom/1.21.1/minecraft-client.jar')
z = zipfile.ZipFile(jar)
names = set(z.namelist())

def js(p):
    if p not in names: return None
    try:
        return json.loads(z.read(p).decode('utf-8-sig'))
    except ValueError:
        return None

def model_textures(ref, depth=0):
    if depth > 8 or ref is None: return {}
    ref = ref.split(':')[-1]
    m = js('assets/minecraft/models/' + ref + '.json')
    if m is None: return {}
    t = dict(model_textures(m.get('parent'), depth + 1))
    t.update(m.get('textures', {}))
    return t

def resolve(t, k, d=0):
    v = t.get(k)
    while isinstance(v, str) and v.startswith('#') and d < 8:
        v = t.get(v[1:]); d += 1
    return v

def avg(tex):
    p = 'assets/minecraft/textures/' + tex.split(':')[-1] + '.png'
    if p not in names: return None
    im = Image.open(io.BytesIO(z.read(p))).convert('RGBA')
    w, h = im.size
    im = im.crop((0, 0, w, min(h, w)))  # animated: the first frame
    px = list(im.getdata())
    r = g = b = n = 0
    for (pr, pg, pb, pa) in px:
        if pa < 40: continue
        r += pr; g += pg; b += pb; n += 1
    if n == 0: return None
    return [round(r / n), round(g / n), round(b / n)]

# tinted by the biome (leaves, grass...): the game's default foliage colour on top
TINT = {'oak_leaves': (0x48, 0xB5, 0x18), 'jungle_leaves': (0x48, 0xB5, 0x18), 'acacia_leaves': (0x48, 0xB5, 0x18),
        'dark_oak_leaves': (0x48, 0xB5, 0x18), 'mangrove_leaves': (0x92, 0xC8, 0x10), 'spruce_leaves': (0x61, 0x99, 0x61),
        'birch_leaves': (0x80, 0xA7, 0x55), 'vine': (0x48, 0xB5, 0x18)}
EMITS = ('lantern', 'glowstone', 'sea_lantern', 'shroomlight', 'froglight', 'magma_block', 'redstone_lamp',
         'jack_o_lantern', 'torch', 'end_rod', 'beacon', 'crying_obsidian', 'sculk_catalyst', 'respawn_anchor',
         'campfire', 'candle', 'amethyst_cluster', 'glow_lichen', 'sea_pickle', 'conduit', 'ochre', 'verdant',
         'pearlescent')
out = {}
for p in sorted(names):
    if not p.startswith('assets/minecraft/blockstates/'): continue
    bid = p[len('assets/minecraft/blockstates/'):-5]
    st = js(p)
    if st is None: continue
    ref = None
    if 'variants' in st:
        v = next(iter(st['variants'].values()))
        ref = (v[0] if isinstance(v, list) else v).get('model')
    elif 'multipart' in st:
        a = st['multipart'][0]['apply']
        ref = (a[0] if isinstance(a, list) else a).get('model')
    t = model_textures(ref)
    keys = [k for k in t if k != 'particle'] or ['particle']
    cols = []
    for k in keys:
        tex = resolve(t, k)
        if isinstance(tex, str):
            c = avg(tex)
            if c: cols.append(c)
    if not cols: continue
    c = [round(sum(x[i] for x in cols) / len(cols)) for i in range(3)]
    if bid in TINT: c = [round(c[i] * TINT[bid][i] / 255) for i in range(3)]
    out['minecraft:' + bid] = c + [1 if any(e in bid for e in EMITS) else 0]
dst = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   'src/main/resources/assets/jj_giants/editor/colours.json')
with open(dst, 'w') as f:
    json.dump(out, f, separators=(',', ':'), sort_keys=True)
print(len(out), 'blocks ->', dst)
