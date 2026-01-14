package com.example.resourcemapperapp;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class StorageActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_storage);

        StatsProvider.StorageDetails d = new StatsProvider(this).collectStorageDetails();
        ((TextView) findViewById(R.id.rowTotal)).setText(d.total);
        ((TextView) findViewById(R.id.rowUsed)).setText(d.used);
        ((TextView) findViewById(R.id.rowFree)).setText(d.free);
        ((TextView) findViewById(R.id.rowUsedPercent)).setText(d.usedPercent);
        ((TextView) findViewById(R.id.rowFsType)).setText(d.fsType);
        ((TextView) findViewById(R.id.rowPath)).setText(d.path);
    }
}

















