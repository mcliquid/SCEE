"""Generates the cycleway:separation answer illustrations (original artwork, SCEE-mc).

Each illustration is a flat-colour perspective view along a cycleway (red, right) next to a
road (grey, left) with the separating element in between, so that only the separator differs.
Writes <value>.svg next to this script and composeResources/drawable/cycleway_separation_<value>.xml

Usage: python generate.py [png-preview-dir]   (the PNG preview needs Pillow)
"""
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DRAWABLE = os.path.join(HERE, "..", "..", "..", "app", "src", "commonMain", "composeResources", "drawable")

SIZE = 256
# Camera: standing on the road, CAM_D metres beside the separator, looking diagonally at it
# (three-quarter view) so that the separator is in the centre and the road / cycleway are
# context to the left / right. Yaw and pitch are chosen to aim at the separator at FOCUS_Z.
CAM_H = 2.6      # camera height in m, slightly elevated
CAM_D = 1.5      # lateral distance of the camera from the separator centre in m
FOCUS_Z = 5.0    # distance along the way of the point the camera aims at
F = 230.0        # focal length in px
YAW = math.atan2(CAM_D, FOCUS_Z)
PITCH = math.atan2(CAM_H - 0.4, math.hypot(CAM_D, FOCUS_Z))
HOR = SIZE / 2 - F * math.tan(PITCH)  # horizon y in px
SHIFT_X = 24.0   # lens shift in px: less road on the left, more cycleway on the right
NEAR_CLIP = 0.3
Z_NEAR, Z_FAR = 0.0, 160.0

SKY, FAR = "#cfe6f5", "#b7c4a8"
ROAD, ROAD_LINE = "#7b7b7b", "#e8e8e8"
CYCLE, CYCLE_MARK = "#c4705f", "#f2f2f2"
WALK = "#cbc6bd"
GRASS, GRASS_DARK, GRASS_LIGHT = "#7fae55", "#5f8f3c", "#98c26c"


class Pic:
    def __init__(self, sep_l, sep_r, cycle_y=0.0):
        self.shapes = []  # (screen points, fill)
        self.sep_l, self.sep_r, self.cycle_y = sep_l, sep_r, cycle_y
        self.cam_x = (sep_l + sep_r) / 2 - CAM_D

    def cam(self, x, y, z):
        dx, dy = x - self.cam_x, y - CAM_H
        cx = dx * math.cos(YAW) - z * math.sin(YAW)
        cz = dx * math.sin(YAW) + z * math.cos(YAW)
        return cx, dy * math.cos(PITCH) + cz * math.sin(PITCH), cz * math.cos(PITCH) - dy * math.sin(PITCH)

    @staticmethod
    def screen(c):
        return SIZE / 2 - SHIFT_X + c[0] * F / c[2], SIZE / 2 - c[1] * F / c[2]

    def p(self, x, y, z):
        return self.screen(self.cam(x, y, z))

    def poly(self, pts3, fill):
        cs = [self.cam(*q) for q in pts3]
        out = []
        for i in range(len(cs)):  # clip against the near plane
            a, b = cs[i - 1], cs[i]
            if (a[2] >= NEAR_CLIP) != (b[2] >= NEAR_CLIP):
                t = (NEAR_CLIP - a[2]) / (b[2] - a[2])
                out.append(tuple(a[k] + (b[k] - a[k]) * t for k in range(3)))
            if b[2] >= NEAR_CLIP:
                out.append(b)
        if len(out) >= 3:
            self.shapes.append(([self.screen(c) for c in out], fill))

    def poly2(self, pts2, fill):
        self.shapes.append((pts2, fill))

    def strip(self, xa, xb, fill, y=0.0, za=Z_NEAR, zb=Z_FAR):
        self.poly([(xa, y, za), (xb, y, za), (xb, y, zb), (xa, y, zb)], fill)

    def xface(self, x, ya, yb, fill, za=Z_NEAR, zb=Z_FAR):
        self.poly([(x, ya, za), (x, yb, za), (x, yb, zb), (x, ya, zb)], fill)

    def box(self, xa, xb, ya, yb, za, zb, top, side, front):
        if self.cam_x < xa:
            self.xface(xa, ya, yb, side, za, zb)
        elif self.cam_x > xb:
            self.xface(xb, ya, yb, side, za, zb)
        self.poly([(xa, yb, za), (xb, yb, za), (xb, yb, zb), (xa, yb, zb)], top)
        self.poly([(xa, ya, za), (xb, ya, za), (xb, yb, za), (xa, yb, za)], front)

    def extrude(self, profile, za, zb, faces, cap):
        """profile: (x, y) points; faces: colour per edge, None = not visible"""
        n = len(profile)
        for i in range(n):
            (x1, y1), (x2, y2) = profile[i], profile[(i + 1) % n]
            if faces[i] is not None:
                self.poly([(x1, y1, za), (x2, y2, za), (x2, y2, zb), (x1, y1, zb)], faces[i])
        self.poly([(x, y, za) for x, y in profile], cap)

    def ellipse(self, xc, y, zc, rx, rz, fill, n=20):
        self.poly([(xc + rx * math.cos(2 * math.pi * i / n), y, zc + rz * math.sin(2 * math.pi * i / n)) for i in range(n)], fill)

    def cylinder(self, xc, zc, r, y0, y1, fill, top=None, n=16):
        front = [(xc + r * math.cos(math.pi + math.pi * i / n), zc + r * math.sin(math.pi + math.pi * i / n)) for i in range(n + 1)]
        self.poly([(x, y0, z) for x, z in front] + [(x, y1, z) for x, z in reversed(front)], fill)
        if top:
            self.ellipse(xc, y1, zc, r, r, top, n=2 * n)

    def ball(self, x, y, z, r, fill, n=12):
        c = self.cam(x, y, z)
        cx, cy = self.screen(c)
        rr = r * F / c[2]
        self.poly2([(cx + rr * math.cos(2 * math.pi * i / n), cy + rr * math.sin(2 * math.pi * i / n)) for i in range(n)], fill)


def base(pic, sep_fill=ROAD, sep_y=0.0):
    sl, sr, cy = pic.sep_l, pic.sep_r, pic.cycle_y
    pic.poly2([(0, 0), (SIZE, 0), (SIZE, HOR + 1), (0, HOR + 1)], SKY)
    pic.poly2([(0, HOR), (SIZE, HOR), (SIZE, SIZE), (0, SIZE)], FAR)
    pic.strip(-30, sl, ROAD)
    for k in range(40):  # road centre line dashes
        z = 2.5 + k * 6
        pic.strip(sl - 3.6, sl - 3.48, ROAD_LINE, 0, z, z + 3)
    pic.strip(sl, sr, sep_fill, sep_y)
    pic.strip(sr, sr + 2.0, CYCLE, cy)
    pic.strip(sr + 2.0, sr + 2.12, "#8a8a8a", cy + 0.08)
    pic.strip(sr + 2.12, sr + 3.6, WALK, cy + 0.08)
    pic.strip(sr + 3.6, 30, GRASS_LIGHT, cy + 0.08)
    bicycle(pic, sr + 1.0, cy)


def bicycle(pic, xc, y):
    """painted bicycle pictogram on the cycleway"""
    def ring(x, z, r, w):
        pic.ellipse(x, y, z, r, r, CYCLE_MARK, 24)
        pic.ellipse(x, y, z, r - w, r - w, CYCLE, 24)

    def bar(x1, z1, x2, z2, w=0.07):
        dx, dz = x2 - x1, z2 - z1
        length = math.hypot(dx, dz)
        nx, nz = -dz / length * w / 2, dx / length * w / 2
        pic.poly([(x1 + nx, y, z1 + nz), (x2 + nx, y, z2 + nz), (x2 - nx, y, z2 - nz), (x1 - nx, y, z1 - nz)], CYCLE_MARK)

    z0 = 7.5  # pictogram lies along the lane, front wheel further away
    ring(xc, z0, 0.32, 0.08)
    ring(xc, z0 + 1.1, 0.32, 0.08)
    bar(xc, z0, xc + 0.4, z0 + 0.5)
    bar(xc + 0.4, z0 + 0.5, xc + 0.4, z0 + 0.85)
    bar(xc + 0.4, z0 + 0.85, xc, z0 + 1.1)
    bar(xc, z0, xc, z0 + 0.6)
    bar(xc, z0 + 0.6, xc + 0.4, z0 + 0.85)
    bar(xc, z0 + 0.6, xc + 0.4, z0 + 0.5)


def positions(start, spacing, until=32.0):
    """z positions of repeated elements, far to near"""
    zs, z = [], start
    while z < until:
        zs.append(z)
        z += spacing
    return list(reversed(zs))


def clip(poly, xmin, xmax, ymin, ymax):
    def cut(pts, inside, inter):
        out = []
        for i in range(len(pts)):
            a, b = pts[i - 1], pts[i]
            if inside(b):
                if not inside(a):
                    out.append(inter(a, b))
                out.append(b)
            elif inside(a):
                out.append(inter(a, b))
        return out

    def ix(v):
        return lambda a, b: (v, a[1] + (b[1] - a[1]) * (v - a[0]) / (b[0] - a[0]))

    def iy(v):
        return lambda a, b: (a[0] + (b[0] - a[0]) * (v - a[1]) / (b[1] - a[1]), v)

    for inside, inter in [(lambda q: q[0] >= xmin, ix(xmin)), (lambda q: q[0] <= xmax, ix(xmax)),
                          (lambda q: q[1] >= ymin, iy(ymin)), (lambda q: q[1] <= ymax, iy(ymax))]:
        poly = cut(poly, inside, inter)
        if not poly:
            break
    return poly


def make_no():
    pic = Pic(0.0, 0.0)
    base(pic)
    return pic


def make_kerb():
    pic = Pic(-0.1, 0.1, cycle_y=0.18)
    base(pic, "#e2e2e2", 0.18)
    pic.strip(-0.3, -0.1, "#5f5f5f")  # shadow / gutter at the foot of the kerb
    pic.xface(-0.1, 0, 0.18, "#b4b4b4")
    for z in positions(2.0, 1.0, 40):  # kerb stone joints
        pic.poly([(-0.1, 0, z), (-0.1, 0.18, z), (-0.1, 0.18, z + 0.04), (-0.1, 0, z + 0.04)], "#6f6f6f")
    return pic


def make_bollard():
    pic = Pic(-0.25, 0.25)
    base(pic)
    for z in positions(3.1, 2.4):
        pic.cylinder(0, z, 0.1, 0, 1.0, "#3a3a3a", "#555555")
        pic.cylinder(0, z, 0.103, 0.78, 0.88, "#eeeeee")
    return pic


def make_flex_post():
    pic = Pic(-0.25, 0.25)
    base(pic)
    for z in positions(3.0, 1.8):
        pic.ellipse(0, 0.0, z, 0.16, 0.16, "#2b2b2b")
        pic.cylinder(0, z, 0.035, 0, 0.9, "#f5f5f5", "#ffffff", n=8)
        pic.cylinder(0, z, 0.037, 0.62, 0.7, "#d23c2c", n=8)
        pic.cylinder(0, z, 0.037, 0.76, 0.84, "#d23c2c", n=8)
    return pic


def make_vertical_panel():
    pic = Pic(-0.25, 0.25)
    base(pic)
    w, y0, y1 = 0.25, 0.22, 1.22
    for z in positions(3.1, 2.6):
        pic.box(-0.22, 0.22, 0, 0.12, z - 0.12, z + 0.12, "#3a3a3a", "#222222", "#2b2b2b")
        pic.box(-0.03, 0.03, 0.12, y0, z - 0.01, z + 0.01, "#444444", "#333333", "#333333")
        pic.poly([(-w / 2, y0, z), (w / 2, y0, z), (w / 2, y1, z), (-w / 2, y1, z)], "#f7f7f7")
        for k in range(-3, 5):  # diagonal red stripes
            c = k * 0.24
            stripe = clip([(-0.5, c - 0.5), (-0.5, c - 0.38), (0.5, c + 0.62), (0.5, c + 0.5)], -w / 2, w / 2, 0, y1 - y0)
            if len(stripe) >= 3:
                pic.poly([(u, y0 + v, z) for u, v in stripe], "#d0312b")
    return pic


def make_bump():
    pic = Pic(-0.25, 0.25)
    base(pic)
    prof = [(-0.2, 0), (0.2, 0), (0.11, 0.13), (-0.11, 0.13)]
    for z in positions(2.4, 1.6):
        pic.extrude(prof, z, z + 1.0, [None, None, "#e3c22a", "#b89a17"], "#c9aa1f")
        for dz in (0.3, 0.6):
            pic.poly([(-0.11, 0.13, z + dz), (0.11, 0.13, z + dz), (0.11, 0.13, z + dz + 0.15), (-0.11, 0.13, z + dz + 0.15)], "#2b2b2b")
    return pic


def make_studs():
    pic = Pic(-0.25, 0.25)
    base(pic)
    for z in positions(2.6, 0.9, 32):  # raised studs with a reflector facing the traffic
        pic.cylinder(0, z, 0.14, 0, 0.06, "#8a8a8a", "#efefef", n=10)
        pic.poly([(-0.08, 0.01, z - 0.14), (0.08, 0.01, z - 0.14), (0.08, 0.05, z - 0.14), (-0.08, 0.05, z - 0.14)], "#e8a21c")
    return pic


def make_planter():
    pic = Pic(-0.7, 0.7)
    base(pic)
    for z in positions(3.2, 3.6):
        pic.box(-0.55, 0.55, 0, 0.6, z, z + 1.8, "#6b4a2e", "#a39c92", "#bdb6ab")
        for i, dz in enumerate((1.5, 1.0, 0.5, 0.2)):
            for dx in (-0.25, 0.25):
                pic.ball(dx, 0.75, z + dz, 0.32, GRASS_DARK if i % 2 else "#4f7f2f")
                pic.ball(dx - 0.05, 0.85, z + dz - 0.05, 0.18, GRASS_LIGHT)
                pic.ball(dx, 0.95, z + dz, 0.06, "#e46aa0")
    return pic


def make_greenery():
    pic = Pic(-0.7, 0.7)
    base(pic, GRASS)
    rnd = random.Random(4)
    for z in positions(2.5, 0.7, 25):  # grass tufts
        a, b = pic.p(rnd.uniform(-0.5, 0.5), 0, z)
        s = 3.0 * 8 / z
        pic.poly2([(a - s, b), (a - s * 0.3, b - 2.4 * s), (a, b - 0.2 * s), (a + s * 0.4, b - 2.6 * s), (a + s, b)], GRASS_DARK)
    return pic


def make_hedge():
    pic = Pic(-0.7, 0.7)
    base(pic, GRASS)
    pic.extrude([(-0.55, 0), (0.55, 0), (0.5, 1.15), (-0.5, 1.15)], 2.6, Z_FAR,
                [None, None, "#4c7f2c", "#3d6b22"], "#355f1d")
    rnd = random.Random(7)
    for z in positions(3.0, 0.8, 30):  # leafy texture
        pic.ball(-0.52, rnd.uniform(0.3, 1.0), z, 0.15, "#2f5719")
        pic.ball(rnd.uniform(-0.4, 0.4), 1.15, z, 0.14, "#5f9a38")
    return pic


def make_tree_row():
    pic = Pic(-0.7, 0.7)
    base(pic, GRASS)
    for z in positions(7.5, 6.0, 60):
        pic.cylinder(0, z, 0.15, 0, 1.6, "#6b4a2e")
        pic.ball(0, 2.25, z, 0.85, "#3f7323")
        pic.ball(-0.25, 2.45, z, 0.5, "#5a9436")
    return pic


def make_fence():
    pic = Pic(-0.25, 0.25)
    base(pic)
    for z in positions(2.6, 2.5, 60):
        for k in range(1, 10):  # vertical bars
            zz = z + k * 0.25
            pic.poly([(0, 0.05, zz), (0, 1.2, zz), (0, 1.2, zz + 0.035), (0, 0.05, zz + 0.035)], "#2f4f3a")
        pic.box(-0.04, 0.04, 0, 1.3, z - 0.04, z + 0.04, "#2a3f30", "#1f3326", "#24392b")
    pic.xface(0, 1.12, 1.2, "#24392b", Z_NEAR, 120)
    pic.xface(0, 0.05, 0.12, "#24392b", Z_NEAR, 120)
    return pic


def make_guard_rail():
    pic = Pic(-0.25, 0.25)
    base(pic)
    for z in positions(2.8, 2.0, 60):
        pic.box(0.02, 0.12, 0, 0.7, z, z + 0.1, "#7c7f82", "#5d6063", "#6b6e71")
    pic.xface(-0.03, 0.4, 0.8, "#c3c7cb", Z_NEAR, 120)
    pic.xface(-0.031, 0.4, 0.45, "#5d6165", Z_NEAR, 120)
    for ya, yb, col in ((0.52, 0.56, "#7f8387"), (0.64, 0.68, "#7f8387"), (0.74, 0.78, "#d3d6d9")):
        pic.xface(-0.031, ya, yb, col, Z_NEAR, 120)
    return pic


def make_jersey_barrier():
    pic = Pic(-0.25, 0.25)
    base(pic)
    prof = [(-0.3, 0), (0.3, 0), (0.28, 0.08), (0.12, 0.3), (0.08, 0.81), (-0.08, 0.81), (-0.12, 0.3), (-0.28, 0.08)]
    faces = [None, None, None, None, "#d6d3cd", None, "#b2aea6", "#9c988f"]
    pic.extrude(prof, 2.6, 60, faces, "#c2beb6")
    for z in positions(5.6, 3.0, 40):  # segment joints
        pic.poly([(x, y, z) for x, y in prof[4:]] + [(x, y, z + 0.04) for x, y in reversed(prof[4:])], "#8d8981")
    return pic


def make_ditch():
    pic = Pic(-0.9, 0.9)
    base(pic, GRASS)
    pic.poly([(-0.9, 0, Z_NEAR), (-0.15, -0.55, Z_NEAR), (-0.15, -0.55, Z_FAR), (-0.9, 0, Z_FAR)], "#4f7f2f")
    pic.poly([(0.15, -0.55, Z_NEAR), (0.9, 0, Z_NEAR), (0.9, 0, Z_FAR), (0.15, -0.55, Z_FAR)], GRASS_LIGHT)
    pic.strip(-0.15, 0.15, "#5d86a8", -0.55)
    pic.strip(-0.08, 0.02, "#86aecb", -0.549)
    return pic


VALUES = ["no", "kerb", "bollard", "flex_post", "vertical_panel", "bump", "studs", "planter",
          "greenery", "hedge", "tree_row", "fence", "guard_rail", "jersey_barrier", "ditch"]


def fmt(v):
    s = f"{v:.1f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def path_d(pts):
    return "M" + "L".join(f"{fmt(x)},{fmt(y)}" for x, y in pts) + "Z"


def merged(shapes):
    out = []
    for pts, fill in shapes:
        if out and out[-1][1] == fill:
            out[-1][0].append(pts)
        else:
            out.append(([pts], fill))
    return out


def to_svg(pic):
    rows = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{SIZE}" height="{SIZE}" viewBox="0 0 {SIZE} {SIZE}">']
    for polys, fill in merged(pic.shapes):
        rows.append(f'  <path fill="{fill}" d="{"".join(path_d(p) for p in polys)}"/>')
    rows.append("</svg>\n")
    return "\n".join(rows)


def to_vector(pic):
    rows = ['<vector xmlns:android="http://schemas.android.com/apk/res/android"',
            '    android:width="128dp"',
            '    android:height="128dp"',
            f'    android:viewportWidth="{SIZE}.0"',
            f'    android:viewportHeight="{SIZE}.0">']
    for polys, fill in merged(pic.shapes):
        rows.append(f'    <path\n        android:pathData="{"".join(path_d(p) for p in polys)}"\n        android:fillColor="{fill}"/>')
    rows.append("</vector>\n")
    return "\n".join(rows)


def to_png(pic, path, size):
    from PIL import Image, ImageDraw
    ss = 4
    img = Image.new("RGB", (SIZE * ss, SIZE * ss), "white")
    d = ImageDraw.Draw(img)
    for pts, fill in pic.shapes:
        d.polygon([(x * ss, y * ss) for x, y in pts], fill=fill)
    img.resize((size, size), Image.LANCZOS).save(path)


if __name__ == "__main__":
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    for v in VALUES:
        pic = globals()["make_" + v]()
        with open(os.path.join(HERE, v + ".svg"), "w", newline="\n") as f:
            f.write(to_svg(pic))
        with open(os.path.join(DRAWABLE, f"cycleway_separation_{v}.xml"), "w", newline="\n") as f:
            f.write(to_vector(pic))
        if preview:
            os.makedirs(preview, exist_ok=True)
            to_png(pic, os.path.join(preview, v + ".png"), 512)
