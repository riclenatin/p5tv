"""Renders the silhouettes of P5R's camp-menu opening from Joker's menu model, one image per frame.

The game draws the menu model playing GAP animation 0 as a flat silhouette: white on the red side of
the star panel's left edge, black over the panel, where the face, eyes and mask show their black and
white UI textures. The files hold no camera, so params.json carries one fitted per frame to 60 fps
footage of the game, plus the panel edge measured there (see its "about").

Software
  * Blender 4.2 LTS (built with 4.2.23, the portable Linux tarball from download.blender.org)
  * BlenderToolsForGFS 0.3.1 by Pherakki (https://github.com/Pherakki/BlenderToolsForGFS),
    installed as an add-on, e.g. unpacked to SCRIPTS/addons/BlenderToolsForGFS

Inputs, extracted from the user's own BASE.CPK (MODEL/CAMP/ holds a second copy; for this animation
the two differ by less than the camera fit's error):
  python3 tools/p5r/cpk.py "$P5R/CPK/BASE.CPK" extract INIT/MENU_C0001_001_A.GMD INIT/MENU_C0001_001_A.GAP build/p5r

Command (from the repository root):
  BLENDER_USER_SCRIPTS=SCRIPTS blender -b --factory-startup --python tools/p5r/opening/render.py -- \
      --gmd build/p5r/INIT/MENU_C0001_001_A.GMD --gap build/p5r/INIT/MENU_C0001_001_A.GAP \
      --params tools/p5r/opening/params.json --out build/p5r/frames [--frames 125,126]

Output: OUT/fNNN.png, RGBA 1920x1080, the model layer only: white and black, anti-aliased coverage
in alpha. tools/p5r/opening/pack.py turns them into the app's assets.
"""
import argparse
import json
import math
import os
import sys

import addon_utils
import bpy
import numpy as np

ANIM_ACTION = "MENU_C0001_001_A|BASE|0"
ARMATURE = "MENU_C0001_001_A"
# meshes the camp-top Joker shows (phantom-thief outfit, gloved hands, UI face); everything else in the GMD is
# parked far off-screen by anim 0 or belongs to other menus (school uniform, bag, phone, card, gun, effect lines)
VISIBLE = ["c0001_body_051_UI_main", "c0001_hair_002a", "c0001_faceUI_main", "c0001_faceUI_main_0",
           "c0001_hand_051B_UI_main", "kamen01_051_main", "line", "eye l0_main", "eye r0_main"]
TEXTURED = {"c0001_faceUI_main", "c0001_faceUI_main_0", "kamen01_051_main", "eye l0_main", "eye r0_main"}
W, H = 1920, 1080


def parse():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    p = argparse.ArgumentParser()
    p.add_argument("--gmd", required=True)
    p.add_argument("--gap", required=True)
    p.add_argument("--params", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--frames", default="")
    p.add_argument("--aa", default="32", help="Workbench AA samples")
    return p.parse_args(argv)


def setup_scene(gmd, gap):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    mod = addon_utils.enable("BlenderToolsForGFS", default_set=True)
    if mod is None:
        raise SystemExit("BlenderToolsForGFS not found: set BLENDER_USER_SCRIPTS to the folder whose addons/ holds it")
    assert bpy.ops.import_file.import_gfs(filepath=gmd) == {"FINISHED"}
    arm = bpy.data.objects[ARMATURE]
    assert bpy.ops.import_file.import_gap(filepath=gap, armature_name=arm.name) == {"FINISHED"}
    ad = arm.animation_data_create()
    for tr in list(ad.nla_tracks):  # the importer's 'Rest Pose' strip; anim 0 keys every visible bone
        ad.nla_tracks.remove(tr)
    act = bpy.data.actions[ANIM_ACTION]
    ad.action = act
    for fc in act.fcurves:  # importer uses Bezier for rotations; the game interpolates keys linearly [INFERENCE]
        for k in fc.keyframe_points:
            k.interpolation = "LINEAR"
    for o in bpy.data.objects:
        if o.type == "MESH":
            o.hide_render = o.name not in VISIBLE
    # flat materials: black for the UI body materials, texture for face/eyes/mask (Workbench TEXTURE mode)
    for o in bpy.data.objects:
        if o.type != "MESH" or o.hide_render:
            continue
        for slot in o.material_slots:
            m = slot.material
            if m is not None and o.name not in TEXTURED:
                m.diffuse_color = (0, 0, 0, 1)
    sc = bpy.context.scene
    sc.render.engine = "BLENDER_WORKBENCH"
    sc.render.resolution_x, sc.render.resolution_y, sc.render.resolution_percentage = W, H, 100
    sc.render.film_transparent = True
    sc.render.image_settings.file_format = "PNG"
    sc.render.image_settings.color_mode = "RGBA"
    sc.render.image_settings.color_depth = "16"
    sc.view_settings.view_transform = "Standard"
    sc.view_settings.look = "None"
    sc.render.fps = 30  # Blender frame axis = real time x30 (importer: frame = 1 + 30 * t / speed, speed 1.2)
    sh = sc.display.shading
    sh.light = "FLAT"
    sh.show_object_outline = False
    sh.show_cavity = False
    sh.show_shadows = False
    sh.show_specular_highlight = False
    sh.show_backface_culling = False
    sh.show_xray = False
    sc.display.render_aa = "32"
    cd = bpy.data.cameras.new("cam")
    cd.sensor_fit = "HORIZONTAL"
    cd.sensor_width = 36.0
    cd.clip_start = 1.0
    cd.clip_end = 100000.0
    cam = bpy.data.objects.new("cam", cd)
    sc.collection.objects.link(cam)
    sc.camera = cam
    return sc, cam


def render_to_array(sc, path):
    sc.render.filepath = path
    bpy.ops.render.render(write_still=True)
    img = bpy.data.images.load(path, check_existing=False)
    px = np.empty(W * H * 4, np.float32)
    img.pixels.foreach_get(px)
    bpy.data.images.remove(img)
    os.remove(path)
    return px.reshape(H, W, 4)[::-1]  # top-down


def save_rgba(arr, path):
    img = bpy.data.images.new("out", W, H, alpha=True)
    img.alpha_mode = "STRAIGHT"
    img.pixels.foreach_set(np.ascontiguousarray(arr[::-1]).reshape(-1).astype(np.float32))
    img.filepath_raw = path
    img.file_format = "PNG"
    img.save()
    bpy.data.images.remove(img)


def main():
    a = parse()
    params = json.load(open(a.params))
    frames = [int(x) for x in a.frames.split(",")] if a.frames else sorted(int(k) for k in params["frames"])
    os.makedirs(a.out, exist_ok=True)
    sc, cam = setup_scene(a.gmd, a.gap)
    sc.display.render_aa = a.aa
    sh = sc.display.shading
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    tmp = os.path.join(a.out, "_tmp.png")
    for n in frames:
        p = params["frames"][str(n)]
        t = float(p["t"])
        sc.frame_set(int(math.floor(t)), subframe=t - math.floor(t))
        c = p["cam"]
        cam.location = c["loc"]
        cam.rotation_mode = "XYZ"
        cam.rotation_euler = c["rot"]
        cam.data.lens = c["f_px"] * 36.0 / W
        cam.data.shift_x, cam.data.shift_y = c.get("shift", (0.0, 0.0))
        # pass 1: coverage (all visible meshes in one flat colour)
        sh.color_type = "SINGLE"
        sh.single_color = (1, 1, 1)
        cov = render_to_array(sc, tmp)[..., 3]
        # pass 2: the game's own colours (flat black body, black/white UI textures for face, eyes, mask)
        sh.color_type = "TEXTURE"
        col = render_to_array(sc, tmp)
        lum = col[..., :3].mean(-1)  # PNG round trip -> straight (un-premultiplied) colour
        black_side = (lum > 0.5).astype(np.float32)  # two tones over the panel
        # star-panel edge x = a*y + b (footage measurement); pixel centres at +0.5; 1 px soft edge
        e = p["edge"]
        left = np.clip(e["a"] * (yy + 0.5) + e["b"] - (xx + 0.5) + 0.5, 0.0, 1.0)
        rgb = left + (1.0 - left) * black_side
        out = np.dstack([rgb, rgb, rgb, cov])
        save_rgba(out, os.path.join(a.out, "f%03d.png" % n))
        print("frame", n, "t=%.4f" % t, "coverage px %.0f" % cov.sum(), flush=True)


main()
