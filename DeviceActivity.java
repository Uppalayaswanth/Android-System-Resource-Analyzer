package com.example.resourcemapperapp;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class DeviceActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);

        StatsProvider provider = new StatsProvider(this);
        StatsProvider.DeviceDetails d = provider.collectDeviceDetails();

        ((TextView) findViewById(R.id.rowModel)).setText(d.model);
        ((TextView) findViewById(R.id.rowDeviceString)).setText(d.deviceString);
        ((TextView) findViewById(R.id.rowMotherboard)).setText(d.motherboardId);
        ((TextView) findViewById(R.id.rowReleased)).setText(d.released);
        ((TextView) findViewById(R.id.rowThermal)).setText(d.thermalState);

        ((TextView) findViewById(R.id.rowBtVersion)).setText(d.bluetoothVersion);
        ((TextView) findViewById(R.id.rowBtController)).setText(d.bluetoothController);
        ((TextView) findViewById(R.id.rowBtSmart)).setText(d.bluetoothSmart);

        ((TextView) findViewById(R.id.rowHeight)).setText(d.heightMm);
        ((TextView) findViewById(R.id.rowWidth)).setText(d.widthMm);
    }
}


