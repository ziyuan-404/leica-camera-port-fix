package local.leica.watermarkfix;

import android.view.View;
import de.robv.android.xposed.*;
import java.lang.reflect.Method;

/** Restore shipped hardware data while retaining the camera's native controllers. */
final class NativeControls {
    static void install(final ClassLoader loader, final Object feature, final Object physical) {
        for (final String name : new String[]{"S4", "m5", "y5", "I5", "b2", "Q0", "R0", "P0", "S0", "T0", "B1", "v1", "S6", "w1"}) {
            try {
                final Method nativeMethod = XposedHelpers.findMethodBestMatch(physical.getClass(), name);
                Method activeMethod = XposedHelpers.findMethodBestMatch(feature.getClass(), name);
                Object value = nativeMethod.invoke(physical);
                RearMenu.log("NATIVE_CONTROL " + name + " physical=" + value
                        + " active=" + activeMethod.invoke(feature));
                XposedBridge.hookMethod(activeMethod, new XC_MethodHook(XC_MethodHook.PRIORITY_HIGHEST) {
                    @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                        if (p.thisObject != feature) return;
                        // Smart FOV on the rear panel has a separate, paired stop table.
                        if ((name.equals("S6") || name.equals("w1")) && !RearMenu.rear()) return;
                        if ((name.equals("B1") || name.equals("v1")) && RearMenu.rear()) return;
                        Object result = nativeMethod.invoke(physical);
                        p.setResult(result);
                    }
                });
            } catch (Throwable failure) {
                RearMenu.log("NATIVE_CONTROL_FAILED " + name + ": " + failure);
            }
        }
        // Both earlier patches and recent base adapters add a relocated ESP control.
        // Keep the original extra-top control and its original visibility policy.
        XposedHelpers.findAndHookMethod(View.class, "setVisibility", int.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                View view = (View) p.thisObject;
                if ("kobbin.leicaflavor.native-esp".equals(view.getTag(2131754994))
                        || "local-native-rear-preview".equals(view.getTag())) p.args[0] = View.GONE;
            }
        });
        XposedHelpers.findAndHookMethod(View.class, "setTag", int.class, Object.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if ((Integer) p.args[0] == 2131754994
                        && "kobbin.leicaflavor.native-esp".equals(p.args[1]))
                    ((View) p.thisObject).setVisibility(View.GONE);
            }
        });
        // Bound diagnostics: record native component data, not pixels or user metadata.
        XposedBridge.hookAllMethods(XposedHelpers.findClass("r2.c0", loader), "N", new XC_MethodHook() {
            int reports;
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (reports++ >= 8) return;
                RearMenu.log("PIXEL_NATIVE mode=" + p.args[0] + " camera=" + p.args[1]
                        + " items=" + XposedHelpers.callMethod(p.thisObject, "getItems"));
            }
        });
    }

}
