"""blender -b --python convert_fbx.py -- вход.fbx выход.pmodel"""
import os
import sys

import bpy

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pmodel_export import export_pmodel  # noqa: E402

src, dst = sys.argv[sys.argv.index("--") + 1:][:2]
bpy.ops.wm.read_factory_settings(use_empty=True)
ext = os.path.splitext(src)[1].lower()
if ext == ".fbx":
    bpy.ops.import_scene.fbx(filepath=src)
elif ext == ".obj":
    bpy.ops.wm.obj_import(filepath=src)
elif ext in (".glb", ".gltf"):
    bpy.ops.import_scene.gltf(filepath=src)
else:
    raise SystemExit("неизвестный формат: " + ext)
print("PMODEL_OK %d" % export_pmodel(dst))
