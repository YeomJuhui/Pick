package com.example.pick;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.objdetect.BaseCascadeClassifier;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity2 extends AppCompatActivity {

    private static final String TAG = "mainactivity2";

    public static final int REQUEST_TAKE_PHOTO = 10;
    public static final int REQUEST_PERMISSION = 11;

    String mCurrentPhotoPath;

    ImageView iv_camera;

    Button btn_cyes, btn_mno;

    private static final String TAG2 = "TcpClient_Mainactivity2";
    private boolean isConnected = false;

    private TextView mConnectionStatus;
    private EditText mInputEditText;
    private Button btn_camera, btn_back;
    private ArrayAdapter<String> mConversationArrayAdapter;

    private String mServerIP = null;
    private Socket mSocket = null;
    private PrintWriter mOut;
    private BufferedReader mIn;
    private Thread mReceiverThread = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        iv_camera=findViewById(R.id.iv_camera);
        btn_camera=findViewById(R.id.btn_camera);
        btn_back=findViewById(R.id.btn_back);
        btn_cyes=findViewById(R.id.btn_cyes);
        btn_mno=findViewById(R.id.btn_mno);

        mConnectionStatus = (TextView)findViewById(R.id.connection_status_textview);
        mInputEditText = (EditText)findViewById(R.id.input_string_edittext);
        ListView mMessageListview = (ListView) findViewById(R.id.message_listview);


        checkPermission(); //권한체크
        loadImgArr();

        btn_camera.setOnClickListener(v -> {
            Intent in = new Intent(MainActivity2.this, MainActivity.class);
            startActivity(in);
            finish();
        });

        btn_back.setOnClickListener(v -> {
            mInputEditText.setText("3");
            String sendMessage = mInputEditText.getText().toString();
            if ( sendMessage.length() > 0 ) {
                if (!isConnected) showErrorDialog("Connect to the server and try again.");
                else {
                    new Thread(new SenderThread(sendMessage)).start();
                    mInputEditText.setText(" ");

                    Intent in = new Intent(MainActivity2.this, BackImage.class);
                    startActivity(in);
                }
            }
        });

//        btn_camera.setOnClickListener(new View.OnClickListener(){
//            public void onClick(View v){
//                mInputEditText.setText("3");
//                String sendMessage = mInputEditText.getText().toString();
//                if ( sendMessage.length() > 0 ) {
//                    if (!isConnected) showErrorDialog("Connect to the server and try again.");
//                    else {
//                        new Thread(new SenderThread(sendMessage)).start();
//                        mInputEditText.setText(" ");
//
//                        Intent in = new Intent(MainActivity2.this, MainActivity.class);
//                        startActivity(in);
//                    }
//                }
//            }
//        });

        //저장
        btn_cyes.setOnClickListener(v -> {
            try {
                BitmapDrawable drawable = (BitmapDrawable) iv_camera.getDrawable();
                Bitmap bitmap = drawable.getBitmap();

                //찍은 사진이 없으면
                if (bitmap == null) {
                    Toast.makeText(this, "I don't have any pictures to save.", Toast.LENGTH_SHORT).show();
                } else {
                    //저장
                    saveImg();
                    mCurrentPhotoPath = ""; //initialize

                    Handler timer = new Handler();
                    timer.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Intent in = new Intent(MainActivity2.this, MailPage.class);
                            startActivity(in);
                            finish();
                        }
                    },2000);
                }
            } catch (Exception e) {
                Log.w(TAG, "SAVE ERROR!", e);
            }
        });

        btn_mno.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i2=new Intent(MainActivity2.this, Angle.class);
                startActivity(i2);
            }
        });

        mConversationArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        mMessageListview.setAdapter(mConversationArrayAdapter);

        new Thread(new ConnectThread("192.168.112.15", 2424)).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        isConnected = false;
    }

    private static long back_pressed;
    @Override
    public void onBackPressed(){
        if (back_pressed + 2000 > System.currentTimeMillis()){
            super.onBackPressed();

            Log.d(TAG2, "onBackPressed:");
            isConnected = false;

            ActivityCompat.finishAffinity(MainActivity2.this);
            System.exit(0);
        }
        else{
            Toast.makeText(getBaseContext(), "Press Back one more time to exit.", Toast.LENGTH_SHORT).show();
            back_pressed = System.currentTimeMillis();
        }
    }

    private class ConnectThread implements Runnable {
        private String serverIP;
        private int serverPort;

        ConnectThread(String ip, int port) {
            serverIP = ip;
            serverPort = port;

            mConnectionStatus.setText("connecting to " + serverIP + ".......");
        }
        @Override
        public void run() {
            try {
                mSocket = new Socket(serverIP, serverPort);
                //ReceiverThread: java.net.SocketTimeoutException: Read timed out 때문에 주석처리
                //mSocket.setSoTimeout(3000);

                mServerIP = mSocket.getRemoteSocketAddress().toString();

            } catch( UnknownHostException e )
            {
                Log.d(TAG2,  "ConnectThread: can't find host");
            }
            catch( SocketTimeoutException e )
            {
                Log.d(TAG2, "ConnectThread: timeout");
            }
            catch (Exception e) {

                Log.e(TAG2, ("ConnectThread:" + e.getMessage()));
            }
            if (mSocket != null) {
                try {
                    mOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(mSocket.getOutputStream(), "UTF-8")), true);
                    mIn = new BufferedReader(new InputStreamReader(mSocket.getInputStream(), "UTF-8"));

                    isConnected = true;
                } catch (IOException e) {
                    Log.e(TAG2, ("ConnectThread:" + e.getMessage()));
                }
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (isConnected) {
                        Log.d(TAG2, "connected to " + serverIP);
                        mConnectionStatus.setText("connected to " + serverIP);

                        mReceiverThread = new Thread(new ReceiverThread());
                        mReceiverThread.start();
                    }else{
                        Log.d(TAG2, "failed to connect to server " + serverIP);
                        mConnectionStatus.setText("failed to connect to server "  + serverIP);
                    }

                }
            });
        }
    }

    private class SenderThread implements Runnable {

        private String msg;

        SenderThread(String msg) {
            this.msg = msg;
        }

        @Override
        public void run() {
            mOut.println(this.msg);
            mOut.flush();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG2, "send message: " + msg);
                    mConversationArrayAdapter.insert("Me - " + msg, 0);
                }
            });
        }
    }

    private class ReceiverThread implements Runnable {
        @Override
        public void run() {
            try {
                while (isConnected) {
                    if ( mIn ==  null ) {
                        Log.d(TAG2, "ReceiverThread: mIn is null");
                        break;
                    }

                    final String recvMessage =  mIn.readLine();

                    if (recvMessage != null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG2, "recv message: "+recvMessage);
                                mConversationArrayAdapter.insert(mServerIP + " - " + recvMessage, 0);

//                                Intent sendData = new Intent(getApplicationContext(), MainActivity.class);
//                                sendData.putExtra("recvMessage",recvMessage);
//                                startActivity(sendData);
                            }
                        });
                    }
                }

                Log.d(TAG2, "ReceiverThread: thread has exited");
                if (mOut != null) {
                    mOut.flush();
                    mOut.close();
                }

                mIn = null;
                mOut = null;

                if (mSocket != null) {
                    try {
                        mSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            catch (IOException e) {
                Log.e(TAG2, "ReceiverThread: "+ e);
            }
        }
    }

    public void showErrorDialog(String message)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Error");
        builder.setCancelable(false);
        builder.setMessage(message);
        builder.setPositiveButton("OK",  new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                finish();
            }
        });
        builder.create().show();
    }

    private void captureCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // 인텐트를 처리 할 카메라 액티비티가 있는지 확인
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {

            // 촬영한 사진을 저장할 파일 생성
            File photoFile = null;

            try {
                //임시로 사용할 파일이므로 경로는 캐시폴더로
                File tempDir = getCacheDir();

                //임시촬영파일 세팅
                String timeStamp = new SimpleDateFormat("yyyyMMdd").format(new Date());
                String imageFileName = "Capture_" + timeStamp + "_"; //ex) Capture_20201206_

                File tempImage = File.createTempFile(
                        imageFileName,  /* 파일이름 */
                        ".jpg",         /* 파일형식 */
                        tempDir      /* 경로 */
                );

                // ACTION_VIEW 인텐트를 사용할 경로 (임시파일의 경로)
                mCurrentPhotoPath = tempImage.getAbsolutePath();

                photoFile = tempImage;
            } catch (IOException e) {
                //에러 로그는 이렇게 관리하는 편이 좋다.
                Log.w(TAG, "Error generating file", e);
            }

            //파일이 정상적으로 생성되었다면 계속 진행
            if (photoFile != null) {
                //Uri 가져오기
                Uri photoURI = FileProvider.getUriForFile(this,
                        getPackageName() + ".fileprovider",
                        photoFile);
                //인텐트에 Uri담기
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);

                //인텐트 실행
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    //이미지저장 메소드
    private void saveImg() {
        try {
            //저장할 파일 경로
            File storageDir = getFilesDir();
            Log.d(TAG, "경로 : " + getFilesDir());
            if (!storageDir.exists()) //폴더가 없으면 생성.
                storageDir.mkdirs();

            String filename = "image.jpg";

            File file = new File(storageDir, filename);
            boolean deleted = file.delete();
            Log.w(TAG, "Delete Dup Check : " + deleted);
            FileOutputStream output = null;

            try {
                output = new FileOutputStream(file);
                BitmapDrawable drawable = (BitmapDrawable) iv_camera.getDrawable();
                Bitmap bitmap = drawable.getBitmap();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, output); //해상도에 맞추어 Compress
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } finally {
                try {
                    assert output != null;
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            Log.e(TAG, "Captured Saved");
            Toast.makeText(this, "Capture Saved ", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.w(TAG, "Capture Saving Error!", e);
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadImgArr() {
        try {
            File storageDir = getFilesDir();
            String filename = "image.jpg";

            File file = new File(storageDir, filename);
            Bitmap bitmap = BitmapFactory.decodeStream(new FileInputStream(file));
            iv_camera.setImageBitmap(bitmap);
        } catch (Exception e) {
            Log.w(TAG, "Capture loading Error!", e);
            //Toast.makeText(this, "load failed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        try {
            //after capture
            switch (requestCode) {
                case REQUEST_TAKE_PHOTO: {
                    if (resultCode == RESULT_OK) {

                        File file = new File(mCurrentPhotoPath);
                        Bitmap bitmap = MediaStore.Images.Media
                                .getBitmap(getContentResolver(), Uri.fromFile(file));

                        if (bitmap != null) {
                            ExifInterface ei = new ExifInterface(mCurrentPhotoPath);
                            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_UNDEFINED);

//                            //사진해상도가 너무 높으면 비트맵으로 로딩
//                            BitmapFactory.Options options = new BitmapFactory.Options();
//                            options.inSampleSize = 8; //8분의 1크기로 비트맵 객체 생성
//                            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);

                            Bitmap rotatedBitmap = null;
                            switch (orientation) {
                                case ExifInterface.ORIENTATION_ROTATE_90:
                                    rotatedBitmap = rotateImage(bitmap, 90);
                                    break;
                                case ExifInterface.ORIENTATION_ROTATE_180:
                                    rotatedBitmap = rotateImage(bitmap, 180);
                                    break;
                                case ExifInterface.ORIENTATION_ROTATE_270:
                                    rotatedBitmap = rotateImage(bitmap, 270);
                                    break;
                                case ExifInterface.ORIENTATION_NORMAL:
                                default:
                                    rotatedBitmap = bitmap;
                            }
                            //Rotate한 bitmap을 ImageView에 저장
                            iv_camera.setImageBitmap(rotatedBitmap);
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "onActivityResult Error !", e);
        }
    }

    //카메라에 맞게 이미지 로테이션
    public static Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(),
                matrix, true);
    }

    @Override
    public void onResume() {
        super.onResume();
        checkPermission(); //권한체크
    }

    //권한 확인
    public void checkPermission() {
        int permissionCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int permissionRead = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        int permissionWrite = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        //권한이 없으면 권한 요청
        if (permissionCamera != PackageManager.PERMISSION_GRANTED
                || permissionRead != PackageManager.PERMISSION_GRANTED
                || permissionWrite != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                Toast.makeText(this, "You need permission to run this app.", Toast.LENGTH_SHORT).show();
            }

            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION: {
                // 권한이 취소되면 result 배열은 비어있다.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permissions ok", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "No permissions", Toast.LENGTH_LONG).show();
                    finish(); //권한이 없으면 앱 종료
                }
            }
        }
    }
}