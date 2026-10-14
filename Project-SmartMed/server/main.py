import re
import json
import sqlite3
from datetime import datetime

from fastapi import FastAPI, UploadFile, File
from rapidocr import RapidOCR

app = FastAPI()
engine = RapidOCR()

DB = "meds.db"

# ---------- 資料庫 ----------
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

# ---------- API ----------
@app.get("/")
def home():
    return {"message": "伺服器運作中"}

@app.post("/upload")
async def upload(file: UploadFile = File(...)):
    data = await file.read()
    result = engine(data)
    if result.txts is None:
        return {"filename": file.filename, "meds": []}

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

    warning = None
    if len(names) != len(doses):
        warning = f"藥名 {len(names)} 筆、劑量 {len(doses)} 筆，數量不一致，請人工確認"

    with sqlite3.connect(DB) as con:
        con.execute(
            "INSERT INTO scans (created_at, meds_json) VALUES (?, ?)",
            (datetime.now().isoformat(timespec="seconds"),
             json.dumps(meds, ensure_ascii=False)),
        )

    return {
        "filename": file.filename,
        "meds": meds,
        "warning": warning,
        "names_found": [n for _, n in names],
        "lines": list(result.txts),
    }

@app.get("/history")
def history():
    with sqlite3.connect(DB) as con:
        rows = con.execute(
            "SELECT id, created_at, meds_json FROM scans ORDER BY id DESC"
        ).fetchall()
    return [{"id": r[0], "created_at": r[1], "meds": json.loads(r[2])} for r in rows]
