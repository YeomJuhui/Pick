package com.example.pick;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.File;

public class FinishPage2 extends AppCompatActivity {

    private static final String TAG = "finishpage2";

    Button btn_finish2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finish_page2);

        btn_finish2=findViewById(R.id.btn_finish2);
        btn_finish2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File tempDir = getFilesDir();
                Log.d(TAG, "경로 : " + getFilesDir());
                if (!tempDir.exists()) //폴더가 없으면 생성.
                    tempDir.mkdirs();

                String filename = "image.jpg";
                String filename1 ="back1.jpg";
                String filename2 ="back2.jpg";
                String filename3 ="back3.jpg";
                String filename4 ="back4.jpg";
                String filename5 ="back5.jpg";
                String filename6 ="back6.jpg";

                // 기존에 있다면 삭제
                File file = new File(tempDir, filename);
                boolean deleted = file.delete();
                Log.w(TAG, "Delete Dup Check : " + deleted);
                File file1 = new File(tempDir, filename1);
                boolean deleted1 = file1.delete();
                Log.w(TAG, "Delete Dup Check : " + deleted1);
                File file2 = new File(tempDir, filename2);
                boolean deleted2 = file2.delete();
                Log.w(TAG, "Delete Dup Check : " + deleted2);
                File file3 = new File(tempDir, filename3);
                boolean deleted3 = file3.delete();
                Log.w(TAG, "Delete Dup Check : " + deleted3);
                File file4 = new File(tempDir, filename4);
                boolean deleted4 = file4.delete();
                Log.w(TAG, "Delete Dup Check : " + deleted4);
                File file5 = new File(tempDir, filename5);
                boolean deleted5 = file5.delete();
                Log.w(TAG, "Delete Dup Check : " + deleted5);
                File file6 = new File(tempDir, filename6);
                boolean deleted6 = file6.delete();
                Log.w(TAG, "Delete Dup Check : " + deleted6);

                ActivityCompat.finishAffinity(FinishPage2.this);
                System.exit(0);
            }
        });
    }
}