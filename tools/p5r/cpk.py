"""Minimal CRI CPK reader (TOC, P5R's per-file XOR, CRILAYLA) for pulling UI files out of
Persona 5 Royal's archives (Steam: steamapps/common/P5R/CPK/*.CPK).

    python3 cpk.py ARCHIVE list [substring]      -> matching paths with sizes
    python3 cpk.py ARCHIVE extract PATH... OUTDIR -> extract files (decrypted, decompressed)
"""
import os
import struct
import sys


def _decrypt_utf(data: bytes) -> bytes:
    # Some CPKs XOR-encrypt their @UTF tables; plain tables start with "@UTF".
    if data[:4] == b"@UTF":
        return data
    out = bytearray(len(data))
    m, t = 0x655F, 0x4115
    for i, b in enumerate(data):
        out[i] = b ^ (m & 0xFF)
        m = (m * t) & 0xFFFFFFFF
    return bytes(out)


class Utf:
    TYPES = {0: ">B", 1: ">b", 2: ">H", 3: ">h", 4: ">I", 5: ">i", 6: ">Q", 7: ">q", 8: ">f"}

    def __init__(self, raw: bytes):
        d = _decrypt_utf(raw)
        assert d[:4] == b"@UTF", d[:16]
        size = struct.unpack(">I", d[4:8])[0]
        t = d[8:8 + size]
        rows_off = struct.unpack(">H", t[2:4])[0]
        strings_off, data_off, _name = struct.unpack(">III", t[4:16])
        ncols, row_len, nrows = struct.unpack(">HHI", t[16:24])
        self.t, self.strings_off, self.data_off = t, strings_off, data_off
        pos = 24
        cols = []
        for _ in range(ncols):
            flags = t[pos]
            name = self._str(struct.unpack(">I", t[pos + 1:pos + 5])[0])
            pos += 5
            storage, typ = flags & 0xF0, flags & 0x0F
            const = None
            if storage in (0x30, 0x70):
                const, pos = self._value(typ, pos)
            cols.append((name, storage, typ, const))
        self.rows = []
        for r in range(nrows):
            p = rows_off + r * row_len
            row = {}
            for name, storage, typ, const in cols:
                if storage == 0x50:
                    row[name], p = self._value(typ, p)
                elif storage in (0x30, 0x70):
                    row[name] = const
                else:
                    row[name] = None
            self.rows.append(row)

    def _str(self, off):
        s = self.strings_off + off
        e = self.t.index(b"\0", s)
        return self.t[s:e].decode("utf-8", "replace")

    def _value(self, typ, p):
        if typ in self.TYPES:
            fmt = self.TYPES[typ]
            n = struct.calcsize(fmt)
            return struct.unpack(fmt, self.t[p:p + n])[0], p + n
        if typ == 0xA:
            return self._str(struct.unpack(">I", self.t[p:p + 4])[0]), p + 4
        if typ == 0xB:
            off, size = struct.unpack(">II", self.t[p:p + 8])
            return (self.data_off + off, size), p + 8
        raise ValueError(f"utf type {typ}")


def crilayla(src: bytes) -> bytes:
    usize, hoff = struct.unpack("<II", src[8:16])
    result = bytearray(usize + 0x100)
    result[:0x100] = src[hoff + 0x10:hoff + 0x110]
    inp = len(src) - 0x100 - 1
    out_end = 0x100 + usize - 1
    pool = 0
    left = 0
    done = 0

    def bits(n):
        nonlocal pool, left, inp
        v = 0
        while n:
            if left == 0:
                pool = src[inp]
                left = 8
                inp -= 1
            take = min(left, n)
            v = (v << take) | ((pool >> (left - take)) & ((1 << take) - 1))
            left -= take
            n -= take
        return v

    while done < usize:
        if bits(1):
            ref = out_end - done + bits(13) + 3
            length = 3
            for level in (2, 3, 5, 8):
                v = bits(level)
                length += v
                if v != (1 << level) - 1:
                    break
            else:
                while True:
                    v = bits(8)
                    length += v
                    if v != 255:
                        break
            for _ in range(length):
                result[out_end - done] = result[ref]
                ref -= 1
                done += 1
        else:
            result[out_end - done] = bits(8)
            done += 1
    return bytes(result)


class Cpk:
    def __init__(self, path):
        self.f = open(path, "rb")
        head = self._chunk(0, b"CPK ")
        self.header = head.rows[0]
        toc_off = self.header["TocOffset"]
        content_off = self.header.get("ContentOffset") or toc_off
        self.base = min(toc_off, content_off)
        toc = self._chunk(toc_off, b"TOC ")
        self.files = {}
        for r in toc.rows:
            name = (r["DirName"] + "/" if r["DirName"] else "") + r["FileName"]
            encrypted = r.get("UserString") == "CRI_CFATTR:ENCRYPT"
            self.files[name] = (self.base + r["FileOffset"], r["FileSize"], r["ExtractSize"], encrypted)

    def _chunk(self, off, magic):
        self.f.seek(off)
        hdr = self.f.read(16)
        assert hdr[:4] == magic, (hdr[:4], magic)
        size = struct.unpack("<Q", hdr[8:16])[0]
        return Utf(self.f.read(size))

    def read(self, name):
        off, size, _, encrypted = self.files[name]
        self.f.seek(off)
        data = bytearray(self.f.read(size))
        # P5R (PC) in-place XOR: data[0x20:0x420] ^= data[0x420:0x820] (CriFsV2Lib P5RCrypto).
        if encrypted and size > 0x820:
            for i in range(0x20, 0x420):
                data[i] ^= data[i + 0x400]
        data = bytes(data)
        return crilayla(data) if data[:8] == b"CRILAYLA" else data


if __name__ == "__main__":
    cpk = Cpk(sys.argv[1])
    if sys.argv[2] == "list":
        sub = sys.argv[3].lower() if len(sys.argv) > 3 else ""
        for name, (_, size, xsize, _enc) in sorted(cpk.files.items()):
            if sub in name.lower():
                print(f"{xsize:>10} {size:>10} {name}")
    elif sys.argv[2] == "extract":
        *names, outdir = sys.argv[3:]
        for name in names:
            dest = os.path.join(outdir, name)
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            with open(dest, "wb") as fh:
                fh.write(cpk.read(name))
            print("extracted", dest)
