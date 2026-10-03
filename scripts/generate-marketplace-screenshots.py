#!/usr/bin/env python3
"""Generate JetBrains Marketplace screenshots (≥1200×760).

Outputs PNG stills at 1280×800 (16:10) under docs/marketplace/.
Requires: pip install Pillow
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "docs" / "marketplace"

# Marketplace recommends ≥1200×760; 1280×800 is a clean 16:10.
W, H = 1280, 800

# WebStorm New UI Light (default theme — Marketplace preference)
BG = (255, 255, 255)
SIDEBAR_BG = (247, 248, 250)
GUTTER_BG = (245, 246, 248)
TAB_BG = (242, 243, 245)
TAB_ACTIVE = (255, 255, 255)
BORDER = (220, 222, 226)
STATUS_BG = (242, 243, 245)
ACCENT = (53, 116, 240)  # New UI blue
ADONIS = (90, 109, 245)
TEXT = (30, 31, 34)
TEXT_DIM = (110, 114, 122)
KEYWORD = (0, 63, 180)
STRING = (6, 125, 23)
FUNC = (120, 94, 0)
COMMENT = (140, 140, 140)
HTML_TAG = (140, 70, 0)
EDGE_DIR = ADONIS
EDGE_EXPR = (152, 68, 32)
TYPE = (0, 117, 163)
POPUP_BG = (255, 255, 255)
POPUP_SEL = (232, 240, 254)
POPUP_BORDER = (200, 204, 212)
CURSOR = (30, 31, 34)
CAPTION_BG = (30, 31, 34)
CAPTION_FG = (255, 255, 255)

SIDEBAR_W = 220
GUTTER_W = 52
TAB_H = 36
STATUS_H = 28
TITLE_H = 36
CAPTION_H = 48
FONT_SIZE = 16
LINE_H = 24
PAD_X = 16
CODE_X = SIDEBAR_W + GUTTER_W + PAD_X

FONT_PATHS = [
    ("/usr/share/fonts/truetype/hack/Hack-Regular.ttf", "/usr/share/fonts/truetype/hack/Hack-Bold.ttf"),
    ("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf"),
]
UI_FONT_PATHS = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
]


def resolve_mono() -> tuple[ImageFont.FreeTypeFont, ImageFont.FreeTypeFont]:
    for regular, bold in FONT_PATHS:
        if Path(regular).is_file():
            bold_path = bold if Path(bold).is_file() else regular
            return (
                ImageFont.truetype(regular, FONT_SIZE),
                ImageFont.truetype(bold_path, FONT_SIZE),
            )
    return ImageFont.load_default(), ImageFont.load_default()


def resolve_ui() -> tuple[ImageFont.FreeTypeFont, ImageFont.FreeTypeFont]:
    regular = next((p for p in UI_FONT_PATHS if Path(p).is_file()), None)
    bold = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    if regular:
        return (
            ImageFont.truetype(regular, 15),
            ImageFont.truetype(bold if Path(bold).is_file() else regular, 18),
        )
    return ImageFont.load_default(), ImageFont.load_default()


FONT, FONT_BOLD = resolve_mono()
UI_FONT, UI_BOLD = resolve_ui()


class Segment:
    __slots__ = ("text", "color", "bold")

    def __init__(self, text: str, color: tuple[int, ...] = TEXT, bold: bool = False):
        self.text = text
        self.color = color
        self.bold = bold


def text_w(text: str, font: ImageFont.FreeTypeFont | None = None) -> int:
    f = font or FONT
    bbox = f.getbbox(text)
    return bbox[2] - bbox[0]


def draw_segments(draw: ImageDraw.ImageDraw, x: int, y: int, segments: list[Segment]) -> int:
    for seg in segments:
        f = FONT_BOLD if seg.bold else FONT
        draw.text((x, y), seg.text, fill=seg.color, font=f)
        x += text_w(seg.text, f)
    return x


def draw_caption(draw: ImageDraw.ImageDraw, caption: str) -> None:
    y0 = H - CAPTION_H
    draw.rectangle((0, y0, W, H), fill=CAPTION_BG)
    draw.rectangle((0, y0, 6, H), fill=ADONIS)
    draw.text((20, y0 + 14), caption, fill=CAPTION_FG, font=UI_BOLD)


def draw_sidebar(draw: ImageDraw.ImageDraw, files: list[tuple[str, bool]]) -> None:
    draw.rectangle((0, TITLE_H, SIDEBAR_W, H - STATUS_H - CAPTION_H), fill=SIDEBAR_BG)
    draw.line((SIDEBAR_W, TITLE_H, SIDEBAR_W, H - STATUS_H - CAPTION_H), fill=BORDER, width=1)
    draw.text((16, TITLE_H + 12), "PROJECT", fill=TEXT_DIM, font=UI_FONT)
    y = TITLE_H + 40
    for name, active in files:
        if active:
            draw.rectangle((8, y - 4, SIDEBAR_W - 8, y + 20), fill=POPUP_SEL)
        color = ADONIS if name.endswith(".edge") else TEXT
        draw.text((20, y), name, fill=color if active else TEXT_DIM, font=UI_FONT)
        y += 28


def draw_chrome(
    draw: ImageDraw.ImageDraw,
    tabs: list[tuple[str, bool]],
    files: list[tuple[str, bool]],
    status: str,
) -> None:
    # Title bar
    draw.rectangle((0, 0, W, TITLE_H), fill=TAB_BG)
    for i, c in enumerate([(255, 95, 86), (255, 189, 46), (39, 201, 63)]):
        draw.ellipse((14 + i * 22, 12, 26 + i * 22, 24), fill=c)
    draw.text((90, 10), "WebStorm — AdonisJS", fill=TEXT_DIM, font=UI_FONT)

    # Tabs
    x = SIDEBAR_W + 8
    for name, active in tabs:
        tw = text_w(name, UI_FONT) + 28
        bg = TAB_ACTIVE if active else TAB_BG
        draw.rectangle((x, TITLE_H, x + tw, TITLE_H + TAB_H), fill=bg)
        if active:
            draw.rectangle((x, TITLE_H + TAB_H - 3, x + tw, TITLE_H + TAB_H), fill=ACCENT)
        draw.text((x + 14, TITLE_H + 10), name, fill=TEXT if active else TEXT_DIM, font=UI_FONT)
        x += tw + 4

    draw_sidebar(draw, files)

    # Editor + gutter background
    code_top = TITLE_H + TAB_H
    code_bottom = H - STATUS_H - CAPTION_H
    draw.rectangle((SIDEBAR_W, code_top, W, code_bottom), fill=BG)
    draw.rectangle((SIDEBAR_W, code_top, SIDEBAR_W + GUTTER_W, code_bottom), fill=GUTTER_BG)
    draw.line((SIDEBAR_W + GUTTER_W, code_top, SIDEBAR_W + GUTTER_W, code_bottom), fill=BORDER, width=1)

    # Status
    sy = H - STATUS_H - CAPTION_H
    draw.rectangle((0, sy, W, sy + STATUS_H), fill=STATUS_BG)
    draw.line((0, sy, W, sy), fill=BORDER, width=1)
    draw.text((12, sy + 6), status, fill=TEXT_DIM, font=UI_FONT)
    draw.text((W - 160, sy + 6), "TypeScript / Edge", fill=TEXT_DIM, font=UI_FONT)


def draw_gutter_nums(draw: ImageDraw.ImageDraw, count: int, y0: int) -> None:
    for i in range(count):
        n = str(i + 1)
        draw.text(
            (SIDEBAR_W + GUTTER_W - 10 - text_w(n), y0 + i * LINE_H),
            n,
            fill=TEXT_DIM,
            font=FONT,
        )


def draw_popup(
    draw: ImageDraw.ImageDraw,
    x: int,
    y: int,
    items: list[str],
    selected: int = 0,
    width: int = 340,
) -> None:
    row_h = 26
    h = len(items) * row_h + 10
    draw.rectangle((x, y, x + width, y + h), fill=POPUP_BG, outline=POPUP_BORDER, width=1)
    # subtle shadow
    for i, item in enumerate(items):
        ry = y + 5 + i * row_h
        if i == selected:
            draw.rectangle((x + 2, ry, x + width - 2, ry + row_h), fill=POPUP_SEL)
        draw.text((x + 12, ry + 4), item, fill=ACCENT if i == selected else TEXT, font=FONT)


def draw_menu(
    draw: ImageDraw.ImageDraw,
    x: int,
    y: int,
    items: list[tuple[str, bool]],
    width: int = 280,
) -> None:
    row_h = 28
    h = len(items) * row_h + 8
    draw.rectangle((x, y, x + width, y + h), fill=POPUP_BG, outline=POPUP_BORDER, width=1)
    for i, (label, highlight) in enumerate(items):
        ry = y + 4 + i * row_h
        if highlight:
            draw.rectangle((x + 2, ry, x + width - 2, ry + row_h), fill=POPUP_SEL)
        draw.text((x + 14, ry + 5), label, fill=ACCENT if highlight else TEXT, font=UI_FONT)


def base(
    tabs: list[tuple[str, bool]],
    files: list[tuple[str, bool]],
    line_count: int,
    caption: str,
    status: str = "AdonisJS · Index OK",
) -> tuple[Image.Image, ImageDraw.ImageDraw, int]:
    img = Image.new("RGB", (W, H), BG)
    draw = ImageDraw.Draw(img)
    draw_chrome(draw, tabs, files, status)
    y0 = TITLE_H + TAB_H + 12
    draw_gutter_nums(draw, line_count, y0)
    draw_caption(draw, caption)
    return img, draw, y0


def shot_edge_templates() -> Image.Image:
    lines = [
        [Segment("@layout", EDGE_DIR), Segment("('layouts/main')", EDGE_EXPR)],
        [],
        [Segment("@form", EDGE_DIR), Segment("({ route: 'auth.login', method: 'post' })", EDGE_EXPR)],
        [Segment("  <label>", HTML_TAG), Segment("Email", TEXT), Segment("</label>", HTML_TAG)],
        [Segment("  ", TEXT), Segment("@!input", EDGE_DIR), Segment("({ type: 'email', name: 'email' })", EDGE_EXPR)],
        [Segment("  ", TEXT), Segment("@!button", EDGE_DIR), Segment("({ type: 'submit' })", EDGE_EXPR), Segment(" Sign in ", TEXT), Segment("@end", EDGE_DIR)],
        [Segment("@end", EDGE_DIR)],
        [],
        [Segment("{!! ", EDGE_EXPR), Segment("csrfField()", FUNC), Segment(" !!}", EDGE_EXPR)],
    ]
    files = [("app/", False), ("resources/views/", False), ("  login.edge", True), ("start/routes.ts", False)]
    img, draw, y = base(
        [("login.edge", True), ("routes.ts", False)],
        files,
        len(lines) + 1,
        "Native Edge highlighting — tag components, @directives, and {!! raw !!} echo",
    )
    for segs in lines:
        if segs:
            draw_segments(draw, CODE_X, y, segs)
        y += LINE_H
    return img


def shot_edge_directives() -> Image.Image:
    popup = [
        "each(item in items) … @end",
        "if(condition) … @end",
        "form({ route }) … @end",
        "!component('name')",
        "include('partial')",
        "layout('name') … @end",
        "wire('name') … @end",
    ]
    files = [("resources/views/", False), ("  posts.edge", True), ("start/routes.ts", False)]
    img, draw, y = base(
        [("posts.edge", True)],
        files,
        8,
        "Type @ in Edge — full directive catalog (snippets + @end), no Ctrl+Space",
    )
    draw_segments(draw, CODE_X, y, [Segment("<ul>", HTML_TAG)])
    y += LINE_H
    draw.text((CODE_X, y), "@", fill=EDGE_DIR, font=FONT_BOLD)
    draw.rectangle((CODE_X + text_w("@", FONT_BOLD), y, CODE_X + text_w("@", FONT_BOLD) + 2, y + LINE_H - 4), fill=CURSOR)
    draw_popup(draw, CODE_X, y + LINE_H + 4, popup, selected=0, width=360)
    y += LINE_H * 2
    # keep closing tag visible under popup area conceptually
    draw_segments(draw, CODE_X, TITLE_H + TAB_H + 12 + 7 * LINE_H, [Segment("</ul>", HTML_TAG)])
    return img


def shot_view_completion() -> Image.Image:
    popup = [
        "pages/auth/login",
        "pages/auth/register",
        "pages/auth/forgot",
        "pages/auth/reset",
    ]
    files = [("app/controllers/", False), ("  session_controller.ts", True), ("resources/views/", False)]
    img, draw, y = base(
        [("session_controller.ts", True), ("login.edge", False)],
        files,
        10,
        "view.render('…') — complete Adonis view & component paths",
    )
    rows = [
        [Segment("import ", KEYWORD), Segment("{ HttpContext } ", TYPE), Segment("from ", KEYWORD), Segment("'@adonisjs/core/http'", STRING)],
        [],
        [Segment("export default ", KEYWORD), Segment("class ", KEYWORD), Segment("SessionController", TYPE), Segment(" {", TEXT)],
        [Segment("  async ", KEYWORD), Segment("login", FUNC), Segment("({ view }: HttpContext) {", TEXT)],
        [Segment("    ", TEXT), Segment("return", KEYWORD), Segment(" view.render(", TEXT), Segment("'pages/auth/'", STRING), Segment(")", TEXT)],
        [Segment("  }", TEXT)],
        [Segment("}", TEXT)],
    ]
    for i, segs in enumerate(rows):
        if segs:
            draw_segments(draw, CODE_X, y, segs)
        if i == 4:
            cx = CODE_X + text_w("    return view.render('pages/auth/")
            draw_popup(draw, cx - 20, y + LINE_H + 2, popup, selected=0, width=300)
        y += LINE_H
    return img


def shot_route_completion() -> Image.Image:
    popup = ["home", "home.dashboard", "auth.login", "auth.register", "posts.show"]
    files = [("resources/views/", False), ("  welcome.edge", True), ("start/routes.ts", False)]
    img, draw, y = base(
        [("welcome.edge", True), ("routes.ts", False)],
        files,
        8,
        "route('…') — named route completion in Edge and TypeScript",
    )
    rows = [
        [Segment("<!DOCTYPE html>", HTML_TAG)],
        [Segment("<html>", HTML_TAG)],
        [Segment("  <body>", HTML_TAG)],
        [Segment("    <a href=\"{{ ", EDGE_EXPR), Segment("route", EDGE_DIR), Segment("('hom", STRING)],
        [Segment("      Home", TEXT)],
        [Segment("    </a>", HTML_TAG)],
        [Segment("  </body>", HTML_TAG)],
    ]
    for i, segs in enumerate(rows):
        if segs:
            end_x = draw_segments(draw, CODE_X, y, segs)
            if i == 3:
                draw.text((end_x, y), "') }}\">", fill=EDGE_EXPR, font=FONT)
                draw_popup(draw, end_x - 40, y + LINE_H + 2, popup, selected=1, width=280)
        y += LINE_H
    return img


def shot_controller_navigation() -> Image.Image:
    files = [("start/", False), ("  routes.ts", True), ("app/controllers/", False), ("  session_controller.ts", False)]
    img, draw, y = base(
        [("routes.ts", True), ("session_controller.ts", False)],
        files,
        9,
        "Go to Declaration on 'store' → SessionController#store (exact action)",
    )
    rows = [
        [Segment("import ", KEYWORD), Segment("router ", TEXT), Segment("from ", KEYWORD), Segment("'@adonisjs/core/services/router'", STRING)],
        [Segment("import ", KEYWORD), Segment("controllers ", TEXT), Segment("from ", KEYWORD), Segment("'#controllers/index.js'", STRING)],
        [],
        [Segment("router.", TEXT), Segment("group", FUNC), Segment("(() => {", TEXT)],
        [Segment("  router.", TEXT), Segment("post", FUNC), Segment("('login', [controllers.Session, ", TEXT), Segment("'store'", STRING), Segment("])", TEXT)],
        [Segment("}).prefix('auth')", TEXT)],
    ]
    for i, segs in enumerate(rows):
        if segs:
            draw_segments(draw, CODE_X, y, segs)
        if i == 4:
            store_x = CODE_X + text_w("  router.post('login', [controllers.Session, '")
            underline_y = y + LINE_H - 6
            draw.line((store_x, underline_y, store_x + text_w("store"), underline_y), fill=ACCENT, width=2)
            hint = "Ctrl+Click → SessionController#store"
            hw = text_w(hint, UI_FONT) + 20
            draw.rectangle((store_x, y - 34, store_x + hw, y - 8), fill=POPUP_BG, outline=ACCENT)
            draw.text((store_x + 10, y - 28), hint, fill=ACCENT, font=UI_FONT)
        y += LINE_H
    return img


def shot_ace_generators() -> Image.Image:
    files = [("app/", False), ("database/", False), ("start/", False), ("adonisrc.ts", True)]
    img, draw, y = base(
        [("adonisrc.ts", True)],
        files,
        6,
        "Adonis → New… — Ace make:controller, model, migration, Wire, and more",
        status="AdonisJS · Ace ready",
    )
    draw_segments(draw, CODE_X, y, [Segment("import ", KEYWORD), Segment("{ defineConfig } ", TYPE), Segment("from ", KEYWORD), Segment("'@adonisjs/core/app'", STRING)])
    y += LINE_H * 2
    draw_segments(draw, CODE_X, y, [Segment("// Use the Adonis menu or IDE New… dialog", COMMENT)])
    y += LINE_H
    draw_segments(draw, CODE_X, y, [Segment("export default ", KEYWORD), Segment("defineConfig({", TEXT)])

    menu = [
        ("Adonis", False),
        ("  Rebuild Index", False),
        ("  New…", True),
        ("    make:controller", False),
        ("    make:model", False),
        ("    make:migration", False),
        ("    make:validator", False),
        ("    make:wire", False),
    ]
    draw_menu(draw, SIDEBAR_W + 280, TITLE_H + TAB_H + 60, menu, width=300)
    return img


def shot_tool_window() -> Image.Image:
    files = [("app/", False), ("resources/views/", False), ("start/routes.ts", True)]
    img, draw, y = base(
        [("routes.ts", True)],
        files,
        8,
        "Adonis tool window — routes, views, and index health at a glance",
        status="AdonisJS · 24 routes · 18 views · Index OK",
    )
    rows = [
        [Segment("import ", KEYWORD), Segment("router ", TEXT), Segment("from ", KEYWORD), Segment("'@adonisjs/core/services/router'", STRING)],
        [],
        [Segment("router.", TEXT), Segment("get", FUNC), Segment("('/', ", TEXT), Segment("'#controllers/home.index'", STRING), Segment(")", TEXT)],
        [Segment("router.", TEXT), Segment("get", FUNC), Segment("('/posts', ", TEXT), Segment("'#controllers/posts.index'", STRING), Segment(")", TEXT)],
        [Segment("router.", TEXT), Segment("post", FUNC), Segment("('/login', ", TEXT), Segment("'#controllers/session.store'", STRING), Segment(")", TEXT)],
    ]
    for segs in rows:
        if segs:
            draw_segments(draw, CODE_X, y, segs)
        y += LINE_H

    # Right tool window panel
    panel_x = W - 320
    panel_top = TITLE_H + TAB_H
    panel_bottom = H - STATUS_H - CAPTION_H
    draw.rectangle((panel_x, panel_top, W, panel_bottom), fill=SIDEBAR_BG)
    draw.line((panel_x, panel_top, panel_x, panel_bottom), fill=BORDER, width=1)
    draw.rectangle((panel_x, panel_top, W, panel_top + 36), fill=TAB_BG)
    draw.text((panel_x + 16, panel_top + 10), "Adonis", fill=ADONIS, font=UI_BOLD)
    draw.text((panel_x + 16, panel_top + 48), "Index", fill=TEXT_DIM, font=UI_FONT)
    draw.text((panel_x + 16, panel_top + 72), "✓ Healthy  ·  42 ms", fill=(6, 125, 23), font=UI_FONT)
    draw.text((panel_x + 16, panel_top + 110), "Routes", fill=TEXT_DIM, font=UI_FONT)
    for i, name in enumerate(["home", "posts.index", "auth.login", "auth.register"]):
        draw.text((panel_x + 24, panel_top + 136 + i * 26), name, fill=TEXT, font=FONT)
    draw.text((panel_x + 16, panel_top + 260), "Views", fill=TEXT_DIM, font=UI_FONT)
    for i, name in enumerate(["pages/home", "pages/auth/login", "layouts/main"]):
        draw.text((panel_x + 24, panel_top + 286 + i * 26), name, fill=TEXT, font=FONT)

    # Stripe icon hint
    draw.rectangle((W - 28, panel_top + 80, W - 8, panel_top + 100), fill=ADONIS)
    return img


def main() -> None:
    shots = [
        ("01-edge-templates.png", shot_edge_templates),
        ("02-edge-directives.png", shot_edge_directives),
        ("03-view-completion.png", shot_view_completion),
        ("04-route-completion.png", shot_route_completion),
        ("05-controller-navigation.png", shot_controller_navigation),
        ("06-ace-generators.png", shot_ace_generators),
        ("07-adonis-tool-window.png", shot_tool_window),
    ]

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    print(f"Writing Marketplace screenshots to {OUT_DIR}/ ({W}×{H})")
    for name, fn in shots:
        path = OUT_DIR / name
        img = fn()
        assert img.size[0] >= 1200 and img.size[1] >= 760, img.size
        img.save(path, "PNG", optimize=True)
        kb = path.stat().st_size / 1024
        print(f"  {name}: {img.size[0]}×{img.size[1]}  {kb:.1f} KB")
    print("Done. Upload PNGs from docs/marketplace/ in the Marketplace Media section.")


if __name__ == "__main__":
    main()
