import sqlite3
from collections import Counter
from pathlib import Path


def find_root():
    for p in Path(__file__).resolve().parents:
        if (p / "data" / "drugs.db").exists():
            return p
    raise SystemExit("找不到 data/drugs.db")


con = sqlite3.connect(find_root() / "data" / "drugs.db")
rows = con.execute(
    "SELECT d.category, d.name_en, d.name_zh, d.dosage_form FROM drugs d "
    "LEFT JOIN drug_ingredients di ON di.license_no = d.license_no "
    "WHERE di.license_no IS NULL"
).fetchall()

print("成分為空的許可證:", len(rows))
print("\n藥品類別:")
for k, v in Counter(r[0] for r in rows).most_common(8):
    print(" ", repr(k), v)
print("\n劑型:")
for k, v in Counter(r[3] for r in rows).most_common(8):
    print(" ", repr(k), v)
print("\n範例:")
for r in rows[:10]:
    print(" ", r[1], "|", r[2])