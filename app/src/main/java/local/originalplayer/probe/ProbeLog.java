package local.originalplayer.probe;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

final class ProbeLog {
    static synchronized void write(Context context, String event, Object value) {
        try {
            JSONObject record = new JSONObject();
            record.put("monotonic_ns", SystemClock.elapsedRealtimeNanos());
            record.put("event", event);
            record.put("value", value instanceof JSONObject || value instanceof org.json.JSONArray
                    || value instanceof Number || value instanceof Boolean ? value : String.valueOf(value));
            File file = new File(context.getFilesDir(), "probe.jsonl");
            try (FileOutputStream stream = new FileOutputStream(file, true)) {
                stream.write((record.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                stream.getFD().sync();
            }
            Log.i("OriginalPlayerProbe", record.toString());
        } catch (Exception error) { Log.e("OriginalPlayerProbe", "Cannot persist trace", error); }
    }
}
