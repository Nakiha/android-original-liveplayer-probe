package local.originalplayer.probe;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import org.json.JSONObject;

/** OS reference samples; never presented as the original business report. */
final class DeviceTelemetry implements Runnable {
    private final Context context;
    private final Handler handler;
    private boolean active;
    DeviceTelemetry(Context context, Handler handler) { this.context = context; this.handler = handler; }
    void start() { if (!active) { active = true; handler.post(this); } }
    void stop() { active = false; handler.removeCallbacks(this); }
    @Override public void run() {
        if (!active) return;
        JSONObject sample = new JSONObject();
        try {
            sample.put("source", "probe_os_reference");
            sample.put("interval_ms", 5000);
            Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery != null && battery.hasExtra(BatteryManager.EXTRA_TEMPERATURE)) {
                int raw = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                sample.put("battery_temperature_raw", raw);
                sample.put("battery_temperature_c", raw / 10.0);
            }
            if (battery != null && battery.hasExtra(BatteryManager.EXTRA_PLUGGED))
                sample.put("battery_plugged", battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1));
            if (Build.VERSION.SDK_INT >= 29) {
                PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (power != null) sample.put("thermal_status", power.getCurrentThermalStatus());
            }
        } catch (Exception error) {
            try { sample.put("read_error", error.getClass().getName()); } catch (Exception ignored) { }
        }
        ProbeLog.write(context, "device_telemetry", sample);
        if (active) handler.postDelayed(this, 5000);
    }
}
