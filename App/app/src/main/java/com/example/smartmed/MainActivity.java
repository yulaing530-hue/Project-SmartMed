package com.example.smartmed; // ⚠️ 記得對照你自己的 package 名稱

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
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
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    // 用來儲存拍照產生的暫存檔路徑
    private Uri photoUri;
    private String currentPhotoPath;

    // 畫面上會用到的所有元件
    private Button btnScanPrescription, btnCheckPills, btnConfirm, btnRetake;
    private LinearLayout layoutHomeButtons, layoutPreview;
    private ImageView ivPreview;
    private TextView tvTitle;

    // 處理相機拍照結果的快遞員 (使用高畫質檔案模式)
    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    try {
                        // 為了避免原圖太大導致預覽時 Canvas 記憶體爆掉閃退，
                        // 我們在這裡使用安全縮放來載入預覽圖，但檔案本身保持 100% 原畫質上傳！
                        Bitmap previewBitmap = decodeSampledBitmapFromFile(currentPhotoPath, 1024, 1024);

                        if (previewBitmap != null) {
                            ivPreview.setImageBitmap(previewBitmap);

                            // 切換畫面：隱藏首頁，顯示預覽與確認按鈕
                            layoutHomeButtons.setVisibility(View.GONE);
                            layoutPreview.setVisibility(View.VISIBLE);
                            tvTitle.setText("請確認照片是否清晰");
                        } else {
                            Toast.makeText(this, "讀取照片失敗", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "相片載入錯誤: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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

        // 綁定 XML 裡的 ID
        tvTitle = findViewById(R.id.tv_title);
        btnScanPrescription = findViewById(R.id.btn_scan_prescription);
        btnCheckPills = findViewById(R.id.btn_check_pills);
        layoutHomeButtons = findViewById(R.id.layout_home_buttons);
        layoutPreview = findViewById(R.id.layout_preview);
        ivPreview = findViewById(R.id.iv_preview);
        btnConfirm = findViewById(R.id.btn_confirm);
        btnRetake = findViewById(R.id.btn_retake);

        // 設定兩個大按鈕都去呼叫高畫質相機
        View.OnClickListener takePhotoListener = view -> openHighQualityCamera();
        btnCheckPills.setOnClickListener(takePhotoListener);
        btnScanPrescription.setOnClickListener(takePhotoListener);

        // 點擊「重新拍照」
        btnRetake.setOnClickListener(view -> openHighQualityCamera());

        // 點擊「確認送出」：將完整的 100% 高畫質原圖檔案上傳給伺服器
        btnConfirm.setOnClickListener(view -> {
            if (currentPhotoPath != null) {
                btnConfirm.setText("高畫質辨識中...");
                btnConfirm.setEnabled(false); // 防呆

                Toast.makeText(this, "正在上傳高畫質原圖，請稍候...", Toast.LENGTH_SHORT).show();
                uploadImageFileToServer(currentPhotoPath);
            }
        });
    }

    /**
     * 建立暫存檔並開啟高畫質相機
     */
    private void openHighQualityCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                File storageDir = getCacheDir();
                photoFile = File.createTempFile("药丸原圖_", ".jpg", storageDir);
                currentPhotoPath = photoFile.getAbsolutePath();
            } catch (IOException ex) {
                Toast.makeText(this, "建立暫存檔失敗", Toast.LENGTH_SHORT).show();
                return;
            }

            if (photoFile != null) {
                photoUri = FileProvider.getUriForFile(this,
                        getPackageName() + ".fileprovider",
                        photoFile);

                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                cameraLauncher.launch(takePictureIntent);
            }
        }
    }

    /**
     * 安全載入預覽圖的方法：透過計算比例縮小，防止 Canvas 記憶體溢位閃退
     */
    private Bitmap decodeSampledBitmapFromFile(String filePath, int reqWidth, int reqHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(filePath, options);
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /**
     * 直接讀取高畫質檔案原封不動上傳給伺服器
     */
    private void uploadImageFileToServer(String filePath) {
        String SERVER_URL = "https://situated-plaster-copartner.ngrok-free.dev/upload";
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();

        new Thread(() -> {
            try {
                File imageFile = new File(filePath);
                if (!imageFile.exists()) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "找不到相片檔案", Toast.LENGTH_SHORT).show();
                        resetToHome();
                    });
                    return;
                }

                java.net.URL url = new java.net.URL(SERVER_URL);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setRequestProperty("ngrok-skip-browser-warning", "true");

                java.io.OutputStream os = conn.getOutputStream();
                java.io.DataOutputStream dos = new java.io.DataOutputStream(os);

                dos.writeBytes("--" + boundary + "\r\n");
                dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"pill_original.jpg\"\r\n");
                dos.writeBytes("Content-Type: image/jpeg\r\n\r\n");

                java.io.FileInputStream fileInputStream = new java.io.FileInputStream(imageFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                }
                fileInputStream.close();

                dos.writeBytes("\r\n");
                dos.writeBytes("--" + boundary + "--\r\n");
                dos.flush();
                dos.close();

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder responseStr = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        responseStr.append(line);
                    }
                    br.close();

                    org.json.JSONObject jsonResponse = new org.json.JSONObject(responseStr.toString());
                    org.json.JSONArray medsArray = jsonResponse.optJSONArray("meds");
                    String warning = jsonResponse.optString("warning", "");

                    StringBuilder resultMsg = new StringBuilder();
                    if (medsArray != null && medsArray.length() > 0) {
                        resultMsg.append("高畫質辨識出 ").append(medsArray.length()).append(" 種藥物：\n\n");
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

                    if (!warning.isEmpty() && !warning.equals("null")) {
                        resultMsg.append("\n⚠️ 注意：").append(warning);
                    }

                    runOnUiThread(() -> {
                        new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                                .setTitle("辨識結果 (高畫質原圖)")
                                .setMessage(resultMsg.toString())
                                .setPositiveButton("我知道了", (dialog, which) -> resetToHome())
                                .setCancelable(false)
                                .show();
                    });

                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "伺服器錯誤代碼: " + responseCode, Toast.LENGTH_LONG).show();
                        resetToHome();
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "上傳失敗: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    resetToHome();
                });
            }
        }).start();
    }

    /**
     * 重置畫面並清除暫存檔與個資
     */
    private void resetToHome() {
        btnConfirm.setText("確認送出");
        btnConfirm.setEnabled(true);
        layoutPreview.setVisibility(View.GONE);
        layoutHomeButtons.setVisibility(View.VISIBLE);
        tvTitle.setText("智慧用藥小幫手");

        ivPreview.setImageDrawable(null);

        if (currentPhotoPath != null) {
            File f = new File(currentPhotoPath);
            if (f.exists()) f.delete();
            currentPhotoPath = null;
        }
    }
}