"""Synthesises the Duudle promo V2 soundtrack (original music + sound effects) with numpy.

    python promo/audio.py <out.wav>

120 BPM, so one beat = 15 video frames and one bar = 2 s. Everything here is generated
from scratch, so there is nothing to license. SFX cues are read from cues.json, which
index.html's timeline mirrors (times in seconds).
"""
import json
import os
import sys
import wave

import numpy as np
from scipy.signal import butter, lfilter

SR = 44100
BPM = 120.0
BEAT = 60.0 / BPM
BAR = BEAT * 4
HERE = os.path.dirname(os.path.abspath(__file__))
rng = np.random.default_rng(7)

cfg = json.load(open(os.path.join(HERE, "cues.json")))
TOTAL = float(cfg["duration"])
N = int(TOTAL * SR) + SR  # 1 s tail room, trimmed at the end
mixL = np.zeros(N)
mixR = np.zeros(N)
drumsL = np.zeros(N)
drumsR = np.zeros(N)
musicL = np.zeros(N)
musicR = np.zeros(N)
duck = np.ones(N)  # sidechain envelope written by the kick


def t_arr(dur):
    return np.arange(int(dur * SR)) / SR


def place(busL, busR, sig, at, gain=1.0, pan=0.0):
    i = int(at * SR)
    if i >= N or i < 0:
        return
    sig = sig[: N - i]
    l = gain * np.cos((pan + 1) * np.pi / 4)
    r = gain * np.sin((pan + 1) * np.pi / 4)
    busL[i:i + len(sig)] += sig * l
    busR[i:i + len(sig)] += sig * r


def filt(sig, kind, freq, order=2):
    b, a = butter(order, np.asarray(freq) / (SR / 2), btype=kind)
    return lfilter(b, a, sig)


def note(n):  # MIDI -> Hz
    return 440.0 * 2 ** ((n - 69) / 12)


# ---------- instruments ----------
def kick():
    t = t_arr(0.32)
    f = 46 + 110 * np.exp(-t * 32)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 11)
    click = filt(rng.standard_normal(len(t)), "high", 3000) * np.exp(-t * 300) * 0.25
    return np.tanh((body + click) * 1.6)


def clap():
    t = t_arr(0.28)
    n = filt(rng.standard_normal(len(t)), "band", [900, 3800])
    env = np.zeros(len(t))
    for d in (0.0, 0.011, 0.023):
        env += np.where(t >= d, np.exp(-(t - d) * 55), 0) * 0.5
    env += np.where(t >= 0.03, np.exp(-(t - 0.03) * 16), 0)
    return n * env * 0.55


def hat(open_=False):
    t = t_arr(0.22 if open_ else 0.06)
    n = filt(rng.standard_normal(len(t)), "high", 7500)
    return n * np.exp(-t * (16 if open_ else 75)) * 0.3


def shaker():
    t = t_arr(0.07)
    n = filt(rng.standard_normal(len(t)), "band", [4500, 9500])
    return n * np.sin(np.pi * np.clip(t / 0.07, 0, 1)) ** 2 * 0.16


def pluck(freq, dur=0.5, bright=1.0):
    """Marimba / kalimba-like mallet: a few partials with fast, staggered decays."""
    t = t_arr(dur)
    s = (np.sin(2 * np.pi * freq * t) * np.exp(-t * 7)
         + 0.45 * bright * np.sin(2 * np.pi * freq * 2.0 * t) * np.exp(-t * 12)
         + 0.28 * bright * np.sin(2 * np.pi * freq * 4.0 * t) * np.exp(-t * 22)
         + 0.10 * bright * np.sin(2 * np.pi * freq * 9.2 * t) * np.exp(-t * 60))
    atk = np.clip(t / 0.003, 0, 1)
    return s * atk * 0.5


def bass(freq, dur):
    t = t_arr(dur)
    s = np.sin(2 * np.pi * freq * t) + 0.35 * np.sin(2 * np.pi * freq * 2 * t) * np.exp(-t * 6)
    s += 0.12 * np.sign(np.sin(2 * np.pi * freq * t)) * np.exp(-t * 9)
    env = np.clip(t / 0.006, 0, 1) * np.exp(-t * 2.2) * np.clip((dur - t) / 0.03, 0, 1)
    return np.tanh(s * env * 1.4) * 0.55


def pad(freqs, dur):
    t = t_arr(dur)
    s = np.zeros(len(t))
    for f in freqs:
        for det in (-0.004, 0.0, 0.005):
            ph = rng.uniform(0, 2 * np.pi)
            s += np.sin(2 * np.pi * f * (1 + det) * t + ph)
            s += 0.3 * np.sin(2 * np.pi * f * 2 * (1 + det) * t + ph)
    s = filt(s, "low", 2400)
    env = np.clip(t / 0.35, 0, 1) * np.clip((dur - t) / 0.5, 0, 1)
    return s * env * 0.035


def lead(freq, dur):
    t = t_arr(dur)
    vib = 1 + 0.004 * np.sin(2 * np.pi * 5.5 * t) * np.clip(t / 0.15, 0, 1)
    ph = 2 * np.pi * np.cumsum(freq * vib) / SR
    s = np.sin(ph) + 0.3 * np.sin(2 * ph) + 0.12 * np.sin(3 * ph)
    env = np.clip(t / 0.012, 0, 1) * np.exp(-t * 3.0) * np.clip((dur - t) / 0.04, 0, 1)
    return s * env * 0.26


# ---------- sound effects ----------
def sfx_whoosh(dur=0.45, up=True):
    t = t_arr(dur)
    n = rng.standard_normal(len(t))
    out = np.zeros(len(t))
    # sweep a band-pass by cross-fading a few fixed bands
    bands = [(300, 900), (700, 2000), (1500, 4500), (3500, 9000)]
    if not up:
        bands = bands[::-1]
    for k, (lo, hi) in enumerate(bands):
        c = (k + 0.5) / len(bands)
        w = np.exp(-((t / dur - c) ** 2) / 0.035)
        out += filt(n, "band", [lo, hi]) * w
    env = np.sin(np.pi * t / dur) ** 1.5
    return out * env * 0.22


def sfx_pop(freq=520.0):
    t = t_arr(0.16)
    f = freq * (1 + 1.3 * np.exp(-t * 55))
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 26)
    return s * 0.5


def sfx_tick():
    t = t_arr(0.05)
    s = np.sin(2 * np.pi * 2100 * t) * np.exp(-t * 130)
    s += filt(rng.standard_normal(len(t)), "high", 5000) * np.exp(-t * 320) * 0.4
    return s * 0.32


def sfx_ding():
    t = t_arr(1.1)
    s = np.zeros(len(t))
    for f, a, d in ((1318.5, 1.0, 4.5), (1975.5, 0.5, 6.0), (2637.0, 0.28, 8.0)):
        s += a * np.sin(2 * np.pi * f * t) * np.exp(-t * d)
    return s * np.clip(t / 0.002, 0, 1) * 0.24


def sfx_ding2():
    """Two-note 'doodle arrived' chime."""
    out = np.zeros(int(1.4 * SR))
    for k, (f, at) in enumerate(((987.8, 0.0), (1480.0, 0.13))):
        t = t_arr(1.1)
        s = (np.sin(2 * np.pi * f * t) + 0.35 * np.sin(2 * np.pi * f * 2.01 * t)) * np.exp(-t * 5.5)
        i = int(at * SR)
        out[i:i + len(s)] += s * np.clip(t / 0.002, 0, 1) * 0.22
    return out


def sfx_scribble(dur=0.8):
    """Pencil on paper: band-passed noise with a wobbling stroke envelope."""
    t = t_arr(dur)
    n = filt(rng.standard_normal(len(t)), "band", [1800, 6500])
    wob = 0.55 + 0.45 * np.sin(2 * np.pi * (7 + 3 * np.sin(2 * np.pi * 1.3 * t)) * t) ** 2
    env = np.clip(t / 0.03, 0, 1) * np.clip((dur - t) / 0.06, 0, 1)
    return n * wob * env * 0.17


def sfx_impact():
    t = t_arr(0.9)
    f = 38 + 70 * np.exp(-t * 18)
    sub = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 4.5)
    air = filt(rng.standard_normal(len(t)), "low", 1400) * np.exp(-t * 9) * 0.5
    return np.tanh((sub + air) * 1.3) * 0.75


def sfx_riser(dur=2.0):
    t = t_arr(dur)
    n = filt(rng.standard_normal(len(t)), "high", 2500)
    tone = np.sin(2 * np.pi * np.cumsum(220 * 2 ** (t / dur * 2)) / SR) * 0.25
    env = (t / dur) ** 2.2
    return (n * 0.35 + tone) * env * 0.3


def sfx_send():
    """Rising two-blip 'sent' swoosh."""
    out = sfx_whoosh(0.35, up=True) * 0.9
    t = t_arr(0.3)
    f = 600 * 2 ** (t / 0.3 * 1.2)
    blip = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * t / 0.3) ** 2 * 0.2
    out[: len(blip)] += blip
    return out


def sfx_swipe():
    """Thumb flicking a feed."""
    t = t_arr(0.24)
    n = filt(rng.standard_normal(len(t)), "band", [500, 2600])
    return n * np.sin(np.pi * t / 0.24) ** 2 * 0.13


def sfx_flip():
    """A card snapping over."""
    t = t_arr(0.16)
    air = filt(rng.standard_normal(len(t)), "high", 2200) * np.exp(-((t - 0.045) ** 2) / 0.0006) * 0.32
    snap = filt(rng.standard_normal(len(t)), "band", [900, 5000]) * np.where(t > 0.085, np.exp(-(t - 0.085) * 120), 0) * 0.5
    thud = np.sin(2 * np.pi * 170 * t) * np.where(t > 0.085, np.exp(-(t - 0.085) * 45), 0) * 0.35
    return air + snap + thud


def sfx_zzz():
    """Three falling notes: snoozed."""
    out = np.zeros(int(0.6 * SR))
    for f, at, d in ((784.0, 0.0, 0.16), (587.3, 0.15, 0.2), (440.0, 0.32, 0.26)):
        t = t_arr(d)
        s = np.sin(2 * np.pi * f * t) * np.sin(np.pi * t / d) ** 1.5 * 0.2
        i = int(at * SR)
        out[i:i + len(s)] += s
    return out


def sfx_tock():
    t = t_arr(0.08)
    return (np.sin(2 * np.pi * 880 * t) + 0.5 * np.sin(2 * np.pi * 1320 * t)) * np.exp(-t * 70) * 0.3


SFX = {
    "swipe": sfx_swipe, "flip": sfx_flip, "zzz": sfx_zzz, "tock": sfx_tock,
    "whoosh": lambda: sfx_whoosh(0.45, True), "whoosh_down": lambda: sfx_whoosh(0.5, False),
    "whoosh_long": lambda: sfx_whoosh(0.8, True),
    "pop": sfx_pop, "pop_hi": lambda: sfx_pop(760.0), "pop_lo": lambda: sfx_pop(380.0),
    "tick": sfx_tick, "ding": sfx_ding, "ding2": sfx_ding2,
    "scribble": lambda: sfx_scribble(0.8), "scribble_short": lambda: sfx_scribble(0.4),
    "scribble_long": lambda: sfx_scribble(1.5),
    "impact": sfx_impact, "riser": lambda: sfx_riser(2.0), "riser_short": lambda: sfx_riser(1.0),
    "send": sfx_send,
}

# ---------- arrangement ----------
# D major, one chord per bar: D  A  Bm  G   (I V vi IV)
CHORDS = [
    (38, [62, 66, 69, 74]),   # D
    (33, [61, 64, 69, 73]),   # A
    (35, [62, 66, 71, 74]),   # Bm
    (31, [62, 67, 71, 74]),   # G
]
# 8th-note pluck pattern (index into chord tones, -1 = rest), slightly different per bar
PLUCK_PATS = [
    [0, 2, 1, 3, 2, 1, 3, -1],
    [0, 1, 2, 3, 1, 2, 3, 2],
    [0, 2, 3, 2, 1, 3, 2, -1],
    [0, 1, 3, 2, 3, 1, 2, 3],
]
# pentatonic lead phrases (MIDI, start beat, beats) for the "lift" section, 4-bar loop
LEAD = [
    (81, 0.0, 1.0), (78, 1.0, 0.5), (81, 1.5, 0.5), (83, 2.0, 1.0), (81, 3.0, 1.0),
    (85, 4.0, 1.5), (81, 5.5, 0.5), (76, 6.0, 2.0),
    (78, 8.0, 1.0), (83, 9.0, 0.5), (81, 9.5, 0.5), (78, 10.0, 1.0), (74, 11.0, 1.0),
    (79, 12.0, 1.0), (83, 13.0, 1.0), (86, 14.0, 1.0), (85, 15.0, 1.0),
]

sec = cfg["sections"]  # bar numbers (0-based): groove, full, lift, outro, end
B_GROOVE, B_FULL, B_LIFT, B_OUTRO, B_END = (sec[k] for k in ("groove", "full", "lift", "outro", "end"))
SWING = 0.018
BREAKS = set(cfg.get("breaks", []))   # bars with the drums pulled back
DUCKS = cfg.get("ducks", [])           # [start, end] seconds where the music dips

for bar in range(B_END):
    t0 = bar * BAR
    root, tones = CHORDS[bar % 4]
    intro = bar < B_GROOVE
    groove = B_GROOVE <= bar < B_FULL
    full = B_FULL <= bar < B_LIFT
    lift = B_LIFT <= bar < B_OUTRO
    outro = bar >= B_OUTRO
    last_before = bar + 1 in (B_GROOVE, B_FULL, B_LIFT, B_OUTRO)  # bar leading into a new section

    # --- plucks ---
    pat = PLUCK_PATS[bar % 4]
    for i, idx in enumerate(pat):
        if idx < 0:
            continue
        if intro and i % 2 == 1 and bar == 0:
            continue  # very sparse first bar
        if outro and bar > B_OUTRO and (i % 4 != 0 or bar > B_OUTRO + 1):
            continue  # just a few soft notes under the logo
        at = t0 + i * BEAT / 2 + (SWING if i % 2 else 0)
        n = tones[idx] + (12 if (lift and i % 4 == 2) else 0)
        vel = 0.75 if i % 2 == 0 else 0.5
        if intro:
            vel *= 0.8
        sig = pluck(note(n), 0.6, bright=1.2 if (full or lift) else 0.8)
        pan = (-0.45, 0.45, -0.15, 0.3)[i % 4]
        place(musicL, musicR, sig, at, vel, pan)
        # dotted-eighth echo for width
        place(musicL, musicR, sig, at + BEAT * 0.75, vel * 0.28, -pan)
        place(musicL, musicR, sig, at + BEAT * 1.5, vel * 0.12, pan)

    # --- bass ---
    if not intro:
        if outro and bar > B_OUTRO:
            pass
        elif outro:
            place(musicL, musicR, bass(note(root), BAR * 0.95), t0, 0.9)
        else:
            steps = [(0, 0.9, 0), (1.5, 0.45, 0), (2, 0.9, 12 if (full or lift) else 0), (3.5, 0.45, 0)]
            if lift:
                steps = [(0, 0.7, 0), (0.75, 0.5, 12), (1.5, 0.45, 0), (2, 0.7, 0), (2.75, 0.5, 12), (3.5, 0.45, 7)]
            for b, d, up in steps:
                place(musicL, musicR, bass(note(root + up), d * BEAT), t0 + b * BEAT, 0.85)

    # --- pad ---
    if full or lift or (outro and bar == B_OUTRO):
        p = pad([note(n) for n in tones[:3]], BAR * (2.2 if outro else 1.05))
        place(musicL, musicR, p, t0, 1.0 if lift or outro else 0.7, -0.2)
        place(musicL, musicR, p, t0 + 0.012, 1.0 if lift or outro else 0.7, 0.2)

    # --- lead ---
    if lift:
        lb = (bar - B_LIFT) % 4 * 4.0
        for n, b, d in LEAD:
            if lb <= b < lb + 4:
                sig = lead(note(n), d * BEAT * 1.1)
                at = t0 + (b - lb) * BEAT
                place(musicL, musicR, sig, at, 0.8, 0.1)
                place(musicL, musicR, sig, at + BEAT * 0.75, 0.22, -0.4)

    # --- drums ---
    if not intro and not (outro and bar > B_OUTRO):
        kicks = [0, 2] if groove else [0, 1, 2, 3]
        if outro or bar in BREAKS:
            kicks = [0]
        for b in kicks:
            at = t0 + b * BEAT
            place(drumsL, drumsR, kick(), at, 0.95)
            i = int(at * SR)
            env = 1 - 0.55 * np.exp(-t_arr(0.3) * 9)
            duck[i:i + len(env)] = np.minimum(duck[i:i + len(env)], env[: max(0, N - i)])
        if (full or lift):
            for b in (1, 3):
                place(drumsL, drumsR, clap(), t0 + b * BEAT, 0.8, 0.05)
        if not outro:
            for i in range(8):
                at = t0 + i * BEAT / 2 + (SWING if i % 2 else 0)
                if groove and i % 2 == 0:
                    continue  # off-beat hats only
                place(drumsL, drumsR, hat(open_=(i == 7 and last_before)), at, 0.75 if i % 2 else 0.4, 0.25)
            if (full or lift) and bar not in BREAKS:
                for i in range(16):
                    at = t0 + i * BEAT / 4 + (SWING * 0.5 if i % 2 else 0)
                    place(drumsL, drumsR, shaker(), at, 0.55 if i % 4 == 2 else 0.3, -0.35)
        if last_before and not outro:  # little fill into the next section
            for k, b in enumerate((3.0, 3.25, 3.5, 3.75)):
                place(drumsL, drumsR, clap(), t0 + b * BEAT, 0.3 + 0.12 * k, -0.1 + 0.1 * k)

# risers into section starts, impact on the downbeats
for b in (B_GROOVE, B_FULL, B_LIFT):
    place(mixL, mixR, sfx_riser(1.0), b * BAR - 1.0, 0.55)
place(mixL, mixR, sfx_riser(2.0), B_OUTRO * BAR - 2.0, 0.8)
place(mixL, mixR, sfx_impact(), B_OUTRO * BAR, 0.9)
place(mixL, mixR, sfx_impact(), B_GROOVE * BAR, 0.5)
# final chord: D major ring-out
for k, n in enumerate((50, 62, 66, 69, 74, 78)):
    place(musicL, musicR, pluck(note(n), 2.4, bright=1.0), B_OUTRO * BAR + BAR + k * 0.035, 0.6, (-0.5 + k * 0.2))

# ---------- sfx cues ----------
for c in cfg["sfx"]:
    fn = SFX[c["s"]]
    place(mixL, mixR, fn(), float(c["t"]), float(c.get("g", 1.0)), float(c.get("pan", 0.0)))

# ---------- mix ----------
for d0, d1 in DUCKS:
    env = np.ones(N)
    i0, i1 = int(d0 * SR), int(d1 * SR)
    r = int(0.06 * SR)
    env[i0:i1] = 0.28
    env[i0 - r:i0] = np.linspace(1, 0.28, r)
    env[i1:i1 + r // 3] = np.linspace(0.28, 1, r // 3)
    for bus in (musicL, musicR, drumsL, drumsR):
        bus *= env
musicL *= duck
musicR *= duck
L = musicL * 0.8 + drumsL * 0.62 + mixL * 0.9
R = musicR * 0.8 + drumsR * 0.62 + mixR * 0.9
n_out = int(TOTAL * SR)
L, R = L[:n_out], R[:n_out]
fade_in = np.clip(np.arange(n_out) / (0.02 * SR), 0, 1)
fade_out = np.clip((n_out - np.arange(n_out)) / (1.6 * SR), 0, 1) ** 1.5
L, R = L * fade_in * fade_out, R * fade_in * fade_out
peak = max(np.abs(L).max(), np.abs(R).max(), 1e-9)
L, R = np.tanh(L / peak * 1.5), np.tanh(R / peak * 1.5)
peak = max(np.abs(L).max(), np.abs(R).max())
g = 0.89 / peak
pcm = (np.stack([L * g, R * g], axis=1) * 32767).astype("<i2")

out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "soundtrack.wav")
with wave.open(out, "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(pcm.tobytes())
print("wrote", out, f"{TOTAL:.1f}s")
