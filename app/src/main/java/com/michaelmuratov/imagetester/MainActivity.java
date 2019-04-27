package com.michaelmuratov.imagetester;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {
    Activity activity;
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        Button single = findViewById(R.id.btn_single);
        Button multi = findViewById(R.id.btn_multi);

        activity = this;

        single.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent singleIntent = new Intent(activity, SingleImageInput.class);
                startActivity(singleIntent);
            }
        });

        multi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent multiIntent = new Intent(activity, MultiImageInput.class);
                startActivity(multiIntent);
            }
        });
    }
}
