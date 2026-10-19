package local.leica.watermarkfix;

import android.os.Build;
import de.robv.android.xposed.*;

/** Complete the Leica variant marker for the exact supported editor APK only. */
final class EditorLeicaIdentity {
    static void install(final ClassLoader loader) {
        // These Leica identities are used by the camera adapter / connected faker template.
        // Retain the official XM_LEGEND branch for other reported devices and models.
        if (!"nezha".equals(Build.DEVICE)
                || !("2512BPNDAC".equals(Build.MODEL) || "25128PNA1C".equals(Build.MODEL))) {
            log("EDITOR_LEICA_IDENTITY unchanged device=" + Build.DEVICE + " model=" + Build.MODEL);
            return;
        }
        final Class<?> props = XposedHelpers.findClass("android.os.SystemProperties", null);
        String original = (String) XposedHelpers.callStaticMethod(props, "get", "ro.theme_customize", "");
        XposedBridge.hookAllMethods(props, "get", new XC_MethodHook(XC_MethodHook.PRIORITY_HIGHEST) {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.args.length == 0) return;
                if ("ro.theme_customize".equals(p.args[0])
                        || "ro.boot.product.theme_customize".equals(p.args[0])) p.setResult("LCC");
            }
        });
        // rt.a.g() is the editor's own cached (nezha && theme == LCC) predicate.
        // Install before its first use; leave mode, photo parsing and render jobs native.
        Object leica = XposedHelpers.callStaticMethod(XposedHelpers.findClass("rt.a", loader), "g");
        log("EDITOR_LEICA_IDENTITY device=" + Build.DEVICE + " model=" + Build.MODEL
                + " originalTheme=" + original + " nativeLeica=" + leica);
    }
    private static void log(String message) {
        android.util.Log.i("LeicaMomentFix", message);
        XposedBridge.log("LeicaMomentFix " + message);
    }
}
