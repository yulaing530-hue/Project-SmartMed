package com.example.smartmed; // ⚠️ 記得保留你自己的 package 名稱

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    // --- 全域變數宣告 ---
    // 用來暫存準備壓縮與上傳的照片，解決剛剛 cannot find symbol 的錯誤
    private Bitmap currentBitmap;

    // 畫面上會用到的所有元件
    private Button btnScanPrescription, btnCheckPills, btnConfirm, btnRetake;
    private LinearLayout layoutHomeButtons, layoutPreview;
    private ImageView ivPreview;
    private TextView tvTitle;

    // 處理相機拍照結果的快遞員
    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Bundle extras = result.getData().getExtras();
                    if (extras != null) {
                        // 1. 拿到原始照片
                        Bitmap rawBitmap = (Bitmap) extras.get("data");

                        // 2. 進行壓縮：限制長邊最大為 1600 像素 (符合 API 文件建議)
                        currentBitmap = resizeBitmapToMaxLongSide(rawBitmap, 1600);

                        // 3. 把壓縮後的照片顯示在畫面上給長輩預覽
                        ivPreview.setImageBitmap(currentBitmap);

                        // 4. 切換畫面：隱藏首頁，顯示預覽
                        layoutHomeButtons.setVisibility(View.GONE);
                        layoutPreview.setVisibility(View.VISIBLE);
                        tvTitle.setText("請確認照片是否清晰");
                    }
                } else {
                    Toast.makeText(this, "取消拍照", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 綁定 XML 裡的 ID 到 Java 變數
        tvTitle = findViewById(R.id.tv_title);
        btnScanPrescription = findViewById(R.id.btn_scan_prescription);
        btnCheckPills = findViewById(R.id.btn_check_pills);
        layoutHomeButtons = findViewById(R.id.layout_home_buttons);
        layoutPreview = findViewById(R.id.layout_preview);
        ivPreview = findViewById(R.id.iv_preview);
        btnConfirm = findViewById(R.id.btn_confirm);
        btnRetake = findViewById(R.id.btn_retake);

        // 設定兩個大按鈕都去呼叫相機
        View.OnClickListener takePhotoListener = view -> {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraLauncher.launch(takePictureIntent);
        };
        btnCheckPills.setOnClickListener(takePhotoListener);
        btnScanPrescription.setOnClickListener(takePhotoListener);

        // 點擊「重新拍照」
        btnRetake.setOnClickListener(view -> {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraLauncher.launch(takePictureIntent);
        });

        // 點擊「確認送出」
        btnConfirm.setOnClickListener(view -> {
            if (currentBitmap != null) {
                btnConfirm.setText("處理中...");
                btnConfirm.setEnabled(false); // 防呆：避免長輩連按

                // 提示長輩正在上傳，並呼叫我們剛剛寫好的方法！
                Toast.makeText(this, "照片上傳辨識中，請稍候...", Toast.LENGTH_SHORT).show();
                uploadImageToServer(currentBitmap);
            }
        });
    }

    /**
     * 重置回首頁畫面，並清除照片以保護個資
     */
    private void resetToHome() {
        btnConfirm.setText("確認送出");
        btnConfirm.setEnabled(true);
        layoutPreview.setVisibility(View.GONE);
        layoutHomeButtons.setVisibility(View.VISIBLE);
        tvTitle.setText("智慧用藥小幫手");

        // 清除記憶體與畫面中的照片 (落實 API 建議的個資保護)
        currentBitmap = null;
        ivPreview.setImageDrawable(null);
    }

    /**
     * 壓縮圖片方法：將長邊限制在指定的像素大小
     */
    private Bitmap resizeBitmapToMaxLongSide(Bitmap original, int maxDimension) {
        int width = original.getWidth();
        int height = original.getHeight();

        if (width <= maxDimension && height <= maxDimension) {
            return original;
        }

        float ratio = (float) width / height;
        int newWidth, newHeight;

        if (ratio > 1) { // 寬大於高 (橫圖)
            newWidth = maxDimension;
            newHeight = (int) (newWidth / ratio);
        } else { // 高大於寬 (直圖)
            newHeight = maxDimension;
            newWidth = (int) (newHeight * ratio);
        }

        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
    }
    /**
     * 將照片打包成 multipart/form-data 格式，上傳給 FastAPI 伺服器
     */
    private void uploadImageToServer(Bitmap bitmap) {
        // ⚠️ 這裡使用 10.0.2.2 是給「電腦上的虛擬機」連線用的
        // 如果你之後接上「實體手機」，請改成你電腦的 Wi-Fi IP，例如: "http://192.168.X.X:8000/upload"
        String SERVER_URL = "https://situated-plaster-copartner.ngrok-free.dev/upload";
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();

        new Thread(() -> {
            try {
                // 1. 將 Bitmap 壓縮並轉換成 JPG 的位元組陣列 (byte array)
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                byte[] imageBytes = baos.toByteArray();

                // 2. 建立 HTTP 連線設定
                java.net.URL url = new java.net.URL(SERVER_URL);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setRequestProperty("ngrok-skip-browser-warning", "true");

                // 3. 按照 multipart/form-data 的格式寫入資料
                java.io.OutputStream os = conn.getOutputStream();
                java.io.DataOutputStream dos = new java.io.DataOutputStream(os);

                dos.writeBytes("--" + boundary + "\r\n");
                dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"pill_image.jpg\"\r\n");
                dos.writeBytes("Content-Type: image/jpeg\r\n\r\n");
                dos.write(imageBytes); // 寫入照片本體
                dos.writeBytes("\r\n");
                dos.writeBytes("--" + boundary + "--\r\n");
                dos.flush();
                dos.close();

                // 4. 讀取組員伺服器回傳的狀態碼與 JSON
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder responseStr = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        responseStr.append(line);
                    }
                    br.close();

                    // 5. 解析回傳的 JSON 資料
                    org.json.JSONObject jsonResponse = new org.json.JSONObject(responseStr.toString());
                    org.json.JSONArray medsArray = jsonResponse.optJSONArray("meds");
                    String warning = jsonResponse.optString("warning", "");

                    // 組合要顯示給長輩看的結果字串
                    StringBuilder resultMsg = new StringBuilder();
                    if (medsArray != null && medsArray.length() > 0) {
                        resultMsg.append("系統為您辨識出 ").append(medsArray.length()).append(" 種藥物：\n\n");
                        for (int i = 0; i < medsArray.length(); i++) {
                            org.json.JSONObject med = medsArray.getJSONObject(i);
                            String name = med.optString("name", "未知藥物");
                            double total = med.optDouble("total", 0);
                            String unit = med.optString("unit", "顆");
                            resultMsg.append("📍 ").append(name).append("\n(共 ").append(total).append(" ").append(unit).append(")\n\n");
                        }
                    } else {
                        resultMsg.append("照片中沒有辨識到任何藥物，請重新確認。\n");
                    }

                    // 如果 API 有回傳 warning，加上醒目提示
                    if (!warning.isEmpty() && !warning.equals("null")) {
                        resultMsg.append("\n⚠️ 注意：").append(warning);
                    }

                    // 6. 切回主畫面 (UI 執行緒)，彈出大對話框顯示結果
                    runOnUiThread(() -> {
                        new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                                .setTitle("辨識結果")
                                .setMessage(resultMsg.toString())
                                .setPositiveButton("我知道了", (dialog, which) -> resetToHome()) // 點擊後清除照片並回到首頁
                                .setCancelable(false)
                                .show();
                    });

                } else {
                    // 伺服器報錯 (例如 422 或是 500)
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "伺服器錯誤代碼: " + responseCode, Toast.LENGTH_LONG).show();
                        resetToHome();
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "連線失敗，請檢查伺服器是否開啟", Toast.LENGTH_LONG).show();
                    resetToHome();
                });
            }
        }).start();
    }
}