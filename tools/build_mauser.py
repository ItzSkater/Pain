"""blender -b --python build_mauser.py -- папка_вывода
Маузер: толстый белый кот с полосатой шапочкой, хвостом, пятном на боку и одной полосатой лапой.
Части для анимации в тех же единицах, что и Claw'd: земля z=0, перёд — -Y."""
import math
import os
import sys

import bmesh
import bpy
from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pmodel_export import export_pmodel  # noqa: E402

OUT = sys.argv[sys.argv.index("--") + 1]
WHITE = (0.96, 0.95, 0.92)
TABBY = (0.45, 0.39, 0.33)
STRIPE = (0.2, 0.17, 0.15)
PINK = (0.96, 0.62, 0.64)
GREEN = (0.62, 0.66, 0.3)
BLACK = (0.06, 0.05, 0.05)
MATS = {}


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    MATS.clear()


def mat(rgb):
    if rgb not in MATS:
        m = bpy.data.materials.new("m")
        m.diffuse_color = (*rgb, 1.0)
        m.use_nodes = True
        m.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (*rgb, 1.0)
        MATS[rgb] = m
    return MATS[rgb]


def blob(scale, pos, rgb, rot=(0, 0, 0), segs=14, rings=9):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=segs, ring_count=rings, radius=1.0, location=pos)
    o = bpy.context.active_object
    o.scale = scale
    o.rotation_euler = [math.radians(a) for a in rot]
    o.data.materials.append(mat(rgb))
    return o


def cone(r, h, pos, rgb, rot=(0, 0, 0), verts=10):
    bpy.ops.mesh.primitive_cone_add(vertices=verts, radius1=r, radius2=0.0, depth=h, location=pos)
    o = bpy.context.active_object
    o.rotation_euler = [math.radians(a) for a in rot]
    o.data.materials.append(mat(rgb))
    return o


def cap(scale, pos, rgb, keep):
    """Часть сферы (шапочка): оставляем только вершины, для которых keep(x, y, z) истинно (в единичной сфере)."""
    mesh = bpy.data.meshes.new("cap")
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=18, v_segments=12, radius=1.0)
    bmesh.ops.delete(bm, geom=[v for v in bm.verts if not keep(v.co.x, v.co.y, v.co.z)], context="VERTS")
    bm.to_mesh(mesh)
    bm.free()
    o = bpy.data.objects.new("cap", mesh)
    bpy.context.collection.objects.link(o)
    o.location = pos
    o.scale = scale
    o.data.materials.append(mat(rgb))
    return o


HEAD = Vector((0.0, -1.22, 1.42))
HEAD_R = (0.7, 0.62, 0.6)


def on_head(x, z, lift=1.0):
    """Точка на передней поверхности головы для смещения (x, z) от её центра."""
    k = 1.0 - (x / HEAD_R[0]) ** 2 - (z / HEAD_R[2]) ** 2
    y = -HEAD_R[1] * math.sqrt(max(k, 0.0)) * lift
    return (HEAD.x + x, HEAD.y + y, HEAD.z + z)


def build_body():
    reset()
    # толстое тело: широкое, длинное, низкое; пузо почти до земли
    blob((1.08, 1.3, 0.8), (0, 0.08, 0.88), WHITE, segs=18, rings=12)
    blob((0.8, 0.7, 0.55), (0, -0.55, 1.0), WHITE)                    # грудь
    blob(HEAD_R, HEAD, WHITE, segs=18, rings=12)
    # полосатая шапочка: верх и затылок головы, спереди спускается до глаз, мордочка белая
    cap((HEAD_R[0] * 1.03, HEAD_R[1] * 1.03, HEAD_R[2] * 1.03), HEAD, TABBY, lambda x, y, z: z > (0.3 if y < 0 else 0.3 - 0.75 * y))
    for sx in (-1, 1):
        cone(0.24, 0.5, (sx * 0.4, HEAD.y + 0.05, HEAD.z + 0.66), TABBY, rot=(8, sx * 16, 0))
        cone(0.13, 0.3, (sx * 0.4, HEAD.y - 0.03, HEAD.z + 0.6), PINK, rot=(8, sx * 16, 0))
        # тёмные полоски на шапочке
        blob((0.05, 0.3, 0.05), (sx * 0.22, HEAD.y + 0.1, HEAD.z + 0.6), STRIPE, rot=(18, 0, sx * 12), segs=8, rings=5)
        # белые щёчки-подушечки у носа
        blob((0.17, 0.12, 0.12), on_head(sx * 0.13, -0.2, 0.98), WHITE, segs=10, rings=6)
    blob((0.05, 0.3, 0.05), (0, HEAD.y + 0.08, HEAD.z + 0.63), STRIPE, rot=(18, 0, 0), segs=8, rings=5)
    blob((0.07, 0.05, 0.05), on_head(0.0, -0.1, 1.05), PINK, segs=8, rings=5)       # розовый нос
    # пятно на боку и полосатый хвост с тёмными кольцами
    blob((0.1, 0.34, 0.26), (1.02, 0.25, 1.0), TABBY, segs=10, rings=6)
    tail = [((0, 1.25, 1.0), 0.2), ((0, 1.55, 1.25), 0.19), ((0, 1.72, 1.6), 0.17), ((0, 1.72, 1.95), 0.15), ((0, 1.6, 2.2), 0.12)]
    for i, (p, r) in enumerate(tail):
        blob((r, r, r * 1.25), p, STRIPE if i % 2 == 1 else TABBY, segs=10, rings=6)
    return "mauser_body"


def build_eyes():
    reset()
    # сонные полуприкрытые глаза: низкие и широкие, зелёные с тёмным зрачком
    for sx in (-1, 1):
        blob((0.11, 0.035, 0.055), on_head(sx * 0.25, 0.04, 0.985), GREEN, segs=10, rings=6)
        blob((0.04, 0.03, 0.05), on_head(sx * 0.25, 0.04, 1.02), BLACK, segs=8, rings=5)
    return "mauser_eyes"


def build_leg(rgb, name):
    reset()
    # короткая толстая лапа: точка вращения сверху в нуле
    bpy.ops.mesh.primitive_cylinder_add(vertices=10, radius=0.2, depth=0.36, location=(0, 0, -0.18))
    bpy.context.active_object.data.materials.append(mat(rgb))
    blob((0.23, 0.27, 0.11), (0, -0.05, -0.36), WHITE, segs=10, rings=6)   # белый «носочек»
    return name


def render_preview(name):
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.samples = 24
    scene.cycles.device = "CPU"
    scene.render.resolution_x = 900
    scene.render.resolution_y = 520
    world = bpy.data.worlds.new("w")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs[0].default_value = (0.2, 0.22, 0.27, 1)
    world.node_tree.nodes["Background"].inputs[1].default_value = 1.3
    scene.world = world
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    scene.collection.objects.link(cam)
    cam.location = (3.6, -5.6, 2.6)
    cam.rotation_euler = (Vector((0, -0.1, 0.95)) - cam.location).to_track_quat("-Z", "Y").to_euler()
    scene.camera = cam
    sun = bpy.data.objects.new("sun", bpy.data.lights.new("sun", "SUN"))
    sun.data.energy = 3.0
    scene.collection.objects.link(sun)
    sun.rotation_euler = (math.radians(50), 0, math.radians(25))
    scene.render.filepath = os.path.join(OUT, name + "_preview.png")
    bpy.ops.render.render(write_still=True)


def export(name):
    meshes = [o for o in bpy.context.scene.objects if o.type == "MESH"]
    print("BUILT %s triangles=%d" % (name, export_pmodel(os.path.join(OUT, name + ".pmodel"), meshes, normalize=False)))


export(build_eyes())
export(build_leg(WHITE, "mauser_leg"))
export(build_leg(TABBY, "mauser_leg_tabby"))
export(build_body())
# превью целиком: тело + глаза + четыре лапы
for sx, y, rgb in ((-0.5, -0.62, WHITE), (0.5, -0.62, WHITE), (-0.55, 0.72, TABBY), (0.55, 0.72, WHITE)):
    bpy.ops.mesh.primitive_cylinder_add(vertices=10, radius=0.2, depth=0.36, location=(sx, y, 0.24))
    bpy.context.active_object.data.materials.append(mat(rgb))
    blob((0.23, 0.27, 0.11), (sx, y - 0.05, 0.06), WHITE, segs=10, rings=6)
for sx in (-1, 1):
    blob((0.11, 0.035, 0.055), on_head(sx * 0.25, 0.04, 0.985), GREEN, segs=10, rings=6)
    blob((0.04, 0.03, 0.05), on_head(sx * 0.25, 0.04, 1.02), BLACK, segs=8, rings=5)
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(OUT, "mauser.blend"))
bpy.ops.export_scene.fbx(filepath=os.path.join(OUT, "mauser.fbx"))
render_preview("mauser")
