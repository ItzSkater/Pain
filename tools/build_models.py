"""blender -b --python build_models.py -- папка_вывода
Собирает две модели: 3D-лого Pain и талисмана Claw'd, пишет .pmodel, .fbx и превью."""
import math
import os
import sys

import bmesh
import bpy
from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pmodel_export import export_pmodel  # noqa: E402

OUT = sys.argv[sys.argv.index("--") + 1]


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def material(name, rgb):
    m = bpy.data.materials.new(name)
    m.diffuse_color = (*rgb, 1.0)
    m.use_nodes = True
    bsdf = m.node_tree.nodes.get("Principled BSDF")
    if bsdf:
        bsdf.inputs["Base Color"].default_value = (*rgb, 1.0)
        bsdf.inputs["Roughness"].default_value = 0.35
    return m


def prism(name, outline, depth, mat, bevel=0.0):
    """Выдавленный многоугольник в плоскости XZ (лицом к -Y)."""
    mesh = bpy.data.meshes.new(name)
    bm = bmesh.new()
    front = [bm.verts.new((x, -depth / 2, z)) for x, z in outline]
    back = [bm.verts.new((x, depth / 2, z)) for x, z in outline]
    bm.faces.new(front)
    bm.faces.new(list(reversed(back)))
    n = len(outline)
    for i in range(n):
        bm.faces.new((front[(i + 1) % n], front[i], back[i], back[(i + 1) % n]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(mesh)
    bm.free()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    obj.data.materials.append(mat)
    if bevel > 0:
        mod = obj.modifiers.new("bevel", "BEVEL")
        mod.width = bevel
        mod.segments = 2
        mod.limit_method = "ANGLE"
    return obj


def box(name, size, pos, mat, bevel=0.0):
    bpy.ops.mesh.primitive_cube_add(size=1.0, location=pos)
    obj = bpy.context.active_object
    obj.name = name
    obj.scale = size
    obj.data.materials.append(mat)
    if bevel > 0:
        mod = obj.modifiers.new("bevel", "BEVEL")
        mod.width = bevel
        mod.segments = 2
    return obj


def build_logo():
    """Шесть объёмных клиньев по кругу, с зазорами и дыркой в центре — как на лого."""
    reset()
    red = material("pain_red", (0.93, 0.07, 0.07))
    outer, inner, gap = 1.0, 0.24, 0.075
    for i in range(6):
        a0 = math.radians(60 * i + 30)
        a1 = math.radians(60 * i + 90)
        mid = (a0 + a1) / 2
        p0 = Vector((math.cos(a0), math.sin(a0)))
        p1 = Vector((math.cos(a1), math.sin(a1)))
        d = Vector((math.cos(mid), math.sin(mid)))
        # клин: две точки на внешнем шестиугольнике и срезанная вершина к центру, всё сдвинуто наружу на зазор
        pts = [p0 * outer, p1 * outer, p1 * inner * 1.0, p0 * inner * 1.0]
        c = sum(pts, Vector((0, 0))) / 4
        pts = [c + (p - c) * 0.90 + d * gap for p in pts]
        prism("segment_%d" % i, [(p.x, p.y) for p in pts], 0.34, red, bevel=0.035)
    return "pain_logo"


def build_clawd():
    """Claw'd: оранжевый блок-тело, два вертикальных глаза, боковые клешни и четыре ножки."""
    reset()
    orange = material("clawd_orange", (0.85, 0.47, 0.34))
    dark = material("clawd_eye", (0.08, 0.07, 0.07))
    b = 0.03
    box("body", (2.0, 1.1, 1.2), (0, 0, 0.75), orange, b)
    for sx in (-1, 1):
        box("arm", (0.36, 0.7, 0.42), (sx * 1.18, 0, 0.72), orange, b)
        box("eye", (0.2, 0.08, 0.44), (sx * 0.46, -0.56, 0.9), dark, 0.015)
        for lx in (0.34, 0.82):
            box("leg", (0.24, 0.5, 0.42), (sx * lx, 0, 0.0), orange, b)
    return "clawd"


def render_preview(name):
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.samples = 24
    scene.cycles.device = "CPU"
    scene.render.resolution_x = 640
    scene.render.resolution_y = 480
    scene.render.film_transparent = False
    world = bpy.data.worlds.new("w")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs[0].default_value = (0.05, 0.05, 0.07, 1)
    world.node_tree.nodes["Background"].inputs[1].default_value = 1.2
    scene.world = world
    cam_data = bpy.data.cameras.new("cam")
    cam = bpy.data.objects.new("cam", cam_data)
    scene.collection.objects.link(cam)
    cam.location = (2.6, -4.4, 2.2)
    target = Vector((0, 0, 0.55 if name == "clawd" else 0.0))
    cam.rotation_euler = (target - cam.location).to_track_quat("-Z", "Y").to_euler()
    scene.camera = cam
    light_data = bpy.data.lights.new("sun", "SUN")
    light_data.energy = 4.0
    light = bpy.data.objects.new("sun", light_data)
    scene.collection.objects.link(light)
    light.rotation_euler = (math.radians(50), 0, math.radians(30))
    scene.render.filepath = os.path.join(OUT, name + "_preview.png")
    bpy.ops.render.render(write_still=True)
    for o in (cam, light):
        bpy.data.objects.remove(o)


def build_pet_parts():
    """Claw'd по частям для питомца. Земля — z=0, перёд — -Y, координаты не нормализуются."""
    parts = {}
    b = 0.03
    reset()
    orange = material("clawd_orange", (0.85, 0.47, 0.34))
    box("body", (2.0, 1.1, 1.2), (0, 0, 0.96), orange, b)
    for sx in (-1, 1):
        box("arm", (0.36, 0.7, 0.42), (sx * 1.18, 0, 0.93), orange, b)
    parts["pet_body"] = None
    yield "pet_body"
    reset()
    dark = material("clawd_eye", (0.08, 0.07, 0.07))
    for sx in (-1, 1):
        box("eye", (0.2, 0.08, 0.44), (sx * 0.46, -0.56, 1.11), dark, 0.015)
    yield "pet_eyes"
    reset()
    orange = material("clawd_orange", (0.85, 0.47, 0.34))
    # ножка: точка вращения сверху, в нуле
    box("leg", (0.24, 0.5, 0.42), (0, 0, -0.21), orange, b)
    yield "pet_leg"
    reset()
    steel = material("steel", (0.84, 0.88, 0.95))
    gold = material("gold", (0.95, 0.74, 0.22))
    wood = material("wood", (0.42, 0.26, 0.14))
    # меч: рукоять в нуле, клинок вверх
    box("handle", (0.1, 0.1, 0.3), (0, 0, 0.0), wood, 0.01)
    box("guard", (0.4, 0.12, 0.08), (0, 0, 0.19), gold, 0.01)
    box("blade", (0.15, 0.05, 0.8), (0, 0, 0.63), steel, 0.01)
    box("tip", (0.09, 0.05, 0.12), (0, 0, 1.09), steel, 0.01)
    yield "pet_sword"
    reset()
    metal = material("jet_metal", (0.62, 0.65, 0.72))
    dark = material("jet_dark", (0.16, 0.17, 0.2))
    accent = material("jet_accent", (0.95, 0.3, 0.2))
    # джетпак на спине (спина — +Y): два баллона, перемычка, сопла снизу
    for sx in (-1, 1):
        box("tank", (0.42, 0.4, 0.9), (sx * 0.36, 0.78, 1.0), metal, 0.05)
        box("cap", (0.3, 0.3, 0.12), (sx * 0.36, 0.78, 1.5), accent, 0.03)
        box("nozzle", (0.3, 0.3, 0.2), (sx * 0.36, 0.78, 0.47), dark, 0.03)
    box("bridge", (0.5, 0.22, 0.3), (0, 0.72, 1.05), dark, 0.03)
    yield "pet_jetpack"
    reset()
    # парашют: купол из долек двух цветов и стропы к телу
    c1 = material("chute_orange", (0.95, 0.42, 0.2))
    c2 = material("chute_white", (0.96, 0.95, 0.92))
    rope = material("chute_rope", (0.25, 0.22, 0.2))
    mesh = bpy.data.meshes.new("canopy")
    bm = bmesh.new()
    segs, rings, R, top = 12, 5, 1.9, 4.3
    grid = []
    for r in range(rings + 1):
        a = (math.pi / 2) * r / rings          # 0 — макушка, pi/2 — край
        row = []
        for sgm in range(segs):
            ang = 2 * math.pi * sgm / segs
            # край купола слегка волнистый, как у настоящего
            rr = R * math.sin(a) * (1.0 + (0.06 if (r == rings and sgm % 2 == 0) else 0.0))
            row.append(bm.verts.new((math.cos(ang) * rr, math.sin(ang) * rr, top - (1 - math.cos(a)) * R * 0.62)))
        grid.append(row)
    for r in range(rings):
        for sgm in range(segs):
            f = bm.faces.new((grid[r][sgm], grid[r][(sgm + 1) % segs], grid[r + 1][(sgm + 1) % segs], grid[r + 1][sgm]))
            f.material_index = sgm % 2
    bm.to_mesh(mesh)
    bm.free()
    canopy = bpy.data.objects.new("canopy", mesh)
    bpy.context.collection.objects.link(canopy)
    canopy.data.materials.append(c1)
    canopy.data.materials.append(c2)
    rim_z = top - R * 0.62
    for sgm in range(0, segs, 2):
        ang = 2 * math.pi * sgm / segs
        p0 = Vector((math.cos(ang) * R, math.sin(ang) * R, rim_z))
        p1 = Vector((math.cos(ang) * 0.75, math.sin(ang) * 0.4, 1.56))
        d = (p1 - p0)
        mid = (p0 + p1) / 2
        bpy.ops.mesh.primitive_cube_add(size=1.0, location=mid)
        o = bpy.context.active_object
        o.scale = (0.035, 0.035, d.length)
        o.rotation_euler = d.to_track_quat("Z", "Y").to_euler()
        o.data.materials.append(rope)
    yield "pet_parachute"


for name in build_pet_parts():
    meshes = [o for o in bpy.context.scene.objects if o.type == "MESH"]
    count = export_pmodel(os.path.join(OUT, name + ".pmodel"), meshes, normalize=False)
    print("BUILT %s triangles=%d" % (name, count))

for build in (build_logo, build_clawd):
    name = build()
    meshes = [o for o in bpy.context.scene.objects if o.type == "MESH"]
    count = export_pmodel(os.path.join(OUT, name + ".pmodel"), meshes)
    bpy.ops.export_scene.fbx(filepath=os.path.join(OUT, name + ".fbx"), use_mesh_modifiers=True)
    render_preview(name)
    print("BUILT %s triangles=%d" % (name, count))
