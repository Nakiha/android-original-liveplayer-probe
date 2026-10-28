package local.originalplayer.probe;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/** Reapply locally observed, version-bound player inputs. Never execute arbitrary method names. */
final class ReplaySession {
    interface Reporter { void record(String event, Object value); }
    private final JSONObject document;

    ReplaySession(File file) throws Exception {
        if (file.length() > 4 * 1024 * 1024) throw new IllegalArgumentException("Replay file exceeds 4 MiB");
        document = new JSONObject(MainActivity.read(file));
        if (document.getInt("schema_version") != 1) throw new IllegalArgumentException("Unsupported replay schema");
    }

    void apply(Object player, Class<?> engine, ClassLoader original, String version, long sourceVersion, Reporter reporter) throws Exception {
        if (!document.getString("native_version").equals(version)
                || document.getLong("source_version_code") != sourceVersion)
            throw new IllegalArgumentException("Replay requires the exact captured source/native versions");
        int id = ((Number)engine.getMethod("getPlayerId").invoke(player)).intValue();
        JSONArray calls = document.getJSONArray("native_calls");
        boolean hasStream = false;
        int applied = 0;
        for (int i = 0; i < calls.length(); i++) {
            JSONObject call = calls.getJSONObject(i);
            String name = call.getString("method");
            JSONArray args = call.getJSONArray("args");
            if (name.equals("cpp_play")) break;
            switch (name) {
                case "cpp_setObserver":
                    reporter.record("replay_observer_scope", "Probe diagnostic observer retained instead of the source app observer");
                    continue;
                case "cpp_setSettingsBundle":
                    JSONArray lookups = document.optJSONArray("settings_results");
                    if (lookups == null || lookups.length() == 0) {
                        reporter.record("replay_settings_gap", "Source provider instance cannot cross processes; resolved settings lookup results have not been captured");
                    } else {
                        Class<?> iface = Class.forName("com.ss.videoarch.liveplayer2.ILiveSettingBundle", true, original);
                        Map<String, Object> values = new HashMap<>();
                        for (int j = 0; j < lookups.length(); j++) {
                            JSONObject item = lookups.getJSONObject(j);
                            Object fallback = item.opt("default_value");
                            String key = item.getString("key") + "|" + encodedSignature(fallback);
                            Object value = decodeValue(item.opt("result"));
                            if (values.containsKey(key) && !java.util.Objects.equals(values.get(key), value))
                                throw new IllegalArgumentException("Captured settings changed during observation: " + item.getString("key"));
                            values.put(key, value);
                        }
                        Object provider = Proxy.newProxyInstance(original, new Class<?>[]{iface}, (proxy, method, arguments) -> {
                            if (method.getDeclaringClass() == Object.class) {
                                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                                if (method.getName().equals("equals")) return proxy == arguments[0];
                                return "ObservedSettingsReplay";
                            }
                            if (!method.getName().equals("getSettingsValueForKey") || arguments.length != 2)
                                throw new UnsupportedOperationException("Unobserved settings interface method");
                            String key = arguments[0] + "|" + signature(arguments[1]);
                            if (values.containsKey(key)) return values.get(key);
                            reporter.record("replay_settings_default_fallback", String.valueOf(arguments[0]));
                            return arguments[1];
                        });
                        invoke(player, engine, name, new Class<?>[]{int.class, iface}, id, provider);
                        reporter.record("replay_observed_settings_loaded", values.size());
                    }
                    continue;
                case "cpp_setConfig": {
                    Class<?> configClass = Class.forName("com.ss.videoarch.liveplayer2.VeLivePlayerConfiguration", true, original);
                    Object config = configClass.getConstructor().newInstance();
                    JSONObject fields = args.getJSONObject(1);
                    java.util.Iterator<String> keys = fields.keys();
                    while (keys.hasNext()) {
                        String key = keys.next(); Field field = configClass.getField(key);
                        if (Modifier.isStatic(field.getModifiers())) throw new IllegalArgumentException("Static config input rejected");
                        Class<?> type = field.getType();
                        if (type == boolean.class) field.setBoolean(config, fields.getBoolean(key));
                        else if (type == int.class) field.setInt(config, fields.getInt(key));
                        else if (type == long.class) field.setLong(config, Long.parseLong(fields.get(key).toString()));
                        else if (type == String.class) field.set(config, fields.isNull(key) ? null : fields.getString(key));
                        else throw new IllegalArgumentException("Unsupported captured configuration field " + key);
                    }
                    // This is an explicit measurement change, never presented as exact replay.
                    configClass.getField("enableStatisticsCallback").setBoolean(config, true);
                    configClass.getField("statisticsCallbackInterval").setInt(config, 1);
                    invoke(player, engine, name, new Class<?>[]{int.class, configClass}, id, config);
                    reporter.record("replay_statistics_override", "Captured config restored; only statistics callback enabled at 1 second for diagnosis");
                    break;
                }
                case "cpp_setStreamDataInfo":
                case "cpp_setPlayUrl":
                    String input = args.getString(1);
                    reporter.record("replay_stream_input_sha256", sha256(input));
                    invoke(player, engine, name, new Class<?>[]{int.class, String.class}, id, input);
                    hasStream = true;
                    break;
                case "cpp_setIntPropertyId":
                    invoke(player, engine, name, new Class<?>[]{int.class, int.class, int.class}, id, args.getInt(1), args.getInt(2)); break;
                case "cpp_setStringPropertyId":
                    invoke(player, engine, name, new Class<?>[]{int.class, int.class, String.class}, id, args.getInt(1), args.getString(2)); break;
                case "cpp_setIntProperty":
                    invoke(player, engine, name, new Class<?>[]{int.class, String.class, int.class}, id, args.getString(1), args.getInt(2)); break;
                case "cpp_setStringProperty":
                    invoke(player, engine, name, new Class<?>[]{int.class, String.class, String.class}, id, args.getString(1), args.getString(2)); break;
                case "cpp_stop":
                    invoke(player, engine, name, new Class<?>[]{int.class}, id); break;
                default: throw new IllegalArgumentException("Replay method is outside the supported allowlist: " + name);
            }
            applied++;
            reporter.record("replay_call_applied", name);
        }
        if (!hasStream) throw new IllegalArgumentException("No captured stream input before first play");
        if (document.has("selected_resolution")) {
            String label = document.getString("selected_resolution");
            JSONObject streamInfo = null;
            for (int i = 0; i < calls.length(); i++) {
                JSONObject call = calls.getJSONObject(i);
                if (call.getString("method").equals("cpp_play")) break;
                if (call.getString("method").equals("cpp_setStreamDataInfo"))
                    streamInfo = new JSONObject(call.getJSONArray("args").getString(1));
            }
            if (streamInfo == null || !streamInfo.getJSONObject("data").has(label))
                throw new IllegalArgumentException("Observed rendition is absent from the captured stream data");
            Class<?> resolution = Class.forName("com.ss.videoarch.liveplayer2.VeLivePlayerResolution", true, original);
            Object selected = resolution.getConstructor(String.class).newInstance(label);
            engine.getMethod("setStartPlayResolution", resolution).invoke(player, selected);
            reporter.record("replay_rendition_pin", label + "; derived from the source's actual URL, not a reproduction of its ABR selection policy");
        }
        if (document.has("selected_stream_url")) reporter.record("replay_expected_url_sha256", sha256(document.getString("selected_stream_url")));
        reporter.record("replay_inputs_applied", applied);
        reporter.record("replay_equivalence_scope", "Observed stream/config/property inputs only; historical globals, business callbacks, and uncaptured settings are not reproduced");
    }

    private static void invoke(Object player, Class<?> engine, String name, Class<?>[] signature, Object... arguments) throws Exception {
        Method method = engine.getDeclaredMethod(name, signature); method.setAccessible(true); method.invoke(player, arguments);
    }

    private static String signature(Object value) { return value == null ? "null" : value.getClass().getName() + ":" + value; }
    private static String encodedSignature(Object value) throws Exception {
        if (value == null || value == JSONObject.NULL) return "null";
        if (value instanceof String) return "java.lang.String:" + value;
        JSONObject encoded = (JSONObject)value;
        return encoded.getString("java_type") + ":" + encoded.getString("value");
    }
    private static Object decodeValue(Object value) throws Exception {
        if (value == null || value == JSONObject.NULL) return null;
        if (value instanceof String || value instanceof Boolean || value instanceof Number) return value;
        JSONObject encoded = (JSONObject)value;
        String type = encoded.getString("java_type"), text = encoded.getString("value");
        switch (type) {
            case "java.lang.String": return text;
            case "java.lang.Boolean": return Boolean.valueOf(text);
            case "java.lang.Integer": return Integer.valueOf(text);
            case "java.lang.Long": return Long.valueOf(text);
            case "java.lang.Float": return Float.valueOf(text);
            case "java.lang.Double": return Double.valueOf(text);
            case "org.json.JSONObject": return new JSONObject(text);
            case "org.json.JSONArray": return new JSONArray(text);
            default: throw new IllegalArgumentException("Captured setting result type cannot be replayed: " + type);
        }
    }
    private static String sha256(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(); for (byte b : hash) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255)); return out.toString();
    }
}
