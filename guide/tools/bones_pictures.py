# writes guide/tools/bones_autotest.txt: every giant's remains (each corpse stage and the skeleton) at size 1 and
# 0.25, from a 3/4 view and from above, beside a picture of him alive
import math, sys, json, os
ONLY = os.environ.get('VARIANTS', '').split(',') if os.environ.get('VARIANTS') else None
ALIVE = {'pitchgut': (185, 265), 'furrowmaw': (45, 600), 'cerberus': (200, 280), 'hollowbell': (200, 200),
         'lanternwillow': (200, 200), 'wreckback': (208, 323), 'swarmforge': (152, 225), 'gloamwick': (200, 277)}
EXT = json.load(open(__file__.replace('bones_pictures.py', 'bones_extents.json')))
ks = sys.argv[1].split(',') if len(sys.argv) > 1 else ['pitchgut','furrowmaw','cerberus','hollowbell','lanternwillow','wreckback','swarmforge','gloamwick']
sizes = [float(s) for s in sys.argv[2].split(',')] if len(sys.argv) > 2 else [1.0, 0.25]
G = -60
L = ['# Remains pictures (bones pass): set GUIDE_SCRIPT=<this file>; withslot.ps1 <guide> runAutotest -Pgiants',
     'wait 60', 'cmd time set 6000', 'cmd gamerule doDaylightCycle false', 'cmd weather clear',
     'cmd gamerule doMobSpawning false', 'cmd gamerule randomTickSpeed 0', 'cmd giants admin set all corpseDays 30',
     'gui off', 'cmd gamemode spectator', 'wait 20']
def look(cx, cy, cz, px, py, pz):
    dx, dy, dz = cx - px, cy - py, cz - pz
    yaw = math.degrees(math.atan2(-dx, dz)); pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
    return 'cmd tp @s %.1f %.1f %.1f %.1f %.1f' % (px, py, pz, yaw, pitch)
X = int(os.environ.get("XSTART", "0"))
for k in ks:
    for s in sizes:
        tag = '%s_%s' % (k, '1' if s == 1 else '025')
        ex = EXT[k]
        R = max(max(ex[2], ex[3]), ex[4] * 0.8) * s + 8   # how far round he lies (and how tall)
        OX, OZ = ex[0] * s, ex[1] * s   # the middle of his remains from where he fell
        H = ex[4] * s
        variants = [('skeleton', 'skeleton'), ('corpse0', 'corpse 0'), ('corpse1', 'corpse 1'), ('corpse2', 'corpse 2'), ('corpse3', 'corpse 3')]
        for vi, (vn, arg) in enumerate(variants):
            if ONLY is not None and vn not in ONLY: continue
            X += int(2 * R + 260)
            Z = 0
            cy = G + H * 0.3
            L.append('cmd tp @s %d %d %d 0 0' % (X, G, Z))
            L.append('wait 30')
            L.append('cmd giants admin systems remains %s %s %s' % (k, s, arg))
            L.append('wait 1')
            L.append(look(X + OX, cy, Z + OZ, X + OX + R * 1.0, G + R * 0.6 + H * 0.3, Z + OZ + R * 0.55))
            L.append('wait %d' % (int(80 + 700 * s * s) + 60))
            L.append('shot bones_%s_%s' % (tag, vn))
            if vn in ('skeleton', 'corpse0'):
                L.append(look(X + OX, cy, Z + OZ, X + OX - R * 0.9, G + R * 0.55 + H * 0.3, Z + OZ - R * 0.75))
                L.append('wait 40')
                L.append('shot bones_%s_%s_b' % (tag, vn))
                RF = max(ex[2], ex[3]) * s + 8
                L.append('cmd tp @s %.1f %.1f %.1f 0 90' % (X + OX, G + max(H * 0.3 + RF * 1.2, H + RF * 0.55), Z + OZ))
                L.append('wait 50')
                L.append('shot bones_%s_%s_top' % (tag, vn))
        if s < 1:
            # him alive, for comparing
            X += int(2 * R + 260)
            L.append('cmd tp @s %d %d %d 0 0' % (X, G, 0))
            L.append('wait 30')
            L.append('cmd gamemode creative')
            L.append('cmd %s summon calm %s' % (k, s))
            L.append('wait 4')
            L.append('cmd giants freeze')
            L.append('wait 30')
            L.append('cmd gamemode spectator')
            ah, al = ALIVE[k][0] * s, ALIVE[k][1] * s
            d = max(ah, al * 0.5) * 1.6 + 12
            L.append(look(X, G + ah * 0.55, 0, X + d * 0.8, G + ah * 0.6 + d * 0.2, d * 0.6))
            L.append('wait 80')
            L.append('shot bones_%s_alive' % tag)
            L.append('cmd giants unfreeze')
            L.append('cmd %s kill' % k)
            L.append('wait 20')
L.append('quit')
open(sys.argv[3] if len(sys.argv) > 3 else 'bones_autotest.txt', 'w').write('\n'.join(L) + '\n')
print(len(L))
