package com.example.pick;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.wonderkiln.camerakit.CameraKitError;
import com.wonderkiln.camerakit.CameraKitEvent;
import com.wonderkiln.camerakit.CameraKitEventListener;
import com.wonderkiln.camerakit.CameraKitImage;
import com.wonderkiln.camerakit.CameraKitVideo;
import com.wonderkiln.camerakit.CameraView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
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
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity3 extends AppCompatActivity {

    private static final String MODEL_PATH = "colab.tflite";
    private static final boolean QUANT = false;
    private static final String LABEL_PATH = "labels.txt";
    private static final int INPUT_SIZE = 64;

    private static final String TAG3 ="TcpClient_class33" ;
    private static final String TAG = "mainactivity3";

    private Classifier classifier;

    private Executor executor = Executors.newSingleThreadExecutor();
    private TextView textViewResult;
    private Button btnDetectObject, btnToggleCamera;
    private ImageView imageViewResult;
    private CameraView cameraView;

    private TextView connecttext;
    private EditText editText;
    private boolean isConnected = false;
    private String recvMessage;
    private String mServerIP = null;
    private Socket mSocket = null;
    private PrintWriter mOut;
    private BufferedReader mIn;
    String imgName="back";
    private Thread mReceiverThread = null;
    int count=1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main3);

        cameraView = findViewById(R.id.cameraView);

        imageViewResult = findViewById(R.id.imageViewResult);
        textViewResult = findViewById(R.id.textViewResult);
        textViewResult.setMovementMethod(new ScrollingMovementMethod());

        btnToggleCamera = findViewById(R.id.btnToggleCamera);
        btnDetectObject = findViewById(R.id.btnDetectObject);

        Intent sendDate = getIntent();
        recvMessage = sendDate.getStringExtra("recvMessage");
        Log.d(TAG3, "recv message: "+recvMessage);
        connecttext=findViewById(R.id.connection_status_textview);
        editText=findViewById(R.id.input_string_edittext);

        cameraView.toggleFacing();

        cameraView.addCameraKitListener(new CameraKitEventListener() {
            @Override
            public void onEvent(CameraKitEvent cameraKitEvent) {

            }

            @Override
            public void onError(CameraKitError cameraKitError) {

            }

            @Override
            public void onImage(CameraKitImage cameraKitImage) {
                Bitmap bitmap = cameraKitImage.getBitmap();

                bitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false);

                imageViewResult.setImageBitmap(bitmap);

                final List<Classifier.Recognition> results = classifier.recognizeImage(bitmap);

                textViewResult.setText(results.toString());
            }
            public void saveBitmapToJpeg(Bitmap bitmap) {

                String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                File tempFile = new File(getFilesDir(), imgName+count+".jpg");    // 파일 경로와 이름 넣기
                try {
                    tempFile.createNewFile();   // 자동으로 빈 파일을 생성하기
                    FileOutputStream out = new FileOutputStream(tempFile);

                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);   // compress 함수를 사용해 스트림에 비트맵을 저장하기
                    out.close();    // 스트림 닫아주기
                    count++;
                    Toast.makeText(getApplicationContext(), "파일 저장 성공", Toast.LENGTH_SHORT).show();
                    bitmap.recycle();


                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(), "파일 저장 실패", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onVideo(CameraKitVideo cameraKitVideo) {

            }
        });

        btnToggleCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraView.toggleFacing();
            }
        });

        btnDetectObject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraView.captureImage();
            }
        });

        new Thread(new MainActivity3.ConnectThread("192.168.112.15", 2424)).start();

        initTensorFlowAndLoadModel();
    }
    @Override
    protected void onResume() {
        super.onResume();
        cameraView.start();
    }

    @Override
    protected void onPause() {
        cameraView.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isConnected = false;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                classifier.close();
            }
        });
    }
    private class ConnectThread implements Runnable {
        private String serverIP;
        private int serverPort;

        ConnectThread(String ip, int port) {
            serverIP = ip;
            serverPort = port;

            connecttext.setText("connecting to " + serverIP + ".......");
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
                        connecttext.setText("connected to " + serverIP);

                        mReceiverThread = new Thread(new MainActivity3.ReceiverThread());
                        mReceiverThread.start();
                    }else{
                        Log.d(TAG, "failed to connect to server " + serverIP);
                        connecttext.setText("failed to connect to server "  + serverIP);
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
    private void initTensorFlowAndLoadModel() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    classifier = TensorFlowImageClassifier.create(
                            getAssets(),
                            MODEL_PATH,
                            LABEL_PATH,
                            INPUT_SIZE,
                            QUANT);
                    makeButtonVisible();
                } catch (final Exception e) {
                    throw new RuntimeException("Error initializing TensorFlow!", e);
                }
            }
        });
    }

    private void makeButtonVisible() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                btnDetectObject.setVisibility(View.VISIBLE);
            }
        });
    }
}