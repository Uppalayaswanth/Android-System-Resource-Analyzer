package com.example.resourcemapperapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ProcessorActivity extends AppCompatActivity {

    private StatsProvider statsProvider;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            updateProcessorDetails();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processor);

        statsProvider = new StatsProvider(this);
        // Initial update - this will show "-" for CPU usage (needs 2 samples)
        updateProcessorDetails();
        
        // Start continuous updates immediately
        // This ensures CPU usage updates every second continuously
        handler.post(updateTask);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ensure updates continue when activity resumes
        // Remove any existing callbacks first to avoid duplicates
        handler.removeCallbacks(updateTask);
        handler.post(updateTask);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop updates when screen is not visible to save resources
        handler.removeCallbacks(updateTask);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up handler when activity is destroyed
        handler.removeCallbacks(updateTask);
    }

    private void updateProcessorDetails() {
        StatsProvider.ProcessorDetails d = statsProvider.collectProcessorDetails();

        ((TextView) findViewById(R.id.rowModel)).setText(d.model);
        // CPU usage (rowUsage) comes from StatsProvider (overall CPU when available)
        ((TextView) findViewById(R.id.rowUsage)).setText(d.usagePercent);
        ((TextView) findViewById(R.id.rowCurFreq)).setText(d.currentFreq);
        ((TextView) findViewById(R.id.rowDesignFreq)).setText(d.designFreq);
        ((TextView) findViewById(R.id.rowInstrSet)).setText(d.instructionSet);
        ((TextView) findViewById(R.id.rowMicroArch)).setText(d.microArch);
        ((TextView) findViewById(R.id.rowProcess)).setText(d.processNm);
        ((TextView) findViewById(R.id.rowCoreCount)).setText(d.coreCount);
        ((TextView) findViewById(R.id.rowThermal)).setText(d.thermalState);

        ((TextView) findViewById(R.id.rowCoProcModel)).setText(d.coprocessorModel);
        ((TextView) findViewById(R.id.rowGpuType)).setText(d.gpuType);
        ((TextView) findViewById(R.id.rowGpuCoreCount)).setText(d.gpuCoreCount);
    }
}
