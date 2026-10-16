package local.leica.watermarkfix;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipFile;

/** Compatibility fix for the signed Nezha camera bundled with Leica module 0.216. */
public final class WatermarkFix implements IXposedHookLoadPackage {
    private static final String CAMERA_SHA =
        "a53caba631d2e76510546e351f2cd24c5259c08383a9825d59a19e845fb3e7b0";
    private static final String TARGET = "17 ultra by leica";
    private int reports;

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam load) throws Throwable {
        if (!"com.android.camera".equals(load.packageName)
                || !"com.android.camera".equals(load.processName)) return;
        try {
            String source = (String) XposedHelpers.getObjectField(load.appInfo, "sourceDir");
            if (!CAMERA_SHA.equals(sha256(source))) {
                log("SKIPPED: camera APK fingerprint differs");
                return;
            }
            final ClassLoader loader = load.classLoader;
            Class<?> view = XposedHelpers.findClass("fs.g", loader);
            Method serializer = view.getDeclaredMethod("b",
                    XposedHelpers.findClass("org.json.JSONArray", loader),
                    XposedHelpers.findClass("cs.e", loader),
                    XposedHelpers.findClass("Gg.a0", loader), Path.class);
            XposedBridge.hookMethod(serializer, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable()) return;
                    try {
                        fixNativeLogo(param, loader, source);
                    } catch (Throwable failure) {
                        log("NATIVE_LOGO_ERROR: " + failure.getClass().getSimpleName()
                                + ": " + failure.getMessage());
                    }
                }
            });
            log("READY 0.2: official logo for native template; original model formatter retained");
            try { RearMenu.install(loader); }
            catch (Throwable rearFailure) { log("REAR_HOOK_ERROR: " + rearFailure); }
        } catch (Throwable failure) {
            log("HOOK_ERROR: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private void fixNativeLogo(XC_MethodHook.MethodHookParam param, ClassLoader loader,
            String source) throws Exception {
        if (!"logo".equalsIgnoreCase((String) XposedHelpers.callMethod(param.thisObject, "i"))) return;
        Object identity = XposedHelpers.callStaticMethod(XposedHelpers.findClass("Ku.a", loader), "a");
        String brand = ((String) XposedHelpers.getObjectField(identity, "a")).toLowerCase(Locale.ROOT);
        String model = (String) XposedHelpers.getObjectField(identity, "b");
        if (!"xiaomi".equals(brand) || !TARGET.equalsIgnoreCase(model)) return;

        Object array = param.args[0];
        int count = (Integer) XposedHelpers.callMethod(array, "length");
        if (count == 0) return;
        Object json = XposedHelpers.callMethod(array, "getJSONObject", count - 1);
        if (!"logo".equals(XposedHelpers.callMethod(json, "optString", "id"))) return;
        String path = (String) XposedHelpers.callMethod(json, "optString", "path");
        Class<?> paths = XposedHelpers.findClass("Gg.s", loader);
        Map<?, ?> pathMap = (Map<?, ?>) XposedHelpers.callStaticMethod(paths, "j", path);
        if (!"fill".equals(pathMap.get("pathType")) || !"${logo}".equals(pathMap.get("path"))) return;

        Path folder = ((Path) param.args[3]).toAbsolutePath().normalize();
        // Initial compatibility scope: the active official Leica 91 template only.
        if (!folder.toString().equals("/data/user/0/com.android.camera/files/watermarks/leica/91")
                && !folder.toString().equals("/data/data/com.android.camera/files/watermarks/leica/91")) return;
        Object config = XposedHelpers.newInstance(XposedHelpers.findClass("org.json.JSONObject", loader),
                new String(Files.readAllBytes(folder.resolve("config.json")), StandardCharsets.UTF_8));
        Object metadata = XposedHelpers.callMethod(config, "getJSONObject", "metadata");
        boolean inside = "in".equalsIgnoreCase((String) XposedHelpers.callMethod(metadata, "optString", "position"));

        String theme = (String) XposedHelpers.callMethod(param.args[2], "i");
        Object background = XposedHelpers.getObjectField(param.args[1], "a");
        List<?> backgrounds = (List<?>) XposedHelpers.getObjectField(background, "b");
        String foreground = null;
        int index = Integer.parseInt((String) pathMap.get("foreground"));
        for (Object item : backgrounds) {
            if (theme.equals(XposedHelpers.getObjectField(item, "a"))) {
                List<?> colors = (List<?>) XposedHelpers.getObjectField(item, "h");
                foreground = (String) colors.get(index);
                break;
            }
        }
        if (foreground == null || !foreground.matches("[A-Za-z0-9]+")) return;
        String assetName = brand + "_" + foreground + (inside ? "_shadow" : "") + ".webp";
        String asset = "assets/cloud_watermark_material/17_ultra_by_leica/" + assetName;
        String fileName = "lcc17_" + assetName;
        Path local = folder.resolve(fileName);
        try (ZipFile apk = new ZipFile(source)) {
            if (apk.getEntry(asset) == null) {
                log("NATIVE_LOGO_MISSING: " + asset);
                return;
            }
            try (InputStream input = apk.getInputStream(apk.getEntry(asset))) {
                if (!Files.exists(local) || Files.size(local) != apk.getEntry(asset).getSize()) {
                    Files.copy(input, local, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        Path vendor = Paths.get("/data/vendor/camera/watermarks/leica/91", fileName);
        Class<?> nativeFiles = XposedHelpers.findClass("cn.b", loader);
        byte[] expected = Files.readAllBytes(local);
        byte[] actual = (byte[]) XposedHelpers.callStaticMethod(nativeFiles, "h",
                vendor.getParent().toString(), fileName);
        if (!Arrays.equals(expected, actual)) {
            XposedHelpers.callStaticMethod(paths, "d", local, vendor);
            actual = (byte[]) XposedHelpers.callStaticMethod(nativeFiles, "h",
                    vendor.getParent().toString(), fileName);
        }
        if (!Arrays.equals(expected, actual)) {
            log("NATIVE_LOGO_COPY_FAILED: " + fileName);
            return;
        }
        XposedHelpers.callMethod(json, "put", "path", "pathType=direct;path=" + fileName);
        if (reports++ < 24) log("NATIVE_LOGO: " + fileName + " bytes=" + actual.length
                + " verified=true theme=" + theme + "; official dimensions retained");
    }

    private static String sha256(String path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(path)) {
            byte[] buffer = new byte[65536];
            int length;
            while ((length = input.read(buffer)) != -1) digest.update(buffer, 0, length);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) result.append(String.format("%02x", value & 255));
        return result.toString();
    }

    private static void log(String text) {
        XposedBridge.log("LeicaWatermarkFix " + text);
        try {
            XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.util.Log", null),
                "i", "LeicaWatermarkFix", text);
        } catch (Throwable ignored) { }
    }
}
