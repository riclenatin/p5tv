"""P5R system UI sound bank (SOUND_E.CPK: SOUND/SYSTEM.ACB + SYSTEM.AWB) -> WAV.

    python3 sfx.py list                  -> cue index, name, waveform ids, durations
    python3 sfx.py wav CUE [CUE...] OUTDIR -> one WAV per waveform of each named cue

Waveforms are CRI ADX with encryption type 9; key = P5R (PC) keycode 9923540143823782
(vgmstream src/meta/adx_keys.h). Decoder follows vgmstream's coding/adx_decoder.c.
"""
import math
import os
import struct
import sys
import wave

from cpk import Cpk, Utf

CPK = os.path.expanduser("~/.local/share/Steam/steamapps/common/P5R/CPK/SOUND_E.CPK")
KEYCODE = 9923540143823782


def key9_derive(k, subkey=0):
    if subkey:
        k = (k * ((subkey << 16) | ((~subkey + 2) & 0xFFFF))) & 0xFFFFFFFFFFFFFFFF
    k -= 1
    return (k >> 27) & 0x7FFF, ((k >> 12) & 0x7FFC) | 1, ((k << 1) & 0x7FFF) | 1


def afs2(buf, base=0):
    assert buf[base:base + 4] == b"AFS2"
    offsz, idsz = buf[base + 5], buf[base + 6]
    n, align, subkey = struct.unpack("<IHH", buf[base + 8:base + 16])
    ids = [int.from_bytes(buf[base + 16 + i * idsz:base + 16 + (i + 1) * idsz], "little") for i in range(n)]
    p = base + 16 + n * idsz
    offs = [int.from_bytes(buf[p + i * offsz:p + (i + 1) * offsz], "little") for i in range(n + 1)]
    entries = {}
    for i in range(n):
        s = -(-offs[i] // align) * align
        entries[ids[i]] = (base + s, offs[i + 1] - s)
    return entries, subkey


def adx_decode(b, key):
    """-> (rate, channels, list of per-channel int16 lists). vgmstream-compatible (scale + 1, v4 history)."""
    start = struct.unpack(">H", b[2:4])[0] + 4
    ch, rate, ns, cutoff = b[7], *struct.unpack(">IIH", b[8:18])
    assert b[start - 6:start] == b"(c)CRI" and b[4] == 3 and b[5] == 0x12 and b[6] == 4
    z = math.cos(2 * math.pi * cutoff / rate)
    a, bb = math.sqrt(2) - z, math.sqrt(2) - 1
    c = (a - math.sqrt((a + bb) * (a - bb))) / bb
    c1, c2 = int(c * 8192), int(c * c * -4096)
    hist = [list(struct.unpack(">hh", b[0x18 + i * 4:0x1C + i * 4])) for i in range(ch)]
    encrypted = b[19] in (8, 9)
    xs, xm, xa = key
    xor = xs
    out = [[] for _ in range(ch)]
    for f in range((ns + 31) // 32):
        for ci in range(ch):
            o = start + (f * ch + ci) * 18
            raw = struct.unpack(">H", b[o:o + 2])[0]
            if encrypted:
                scale = ((raw ^ xor) & 0x1FFF) + 1
                xor = (xor * xm + xa) & 0x7FFF
            else:
                scale = (raw - 0x10000 if raw & 0x8000 else raw) + 1
            h1, h2 = hist[ci]
            dst = out[ci]
            for j in range(32):
                nib = b[o + 2 + (j >> 1)]
                n = (nib & 0xF) if j & 1 else (nib >> 4)
                if n >= 8:
                    n -= 16
                s = n * scale + ((c1 * h1 + c2 * h2) >> 12)
                s = -32768 if s < -32768 else 32767 if s > 32767 else s
                dst.append(s)
                h2, h1 = h1, s
            hist[ci] = [h1, h2]
    return rate, ch, [o[:ns] for o in out]


class SystemBank:
    def __init__(self, cpk_path=CPK):
        cpk = Cpk(cpk_path)
        self.acb = cpk.read("SOUND/SYSTEM.ACB")
        self.awb = cpk.read("SOUND/SYSTEM.AWB")
        u = Utf(self.acb)
        row = u.rows[0]
        tab = lambda name: Utf(u.t[row[name][0]:row[name][0] + row[name][1]])  # noqa: E731
        self.cue, self.cuename, self.wave = tab("CueTable"), tab("CueNameTable"), tab("WaveformTable")
        self.synth, self.seq, self.track, self.tev = tab("SynthTable"), tab("SequenceTable"), tab("TrackTable"), tab("TrackEventTable")
        self.mem, mem_subkey = afs2(self.acb, 8 + row["AwbFile"][0])  # Utf.t starts 8 bytes into the file
        self.stream, stream_subkey = afs2(self.awb)
        self.mem_key, self.stream_key = key9_derive(KEYCODE, mem_subkey), key9_derive(KEYCODE, stream_subkey)
        self.names = {r["CueIndex"]: r["CueName"] for r in self.cuename.rows}

    @staticmethod
    def _bytes(table, v):
        return table.t[v[0]:v[0] + v[1]] if v and v[1] else b""

    def _resolve(self, kind, idx, depth=0):
        if depth > 12:
            return []
        if kind == 1:  # waveform
            return [idx]
        if kind == 2:  # synth: ReferenceItems = (u16 type, u16 index) pairs
            refs = self._bytes(self.synth, self.synth.rows[idx]["ReferenceItems"])
            pairs = struct.unpack(">%dH" % (len(refs) // 2), refs)
            return [w for t, i in zip(pairs[0::2], pairs[1::2]) for w in self._resolve(t, i, depth + 1)]
        if kind == 3:  # sequence -> tracks -> TrackEvent commands; 2000/2003 = noteOn(type, index)
            s = self.seq.rows[idx]
            tracks = self._bytes(self.seq, s["TrackIndex"])
            res = []
            for ti in struct.unpack(">%dH" % (len(tracks) // 2), tracks)[:s["NumTracks"]]:
                ev = self.track.rows[ti]["EventIndex"]
                if ev == 0xFFFF:
                    continue
                cmd, p = self._bytes(self.tev, self.tev.rows[ev]["Command"]), 0
                while p + 3 <= len(cmd):
                    code, size = struct.unpack(">HB", cmd[p:p + 3])
                    if code in (2000, 2003) and size >= 4:
                        res += self._resolve(*struct.unpack(">HH", cmd[p + 3:p + 7]), depth + 1)
                    p += 3 + size
            return res
        return []

    def cue_waveforms(self, cue_index):
        c = self.cue.rows[cue_index]
        return list(dict.fromkeys(self._resolve(c["ReferenceType"], c["ReferenceIndex"])))

    def decode(self, wi):
        w = self.wave.rows[wi]
        if w["Streaming"] == 0:
            o, s = self.mem[w["MemoryAwbId"]]
            return adx_decode(self.acb[o:o + s], self.mem_key)
        o, s = self.stream[w["StreamAwbId"]]
        return adx_decode(self.awb[o:o + s], self.stream_key)


def write_wav(path, rate, chans):
    with wave.open(path, "wb") as f:
        f.setnchannels(len(chans))
        f.setsampwidth(2)
        f.setframerate(rate)
        f.writeframes(b"".join(struct.pack("<%dh" % len(chans), *fr) for fr in zip(*chans)))


if __name__ == "__main__":
    bank = SystemBank()
    if sys.argv[1] == "list":
        for ci in range(len(bank.cue.rows)):
            ws = bank.cue_waveforms(ci)
            desc = ", ".join("%d(%dms)" % (w, bank.wave.rows[w]["NumSamples"] * 1000 // bank.wave.rows[w]["SamplingRate"]) for w in ws)
            print(ci, bank.names.get(ci), desc)
    elif sys.argv[1] == "wav":
        outdir = sys.argv[-1]
        os.makedirs(outdir, exist_ok=True)
        by_name = {v: k for k, v in bank.names.items()}
        for name in sys.argv[2:-1]:
            ws = bank.cue_waveforms(by_name[name])
            for k, wi in enumerate(ws):
                rate, _, chans = bank.decode(wi)
                path = os.path.join(outdir, name + ("_v%d" % k if len(ws) > 1 else "") + ".wav")
                write_wav(path, rate, chans)
                print("wrote", path)
