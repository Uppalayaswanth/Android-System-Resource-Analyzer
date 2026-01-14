package com.example.resourcemapperapp;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class OsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_os);

        StatsProvider.OsDetails d = new StatsProvider(this).collectOsDetails();
        ((TextView) findViewById(R.id.rowOsName)).setText(d.osName);
        ((TextView) findViewById(R.id.rowVersion)).setText(d.version);
        ((TextView) findViewById(R.id.rowBuild)).setText(d.build);
        ((TextView) findViewById(R.id.rowMultitasking)).setText(d.multitasking);
        ((TextView) findViewById(R.id.rowKernType)).setText(d.kernType);
        ((TextView) findViewById(R.id.rowKernBuild)).setText(d.kernBuild);
        ((TextView) findViewById(R.id.rowNativeOsVersion)).setText(d.nativeOsVersion);
        ((TextView) findViewById(R.id.rowMaxSupportedVersion)).setText(d.maxSupportedVersion);
    }
}

















