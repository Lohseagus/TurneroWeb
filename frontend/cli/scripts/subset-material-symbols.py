#!/usr/bin/env python3
"""Rebuild the self-hosted Material Symbols subset.

The production page used to download the full variable font (~4 MB) from
fonts.gstatic.com. This script keeps only the ligatures the app renders,
drops the unused GRAD and opsz axes (the UI always uses the defaults GRAD 0
and opsz 24) and keeps FILL and wght, which the styles actually change.

Requires: fonttools, brotli, and (optionally) uharfbuzz to prove shaping
matches the full font. Run from frontend/cli:

    python3 scripts/subset-material-symbols.py
"""

from __future__ import annotations

import hashlib
import json
import subprocess
import urllib.request
from pathlib import Path

from fontTools.subset import Options, Subsetter
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parents[1]
FONT_DIR = ROOT / "public" / "fonts"
MANIFEST = ROOT / "src" / "fonts" / "material-symbols.manifest.json"
ICON_SCRIPT = ROOT / "scripts" / "material-symbol-icons.mjs"
# Same file cheturno.site inlines today (Material Symbols Outlined v385, full axes).
SOURCE_URL = (
    "https://fonts.gstatic.com/s/materialsymbolsoutlined/v385/"
    "kJEhBvYX7BgnkSrUwT8OhrdQw4oELdPIeeII9v6oFsI.woff2"
)


def collect_icons() -> list[str]:
    result = subprocess.run(
        ["node", str(ICON_SCRIPT)],
        check=True,
        capture_output=True,
        text=True,
    )
    return [line.strip() for line in result.stdout.splitlines() if line.strip()]


def ligature_map(font: TTFont) -> dict[tuple[str, ...], str]:
    gsub = font["GSUB"].table
    mapping: dict[tuple[str, ...], str] = {}
    for lookup in gsub.LookupList.Lookup:
        if lookup.LookupType == 7:
            tables = [(st.ExtensionLookupType, st.ExtSubTable) for st in lookup.SubTable]
        else:
            tables = [(lookup.LookupType, st) for st in lookup.SubTable]
        for typ, subtable in tables:
            if typ != 4:
                continue
            for first, ligatures in subtable.ligatures.items():
                for ligature in ligatures:
                    mapping[(first,) + tuple(ligature.Component)] = ligature.LigGlyph
    return mapping


def subset_font(source: TTFont, icons: list[str]) -> tuple[TTFont, list[str]]:
    cmap = source.getBestCmap()
    ligatures = ligature_map(source)
    order = set(source.getGlyphOrder())
    glyphs = {".notdef"}
    missing: list[str] = []
    for name in icons:
        try:
            sequence = tuple(cmap[ord(char)] for char in name)
        except KeyError:
            missing.append(name)
            continue
        glyphs.update(sequence)
        target = ligatures.get(sequence)
        if target is None:
            missing.append(name)
            continue
        glyphs.add(target)
        filled = f"{target}.fill"
        if filled in order:
            glyphs.add(filled)

    options = Options()
    options.layout_features = ["rlig", "rclt"]
    options.layout_scripts = ["DFLT", "latn"]
    # Closure would pull every ligature that can be spelled with a-z.
    options.layout_closure = False
    options.name_IDs = ["*"]
    options.notdef_outline = True
    options.recommended_glyphs = True
    options.glyph_names = True
    options.hinting = True
    subsetter = Subsetter(options=options)
    subsetter.populate(glyphs=glyphs)
    subsetter.subset(source)
    # The app only sets GRAD 0 and opsz 24. Pinning them keeps those outlines
    # and drops the axis data. FILL and wght stay variable.
    pinned = instantiateVariableFont(source, {"GRAD": 0, "opsz": 24}, inplace=False)
    pinned.flavor = "woff2"
    return pinned, missing


def shape(path: Path, text: str) -> tuple:
    import uharfbuzz as hb

    face = hb.Face(hb.Blob.from_file_path(str(path)))
    font = hb.Font(face)
    buf = hb.Buffer()
    buf.add_str(text)
    buf.guess_segment_properties()
    hb.shape(font, buf, {"rlig": True, "liga": True, "rclt": True})
    return tuple((font.glyph_to_string(info.codepoint), info.cluster) for info in buf.glyph_infos)


def assert_shaping_matches(full_ttf: Path, subset_ttf: Path, icons: list[str]) -> None:
    try:
        import uharfbuzz  # noqa: F401
    except ImportError:
        print("uharfbuzz not installed; skipped shaping check")
        return
    mismatches = []
    for name in icons:
        if shape(full_ttf, name) != shape(subset_ttf, name):
            mismatches.append(name)
    if mismatches:
        raise SystemExit(f"subset shaping diverges for: {', '.join(mismatches)}")
    print(f"shaping matches the full font for {len(icons)} icon names")


def main() -> None:
    icons = collect_icons()
    print(f"icons in source: {len(icons)}")
    raw = urllib.request.urlopen(SOURCE_URL, timeout=120).read()
    cache = Path("/tmp/material-symbols-full.woff2")
    cache.write_bytes(raw)
    source = TTFont(cache)
    subset, missing = subset_font(source, icons)
    if missing:
        print("no dedicated ligature (same fallback text as the full font):")
        for name in missing:
            print(f"  {name}")

    blob = subset_to_bytes(subset)
    digest = hashlib.sha256(blob).hexdigest()
    filename = f"material-symbols-outlined-{digest[:8].upper()}.woff2"
    FONT_DIR.mkdir(parents=True, exist_ok=True)
    for old in FONT_DIR.glob("material-symbols-outlined-*.woff2"):
        old.unlink()
    (FONT_DIR / filename).write_bytes(blob)

    full_ttf = Path("/tmp/material-symbols-full.ttf")
    subset_ttf = Path("/tmp/material-symbols-subset.ttf")
    full = TTFont(cache)
    full.flavor = None
    full.save(full_ttf)
    check = TTFont(FONT_DIR / filename)
    check.flavor = None
    check.save(subset_ttf)
    assert_shaping_matches(full_ttf, subset_ttf, icons)

    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    manifest = {
        "file": filename,
        "sha256": digest,
        "bytes": len(blob),
        "source": SOURCE_URL,
        "axes": ["FILL", "wght"],
        "icons": icons,
        "iconsWithoutLigature": missing,
    }
    MANIFEST.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {filename} ({len(blob)} bytes)")


def subset_to_bytes(font: TTFont) -> bytes:
    from io import BytesIO

    buffer = BytesIO()
    font.save(buffer)
    return buffer.getvalue()


if __name__ == "__main__":
    main()
