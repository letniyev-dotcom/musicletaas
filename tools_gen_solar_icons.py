#!/usr/bin/env python3
"""Fetch Solar icons from the Iconify API and emit Android vector drawables.

We try candidate Solar icon names per slot and use the first that returns 200.
Tinting is done by Compose's Icon(tint=...), so fillColor here is a placeholder.
"""
import re, sys, urllib.request, urllib.parse, os

OUT = "app/src/main/res/drawable"
os.makedirs(OUT, exist_ok=True)

ICONS = {
    "sol_home":       ["home-2-bold", "home-angle-bold", "home-smile-bold"],
    "sol_home_out":   ["home-2-linear", "home-angle-linear", "home-smile-linear"],
    "sol_search":     ["magnifer-bold", "minimalistic-magnifer-bold", "magnifer-linear"],
    "sol_search_out": ["magnifer-linear", "minimalistic-magnifer-linear"],
    "sol_library":    ["library-bold", "music-library-2-bold", "playlist-2-bold"],
    "sol_library_out":["library-linear", "music-library-2-linear", "playlist-2-linear"],
    "sol_play":       ["play-bold"],
    "sol_pause":      ["pause-bold"],
    "sol_next":       ["skip-next-bold", "rewind-forward-bold", "next-bold"],
    "sol_prev":       ["skip-previous-bold", "rewind-back-bold", "previous-bold"],
    "sol_heart":      ["heart-bold"],
    "sol_heart_out":  ["heart-linear", "heart-outline"],
    "sol_note":       ["music-note-bold", "music-notes-bold", "music-note-2-bold"],
    "sol_wave":       ["soundwave-bold", "soundwave-linear", "soundwave-circle-bold"],
    "sol_arrow_down": ["alt-arrow-down-linear", "alt-arrow-down-bold"],
    "sol_close":      ["close-circle-bold", "close-circle-linear"],
    "sol_shuffle":    ["shuffle-bold", "shuffle-linear"],
}

def fetch_svg(prefix, name):
    url = f"https://api.iconify.design/{prefix}/{name}.svg"
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (icon-fetch)"})
        with urllib.request.urlopen(req, timeout=20) as r:
            if r.status != 200:
                return None
            body = r.read().decode("utf-8")
            if "<path" not in body and "<svg" not in body:
                return None
            if body.strip().lower().startswith("<?xml") is False and "<svg" not in body:
                return None
            return body
    except Exception:
        return None

def parse_viewbox(svg):
    m = re.search(r'viewBox="([\d.\- ]+)"', svg)
    if m:
        p = m.group(1).split()
        if len(p) == 4:
            return float(p[2]), float(p[3])
    return 24.0, 24.0

def extract_paths(svg):
    """Return list of (pathData, evenodd: bool)."""
    out = []
    for m in re.finditer(r'<path\b([^>]*?)/?>', svg, re.S):
        attrs = m.group(1)
        d = re.search(r'\bd="([^"]+)"', attrs)
        if not d:
            continue
        evenodd = ('evenodd' in attrs)
        out.append((d.group(1), evenodd))
    return out

def to_vector(name, svg):
    w, h = parse_viewbox(svg)
    paths = extract_paths(svg)
    if not paths:
        return None
    lines = [
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="24dp"',
        f'    android:height="24dp"',
        f'    android:viewportWidth="{w:g}"',
        f'    android:viewportHeight="{h:g}">',
    ]
    for d, evenodd in paths:
        d = d.replace('"', '&quot;')
        ft = '\n        android:fillType="evenOdd"' if evenodd else ''
        lines.append(f'    <path\n        android:fillColor="#FFFFFFFF"{ft}\n        android:pathData="{d}"/>')
    lines.append('</vector>')
    return "\n".join(lines) + "\n"

def main():
    ok, fail = [], []
    for res, candidates in ICONS.items():
        wrote = False
        for cand in candidates:
            svg = fetch_svg("solar", cand)
            if not svg:
                continue
            xml = to_vector(res, svg)
            if not xml:
                continue
            with open(os.path.join(OUT, f"{res}.xml"), "w") as f:
                f.write(xml)
            ok.append(f"{res} <- solar:{cand}")
            wrote = True
            break
        if not wrote:
            fail.append(res)
    print("WROTE:")
    for o in ok:
        print("  ", o)
    if fail:
        print("FAILED:", fail)
        sys.exit(1)

if __name__ == "__main__":
    main()
