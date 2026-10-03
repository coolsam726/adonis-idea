#!/usr/bin/env python3
"""Regenerate README demo GIFs for the Adonis Idea WebStorm plugin.

Produces animated IDE mockups in docs/media/ (~880×420, dark theme).
Requires Python 3 and Pillow (pip install Pillow).
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "docs" / "media"

W, H = 880, 420
FPS = 10

# Dark IDE palette
BG = (30, 31, 34)  # #1e1f22
GUTTER_BG = (37, 38, 41)
TAB_BG = (43, 44, 48)
TAB_ACTIVE = (30, 31, 34)
BORDER = (60, 61, 65)
STATUS_BG = (43, 44, 48)
ACCENT = (90, 109, 245)  # #5A6DF5
TEXT = (187, 187, 187)
TEXT_DIM = (120, 123, 130)
KEYWORD = (204, 120, 50)
STRING = (106, 135, 89)
FUNC = (255, 198, 109)
COMMENT = (106, 135, 89)
HTML_TAG = (232, 191, 106)
EDGE_DIR = ACCENT
EDGE_EXPR = (206, 145, 120)
TYPE = (86, 156, 214)
POPUP_BG = (50, 51, 55)
POPUP_SEL = (62, 65, 72)
POPUP_BORDER = (80, 82, 88)
CURSOR = (187, 187, 187)
HIGHLIGHT = (90, 109, 245, 60)
CTRL_HINT = (200, 200, 200)

FONT_PATHS = [
    ("/usr/share/fonts/truetype/hack/Hack-Regular.ttf", "/usr/share/fonts/truetype/hack/Hack-Bold.ttf"),
    ("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf"),
]

GUTTER_W = 48
TAB_H = 32
STATUS_H = 24
TITLE_H = 28
FONT_SIZE = 14
LINE_H = 20
PAD_X = 12
CODE_X = GUTTER_W + PAD_X


def resolve_fonts() -> tuple[ImageFont.FreeTypeFont, ImageFont.FreeTypeFont]:
    for regular, bold in FONT_PATHS:
        if Path(regular).is_file():
            bold_path = bold if Path(bold).is_file() else regular
            return (
                ImageFont.truetype(regular, FONT_SIZE),
                ImageFont.truetype(bold_path, FONT_SIZE),
            )
    return ImageFont.load_default(), ImageFont.load_default()


FONT, FONT_BOLD = resolve_fonts()


def lerp(a: float, b: float, t: float) -> float:
    return a + (b - a) * t


def ease(t: float) -> float:
    return t * t * (3 - 2 * t)


def text_w(text: str, font: ImageFont.FreeTypeFont | None = None) -> int:
    f = font or FONT
    bbox = f.getbbox(text)
    return bbox[2] - bbox[0]


def draw_traffic_lights(draw: ImageDraw.ImageDraw, y: int = 10) -> None:
    colors = [(255, 95, 86), (255, 189, 46), (39, 201, 63)]
    for i, c in enumerate(colors):
        draw.ellipse((14 + i * 22, y, 26 + i * 22, y + 12), fill=c)


def draw_tab_bar(draw: ImageDraw.ImageDraw, tabs: list[tuple[str, bool]], title: str = "WebStorm") -> None:
    draw.rectangle((0, 0, W, TITLE_H), fill=TAB_BG)
    draw_traffic_lights(draw, 8)
    draw.text((80, 7), title, fill=TEXT_DIM, font=FONT)

    x = 200
    for name, active in tabs:
        tw = text_w(name) + 24
        bg = TAB_ACTIVE if active else TAB_BG
        draw.rectangle((x, TITLE_H - TAB_H, x + tw, TITLE_H), fill=bg)
        if active:
            draw.rectangle((x, TITLE_H - 2, x + tw, TITLE_H), fill=ACCENT)
        draw.text((x + 12, TITLE_H - TAB_H + 8), name, fill=TEXT if active else TEXT_DIM, font=FONT)
        x += tw + 2


def draw_gutter(draw: ImageDraw.ImageDraw, line_count: int, y0: int) -> None:
    draw.rectangle((0, y0, GUTTER_W, H - STATUS_H), fill=GUTTER_BG)
    for i in range(line_count):
        draw.text((GUTTER_W - 8 - text_w(str(i + 1)), y0 + 8 + i * LINE_H), str(i + 1), fill=TEXT_DIM, font=FONT)


def draw_status_bar(draw: ImageDraw.ImageDraw, message: str = "Adonis Idea · Index OK") -> None:
    y = H - STATUS_H
    draw.rectangle((0, y, W, H), fill=STATUS_BG)
    draw.line((0, y, W, y), fill=BORDER, width=1)
    draw.text((12, y + 5), message, fill=TEXT_DIM, font=FONT)
    draw.text((W - 120, y + 5), "TypeScript", fill=TEXT_DIM, font=FONT)


def base_frame(tabs: list[tuple[str, bool]], line_count: int, title: str = "WebStorm") -> Image.Image:
    img = Image.new("RGB", (W, H), BG)
    draw = ImageDraw.Draw(img)
    y0 = TITLE_H
    draw_tab_bar(draw, tabs, title)
    draw_gutter(draw, line_count, y0)
    draw.rectangle((GUTTER_W, y0, W, H - STATUS_H), fill=BG)
    draw_status_bar(draw)
    return img


class Segment:
    __slots__ = ("text", "color", "bold")

    def __init__(self, text: str, color: tuple[int, ...] = TEXT, bold: bool = False):
        self.text = text
        self.color = color
        self.bold = bold


def draw_segments(
    draw: ImageDraw.ImageDraw,
    x: int,
    y: int,
    segments: list[Segment],
) -> int:
    for seg in segments:
        f = FONT_BOLD if seg.bold else FONT
        draw.text((x, y), seg.text, fill=seg.color, font=f)
        x += text_w(seg.text, f)
    return x


def draw_cursor(draw: ImageDraw.ImageDraw, x: int, y: int, blink: bool = True) -> None:
    if blink:
        draw.rectangle((x, y, x + 2, y + LINE_H - 4), fill=CURSOR)


def draw_popup(
    draw: ImageDraw.ImageDraw,
    x: int,
    y: int,
    items: list[str],
    selected: int = 0,
    width: int = 260,
) -> None:
    row_h = 22
    h = len(items) * row_h + 8
    draw.rectangle((x, y, x + width, y + h), fill=POPUP_BG, outline=POPUP_BORDER)
    for i, item in enumerate(items):
        ry = y + 4 + i * row_h
        if i == selected:
            draw.rectangle((x + 2, ry, x + width - 2, ry + row_h), fill=POPUP_SEL)
        draw.text((x + 10, ry + 3), item, fill=ACCENT if i == selected else TEXT, font=FONT)


def draw_menu(
    draw: ImageDraw.ImageDraw,
    x: int,
    y: int,
    items: list[tuple[str, bool]],
    width: int = 220,
) -> None:
    row_h = 24
    h = len(items) * row_h + 6
    draw.rectangle((x, y, x + width, y + h), fill=POPUP_BG, outline=POPUP_BORDER)
    for i, (label, highlight) in enumerate(items):
        ry = y + 3 + i * row_h
        if highlight:
            draw.rectangle((x + 2, ry, x + width - 2, ry + row_h), fill=POPUP_SEL)
        draw.text((x + 12, ry + 4), label, fill=ACCENT if highlight else TEXT, font=FONT)


def highlight_range(draw: ImageDraw.ImageDraw, x: int, y: int, width: int) -> None:
    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    od.rectangle((x, y, x + width, y + LINE_H), fill=HIGHLIGHT)
    return overlay


def save_gif(frames: list[Image.Image], path: Path, duration_ms: int = 100) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    palette_frames: list[Image.Image] = []
    for frame in frames:
        q = frame.convert("P", palette=Image.ADAPTIVE, colors=128)
        palette_frames.append(q)
    palette_frames[0].save(
        path,
        save_all=True,
        append_images=palette_frames[1:],
        duration=duration_ms,
        loop=0,
        optimize=True,
    )


def make_completions_views() -> list[Image.Image]:
    prefix = "pages/auth/"
    typed_chars = list(prefix)
    popup_items = [
        "pages/auth/login.edge",
        "pages/auth/register.edge",
        "pages/auth/forgot.edge",
        "pages/auth/reset.edge",
    ]
    frames: list[Image.Image] = []

    static_before = [
        Segment("  async ", KEYWORD),
        Segment("login", FUNC),
        Segment("({ view }: HttpContext) {", TEXT),
    ]
    static_mid = [
        Segment("    ", TEXT),
        Segment("return", KEYWORD),
        Segment(" view.render(", TEXT),
        Segment("'", STRING),
    ]

    total_typing = len(typed_chars) + 8
    for step in range(total_typing + 20):
        img = base_frame([("session_controller.ts", True), ("routes.ts", False)], 8)
        draw = ImageDraw.Draw(img)
        y = TITLE_H + 8

        draw_segments(draw, CODE_X, y, [Segment("import ", KEYWORD), Segment("{ HttpContext } ", TYPE), Segment("from ", KEYWORD), Segment("'@adonisjs/core/http'", STRING)])
        y += LINE_H
        draw_segments(draw, CODE_X, y, [Segment("export default ", KEYWORD), Segment("class ", KEYWORD), Segment("SessionController", TYPE), Segment(" {", TEXT)])
        y += LINE_H
        draw_segments(draw, CODE_X, y, static_before)
        y += LINE_H

        chars = min(step, len(typed_chars))
        typed = prefix[:chars]
        draw_segments(draw, CODE_X, y, static_mid)
        cx = CODE_X + sum(text_w(s.text, FONT_BOLD if s.bold else FONT) for s in static_mid)
        draw.text((cx, y), typed, fill=STRING, font=FONT)
        cx += text_w(typed)

        blink = (step // 5) % 2 == 0
        if step < total_typing:
            draw_cursor(draw, cx, y, blink)

        if step >= len(typed_chars):
            draw_popup(draw, cx, y + LINE_H, popup_items, selected=min(step - len(typed_chars), len(popup_items) - 1))

        frames.append(img)

    return frames


def make_navigation_controller() -> list[Image.Image]:
    frames: list[Image.Image] = []

    routes_lines = [
        ([Segment("import ", KEYWORD), Segment("router ", TEXT), Segment("from ", KEYWORD), Segment("'@adonisjs/core/services/router'", STRING)], 0),
        ([Segment("import ", KEYWORD), Segment("controllers ", TEXT), Segment("from ", KEYWORD), Segment("'#controllers/index.js'", STRING)], 1),
        ([], 2),
        ([Segment("router.", TEXT), Segment("group", FUNC), Segment("(() => {", TEXT)], 3),
        ([Segment("  router.", TEXT), Segment("post", FUNC), Segment("('login', [controllers.Session, ", TEXT), Segment("'store'", STRING), Segment("])", TEXT)], 4),
        ([Segment("}).prefix('auth')", TEXT)], 5),
    ]

    controller_lines = [
        ([Segment("export default ", KEYWORD), Segment("class ", KEYWORD), Segment("SessionController", TYPE), Segment(" {", TEXT)], 0),
        ([Segment("  async ", KEYWORD), Segment("store", FUNC), Segment("(ctx: HttpContext) {", TEXT)], 1),
        ([Segment("    const { request, response, session } = ctx", TEXT)], 2),
        ([Segment("    // validate & persist session…", COMMENT)], 3),
        ([Segment("    return response.redirect('/')", TEXT)], 4),
        ([Segment("  }", TEXT)], 5),
        ([Segment("}", TEXT)], 6),
    ]

    for phase in range(55):
        if phase < 28:
            img = base_frame([("routes.ts", True), ("session_controller.ts", False)], 7)
            draw = ImageDraw.Draw(img)
            y = TITLE_H + 8
            for segs, _ in routes_lines:
                if segs:
                    draw_segments(draw, CODE_X, y, segs)
                y += LINE_H

            store_x = CODE_X + text_w("  router.post('login', [controllers.Session, '")
            store_y = TITLE_H + 8 + 4 * LINE_H
            pulse = 0.5 + 0.5 * math.sin(phase * 0.4)
            underline_y = store_y + LINE_H - 6
            draw.line((store_x, underline_y, store_x + text_w("store"), underline_y), fill=ACCENT, width=2)

            if phase > 8:
                hint = "Ctrl+Click"
                draw.rectangle((store_x, store_y - 26, store_x + text_w(hint) + 12, store_y - 6), fill=POPUP_BG, outline=ACCENT)
                draw.text((store_x + 6, store_y - 22), hint, fill=CTRL_HINT, font=FONT)

            if phase > 18:
                t = ease(min(1.0, (phase - 18) / 8))
                flash = int(lerp(0, 80, t))
                overlay = Image.new("RGBA", (W, H), (90, 109, 245, flash))
                img = Image.alpha_composite(img.convert("RGBA"), overlay).convert("RGB")
        else:
            t = phase - 28
            img = base_frame([("session_controller.ts", True), ("routes.ts", False)], 7)
            draw = ImageDraw.Draw(img)
            y = TITLE_H + 8
            overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
            od = ImageDraw.Draw(overlay)
            for segs, idx in controller_lines:
                if segs:
                    draw_segments(draw, CODE_X, y, segs)
                    if idx == 1 and t < 15:
                        sx = CODE_X + text_w("  async ")
                        od.rectangle((sx, y, sx + text_w("store"), y + LINE_H), fill=(90, 109, 245, 90))
                y += LINE_H
            img = Image.alpha_composite(img.convert("RGBA"), overlay).convert("RGB")
            draw = ImageDraw.Draw(img)
            draw.text((CODE_X, TITLE_H + 8 + 7 * LINE_H + 4), "SessionController#store", fill=ACCENT, font=FONT_BOLD)

        frames.append(img)

    return frames


def make_completions_routes() -> list[Image.Image]:
    prefix = "hom"
    popup_items = ["home", "home.dashboard", "home.settings", "home.profile"]
    frames: list[Image.Image] = []

    for step in range(len(prefix) + 18):
        img = base_frame([("welcome.edge", True), ("routes.ts", False)], 6, title="WebStorm")
        draw = ImageDraw.Draw(img)
        y = TITLE_H + 8

        draw_segments(draw, CODE_X, y, [Segment("<!DOCTYPE html>", HTML_TAG)])
        y += LINE_H
        draw_segments(draw, CODE_X, y, [Segment("<html>", HTML_TAG)])
        y += LINE_H
        draw_segments(draw, CODE_X, y, [Segment("  <body>", HTML_TAG)])
        y += LINE_H

        before = [Segment("    <a href=\"{{ ", EDGE_EXPR), Segment("route", EDGE_DIR), Segment("('", EDGE_EXPR)]
        draw_segments(draw, CODE_X, y, before)
        cx = CODE_X + sum(text_w(s.text) for s in before)
        typed = prefix[: min(step, len(prefix))]
        draw.text((cx, y), typed, fill=STRING, font=FONT)
        cx += text_w(typed)
        draw.text((cx, y), "')", fill=EDGE_EXPR, font=FONT)

        if step < len(prefix) + 2:
            draw_cursor(draw, cx - text_w("')"), y, (step // 4) % 2 == 0)

        if step >= len(prefix):
            draw_popup(draw, cx - 40, y + LINE_H, popup_items, selected=min(step - len(prefix), 2))

        y += LINE_H
        draw_segments(draw, CODE_X, y, [Segment("      Home", TEXT)])
        y += LINE_H
        draw_segments(draw, CODE_X, y, [Segment("    </a>", HTML_TAG)])
        y += LINE_H
        draw_segments(draw, CODE_X, y, [Segment("  </body>", HTML_TAG)])

        frames.append(img)

    return frames


def make_edge_templates() -> list[Image.Image]:
    lines: list[list[Segment]] = [
        [Segment("@layout", EDGE_DIR), Segment("('layouts/main')", EDGE_EXPR)],
        [Segment("", TEXT)],
        [Segment("<section ", HTML_TAG), Segment("class", HTML_TAG), Segment("=\"hero\">", HTML_TAG)],
        [Segment("  ", TEXT), Segment("@if", EDGE_DIR), Segment("(user)", EDGE_EXPR)],
        [Segment("    <h1>", HTML_TAG), Segment("Hello, {{ user.name }}", EDGE_EXPR), Segment("</h1>", HTML_TAG)],
        [Segment("  ", TEXT), Segment("@else", EDGE_DIR)],
        [Segment("    <h1>", HTML_TAG), Segment("Welcome guest", TEXT), Segment("</h1>", HTML_TAG)],
        [Segment("  ", TEXT), Segment("@end", EDGE_DIR)],
        [Segment("</section>", HTML_TAG)],
    ]

    frames: list[Image.Image] = []
    for step in range(30):
        img = base_frame([("dashboard.edge", True), ("session_controller.ts", False)], len(lines), title="WebStorm")
        draw = ImageDraw.Draw(img)
        y = TITLE_H + 8
        highlight_line = (step // 6) % len(lines)
        overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        od = ImageDraw.Draw(overlay)

        for i, segs in enumerate(lines):
            if segs:
                draw_segments(draw, CODE_X, y, segs)
            if i == highlight_line:
                od.rectangle((GUTTER_W, y - 2, W - 8, y + LINE_H), fill=(90, 109, 245, 35))
            y += LINE_H

        img = Image.alpha_composite(img.convert("RGBA"), overlay).convert("RGB")
        frames.append(img)

    return frames


def make_edge_directives() -> list[Image.Image]:
    """Type `@` → full directive list → pick `each` → structured snippet + `@end`."""
    popup_items = [
        "each(item in items) … @end",
        "if(condition) … @end",
        "component('name') … @end",
        "!component('name')",
        "include('partial')",
        "layout('name') … @end",
        "page() … @end",
        "wire('name') … @end",
    ]
    frames: list[Image.Image] = []

    # Phase A: empty file → type `@` → popup
    for step in range(22):
        img = base_frame([("posts.edge", True), ("routes.ts", False)], 8, title="WebStorm")
        draw = ImageDraw.Draw(img)
        y = TITLE_H + 8

        draw_segments(draw, CODE_X, y, [Segment("<ul>", HTML_TAG)])
        y += LINE_H

        at_visible = step >= 2
        typed = "@" if at_visible else ""
        draw.text((CODE_X, y), typed, fill=EDGE_DIR, font=FONT_BOLD)
        cx = CODE_X + text_w(typed, FONT_BOLD)
        if step < 6:
            draw_cursor(draw, cx, y, (step // 2) % 2 == 0)

        if step >= 4:
            sel = min((step - 4) // 2, 2)
            draw_popup(draw, CODE_X, y + LINE_H, popup_items, selected=sel, width=300)

        y += LINE_H
        draw_segments(draw, CODE_X, y, [Segment("</ul>", HTML_TAG)])
        frames.append(img)

    # Phase B: accept `each` → live template with placeholders + @end
    expanded = [
        [Segment("@each", EDGE_DIR), Segment("(item in items)", EDGE_EXPR)],
        [Segment("  ", TEXT), Segment("<li>", HTML_TAG), Segment("{{ item }}", EDGE_EXPR), Segment("</li>", HTML_TAG)],
        [Segment("@end", EDGE_DIR)],
    ]
    for step in range(18):
        img = base_frame([("posts.edge", True), ("routes.ts", False)], 8, title="WebStorm")
        draw = ImageDraw.Draw(img)
        y = TITLE_H + 8
        draw_segments(draw, CODE_X, y, [Segment("<ul>", HTML_TAG)])
        y += LINE_H

        overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        od = ImageDraw.Draw(overlay)
        for i, segs in enumerate(expanded):
            draw_segments(draw, CODE_X, y, segs)
            if i == 0 and step < 10:
                # Highlight the first tab-stop (`item`).
                hx = CODE_X + text_w("@each(")
                od.rectangle((hx, y, hx + text_w("item"), y + LINE_H - 2), fill=(90, 109, 245, 70))
            y += LINE_H
        img = Image.alpha_composite(img.convert("RGBA"), overlay).convert("RGB")
        draw = ImageDraw.Draw(img)
        if step > 6:
            draw.text(
                (CODE_X, TITLE_H + 8 + 5 * LINE_H),
                "Tab through args · @end inserted",
                fill=ACCENT,
                font=FONT,
            )
        frames.append(img)

    return frames


def make_ace_generators() -> list[Image.Image]:

    menu_items = [
        ("Adonis", False),
        ("  New…", True),
        ("    make:controller", False),
        ("    make:model", False),
        ("    make:migration", False),
        ("    make:validator", False),
        ("    make:wire", False),
    ]
    frames: list[Image.Image] = []

    for step in range(35):
        img = base_frame([("Project", True)], 4, title="WebStorm")
        draw = ImageDraw.Draw(img)
        y = TITLE_H + 8

        draw_segments(draw, CODE_X, y, [Segment("// Right-click project or use menu:", COMMENT)])
        y += LINE_H * 2
        draw_segments(draw, CODE_X, y, [Segment("Adonis → New… → make:controller", ACCENT)])

        menu_x, menu_y = 280, TITLE_H + 40
        visible = min(len(menu_items), max(0, step - 4))
        if visible > 0:
            draw_menu(draw, menu_x, menu_y, menu_items[:visible], width=240)

        if step > 12:
            sel_y = menu_y + 3 + 24  # "  New…"
            draw.rectangle((menu_x + 230, sel_y + 4, menu_x + 238, sel_y + 16), fill=ACCENT)

        frames.append(img)

    return frames


def main() -> None:
    generators = [
        ("completions-views.gif", make_completions_views),
        ("navigation-controller.gif", make_navigation_controller),
        ("completions-routes.gif", make_completions_routes),
        ("edge-templates.gif", make_edge_templates),
        ("edge-directives.gif", make_edge_directives),
        ("ace-generators.gif", make_ace_generators),
    ]

    print(f"Writing GIFs to {OUT_DIR}/")
    for name, fn in generators:
        path = OUT_DIR / name
        frames = fn()
        save_gif(frames, path, duration_ms=1000 // FPS)
        size_kb = path.stat().st_size / 1024
        flag = "OK" if size_kb <= 200 else "WARN (>200KB)"
        print(f"  {name}: {size_kb:.1f} KB  [{flag}]")

    print("Done.")


if __name__ == "__main__":
    main()
