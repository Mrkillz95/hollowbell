#!/usr/bin/env python3
"""Checks the sounds made by make_music.py by the numbers, and draws each one's waveform and spectrogram into
pictures/sounds/ to look at.

  peak      at most -1 dBFS (no clipping)
  dc        no offset
  seam      for the loops: the last sample runs into the first like any two samples in the middle (level and slope)
  bands     there is something in the lows, the mids and the highs (share of the energy in each)
  loudness  the fight theme about -14 LUFS (as loud as the game's own music); the ground's music calmer
  ground    the ground's sounds: the loop at least 60 s and quiet, no hiss over 8 kHz, no sudden loud moments,
            five or more of each now-and-then sound
  size      all of the mod's .ogg files together under 4 MB

    python3 tools/sounds/check_sounds.py
"""
import glob
import os
import sys
import numpy as np
import soundfile as sf
from scipy import signal

ROOT = os.path.join(os.path.dirname(__file__), '..', '..')
SND = os.path.join(ROOT, 'src/main/resources/assets/hollowbell/sounds')
PIC = os.path.join(ROOT, 'pictures/sounds')
LOOPS = {'music/fight', 'music/ground', 'ambient/ground'}


def lufs(x, sr):
    """integrated loudness (ITU BS.1770, gated), and the momentary loudness of each 400 ms block"""
    if x.ndim == 1:
        x = x[:, None]
    # K-weighting: a high shelf, then a high pass (the standard's analogue values, made for any rate)
    f0, G, Q = 1681.974450955533, 3.999843853973347, 0.7071752369554196
    K = np.tan(np.pi * f0 / sr)
    Vh = 10 ** (G / 20)
    Vb = Vh ** 0.4996667741545416
    a0 = 1 + K / Q + K * K
    b1 = [(Vh + Vb * K / Q + K * K) / a0, 2 * (K * K - Vh) / a0, (Vh - Vb * K / Q + K * K) / a0]
    a1 = [1, 2 * (K * K - 1) / a0, (1 - K / Q + K * K) / a0]
    f0, Q = 38.13547087602444, 0.5003270373238773
    K = np.tan(np.pi * f0 / sr)
    d = 1 + K / Q + K * K
    a2 = [1, 2 * (K * K - 1) / d, (1 - K / Q + K * K) / d]
    y = signal.lfilter([1, -2, 1], a2, signal.lfilter(b1, a1, x, axis=0), axis=0)
    blk, hop = int(0.4 * sr), int(0.1 * sr)
    c = np.cumsum(np.concatenate([np.zeros((1, y.shape[1])), y ** 2]), axis=0)
    starts = np.arange(0, max(1, len(y) - blk), hop)
    ms = ((c[np.minimum(starts + blk, len(y))] - c[starts]) / blk).sum(axis=1)
    mom = -0.691 + 10 * np.log10(ms + 1e-12)
    g = ms[mom > -70]
    if len(g) == 0:
        return -70.0, mom
    rel = -0.691 + 10 * np.log10(g.mean()) - 10
    g2 = ms[(mom > -70) & (mom > rel)]
    return -0.691 + 10 * np.log10(g2.mean()), mom


def picture(name, x, sr):
    from PIL import Image, ImageDraw
    m = x.mean(axis=1) if x.ndim > 1 else x
    f, t, S = signal.spectrogram(m, sr, nperseg=2048, noverlap=1024)
    S = 10 * np.log10(S + 1e-12)
    keep = f <= 12000
    S = S[keep]
    top = S.max()
    S = np.clip((S - (top - 80)) / 80, 0, 1)
    # frequency on a log scale, 30 Hz to 12 kHz, 360 rows; time across, 900 columns
    rows, cols, wave = 360, 900, 120
    ff = f[keep]
    logf = np.log10(np.maximum(ff, 1))
    want = np.linspace(np.log10(30), np.log10(12000), rows)
    img = np.zeros((rows, S.shape[1]))
    for i, lf in enumerate(want):
        img[rows - 1 - i] = S[np.argmin(np.abs(logf - lf))]
    img = np.array(Image.fromarray((img * 255).astype(np.uint8)).resize((cols, rows))).astype(float)
    rgb = np.stack([np.clip(img * 1.2, 0, 255), np.clip((img - 60) * 1.3, 0, 255),
                    np.clip((img - 150) * 2.5, 0, 255)], -1).astype(np.uint8)
    im = Image.new('RGB', (cols, rows + wave), (16, 16, 24))
    im.paste(Image.fromarray(rgb), (0, wave))
    d = ImageDraw.Draw(im)
    # the waveform over it: the peak of each column, and the loudness under it
    edges = np.linspace(0, len(m), cols + 1).astype(int)
    mid = wave // 2
    for c in range(cols):
        seg = x[edges[c]:max(edges[c] + 1, edges[c + 1])]
        pk = np.abs(seg).max()
        rms = np.sqrt((seg ** 2).mean())
        d.line([(c, mid - pk * mid), (c, mid + pk * mid)], fill=(90, 140, 220))
        d.line([(c, mid - rms * mid), (c, mid + rms * mid)], fill=(170, 210, 255))
    for hz in (100, 1000, 10000):
        y = wave + rows - 1 - int((np.log10(hz) - np.log10(30)) / (np.log10(12000) - np.log10(30)) * (rows - 1))
        d.line([(0, y), (12, y)], fill=(255, 255, 255))
        d.text((15, y - 6), f'{hz} Hz', fill=(255, 255, 255))
    L, _ = lufs(x, sr)
    d.text((6, 6), f'{name}  {len(m) / sr:.1f} s  {L:.1f} LUFS', fill=(255, 255, 0))
    os.makedirs(PIC, exist_ok=True)
    im.save(os.path.join(PIC, name.replace('/', '_') + '.png'))


def main(pictures=True):
    bad = []
    total = 0
    moods, chimes = 0, 0
    for path in sorted(glob.glob(os.path.join(SND, '**', '*.ogg'), recursive=True)):
        total += os.path.getsize(path)
        name = os.path.relpath(path, SND)[:-4].replace('\\', '/')
        x, sr = sf.read(path, always_2d=True)
        mono = x.mean(axis=1)
        pk = 20 * np.log10(np.max(np.abs(x)) + 1e-12)
        dc = float(np.abs(x.mean(axis=0)).max())
        spec = np.abs(np.fft.rfft(mono)) ** 2
        f = np.fft.rfftfreq(len(mono), 1 / sr)
        e = spec.sum() + 1e-20
        lows, mids, highs = spec[f < 250].sum() / e, spec[(f >= 250) & (f < 2500)].sum() / e, spec[f >= 2500].sum() / e
        over8k = spec[f >= 8000].sum() / e
        L, mom = lufs(x, sr)
        jump_db = float(mom.max() - L)
        line = (f'{name:18s} {len(x) / sr:6.1f} s  {x.shape[1]}ch  peak {pk:6.2f} dB  {L:6.1f} LUFS  '
                f'max-mom {jump_db:4.1f}  lows {lows:.2f} mids {mids:.2f} highs {highs:.3f} >8k {over8k:.4f}')
        if pk > -1.0:
            bad.append(f'{name}: peak {pk:.2f} dBFS')
        if dc > 0.002:
            bad.append(f'{name}: DC offset {dc:.4f}')
        if name in LOOPS:
            d = np.abs(np.diff(x, axis=0))
            limit = np.percentile(d, 99.9, axis=0)
            jump = np.abs(x[0] - x[-1])
            slope = np.abs((x[1] - x[0]) - (x[-1] - x[-2]))
            # (the same step-to-step change, two samples apart, as anywhere in the middle)
            d1 = np.diff(x, axis=0)
            slope_limit = np.percentile(np.abs(d1[2:] - d1[:-2]), 99.9, axis=0)
            line += f'  seam {jump.max():.4f} (99.9% step {limit.max():.4f}) slope {slope.max():.4f}'
            if np.any(jump > limit) or np.any(slope > slope_limit):
                bad.append(f'{name}: the loop seam jumps')
            if mids < 0.1 or lows < 0.05:
                bad.append(f'{name}: the lows or mids are empty')
        if name == 'music/fight':
            if not -15.0 <= L <= -13.0:
                bad.append(f'{name}: {L:.1f} LUFS, should be about -14')
            if not 120 <= len(x) / sr <= 180:
                bad.append(f'{name}: should be 2 to 3 minutes')
        if name == 'music/ground' and not -21.0 <= L <= -16.0:
            bad.append(f'{name}: {L:.1f} LUFS, should be calmer than the fight')
        if name.startswith('ambient/'):
            if over8k > 0.002:
                bad.append(f'{name}: hiss over 8 kHz ({over8k:.4f} of it)')
            moods += name.startswith('ambient/mood')
            chimes += name.startswith('ambient/chime')
            if name == 'ambient/ground':
                if len(x) / sr < 60:
                    bad.append(f'{name}: the loop should be at least 60 s')
                if L > -23.0:
                    bad.append(f'{name}: {L:.1f} LUFS, too loud for a background loop')
                if jump_db > 4.0:
                    bad.append(f'{name}: a moment {jump_db:.1f} dB over the rest')
            elif L > -25.0:
                bad.append(f'{name}: {L:.1f} LUFS, too loud for a sound now and then')
        if name.startswith('music') and x.shape[1] != 2:
            bad.append(f'{name}: music should be stereo')
        if not name.startswith(('music', 'ambient/ground')) and x.shape[1] != 1:
            bad.append(f'{name}: placed sounds must be mono')
        print(line)
        if pictures:
            picture(name, x, sr)
    if moods < 5 or chimes < 5:
        bad.append(f'only {moods} mood and {chimes} chime sounds, five of each wanted')
    print(f'all .ogg files: {total / 1024:.0f} KB')
    if total > 4 * 1024 * 1024:
        bad.append('over 4 MB')
    if bad:
        print('PROBLEMS:\n  ' + '\n  '.join(bad))
        sys.exit(1)
    print('all good')


if __name__ == '__main__':
    main('--no-pictures' not in sys.argv)
