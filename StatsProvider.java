package com.example.resourcemapperapp;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.TrafficStats;
import android.provider.Settings;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.util.DisplayMetrics;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StatsProvider {

    public static class NetSample {
        public long rxBytes;
        public long txBytes;
    }

    public static class Snapshot {
        public Float cpuPercent; // null if unavailable (not shown in UI)
        public String memHuman;
        public String batteryHuman;
        public String storageHuman;
        public String networkHuman;
        public String deviceHuman;
        public String processorHuman;
        public String processorModel;
        public String processorGpuType;
        public String processorGpuCoreCount;
        public String displayHuman;
        public String osHuman;
    }

    public static class DeviceDetails {
        public String model;
        public String deviceString;
        public String motherboardId;
        public String released;
        public String thermalState;
        public String bluetoothVersion;
        public String bluetoothController;
        public String bluetoothSmart;
        public String heightMm;
        public String widthMm;
        public String depthMm;
        public String weightG;
    }

    public static class BatteryDetails {
        public String status;
        public String levelWithCapacity;
        public String voltage;
        public String capacityMah;
        public String technology;
        public String thermalState;
        public String lastCharge;
        public String remainingCapacity;
        public String health;
    }

    public static class DisplayDetails {
        public String screenSize;
        public String aspectRatio;
        public String pixelDensity;
        public String brightness;
        public String fps;
        public String hz;
        public String screenCaptured;
        public String currentEdr;
        public String potentialEdr;
        public String vertRes;
        public String horizRes;
        public String contrastRatio;
        public String brightnessTypical;
        public String technology;
        public String frameRate;
        public String colorGamut;
    }

    public static class MemoryDetails {
        public String designCapacity;
        public String memoryType;
        public String capacity;
        public String free;
        public String active;
        public String inactive;
        public String wired;
        public String compressed;
        public String pressure;
        public String pageIns;
        public String pageOuts;
        public String pageFaults;
    }

    public static class OsDetails {
        public String osName;
        public String version;
        public String build;
        public String multitasking;
        public String kernType;
        public String kernBuild;
        public String lastReboot;
        public String runningDuration;
        public String activeDuration;
        public String nativeOsVersion;
        public String maxSupportedVersion;
    }

    public static class StorageDetails {
        public String total;
        public String used;
        public String free;
        public String usedPercent;
        public String fsType;
        public String path;
    }

    public static class ProcessorDetails {
        public String model;
        public String usagePercent;
        public String currentFreq;
        public String designFreq;
        public String instructionSet;
        public String microArch;
        public String processNm;
        public String coreCount;
        public String thermalState;
        public String coprocessorModel;
        public String gpuType;
        public String gpuCoreCount;
    }

    private static class GpuInfo {
        String type = "-";
        String coreCount = "-";
    }

    private final Context context;
    private long lastAppCpuTimeNs;
    private long lastAppCpuTimestampMs;
    private NetSample lastNetSample;
    private long lastNetTimestampMs;
    private String glRendererCache;
    // For tracking overall (system-wide) CPU usage between samples
    private long lastTotalCpuIdle;
    private long lastTotalCpuTotal;
    private boolean usingTotalCpu = false; // Track if we're successfully using overall CPU

    public StatsProvider(Context context) {
        this.context = context.getApplicationContext();
    }

    public Snapshot collectSnapshot() {
        Snapshot s = new Snapshot();
        s.cpuPercent = readCpuUsagePercent();
        s.memHuman = readMemoryHuman();
        s.batteryHuman = readBatteryHuman();
        s.storageHuman = readStorageHuman();
        s.networkHuman = readNetworkHuman();
        s.deviceHuman = readDeviceHuman();
        s.processorModel = getProcessorModelString();
        s.processorHuman = readProcessorHuman();
        GpuInfo gpuInfo = readGpuInfo();
        s.processorGpuType = gpuInfo.type;
        s.processorGpuCoreCount = gpuInfo.coreCount;
        s.displayHuman = readDisplayHuman();
        s.osHuman = readOsHuman();
        return s;
    }

    private Float readCpuUsagePercent() {
        // Calculate this app's CPU usage percentage
        // Uses Process.getElapsedCpuTime() which returns CPU time used by this process in milliseconds
        try {
            long currentCpuTimeMs = android.os.Process.getElapsedCpuTime();
            long currentTimestampMs = System.currentTimeMillis();
            
            if (lastAppCpuTimeNs == 0 || lastAppCpuTimestampMs == 0) {
                // First sample - initialize and return null
                // Store in nanoseconds for consistency (convert ms to ns)
                lastAppCpuTimeNs = currentCpuTimeMs * 1_000_000L;
                lastAppCpuTimestampMs = currentTimestampMs;
                return null; // Need a second sample to compute percentage
            }
            
            // Calculate delta
            long cpuTimeDeltaMs = currentCpuTimeMs - (lastAppCpuTimeNs / 1_000_000L);
            long timeDeltaMs = currentTimestampMs - lastAppCpuTimestampMs;
            
            // Update for next iteration (store in nanoseconds)
            lastAppCpuTimeNs = currentCpuTimeMs * 1_000_000L;
            lastAppCpuTimestampMs = currentTimestampMs;
            
            if (timeDeltaMs <= 0) {
                return null; // Invalid time delta
            }
            
            // Calculate CPU usage percentage
            // cpuTimeDeltaMs is CPU time used by this app in milliseconds
            // timeDeltaMs is wall clock time in milliseconds
            // For multi-core systems: account for number of cores
            // If app uses 100ms CPU time over 1000ms wall time on 8 cores:
            // usage = (100 / (1000 * 8)) * 100 = 1.25%
            int numCores = Runtime.getRuntime().availableProcessors();
            double totalAvailableCpuTimeMs = timeDeltaMs * numCores;
            
            if (totalAvailableCpuTimeMs <= 0) {
                return null;
            }
            
            float usage = (float) ((cpuTimeDeltaMs / totalAvailableCpuTimeMs) * 100.0);
            if (usage < 0f) usage = 0f;
            if (usage > 100f) usage = 100f;
            
            return usage;
        } catch (Exception e) {
            // If getElapsedCpuTime() is not available (shouldn't happen on Android)
            return null;
        }
    }
    
    /**
     * Reads overall (system-wide) CPU usage percentage based on /proc/stat.
     * This does NOT require root, but relies on Linux procfs, which may vary by device/Android version.
     * The first call initializes the baseline and returns null; subsequent calls return a percentage.
     */
    private Float readTotalCpuUsagePercent() {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader("/proc/stat"));
            String line = reader.readLine();
            if (line == null || !line.startsWith("cpu ")) {
                // If we can't read /proc/stat, mark as not using and reset state
                usingTotalCpu = false;
                lastTotalCpuIdle = 0L;
                lastTotalCpuTotal = 0L;
                return null;
            }

            String[] toks = line.trim().split("\\s+");
            if (toks.length < 5) {
                usingTotalCpu = false;
                lastTotalCpuIdle = 0L;
                lastTotalCpuTotal = 0L;
                return null;
            }

            // Parse CPU stats from /proc/stat
            // Format: cpu user nice system idle iowait irq softirq steal ...
            long user    = parseLongSafe(toks, 1);
            long nice    = parseLongSafe(toks, 2);
            long system  = parseLongSafe(toks, 3);
            long idle    = parseLongSafe(toks, 4);
            long iowait  = toks.length > 5 ? parseLongSafe(toks, 5) : 0L;
            long irq     = toks.length > 6 ? parseLongSafe(toks, 6) : 0L;
            long softirq = toks.length > 7 ? parseLongSafe(toks, 7) : 0L;
            long steal   = toks.length > 8 ? parseLongSafe(toks, 8) : 0L;

            // Calculate idle time (idle + iowait)
            // iowait is time waiting for I/O, which is also considered "idle" for CPU usage
            long idleAll = idle + iowait;
            
            // Calculate non-idle time (all active CPU usage)
            long nonIdle = user + nice + system + irq + softirq + steal;
            
            // Total CPU time = idle + non-idle
            long total   = idleAll + nonIdle;
            
            // Verify we have valid values
            if (total <= 0L) {
                // Invalid total, but preserve state if we were working
                return null;
            }

            // Check for overflow/wraparound (values should always increase)
            // But allow for small decreases due to system resets or clock adjustments
            // Only reset if the decrease is significant (more than 10% of previous total)
            if (lastTotalCpuTotal > 0L && total < lastTotalCpuTotal) {
                long decrease = lastTotalCpuTotal - total;
                // If decrease is significant, it's likely a wraparound/reset
                if (decrease > (lastTotalCpuTotal / 10)) {
                    // Values wrapped around or reset - reinitialize but keep using flag
                    lastTotalCpuIdle = idleAll;
                    lastTotalCpuTotal = total;
                    // Return null to skip this sample, next one will work
                    return null;
                }
                // Small decrease - might be clock adjustment, continue with calculation
            }

            if (lastTotalCpuIdle == 0L && lastTotalCpuTotal == 0L) {
                // First sample - store baseline and return null
                // This is normal - we need 2 samples to calculate CPU usage
                lastTotalCpuIdle = idleAll;
                lastTotalCpuTotal = total;
                return null;
            }

            long totalDelta = total - lastTotalCpuTotal;
            long idleDelta  = idleAll - lastTotalCpuIdle;

            // Validate delta before updating state
            if (totalDelta <= 0L) {
                // Invalid delta - shouldn't happen, but handle gracefully
                // Don't reset state, just return null for this sample
                // Keep the state so next call can work
                return null;
            }
            
            // Ensure we have a reasonable delta (at least 1 jiffy)
            // This prevents division by zero or very small numbers
            if (totalDelta < 1L) {
                return null;
            }
            
            // Now update state AFTER validation (so next call has correct baseline)
            // This ensures we only update state when we have valid data
            lastTotalCpuIdle = idleAll;
            lastTotalCpuTotal = total;

            // Calculate CPU usage: (total - idle) / total * 100
            // This gives us the percentage of CPU time that was NOT idle
            // This is the real-time CPU usage over the last sampling interval (1 second)
            // Formula: (non-idle time) / (total time) * 100
            // 
            // totalDelta = total CPU time used in the last second (across all cores)
            // idleDelta = idle CPU time in the last second
            // (totalDelta - idleDelta) = active CPU time
            // usage = active CPU time / total CPU time * 100
            float usage = (float) (totalDelta - idleDelta) * 100f / (float) totalDelta;
            
            // Clamp to valid range (0-100%)
            if (usage < 0f) usage = 0f;
            if (usage > 100f) usage = 100f;
            
            // Return raw real-time CPU usage without smoothing
            // This ensures accurate representation of actual system load
            // If system is busy, usage will be high; if idle, usage will be low
            
            // Mark that we're successfully using overall CPU
            usingTotalCpu = true;
            return usage;
        } catch (Exception e) {
            // On error, only reset state if we're sure it won't work
            // Don't reset if we were previously working (might be temporary error)
            // This ensures continuous updates even if there's a temporary read error
            if (!usingTotalCpu) {
                lastTotalCpuIdle = 0L;
                lastTotalCpuTotal = 0L;
            }
            // Return null but keep state - next call might work
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {}
            }
        }
    }
    
    private String readDeviceHuman() {
        String model = safeString(Build.MODEL);
        String deviceString = safeString(Build.DEVICE);
        String board = safeString(Build.BOARD);
        String released = safeString(Build.VERSION.SECURITY_PATCH); // best available date string

        String thermal = getThermalStatusHuman();

        PackageManager pm = context.getPackageManager();
        boolean bleSupported = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
        String btVersion = "-"; // Android does not expose precise BT version reliably
        String btController = "-"; // Controller model is not exposed via public API

        // Dimensions (approximate) using display metrics
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        double widthMm = (dm.xdpi > 0) ? (dm.widthPixels / dm.xdpi * 25.4) : 0;
        double heightMm = (dm.ydpi > 0) ? (dm.heightPixels / dm.ydpi * 25.4) : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("BASIC\n");
        sb.append("Model\t\t\t").append(model != null ? model : "-").append('\n');
        sb.append("Device String\t").append(deviceString != null ? deviceString : "-").append('\n');
        sb.append("Motherboard ID\t").append(board != null ? board : "-").append('\n');
        sb.append("Released\t\t").append(released != null ? released : "-").append('\n');
        sb.append("Thermal State\t").append(thermal).append("\n\n");

        sb.append("BLUETOOTH\n");
        sb.append("Version\t\t\t").append(btVersion).append('\n');
        sb.append("Controller\t\t").append(btController).append('\n');
        sb.append("Bluetooth Smart\t").append(bleSupported ? "YES" : "NO").append("\n\n");

        sb.append("DIMENSIONS\n");
        sb.append("Height\t\t\t").append(heightMm > 0 ? String.format("%.1fmm", heightMm) : "-").append('\n');
        sb.append("Width\t\t\t").append(widthMm > 0 ? String.format("%.1fmm", widthMm) : "-").append('\n');
        sb.append("Depth\t\t\t-").append('\n');
        sb.append("Weight\t\t\t-");
        return sb.toString();
    }

    public DeviceDetails collectDeviceDetails() {
        DeviceDetails d = new DeviceDetails();
        String model = safeString(Build.MODEL);
        String deviceString = safeString(Build.DEVICE);
        String board = safeString(Build.BOARD);
        String released = safeString(Build.VERSION.SECURITY_PATCH);
        d.model = model != null ? model : "-";
        d.deviceString = deviceString != null ? deviceString : "-";
        d.motherboardId = board != null ? board : "-";
        d.released = released != null ? released : "-";
        d.thermalState = getThermalStatusHuman();

        PackageManager pm = context.getPackageManager();
        boolean bleSupported = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
        boolean btSupported = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
        
        // Try to get Bluetooth adapter info
        try {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            if (btAdapter != null) {
                // Get Bluetooth version - Android doesn't expose exact version, but we can check if it's available
                try {
                    if (btAdapter.isEnabled()) {
                        d.bluetoothVersion = "Available";
                    } else {
                        d.bluetoothVersion = "Disabled";
                    }
                } catch (SecurityException e) {
                    // Permission denied on Android 12+, but adapter exists
                    d.bluetoothVersion = "Available (Permission Required)";
                }
                
                // Try to get adapter name/address as controller identifier
                try {
                    String address = btAdapter.getAddress();
                    String name = btAdapter.getName();
                    if (name != null && !name.isEmpty() && !name.equals("null")) {
                        d.bluetoothController = name;
                    } else if (address != null && !address.isEmpty() && !address.equals("02:00:00:00:00:00") && !address.equals("null")) {
                        d.bluetoothController = address;
                    } else {
                        d.bluetoothController = "Available";
                    }
                } catch (SecurityException e) {
                    // Permission denied on Android 12+, but adapter exists
                    d.bluetoothController = "Available (Permission Required)";
                }
            } else if (btSupported || bleSupported) {
                // Bluetooth is supported but adapter is null (rare case)
                d.bluetoothVersion = "Supported";
                d.bluetoothController = "Supported";
            } else {
                d.bluetoothVersion = "Not Available";
                d.bluetoothController = "Not Available";
            }
        } catch (Exception e) {
            // Bluetooth not available or permission denied
            if (btSupported || bleSupported) {
                d.bluetoothVersion = "Supported";
                d.bluetoothController = "Supported";
            } else {
                d.bluetoothVersion = "Not Available";
                d.bluetoothController = "Not Available";
            }
        }
        d.bluetoothSmart = bleSupported ? "YES" : "NO";

        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        double widthMm = (dm.xdpi > 0) ? (dm.widthPixels / dm.xdpi * 25.4) : 0;
        double heightMm = (dm.ydpi > 0) ? (dm.heightPixels / dm.ydpi * 25.4) : 0;
        d.heightMm = heightMm > 0 ? String.format("%.1fmm", heightMm) : "-";
        d.widthMm = widthMm > 0 ? String.format("%.1fmm", widthMm) : "-";
        d.depthMm = "-";
        d.weightG = "-";
        return d;
    }

    public BatteryDetails collectBatteryDetails() {
        BatteryDetails d = new BatteryDetails();
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);
        
        if (batteryStatus == null) {
            d.status = "-";
            d.levelWithCapacity = "-";
            d.voltage = "-";
            d.capacityMah = "-";
            d.technology = "-";
            d.thermalState = "-";
            d.lastCharge = "-";
            d.remainingCapacity = "-";
            d.health = "-";
            return d;
        }

        // Status
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        String statusStr;
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: statusStr = "Charging"; break;
            case BatteryManager.BATTERY_STATUS_DISCHARGING: statusStr = "Discharging"; break;
            case BatteryManager.BATTERY_STATUS_FULL: statusStr = "Full"; break;
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: statusStr = "Not charging"; break;
            default: statusStr = "Unknown"; break;
        }
        d.status = statusStr;

        // Level and capacity
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float pct = (level >= 0 && scale > 0) ? (level * 100f / scale) : -1f;
        String levelStr = pct >= 0 ? String.format("%.0f%%", pct) : "-";
        
        // Battery capacity (mAh) - try to get from BatteryManager if available
        String capacityStr = "-";
        long remainingChargeMicroAh = -1;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
                if (bm != null) {
                    // Get remaining charge counter in microampere-hours
                    remainingChargeMicroAh = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                    // Get energy counter in nanowatt-hours (API 21+)
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                            long energyCounter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);
                            if (energyCounter > 0) {
                                // Energy counter is in nanowatt-hours, voltage is in millivolts
                                int voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                                if (voltage > 0) {
                                    // energy (nWh) / voltage (mV) = charge (nAh) = charge / 1000000 mAh
                                    // But we need current capacity, not total
                                    // This is complex, so we'll just show the remaining charge if available
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        } catch (Exception e) {
            // Ignored
        }
        
        // Calculate remaining capacity from charge counter if available
        if (remainingChargeMicroAh > 0) {
            long remainingMah = remainingChargeMicroAh / 1000; // Convert microAh to mAh
            d.remainingCapacity = String.format("%d mAh", remainingMah);
            // Try to estimate total capacity from remaining capacity and percentage
            if (pct > 0 && pct <= 100) {
                long estimatedTotalMah = (long) (remainingMah / (pct / 100.0));
                capacityStr = String.format("%d mAh", estimatedTotalMah);
            }
        } else {
            d.remainingCapacity = "-";
        }
        
        d.levelWithCapacity = levelStr;
        d.capacityMah = capacityStr;

        // Voltage
        int voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        d.voltage = voltage > 0 ? String.format("%.0f V", voltage / 1000f) : "-";

        // Technology
        String technology = batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
        d.technology = technology != null && !technology.isEmpty() ? technology : "-";

        // Thermal state
        d.thermalState = getThermalStatusHuman();

        // Health
        int health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
        String healthStr;
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_COLD: healthStr = "Cold"; break;
            case BatteryManager.BATTERY_HEALTH_DEAD: healthStr = "Dead"; break;
            case BatteryManager.BATTERY_HEALTH_GOOD: healthStr = "Good"; break;
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: healthStr = "Overheat"; break;
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: healthStr = "Over voltage"; break;
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: healthStr = "Unspecified failure"; break;
            default: healthStr = "Unknown"; break;
        }
        d.health = healthStr;

        // Last charge and remaining capacity - not directly available from battery status
        d.lastCharge = "-"; // Android doesn't expose last charge time via public API
        d.remainingCapacity = "-"; // Would need BatteryStatsManager which requires system permissions

        return d;
    }

    public DisplayDetails collectDisplayDetails() {
        DisplayDetails d = new DisplayDetails();
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        android.view.Display display = ((android.view.WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        float density = dm.density;
        float densityDpi = dm.densityDpi;
        float xdpi = dm.xdpi;
        float ydpi = dm.ydpi;
        
        // Screen size in inches
        double widthInches = width / xdpi;
        double heightInches = height / ydpi;
        double diagonalInches = Math.sqrt(widthInches * widthInches + heightInches * heightInches);
        d.screenSize = String.format("%.2f\"", diagonalInches);
        
        // Aspect ratio
        int gcd = gcd(width, height);
        int aspectWidth = width / gcd;
        int aspectHeight = height / gcd;
        d.aspectRatio = aspectWidth + ":" + aspectHeight;
        
        // Pixel density
        d.pixelDensity = String.format("%.1f dpi", densityDpi);
        
        // Resolution
        d.horizRes = width + " px";
        d.vertRes = height + " px";
        
        // Brightness - try to get from Settings.System
        try {
            int brightness = Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            // Brightness is 0-255, convert to percentage
            float brightnessPercent = (brightness / 255f) * 100f;
            d.brightness = String.format("%.0f%%", brightnessPercent);
        } catch (Exception e) {
            // Fallback to window brightness if available
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context instanceof android.app.Activity) {
                    android.view.WindowManager.LayoutParams params = ((android.app.Activity) context).getWindow().getAttributes();
                    float brightness = params.screenBrightness;
                    if (brightness >= 0) {
                        d.brightness = String.format("%.0f%%", brightness * 100);
                    } else {
                        d.brightness = "-";
                    }
                } else {
                    d.brightness = "-";
                }
            } catch (Exception e2) {
                d.brightness = "-";
            }
        }
        
        // Refresh rate (Hz) and frame rate
        float refreshRate = display.getRefreshRate();
        d.hz = String.format("%.0f Hz", refreshRate);
        d.frameRate = String.format("%.0f fps", refreshRate);
        d.fps = String.format("%.0f fps", refreshRate);
        
        // HDR (High Dynamic Range) - available on API 26+
        d.currentEdr = "-";
        d.potentialEdr = "-";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // HDR capability check via Configuration
                android.content.res.Configuration config = context.getResources().getConfiguration();
                if (config.isScreenWideColorGamut()) {
                    d.currentEdr = "HDR10";
                    d.potentialEdr = "HDR10+";
                } else {
                    d.currentEdr = "SDR";
                    d.potentialEdr = "HDR10";
                }
            }
        } catch (Exception e) {
            // HDR not available
        }
        
        // Screen captured - not directly available via public API
        d.screenCaptured = "-";
        
        // Contrast ratio - not available via public API
        d.contrastRatio = "-";
        
        // Typical brightness - not available via public API
        d.brightnessTypical = "-";
        
        // Technology (OLED, LCD, etc.) - not directly exposed via public API
        d.technology = "-";
        
        // Color gamut
        d.colorGamut = "-";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.content.res.Configuration config = context.getResources().getConfiguration();
                if (config.isScreenWideColorGamut()) {
                    d.colorGamut = "Wide Color Gamut (P3)";
                } else {
                    d.colorGamut = "sRGB";
                }
            }
        } catch (Exception e) {
            // Color gamut info not available
        }
        
        return d;
    }

    public MemoryDetails collectMemoryDetails() {
        MemoryDetails d = new MemoryDetails();
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        
        long totalMem = mi.totalMem;
        long availMem = mi.availMem;
        
        // Basic memory info
        d.capacity = humanBytes(totalMem);
        d.free = humanBytes(availMem);
        d.designCapacity = humanBytes(totalMem); // Design capacity is same as total on Android
        
        // Memory type - try to read from various sysfs paths
        d.memoryType = "-";
        BufferedReader memTypeReader = null;
        try {
            // Try multiple paths for memory type detection
            String[] paths = {
                "/sys/class/ddr_info/type",
                "/sys/class/ddr_info/ddr_type",
                "/sys/class/memory/memory0/uevent",
                "/proc/meminfo",
                "/sys/devices/system/memory/memory0/uevent"
            };
            
            for (String path : paths) {
                try {
                    if (memTypeReader != null) {
                        try { memTypeReader.close(); } catch (IOException ignored) {}
                    }
                    memTypeReader = new BufferedReader(new FileReader(path));
                    String line;
                    while ((line = memTypeReader.readLine()) != null) {
                        if (line == null || line.trim().isEmpty()) continue;
                        String lower = line.toLowerCase();
                        // Check for DDR types - be more specific
                        if (lower.contains("lpddr5") || lower.contains("ddr5")) {
                            d.memoryType = "LPDDR5";
                            break;
                        } else if (lower.contains("lpddr4") || lower.contains("ddr4")) {
                            d.memoryType = "LPDDR4";
                            break;
                        } else if (lower.contains("lpddr3") || lower.contains("ddr3")) {
                            d.memoryType = "LPDDR3";
                            break;
                        } else if (lower.contains("lpddr2") || lower.contains("ddr2")) {
                            d.memoryType = "LPDDR2";
                            break;
                        } else if (lower.contains("ddr") || lower.contains("dram")) {
                            // Generic DDR if no specific version found
                            if (d.memoryType.equals("-")) {
                                d.memoryType = "DDR";
                            }
                        }
                    }
                    if (!d.memoryType.equals("-")) {
                        break; // Found memory type, stop searching
                    }
                } catch (IOException e) {
                    // Try next path
                    continue;
                }
            }
            
            // If still not found, try to infer from device properties and Build info
            if (d.memoryType.equals("-")) {
                // Try Build properties as fallback
                String hardware = safeString(Build.HARDWARE);
                String model = safeString(Build.MODEL);
                String brand = safeString(Build.BRAND);
                String manufacturer = safeString(Build.MANUFACTURER);
                
                if (hardware != null || model != null || brand != null || manufacturer != null) {
                    String combined = ((hardware != null ? hardware : "") + " " + 
                                     (model != null ? model : "") + " " + 
                                     (brand != null ? brand : "") + " " + 
                                     (manufacturer != null ? manufacturer : "")).toLowerCase();
                    
                    // Check for modern devices (likely LPDDR4/LPDDR5)
                    if (combined.contains("snapdragon") || combined.contains("qualcomm") ||
                        combined.contains("exynos") || combined.contains("samsung") ||
                        combined.contains("mediatek") || combined.contains("dimensity") ||
                        combined.contains("kirin") || combined.contains("tensor")) {
                        // Most modern devices (2020+) use LPDDR4 or LPDDR5
                        d.memoryType = "LPDDR4/LPDDR5";
                    } else if (combined.contains("pixel") || combined.contains("galaxy") || 
                               combined.contains("oneplus") || combined.contains("xiaomi") ||
                               combined.contains("oppo") || combined.contains("vivo") ||
                               combined.contains("realme")) {
                        // Popular modern brands typically use LPDDR4/LPDDR5
                        d.memoryType = "LPDDR4/LPDDR5";
                    } else {
                        // Generic fallback
                        d.memoryType = "DDR";
                    }
                }
            }
        } catch (Exception e) {
            // If all methods fail, try basic inference
            try {
                String hardware = safeString(Build.HARDWARE);
                if (hardware != null && !hardware.isEmpty()) {
                    d.memoryType = "DDR";
                }
            } catch (Exception e2) {
                // Leave as "-"
            }
        } finally {
            if (memTypeReader != null) {
                try { memTypeReader.close(); } catch (IOException ignored) {}
            }
        }
        
        // Memory pressure
        if (mi.lowMemory) {
            d.pressure = "High";
        } else {
            float pressurePct = (totalMem - availMem) * 100f / (totalMem > 0 ? totalMem : 1);
            if (pressurePct < 50) {
                d.pressure = "Low";
            } else if (pressurePct < 80) {
                d.pressure = "Medium";
            } else {
                d.pressure = "High";
            }
        }
        
        // Try to read detailed memory stats from /proc/meminfo
        BufferedReader reader = null;
        long activeKb = 0;
        long inactiveKb = 0;
        long wiredKb = 0;
        long compressedKb = 0;
        long pageIns = 0;
        long pageOuts = 0;
        long pageFaults = 0;
        
        try {
            reader = new BufferedReader(new FileReader("/proc/meminfo"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Active:")) {
                    activeKb = parseMeminfoValue(line);
                } else if (line.startsWith("Inactive:")) {
                    inactiveKb = parseMeminfoValue(line);
                } else if (line.startsWith("Buffers:")) {
                    wiredKb += parseMeminfoValue(line);
                } else if (line.startsWith("Cached:")) {
                    wiredKb += parseMeminfoValue(line);
                } else if (line.startsWith("Compressed:")) {
                    compressedKb = parseMeminfoValue(line);
                }
            }
            reader.close();
            
            // Try to read vmstat for page stats
            reader = new BufferedReader(new FileReader("/proc/vmstat"));
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("pgpgin ")) {
                    pageIns = parseLongSafe(line.split("\\s+"), 1) * 1024; // Convert pages to KB
                } else if (line.startsWith("pgpgout ")) {
                    pageOuts = parseLongSafe(line.split("\\s+"), 1) * 1024; // Convert pages to KB
                } else if (line.startsWith("pgfault ")) {
                    pageFaults = parseLongSafe(line.split("\\s+"), 1);
                }
            }
        } catch (IOException e) {
            // /proc/meminfo or /proc/vmstat may not be accessible or may not exist
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {}
            }
        }
        
        // Set memory stats
        d.active = activeKb > 0 ? humanBytes(activeKb * 1024) : "-";
        d.inactive = inactiveKb > 0 ? humanBytes(inactiveKb * 1024) : "-";
        d.wired = wiredKb > 0 ? humanBytes(wiredKb * 1024) : "-";
        d.compressed = compressedKb > 0 ? humanBytes(compressedKb * 1024) : "-";
        d.pageIns = pageIns > 0 ? humanBytes(pageIns * 1024) : "-";
        d.pageOuts = pageOuts > 0 ? humanBytes(pageOuts * 1024) : "-";
        d.pageFaults = pageFaults > 0 ? String.valueOf(pageFaults) : "-";
        
        return d;
    }

    public OsDetails collectOsDetails() {
        OsDetails d = new OsDetails();
        
        // OS Name
        d.osName = "Android";
        
        // Version
        String release = safeString(Build.VERSION.RELEASE);
        d.version = release != null ? release : "-";
        
        // Build number
        String buildId = safeString(Build.ID);
        d.build = buildId != null ? buildId : "-";
        
        // Multitasking - Android always supports multitasking
        d.multitasking = "YES";
        
        // Kernel type
        d.kernType = "Linux";
        
        // Kernel build/version - try multiple methods
        d.kernBuild = "-";
        try {
            // Method 1: Read from /proc/version
            BufferedReader reader = new BufferedReader(new FileReader("/proc/version"));
            String kernelVersion = reader.readLine();
            reader.close();
            if (kernelVersion != null && !kernelVersion.trim().isEmpty()) {
                // Extract kernel version (e.g., "Linux version 4.14.xxx")
                String[] parts = kernelVersion.trim().split("\\s+");
                if (parts.length >= 3) {
                    d.kernBuild = parts[2]; // Usually the version number
                } else {
                    // Try to extract version from the string
                    // Look for pattern like "4.14.xxx" or "5.4.xxx"
                    Pattern versionPattern = Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?(?:-.*)?)");
                    Matcher matcher = versionPattern.matcher(kernelVersion);
                    if (matcher.find()) {
                        d.kernBuild = matcher.group(1);
                    } else {
                        d.kernBuild = kernelVersion.substring(0, Math.min(50, kernelVersion.length()));
                    }
                }
            }
        } catch (IOException e) {
            // Try alternative method
            try {
                // Method 2: Try reading from /proc/sys/kernel/osrelease
                BufferedReader reader = new BufferedReader(new FileReader("/proc/sys/kernel/osrelease"));
                String kernelRelease = reader.readLine();
                reader.close();
                if (kernelRelease != null && !kernelRelease.trim().isEmpty()) {
                    d.kernBuild = kernelRelease.trim();
                }
            } catch (IOException e2) {
                // Method 3: Try Build.VERSION.INCREMENTAL as fallback
                try {
                    String incremental = safeString(Build.VERSION.INCREMENTAL);
                    if (incremental != null && !incremental.isEmpty()) {
                        d.kernBuild = incremental;
                    }
                } catch (Exception e3) {
                    // Leave as "-"
                }
            }
        }
        
        // Last reboot / Uptime - Current Session Details
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/uptime"));
            String uptimeLine = reader.readLine();
            reader.close();
            if (uptimeLine != null && !uptimeLine.trim().isEmpty()) {
                String[] parts = uptimeLine.trim().split("\\s+");
                if (parts.length > 0 && !parts[0].isEmpty()) {
                    try {
                        double uptimeSecondsDouble = Double.parseDouble(parts[0]);
                        long uptimeSeconds = (long) uptimeSecondsDouble;
                        
                        if (uptimeSeconds > 0) {
                            long days = uptimeSeconds / 86400;
                            long hours = (uptimeSeconds % 86400) / 3600;
                            long minutes = (uptimeSeconds % 3600) / 60;
                            long seconds = uptimeSeconds % 60;
                            
                            if (days > 0) {
                                d.lastReboot = String.format("%d days, %d hours ago", days, hours);
                                d.runningDuration = String.format("%d days, %d hours", days, hours);
                            } else if (hours > 0) {
                                d.lastReboot = String.format("%d hours, %d minutes ago", hours, minutes);
                                d.runningDuration = String.format("%d hours, %d minutes", hours, minutes);
                            } else if (minutes > 0) {
                                d.lastReboot = String.format("%d minutes ago", minutes);
                                d.runningDuration = String.format("%d minutes", minutes);
                            } else {
                                d.lastReboot = String.format("%d seconds ago", seconds);
                                d.runningDuration = String.format("%d seconds", seconds);
                            }
                            
                            // Active duration same as running duration on Android
                            d.activeDuration = d.runningDuration;
                        } else {
                            d.lastReboot = "-";
                            d.runningDuration = "-";
                            d.activeDuration = "-";
                        }
                    } catch (NumberFormatException e) {
                        d.lastReboot = "-";
                        d.runningDuration = "-";
                        d.activeDuration = "-";
                    }
                } else {
                    d.lastReboot = "-";
                    d.runningDuration = "-";
                    d.activeDuration = "-";
                }
            } else {
                d.lastReboot = "-";
                d.runningDuration = "-";
                d.activeDuration = "-";
            }
        } catch (IOException e) {
            d.lastReboot = "-";
            d.runningDuration = "-";
            d.activeDuration = "-";
        }
        
        // Native OS version (same as Android version for Android)
        d.nativeOsVersion = d.version != null && !d.version.equals("-") ? d.version : "Android";
        
        // Max supported version - try to infer from SDK level or device capabilities
        d.maxSupportedVersion = "-";
        try {
            // Get current SDK level
            int currentSdk = Build.VERSION.SDK_INT;
            // Try to get the release version name for current SDK
            String currentRelease = safeString(Build.VERSION.RELEASE);
            
            // For compatibility, show current version as max supported
            // (Android doesn't expose future update capability via public API)
            if (currentRelease != null && !currentRelease.isEmpty()) {
                d.maxSupportedVersion = "Android " + currentRelease + " (SDK " + currentSdk + ")";
            } else {
                d.maxSupportedVersion = "SDK " + currentSdk;
            }
        } catch (Exception e) {
            // Leave as "-"
        }
        
        return d;
    }

    public StorageDetails collectStorageDetails() {
        StorageDetails d = new StorageDetails();
        StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        
        long blockSize, totalBlocks, availableBlocks;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            blockSize = stat.getBlockSizeLong();
            totalBlocks = stat.getBlockCountLong();
            availableBlocks = stat.getAvailableBlocksLong();
        } else {
            blockSize = stat.getBlockSize();
            totalBlocks = stat.getBlockCount();
            availableBlocks = stat.getAvailableBlocks();
        }
        
        long total = totalBlocks * blockSize;
        long avail = availableBlocks * blockSize;
        long used = Math.max(0, total - avail);
        float usedPct = (total > 0) ? (used * 100f / total) : 0f;
        
        d.total = humanBytes(total);
        d.used = humanBytes(used);
        d.free = humanBytes(avail);
        d.usedPercent = String.format("%.1f%%", usedPct);
        
        // File system type
        d.fsType = "ext4"; // Default on most Android devices
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/mounts"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("/data")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 3) {
                        d.fsType = parts[2]; // Third field is filesystem type
                        break;
                    }
                }
            }
            reader.close();
        } catch (IOException e) {
            // Keep default value
        }
        
        // Path
        d.path = Environment.getDataDirectory().getAbsolutePath();
        
        return d;
    }

    public ProcessorDetails collectProcessorDetails() {
        ProcessorDetails d = new ProcessorDetails();
        
        // Processor model
        String socModel = getFieldIfAvailable("SOC_MODEL");
        String socManufacturer = getFieldIfAvailable("SOC_MANUFACTURER");
        String hardware = safeString(Build.HARDWARE);
        
        if (socManufacturer != null && socModel != null) {
            d.model = socManufacturer + " " + socModel;
        } else if (socModel != null) {
            d.model = socModel;
        } else if (hardware != null) {
            d.model = hardware;
        } else {
            d.model = "-";
        }
        
        // CPU usage percent - overall (system-wide) if available
        // This uses /proc/stat via readTotalCpuUsagePercent().
        // It may return null for the very first sample (needs 2 samples).
        Float cpuUsage = readTotalCpuUsagePercent();
        // Fallback to this app's CPU usage if overall is unavailable
        if (cpuUsage == null) {
            cpuUsage = readCpuUsagePercent();
        }
        
        // Format the result - needs 2 samples, so first call may return null
        if (cpuUsage != null) {
            d.usagePercent = String.format("%.1f%%", cpuUsage);
        } else {
            // First sample or temporary error - will show value on next update (1 second later)
            // This happens because we need 2 samples to calculate the delta
            // The state is preserved, so the next call should work
            d.usagePercent = "-";
        }
        
        // Current frequency
        d.currentFreq = "-";
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"));
            String freqLine = reader.readLine();
            reader.close();
            if (freqLine != null) {
                long freqHz = Long.parseLong(freqLine.trim());
                d.currentFreq = String.format("%.2f GHz", freqHz / 1000000.0);
            }
        } catch (IOException | NumberFormatException e) {
            // Not available
        }
        
        // Design/Max frequency
        d.designFreq = "-";
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"));
            String freqLine = reader.readLine();
            reader.close();
            if (freqLine != null) {
                long freqHz = Long.parseLong(freqLine.trim());
                d.designFreq = String.format("%.2f GHz", freqHz / 1000000.0);
            }
        } catch (IOException | NumberFormatException e) {
            // Not available
        }
        
        // Instruction set / ABI
        String abi = (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) ? Build.SUPPORTED_ABIS[0] : null;
        d.instructionSet = abi != null ? abi : "-";
        
        // Microarchitecture - try to read from /proc/cpuinfo
        d.microArch = "-";
        BufferedReader cpuInfoReader = null;
        try {
            cpuInfoReader = new BufferedReader(new FileReader("/proc/cpuinfo"));
            String line;
            while ((line = cpuInfoReader.readLine()) != null) {
                if (line.toLowerCase().contains("hardware") || line.toLowerCase().contains("processor")) {
                    String[] parts = line.split(":");
                    if (parts.length > 1) {
                        String value = parts[1].trim();
                        if (!value.isEmpty() && !value.equalsIgnoreCase("unknown")) {
                            d.microArch = value;
                            break;
                        }
                    }
                }
                // Also check for CPU implementer/architecture info
                if (line.toLowerCase().startsWith("cpu implementer")) {
                    String[] parts = line.split(":");
                    if (parts.length > 1) {
                        String impl = parts[1].trim();
                        // Map implementer to architecture if possible
                        if (impl.equals("0x41")) {
                            d.microArch = "ARM"; // ARM Ltd
                        } else if (impl.equals("0x51")) {
                            d.microArch = "Qualcomm"; // Qualcomm
                        }
                    }
                }
            }
        } catch (IOException e) {
            // Not available
        } finally {
            if (cpuInfoReader != null) {
                try {
                    cpuInfoReader.close();
                } catch (IOException ignored) {}
            }
        }

        if (d.microArch == null || d.microArch.isEmpty() || "0".equals(d.microArch) || "-".equals(d.microArch)) {
            if (socModel != null && !socModel.isEmpty()) {
                d.microArch = socModel;
            } else if (hardware != null && !hardware.isEmpty()) {
                d.microArch = hardware;
            } else {
                d.microArch = "-";
            }
        }
        
        // Process name / nm (instruction set architecture)
        if (abi != null) {
            if (abi.contains("arm64")) {
                d.processNm = "arm64-v8a";
            } else if (abi.contains("armeabi-v7a")) {
                d.processNm = "armeabi-v7a";
            } else if (abi.contains("x86_64")) {
                d.processNm = "x86_64";
            } else if (abi.contains("x86")) {
                d.processNm = "x86";
            } else {
                d.processNm = abi;
            }
        } else {
            d.processNm = "-";
        }
        
        // Core count
        int coreCount = Runtime.getRuntime().availableProcessors();
        d.coreCount = String.valueOf(coreCount);
        
        // Thermal state
        d.thermalState = getThermalStatusHuman();
        
        // Coprocessor model - typically GPU or DSP
        d.coprocessorModel = "-";
        
        GpuInfo gpuInfo = readGpuInfo();
        d.gpuType = gpuInfo.type != null ? gpuInfo.type : "-";
        d.gpuCoreCount = gpuInfo.coreCount != null ? gpuInfo.coreCount : "-";

        if (gpuInfo.type != null && !gpuInfo.type.equals("-")) {
            d.coprocessorModel = gpuInfo.type;
        }
        if (d.gpuType.equals("-")) {
            d.gpuType = (hardware != null && !hardware.isEmpty()) ? hardware : "-";
        }
        if (d.gpuCoreCount.equals("-")) {
            String inferredFromRenderer = inferGpuCoreCountFromRenderer(gpuInfo.type);
            d.gpuCoreCount = inferredFromRenderer != null ? inferredFromRenderer : "Unknown";
        }
        
        return d;
    }
    
    private long parseMeminfoValue(String line) {
        try {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2) {
                return Long.parseLong(parts[1]);
            }
        } catch (Exception e) {
            // Ignored
        }
        return 0;
    }
    
    private int gcd(int a, int b) {
        while (b != 0) {
            int temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }

    private String getThermalStatusHuman() {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm == null) return "Unknown";
        int status = pm.getCurrentThermalStatus();
        switch (status) {
            case PowerManager.THERMAL_STATUS_NONE: return "None";
            case PowerManager.THERMAL_STATUS_LIGHT: return "Light";
            case PowerManager.THERMAL_STATUS_MODERATE: return "Moderate";
            case PowerManager.THERMAL_STATUS_SEVERE: return "Severe";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "Critical";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "Emergency";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "Shutdown";
            default: return "Unknown";
        }
    }

    private String readProcessorHuman() {
        String baseModel = getProcessorModelString();
        String abi = (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) ? Build.SUPPORTED_ABIS[0] : null;
        StringBuilder sb = new StringBuilder();
        if (baseModel != null && !baseModel.equals("-")) {
            sb.append(baseModel);
        }
        if (abi != null) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(abi);
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    private String getProcessorModelString() {
        String socModel = getFieldIfAvailable("SOC_MODEL");
        String socManufacturer = getFieldIfAvailable("SOC_MANUFACTURER");
        String hardware = safeString(Build.HARDWARE);
        StringBuilder sb = new StringBuilder();
        if (socManufacturer != null && !socManufacturer.isEmpty()) {
            sb.append(socManufacturer.trim());
            if (socModel != null && !socModel.isEmpty()) {
                sb.append(" ");
            }
        }
        if (socModel != null && !socModel.isEmpty()) {
            sb.append(socModel.trim());
        }
        if (sb.length() == 0 && hardware != null && !hardware.isEmpty()) {
            sb.append(hardware);
        }
        return sb.length() == 0 ? "-" : sb.toString().trim();
    }

    private String readDisplayHuman() {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        float density = dm.density;
        // Show resolution and density bucket
        String densityStr = String.format("%.1fx", density);
        return width + "×" + height + " @ " + densityStr;
    }

    private String readOsHuman() {
        String release = safeString(Build.VERSION.RELEASE);
        int sdk = Build.VERSION.SDK_INT;
        return (release != null ? ("Android " + release) : "Android") + " (SDK " + sdk + ")";
    }

    private static String safeString(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static String getFieldIfAvailable(String fieldName) {
        try {
            return (String) Build.class.getField(fieldName).get(null);
        } catch (Throwable t) {
            return null;
        }
    }


    private String readMemoryHuman() {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long total = mi.totalMem;
        long avail = mi.availMem;
        long used = Math.max(0, total - avail);
        float usedPct = (total > 0) ? (used * 100f / total) : 0f;
        return humanBytes(used) + " / " + humanBytes(total) + String.format(" (%.0f%%)", usedPct);
        
    }

    private String readBatteryHuman() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);
        if (batteryStatus == null) return "-";
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        float pct = (level >= 0 && scale > 0) ? (level * 100f / scale) : -1f;
        String statusStr;
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: statusStr = "Charging"; break;
            case BatteryManager.BATTERY_STATUS_DISCHARGING: statusStr = "Discharging"; break;
            case BatteryManager.BATTERY_STATUS_FULL: statusStr = "Full"; break;
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: statusStr = "Not charging"; break;
            default: statusStr = "Unknown"; break;
        }
        return (pct >= 0 ? String.format("%.0f%%", pct) : "-") + " (" + statusStr + ")";
    }

    private String readStorageHuman() {
        StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        long blockSize, totalBlocks, availableBlocks;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            blockSize = stat.getBlockSizeLong();
            totalBlocks = stat.getBlockCountLong();
            availableBlocks = stat.getAvailableBlocksLong();
        } else {
            blockSize = stat.getBlockSize();
            totalBlocks = stat.getBlockCount();
            availableBlocks = stat.getAvailableBlocks();
        }
        long total = totalBlocks * blockSize;
        long avail = availableBlocks * blockSize;
        long used = Math.max(0, total - avail);
        float usedPct = (total > 0) ? (used * 100f / total) : 0f;
        return humanBytes(used) + " / " + humanBytes(total) + String.format(" (%.0f%%)", usedPct);
    }

    private String readNetworkHuman() {
        long now = System.currentTimeMillis();
        NetSample nowSample = new NetSample();
        nowSample.rxBytes = TrafficStats.getTotalRxBytes();
        nowSample.txBytes = TrafficStats.getTotalTxBytes();
        if (lastNetSample == null) {
            lastNetSample = nowSample;
            lastNetTimestampMs = now;
            return "-";
        }
        long dt = Math.max(1, now - lastNetTimestampMs);
        long rxDelta = Math.max(0, nowSample.rxBytes - lastNetSample.rxBytes);
        long txDelta = Math.max(0, nowSample.txBytes - lastNetSample.txBytes);
        lastNetSample = nowSample;
        lastNetTimestampMs = now;
        String down = humanBytesPerSec(rxDelta, dt);
        String up = humanBytesPerSec(txDelta, dt);
        return "↓ " + down + "  ↑ " + up;
    }

    private static long parseLongSafe(String[] toks, int idx) {
        try {
            return Long.parseLong(toks[idx]);
        } catch (Throwable t) {
            return 0L;
        }
    }

    private static String humanBytes(long bytes) {
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        double b = (double) bytes;
        int u = 0;
        while (b >= 1024 && u < units.length - 1) {
            b /= 1024.0;
            u++;
        }
        if (u == 0) return ((long) b) + " " + units[u];
        return String.format("%.1f %s", b, units[u]);
    }

    private static String humanBytesPerSec(long bytesDelta, long millisDelta) {
        double perSec = (bytesDelta * 1000.0) / Math.max(1.0, millisDelta);
        final String[] units = new String[]{"B/s", "KB/s", "MB/s", "GB/s"};
        int u = 0;
        while (perSec >= 1024 && u < units.length - 1) {
            perSec /= 1024.0;
            u++;
        }
        if (u == 0) return ((long) perSec) + " " + units[u];
        return String.format("%.1f %s", perSec, units[u]);
    }

    private GpuInfo readGpuInfo() {
        GpuInfo info = new GpuInfo();
        String gpuModel = readFirstAvailableLine(
                "/sys/class/kgsl/kgsl-3d0/gpu_model",
                "/sys/class/misc/mali0/device/gpu_model",
                "/proc/gpuinfo"
        );
        if (gpuModel != null && !gpuModel.trim().isEmpty()) {
            info.type = gpuModel.trim();
        } else {
            String renderer = queryGlRenderer();
            if (renderer != null && !renderer.trim().isEmpty()) {
                info.type = renderer.trim();
            }
        }

        String coreCount = readGpuCoreCountFromFile("/sys/class/misc/mali0/device/gpuinfo");
        if (coreCount == null) {
            coreCount = readGpuCoreCountFromFile("/sys/class/kgsl/kgsl-3d0/gpuinfo");
        }
        if (coreCount == null) {
            coreCount = inferGpuCoreCountFromModel(info.type);
        }
        if (coreCount == null) {
            coreCount = inferGpuCoreCountFromRenderer(info.type);
        }
        if (coreCount != null && !coreCount.isEmpty()) {
            info.coreCount = coreCount;
        }
        return info;
    }

    private String readFirstAvailableLine(String... paths) {
        for (String path : paths) {
            if (path == null) continue;
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(path));
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    return line.trim();
                }
            } catch (IOException ignored) {
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException ignored) {}
                }
            }
        }
        return null;
    }

    private String readGpuCoreCountFromFile(String path) {
        if (path == null) return null;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(path));
            String line;
            while ((line = reader.readLine()) != null) {
                String lower = line.toLowerCase(Locale.US);
                if (lower.contains("core")) {
                    String digits = extractDigits(line);
                    if (digits != null) {
                        return digits;
                    }
                }
            }
        } catch (IOException ignored) {
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {}
            }
        }
        return null;
    }

    private String inferGpuCoreCountFromModel(String model) {
        if (model == null) return null;
        Matcher matcher = Pattern.compile("MP(\\d+)", Pattern.CASE_INSENSITIVE).matcher(model);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = Pattern.compile("MC(\\d+)", Pattern.CASE_INSENSITIVE).matcher(model);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = Pattern.compile("(\\d+)C", Pattern.CASE_INSENSITIVE).matcher(model);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String inferGpuCoreCountFromRenderer(String renderer) {
        if (renderer == null) return null;
        Matcher matcher = Pattern.compile("MC(\\d+)", Pattern.CASE_INSENSITIVE).matcher(renderer);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = Pattern.compile("(\\d+)\\s*cores?", Pattern.CASE_INSENSITIVE).matcher(renderer);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String queryGlRenderer() {
        if (glRendererCache != null) {
            return glRendererCache;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return null;
        }
        EGLDisplay display = null;
        EGLContext context = null;
        EGLSurface surface = null;
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (display == EGL14.EGL_NO_DISPLAY) return null;
            int[] version = new int[2];
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                return null;
            }
            int[] configAttribs = {
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, configs.length, numConfigs, 0)) {
                cleanupEgl(display, context, surface);
                return null;
            }
            int[] contextAttribs = {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL14.EGL_NONE
            };
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0);
            int[] surfaceAttribs = {
                    EGL14.EGL_WIDTH, 1,
                    EGL14.EGL_HEIGHT, 1,
                    EGL14.EGL_NONE
            };
            surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfaceAttribs, 0);
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                cleanupEgl(display, context, surface);
                return null;
            }
            String renderer = GLES20.glGetString(GLES20.GL_RENDERER);
            if (renderer == null) {
                renderer = GLES20.glGetString(GLES20.GL_VENDOR);
            }
            if (renderer != null && !renderer.trim().isEmpty()) {
                glRendererCache = renderer.trim();
            }
            cleanupEgl(display, context, surface);
            return glRendererCache;
        } catch (Throwable t) {
            cleanupEgl(display, context, surface);
            return null;
        }
    }

    private void cleanupEgl(EGLDisplay display, EGLContext context, EGLSurface surface) {
        if (display == null || display == EGL14.EGL_NO_DISPLAY) {
            return;
        }
        try {
            if (surface != null && surface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                EGL14.eglDestroySurface(display, surface);
            }
            if (context != null && context != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(display, context);
            }
            EGL14.eglTerminate(display);
        } catch (Throwable ignored) {
        } finally {
            EGL14.eglReleaseThread();
        }
    }

    private String extractDigits(String text) {
        if (text == null) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(c);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}



