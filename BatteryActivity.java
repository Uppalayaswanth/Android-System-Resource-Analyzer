package com.example.resourcemapperapp;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class BatteryActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battery);

        StatsProvider.BatteryDetails d = new StatsProvider(this).collectBatteryDetails();

        ((TextView) findViewById(R.id.rowStatus)).setText(d.status);
        ((TextView) findViewById(R.id.rowBatteryLevel)).setText(d.levelWithCapacity);
        ((TextView) findViewById(R.id.rowVoltage)).setText(d.voltage);
        ((TextView) findViewById(R.id.rowCapacity)).setText(d.capacityMah);
        ((TextView) findViewById(R.id.rowTechnology)).setText(d.technology);
        ((TextView) findViewById(R.id.rowThermal)).setText(d.thermalState);
        ((TextView) findViewById(R.id.rowHealth)).setText(d.health);
    }
}

















