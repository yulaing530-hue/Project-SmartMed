import re
import json
import sqlite3
import unicodedata
from collections import Counter
from datetime import datetime
from pathlib import Path

from fastapi import FastAPI, UploadFile, File
from rapidocr import RapidOCR

app = FastAPI()
engine = RapidOCR()

DB = "meds.db"
DRUGS_DB = Path(__file__).resolve().parents[1] / "data" / "drugs.db"

NOTICE = "成分比對僅供參考，是否重複或有交互作用，請詢問醫師或藥師。"


# ---------- 辨識紀錄資料庫 ----------
def init_db():
    with sqlite3.connect(DB) as con:
        con.execute("""CREATE TABLE IF NOT EXISTS scans (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            created_at TEXT,
            meds_json TEXT
        )""")


init_db()

# ---------- 解析規則 ----------
DOSE = re.compile(r"([\d.]+)\s*(粒|錠|顆|包|片|粉)?\s*[xX×*]\s*(\d+)\s*[xX×*]\s*(\d+)")
CJK = re.compile(r"[\u4e00-\u9fff]")


def center_y(box):
    return sum(p[1] for p in box) / len(box)


def is_drug_name(t):
    return (not CJK.search(t)
            and re.search(r"[A-Za-z]{4,}", t)
            and not re.search(r"x{3,}", t, re.I))


def clean_name(t):
    return re.sub(r"^[\d\s.]+", "", t).strip()


# ---------- 成分比對 ----------
STOP = {"TABLET", "TABLETS", "CAPSULE", "CAPSULES", "INJECTION", "SYRUP",
        "SOLUTION", "CREAM", "OINTMENT", "FILM", "COATED", "GRANULES",
        "POWDER", "SUSPENSION", "ORAL"}

BASE_SQL = """
SELECT COALESCE(b.base, i.name)
FROM drug_ingredients di
JOIN ingredients i ON i.id = di.ingredient_id
LEFT JOIN ingredient_base b ON b.name = i.name
WHERE di.license_no = ?
"""


def norm(s):
    s = unicodedata.normalize("NFKC", s or "").upper()
    return re.sub(r"\s+", " ", s).strip()


def drug_con():
    if not DRUGS_DB.exists():
        return None
    return sqlite3.connect(DRUGS_DB)


def lookup_ingredients(con, raw_name):
    tokens = [t for t in re.findall(r"[A-Z]{4,}", norm(raw_name)) if t not in STOP]
    for t in tokens:
        lics = [r[0] for r in con.execute(
            "SELECT d.license_no FROM drugs d WHERE d.name_en_norm LIKE ? "
            "AND EXISTS (SELECT 1 FROM drug_ingredients x "
            "WHERE x.license_no = d.license_no)", (f"%{t}%",))]
        how = f"品名含 {t}"
        if not lics:
            lics = [r[0] for r in con.execute(
                "SELECT DISTINCT di.license_no FROM drug_ingredients di "
                "JOIN ingredients i ON i.id = di.ingredient_id "
                "WHERE i.name LIKE ?", (f"{t}%",))]
            how = f"成分開頭 {t}"
        if not lics:
            continue
        combos = Counter()
        for lic in lics:
            combos[tuple(sorted({r[0] for r in con.execute(BASE_SQL, (lic,))}))] += 1
        top, cnt = combos.most_common(1)[0]
        return {"ingredients": list(top), "how": how,
                "licenses": len(lics), "confidence": round(cnt / len(lics), 2)}
    return {"ingredients": [], "how": None, "licenses": 0, "confidence": 0}


def find_duplicates(meds):
    owners = {}
    for m in meds:
        for ing in m.get("ingredients", []):
            owners.setdefault(ing, []).append(m["name"])
    return [{"ingredient": k, "meds": v} for k, v in owners.items() if len(v) > 1]


# ---------- API ----------
@app.get("/")
def home():
    return {"message": "伺服器運作中"}


@app.post("/upload")
async def upload(file: UploadFile = File(...)):
    data = await file.read()
    result = engine(data)
    if result.txts is None:
        return {"filename": file.filename, "meds": [], "duplicates": [], "notice": NOTICE}

    items = list(zip(result.boxes, result.txts))
    names = sorted(
        [(center_y(b), clean_name(t)) for b, t in items if is_drug_name(t)]
    )
    doses = sorted(
        [(center_y(b), DOSE.search(t)) for b, t in items if DOSE.search(t)],
        key=lambda x: x[0],
    )

    meds = []
    for (_, name), (_, m) in zip(names, doses):
        per, times, days = float(m.group(1)), int(m.group(3)), int(m.group(4))
        meds.append({
            "name": name,
            "dose_per_time": per,
            "unit": m.group(2),
            "times_per_day": times,
            "days": days,
            "total": round(per * times * days, 1),
        })

    con = drug_con()
    if con:
        for m in meds:
            info = lookup_ingredients(con, m["name"])
            m["ingredients"] = info["ingredients"]
            m["match"] = {"how": info["how"], "licenses": info["licenses"],
                          "confidence": info["confidence"]}
        con.close()

    warning = None
    if len(names) != len(doses):
        warning = f"藥名 {len(names)} 筆、劑量 {len(doses)} 筆，數量不一致，請人工確認"

    with sqlite3.connect(DB) as c:
        c.execute(
            "INSERT INTO scans (created_at, meds_json) VALUES (?, ?)",
            (datetime.now().isoformat(timespec="seconds"),
             json.dumps(meds, ensure_ascii=False)),
        )

    return {
        "filename": file.filename,
        "meds": meds,
        "duplicates": find_duplicates(meds),
        "warning": warning,
        "notice": NOTICE,
        "names_found": [n for _, n in names],
    }


@app.get("/history")
def history():
    with sqlite3.connect(DB) as c:
        rows = c.execute(
            "SELECT id, created_at, meds_json FROM scans ORDER BY id DESC"
        ).fetchall()
    return [{"id": r[0], "created_at": r[1], "meds": json.loads(r[2])} for r in rows]