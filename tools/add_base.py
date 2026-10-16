import sqlite3
from collections import defaultdict
from pathlib import Path


def find_root():
    for p in Path(__file__).resolve().parents:
        if (p / "data" / "drugs.db").exists():
            return p
    raise SystemExit("找不到 data/drugs.db")


# 只移除名稱「最後面」的這些字
SUFFIX = {
    "HYDROCHLORIDE", "HCL", "HBR", "HYDROBROMIDE", "MALEATE", "SULFATE",
    "SULPHATE", "PHOSPHATE", "CITRATE", "TARTRATE", "MESYLATE", "BESYLATE",
    "FUMARATE", "SUCCINATE", "ACETATE", "NITRATE", "SODIUM", "POTASSIUM",
    "CALCIUM", "MAGNESIUM", "TRIHYDRATE", "DIHYDRATE", "MONOHYDRATE",
    "HYDRATE", "ANHYDROUS",
}
# 去掉之後如果只剩下這些，就不去掉（避免 SODIUM CITRATE 變成 SODIUM）
CATIONS = {"SODIUM", "POTASSIUM", "CALCIUM", "MAGNESIUM"}


NO_MERGE = {"ASPARTATE", "ALUMINUM"}


def base_name(name):
    tokens = name.split()
    while len(tokens) > 1 and tokens[-1] in SUFFIX:
        rest = tokens[:-1]
        if len(rest) == 1 and rest[0] in CATIONS:
            break
        tokens = rest
    b = " ".join(tokens)
    return name if b in NO_MERGE else b


con = sqlite3.connect(find_root() / "data" / "drugs.db")
con.execute("DROP TABLE IF EXISTS ingredient_base")
con.execute("CREATE TABLE ingredient_base(name TEXT PRIMARY KEY, base TEXT)")

groups = defaultdict(list)
for (name,) in con.execute("SELECT name FROM ingredients").fetchall():
    b = base_name(name)
    con.execute("INSERT INTO ingredient_base VALUES (?,?)", (name, b))
    groups[b].append(name)
con.commit()

merged = {b: v for b, v in groups.items() if len(v) > 1}
print("成分種類:", sum(len(v) for v in groups.values()), "→ 合併後:", len(groups))
print("有被合併的群組數:", len(merged))

print("\n收據相關:")
for key in ["AMOXICILLIN", "DICLOFENAC", "OXOLAMINE", "MEQUITAZINE", "FAMOTIDINE"]:
    print(" ", key, "->", groups.get(key, "(沒有)"))

print("\n合併最多的 15 群（請人工檢查有沒有併錯）:")
for b, v in sorted(merged.items(), key=lambda x: -len(x[1]))[:15]:
    print(" ", b, "<-", v[:6])

out = find_root() / "data" / "merged_groups.txt"
with open(out, "w", encoding="utf-8") as f:
    for b, v in sorted(merged.items()):
        f.write(f"{b} <- {' | '.join(v)}\n")
print("\n完整合併清單已存到:", out)    