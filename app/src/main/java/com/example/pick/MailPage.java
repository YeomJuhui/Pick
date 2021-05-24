package com.example.pick;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class MailPage extends AppCompatActivity {

    private static final String TAG = "mailpage";

    ImageView iv_image;
    EditText et_mail;
    Button btn_mail, btn_efinish;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mail_page);

        iv_image = findViewById(R.id.iv_image);
        et_mail = findViewById(R.id.et_mail);

        //내부저장소의 이미지를 이미지뷰에 띄워주기기
        File storageDir = getFilesDir();

        String filename = "image.jpg";

        File file = new File(storageDir, filename);
        Uri uri = FileProvider.getUriForFile(getApplicationContext(), getPackageName() + ".fileprovider", file);

        try {
            Bitmap bitmap = BitmapFactory.decodeStream(new FileInputStream(file));
            iv_image.setImageBitmap(bitmap);
        } catch (Exception e) {
            Log.w(TAG, "Capture loading Error!", e);
            Toast.makeText(this, "load failed", Toast.LENGTH_SHORT).show();
        }

        btn_mail = findViewById(R.id.btn_mail);
        btn_mail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    //ACTION_SEND를 통한 공유 기능(GMail로)
                    //putExtra를 이용해 intent 값 넘기기
                    Intent sendIntent = new Intent(Intent.ACTION_SEND);
                    sendIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{et_mail.getText().toString()});
                    sendIntent.putExtra("subject", "MMS Test");
                    sendIntent.putExtra("sms_body", "Send pictures.");
                    sendIntent.putExtra(Intent.EXTRA_SUBJECT, "[Pickpic]");
                    sendIntent.setType("image/*");

                    //imgUri를 이메일 첨부파일에 넣기
                    sendIntent.putExtra(Intent.EXTRA_STREAM, uri);

                    //ResolveInfo타입에서 sendIntent에 넣었던 내용 넘겨받기
                    List<ResolveInfo> resInfo = getPackageManager().queryIntentActivities(sendIntent, 0);

                    //리스트에 내용이 없을 때
                    if (resInfo.isEmpty()) {
                        return;
                    }

                    List<Intent> targetedShareIntents = new ArrayList<>();

                    for (ResolveInfo resolveInfo : resInfo) {
                        String packageName = resolveInfo.activityInfo.packageName;
                        //targetedShareIntent에 이메일형식에 맞게 내용 넘기기
                        Intent targetedShareIntent = new Intent(android.content.Intent.ACTION_SEND);
                        targetedShareIntent.setType("image/*");

                        if (packageName.equals("com.google.android.gm")) {
                            ComponentName name = new ComponentName(packageName, resolveInfo.activityInfo.name);
                            //이메일 수신단에 수신자 이메일 넣기,첨부파일에 받아온 Uri
                            targetedShareIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{et_mail.getText().toString()});
                            targetedShareIntent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
                            targetedShareIntent.putExtra(Intent.EXTRA_SUBJECT, "[Pickpic]");
                            targetedShareIntent.setComponent(name);
                            targetedShareIntent.setPackage(packageName);
                            //targetedShareIntents에 리스트 넘기기
                            targetedShareIntents.add(targetedShareIntent);
                        }
                    }
                    if (targetedShareIntents.isEmpty()) {
                        Log.i("###", "No apps available for sharing");
                        return;
                    }
                    Intent chooserIntent = Intent.createChooser(targetedShareIntents.remove(0), "Share");

                    //리스트에서 받은 내용 chooseIntent에 넘기기
                    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetedShareIntents.toArray(new Parcelable[targetedShareIntents.size()]));
                    startActivity(chooserIntent);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        });

        btn_efinish = findViewById(R.id.btn_efinish);
        btn_efinish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MailPage.this, FinishPage2.class);
                startActivity(i);
            }
        });
    }
}