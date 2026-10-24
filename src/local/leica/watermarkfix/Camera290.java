package local.leica.watermarkfix;

import android.app.Application;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.Display;
import de.robv.android.xposed.*;

/** Exact 6.7.000280.0 compatibility, using the camera's own controllers. */
final class Camera290 {
    private static boolean nativeInstalled, identityInstalled;

    static void installWhenReady(final ClassLoader loader) {
        Context app = (Context) XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.app.ActivityThread", null), "currentApplication");
        if (app != null) {
            installNative(loader, app);
            installIdentity(loader);
            return;
        }
        // After-hooks run in reverse priority. Initialize the outer feature class
        // before the base adapter touches its holder, after Application.attach.
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
                new XC_MethodHook(XC_MethodHook.PRIORITY_LOWEST) {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                installNative(loader, (Context) p.args[0]);
            }
        });
        // Capture the process's original properties after all base attach hooks.
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
                new XC_MethodHook(XC_MethodHook.PRIORITY_HIGHEST) {
            @Override protected void afterHookedMethod(MethodHookParam p) { installIdentity(loader); }
        });
    }

    private static synchronized void installIdentity(ClassLoader loader) {
        if (identityInstalled) return;
        identityInstalled = true;
        DisplayIdentity290.install(loader, "", Build.MODEL);
    }

    private static synchronized void installNative(ClassLoader loader, Context context) {
        if (nativeInstalled) return;
        nativeInstalled = true;
        try {
            DisplayManager manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (manager == null) return;
            for (Display display : manager.getDisplays()) {
                Display.Mode mode = display.getMode();
                RearDevice profile = RearDevice.forPanel(display.getDisplayId(),
                        (Integer) XposedHelpers.callMethod(display, "getType"),
                        mode.getPhysicalWidth(), mode.getPhysicalHeight());
                if (profile == null) continue;
                Class.forName("Te.c", true, loader);
                Object active = XposedHelpers.getObjectField(XposedHelpers.getStaticObjectField(
                        XposedHelpers.findClass("Te.c$b", loader), "a"), "e");
                int key = profile == RearDevice.PRO_MAX ? 42674 : 35562;
                char[] encoded = ("com.mi.device." + (profile == RearDevice.PRO_MAX ? "Popsicle" : "Pandora")).toCharArray();
                for (int i = 0; i < encoded.length; i++) encoded[i] ^= key;
                Object physical = XposedHelpers.newInstance(XposedHelpers.findClass(new String(encoded), loader));
                NativeZoom290.install(active, physical);
                RearMenu.log("READY 1.2.5 camera=670002800 physical=" + profile.name);
                return;
            }
            RearMenu.log("SKIPPED 290 native controls: no supported internal rear panel");
        } catch (Throwable failure) { RearMenu.log("CAMERA_290_NATIVE_FAILED " + failure); }
    }
}
