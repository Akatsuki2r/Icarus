"""Generate the three short notification chimes. No external assets.

Run from the repo root: python3 tools/gen_chimes.py
"""
import math
import os
import struct
import wave

RATE = 22050
OUT = os.path.join("app", "src", "main", "res", "raw")


def note(freq, seconds, volume=0.28):
    count = int(RATE * seconds)
    samples = []
    for i in range(count):
        t = i / RATE
        attack = min(1.0, i / (RATE * 0.012))
        release = min(1.0, (count - i) / (RATE * 0.05))
        env = attack * release * volume
        samples.append(int(32767 * env * math.sin(2 * math.pi * freq * t)))
    return samples


def silence(seconds):
    return [0] * int(RATE * seconds)


def write(name, samples):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name + ".wav")
    with wave.open(path, "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes(b"".join(struct.pack("<h", s) for s in samples))
    print(path, f"{len(samples) / RATE:.2f}s")


write("chime_prepare", note(659, 0.13) + silence(0.04) + note(880, 0.16))
write("chime_start", note(880, 0.20))
write("chime_overdue", note(523, 0.14) + silence(0.03) + note(392, 0.20))
