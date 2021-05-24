package com.example.pick;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class BackImage extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2{

    private static final String TAG = "backimage";

    private Mat matInput;
    private Mat matResult;

    private String recvMessage;
    private ImageView imageBack;
    private String mCurrentPhotoPath;
    private int count=0;
    private long mStartTime=0;

    private CameraBridgeViewBase mOpenCvCameraView;

    private TextView mConnectionStatus;
    private EditText mInputEditText;
    private ArrayAdapter<String> mConversationArrayAdapter;

    private static final String TAG2 = "TcpClient_backimage";
    private boolean isConnected = false;

    private String mServerIP = null;
    private Socket mSocket = null;
    private PrintWriter mOut;
    private BufferedReader mIn;
    private Thread mReceiverThread = null;

    public static final int REQUEST_TAKE_PHOTO = 10;
    public static final int REQUEST_PERMISSION = 11;

    //    public native void ConvertRGBtoGray(long matAddrInput, long matAddrResult);
    public native long loadCascade(String cascadeFileName );
    public native void detect(long cascadeClassifier_face,
                              long cascadeClassifier_eye, long matAddrInput, long matAddrResult);
    public long cascadeClassifier_face = 0;
    public long cascadeClassifier_eye = 0;

    private final Semaphore writeLock = new Semaphore(1);

    public void getWriteLock() throws InterruptedException {
        writeLock.acquire();
    }

    public void releaseWriteLock() {
        writeLock.release();
    }

    static {
        System.loadLibrary("opencv_java4");
        System.loadLibrary("native-lib");
    }

    private void copyFile(String filename) {
        String baseDir = Environment.getExternalStorageDirectory().getPath();
        String pathDir = baseDir + File.separator + filename;

        AssetManager assetManager = this.getAssets();

        InputStream inputStream = null;
        OutputStream outputStream = null;

        try {
            Log.d( TAG, "copyFile :: 다음 경로로 파일복사 "+ pathDir);
            inputStream = assetManager.open(filename);
            outputStream = new FileOutputStream(pathDir);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            inputStream.close();
            inputStream = null;
            outputStream.flush();
            outputStream.close();
            outputStream = null;
        } catch (Exception e) {
            Log.d(TAG, "copyFile :: 파일 복사 중 예외 발생 "+e.toString() );
        }
    }

    private void read_cascade_file(){
        copyFile("haarcascade_frontalface_alt.xml");
        copyFile("haarcascade_eye_tree_eyeglasses.xml");

        Log.d(TAG, "read_cascade_file:");

        cascadeClassifier_face = loadCascade( "haarcascade_frontalface_alt.xml");
        Log.d(TAG, "read_cascade_file:");

        cascadeClassifier_eye = loadCascade( "haarcascade_eye_tree_eyeglasses.xml");
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_back_image);

        mConnectionStatus = (TextView)findViewById(R.id.connection_status_textview);
        mInputEditText = (EditText)findViewById(R.id.input_string_edittext);
        ListView mMessageListview = (ListView) findViewById(R.id.message_listview);

        imageBack=findViewById(R.id.imageBack);

        mOpenCvCameraView = (CameraBridgeViewBase)findViewById(R.id.activity_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setCameraIndex(1); // front-camera(1),  back-camera(0)

        Intent sendDate = getIntent();
        recvMessage = sendDate.getStringExtra("recvMessage");
        Log.d(TAG2, "recv message: "+recvMessage);

        Button button = (Button)findViewById(R.id.button3);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mInputEditText.setText("3");
                String sendMessage = mInputEditText.getText().toString();
                if ( sendMessage.length() > 0 ) {
                    if (!isConnected) showErrorDialog("Connect to the server and try again.");
                    else {
                        new Thread(new SenderThread(sendMessage)).start();
                        mInputEditText.setText(" ");
                    }
                }
            }
        });

        mConversationArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        mMessageListview.setAdapter(mConversationArrayAdapter);

        new Thread(new ConnectThread("172.20.10.2", 9898)).start();
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "onResume :: Internal OpenCV library not found.");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "onResum :: OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();

        isConnected = false;

        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    private static long back_pressed;
    @Override
    public void onBackPressed(){
        if (back_pressed + 2000 > System.currentTimeMillis()){
            super.onBackPressed();

            Log.d(TAG, "onBackPressed:");
            isConnected = false;

            finish();
        }
        else{
            Toast.makeText(getBaseContext(), "한번 더 뒤로가기를 누르면 종료됩니다.", Toast.LENGTH_SHORT).show();
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
                Log.d(TAG,  "ConnectThread: can't find host");
            }
            catch( SocketTimeoutException e )
            {
                Log.d(TAG, "ConnectThread: timeout");
            }
            catch (Exception e) {

                Log.e(TAG, ("ConnectThread:" + e.getMessage()));
            }
            if (mSocket != null) {
                try {
                    mOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(mSocket.getOutputStream(), "UTF-8")), true);
                    mIn = new BufferedReader(new InputStreamReader(mSocket.getInputStream(), "UTF-8"));

                    isConnected = true;
                } catch (IOException e) {
                    Log.e(TAG, ("ConnectThread:" + e.getMessage()));
                }
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (isConnected) {
                        Log.d(TAG, "connected to " + serverIP);
                        mConnectionStatus.setText("connected to " + serverIP);

                        mReceiverThread = new Thread(new ReceiverThread());
                        mReceiverThread.start();
                    }else{
                        Log.d(TAG, "failed to connect to server " + serverIP);
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
                    Log.d(TAG, "send message: " + msg);
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
                        Log.d(TAG, "ReceiverThread: mIn is null");
                        break;
                    }

                    final String recvMessage =  mIn.readLine();

                    if (recvMessage != null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "recv message: "+recvMessage);
                                mConversationArrayAdapter.insert(mServerIP + " - " + recvMessage, 0);
                            }
                        });
                    }
                }

                Log.d(TAG, "ReceiverThread: thread has exited");
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
                Log.e(TAG, "ReceiverThread: "+ e);
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

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        long currentTimeMillis=System.currentTimeMillis();
        Log.d("현재시간: ",""+currentTimeMillis);

        if(mStartTime==0){
            mStartTime=currentTimeMillis;
        }

        try {
            getWriteLock();

            matInput = inputFrame.rgba();

            if ( matResult == null )

                matResult = new Mat(matInput.rows(), matInput.cols(), matInput.type());

//        ConvertRGBtoGray(matInput.getNativeObjAddr(), matResult.getNativeObjAddr());
            Core.flip(matInput, matInput, 1);

            detect(cascadeClassifier_face,cascadeClassifier_eye, matInput.getNativeObjAddr(),
                    matResult.getNativeObjAddr());

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(((currentTimeMillis - mStartTime) > (10.5 * 1000)) && ((currentTimeMillis - mStartTime) < (10.7 * 1000))){
                        try {
                            getWriteLock();

                            File tempDir = getFilesDir();
                            String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                            String imageFileName = "Capture_" + timeStamp + "_"; //ex) Capture_20201206_

                            File file = new File(tempDir, "back1.jpg");
                            mCurrentPhotoPath = file.getAbsolutePath();

                            String filename = file.toString();

                            Imgproc.cvtColor(matResult, matResult, Imgproc.COLOR_BGR2RGBA);
                            boolean ret = Imgcodecs.imwrite(filename, matResult);
                            if (ret) Log.d(TAG, "SUCESS");
                            else Log.d(TAG, "FAIL");

                            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                            mediaScanIntent.setData(Uri.fromFile(file));
                            sendBroadcast(mediaScanIntent);

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        releaseWriteLock();
                    }
                    if(((currentTimeMillis - mStartTime) > (20.5 * 1000)) && ((currentTimeMillis - mStartTime) < (20.7 * 1000))){
                        try {
                            getWriteLock();

                            File tempDir = getFilesDir();
                            String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                            String imageFileName = "Capture_" + timeStamp + "_"; //ex) Capture_20201206_

                            File file = new File(tempDir, "back2.jpg");
                            mCurrentPhotoPath = file.getAbsolutePath();

                            String filename = file.toString();

                            Imgproc.cvtColor(matResult, matResult, Imgproc.COLOR_BGR2RGBA);
                            boolean ret = Imgcodecs.imwrite(filename, matResult);
                            if (ret) Log.d(TAG, "SUCESS");
                            else Log.d(TAG, "FAIL");

                            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                            mediaScanIntent.setData(Uri.fromFile(file));
                            sendBroadcast(mediaScanIntent);

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        releaseWriteLock();
                    }
                    if((currentTimeMillis-mStartTime)>(30.5*1000) && ((currentTimeMillis - mStartTime) < (30.7 * 1000))){
                        try {
                            getWriteLock();

                            File tempDir = getFilesDir();
                            String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                            String imageFileName = "Capture_" + timeStamp + "_"; //ex) Capture_20201206_

                            File file = new File(tempDir,  "back3.jpg");
                            mCurrentPhotoPath = file.getAbsolutePath();

                            String filename = file.toString();

                            Imgproc.cvtColor(matResult, matResult, Imgproc.COLOR_BGR2RGBA);
                            boolean ret = Imgcodecs.imwrite(filename, matResult);
                            if (ret) Log.d(TAG, "SUCESS");
                            else Log.d(TAG, "FAIL");

                            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                            mediaScanIntent.setData(Uri.fromFile(file));
                            sendBroadcast(mediaScanIntent);

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        releaseWriteLock();
                    }
                    if((currentTimeMillis-mStartTime)>(40.5*1000)&& ((currentTimeMillis - mStartTime) < (40.7 * 1000))){
                        try {
                            getWriteLock();

                            File tempDir = getFilesDir();
                            String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                            String imageFileName = "Capture_" + timeStamp + "_"; //ex) Capture_20201206_

                            File file = new File(tempDir, "back4.jpg");
                            mCurrentPhotoPath = file.getAbsolutePath();

                            String filename = file.toString();

                            Imgproc.cvtColor(matResult, matResult, Imgproc.COLOR_BGR2RGBA);
                            boolean ret = Imgcodecs.imwrite(filename, matResult);
                            if (ret) Log.d(TAG, "SUCESS");
                            else Log.d(TAG, "FAIL");

                            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                            mediaScanIntent.setData(Uri.fromFile(file));
                            sendBroadcast(mediaScanIntent);

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        releaseWriteLock();
                    }
                    if((currentTimeMillis-mStartTime)>(50.5*1000)&& ((currentTimeMillis - mStartTime) < (50.7 * 1000))){
                        try {
                            getWriteLock();

                            File tempDir = getFilesDir();
                            String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                            String imageFileName = "Capture_" + timeStamp + "_"; //ex) Capture_20201206_

                            File file = new File(tempDir, "back5.jpg");
                            mCurrentPhotoPath = file.getAbsolutePath();

                            String filename = file.toString();

                            Imgproc.cvtColor(matResult, matResult, Imgproc.COLOR_BGR2RGBA);
                            boolean ret = Imgcodecs.imwrite(filename, matResult);
                            if (ret) Log.d(TAG, "SUCESS");
                            else Log.d(TAG, "FAIL");

                            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                            mediaScanIntent.setData(Uri.fromFile(file));
                            sendBroadcast(mediaScanIntent);

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        releaseWriteLock();
                    }
                    if((currentTimeMillis-mStartTime)>(60.5*1000)&& ((currentTimeMillis - mStartTime) < (60.6 * 1000))){
                        try {
                            getWriteLock();

                            File tempDir = getFilesDir();
                            String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                            String imageFileName = "Capture_" + timeStamp + "_"; //ex) Capture_20201206_

                            File file = new File(tempDir, "back6.jpg");
                            mCurrentPhotoPath = file.getAbsolutePath();

                            String filename = file.toString();

                            Imgproc.cvtColor(matResult, matResult, Imgproc.COLOR_BGR2RGBA);
                            boolean ret = Imgcodecs.imwrite(filename, matResult);
                            if (ret) Log.d(TAG, "SUCESS");
                            else Log.d(TAG, "FAIL");

                            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                            mediaScanIntent.setData(Uri.fromFile(file));
                            sendBroadcast(mediaScanIntent);

                            Intent i=new Intent(BackImage.this, MainActivity2.class);
                            startActivity(i);

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        releaseWriteLock();
                    }
                }
            });
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        releaseWriteLock();

        return matResult;
    }

    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }

    //여기서부턴 퍼미션 관련 메소드
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;

    protected void onCameraPermissionGranted() {
        List<? extends CameraBridgeViewBase> cameraViews = getCameraViewList();
        if (cameraViews == null) {
            return;
        }
        for (CameraBridgeViewBase cameraBridgeViewBase: cameraViews) {
            if (cameraBridgeViewBase != null) {
                cameraBridgeViewBase.setCameraPermissionGranted();
                read_cascade_file();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        boolean havePermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(CAMERA) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{CAMERA, WRITE_EXTERNAL_STORAGE}, CAMERA_PERMISSION_REQUEST_CODE);
                havePermission = false;
            }
        }
        if (havePermission) {
            onCameraPermissionGranted();
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            onCameraPermissionGranted();
        }else{
            showDialogForPermission("앱을 실행하려면 퍼미션을 허가하셔야합니다.");
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void showDialogForPermission(String msg) {

        AlertDialog.Builder builder = new AlertDialog.Builder( BackImage.this);
        builder.setTitle("알림");
        builder.setMessage(msg);
        builder.setCancelable(false);
        builder.setPositiveButton("예", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id){
                requestPermissions(new String[]{CAMERA, WRITE_EXTERNAL_STORAGE}, CAMERA_PERMISSION_REQUEST_CODE);
            }
        });
        builder.setNegativeButton("아니오", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface arg0, int arg1) {
                finish();
            }
        });
        builder.create().show();
    }
}