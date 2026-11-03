#!/usr/bin/env python3
"""Gera os ícones de clima (VectorDrawables do Android + SVGs para pré-visualização).

Tudo é desenhado do zero aqui, com geometria própria (círculos, arcos e linhas).
Uso: python3 tools/gen_icons.py
"""
import math, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DRAWABLE = os.path.join(ROOT, "app/src/main/res/drawable")
SVG_DIR = os.path.join(ROOT, "docs/icons")
V = 48  # viewport


def f(x):
    s = f"{x:.2f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


# ---------- geometria ----------
def circle_path(cx, cy, r):
    return f"M{f(cx - r)},{f(cy)}a{f(r)},{f(r)} 0 1,0 {f(2 * r)},0a{f(r)},{f(r)} 0 1,0 {f(-2 * r)},0z"


def inter(c1, c2, upper=True):
    (x1, y1, r1), (x2, y2, r2) = c1, c2
    d = math.hypot(x2 - x1, y2 - y1)
    a = (r1 * r1 - r2 * r2 + d * d) / (2 * d)
    h = math.sqrt(max(r1 * r1 - a * a, 0))
    mx, my = x1 + a * (x2 - x1) / d, y1 + a * (y2 - y1) / d
    p1 = (mx + h * (y2 - y1) / d, my - h * (x2 - x1) / d)
    p2 = (mx - h * (y2 - y1) / d, my + h * (x2 - x1) / d)
    return min(p1, p2, key=lambda p: p[1]) if upper else max(p1, p2, key=lambda p: p[1])


def arc(c, p_from, p_to, cw):
    cx, cy, r = c
    a0 = math.atan2(p_from[1] - cy, p_from[0] - cx)
    a1 = math.atan2(p_to[1] - cy, p_to[0] - cx)
    sweep = (a1 - a0) % (2 * math.pi) if cw else (a0 - a1) % (2 * math.pi)
    large = 1 if sweep > math.pi else 0
    return f"A{f(r)},{f(r)} 0 {large},{1 if cw else 0} {f(p_to[0])},{f(p_to[1])}"


def cloud_circles(x0, yb, s):
    L = (x0 + 8 * s, yb - 8 * s, 8 * s)
    T = (x0 + 18 * s, yb - 15 * s, 10 * s)
    R = (x0 + 30 * s, yb - 9 * s, 9 * s)
    return L, T, R


def cloud_path(x0, yb, s):
    L, T, R = cloud_circles(x0, yb, s)
    p_rt = inter(R, T)
    p_tl = inter(T, L)
    start = (L[0], yb)
    end_r = (R[0], yb)
    return (f"M{f(start[0])},{f(start[1])}H{f(end_r[0])}" + arc(R, end_r, p_rt, False)
            + arc(T, p_rt, p_tl, False) + arc(L, p_tl, start, False) + "z"), yb - 25 * s


def crescent_path(cx, cy, R, dx, dy, r):
    O = (cx, cy, R)
    C = (cx + dx, cy + dy, r)
    pts = [inter(O, C, True), inter(O, C, False)]
    A, B = pts
    # arco externo: o que fica fora do recorte
    def mid(c, p0, p1, cw):
        a0 = math.atan2(p0[1] - c[1], p0[0] - c[0]); a1 = math.atan2(p1[1] - c[1], p1[0] - c[0])
        sw = (a1 - a0) % (2 * math.pi) if cw else -((a0 - a1) % (2 * math.pi))
        am = a0 + sw / 2
        return (c[0] + c[2] * math.cos(am), c[1] + c[2] * math.sin(am))
    outside = lambda p: math.hypot(p[0] - C[0], p[1] - C[1]) > C[2]
    inside = lambda p: math.hypot(p[0] - O[0], p[1] - O[1]) < O[2]
    cw1 = outside(mid(O, A, B, True))
    d = f"M{f(A[0])},{f(A[1])}" + arc(O, A, B, cw1)
    cw2 = inside(mid(C, B, A, True))
    d += arc(C, B, A, cw2) + "z"
    return d


# ---------- modelo de forma ----------
def lin(x1, y1, x2, y2, c1, c2):
    return ("lin", x1, y1, x2, y2, c1, c2)


def rad(cx, cy, r, c1, c2):
    return ("rad", cx, cy, r, c1, c2)


class Shape:
    def __init__(self, d, fill=None, stroke=None, width=0, alpha=1.0):
        self.d, self.fill, self.stroke, self.width, self.alpha = d, fill, stroke, width, alpha


def cloud_shapes(x0, yb, s, top="#FFFFFF", bottom="#BFCBE0"):
    d, ytop = cloud_path(x0, yb, s)
    shapes = [Shape(d, fill=lin(0, ytop, 0, yb, top, bottom))]
    # brilho suave no topo para dar volume
    L, T, R = cloud_circles(x0, yb, s)
    hl = circle_path(T[0] - 1.5 * s, T[1] - 2.5 * s, 4.2 * s)
    shapes.append(Shape(hl, fill="#FFFFFF", alpha=0.35))
    return shapes


def sun_shapes(cx, cy, r, rays=True):
    sh = []
    if rays:
        for i in range(8):
            a = i * math.pi / 4
            r1, r2 = r + 3.2, r + 6.4
            sh.append(Shape(f"M{f(cx + r1 * math.cos(a))},{f(cy + r1 * math.sin(a))}L{f(cx + r2 * math.cos(a))},{f(cy + r2 * math.sin(a))}",
                            stroke="#FFC12E", width=2.4))
    sh.append(Shape(circle_path(cx, cy, r), fill=rad(cx - r * 0.3, cy - r * 0.35, r * 1.5, "#FFF3A6", "#FFA81C")))
    return sh


def moon_shapes(cx, cy, R, dx, dy, r):
    return [Shape(crescent_path(cx, cy, R, dx, dy, r), fill=lin(cx - R, cy - R, cx + R, cy + R, "#8EC0FF", "#3563E0"))]


def dot(cx, cy, r, color="#FFFFFF", alpha=1.0):
    return Shape(circle_path(cx, cy, r), fill=color, alpha=alpha)


ICONS = {}
ICONS["ic_w_clear_day"] = sun_shapes(24, 24, 10)
ICONS["ic_w_clear_night"] = moon_shapes(23, 24, 16, 8, -6.5, 13.2) + [dot(36, 34, 1.5, "#DCE9FF", .9), dot(40, 22, 1.1, "#DCE9FF", .8)]
ICONS["ic_w_partly_day"] = sun_shapes(17, 17, 7.2) + cloud_shapes(11, 41, 0.86)
ICONS["ic_w_partly_night"] = moon_shapes(17, 17, 11.5, 6.5, -5, 9.6) + cloud_shapes(7.5, 42, 0.86)
back = cloud_shapes(2, 29, .62, "#E7EDF7", "#8E9DB8")
ICONS["ic_w_cloudy"] = back + cloud_shapes(8, 41, .95, "#FFFFFF", "#B7C4DA")
rain = []
for x in (15, 24, 33):
    rain.append(Shape(f"M{f(x + 1.6)},{f(35.5)}L{f(x - 1.6)},{f(42)}", stroke="#4DA3FF", width=2.8))
ICONS["ic_w_rain"] = cloud_shapes(4.5, 33, 1.0, "#F2F6FC", "#9FB0CB") + rain
bolt = "M26,28.5L19,39H24L22,46L31,35H26.2L28.5,28.5Z"
ICONS["ic_w_storm"] = cloud_shapes(4.5, 31, 1.0, "#D3DBE8", "#7C8CA8") + [Shape(bolt, fill=lin(0, 28, 0, 46, "#FFE34D", "#FF9F1A"))]
fog = [Shape("M9,34.5H33", stroke="#C5D0E2", width=2.8), Shape("M15,39.5H39", stroke="#C5D0E2", width=2.8), Shape("M9,44.5H29", stroke="#C5D0E2", width=2.8)]
ICONS["ic_w_fog"] = cloud_shapes(5, 29, .95, "#F2F6FC", "#A8B7D0") + fog
snow = []
for x, y in ((14, 38.5), (24, 42.5), (34, 38.5)):
    snow.append(dot(x, y, 2.3, "#FFFFFF"))
ICONS["ic_w_snow"] = cloud_shapes(4.5, 33, 1.0, "#E6EDF8", "#9DB0CF") + snow


# ---------- saída: VectorDrawable ----------
def vd_color(c, alpha):
    if alpha >= 1:
        return c
    a = int(round(alpha * 255))
    return f"#{a:02X}{c[1:]}"


def to_vd(shapes, size=V, vp=V):
    o = [f'<vector xmlns:android="http://schemas.android.com/apk/res/android"',
         f'    xmlns:aapt="http://schemas.android.com/aapt"',
         f'    android:width="{size}dp" android:height="{size}dp"',
         f'    android:viewportWidth="{vp}" android:viewportHeight="{vp}">']
    for s in shapes:
        attrs = f'android:pathData="{s.d}"'
        if s.stroke:
            attrs += f'\n        android:strokeColor="{s.stroke}" android:strokeWidth="{f(s.width)}" android:strokeLineCap="round"'
        if isinstance(s.fill, str):
            attrs += f'\n        android:fillColor="{vd_color(s.fill, s.alpha)}"'
        if isinstance(s.fill, tuple):
            g = s.fill
            o.append(f"    <path\n        {attrs}>")
            o.append('        <aapt:attr name="android:fillColor">')
            if g[0] == "lin":
                o.append(f'            <gradient android:type="linear" android:startX="{f(g[1])}" android:startY="{f(g[2])}" android:endX="{f(g[3])}" android:endY="{f(g[4])}">')
            else:
                o.append(f'            <gradient android:type="radial" android:centerX="{f(g[1])}" android:centerY="{f(g[2])}" android:gradientRadius="{f(g[3])}">')
            o.append(f'                <item android:offset="0" android:color="{g[-2]}" />')
            o.append(f'                <item android:offset="1" android:color="{g[-1]}" />')
            o.append("            </gradient>\n        </aapt:attr>\n    </path>")
            continue
        o.append(f"    <path\n        {attrs} />")
    o.append("</vector>")
    return "\n".join(o) + "\n"


def to_svg(shapes, name):
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {V} {V}" width="{V*4}" height="{V*4}">', "<defs>"]
    body = []
    for i, s in enumerate(shapes):
        attrs = f'd="{s.d}"'
        if s.stroke:
            attrs += f' stroke="{s.stroke}" stroke-width="{f(s.width)}" stroke-linecap="round" fill="none"'
        elif isinstance(s.fill, str):
            attrs += f' fill="{s.fill}" fill-opacity="{s.alpha}"'
        elif isinstance(s.fill, tuple):
            g = s.fill; gid = f"{name}_g{i}"
            if g[0] == "lin":
                o.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{f(g[1])}" y1="{f(g[2])}" x2="{f(g[3])}" y2="{f(g[4])}"><stop offset="0" stop-color="{g[-2]}"/><stop offset="1" stop-color="{g[-1]}"/></linearGradient>')
            else:
                o.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{f(g[1])}" cy="{f(g[2])}" r="{f(g[3])}"><stop offset="0" stop-color="{g[-2]}"/><stop offset="1" stop-color="{g[-1]}"/></radialGradient>')
            attrs += f' fill="url(#{gid})"'
        body.append(f"<path {attrs}/>")
    o.append("</defs>")
    o += body + ["</svg>"]
    return "\n".join(o) + "\n"


if __name__ == "__main__":
    os.makedirs(DRAWABLE, exist_ok=True); os.makedirs(SVG_DIR, exist_ok=True)
    for name, shapes in ICONS.items():
        open(os.path.join(DRAWABLE, name + ".xml"), "w").write(to_vd(shapes))
        open(os.path.join(SVG_DIR, name + ".svg"), "w").write(to_svg(shapes, name))
    print("ok", len(ICONS))
