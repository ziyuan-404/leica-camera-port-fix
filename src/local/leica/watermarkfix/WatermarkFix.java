package local.leica.watermarkfix;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Locale;

/** Compatibility patch for the exact signed camera/editor shipped by the base module. */
public final class WatermarkFix implements IXposedHookLoadPackage {
    private static final String CAMERA_SHA =
        "a53caba631d2e76510546e351f2cd24c5259c08383a9825d59a19e845fb3e7b0";
    private static final String EDITOR_SHA =
        "92049bff99de111fd59d3838d461019ea0c4b3b58a024ba9bf8da68df40c7e07";

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam load) throws Throwable {
        if ("com.miui.mediaeditor".equals(load.packageName)
                && "com.miui.mediaeditor".equals(load.processName)) {
            try {
                String source = (String) XposedHelpers.getObjectField(load.appInfo, "sourceDir");
                if (EDITOR_SHA.equals(sha256(source))) {
                    EditorLeicaIdentity.install(load.classLoader);
                    PortraitCompatibility.installEditor(load.classLoader);
                } else log("SKIPPED editor: APK fingerprint differs");
            } catch (Throwable failure) { log("EDITOR_HOOK_ERROR: " + failure); }
            return;
        }
        if (!"com.android.camera".equals(load.packageName)
                || !"com.android.camera".equals(load.processName)) return;
        try {
            String source = (String) XposedHelpers.getObjectField(load.appInfo, "sourceDir");
            if (!CAMERA_SHA.equals(sha256(source))) {
                log("SKIPPED: camera APK fingerprint differs");
                return;
            }
            // Capture native per-process identity after the base module registers its hooks.
            // DisplayIdentity also repairs official bitmap export for eligible templates.
            DisplayIdentity.installWhenReady(load.classLoader);
            log("READY 1.2.4: display identity scheduled; native portrait compatibility retained");
            try { RearMenu.installWhenReady(load.classLoader); }
            catch (Throwable rearFailure) { log("REAR_HOOK_ERROR: " + rearFailure); }
        } catch (Throwable failure) {
            log("HOOK_ERROR: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private static String sha256(String path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(path)) {
            byte[] buffer = new byte[65536]; int length;
            while ((length = input.read(buffer)) != -1) digest.update(buffer, 0, length);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value & 255));
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
