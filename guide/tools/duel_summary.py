"""Sums up the duel harness's CSVs (build/duels/<out>.csv and <out>-moves.csv) as Markdown tables.

usage: python -I tools/duel_summary.py build/duels/base.csv [build/duels/tuned.csv ...]

Per pair: wins each way, mean HP left, damage per second each way, and the "edge": how much faster the stronger one
drains the other's health share (1.00 = even). Then a win/edge matrix, a ranking, and per pairing the moves that
landed and the ones used a lot that never landed.
"""
import csv, sys, os
from collections import defaultdict

ORDER = ['P', 'F', 'C', 'H', 'W', 'WB', 'S', 'G']
NAME = {'P': 'Pitchgut', 'F': 'Furrowmaw', 'C': 'Cerberus', 'H': 'Hollowbell', 'W': 'Willow', 'WB': 'Wreckback',
        'S': 'Swarmforge', 'G': 'Gloamwick'}


def load(path):
    rows = list(csv.DictReader(open(path, encoding='utf-8')))
    mp = path[:-4] + '-moves.csv'
    moves = list(csv.DictReader(open(mp, encoding='utf-8'))) if os.path.exists(mp) else []
    return rows, moves


def pairs(rows):
    out = defaultdict(list)
    for r in rows:
        x, y = sorted((r['a'], r['b']), key=ORDER.index)
        out[(x, y)].append(r)
    return out


def side(r, who):
    """(damage dealt by who, damage taken by who, max hp of who, hp share left of who)"""
    if r['a'] == who:
        return float(r['dmg_a_to_b']), float(r['dmg_b_to_a']), float(r['max_a']), float(r['hp_a_left'])
    return float(r['dmg_b_to_a']), float(r['dmg_a_to_b']), float(r['max_b']), float(r['hp_b_left'])


def pair_stats(x, y, rs):
    n = len(rs)
    wx = sum(r['winner'] == x for r in rs)
    wy = sum(r['winner'] == y for r in rs)
    secs = sum(float(r['seconds']) for r in rs)
    dx = sum(side(r, x)[0] for r in rs)
    dy = sum(side(r, y)[0] for r in rs)
    mx, my = side(rs[0], x)[2], side(rs[0], y)[2]
    # share of the other's max HP drained per minute, each way
    rx = dx / my / secs * 60 * 100 if secs else 0
    ry = dy / mx / secs * 60 * 100 if secs else 0
    lo, hi = min(rx, ry), max(rx, ry)
    edge = hi / lo if lo > 0 else float('inf')
    stronger = x if rx > ry else y
    hpx = sum(side(r, x)[3] for r in rs) / n
    hpy = sum(side(r, y)[3] for r in rs) / n
    yields = sum(r['how'] in ('yield', 'death') for r in rs)
    return dict(n=n, wx=wx, wy=wy, draws=n - wx - wy, mins=secs / n / 60, dpsx=dx / secs, dpsy=dy / secs,
                rx=rx, ry=ry, edge=edge, stronger=stronger, hpx=hpx, hpy=hpy, yields=yields,
                restarts=sum(int(r['restarts']) for r in rs) / n,
                fhx=[float(r['first_hit_on_b_s'] if r['a'] == x else r['first_hit_on_a_s']) for r in rs],
                fhy=[float(r['first_hit_on_a_s'] if r['a'] == x else r['first_hit_on_b_s']) for r in rs])


def fmt_edge(e):
    return 'inf' if e == float('inf') else f'{e:.2f}'


def main(paths):
    for path in paths:
        rows, moves = load(path)
        ps = pairs(rows)
        print(f'## {os.path.basename(path)}: {len(rows)} duels\n')
        print('| Pair | n | Wins | Draws | Ends by yield | HP left | dmg/s each way | % of other\'s HP per min | Edge | Restarts | First landed (s) |')
        print('|---|---|---|---|---|---|---|---|---|---|---|')
        stats = {}
        for (x, y), rs in sorted(ps.items(), key=lambda k: (ORDER.index(k[0][0]), ORDER.index(k[0][1]))):
            s = pair_stats(x, y, rs)
            stats[(x, y)] = s
            fx = '/'.join('-' if v < 0 else f'{v:.0f}' for v in s['fhx'])
            fy = '/'.join('-' if v < 0 else f'{v:.0f}' for v in s['fhy'])
            print(f"| {x}-{y} | {s['n']} | {x} {s['wx']} - {s['wy']} {y} | {s['draws']} | {s['yields']} | "
                  f"{s['hpx']*100:.0f}% / {s['hpy']*100:.0f}% | {s['dpsx']:.1f} / {s['dpsy']:.1f} | "
                  f"{s['rx']:.1f} / {s['ry']:.1f} | {fmt_edge(s['edge'])} ({s['stronger']}) | {s['restarts']:.1f} | "
                  f"{x}: {fx}; {y}: {fy} |")
        print()
        # matrix: row giant's wins-losses and edge against column giant
        print('| | ' + ' | '.join(ORDER) + ' |')
        print('|---' * (len(ORDER) + 1) + '|')
        for a in ORDER:
            cells = []
            for b in ORDER:
                if a == b:
                    cells.append('-')
                    continue
                k = (a, b) if ORDER.index(a) < ORDER.index(b) else (b, a)
                if k not in stats:
                    cells.append('')
                    continue
                s = stats[k]
                w, l = (s['wx'], s['wy']) if k[0] == a else (s['wy'], s['wx'])
                ra, rb = (s['rx'], s['ry']) if k[0] == a else (s['ry'], s['rx'])
                r = ra / rb if rb > 0 else float('inf')
                cells.append(f'{w}-{l} ({fmt_edge(r)})')
            print(f'| **{a}** | ' + ' | '.join(cells) + ' |')
        print()
        # ranking: duels won, and the mean of log edge (how much faster it drains the other than the other drains it)
        import math
        rank = []
        for g in ORDER:
            won = lost = dr = 0
            logs = []
            for (x, y), s in stats.items():
                if g not in (x, y):
                    continue
                w, l = (s['wx'], s['wy']) if g == x else (s['wy'], s['wx'])
                won += w
                lost += l
                dr += s['draws']
                ra, rb = (s['rx'], s['ry']) if g == x else (s['ry'], s['rx'])
                if ra > 0 and rb > 0:
                    logs.append(math.log(ra / rb))
                elif ra > 0:
                    logs.append(math.log(10))
                elif rb > 0:
                    logs.append(-math.log(10))
            pw = sum(1 for (x, y), s in stats.items() if g in (x, y)
                     and ((s['rx'] > s['ry']) if g == x else (s['ry'] > s['rx'])))
            m = math.exp(sum(logs) / len(logs)) if logs else 1
            rank.append((m, g, won, lost, dr, pw, len(logs)))
        rank.sort(reverse=True)
        print('| Rank | Giant | Duels won-lost-drawn | Pairings ahead (of 7) | Mean edge |')
        print('|---|---|---|---|---|')
        for i, (m, g, won, lost, dr, pw, n) in enumerate(rank, 1):
            print(f'| {i} | {NAME[g]} | {won}-{lost}-{dr} | {pw} | {m:.2f} |')
        print()
        lop = sum(1 for s in stats.values() if s['edge'] > 1.5)
        print(f'Pairings with an edge over 1.5: {lop} of {len(stats)}; over 2: '
              f'{sum(1 for s in stats.values() if s["edge"] > 2)}.\n')

        if rows and 'yielder' in rows[0]:
            escapes(rows)
        if moves:
            agg = defaultdict(lambda: [0, 0, 0.0])
            for m in moves:
                k = (m['attacker'], m['defender'], m['move'])
                agg[k][0] += int(m['started'])
                agg[k][1] += int(float(m['hits']))
                agg[k][2] += float(m['damage'])
            print('### Moves by attacker and target (used / ticks landed / damage, summed over runs)\n')
            for a in ORDER:
                for b in ORDER:
                    ks = [k for k in agg if k[0] == a and k[1] == b]
                    if not ks:
                        continue
                    tot = sum(agg[k][2] for k in ks) or 1
                    land = sorted((k for k in ks if agg[k][2] > 0), key=lambda k: -agg[k][2])
                    miss = sorted((k for k in ks if agg[k][2] == 0 and agg[k][0] >= 3), key=lambda k: -agg[k][0])
                    s1 = ', '.join(f'{k[2]} {agg[k][2]/tot*100:.0f}%' for k in land[:6])
                    s2 = ', '.join(f'{k[2]} x{agg[k][0]}' for k in miss[:6])
                    print(f'- {a} on {b}: {s1 or "nothing"}' + (f'; never landed: {s2}' if s2 else ''))
            print()


def escapes(rows):
    # the backing-down check (-Pduel_escape=true): per giant put low, what the minute after backing down looked like
    print('### Backing down: the minute after the loser yields\n')
    print('| Giant put low | Duels | Backed down | Mean s to back down | Moved off (blocks) | Rose (blocks) | '
          'Gap at yield -> +60 s | Still took after yield | Winner still after it | Stayed put (<40 blocks) |')
    print('|---|---|---|---|---|---|---|---|---|---|')
    by = defaultdict(list)
    for r in rows:
        if r.get('low', '-') != '-':
            by[r['low']].append(r)
    for g in ORDER:
        rs = by.get(g, [])
        if not rs:
            continue
        y = [r for r in rs if r['yielder'] == g]
        def m(k, src=y):
            v = [float(r[k]) for r in src if float(r[k]) >= 0]
            return sum(v) / len(v) if v else float('nan')
        stuck = sum(float(r['loser_moved']) < 40 for r in y)
        others = sorted(set((r['b'] if r['a'] == g else r['a']) for r in y if float(r['loser_moved']) < 40))
        print(f"| {NAME[g]} | {len(rs)} | {len(y)} | {m('drop_to_yield_s'):.0f} | {m('loser_moved'):.0f} | "
              f"{m('loser_rise'):.0f} | {m('dist_at_yield'):.0f} -> {m('dist_60s'):.0f} | {m('dmg_after_yield'):.0f} | "
              f"{m('winner_chase_pct'):.0f}% | {stuck}" + (f" (vs {', '.join(others)})" if others else '') + ' |')
    print()


if __name__ == '__main__':
    main(sys.argv[1:])
