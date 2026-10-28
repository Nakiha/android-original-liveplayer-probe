package local.originalplayer.probe;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.*;
import dalvik.system.DexClassLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;

/** The original APK supplies all engine classes and native implementations. */
public final class ProbeActivity extends Activity implements SurfaceHolder.Callback {
    private final Handler main = new Handler();
    private TextView output;
    private SurfaceView surface;
    private ClassLoader original;
    private Class<?> engineClass;
    private Object player;
    private Object observer;
    private boolean attempted;
    private String mode;
    private File root;
    private int engine;
    private SyntheticLiveSource synthetic;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        mode = getIntent().getStringExtra("mode");
        engine = getIntent().getIntExtra("engine", 0);
        root = new File(getIntent().getStringExtra("root"));
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        surface = new SurfaceView(this); surface.getHolder().addCallback(this);
        box.addView(surface, new LinearLayout.LayoutParams(-1, 0, 1));
        Button exit = new Button(this); exit.setText("停止并关闭探针进程");
        exit.setOnClickListener(v -> {
            shutdown(); finish(); android.os.Process.killProcess(android.os.Process.myPid());
        }); box.addView(exit);
        output = new TextView(this); output.setTextIsSelectable(true); output.setPadding(16, 10, 16, 10);
        ScrollView log = new ScrollView(this); log.addView(output); box.addView(log, new LinearLayout.LayoutParams(-1, 360));
        setContentView(box);
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            ProbeLog.write(this, "uncaught_java_failure", android.util.Log.getStackTraceString(error));
            android.os.Process.killProcess(android.os.Process.myPid());
        });
        record("probe_begin", "engine=" + engine + ", mode=" + mode + ", original=" + root);
        if (!"play".equals(mode)) main.post(this::runProbe);
    }

    private void record(String event, Object value) {
        ProbeLog.write(this, event, value);
        runOnUiThread(() -> {
            if (output.length() > 24000) output.setText(output.getText().subSequence(output.length() - 12000, output.length()));
            output.append(event + ": " + value + "\n");
        });
    }

    private void runProbe() {
        if (attempted) return; attempted = true;
        try {
            File apk = new File(root, "original.apk");
            if (!apk.isFile() || !apk.setReadOnly()) throw new IllegalStateException("Imported DEX archive must be read-only");
            File optimized = new File(root, "optimized");
            if (!optimized.isDirectory() && !optimized.mkdir()) throw new IllegalStateException("Cannot create optimized directory");
            File bridge = prepareCallbackBridge();
            original = new DexClassLoader(apk.getAbsolutePath() + File.pathSeparator + bridge.getAbsolutePath(), optimized.getAbsolutePath(),
                    new File(root, "native").getAbsolutePath(), getClassLoader());
            Thread.currentThread().setContextClassLoader(original);
            bindHostApplicationContext();
            initializeOriginalLibraryHost(apk);
            String name = engine == 0 ? "com.ss.videoarch.liveplayer2.VeLivePlayer" : "com.ss.videoarch.liveplayer.VideoLiveManager";
            // This is the original DEX, not reconstructed Java or a public SDK.
            record("before_class_resolution", name);
            engineClass = Class.forName(name, false, original);
            record("class_resolved", engineClass.getName());
            record("before_original_static_initialization", name);
            Class.forName(name, true, original);
            if (engine == 0) {
                Object loaded = callStatic("loadSystemLivePlayer", new Class<?>[0]);
                record("original_native_loader_result", loaded);
                if (!Boolean.TRUE.equals(loaded)) throw new IllegalStateException("Original native loader returned false");
                record("original_native_version", callStatic("getVersion", new Class<?>[0]));
                Class<?> logLevel = Class.forName("com.ss.videoarch.liveplayer2.VeLivePlayerLogLevel", true, original);
                engineClass.getMethod("setLogLevel", logLevel).invoke(null, logLevel.getField("VeLivePlayerLogLevelDebug").get(null));
                record("native_log_level", "Debug diagnostic mode; do not use verbose logging for performance benchmarks");
            } else {
                Class.forName("com.ss.ttm.player.TTPlayer", true, original);
                record("original_ttm_class_initialized", true);
            }
            record("load_stage_complete", "Original classes initialized; this does not prove player creation or decoding");
            if ("load".equals(mode)) return;
            Context engineContext = makeEngineContext(apk);
            record("before_original_constructor", name);
            player = engineClass.getConstructor(Context.class).newInstance(engineContext);
            if (engine == 0) {
                int id = ((Number) engineClass.getMethod("getPlayerId").invoke(player)).intValue();
                record("original_player_id", id);
                if (id == 0) throw new IllegalStateException("Original native player ID is zero");
            }
            record("initialization_stage_complete", name);
            attachObserver();
            if (!"play".equals(mode)) return;
            if (!surface.getHolder().getSurface().isValid()) throw new IllegalStateException("Surface is not ready");
            String replayPath = getIntent().getStringExtra("replay");
            if (engine == 0 && replayPath == null) {
                Class<?> cfgClass = Class.forName("com.ss.videoarch.liveplayer2.VeLivePlayerConfiguration", true, original);
                Object config = cfgClass.getConstructor().newInstance();
                cfgClass.getField("enableStatisticsCallback").setBoolean(config, true);
                cfgClass.getField("statisticsCallbackInterval").setInt(config, 1);
                boolean hardware = getIntent().getBooleanExtra("hardware", true);
                cfgClass.getField("enableHardwareDecode").setBoolean(config, hardware);
                record("instrumentation_config", "Statistics at 1 second; enableHardwareDecode=" + hardware
                        + "; hardware enabled is not hardware-only; actual decode path must come from statistics; online settings unavailable");
                engineClass.getMethod("setConfig", cfgClass).invoke(player, config);
                engineClass.getMethod("setSurface", Surface.class, boolean.class).invoke(player, surface.getHolder().getSurface(), false);
            } else if (engine != 0) {
                engineClass.getMethod("setSurface", Surface.class).invoke(player, surface.getHolder().getSurface());
            }
            if (replayPath != null) {
                if (engine != 0) throw new IllegalArgumentException("Replay requires the native live engine");
                JSONObject imported = new JSONObject(MainActivity.read(new File(root, "import.json")));
                ReplaySession replay = new ReplaySession(new File(replayPath));
                replay.apply(player, engineClass, original, String.valueOf(callStatic("getVersion", new Class<?>[0])), imported.getLong("version_code"), this::record);
                engineClass.getMethod("setSurface", Surface.class, boolean.class).invoke(player, surface.getHolder().getSurface(), false);
                engineClass.getMethod("play").invoke(player);
                record("replay_play_invoked", "Await actual first-frame/statistics callbacks");
                main.postDelayed(this::snapshotNativeState, 5000);
                return;
            }
            String url = getIntent().getStringExtra("url");
            String syntheticCodec = getIntent().getStringExtra("smoke");
            if (syntheticCodec != null) {
                synthetic = new SyntheticLiveSource(this, syntheticCodec);
                url = synthetic.url();
                record("synthetic_source", "Phone-local paced HTTP-FLV, codec=" + syntheticCodec + "; generated pattern and tone; not a business-network benchmark");
            }
            record("before_set_url", "User-supplied URL; value omitted from probe event");
            try { engineClass.getMethod("setPlayUrl", String.class).invoke(player, url); }
            catch (NoSuchMethodException missing) {
                Class<?> liveUrl = Class.forName("com.ss.videoarch.liveplayer.model.LiveURL", true, original);
                Object entry = liveUrl.getConstructor(String.class, String.class, String.class).newInstance(url, null, "{}");
                Object entries = Array.newInstance(liveUrl, 1); Array.set(entries, 0, entry);
                engineClass.getMethod("setPlayURLs", entries.getClass()).invoke(player, entries);
                record("url_configuration", "Original LiveURL with empty sdkParams; online configuration not reconstructed");
            }
            record("before_play", name);
            engineClass.getMethod("play").invoke(player);
            record("play_invoked", "Await original first-frame/error/stall callbacks; invocation alone is not success");
            if (engine == 0) main.postDelayed(this::snapshotNativeState, 5000);
        } catch (Throwable error) {
            while (error instanceof InvocationTargetException && ((InvocationTargetException) error).getCause() != null)
                error = ((InvocationTargetException) error).getCause();
            record("probe_failure", android.util.Log.getStackTraceString(error));
        }
    }

    private Object callStatic(String name, Class<?>[] types, Object... args) throws Exception {
        return engineClass.getMethod(name, types).invoke(null, args);
    }

    private void snapshotNativeState() {
        if (player == null || engine != 0) return;
        try {
            JSONObject values = new JSONObject();
            for (String key : new String[]{"VeLivePlayerKeyGetStreamLog", "VeLivePlayerKeyGetStreamBaseInfo"}) {
                Object value = engineClass.getMethod("getProperty", String.class, Object.class).invoke(player, key, "");
                values.put(key, value == null ? JSONObject.NULL : value.toString());
            }
            // Raw addresses remain in private diagnostics rather than the visible UI summary.
            ProbeLog.write(this, "native_state_snapshot", values);
            record("native_state_snapshot_saved", "Original stream log/base information retained in local JSONL");
        } catch (Exception error) { record("native_state_snapshot_failure", error.toString()); }
    }

    private File prepareCallbackBridge() throws java.io.IOException {
        File bridge = new File(getCodeCacheDir(), "observer-bridge.dex");
        if (bridge.exists() && !bridge.delete()) throw new java.io.IOException("Cannot replace callback bridge");
        try (java.io.InputStream input = getAssets().open("classes.dex");
             java.io.FileOutputStream output = new java.io.FileOutputStream(bridge)) {
            if (!bridge.setReadOnly()) throw new java.io.IOException("Cannot protect callback DEX");
            MainActivity.copy(input, output, null);
        }
        return bridge;
    }

    private void bindHostApplicationContext() throws Exception {
        Class<?> contexts = Class.forName("com.bytedance.ies.ugc.appcontext.AppContextManager", true, original);
        Field context = contexts.getDeclaredField("context");
        if (!Modifier.isStatic(context.getModifiers()) || !android.app.Application.class.isAssignableFrom(context.getType()))
            throw new IllegalStateException("Unsupported original host context contract");
        context.setAccessible(true);
        if (context.get(null) == null) context.set(null, getApplication());
        record("host_context_binding", "Original AppContextManager receives the probe Application; package identity and signatures unchanged");
    }

    private void initializeOriginalLibraryHost(File apk) throws Exception {
        JSONObject imported = new JSONObject(MainActivity.read(new File(root, "import.json")));
        long version = imported.getLong("version_code");
        if (version != 400701) {
            record("library_host_adapter", "No version-specific Librarian initialization adapter for " + version);
            return;
        }
        Class<?> contexts = Class.forName("com.bytedance.ies.ugc.appcontext.AppContextManager", true, original);
        android.content.pm.PackageInfo own = getPackageManager().getPackageInfo(getPackageName(), 0);
        Field businessVersion = contexts.getDeclaredField("LJ");
        businessVersion.set(null, businessVersion.getType().getConstructor(long.class, String.class, long.class)
                .newInstance((long)own.versionCode, own.versionName, (long)own.versionCode));
        Field packageVersion = contexts.getDeclaredField("LIZLLL");
        packageVersion.set(null, packageVersion.getType().getConstructor(long.class, String.class)
                .newInstance((long)own.versionCode, own.versionName));
        contexts.getDeclaredField("LIZJ").set(null, "OriginalPlayerProbe");
        // This holder's constructor only stores the Application argument. The original
        // constructor is host-subclass typed, while its stored field is Application typed.
        // Bind our Application; never construct or start the original host Application.
        Class<?> holder = Class.forName("com.bytedance.ies.ugc.appcontext.ApplicationHolder", true, original);
        Field binding = holder.getDeclaredField("LIZ");
        if (binding.get(null) == null) {
            Class<?> state = originalClass("X.0ksE");
            Constructor<?> constructor = state.getConstructor(Class.forName("com.ss.android.ugc.aweme.app.host.AwemeHostApplication", false, original));
            Object container = constructor.newInstance(new Object[]{null});
            Field app = state.getDeclaredField("LIZ");
            if (app.getType() != android.app.Application.class) throw new IllegalStateException("Application holder contract changed");
            app.setAccessible(true); app.set(container, getApplication()); binding.set(null, container);
            record("original_application_holder_initialized", "Probe Application bound to original holder; original Application not started");
        }
        Class<?> kevaBuilder = originalClass("X.1QXM");
        Object builder = kevaBuilder.getDeclaredField("LJIIZILJ").get(null);
        Field kevaContext = kevaBuilder.getDeclaredField("LIZ");
        if (kevaContext.getType() != Context.class || builder == null) throw new IllegalStateException("Keva context contract changed");
        kevaContext.set(builder, getApplication());
        record("original_preferences_context_initialized", "Original preferences use probe-private storage");
        // The 40.7.0 loader requires its normal init(Context, version, logger) before loadLibrary.
        // Supply the probe context and original APK paths; do not replace or patch library bytes.
        Class<?> libraryHost = originalClass("X.0lGF");
        for (Method method : libraryHost.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers()) && types.length == 3 && types[0] == Context.class
                    && types[1] == String.class && !types[2].isPrimitive() && types[2] != String.class) {
                method.setAccessible(true);
                method.invoke(null, makeEngineContext(apk), "probe-" + version, null);
                record("original_library_host_initialized", libraryHost.getName() + "." + method.getName());
                return;
            }
        }
        throw new NoSuchMethodException("Original Librarian initialization contract not found");
    }

    private Context makeEngineContext(File apk) throws Exception {
        ApplicationInfo info = new ApplicationInfo(getApplicationInfo());
        info.sourceDir = apk.getAbsolutePath(); info.publicSourceDir = apk.getAbsolutePath();
        info.nativeLibraryDir = new File(root, "native").getAbsolutePath();
        Resources resources = getPackageManager().getResourcesForApplication(info);
        // Resource and classloader adaptation only; identity, permissions and data remain the probe app's.
        return new ContextWrapper(this) {
            @Override public ClassLoader getClassLoader() { return original; }
            @Override public Resources getResources() { return resources; }
            @Override public AssetManager getAssets() { return resources.getAssets(); }
            @Override public ApplicationInfo getApplicationInfo() { return info; }
            @Override public Context getApplicationContext() { return this; }
        };
    }

    private Class<?> originalClass(String rawDexName) throws ClassNotFoundException {
        // Raw DEX names may be invalid Java source identifiers. Keep their resolution
        // separate from member lookup so Java PSI-based lint does not parse them as source.
        return Class.forName(rawDexName, true, original);
    }

    private void attachObserver() throws Exception {
        String ifaceName = engine == 0 ? "com.ss.videoarch.liveplayer2.VeLivePlayerObserver" : "com.ss.videoarch.liveplayer.ILiveListener";
        Class<?> iface = Class.forName(ifaceName, true, original);
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return args != null && proxy == args[0];
                return "OriginalPlayerProbeObserver";
            }
            JSONArray values = new JSONArray();
            if (args != null) for (Object arg : args) if (arg != player) values.put(snapshot(arg));
            record("original_callback." + method.getName(), values);
            if (method.getReturnType() == void.class) return null;
            // Delegate return-valued callbacks to the original DEX default implementation.
            Class<?>[] signature = new Class<?>[method.getParameterTypes().length + 1]; signature[0] = iface;
            System.arraycopy(method.getParameterTypes(), 0, signature, 1, signature.length - 1);
            Object[] arguments = new Object[signature.length]; arguments[0] = proxy;
            if (args != null) System.arraycopy(args, 0, arguments, 1, args.length);
            for (String suffix : new String[]{"$-CC", "$CC"}) {
                try {
                    Class<?> companion = Class.forName(ifaceName + suffix, true, original);
                    return companion.getMethod("$default$" + method.getName(), signature).invoke(null, arguments);
                } catch (ClassNotFoundException | NoSuchMethodException absent) { /* Try raw DEX companion name. */ }
            }
            throw new UnsupportedOperationException("No original default for callback " + method.getName());
        };
        if (engine == 0) {
            // Do not use java.lang.reflect.Proxy for JNI callbacks: Android 17 CheckJNI
            // can observe Proxy's erased Object return instead of the interface's void.
            Class<?> bridgeClass = Class.forName("local.originalplayer.bridge.ConcreteObserver", true, original);
            for (Method callback : iface.getMethods()) {
                if (Modifier.isStatic(callback.getModifiers())) continue;
                Method concrete = bridgeClass.getDeclaredMethod(callback.getName(), callback.getParameterTypes());
                if (concrete.getReturnType() != callback.getReturnType()) throw new IllegalStateException("Observer return contract changed: " + callback.getName());
            }
            observer = bridgeClass.getConstructor(InvocationHandler.class).newInstance(handler);
            if (!iface.isInstance(observer)) throw new IllegalStateException("Callback bridge interface mismatch");
            record("observer_bridge", "Concrete JNI callback methods; original interface resolved from imported APK");
        } else observer = Proxy.newProxyInstance(original, new Class<?>[]{iface}, handler);
        if (engine == 0) engineClass.getMethod("setObserver", iface).invoke(player, observer);
        else {
            Field field = null;
            for (Field f : engineClass.getFields()) if (f.getType() == iface) { field = f; break; }
            if (field == null) throw new NoSuchFieldException("Original Java listener field unavailable");
            field.set(player, observer);
            record("java_listener_attachment", "Attached original interface field; log-service constructor may have retained its earlier listener");
        }
    }

    private Object snapshot(Object value) {
        if (value == null) return JSONObject.NULL;
        if (value instanceof Number || value instanceof Boolean || value instanceof String) return value;
        if (value.getClass().isEnum()) return value.toString();
        JSONObject out = new JSONObject();
        try {
            out.put("class", value.getClass().getName());
            for (Field f : value.getClass().getFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Object v = f.get(value);
                if (v == null || v instanceof Number || v instanceof Boolean || v instanceof String || v.getClass().isEnum())
                    out.put(f.getName(), v == null ? JSONObject.NULL : v.getClass().isEnum() ? v.toString() : v);
            }
        } catch (Exception error) { /* Preserve partial metadata. */ }
        return out;
    }

    private void shutdown() {
        if (synthetic != null) { synthetic.close(); synthetic = null; }
        if (player == null) return;
        try { engineClass.getMethod("stop").invoke(player); }
        catch (Throwable error) { record("stop_failure", error.toString()); }
        if (engine == 0) {
            try { engineClass.getMethod("destroy").invoke(player); }
            catch (Throwable error) { record("destroy_failure", error.toString()); }
        }
        player = null; observer = null;
    }

    @Override public void surfaceCreated(SurfaceHolder holder) { if ("play".equals(mode)) main.post(this::runProbe); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { shutdown(); }
    @Override protected void onDestroy() {
        shutdown(); super.onDestroy();
        // A fresh process is essential: Android native libraries belong to their loading classloader.
        if (isFinishing()) android.os.Process.killProcess(android.os.Process.myPid());
    }
}
