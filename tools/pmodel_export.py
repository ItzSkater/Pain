"""Общий экспорт для Pain: сцена Blender → .pmodel (треугольники с нормалями и цветом).

Формат текстовый: строка на вершину «x y z nx ny nz r g b», три строки подряд — треугольник.
Модель центрируется и приводится к единичному размеру, оси переводятся в майнкрафтовские (Y вверх).
"""
import bpy
from mathutils import Vector


def export_pmodel(path, objects=None, normalize=True):
    deps = bpy.context.evaluated_depsgraph_get()
    tris = []
    for obj in objects or [o for o in bpy.context.scene.objects if o.type == "MESH"]:
        ev = obj.evaluated_get(deps)
        mesh = ev.to_mesh()
        mesh.calc_loop_triangles()
        world = ev.matrix_world
        normal_mat = world.to_3x3().inverted().transposed()
        for tri in mesh.loop_triangles:
            color = (0.8, 0.8, 0.8)
            if mesh.materials and tri.material_index < len(mesh.materials) and mesh.materials[tri.material_index]:
                color = tuple(mesh.materials[tri.material_index].diffuse_color[:3])
            n = (normal_mat @ tri.normal).normalized()
            for vi in tri.vertices:
                p = world @ mesh.vertices[vi].co
                tris.append((p.copy(), n.copy(), color))
        ev.to_mesh_clear()
    if not tris:
        raise RuntimeError("в файле нет полигонов")
    lo = Vector((min(t[0].x for t in tris), min(t[0].y for t in tris), min(t[0].z for t in tris)))
    hi = Vector((max(t[0].x for t in tris), max(t[0].y for t in tris), max(t[0].z for t in tris)))
    center = (lo + hi) / 2
    scale = 1.0 / max((hi - lo).x, (hi - lo).y, (hi - lo).z, 1e-6)
    if not normalize:
        # части для анимации: оставляем координаты как в сцене, чтобы совпадали точки вращения
        center = Vector((0, 0, 0))
        scale = 1.0
    with open(path, "w") as f:
        f.write("# pain model, %d triangles\n" % (len(tris) // 3))
        for p, n, c in tris:
            q = (p - center) * scale
            # Blender: Z вверх, -Y вперёд → Minecraft: Y вверх
            f.write("%.5f %.5f %.5f %.4f %.4f %.4f %.3f %.3f %.3f\n" % (q.x, q.z, -q.y, n.x, n.z, -n.y, c[0], c[1], c[2]))
    return len(tris) // 3
