import csv

rows = list(csv.DictReader(open(r"C:\Users\vivia\OneDrive\桌面\Project-SmartMed\data\drug_license.csv", encoding="utf-8-sig", newline="")))
print("總筆數:", len(rows))

# 你藥單上的四種藥
for k in ["VOREN", "OXOLAMINE", "MEQUITAZINE", "FAMOTIDINE"]:
    hits = [r for r in rows if k in (r["英文品名"] + r["主成分略述"]).upper()]
    print(f"\n== {k}: {len(hits)} 筆 ==")
    for r in hits[:3]:
        print(r["中文品名"], "|", r["英文品名"], "|", r["主成分略述"])

# 多成分藥的成分長相
multi = [r for r in rows if len(r["主成分略述"]) > 60]
print("\n== 多成分範例 ==")
for r in multi[:5]:
    print(repr(r["主成分略述"]))