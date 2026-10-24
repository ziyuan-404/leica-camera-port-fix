package local.leica.watermarkfix;

import android.util.Log;
import de.robv.android.xposed.*;

/** Restore the OEM menu exclusion policy for the physical rear screen. */
public final class RearNativeMenu290 {
    public static void install(final ClassLoader loader) {
        try {
            // q5.M.configFragmentData adds policy 1 -> {241} on the stock theme.
            // Te.c.W() skips it on LCC, although the physical rear layout still
            // needs the original bottom-action fragment exclusion. Restore only
            // that native policy; the OEM fragment manager owns its full lifecycle.
            XposedHelpers.findAndHookMethod("q5.M", loader, "configFragmentData",
                    XposedHelpers.findClass("Z1.b", loader), new XC_MethodHook(XC_MethodHook.PRIORITY_HIGHEST) {
                private int reports;
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable() || !Boolean.TRUE.equals(XposedHelpers.callStaticMethod(
                            XposedHelpers.findClass("L2.c", loader), "b0"))) return;
                    XposedHelpers.callMethod(p.args[0], "a", 1, new int[]{241});
                    if (reports++ < 6) Log.i("LeicaRearMenu", "OEM_EXCLUSION policy=1 fragment=241 rear-only");
                }
            });
            Log.i("LeicaRearMenu", "NATIVE_POLICY_READY no view visibility or drawing-order hooks");
        } catch (Throwable failure) {
            Log.e("LeicaRearMenu", "NATIVE_POLICY_FAILED", failure);
        }
    }
}
