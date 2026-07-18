import json
import math
import re
import unicodedata
from pathlib import Path

import pandas as pd


ROOT = Path(__file__).resolve().parents[1]
XLSX = Path(r"C:/Users/guswn/Downloads/Keyxif_키보드_벤더스튜디오_통합조사_2026-07-19.xlsx")
ANDROID_OUT = ROOT / "android/app/src/main/java/com/keyxif/app/data/presets/ImportedKeyboardPresetData.kt"
WEB_PRESETS = ROOT / "web/data/presets.js"

BRAND_MAP = {
    "Baionlenja": ("baionlenja", "Baionlenja", "baionlenja", ["Baionlenja"]),
    "Bowl": ("bowl", "Bowl Keyboards", "bowl", ["Bowl"]),
    "CannonKeys": ("cannonkeys", "CannonKeys", "cannonkeys", ["Cannon", "CK"]),
    "Cherry": ("cherry", "Cherry", "cherry", ["Cherry MX"]),
    "Fox Lab": ("fox", "Fox Lab", "fox", ["Fox", "FOX"]),
    "Geonworks": ("geonworks", "Geonworks", "geon", ["Geon"]),
    "GOK": ("gok", "GOK Designs", "gok", ["GOK"]),
    "Graystudio": ("graystudio", "Graystudio", "graystudio", ["Gray", "Gray Studio"]),
    "HHKB": ("hhkb", "HHKB", "hhkb", ["Happy Hacking", "Topre"]),
    "IV Works": ("iv-works", "IV Works", "iv-works", ["IVWorks"]),
    "JJW Concepts": ("jjw", "JJW Concepts", "jjw", ["JJW"]),
    "KBDfans": ("kbdfans", "KBDfans", "kbdfans", ["Kbdfans"]),
    "KLC": ("klc", "KLC", "klc", ["Klc"]),
    "KMG": ("kmg", "KMG", "kmg", []),
    "Keycult": ("keycult", "Keycult", "keycult", []),
    "Linworks": ("lin", "Linworks", "lin", ["Lin"]),
    "Machina": ("machina", "Machina", "machina", []),
    "Matrix Lab": ("matrix-lab", "Matrix Lab", "matrix", ["Matrix", "MatrixLab"]),
    "Meletrix": ("meletrix", "Meletrix", "meletrix", ["Zoom"]),
    "Merisi": ("merisi", "Merisi", "merisi", []),
    "Mode Designs": ("mode", "Mode Designs", "mode", ["Mode"]),
    "Neo": ("neo", "Neo", "neo", ["Neo Studio"]),
    "NewOne": ("newone", "NewOne", "newone", ["Newone"]),
    "Niuniu": ("niuniu", "NiuNiu", "niuniu", ["Niu Niu", "Nunu"]),
    "NovelKeys": ("novelkeys", "NovelKeys", "novelkeys", ["NK"]),
    "Nuxros": ("nuxros", "Nuxros", "nuxros", []),
    "OTD": ("otd", "OTD", "otd", ["On The Desk"]),
    "Omnitype": ("omnitype", "Omnitype", "omnitype", []),
    "Orion": ("orion", "Orion", "orion", []),
    "Owlab": ("owlab", "Owlab", "owlab", ["OWL"]),
    "Plex Studio": ("plex", "Plex Studio", "plex", ["Plex", "Plexkbd"]),
    "Qwertykeys": ("qwertykeys", "Qwertykeys", "qwertykeys", ["QK"]),
    "Realforce": ("realforce", "REALFORCE", "realforce", ["Topre", "Realforce"]),
    "Sensy": ("sensy", "SENSY", "sensy", []),
    "SingaKBD": ("singakbd", "SingaKBD", "singakbd", ["Singa"]),
    "Swagkeys": ("swagkey", "Swagkeys", "swagkey", ["Swagkey"]),
    "Syryan": ("syryan", "Syryan", "syryan", []),
    "TGR": ("tgr", "TGR", "tgr", []),
    "TKD": ("tkd", "TKD", "tkd", ["TheKeyDotCo"]),
    "Typface": ("typface", "Typface", "typface", []),
    "Wuque Studio": ("wuque", "Wuque Studio", "wuque", ["WS", "Wuque"]),
}


def clean(value):
    if value is None:
        return None
    if isinstance(value, float) and math.isnan(value):
        return None
    text = str(value).strip()
    return text or None


def slug(value):
    value = unicodedata.normalize("NFKD", value)
    value = value.lower().replace("&", " and ")
    value = re.sub(r"[^a-z0-9]+", "-", value).strip("-")
    return value or "item"


def kstr(value):
    return json.dumps(value, ensure_ascii=False)


def list_expr(values):
    values = [value for value in values if value]
    if not values:
        return "emptyList()"
    return "listOf(" + ", ".join(kstr(value) for value in values) + ")"


def uniq(values):
    out = []
    seen = set()
    for value in values:
        value = clean(value)
        if not value:
            continue
        key = value.casefold()
        if key in seen:
            continue
        seen.add(key)
        out.append(value)
    return out


def imported_presets():
    raw = pd.read_excel(XLSX, sheet_name="키보드_마스터")
    df = raw[raw["enabled"] == True].copy()

    vendors_by_id = {}
    housings = []
    for _, row in df.iterrows():
        brand_raw = clean(row.get("brand")) or "Unknown"
        vendor_id, vendor_name, logo_id, vendor_aliases = BRAND_MAP.get(
            brand_raw,
            (slug(brand_raw), brand_raw, None, []),
        )
        vendors_by_id[vendor_id] = {
            "id": vendor_id,
            "name": vendor_name,
            "logoId": logo_id,
            "aliases": vendor_aliases,
        }
        model = clean(row.get("model")) or clean(row.get("display_name")) or clean(row.get("keyboard_id")) or "Untitled"
        display = clean(row.get("display_name")) or f"{vendor_name} {model}"
        aliases = uniq([
            display,
            f"{brand_raw} {model}",
            brand_raw,
            clean(row.get("layout")),
            clean(row.get("series_or_variant")),
            clean(row.get("release_status")),
        ])
        if display.casefold() == model.casefold():
            aliases = [alias for alias in aliases if alias.casefold() != model.casefold()]
        housings.append({
            "id": clean(row.get("keyboard_id")) or f"{vendor_id}-{slug(model)}",
            "name": model,
            "vendorId": vendor_id,
            "vendor": vendor_name,
            "designer": None,
            "logoId": logo_id,
            "aliases": aliases,
        })

    seen = set()
    deduped_housings = []
    for housing in housings:
        key = (housing["vendorId"].casefold(), housing["name"].casefold())
        if key in seen:
            continue
        seen.add(key)
        deduped_housings.append(housing)

    vendors = sorted(vendors_by_id.values(), key=lambda item: item["name"].casefold())
    housings = sorted(deduped_housings, key=lambda item: (item["vendor"].casefold(), item["name"].casefold()))
    return vendors, housings


def write_android(vendors, housings):
    chunks = [housings[index:index + 100] for index in range(0, len(housings), 100)]
    lines = [
        "package com.keyxif.app.data.presets",
        "",
        "import com.keyxif.app.domain.model.HousingPreset",
        "import com.keyxif.app.domain.model.VendorPreset",
        "",
        "// Generated from Keyxif_키보드_벤더스튜디오_통합조사_2026-07-19.xlsx.",
        "// Keep manual overrides in PresetData.kt; imported lists are appended and de-duplicated there.",
        "internal object ImportedKeyboardPresetData {",
        "    val vendors: List<VendorPreset> = listOf(",
    ]
    for vendor in vendors:
        logo = "null" if vendor["logoId"] is None else kstr(vendor["logoId"])
        lines.append(
            f"        VendorPreset(id = {kstr(vendor['id'])}, name = {kstr(vendor['name'])}, "
            f"logoId = {logo}, aliases = {list_expr(vendor['aliases'])}),"
        )
    lines.extend([
        "    )",
        "",
        "    val housings: List<HousingPreset> = " + " + ".join(f"housingsChunk{index}()" for index in range(len(chunks))),
        "",
    ])
    for index, chunk in enumerate(chunks):
        lines.append(f"    private fun housingsChunk{index}(): List<HousingPreset> = listOf(")
        for housing in chunk:
            logo = "null" if housing["logoId"] is None else kstr(housing["logoId"])
            lines.append(
                f"        HousingPreset(id = {kstr(housing['id'])}, name = {kstr(housing['name'])}, "
                f"vendorId = {kstr(housing['vendorId'])}, vendor = {kstr(housing['vendor'])}, "
                f"designer = null, logoId = {logo}, aliases = {list_expr(housing['aliases'])}),"
            )
        lines.append("    )")
        lines.append("")
    lines.append("}")
    ANDROID_OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")


def merge_unique(new_items, old_items, key_fn):
    out = []
    seen = set()
    for item in list(new_items) + list(old_items):
        key = key_fn(item)
        if key in seen:
            continue
        seen.add(key)
        out.append(item)
    return out


def write_web(vendors, housings):
    text = WEB_PRESETS.read_text(encoding="utf-8")
    match = re.search(r"window\.KEYXIF_PRESETS\s*=\s*(\{.*?\n\});", text, re.S)
    if not match:
        raise RuntimeError("Could not find KEYXIF_PRESETS object")
    data = json.loads(match.group(1))
    data["vendors"] = merge_unique(
        vendors,
        data.get("vendors", []),
        lambda item: str(item.get("id", "")).casefold(),
    )
    data["housings"] = merge_unique(
        housings,
        data.get("housings", []),
        lambda item: (
            str(item.get("vendorId") or "").casefold(),
            str(item.get("name") or "").casefold(),
        ),
    )
    new_object = json.dumps(data, ensure_ascii=False, indent=1)
    WEB_PRESETS.write_text(text[:match.start(1)] + new_object + text[match.end(1):], encoding="utf-8")


def main():
    vendors, housings = imported_presets()
    write_android(vendors, housings)
    write_web(vendors, housings)
    print(f"generated vendors={len(vendors)} housings={len(housings)}")
    print(f"android={ANDROID_OUT}")
    print(f"web={WEB_PRESETS}")


if __name__ == "__main__":
    main()
