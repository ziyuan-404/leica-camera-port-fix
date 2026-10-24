package local.leica.watermarkfix;

import android.util.Log;
import de.robv.android.xposed.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Use this APK's physical-device tables with its original zoom controllers. */
public final class NativeZoom290 {
    public static void install(final Object active, final Object physical) {
        final ClassLoader loader = active.getClass().getClassLoader();
        RearNativeMenu290.install(loader);
        // FeatureLoader builds the mode registry before a display session exists.
        // A rear-only s6 predicate drops StickerModuleEntry at this early stage.
        // Restore the physical predicate only for registration and the native
        // mode-list filter, which itself keeps sticker mode off the main screen.
        try {
            final Method source = XposedHelpers.findMethodBestMatch(physical.getClass(), "s6");
            Method target = XposedHelpers.findMethodBestMatch(active.getClass(), "s6");
            XposedBridge.hookMethod(target, new XC_MethodHook(XC_MethodHook.PRIORITY_HIGHEST) {
                @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    if (p.thisObject != active) return;
                    for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                        String caller = frame.getClassName();
                        if ("com.android.camera.features.mode.sticker.StickerModuleEntry".equals(caller)
                                || "v2.S".equals(caller)) {
                            p.setResult(XposedBridge.invokeOriginalMethod(source, physical, new Object[0]));
                            return;
                        }
                    }
                }
            });
            Log.i("LeicaNativeZoom", "STICKER_REGISTRY_READY native rear entry and main-screen filter");
        } catch (Throwable failure) {
            Log.e("LeicaNativeZoom", "STICKER_REGISTRY_FAILED", failure);
        }
        // Q1: native click/focal labels; K1: main stops; L1/e8: paired rear FOV tables.
        // The remaining tables are the OEM slider and optical expansion providers.
        for (final String name : new String[]{"Q1", "K1", "L1", "e8", "I0", "M1", "P1", "R1"}) {
            try {
                final Method source = XposedHelpers.findMethodBestMatch(physical.getClass(), name);
                Method target = XposedHelpers.findMethodBestMatch(active.getClass(), name);
                if (Modifier.isStatic(source.getModifiers()) || source.getParameterTypes().length != 0
                        || source.getReturnType() != target.getReturnType())
                    throw new IllegalStateException("OEM table signature changed: " + name);
                XposedBridge.hookMethod(target, new XC_MethodHook(XC_MethodHook.PRIORITY_HIGHEST) {
                    @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                        if (p.thisObject != active) return;
                        boolean rear = Boolean.TRUE.equals(XposedHelpers.callStaticMethod(
                                XposedHelpers.findClass("L2.c", loader), "b0"));
                        if (("Q1".equals(name) || "K1".equals(name)) && rear) return;
                        if (("L1".equals(name) || "e8".equals(name)) && !rear) return;
                        p.setResult(XposedBridge.invokeOriginalMethod(source, physical, new Object[0]));
                    }
                });
                Log.i("LeicaNativeZoom", "TABLE_READY " + name + " source=" + physical.getClass().getName());
            } catch (Throwable failure) {
                Log.e("LeicaNativeZoom", "TABLE_FAILED " + name, failure);
            }
        }
    }
}
