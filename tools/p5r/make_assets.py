"""Regenerates the Persona 5 Royal camp-menu sprites (app/src/main/res/drawable-nodpi) and sounds
(app/src/main/res/raw) from the user's own P5R install (Steam). Sprites are pre-scaled to their
on-screen size in a 1920x1080 frame; the scales were measured on an in-game screenshot by template
matching these same sprites.

    python3 tools/p5r/make_assets.py [P5R install dir]

Default install dir: ~/.local/share/Steam/steamapps/common/P5R. Needs Pillow (decodes DDS).
Sources:
    EN.CPK      INIT/P5CAMP_00SPD.SPD           camp-menu sprites: COMMAND, LIKE THAT!, Dont Look at,
                                                MENU: Main, big number digits, money digits (the clock)
    EN.CPK      CALENDAR/P5_FIELD_DAY.SPD       the field date HUD's weather icons and their plate
    BASE.CPK    CAMP/HEROTEX/P5_CAMPTOP_2D.DDS  Joker, hand over his face
    SOUND_E.CPK SOUND/SYSTEM.ACB/.AWB           cues "cursor" (selection change), "camp_open_field"
Sprite ids were picked from `spd.py` dumps of those packs, cue names from `sfx.py list`.
"""
import io
import os
import sys

from PIL import Image

import sfx
import spd
from cpk import Cpk

GAME = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser("~/.local/share/Steam/steamapps/common/P5R")
RES = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../app/src/main/res"))
os.makedirs(f"{RES}/drawable-nodpi", exist_ok=True)  # git ignores everything generated into it


def sprites(pack: bytes, ids: set) -> dict:
    """Sprite id -> RGBA crop of its texture; decodes only the textures those sprites sit on."""
    textures, entries = spd.load(pack)
    decoded, out = {}, {}
    for s in entries:
        if s["id"] in ids and s["id"] not in out and s["w"] and s["h"]:
            if s["tex"] not in decoded:
                decoded[s["tex"]] = Image.open(io.BytesIO(textures[s["tex"]]["data"])).convert("RGBA")
            out[s["id"]] = decoded[s["tex"]].crop((s["x"], s["y"], s["x"] + s["w"], s["y"] + s["h"]))
    missing = ids - out.keys()
    assert not missing, f"sprites not found: {sorted(missing)}"
    return out


def save(img, name, scale):
    if scale != 1:
        img = img.resize((max(1, round(img.width * scale)), max(1, round(img.height * scale))), Image.LANCZOS)
    img.save(f"{RES}/drawable-nodpi/{name}.png", optimize=True)
    print(name, img.size)


BIG_DIGITS = [40, 41, 42, 43, 44, 45, 46, 47, 48, 49]  # "BG 数字:0..9"
CASH = list(range(211, 221))  # "お金数字 0..9": the camp menu's "¥6,340"
CASH_RIM = list(range(222, 232))  # "お金数字ふち": their outline layer
en = Cpk(f"{GAME}/CPK/EN.CPK")
camp = sprites(en.read("INIT/P5CAMP_00SPD.SPD"), {208, 53, 54, 55, 68, 486, *BIG_DIGITS, *CASH, *CASH_RIM})

# Joker: cropped to the drawn area, 0.506 of the texture size; everything below 1100 px is off-screen.
art = Image.open(io.BytesIO(Cpk(f"{GAME}/CPK/BASE.CPK").read("CAMP/HEROTEX/P5_CAMPTOP_2D.DDS"))).convert("RGBA")
art = art.crop(art.getchannel("A").getbbox())
art = art.resize((round(art.width * 0.506), round(art.height * 0.506)), Image.LANCZOS)
art = art.crop((0, 0, art.width, min(art.height, 1100)))
art.save(f"{RES}/drawable-nodpi/p5_camptop.png", optimize=True)
print("p5_camptop", art.size)

save(camp[208], "p5_command", 0.445)
save(camp[53], "p5_dont_look", 0.40)  # "Dont Look at"
save(camp[54], "p5_like_that", 0.60)
save(camp[55], "p5_menu", 0.62)  # "MENU:"
save(camp[68], "p5_menu_main", 0.50)  # "Main"
save(camp[486], "p5_select_command", 0.60)
for d, sid in enumerate(BIG_DIGITS):
    save(camp[sid], f"p5_big_digit_{d}", 0.76)
# Money digits: both layers cut to the outline's width and one shared height, so digit and outline
# stay aligned and every digit keeps its place against the baseline.
top = min(camp[i].getchannel("A").getbbox()[1] for i in CASH_RIM)
bottom = max(camp[i].getchannel("A").getbbox()[3] for i in CASH_RIM)
for d, (digit, rim) in enumerate(zip(CASH, CASH_RIM)):
    x0, _, x1, _ = camp[rim].getchannel("A").getbbox()
    save(camp[digit].crop((x0, top, x1, bottom)), f"p5_money_digit_{d}", 0.7)
    save(camp[rim].crop((x0, top, x1, bottom)), f"p5_money_rim_{d}", 0.7)

# Weather: the field date HUD's icons, white silhouettes with three animation frames each ("天気
# 晴れ1".."雪3"), and the tilted plate they sit on ("天気土台"), drawn 80 px tall in the corner chips.
WEATHER = {"sunny": (97, 109, 110), "cloudy": (99, 113, 114), "rain": (98, 111, 112), "snow": (100, 115, 116)}
day = sprites(en.read("CALENDAR/P5_FIELD_DAY.SPD"), {96, *(sid for ids in WEATHER.values() for sid in ids)})
save(day[96], "p5_weather_plate", 80 / 300)
for kind, ids in WEATHER.items():
    for n, sid in enumerate(ids, 1):
        save(day[sid], f"p5_weather_{kind}_{n}", 80 / 300)

bank = sfx.SystemBank(f"{GAME}/CPK/SOUND_E.CPK")
cue = {name: index for index, name in bank.names.items()}
os.makedirs(f"{RES}/raw", exist_ok=True)
for name, out in (("cursor", "p5_cursor"), ("camp_open_field", "p5_open")):
    (wave_id,) = bank.cue_waveforms(cue[name])
    rate, _, channels = bank.decode(wave_id)
    sfx.write_wav(f"{RES}/raw/{out}.wav", rate, channels)
    print(out, rate, "Hz", len(channels[0]), "samples")
