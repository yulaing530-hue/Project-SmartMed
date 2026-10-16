import csv, re, sqlite3, unicodedata
from collections import Counter
from pathlib import Path


def find_root():
    for p in Path(__file__).resolve().parents:
        if (p / "data" / "drug_license.csv").exists():
            return p
    raise SystemExit("找不到 data/drug_license.csv")


ROOT = find_root()
CSV_PATH = ROOT / "data" / "drug_license.csv"
DB_PATH = ROOT / "data" / "drugs.db"

# 同一成分的不同名稱（先放一個，之後請藥師或老師協助補充）
SYNONYMS = {"ACETAMINOPHEN": "PARACETAMOL"}


def norm(s):
    s = unicodedata.normalize("NFKC", s or "").upper()
    return re.sub(r"\s+", " ", s).strip()


def split_ingredients(raw):
    out = []
    for part in norm(raw).split(";;"):
        part = re.sub(r"\(.*?\)", "", part)
        part = re.sub(r"\s+", " ", part).strip(" ;,)")
        if part:
            out.append(SYNONYMS.get(part, part))
    return out


def build():
    if DB_PATH.exists():
        DB_PATH.unlink()
    con = sqlite3.connect(DB_PATH)
    con.executescript("""
    CREATE TABLE drugs(license_no TEXT PRIMARY KEY, name_zh TEXT, name_en TEXT,
        name_en_norm TEXT, dosage_form TEXT, category TEXT, ingredients_raw TEXT);
    CREATE TABLE ingredients(id INTEGER PRIMARY KEY, name TEXT UNIQUE);
    CREATE TABLE drug_ingredients(license_no TEXT, ingredient_id INTEGER,
        PRIMARY KEY (license_no, ingredient_id));
    CREATE INDEX idx_name_en ON drugs(name_en_norm);
    """)
    ids, rows, empty = {}, 0, 0
    with open(CSV_PATH, encoding="utf-8-sig", newline="") as f:
        for r in csv.DictReader(f):
            rows += 1
            lic = r["許可證字號"]
            ings = split_ingredients(r["主成分略述"])
            if not ings:
                empty += 1
            con.execute(
                "INSERT OR IGNORE INTO drugs VALUES (?,?,?,?,?,?,?)",
                (lic, r["中文品名"], r["英文品名"], norm(r["英文品名"]),
                 r["劑型"], r["藥品類別"], r["主成分略述"]))
            for name in set(ings):
                if name not in ids:
                    cur = con.execute("INSERT INTO ingredients(name) VALUES (?)", (name,))
                    ids[name] = cur.lastrowid
                con.execute("INSERT OR IGNORE INTO drug_ingredients VALUES (?,?)",
                            (lic, ids[name]))
    con.commit()
    return con, rows, empty


def lookup(con, text):
    for t in re.findall(r"[A-Z]{4,}", norm(text)):
        lics = [x[0] for x in con.execute(
            "SELECT license_no FROM drugs WHERE name_en_norm LIKE ?", (f"%{t}%",))]
        how = f"品名含 {t}"
        if not lics:
            lics = [x[0] for x in con.execute(
                "SELECT DISTINCT di.license_no FROM drug_ingredients di "
                "JOIN ingredients i ON i.id=di.ingredient_id WHERE i.name LIKE ?",
                (f"{t}%",))]
            how = f"成分開頭 {t}"
        if lics:
            c = Counter()
            for lic in lics:
                names = tuple(sorted(x[0] for x in con.execute(
                    "SELECT i.name FROM drug_ingredients di "
                    "JOIN ingredients i ON i.id=di.ingredient_id "
                    "WHERE di.license_no=?", (lic,))))
                c[names] += 1
            return how, len(lics), c.most_common(3)
    return None, 0, []


if __name__ == "__main__":
    con, rows, empty = build()
    n_drugs = con.execute("SELECT COUNT(*) FROM drugs").fetchone()[0]
    n_ings = con.execute("SELECT COUNT(*) FROM ingredients").fetchone()[0]
    print(f"CSV 筆數: {rows}，不重複許可證: {n_drugs}，成分欄為空: {empty}，成分種類: {n_ings}")

    print("\n最常見的 10 種成分:")
    for name, cnt in con.execute(
            "SELECT i.name, COUNT(*) c FROM drug_ingredients di "
            "JOIN ingredients i ON i.id=di.ingredient_id "
            "GROUP BY i.id ORDER BY c DESC LIMIT 10"):
        print(f"  {name}: {cnt}")

    print("\n收據上的藥:")
    for q in ["AMOXICILLIN 500", "VOREN", "Oxolamine citrate",
              "Primalan(mequitaz", "Famotidine 20MG"]:
        how, n, top = lookup(con, q)
        print(f"- {q} -> {how}，{n} 筆")
        for names, cnt in top:
            print(f"    {cnt} 筆: {', '.join(names) or '(成分欄為空)'}")