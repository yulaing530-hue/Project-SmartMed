# 藥單辨識 API 說明（v0.2）

給 Android App 開發使用。伺服器程式在 `server/main.py`。
這份文件描述的是**目前已經能跑的版本**；標示「規劃中」的欄位現在還沒有，請先不要依賴。

v0.2 變更：新增 `meds[].ingredients`、`meds[].match`、`duplicates`、`notice`；移除 `lines`（整張藥單原文，含個資）。

## 1. 連線

| 情境 | 網址 |
|---|---|
| 電腦本機測試 | `http://127.0.0.1:8000` |
| Android 模擬器連電腦上的伺服器 | `http://10.0.2.2:8000` |
| 實體手機連電腦上的伺服器 | `http://<電腦的區網 IP>:8000`（手機和電腦要在同一個 Wi-Fi） |

注意：

- 實體手機要連的話，伺服器要用 `--host 0.0.0.0` 啟動：
  `python -m uvicorn main:app --reload --host 0.0.0.0`
  並且 Windows 防火牆要允許 8000 埠。
- Android 預設不允許明文 HTTP。開發階段需要在 `AndroidManifest.xml` 的 `<application>` 加上
  `android:usesCleartextTraffic="true"`（正式上線前要改成 HTTPS）。
- 伺服器啟動後，瀏覽器開 `/docs` 可以直接看到互動式測試頁面。
- 成分比對需要伺服器端有 `data/drugs.db`（藥品資料庫，不在 GitHub 上，要自己用 `tools/import_drugs.py` 和 `tools/add_base.py` 產生）。沒有這個檔案時，`ingredients` 和 `match` 不會出現，其他欄位照常。

## 2. 上傳藥單並辨識

`POST /upload`

- 格式：`multipart/form-data`
- 欄位名稱：`file`（一張圖片，jpg 或 png）

### 成功回應（HTTP 200）

```json
{
  "filename": "我的藥單.jpg",
  "meds": [
    {
      "name": "AMOXICILLIN 500",
      "dose_per_time": 1,
      "unit": "粒",
      "times_per_day": 3,
      "days": 3,
      "total": 9,
      "ingredients": ["AMOXICILLIN"],
      "match": { "how": "品名含 AMOXICILLIN", "licenses": 26, "confidence": 0.92 }
    },
    {
      "name": "VOREN",
      "dose_per_time": 1,
      "unit": "錠",
      "times_per_day": 3,
      "days": 3,
      "total": 9,
      "ingredients": ["DICLOFENAC"],
      "match": { "how": "品名含 VOREN", "licenses": 14, "confidence": 1 }
    },
    {
      "name": "Primalan(mequitaz",
      "dose_per_time": 0.56,
      "unit": "錠",
      "times_per_day": 3,
      "days": 3,
      "total": 5,
      "ingredients": ["MEQUITAZINE"],
      "match": { "how": "品名含 MEQUITAZ", "licenses": 12, "confidence": 1 }
    }
  ],
  "duplicates": [],
  "warning": null,
  "notice": "成分比對僅供參考，是否重複或有交互作用，請詢問醫師或藥師。",
  "names_found": ["AMOXICILLIN 500", "VOREN", "Primalan(mequitaz"]
}
```

（範例只列 3 顆藥，實際會列出藥單上辨識到的全部。）

### 欄位說明

| 欄位 | 型別 | 說明 |
|---|---|---|
| `filename` | string | 上傳的檔名 |
| `meds` | array | 辨識出的藥品清單，可能是空陣列 |
| `meds[].name` | string | 藥名（藥單上印的原文，可能被截斷，例如 `Primalan(mequitaz`） |
| `meds[].dose_per_time` | number | 每次用量，可能是小數（0.56 代表約半錠） |
| `meds[].unit` | string 或 null | 單位：粒、錠、顆、包、片、粉；辨識不到時是 `null` |
| `meds[].times_per_day` | integer | 一天幾次 |
| `meds[].days` | integer | 共幾天 |
| `meds[].total` | number | 總量 = 每次用量 × 次數 × 天數 |
| `meds[].ingredients` | array of string | 主成分（英文大寫，已合併鹽類和水合物的不同寫法，例如鈉鹽、鉀鹽都記為 `DICLOFENAC`）。**可能是空陣列**：代表比對不到，不代表這顆藥沒有成分 |
| `meds[].match.how` | string 或 null | 怎麼比對到的（用哪個字查品名或成分） |
| `meds[].match.licenses` | integer | 資料庫中符合的許可證筆數 |
| `meds[].match.confidence` | number（0 到 1） | 符合的許可證中，有多少比例給出同一組成分。越接近 1 越可靠 |
| `duplicates` | array | 這張藥單裡，不同藥含有相同成分的警示。每筆是 `{"ingredient": "...", "meds": ["藥名1", "藥名2"]}`；沒有就是空陣列 |
| `warning` | string 或 null | 不是 `null` 時，代表藥名數量和劑量數量對不起來，**App 要提示使用者人工確認** |
| `notice` | string | 固定的免責提醒，**App 要顯示在結果頁面上** |
| `names_found` | array of string | 辨識到的藥名（除錯用） |

數字欄位請一律當成「數字」解析（不要假設一定是整數或一定有小數點）。

### 建議的顯示方式

- `confidence` 低於 0.8，或 `ingredients` 是空陣列：顯示「成分不確定，請詢問藥師」。
- `duplicates` 不是空陣列：用醒目的方式顯示「可能含有重複成分：XXX（藥A、藥B）」，語氣用「可能」，不要說「你吃重複了」。
- `notice` 一律顯示。

### 特殊情況

- 圖片裡沒有辨識到任何文字：回傳 `{"filename": "...", "meds": [], "duplicates": [], "notice": "..."}`
- 沒有帶 `file` 欄位：HTTP 422
- 辨識結果**不保證正確**，App 一定要讓使用者看過、確認或修改後才採用
- 成分比對只看這一張藥單；**跨藥單（不同醫院）的比對還沒做**

## 3. 其他 API

| 方法 | 路徑 | 說明 |
|---|---|---|
| GET | `/` | 健康檢查，回傳 `{"message": "伺服器運作中"}` |
| GET | `/history` | 過去辨識紀錄（只含藥品，不含姓名等個資），格式 `[{"id", "created_at", "meds": [...]}]` |

## 4. 規劃中（現在還沒有）

先知道有這些，但目前請不要依賴：

- `meds[].timing`：飯前、飯後（目前藥單上沒有這個資訊，需要使用者手動補）
- 跨藥單比對：同一個使用者上傳多張藥單，找出不同醫院之間的重複成分
- 成分的中文名稱
- 交互作用警示

欄位名稱到時候可能調整，有改動會在這份文件更新並通知。

## 5. App 端建議

1. 拍照後，上傳前先檢查圖片是否模糊、是否歪斜，必要時請使用者重拍。
2. 圖片壓縮到合理大小再上傳（長邊約 1600 到 2000 像素即可）。
3. 顯示辨識結果時，字體要大，每顆藥一張卡片，讓使用者可以修改或刪除。
4. `warning` 有值時，在畫面上用明顯的方式提示「辨識可能有誤，請對照藥單」。
5. 不要在手機上存藥單原圖超過必要的時間，避免個資外洩。
6. 在畫面上註明：本系統僅供輔助，用藥請以醫師或藥師指示為準。

## 6. 問題回報

- 辨識錯誤：請附上截圖（先遮住個資）和上傳的時間，傳給組長。
- API 有問題或格式要調整：直接在群組說，組長會更新這份文件。
