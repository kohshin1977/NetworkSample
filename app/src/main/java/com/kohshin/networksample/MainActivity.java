package com.kohshin.networksample;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    Button host_button;
    Button guest_button;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button hostButton = (Button)findViewById(R.id.host_button);
        Button guestButton = (Button)findViewById(R.id.guest_button);


        hostButton.setOnClickListener(new HostButtonListener());
        guestButton.setOnClickListener(new GuestButtonListener());


    }

    private class HostButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            //ホストの画面に遷移
            Intent intent = new Intent(getApplication(), HostActivity.class);
            startActivity(intent);
        }
    }

    private class GuestButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            //ゲストの画面に遷移
            Intent intent = new Intent(getApplication(), GuestActivity.class);
            startActivity(intent);
        }
    }
}