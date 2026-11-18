#!/usr/bin/env python3
"""Fast zero-dependency static checks for the isolated Tsuzuri keyboard web demo."""
from html.parser import HTMLParser
import json
import re
from pathlib import Path

ROOT = Path(__file__).parent
class Page(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids = []
        self.resources = []
    def handle_starttag(self, tag, attrs):
        attr = dict(attrs)
        if "id" in attr:
            self.ids.append(attr["id"])
        if tag in {"script", "link"}:
            target = attr.get("src") or attr.get("href")
            if target and target.startswith("./"):
                self.resources.append(target[2:])

page = Page()
page.feed((ROOT / "index.html").read_text())
assert len(page.ids) == len(set(page.ids)), "duplicate element IDs"
expected = {
    "device", "draft", "toggleNativeKeyboard", "keyboardContent",
    "view-keyboard", "view-voice", "view-appearance", "view-about",
    "numberRow", "roundKeys", "oneHanded", "layoutChoice", "quickLayout",
    "fontSize", "themeOptions", "voiceSample", "voiceResult", "page-title",
}
assert expected.issubset(set(page.ids)), sorted(expected - set(page.ids))
assert all((ROOT / name).is_file() for name in page.resources), page.resources

html = (ROOT / "index.html").read_text()
js = (ROOT / "app.js").read_text()
css = (ROOT / "styles.css").read_text()
sw = (ROOT / "sw.js").read_text()
manifest = json.loads((ROOT / "manifest.webmanifest").read_text())
assert 'inputmode="none"' in html, "simulated key taps must not pop the real soft keyboard"
assert "navigator.clipboard" not in js, "browser sandbox must not touch system clipboard"
assert not re.search(r"(?m)^\\s+\\$\\([^\\n]*\\)\\.forEach", js), "querySelector returns an Element, not a NodeList: use $ for forEach"
assert "$('.tool').forEach" in js, "all keyboard toolbar buttons must be bound"
assert "$('[data-viewport]').forEach" in js, "all device presets must be bound"
assert "getUserMedia" not in js, "browser simulator must not record microphone"
assert "serviceWorker" in js and "register('./sw.js')" in js
assert "fetch(" not in js, "no network requests from simulator keyboard"
assert "@import" not in css and "fonts.googleapis" not in css
assert "keys.filter(k=>k.startsWith(PREFIX)" in sw, "cache cleanup must be namespaced"
assert manifest["display"] == "standalone"
assert manifest["scope"] == "./" and manifest["start_url"] == "./"
for icon in manifest["icons"]:
    assert (ROOT / icon["src"].removeprefix("./")).exists(), icon
print(f"Tsuzuri PWA static contract: PASS ({len(page.ids)} element IDs, {len(page.resources)} linked resources)")
