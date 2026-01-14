package com.example.resourcemapperapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private TextView deviceValue;
    private TextView processorValue;
    private TextView memValue;
    private TextView displayValue;
    private TextView batteryValue;
    private TextView osValue;
    private TextView storageValue;
    private TextView performanceValue;
    private StatsProvider statsProvider;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            updateStats();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        deviceValue = findViewById(R.id.deviceValue);
        processorValue = findViewById(R.id.processorValue);
        memValue = findViewById(R.id.memValue);
        displayValue = findViewById(R.id.displayValue);
        batteryValue = findViewById(R.id.batteryValue);
        osValue = findViewById(R.id.osValue);
        storageValue = findViewById(R.id.storageValue);
        performanceValue = findViewById(R.id.performanceValue);
        statsProvider = new StatsProvider(this);
        android.view.View deviceRow = findViewById(R.id.deviceRow);
        if (deviceRow != null) {
            deviceRow.setOnClickListener(v -> openDeviceDetails());
        }
        android.view.View processorRow = findViewById(R.id.processorRow);
        if (processorRow != null) {
            processorRow.setOnClickListener(v -> openProcessorDetails());
        }
        android.view.View memoryRow = findViewById(R.id.memoryRow);
        if (memoryRow != null) {
            memoryRow.setOnClickListener(v -> openMemoryDetails());
        }
        android.view.View batteryRow = findViewById(R.id.batteryRow);
        if (batteryRow != null) {
            batteryRow.setOnClickListener(v -> openBatteryDetails());
        }
        android.view.View osRow = findViewById(R.id.osRow);
        if (osRow != null) {
            osRow.setOnClickListener(v -> openOsDetails());
        }
        android.view.View storageRow = findViewById(R.id.storageRow);
        if (storageRow != null) {
            storageRow.setOnClickListener(v -> openStorageDetails());
        }
        android.view.View displayRow = findViewById(R.id.displayRow);
        if (displayRow != null) {
            displayRow.setOnClickListener(v -> openDisplayDetails());
        }
        android.view.View performanceRow = findViewById(R.id.performanceRow);
        if (performanceRow != null) {
            performanceRow.setOnClickListener(v -> openPerformanceDetails());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(updateTask);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(updateTask);
    }

    private void updateStats() {
        StatsProvider.Snapshot s = statsProvider.collectSnapshot();
        // Show only the model name beside the Device label
        deviceValue.setText(getPrettyDeviceName());

        // Show only the processor model in the overview row
        processorValue.setText(s.processorModel != null ? s.processorModel : "-");

        memValue.setText(s.memHuman);
        displayValue.setText(s.displayHuman);
        batteryValue.setText(s.batteryHuman);
        osValue.setText(s.osHuman);
        storageValue.setText(s.storageHuman);
        performanceValue.setText("-"); // Performance score requires running benchmark
    }

    private String getPrettyDeviceName() {
        String manufacturer = android.os.Build.MANUFACTURER;
        String model = android.os.Build.MODEL;
        if (manufacturer == null) manufacturer = "";
        if (model == null) model = "";
        String manClean = manufacturer.trim();
        String modelClean = model.trim();
        String lowerModel = modelClean.toLowerCase();
        String lowerMan = manClean.toLowerCase();
        if (!manClean.isEmpty() && (lowerModel.startsWith(lowerMan))) {
            return capitalize(modelClean);
        } else if (!manClean.isEmpty()) {
            return capitalize(manClean) + " " + modelClean;
        } else {
            return modelClean.isEmpty() ? "-" : capitalize(modelClean);
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) return s;
        return Character.toUpperCase(first) + s.substring(1);
    }

    private void openDeviceDetails() {
        String info = statsProvider.collectSnapshot().deviceHuman;
        android.content.Intent intent = new android.content.Intent(this, DeviceActivity.class);
        intent.putExtra("device_info", info);
        startActivity(intent);
    }

    private void openProcessorDetails() {
        android.content.Intent intent = new android.content.Intent(this, ProcessorActivity.class);
        startActivity(intent);
    }

    private void openMemoryDetails() {
        android.content.Intent intent = new android.content.Intent(this, MemoryActivity.class);
        startActivity(intent);
    }

    private void openBatteryDetails() {
        android.content.Intent intent = new android.content.Intent(this, BatteryActivity.class);
        startActivity(intent);
    }

    private void openOsDetails() {
        android.content.Intent intent = new android.content.Intent(this, OsActivity.class);
        startActivity(intent);
    }

    private void openStorageDetails() {
        android.content.Intent intent = new android.content.Intent(this, StorageActivity.class);
        startActivity(intent);
    }

    private void openDisplayDetails() {
        android.content.Intent intent = new android.content.Intent(this, DisplayActivity.class);
        startActivity(intent);
    }

    private void openPerformanceDetails() {
        android.content.Intent intent = new android.content.Intent(this, PerformanceActivity.class);
        startActivity(intent);
    }
}