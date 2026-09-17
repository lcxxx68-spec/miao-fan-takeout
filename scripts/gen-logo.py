# -*- coding: utf-8 -*-
"""Generate the miao-fan brand mark used by every front end.

The mark is a warm orange rounded square (corner radius 25%) with the
character "miao" (as in miao fan) centred in it, drawn in the rounded CJK
face YouYuan.  Rounded is the point: YouYuan is monolinear with circular
terminals, and the glyph is stroked a little so it still reads at the 30px
the mini program uses instead of looking wispy.

This one script writes every brand asset -- admin console (svg, png and
favicon), mini program (static/*) and backend (report logo, dish tiles) --
so the mark stays identical everywhere.

Usage:
    python scripts/gen-logo.py            # regenerate all assets
    python scripts/gen-logo.py --check    # verify existing assets only

--check re-derives every file and diffs it against what is on disk, and
also re-parses the emitted svg path to compare it with the font outline and
with the pixels Pillow rendered.  That is how the placement and the weight
are verified without looking at the pictures.
"""

import argparse
import hashlib
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]

BRAND_ORANGE = "#F97316"
GLYPH = "\u79d2"           # "miao" (second)
GLYPH_RATIO = 0.62         # glyph size / badge size (largest side, stroke included)
# stroke expansion (per side) / em of the glyph.  YouYuan is a light face
# (stems ~5% of the glyph height); 0.024 lifts it to ~10%, i.e. a rounded
# medium weight that still reads at the 30px the mini program uses.
STROKE_RATIO = 0.024
RADIUS_RATIO = 0.25        # rounded-square corner radius / badge size
VIEW = 64                  # svg viewBox size
SS = 4                     # supersampling factor for the bitmaps
GLYPH_EM = 2048            # em size used to rasterise the glyph mask

FONT_CANDIDATES = [
    r"C:\Windows\Fonts\SIMYOU.TTF",   # YouYuan: the rounded one we want
    r"C:\Windows\Fonts\msyh.ttc",     # Microsoft YaHei
    r"C:\Windows\Fonts\simhei.ttf",   # SimHei
]

# (path relative to the repo root, pixel size, also write a multi size .ico)
ASSETS = [
    ("nginx/html/miaofan/assets/logo.svg", None),
    ("nginx/html/miaofan/assets/favicon.svg", None),
    ("nginx/html/miaofan/assets/logo.png", 256),
    ("nginx/html/miaofan/assets/logo-text.png", 256),
    ("nginx/html/miaofan/assets/favicon.png", 64),
    ("nginx/html/miaofan/favicon.ico", 256),
    ("frontend-miniprogram/static/logo.png", 256),
    ("frontend-miniprogram/static/logo_ruiji.png", 256),
    ("frontend-miniprogram/static/splash.png", 240),
    ("backend/miao-fan-takeout/mf-server/src/main/resources/static/img/brand/logo-cup.png", 256),
    ("backend/miao-fan-takeout/mf-server/src/main/resources/static/img/brand/logo-text.png", 256),
    # dish/set meal tiles shown when a dish has no photo of its own
    ("backend/miao-fan-takeout/mf-server/src/main/resources/static/img/dish/*.png", 480),
    ("backend/miao-fan-takeout/mf-server/src/main/resources/static/img/setmeal/*.png", 480),
]

ICO_SIZES = [16, 24, 32, 48, 64, 128, 256]
LEGACY_TILE = "d68d23ad42ec3502c22a24973bd65dff"   # the old orange cup tile


def pick_font():
    for path in FONT_CANDIDATES:
        if Path(path).exists():
            return path
    raise SystemExit("no usable CJK font found in %s" % FONT_CANDIDATES)


FONT_PATH = pick_font()


def glyph_mask(em=GLYPH_EM, stroke_ratio=STROKE_RATIO):
    """Rasterise the glyph (stroke included) and crop it to its ink bbox.

    Pillow grows the outline by `stroke_width` on every side, so
    `stroke_width = STROKE_RATIO * em` equals a `STROKE_RATIO * upem` radius
    in font units -- exactly what the svg stroke below reproduces.
    """
    font = ImageFont.truetype(FONT_PATH, em)
    canvas = int(em * 1.8)
    mask = Image.new("L", (canvas, canvas), 0)
    ImageDraw.Draw(mask).text(
        (canvas / 2, canvas / 2), GLYPH, font=font, fill=255, anchor="mm",
        stroke_width=int(round(stroke_ratio * em)), stroke_fill=255,
    )
    # crop the *50% coverage* box: the faint antialias fringe would otherwise
    # make the glyph a hair smaller and off centre once it is scaled up
    box = mask.point(lambda value: 255 if value > 127 else 0).getbbox()
    if box is None:
        raise SystemExit("the font %s has no glyph for %r" % (FONT_PATH, GLYPH))
    return mask.crop(box)


def build_mark(size, stroke_ratio=STROKE_RATIO):
    """Render the full badge (rounded square + glyph) at `size` pixels."""
    mask = glyph_mask(stroke_ratio=stroke_ratio)
    big = size * SS
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    ImageDraw.Draw(img).rounded_rectangle(
        (0, 0, big - 1, big - 1), radius=big * RADIUS_RATIO, fill=BRAND_ORANGE,
    )

    target = GLYPH_RATIO * big
    scale = target / max(mask.size)
    glyph = mask.resize(
        (max(1, round(mask.width * scale)), max(1, round(mask.height * scale))),
        Image.Resampling.LANCZOS,
    )
    layer = Image.new("RGBA", glyph.size, (255, 255, 255, 0))
    layer.putalpha(glyph)
    img.alpha_composite(layer, ((big - glyph.width) // 2, (big - glyph.height) // 2))
    return img.resize((size, size), Image.Resampling.LANCZOS)


def glyph_transform(view=VIEW):
    """Map the glyph bbox (font units) onto the badge, keeping it centered."""
    from fontTools.misc.transform import Transform
    from fontTools.pens.boundsPen import BoundsPen
    from fontTools.ttLib import TTFont

    font = TTFont(FONT_PATH)
    upem = font["head"].unitsPerEm
    name = font.getBestCmap()[ord(GLYPH)]
    glyph_set = font.getGlyphSet()
    pen = BoundsPen(glyph_set)
    glyph_set[name].draw(pen)
    x0, y0, x1, y1 = pen.bounds

    radius = STROKE_RATIO * upem                       # stroke radius, font units
    width = (x1 - x0) + 2 * radius
    height = (y1 - y0) + 2 * radius
    scale = (GLYPH_RATIO * view) / max(width, height)  # glyph box -> user units
    cx, cy = (x0 + x1) / 2.0, (y0 + y1) / 2.0
    transform = Transform(
        scale, 0, 0, -scale,                           # flip y: font units are y-up
        view / 2.0 - scale * cx,
        view / 2.0 + scale * cy,
    )
    return glyph_set, name, transform, 2 * scale * radius


def build_svg():
    from fontTools.pens.svgPathPen import SVGPathPen
    from fontTools.pens.transformPen import TransformPen

    glyph_set, name, transform, stroke = glyph_transform()
    pen = SVGPathPen(glyph_set, ntos=lambda value: "%g" % round(value, 2))
    glyph_set[name].draw(TransformPen(pen, transform))
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" '
        'role="img" aria-label="\u79d2\u996d">\n'
        "  <title>\u79d2\u996d</title>\n"
        '  <rect width="64" height="64" rx="%g" fill="%s"/>\n'
        '  <path d="%s"\n'
        '        fill="#FFFFFF" stroke="#FFFFFF" stroke-width="%.2f"'
        ' stroke-linejoin="round" stroke-linecap="round"/>\n'
        "</svg>\n"
    ) % (VIEW * RADIUS_RATIO, BRAND_ORANGE, pen.getCommands(), stroke)


def asset_bytes(rel_path, size):
    """Bytes of one asset, so writes and --check share a single code path."""
    import io

    if size is None:
        return build_svg().encode("utf-8")

    buf = io.BytesIO()
    mark = build_mark(size)
    if rel_path.endswith(".ico"):
        mark.save(buf, format="ICO", sizes=[(s, s) for s in ICO_SIZES])
    else:
        mark.save(buf, format="PNG", optimize=True)
    return buf.getvalue()


def sha(data):
    return hashlib.sha256(data).hexdigest()[:12]


def md5(data):
    return hashlib.md5(data).hexdigest()


def targets(rel_path):
    """Assets for one table row: a single file, or every file of a pattern."""
    pattern = ROOT / rel_path
    if "*" in rel_path:
        return sorted(pattern.parent.glob(pattern.name))
    return [pattern]


def keep(path, old, rel_path):
    """Never overwrite something in a glob folder that is not our tile."""
    return "*" in rel_path and old and md5(old) != LEGACY_TILE


def generate():
    blocked = []
    for rel_path, size in ASSETS:
        data = asset_bytes(rel_path, size)
        for target in targets(rel_path):
            target.parent.mkdir(parents=True, exist_ok=True)
            old = target.read_bytes() if target.exists() else b""
            name = str(target.relative_to(ROOT))
            if old == data:
                print("%-8s %s" % ("same", name))
                continue
            if keep(target, old, rel_path):
                print("%-8s %s (not our tile, left alone)" % ("kept", name))
                continue
            try:
                target.write_bytes(data)
                print("%-8s %s" % ("written", name))
            except PermissionError:
                # A file created by another sandbox session comes back
                # read-only: this only happens inside the agent sandbox.
                blocked.append(name)
                print("%-8s %s" % ("BLOCKED", name))
    if blocked:
        print("read-only files, rerun outside the sandbox or edit by hand: %s"
              % ", ".join(blocked))


def svg_bbox():
    """Parse the emitted path back and measure the fill outline."""
    from fontTools.pens.boundsPen import BoundsPen
    from fontTools.svgLib.path import parse_path

    path_data, stroke = svg_path_data()
    pen = BoundsPen(None)
    parse_path(path_data, pen)
    return pen.bounds, stroke


def svg_path_data():
    import re

    svg = build_svg()
    return (re.search(r'<path d="([^"]+)"', svg).group(1),
            float(re.search(r'stroke-width="([\d.]+)"', svg).group(1)))


def path_pen():
    """A fontTools pen that collects the outline into a matplotlib path."""
    from fontTools.pens.basePen import BasePen
    from matplotlib.path import Path as MplPath

    class Pen(BasePen):
        def __init__(self):
            BasePen.__init__(self, None)
            self.vertices = []
            self.codes = []

        def _moveTo(self, point):
            self.vertices.append(point)
            self.codes.append(MplPath.MOVETO)

        def _lineTo(self, point):
            self.vertices.append(point)
            self.codes.append(MplPath.LINETO)

        def _curveToOne(self, p1, p2, p3):
            for point in (p1, p2, p3):
                self.vertices.append(point)
                self.codes.append(MplPath.CURVE4)

        def _qCurveToOne(self, p1, p2):
            for point in (p1, p2):
                self.vertices.append(point)
                self.codes.append(MplPath.CURVE3)

        def _closePath(self):
            self.vertices.append(self.vertices[0])
            self.codes.append(MplPath.CLOSEPOLY)

    return Pen()


def rasterize(draw, size=256):
    """Fill a drawn outline into a boolean mask, in svg user units."""
    import numpy as np
    from matplotlib.path import Path

    pen = path_pen()
    draw(pen)
    rows, cols = np.mgrid[0:size, 0:size]
    grid = np.stack([cols + 0.5, rows + 0.5], axis=-1).reshape(-1, 2)
    points = grid * (VIEW / size)
    return Path(pen.vertices, pen.codes).contains_points(points).reshape(size, size)


def glyph_masks(size=256, samples=4):
    """The same glyph through three independent code paths, 0..1 per pixel.

    svg      : the emitted path data, filled (verifies the svg file)
    outline  : the font outline, filled (verifies the transform)
    bitmap   : the badge Pillow rendered, read back from the pixels, stroke
               included (verifies the png/ico files)
    The vector fills are sampled `samples` times per pixel so thin strokes
    are not under counted.
    """
    import numpy as np
    from fontTools.pens.transformPen import TransformPen
    from fontTools.svgLib.path import parse_path

    data, _ = svg_path_data()
    glyph_set, name, transform, _ = glyph_transform()
    big = size * samples
    svg = blocks(rasterize(lambda pen: parse_path(data, pen), big), size)
    outline = blocks(rasterize(
        lambda pen: glyph_set[name].draw(TransformPen(pen, transform)), big), size)
    return svg, outline, coverage(size)


def coverage(size, stroke_ratio=STROKE_RATIO):
    """How white each pixel of the rendered badge is (0 = orange, 1 = glyph)."""
    import numpy as np

    mark = np.array(build_mark(size, stroke_ratio=stroke_ratio).convert("RGBA")).astype(float)
    luma = 0.299 * mark[..., 0] + 0.587 * mark[..., 1] + 0.114 * mark[..., 2]
    background = (0.299 * 249 + 0.587 * 115 + 0.114 * 22)   # BRAND_ORANGE luma
    white = np.clip((luma - background) / (255 - background), 0, 1)
    white[mark[..., 3] < 128] = 0         # ignore the antialiased badge corners
    return white


def ink_box(mask, limit=0.5):
    """Pixel box (x0, y0, x1, y1) of what covers more than half of a pixel."""
    import numpy as np

    ys, xs = np.where(np.asarray(mask) > limit)
    if not len(xs):
        raise SystemExit("nothing was drawn")
    return xs.min(), ys.min(), xs.max() + 1, ys.max() + 1


def blocks(mask, count=32):
    """Average a mask down to count x count blocks, tolerant of sub-pixel drift."""
    import numpy as np

    mask = np.asarray(mask, dtype=float)
    step = mask.shape[0] // count
    trimmed = mask[:count * step, :count * step]
    return trimmed.reshape(count, step, count, step).mean(axis=(1, 3))


def overlap(first, second, count=32):
    """Fuzzy IoU of two masks: sum of the smaller / sum of the larger."""
    import numpy as np

    a, b = blocks(first, count), blocks(second, count)
    return np.minimum(a, b).sum() / np.maximum(a, b).sum()


def iou(first, second):
    return (first & second).sum() / float((first | second).sum())


def normalized(mask, size=256):
    """Stretch a mask's ink box onto a square, to compare glyph shapes."""
    import numpy as np
    from PIL import Image as PILImage

    x0, y0, x1, y1 = ink_box(mask)
    patch = np.asarray(mask)[y0:y1, x0:x1]
    image = PILImage.fromarray((patch * 255).astype("uint8"), "L")
    return np.asarray(image.resize((size, size), Image.Resampling.LANCZOS))


def check():
    ok = True
    scale = 256.0 / VIEW

    (x0, y0, x1, y1), stroke = svg_bbox()
    want = GLYPH_RATIO * VIEW
    fill = (x1 - x0, y1 - y0)
    stroked = (fill[0] + stroke, fill[1] + stroke)
    center = ((x0 + x1) / 2, (y0 + y1) / 2)
    good = (abs(max(stroked) - want) < 0.02
            and all(abs(value - VIEW / 2) < 0.02 for value in center))
    ok &= good
    print("svg glyph %.2f x %.2f units around (%.2f, %.2f), stroke %.2f -> %s"
          % (stroked[0], stroked[1], center[0], center[1], stroke,
             "ok" if good else "FAIL"))

    svg, outline, badge = glyph_masks()

    value = iou(svg > 0.5, outline > 0.5)
    good = value > 0.99
    ok &= good
    print("svg path data vs font outline    IoU %.3f (> 0.99) -> %s"
          % (value, "ok" if good else "FAIL"))

    for label, box, want_box in (
        ("svg fill inside badge", ink_box(svg), (fill[0] * scale, fill[1] * scale)),
        ("rendered glyph in badge", ink_box(badge), (stroked[0] * scale, stroked[1] * scale)),
    ):
        size = (box[2] - box[0], box[3] - box[1])
        offset = max(abs(size[0] - want_box[0]), abs(size[1] - want_box[1]))
        centred = max(abs((box[0] + box[2]) / 2 - 128), abs((box[1] + box[3]) / 2 - 128))
        good = offset <= 1.5 and centred <= 1.5
        ok &= good
        print("%-32s %dx%d px, want %.1fx%.1f, off centre %.1f px -> %s"
              % (label, size[0], size[1], want_box[0], want_box[1], centred,
                 "ok" if good else "FAIL"))

    # same glyph, vector fill vs the pixels the renderer produced.  Both are
    # stretched onto their own ink box first: the svg makes room for the
    # stroke inside the 62% box, the stroke-less render does not.
    vector = normalized(svg)
    pixels = normalized(coverage(256, stroke_ratio=0))
    area = pixels.sum() / vector.sum()
    good = abs(area - 1) < 0.05
    ok &= good
    print("glyph ink, svg vs rendered pixels %.3f (1 +- 0.05) -> %s"
          % (area, "ok" if good else "FAIL"))

    value = overlap(vector / 255.0, pixels / 255.0, 8)
    good = value > 0.9
    ok &= good
    print("glyph shape, svg vs rendered px  %.3f (> 0.90) -> %s"
          % (value, "ok" if good else "FAIL"))

    for rel_path, size in ASSETS:
        data = asset_bytes(rel_path, size)
        for target in targets(rel_path):
            old = target.read_bytes() if target.exists() else b""
            same = old == data
            skip = keep(target, old, rel_path)
            ok &= same or skip
            print("%-8s %s %s" % ("ok" if same else ("kept" if skip else "stale"),
                                  target.relative_to(ROOT), sha(data)))

    print("RESULT:", "ok" if ok else "FAIL")
    return 0 if ok else 1


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify, do not write")
    args = parser.parse_args(argv)
    print("font: %s" % FONT_PATH)
    return check() if args.check else (generate() or check())


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
