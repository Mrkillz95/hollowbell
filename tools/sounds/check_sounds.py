#!/usr/bin/env python3
"""Checks the sounds made by make_music.py by the numbers (nobody can listen to them here), and draws a spectrogram
of each into pictures/sounds/ to look at.

  peak      at most -1 dBFS (no clipping)
  dc        no offset
  seam      for the loops: the last sample runs into the first like any two samples in the middle (level and slope)
  bands     there is something in the lows, the mids and the highs (share of the energy in each)
  size      all of the mod's .ogg files together under 3 MB

    python3 tools/sounds/check_sounds.py
"""
import glob, os, sys
import numpy as np
import soundfile as sf
from scipy import signal
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), '..', '..')
SND = os.path.join(ROOT, 'src/main/resources/assets/hollowbell/sounds')
PIC = os.path.join(ROOT, 'pictures/sounds')
LOOPS = {'music/fight', 'music/ground', 'ambient/ground'}


def spectrogram(name, x, sr):
    m = x.mean(axis=1) if x.ndim > 1 else x
    f, t, S = signal.spectrogram(m, sr, nperseg=2048, noverlap=1024)
    S = 10 * np.log10(S + 1e-12)
    keep = f <= 12000
    S = S[keep]
    top = S.max()
    S = np.clip((S - (top - 80)) / 80, 0, 1)
    # frequency on a log scale, 30 Hz to 12 kHz, 360 rows; time across, 900 columns
    rows, cols = 360, 900
    ff = f[keep]
    logf = np.log10(np.maximum(ff, 1))
    want = np.linspace(np.log10(30), np.log10(12000), rows)
    img = np.zeros((rows, S.shape[1]))
    for i, lf in enumerate(want):
        img[rows - 1 - i] = S[np.argmin(np.abs(logf - lf))]
    img = np.array(Image.fromarray((img * 255).astype(np.uint8)).resize((cols, rows))).astype(float)
    rgb = np.stack([np.clip(img * 1.2, 0, 255), np.clip((img - 60) * 1.3, 0, 255), np.clip((img - 150) * 2.5, 0, 255)], -1).astype(np.uint8)
    im = Image.fromarray(rgb)
    d = ImageDraw.Draw(im)
    for hz in (100, 1000, 10000):
        y = rows - 1 - int((np.log10(hz) - np.log10(30)) / (np.log10(12000) - np.log10(30)) * (rows - 1))
        d.line([(0, y), (12, y)], fill=(255, 255, 255))
        d.text((15, y - 6), f'{hz} Hz', fill=(255, 255, 255))
    d.text((6, 6), f'{name}  {len(m) / sr:.1f} s', fill=(255, 255, 0))
    os.makedirs(PIC, exist_ok=True)
    im.save(os.path.join(PIC, name.replace('/', '_') + '.png'))


def main():
    bad = []
    total = 0
    for path in sorted(glob.glob(os.path.join(SND, '**', '*.ogg'), recursive=True)):
        total += os.path.getsize(path)
        name = os.path.relpath(path, SND)[:-4]
        x, sr = sf.read(path, always_2d=True)
        mono = x.mean(axis=1)
        pk = 20 * np.log10(np.max(np.abs(x)) + 1e-12)
        dc = float(np.abs(x.mean(axis=0)).max())
        spec = np.abs(np.fft.rfft(mono)) ** 2
        f = np.fft.rfftfreq(len(mono), 1 / sr)
        e = spec.sum() + 1e-20
        lows, mids, highs = spec[f < 250].sum() / e, spec[(f >= 250) & (f < 2500)].sum() / e, spec[f >= 2500].sum() / e
        line = f'{name:18s} {len(x) / sr:6.1f} s  {x.shape[1]}ch  peak {pk:6.2f} dB  dc {dc:.5f}  lows {lows:.2f} mids {mids:.2f} highs {highs:.3f}'
        if pk > -1.0: bad.append(f'{name}: peak {pk:.2f} dBFS')
        if dc > 0.002: bad.append(f'{name}: DC offset {dc:.4f}')
        if name in LOOPS:
            d = np.abs(np.diff(x, axis=0))
            limit = np.percentile(d, 99.9, axis=0)
            jump = np.abs(x[0] - x[-1])
            slope = np.abs((x[1] - x[0]) - (x[-1] - x[-2]))
            slope_limit = np.percentile(np.abs(np.diff(x, 2, axis=0)), 99.9, axis=0)
            line += f'  seam {jump.max():.4f} (99.9% step {limit.max():.4f}) slope {slope.max():.4f}'
            if np.any(jump > limit) or np.any(slope > slope_limit): bad.append(f'{name}: the loop seam jumps')
            if mids < 0.1 or lows < 0.05: bad.append(f'{name}: the lows or mids are empty')
        if name.startswith('music') and x.shape[1] != 2: bad.append(f'{name}: music should be stereo')
        if not name.startswith(('music', 'ambient/ground')) and x.shape[1] != 1: bad.append(f'{name}: placed sounds must be mono')
        print(line)
        spectrogram(name, x, sr)
    print(f'all .ogg files: {total / 1024:.0f} KB')
    if total > 3 * 1024 * 1024: bad.append('over 3 MB')
    if bad:
        print('PROBLEMS:\n  ' + '\n  '.join(bad)); sys.exit(1)
    print('all good')


if __name__ == '__main__':
    main()
