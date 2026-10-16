package com.example.smartmed; // ⚠️ 這行請對照你自己的專案名稱

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

    // 宣告畫面上會用到的所有元件
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
                        // 1. 拿到照片
                        Bitmap imageBitmap = (Bitmap) extras.get("data");
                        // 2. 把照片顯示在 ImageView 裡面
                        ivPreview.setImageBitmap(imageBitmap);

                        // 3. 切換畫面：把首頁按鈕藏起來，把照片預覽跟確認按鈕顯示出來
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

        // --- 設定按鈕的點擊事件 ---

        // 因為兩個大按鈕現在都是呼叫相機，我們可以寫一個共用的監聽器
        View.OnClickListener takePhotoListener = view -> {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraLauncher.launch(takePictureIntent);
        };
        btnCheckPills.setOnClickListener(takePhotoListener);
        btnScanPrescription.setOnClickListener(takePhotoListener);

        // 點擊「重新拍照」：再次呼叫相機
        btnRetake.setOnClickListener(view -> {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraLauncher.launch(takePictureIntent);
        });

        // 點擊「確認送出」：模擬上傳，然後把畫面切回首頁
        btnConfirm.setOnClickListener(view -> {
            Toast.makeText(this, "假裝正在上傳給 AI 辨識...", Toast.LENGTH_LONG).show();

            // 恢復成首頁狀態
            layoutPreview.setVisibility(View.GONE);
            layoutHomeButtons.setVisibility(View.VISIBLE);
            tvTitle.setText("智慧用藥小幫手");
        });
    }
}