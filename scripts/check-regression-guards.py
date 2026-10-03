#!/usr/bin/env python3
"""Fail CI when known Adonis Idea regressions can reappear.

Guards:
  1. SVG icons must be well-formed XML with no illegal control bytes
     (blank tool-window / *.edge icons in dark UI).
  2. Index rebuild entrypoints must not block the EDT.
  3. @form must remain an Edge builtin; completion must stopHere on DIRECTIVE.
  4. plugin.xml must keep icon / typedHandler / completion wiring.
"""
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []


def err(msg: str) -> None:
    ERRORS.append(msg)


def check_toolwindow_icon() -> None:
    """Tool-window icons must be monochrome gray so New UI can recolor them."""
    tw = ROOT / "src/main/resources/icons/adonisToolWindow.svg"
    if not tw.is_file():
        err("REGRESSION(icons): missing icons/adonisToolWindow.svg")
        return
    text = tw.read_text(encoding="utf-8")
    if "#5943FF" in text or "fill=\"#FFFFFF\"" in text or "fill='#FFFFFF'" in text:
        err(
            "REGRESSION(icons): adonisToolWindow.svg must be monochrome gray "
            "(not brand purple/white) so selected state paints blue"
        )
    if "#6C707E" not in text:
        err(
            "REGRESSION(icons): adonisToolWindow.svg should use JetBrains gray "
            "#6C707E for New UI recoloring"
        )
    xml = read("src/main/resources/META-INF/plugin.xml")
    if 'icon="/icons/adonisToolWindow.svg"' not in xml:
        err("REGRESSION(icons): toolWindow must use /icons/adonisToolWindow.svg")
    icons_kt = read("src/main/kotlin/icons/AdonisIcons.kt")
    if "adonisToolWindow.svg" not in icons_kt:
        err("REGRESSION(icons): AdonisIcons.ToolWindow must load adonisToolWindow.svg")


def check_svgs() -> None:
    roots = [
        ROOT / "src/main/resources/icons",
        ROOT / "src/main/resources/META-INF",
    ]
    svgs = []
    for base in roots:
        if base.is_dir():
            svgs.extend(sorted(base.glob("*.svg")))
    if not svgs:
        err("REGRESSION(icons): no SVG files found under resources/")
        return
    for path in svgs:
        data = path.read_bytes()
        rel = path.relative_to(ROOT)
        bad = [
            i
            for i, b in enumerate(data)
            if b < 32 and b not in (9, 10, 13)
        ]
        if bad:
            err(
                f"REGRESSION(icons): {rel} has illegal control byte(s) at "
                f"{bad[:5]} — blank icons in dark UI"
            )
        try:
            ET.fromstring(data)
        except ET.ParseError as exc:
            err(f"REGRESSION(icons): {rel} is not well-formed XML: {exc}")
        if b'fill="' not in data and b"fill='" not in data:
            err(f"REGRESSION(icons): {rel} has no fill= (likely invisible mark)")


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        err(f"missing source {rel}")
        return ""
    return path.read_text(encoding="utf-8")


def check_edt_contracts() -> None:
    tool = read("src/main/kotlin/dev/shamar/adonis/ide/AdonisToolWindowFactory.kt")
    if "rebuildAsync" not in tool:
        err("REGRESSION(edt): AdonisToolWindowFactory must call rebuildAsync")
    # Button listener must not invoke blocking rebuild() on the EDT.
    after = tool.split("rebuild.addActionListener", 1)
    if len(after) == 2 and ".rebuild()" in after[1].split("}", 1)[0]:
        err(
            "REGRESSION(edt): tool-window Rebuild button still calls .rebuild() "
            "on the EDT"
        )

    action = read("src/main/kotlin/dev/shamar/adonis/ide/RebuildIndexAction.kt")
    if "Task.Backgroundable" not in action:
        err("REGRESSION(edt): RebuildIndexAction must use Task.Backgroundable")

    threading = read("src/main/kotlin/dev/shamar/adonis/ide/AdonisIndexThreading.kt")
    if "shouldDeferRebuild" not in threading:
        err("REGRESSION(edt): AdonisIndexThreading.shouldDeferRebuild missing")
    if "isReadAccessAllowed" not in threading:
        err(
            "REGRESSION(edt): shouldDeferRebuild must consider ReadAction "
            "(refs/highlighting must not wait on Node)"
        )

    service = read("src/main/kotlin/dev/shamar/adonis/ide/AdonisProjectService.kt")
    if "AdonisIndexThreading.shouldDeferRebuild" not in service:
        err("REGRESSION(edt): AdonisProjectService must use AdonisIndexThreading")
    if "isReadAccessAllowed" not in service:
        err("REGRESSION(edt): AdonisProjectService must pass isReadAccessAllowed")

    refs = read("src/main/kotlin/dev/shamar/adonis/ide/AdonisReferenceContributor.kt")
    if "cachedIndex()" not in refs:
        err(
            "REGRESSION(edt): AdonisReferenceProvider must prefer cachedIndex() "
            "under ReadAction"
        )


def check_form_contracts() -> None:
    registry = read("src/main/kotlin/dev/shamar/adonis/ide/EdgeTagRegistry.kt")
    if '"form"' not in registry:
        err("REGRESSION(@form): EdgeTagRegistry must list form")
    contributor = read(
        "src/main/kotlin/dev/shamar/adonis/ide/AdonisCompletionContributor.kt"
    )
    if "stopHere()" not in contributor:
        err(
            "REGRESSION(@form): AdonisCompletionContributor must stopHere() "
            "on DIRECTIVE sites"
        )
    if "PrioritizedLookupElement" not in contributor:
        err(
            "REGRESSION(@form): directive lookups must use PrioritizedLookupElement"
        )
    catalog = read("src/main/kotlin/dev/shamar/adonis/ide/AdonisCompletionCatalog.kt")
    if "EdgeDirectiveSnippets.snippetNames()" not in catalog:
        err(
            "REGRESSION(@form): directiveCompletions must seed snippetNames() "
            "so form appears without a warm index"
        )
    if "wireEnabled" not in catalog:
        err(
            "REGRESSION(wire): directiveCompletions must gate wire/persist on "
            "index.wireEnabled"
        )
    index_kt = read("src/main/kotlin/dev/shamar/adonis/ide/AdonisIndex.kt")
    if "wireEnabled" not in index_kt or "val wire:" not in index_kt:
        err("REGRESSION(wire): FrameworkEntry.wire / wireEnabled missing")
    indexer = read("indexer/index.mjs")
    if "detectWire" not in indexer or "wire," not in indexer:
        err("REGRESSION(wire): indexer must emit framework.wire via detectWire")
    typed = read("src/main/kotlin/dev/shamar/adonis/ide/EdgeTypedHandler.kt")
    if "scheduleAutoPopup" not in typed:
        err("REGRESSION(@popup): EdgeTypedHandler must scheduleAutoPopup")
    check_fn = typed.split("fun checkAutoPopup", 1)
    if len(check_fn) < 2 or "scheduleAutoPopup" not in check_fn[1].split("fun charTyped", 1)[0]:
        err(
            "REGRESSION(@popup): checkAutoPopup must scheduleAutoPopup "
            "(JetBrains contract — Condition runs on up-to-date PSI)"
        )
    if "EdgeCompletionConfidence" not in read(
        "src/main/kotlin/dev/shamar/adonis/ide/EdgeCompletionConfidence.kt"
    ):
        err("REGRESSION(@popup): EdgeCompletionConfidence class missing")
    plugin = read("src/main/resources/META-INF/plugin.xml")
    if "EdgeCompletionConfidence" not in plugin:
        err("REGRESSION(@popup): plugin.xml must register EdgeCompletionConfidence")
    if 'completion.contributor' in plugin and 'order="first"' not in plugin:
        err("REGRESSION(@popup): completion.contributor should be order=first")


def check_plugin_xml() -> None:
    xml = read("src/main/resources/META-INF/plugin.xml")
    for needle in (
        'icon="/icons/adonis.svg"',
        "EdgeTypedHandler",
        "AdonisCompletionContributor",
        "AdonisToolWindowFactory",
        'id="Adonis"',
        "<depends>JavaScript</depends>",
        "<incompatible-with>com.intellij.modules.idea</incompatible-with>",
        "<incompatible-with>com.intellij.modules.idea.community</incompatible-with>",
    ):
        if needle not in xml:
            err(f"REGRESSION(plugin.xml): missing {needle!r}")
    # JetBrains Marketplace rejects display names containing "IDEA".
    import re

    props = read("gradle.properties")
    name_m = re.search(r"^pluginName\s*=\s*(.+)$", props, re.M)
    plugin_name = (name_m.group(1).strip() if name_m else "")
    if not plugin_name:
        err("REGRESSION(name): pluginName missing from gradle.properties")
    elif re.search(r"idea", plugin_name, re.I):
        err(
            f"REGRESSION(name): pluginName {plugin_name!r} contains 'IDEA' "
            "(JetBrains Marketplace rejection)"
        )
    xml_name = re.search(r"<name>([^<]+)</name>", xml)
    if xml_name and re.search(r"idea", xml_name.group(1), re.I):
        err(
            f"REGRESSION(name): plugin.xml <name>{xml_name.group(1)}</name> "
            "contains 'IDEA'"
        )


def main() -> int:
    check_svgs()
    check_toolwindow_icon()
    check_edt_contracts()
    check_form_contracts()
    check_plugin_xml()
    if ERRORS:
        print("Regression guards FAILED:", file=sys.stderr)
        for e in ERRORS:
            print(f"  - {e}", file=sys.stderr)
        return 1
    print(f"Regression guards OK ({ROOT.name})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
