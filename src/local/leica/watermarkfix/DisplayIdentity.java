package local.leica.watermarkfix;

import android.os.Build;
import android.util.Log;
import de.robv.android.xposed.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Capture display identity before the donor compatibility identity is installed. */
public final class DisplayIdentity {
    private static String market, model, make, brand, name;
    private static int reports;
    private static boolean installed;
    public static void installWhenReady(final ClassLoader loader) {
        Object application = XposedHelpers.callStaticMethod(
            XposedHelpers.findClass("android.app.ActivityThread", null), "currentApplication");
        if (application != null) installOnce(loader);
        else XposedHelpers.findAndHookMethod(android.app.Application.class, "attach",
            android.content.Context.class, new XC_MethodHook(XC_MethodHook.PRIORITY_HIGHEST) {
                @Override protected void afterHookedMethod(MethodHookParam p) { installOnce(loader); }
            });
    }
    private static synchronized void installOnce(ClassLoader loader) {
        if (installed) return;
        installed = true;
        // Run after the base adapter's package callbacks; its hardware identity stays intact.
        install(loader, "", Build.MODEL);
    }
    private static void note(String text) { Log.i("LeicaDisplayIdentity", text); }
    private static String originalProperty(String key, String fallback) {
        try {
            java.lang.reflect.Method method = Class.forName("android.os.SystemProperties")
                .getDeclaredMethod("get", String.class, String.class);
            String value = (String)XposedBridge.invokeOriginalMethod(method, null, new Object[]{key, fallback});
            return value == null || value.trim().isEmpty() ? fallback : value.trim();
        } catch (Throwable unavailable) {
            note("ORIGINAL_PROPERTY_UNAVAILABLE " + key + " " + unavailable.getClass().getSimpleName());
            return fallback;
        }
    }
    public static String market() { return market; }
    public static String model() { return model; }
    public static void install(ClassLoader loader, String capturedMarket, String capturedModel) {
        // HyperChanger's Leica UI can already have replaced Build and hooked get().
        // Invoke the original Java accessor: Zygisk's native per-process spoof stays
        // visible, while unrelated Leica UI property hooks do not replace the name.
        model = originalProperty("ro.product.model", capturedModel);
        make = originalProperty("ro.product.manufacturer", Build.MANUFACTURER);
        market = originalProperty("ro.product.marketname", capturedMarket == null ? "" : capturedMarket);
        if (market.isEmpty()) market = model;
        String[] parts = market.split("\\s+", 2);
        brand = parts[0].toUpperCase(Locale.ROOT);
        name = parts.length > 1 ? parts[1] : market;
        // Keep Je.c's hardware fields intact; only the marketing-name field changes.
        try { XposedHelpers.setStaticObjectField(XposedHelpers.findClass("Je.c", loader), "h", market); }
        catch (Throwable e) { note("MARKET_FIELD_FAILED " + e); }
        try {
            XposedHelpers.findAndHookMethod("Je.b", loader, "v", new XC_MethodHook(XC_MethodHook.PRIORITY_HIGHEST) {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable()) return;
                    String[] old = (String[])p.getResult();
                    String[] result = old == null ? new String[3] : Arrays.copyOf(old, Math.max(3, old.length));
                    result[0] = brand; result[1] = name;
                    p.setResult(result);
                }
            });
            XposedHelpers.findAndHookMethod("Ku.a", loader, "a", new XC_MethodHook(XC_MethodHook.PRIORITY_HIGHEST) {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!p.hasThrowable()) p.setResult(XposedHelpers.newInstance(
                        XposedHelpers.findClass("Pu.j", loader), brand, name));
                }
            });
            // Changes only display EXIF fields as the camera writes them. Depth/XMP,
            // XiaomiProduct, algorithm inputs and all image bytes stay native.
            XposedHelpers.findAndHookMethod("rf.b", loader, "R", String.class, String.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if ("Model".equals(p.args[0])) p.args[1] = market;
                        else if ("Make".equals(p.args[0])) p.args[1] = make;
                    }
                });
            java.lang.reflect.Method serializer = XposedHelpers.findClass("fs.g", loader).getDeclaredMethod("b",
                JSONArray.class, XposedHelpers.findClass("cs.e", loader),
                XposedHelpers.findClass("Gg.a0", loader), Path.class);
            XposedBridge.hookMethod(serializer, new XC_MethodHook(XC_MethodHook.PRIORITY_HIGHEST) {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!p.hasThrowable()) try { exportLogo(p, loader); }
                    catch (Throwable e) { if (reports++ < 8) note("LOGO_EXPORT_FAILED " + e); }
                }
            });
            note("READY market=" + market + " model=" + model + " make=" + make);
        } catch (Throwable e) { note("INSTALL_FAILED " + e); }
    }
    public static String property(String key) {
        // Device and model compatibility remain the donor's; display name is separate.
        return "ro.product.marketname".equals(key) ? market : null;
    }
    private static void exportLogo(XC_MethodHook.MethodHookParam p, ClassLoader loader) throws Exception {
        if (!"logo".equalsIgnoreCase(String.valueOf(XposedHelpers.callMethod(p.thisObject, "i")))) return;
        JSONArray rows = (JSONArray)p.args[0];
        if (rows.length() == 0) return;
        JSONObject row = rows.getJSONObject(rows.length() - 1);
        if (!"logo".equals(row.optString("id"))) return;
        Map<?,?> binding = (Map<?,?>)XposedHelpers.callStaticMethod(
            XposedHelpers.findClass("Gg.s", loader), "j", row.optString("path"));
        if (!"fill".equals(binding.get("pathType")) || !"${logo}".equals(binding.get("path"))) return;
        // These are the two image-bearing identities shipped by this verified APK.
        if (!"17 Ultra by Leica".equalsIgnoreCase(name) &&
            !"leitzphone powered by xiaomi".equalsIgnoreCase(name)) return;
        Path dir = ((Path)p.args[3]).toAbsolutePath().normalize();
        String relative = dir.toString().replaceFirst("^/data/(?:user/0|data)/com\\.android\\.camera/files/watermarks/", "");
        if (relative.equals(dir.toString()) || !relative.matches("[A-Za-z0-9_-]+/[0-9]+")) return;
        JSONObject config = new JSONObject(new String(Files.readAllBytes(dir.resolve("config.json")), StandardCharsets.UTF_8));
        JSONObject metadata = config.getJSONObject("metadata");
        boolean shadow = "in".equalsIgnoreCase(metadata.optString("position"));
        String theme = (String)XposedHelpers.callMethod(p.args[2], "i");
        int index = Integer.parseInt(String.valueOf(binding.get("foreground")));
        if (index < 0 || index > 64) return;
        Object data = XposedHelpers.getObjectField(p.args[1], "a");
        String color = null;
        for (Object item : (List<?>)XposedHelpers.getObjectField(data, "b")) {
            if (!theme.equals(XposedHelpers.getObjectField(item, "a"))) continue;
            List<?> colors = (List<?>)XposedHelpers.getObjectField(item, "h");
            if (index < colors.size()) color = (String)colors.get(index);
            break;
        }
        if (color == null || !color.matches("[A-Za-z0-9]+")) return;
        String asset = "cloud_watermark_material/" + name.toLowerCase(Locale.ROOT).replace(' ', '_')
            + "/" + brand.toLowerCase(Locale.ROOT) + "_" + color + (shadow ? "_shadow" : "") + ".webp";
        Object app = XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.app.ActivityThread", null), "currentApplication");
        android.content.Context context = (android.content.Context)app;
        byte[] bytes;
        try (java.io.InputStream in = context.getAssets().open(asset);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = in.read(buffer)) != -1) {
                if (out.size() + count > 512 * 1024) return;
                out.write(buffer, 0, count);
            }
            bytes = out.toByteArray();
        }
        if (bytes.length == 0) return;
        // Content-addressed names never overwrite resources referenced by queued photos.
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder key = new StringBuilder();
        for (int i=0; i<12; i++) key.append(String.format(Locale.ROOT, "%02x", digest[i] & 255));
        String filename = "identity_" + key + ".webp";
        Path local = dir.resolve(filename);
        if (!Files.isRegularFile(local) || !Arrays.equals(bytes, Files.readAllBytes(local))) Files.write(local, bytes);
        Path nativeDir = Paths.get("/data/vendor/camera/watermarks").resolve(relative);
        byte[] readback = (byte[])XposedHelpers.callStaticMethod(XposedHelpers.findClass("cn.b", loader), "h", nativeDir.toString(), filename);
        if (!Arrays.equals(bytes, readback)) {
            XposedHelpers.callStaticMethod(XposedHelpers.findClass("Gg.s", loader), "d", local, nativeDir.resolve(filename));
            readback = (byte[])XposedHelpers.callStaticMethod(XposedHelpers.findClass("cn.b", loader), "h", nativeDir.toString(), filename);
        }
        if (!Arrays.equals(bytes, readback)) throw new java.io.IOException("native logo readback");
        row.put("path", "pathType=direct;path=" + filename);
        if (reports++ < 12) note("LOGO_EXPORTED identity=" + market + " template=" + relative + " color=" + color + " shadow=" + shadow);
    }
}
