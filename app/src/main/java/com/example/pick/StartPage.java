package com.example.pick;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class StartPage extends AppCompatActivity {

    Button btn_syes, btn_sno;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_page);

        btn_syes=findViewById(R.id.btn_syes);
        btn_syes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i=new Intent(StartPage.this, MainActivity2.class);
                startActivity(i);
            }
        });

        btn_sno=findViewById(R.id.btn_sno);
        btn_sno.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i2=new Intent(StartPage.this, FinishPage.class);
                startActivity(i2);
            }
        });
    }
}