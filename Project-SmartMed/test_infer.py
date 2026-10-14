import json
import urllib.request
import numpy as np
import requests
from PIL import Image

# 1. 自動下載一張測試圖片（這裡抓一張維他命/藥瓶的示意圖）與分類標籤檔
img_url = "https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=400"
labels_url = "https://raw.githubusercontent.com/anishathalye/imagenet-simple-labels/master/imagenet-simple-labels.json"

print("正在準備測試圖片與標籤...")
urllib.request.urlretrieve(img_url, "test_pill.jpg")
with urllib.request.urlopen(labels_url) as url:
    labels = json.loads(url.read().decode())

# 2. 讀取圖片並進行預處理 (ResNet50 規定輸入必須是 224x224 的 RGB 浮點數陣列)
img = Image.open("test_pill.jpg").convert("RGB").resize((224, 224))
img_array = np.array(img).astype(np.float32) / 255.0

# ImageNet 標準正規化 (減去平均值、除以標準差)
mean = np.array([0.485, 0.456, 0.406], dtype=np.float32)
std = np.array([0.229, 0.224, 0.225], dtype=np.float32)
img_array = (img_array - mean) / std

# 將維度從 (高, 寬, 通道) 轉成模型要求的 (通道, 高, 寬)，並加上 Batch 維度 -> (1, 3, 224, 224)
img_array = np.transpose(img_array, (2, 0, 1))
img_array = np.expand_dims(img_array, axis=0)

# 3. 打包成 NVIDIA Triton 規定的 JSON 請求格式
# 我們剛才下載的 resnet50-v2-7.onnx，預設輸入層名稱為 "data"，輸出層為 "resnetv24_dense0_fwd"
payload = {
    "inputs": [
        {
            "name": "data",
            "shape": list(img_array.shape),
            "datatype": "FP32",
            "data": img_array.flatten().tolist()
        }
    ]
}

# 4. 發送 HTTP POST 請求給你的 Triton Server
triton_url = "http://localhost:8000/v2/models/resnet50/infer"
print("正在將圖片送往 RTX 4070 進行推論...")
response = requests.post(triton_url, json=payload)

# 5. 解析回傳結果並整理成專題前端好用的 JSON 格式
if response.status_code == 200:
    result_json = response.json()
    # 取出模型輸出的 1000 個類別分數
    scores = np.array(result_json["outputs"][0]["data"])
    # 透過 Softmax 轉換成機率百分比
    exp_scores = np.exp(scores - np.max(scores))
    probs = exp_scores / np.sum(exp_scores)
    
    # 找出機率最高的前 3 名
    top3_indices = np.argsort(probs)[::-1][:3]
    
    final_output = {
        "status": "success",
        "model": result_json["model_name"],
        "predictions": [
            {
                "rank": int(i + 1),
                "label": labels[idx],
                "confidence": round(float(probs[idx]) * 100, 2)
            }
            for i, idx in enumerate(top3_indices)
        ]
    }
    
    print("\n=== 辨識完成！回傳的 JSON 結果 ===")
    print(json.dumps(final_output, indent=2, ensure_ascii=False))
else:
    print(f"推論失敗，狀態碼: {response.status_code}")
    print(response.text)