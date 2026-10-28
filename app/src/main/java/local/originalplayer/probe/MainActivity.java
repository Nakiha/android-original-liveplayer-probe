package local.originalplayer.probe;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

public final class MainActivity extends Activity {
    private static final int PICK_APK = 1, EXPORT_LOG = 2;
    private TextView status;
    private EditText url;
    private Spinner engine;
    private Spinner decoding;
    private String imported;
    private Button importer;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        imported = getPreferences(0).getString("imported", "");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL); box.setPadding(24, 24, 24, 24);
        TextView title = new TextView(this); title.setText("原包播放器探针 · 研究版"); title.setTextSize(23); box.addView(title);
        TextView note = new TextView(this);
        note.setText("直接加载你导入的抖音 APK 中的原始 DEX 和 ARM64 so。未替换解码器；未初始化抖音 Application。\n加载、初始化、播放是不同验证阶段。成功加载不代表已复现线上配置。");
        box.addView(note);
        button(box, "导入本机已安装的抖音（无需电脑）", v -> {
            for (String name : new String[]{"com.ss.android.ugc.aweme", "com.ss.android.ugc.aweme.mobile"}) {
                try {
                    android.content.pm.ApplicationInfo installed = getPackageManager().getApplicationInfo(name, 0);
                    if (installed.splitSourceDirs != null && installed.splitSourceDirs.length > 0) {
                        status.setText("检测到分包安装，本版探针需要独立完整 APK。请通过文件选择导入官方完整 APK。"); return;
                    }
                    beginImport(android.net.Uri.fromFile(new File(installed.sourceDir))); return;
                } catch (android.content.pm.PackageManager.NameNotFoundException absent) { /* Try the next known variant. */ }
            }
            status.setText("未检测到标准版抖音。也可通过下面的文件入口导入官方 APK。");
        });
        importer = button(box, "1. 导入官方抖音 APK", v -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(pick, PICK_APK);
        });
        engine = new Spinner(this);
        engine.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"原包 Native 直播内核（liveplayer2）", "原包 Java 直播内核（VideoLiveManager）"}));
        box.addView(engine);
        decoding = new Spinner(this);
        decoding.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"允许硬解（原内核可能回退软解）", "仅软解（新直播内核实验选项）"}));
        box.addView(decoding);
        url = new EditText(this); url.setSingleLine(true); url.setHint("你拥有的 HTTP-FLV 测试 URL");
        url.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(url);
        button(box, "2. 验证原类和 native 加载", v -> startProbe("load"));
        button(box, "3. 初始化原播放器", v -> startProbe("init"));
        button(box, "4. 用原播放器尝试播放 URL", v -> startProbe("play"));
        button(box, "查看本次探针日志", v -> {
            try { status.setText(read(new File(getFilesDir(), "probe.jsonl"))); }
            catch (Exception error) { status.setText(error.toString()); }
        });
        button(box, "导出探针日志", v -> startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, "original-player-probe.jsonl"), EXPORT_LOG));
        status = new TextView(this); status.setTextIsSelectable(true);
        status.setText("导入目录：" + imported + "\n设备 ABI：" + Arrays.toString(Build.SUPPORTED_ABIS));
        box.addView(status);
        ScrollView scroll = new ScrollView(this); scroll.addView(box); setContentView(scroll);
    }

    private Button button(LinearLayout box, String text, View.OnClickListener click) {
        Button b = new Button(this); b.setText(text); b.setOnClickListener(click); box.addView(b); return b;
    }

    private void startProbe(String mode) {
        if (imported.isEmpty()) { status.setText("请先导入 APK。"); return; }
        if (!Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a")) {
            status.setText("此探针需要 ARM64 Android 真机，当前 ABI 不支持。"); return;
        }
        String address = url.getText().toString().trim();
        if (mode.equals("play") && !(address.startsWith("http://") || address.startsWith("https://"))) {
            status.setText("请输入你的 HTTP/HTTPS 直播测试 URL。"); return;
        }
        startActivity(new Intent(this, ProbeActivity.class).putExtra("root", imported).putExtra("mode", mode)
                .putExtra("engine", engine.getSelectedItemPosition()).putExtra("hardware", decoding.getSelectedItemPosition() == 0)
                .putExtra("url", address));
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == EXPORT_LOG) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData());
                 InputStream in = new FileInputStream(new File(getFilesDir(), "probe.jsonl"))) {
                copy(in, out, null); status.setText("日志已导出。原生崩溃仍需 ADB logcat。");
            } catch (Exception error) { status.setText(error.toString()); }
            return;
        }
        if (request != PICK_APK) return;
        beginImport(data.getData());
    }

    private void beginImport(final android.net.Uri uri) {
        importer.setEnabled(false); status.setText("正在复制并核验 APK，随后提取原始 ARM64 库……");
        new Thread(() -> {
            try {
                File root = new File(getFilesDir(), "imports/" + System.currentTimeMillis());
                if (!root.mkdirs()) throw new IOException("Cannot create import directory");
                File apk = new File(root, "original.apk");
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(apk)) {
                    if (in == null) throw new IOException("Cannot open APK");
                    copy(in, out, digest);
                }
                PackageInfo info = getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
                if (info == null || !info.packageName.startsWith("com.ss.android.ugc.aweme"))
                    throw new IOException("Selected file is not a recognized Douyin APK");
                File nativeDir = new File(root, "native");
                if (!nativeDir.mkdir()) throw new IOException("Cannot create native directory");
                JSONObject hashes = new JSONObject(); int count = 0;
                try (ZipFile archive = new ZipFile(apk)) {
                    Enumeration<? extends ZipEntry> entries = archive.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement(); String name = entry.getName();
                        if (!name.startsWith("lib/arm64-v8a/") || !name.endsWith(".so")) continue;
                        String base = name.substring("lib/arm64-v8a/".length());
                        if (base.contains("/") || base.contains("\\") || base.equals("..")) continue;
                        File target = new File(nativeDir, base);
                        MessageDigest soHash = MessageDigest.getInstance("SHA-256");
                        try (InputStream in = archive.getInputStream(entry); OutputStream out = new FileOutputStream(target)) { copy(in, out, soHash); }
                        hashes.put(base, hex(soHash.digest())); count++;
                    }
                }
                if (count == 0) throw new IOException("APK contains no ARM64 libraries; split APK may be required");
                // Android 14 requires dynamically loaded DEX archives to be read-only.
                if (!apk.setReadOnly()) throw new IOException("Cannot make DEX archive read-only");
                JSONObject report = new JSONObject();
                report.put("package", info.packageName).put("version", info.versionName)
                        .put("version_code", Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode)
                        .put("apk_sha256", hex(digest.digest())).put("native_sha256", hashes)
                        .put("scope", "Original APK imported; runtime success not yet verified");
                try (FileOutputStream out = new FileOutputStream(new File(root, "import.json"))) {
                    out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
                }
                imported = root.getAbsolutePath();
                getPreferences(0).edit().putString("imported", imported).apply();
                ProbeLog.write(this, "import", report);
                runOnUiThread(() -> status.setText("导入完成：" + info.packageName + " " + info.versionName + "\n" + imported));
            } catch (Throwable error) {
                ProbeLog.write(this, "import_failure", android.util.Log.getStackTraceString(error));
                runOnUiThread(() -> status.setText(error.toString()));
            } finally { runOnUiThread(() -> importer.setEnabled(true)); }
        }, "apk-import").start();
    }

    static void copy(InputStream in, OutputStream out, MessageDigest digest) throws IOException {
        byte[] buffer = new byte[256 * 1024]; int size;
        while ((size = in.read(buffer)) != -1) { out.write(buffer, 0, size); if (digest != null) digest.update(buffer, 0, size); }
    }
    static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format(Locale.US, "%02x", b & 255)); return out.toString();
    }
    static String read(File file) throws IOException {
        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            copy(in, out, null); return out.toString("UTF-8");
        }
    }
}
