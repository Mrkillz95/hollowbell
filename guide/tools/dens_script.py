# Writes tools/dens_autotest.txt: pictures of every den at a giant's size 1 and 0.25 (a 3/4 view, one from above,
# one with him standing in it) and of each arena, on a deep flat world (grass at y 36).
#   python tools/dens_script.py [kinds...] [--sizes 1,0.25] [--arenas] > tools/dens_autotest.txt
#   set GUIDE_SCRIPT=<that file>; withslot.ps1 <guide> runAutotest -Pgiants   (pictures in build/autotest/screenshots)
import math, sys

SIZE = {"pitchgut": (185, 265), "furrowmaw": (45, 600), "cerberus": (200, 280), "hollowbell": (200, 200),
        "lanternwillow": (200, 200), "wreckback": (208, 323), "swarmforge": (152, 225), "gloamwick": (200, 277)}
args = [a for a in sys.argv[1:] if not a.startswith("--")]
kinds = args or list(SIZE)
sizes = [1.0, 0.25]
for a in sys.argv[1:]:
    if a.startswith("--sizes="): sizes = [float(s) for s in a[8:].split(",")]
arenas = "--arenas" in sys.argv or not args
noden = "--nodens" in sys.argv
giant = "--nogiant" not in sys.argv

G = 36  # the ground
out = ["# made by tools/dens_script.py", "deepflat", "wait 60", "guiscale 2", "fov 85", "render 20", "noclouds", "gui off",
       "adminintro seen", "cmd time set 6000", "cmd gamerule doDaylightCycle false", "cmd weather clear",
       "cmd gamerule doMobSpawning false", "cmd gamemode spectator", "wait 10"]
x = 0


def shots(name, X, reach, extra=0):
    d = reach * 0.86 + 6
    out.append(f"cmd tp @s {X + d * 0.7071:.1f} {G + 1 + d * 0.78 + extra:.1f} {d * 0.7071:.1f} 135 36")
    out.append("wait 400")
    out.append(f"shot {name}_34")
    hgt = reach * 1.15 + 8 + extra
    out.append(f"cmd tp @s {X + 0.5} {G + 1 + hgt:.1f} 0.5 180 90")
    out.append("wait 300")
    out.append(f"shot {name}_top")


if not noden:
    for k in kinds:
        for s in sizes:
            x += 700
            r = max(10, min(160, round(max(SIZE[k]) * s * 0.5 * 1.15)))
            reach = r * 1.1 + 4
            name = f"den_{k}_{str(s).replace('.', '')}"
            out += [f"# {k} at {s}: r {r}", f"cmd tp @s {x + 0.5} {G + 1} 0.5 0 10", "wait 60",
                    f"cmd giants admin build den {k} {s}", f"wait {int(200 + r * r * 0.035)}"]
            shots(name, x, reach, SIZE[k][0] * s * 0.15 if k == "hollowbell" else 0)
            if giant:
                d = max(reach * 1.05, (SIZE[k][0] if k == 'furrowmaw' else max(SIZE[k])) * s * 0.9) + 6
                if k == 'hollowbell': d = SIZE[k][0] * s * 1.25 + 10
                hy = max(d * 0.5, SIZE[k][0] * s * 0.9)
                pitch = math.degrees(math.atan2(hy - SIZE[k][0] * s * 0.25, d))
                out += [f"cmd tp @s {x + 0.5} {G + 1} 0.5 0 0", "wait 10", f"cmd {k} summon calm {s}", "wait 60",
                        f"cmd giants admin settle {k} here", "wait 40", "cmd giants freeze", "wait 10"]
                out.append(f"cmd tp @s {x + d * 0.7071:.1f} {G + 1 + hy:.1f} {d * 0.7071:.1f} 135 {pitch:.0f}")
                out += ["wait 200", f"shot {name}_giant", "cmd giants remove", "wait 20",
                        "cmd kill @e[type=!minecraft:player]", "wait 20"]
if arenas:
    for size, r in (("small", 40), ("medium", 64), ("large", 96)):
        x += 700
        reach = r + 2 + (6 if r >= 90 else 5 if r >= 60 else 4) * 2 + 6
        out += [f"cmd tp @s {x + 0.5} {G + 1} 0.5 0 10", "wait 60",
                f"cmd giants admin build arena {size}",
                f"wait {int(200 + r * r * 0.03)}"]
        shots(f"arena_{size}", x, reach)
        # from the stands
        out.append(f"cmd tp @s {x + 0.5} {G + 12} {r + 10:.1f} 180 12")
        out += ["wait 60", f"shot arena_{size}_stands"]
out.append("quit")
print("\n".join(out))
