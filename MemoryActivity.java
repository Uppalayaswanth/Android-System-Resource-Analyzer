package com.example.resourcemapperapp;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MemoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_memory);

        StatsProvider provider = new StatsProvider(this);
        StatsProvider.MemoryDetails d = provider.collectMemoryDetails();

        ((TextView) findViewById(R.id.rowDesignCapacity)).setText(d.designCapacity);
        ((TextView) findViewById(R.id.rowMemoryType)).setText(d.memoryType);
        ((TextView) findViewById(R.id.rowCapacity)).setText(d.capacity);
        ((TextView) findViewById(R.id.rowFree)).setText(d.free);
        ((TextView) findViewById(R.id.rowActive)).setText(d.active);
        ((TextView) findViewById(R.id.rowInactive)).setText(d.inactive);
        ((TextView) findViewById(R.id.rowWired)).setText(d.wired);
        ((TextView) findViewById(R.id.rowPressure)).setText(d.pressure);
    }
}



