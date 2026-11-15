"""Makes AWM's warning siren sounds from the maintainer's flat siren_loop.ogg (2026-11-13), and a placeholder klaxon.

warning_siren.ogg        the loop, a little soft clipping (the omni head)
warning_siren_horn.ogg   brighter and clipped harder (the horn head: more distortion)
*_low.ogg                each an octave down: Minecraft will not play below half pitch, and a siren winding up
                         starts lower than that, so the sound crossfades onto these near the bottom
klaxon.ogg               synthesised: a harsh ~220 Hz buzz, 0.55 s on / 0.2 s off, four pulses (a stand-in for a
                         real recording: replace the file, keep the name)
All mono (Minecraft places only mono sounds), loop-clean (each effect wraps round the loop's end).
"""
import numpy as np
import soundfile as sf

SRC = 'tools/sounds/siren_loop.ogg'  # The maintainer's recording. Run from the repo root.
OUT = 'src/main/resources/assets/anotherweathermod/sounds/'

d, sr = sf.read(SRC)
if d.ndim > 1:
    d = d.mean(axis=1)
d = d / np.abs(d).max()

# The fundamental, for the record.
spec = np.abs(np.fft.rfft(d * np.hanning(len(d))))
freqs = np.fft.rfftfreq(len(d), 1 / sr)
band = (freqs > 100) & (freqs < 2000)
print('loop: %.2f s, %d Hz, strongest tone %.0f Hz' % (len(d) / sr, sr, freqs[band][np.argmax(spec[band])]))


def norm(x, peak=0.9):
    return x / np.abs(x).max() * peak


def soft(x, drive):
    return np.tanh(drive * x) / np.tanh(drive)


omni = norm(soft(d, 3.0))  # 2026-11-14: more grit (was 1.6), the maintainer: more distortion.
bright = d + 0.35 * (d - np.roll(d, 1)) * 6  # A little pre-emphasis: the horn's throat is brighter.
horn = norm(0.7 * soft(norm(bright, 1.0), 7.0) + 0.3 * np.clip(norm(bright, 1.0) * 4.0, -1, 1))  # Was drive 4, 0.2 hard clip at 3x.


def octave_down(x):
    n = len(x)
    src = np.append(x, x[0])  # Wrap, so the loop stays seamless.
    pos = np.arange(2 * n) / 2.0
    return np.interp(pos, np.arange(n + 1), src)


def loopfade(x, seconds=0.03):
    """Seamless loop (2026-11-14: the horn clicked at its loop point, a pop the maintainer heard): the last bit is
    faded into the first and dropped, so the end runs straight on into the start."""
    n = int(seconds * sr)
    w = np.linspace(0.0, 1.0, n)
    y = x[:-n].copy()
    y[:n] = x[:n] * w + x[-n:] * (1.0 - w)
    return y


def write(name, x):
    sf.write(OUT + name, x.astype(np.float32), sr, format='OGG', subtype='VORBIS')
    print('wrote', name, '%.2f s' % (len(x) / sr))


omni, horn = loopfade(omni), loopfade(horn)
write('warning_siren.ogg', omni)
write('warning_siren_horn.ogg', horn)
write('warning_siren_low.ogg', norm(octave_down(omni)))
write('warning_siren_horn_low.ogg', norm(octave_down(horn)))

# The klaxon: a sawtooth and square buzz, rattled at 50 Hz, pulsed brrt-brrt-brrt, clipped.
ksr = 44100
pulses, on, off = 4, 0.55, 0.2  # 2026-11-14, the maintainer: lower and longer (was 5 x 0.25 s on, 0.15 off).
n = int(round(pulses * (on + off) * ksr))
t = np.arange(n) / ksr
f = 220.0  # Was 360 Hz.
saw = 2 * ((t * f) % 1.0) - 1
square = np.sign(np.sin(2 * np.pi * f * 1.01 * t))
buzz = 0.6 * saw + 0.4 * square
buzz *= 0.75 + 0.25 * np.sign(np.sin(2 * np.pi * 50 * t))  # The rattle of the diaphragm.
phase = t % (on + off)
attack, release = 0.008, 0.02
env = np.where(phase < on, np.minimum(1.0, phase / attack) * np.minimum(1.0, (on - phase) / release), 0.0)
klaxon = norm(np.tanh(3.0 * buzz * env), 0.85)
write('klaxon.ogg', klaxon)
