"""Atlus SPD (sprite dictionary) parser. As a script it dumps a pack for picking sprite ids:
textures -> PNG, sprites -> crops named ID_tTEX_NAME.png plus sprites.json (names are Shift-JIS).

    python3 spd.py PACK.SPD OUTDIR
"""
import io
import json
import os
import struct
import sys

from PIL import Image


def load(d: bytes):
    """(texture id -> {name, w, h, data: DDS bytes}, [sprite {id, tex, x, y, w, h, name}])."""
    magic, _, _, _, _, ntex, nspr, tex_off, spr_off = struct.unpack_from("<5i2h2i", d, 0)
    assert magic == 0x30525053, hex(magic)
    textures = {}
    for i in range(ntex):
        tid, _, off, size, w, h, _, _, name = struct.unpack_from("<8i16s", d, tex_off + i * 0x30)
        textures[tid] = dict(id=tid, w=w, h=h, name=name.split(b"\0")[0].decode("latin1"), data=d[off:off + size])
    sprites = []
    for i in range(nspr):
        v = struct.unpack_from("<28I48s", d, spr_off + i * 0xA0)
        sprites.append(dict(id=v[0], tex=v[1], x=v[8], y=v[9], w=v[10], h=v[11], name=v[28].split(b"\0")[0].decode("latin1")))
    return textures, sprites


if __name__ == "__main__":
    path, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    textures, sprites = load(open(path, "rb").read())
    images = {}
    for tid, t in textures.items():
        try:
            im = Image.open(io.BytesIO(t["data"]))
            im.load()
            images[tid] = im.convert("RGBA")
            images[tid].save(f"{out}/tex_{tid:03d}_{t['name']}.png")
        except Exception as e:
            print("tex", tid, t["name"], "ERR", e)
    os.makedirs(f"{out}/sprites", exist_ok=True)
    for s in sprites:
        im = images.get(s["tex"])
        if im is None or s["w"] == 0 or s["h"] == 0:
            continue
        safe = "".join(ch if ch.isalnum() or ch in "_-" else "_" for ch in s["name"]) or "x"
        im.crop((s["x"], s["y"], s["x"] + s["w"], s["y"] + s["h"])).save(f"{out}/sprites/{s['id']:04d}_t{s['tex']}_{safe}.png")
    json.dump([{k: v for k, v in s.items()} for s in sprites], open(f"{out}/sprites.json", "w"), indent=1)
    print(len(textures), "textures,", len(sprites), "sprites")
