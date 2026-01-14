package com.example.resourcemapperapp;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class DisplayActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display);

        StatsProvider.DisplayDetails d = new StatsProvider(this).collectDisplayDetails();
        ((TextView) findViewById(R.id.rowScreenSize)).setText(d.screenSize);
        ((TextView) findViewById(R.id.rowAspectRatio)).setText(d.aspectRatio);
        ((TextView) findViewById(R.id.rowPixelDensity)).setText(d.pixelDensity);
        ((TextView) findViewById(R.id.rowBrightness)).setText(d.brightness);
        ((TextView) findViewById(R.id.rowScreenFps)).setText(d.fps);
        ((TextView) findViewById(R.id.rowScreenHz)).setText(d.hz);
        ((TextView) findViewById(R.id.rowCurrentEdr)).setText(d.currentEdr);
        ((TextView) findViewById(R.id.rowPotentialEdr)).setText(d.potentialEdr);
        ((TextView) findViewById(R.id.rowVertRes)).setText(d.vertRes);
        ((TextView) findViewById(R.id.rowHorizRes)).setText(d.horizRes);
        ((TextView) findViewById(R.id.rowFrameRate)).setText(d.frameRate);
        ((TextView) findViewById(R.id.rowColorGamut)).setText(d.colorGamut);
    }
}















