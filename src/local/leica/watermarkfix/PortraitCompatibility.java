package local.leica.watermarkfix;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/** Match native refocus to physical hardware without rewriting photo metadata. */
final class PortraitCompatibility {
    private static RearDevice physical() {
        Context app = (Context) XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.app.ActivityThread", null), "currentApplication");
        if (app == null) return null;
        DisplayManager manager = (DisplayManager) app.getSystemService(Context.DISPLAY_SERVICE);
        if (manager == null) return null;
        for (Display display : manager.getDisplays()) {
            Display.Mode mode = display.getMode();
            RearDevice device = RearDevice.forPanel(display.getDisplayId(),
                    (Integer) XposedHelpers.callMethod(display, "getType"),
                    mode.getPhysicalWidth(), mode.getPhysicalHeight());
            if (device != null) return device;
        }
        return null;
    }

    static void installEditor(ClassLoader loader) {
        final Class<?> support = XposedHelpers.findClass("jt.a", loader);
        XC_MethodHook physicalEntry = new XC_MethodHook() {
            int reports;
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || Boolean.TRUE.equals(p.getResult())) return;
                try {
                    RearDevice device = physical();
                    Object exif = p.args[0];
                    if (device == null || !nativeBokehAvailable(loader)) return;
                    if (!"mi_portrait".equals(XposedHelpers.callMethod(exif, "f", "algorithmComment"))) return;
                    Object xmp = XposedHelpers.callMethod(exif, "z");
                    if (xmp == null) return;
                    String xml = (String) XposedHelpers.callMethod(xmp, "i",
                            "http://ns.xiaomi.com/photos/1.0/camera/", "XMPMeta");
                    if (xml == null) return;
                    org.xmlpull.v1.XmlPullParser parser = android.util.Xml.newPullParser();
                    parser.setInput(new java.io.StringReader(xml));
                    for (int event = parser.getEventType(); event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
                            event = parser.next()) {
                        if (event != org.xmlpull.v1.XmlPullParser.START_TAG || !"depthmap".equals(parser.getName())) continue;
                        String product = parser.getAttributeValue(null, "productName");
                        String version = parser.getAttributeValue(null, "version");
                        if (!device.name.equals(product) || version == null || Integer.parseInt(version) < 4) return;
                        // The JPEG entry has an additional marketing-name check.
                        // Keep it for foreign photos; accept the physical name here.
                        if (p.args.length == 2) {
                            String model = (String) XposedHelpers.callMethod(exif, "f", "XiaomiProduct");
                            if (model == null || model.isEmpty()) model = (String) XposedHelpers.callMethod(exif, "f", "Model");
                            if (!market(device).equals(model)) return;
                            String depthVersion = (String) XposedHelpers.callMethod(exif, "f", "depthMapVersion");
                            if (depthVersion == null || Integer.parseInt(depthVersion) < 4) return;
                        }
                        p.setResult(true);
                        XposedHelpers.setStaticBooleanField(support, "b", true);
                        if (reports++ < 8) RearMenu.log("REFOCUS_PHYSICAL_ENTRY method=" + p.method.getName()
                                + " product=" + product + " version=" + version);
                        return;
                    }
                } catch (Throwable error) { RearMenu.log("REFOCUS_PHYSICAL_ENTRY_FAILED " + error); }
            }
        };
        Class<?> exifClass = XposedHelpers.findClass("pc.b", loader);
        XposedHelpers.findAndHookMethod(support, "j", exifClass, android.net.Uri.class, physicalEntry);
        XposedHelpers.findAndHookMethod(support, "k", exifClass, physicalEntry);
        // Exact editor 2.10.40.4.8 only. Its old MiDualPhotoJni implementation is
        // absent, while MiCameraJni is supplied by libmi_portrait_jni.so. Preserve
        // the original cross-device policy; restore same-device classification
        // only for a V4+ photo whose product matches the actual supported panel.
        XposedHelpers.findAndHookMethod("y30.o", loader, "o", new XC_MethodHook() {
            int reports;
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || Boolean.TRUE.equals(p.getResult())) return;
                try {
                    RearDevice device = physical();
                    if (device == null) return;
                    Object metadata = XposedHelpers.getObjectField(p.thisObject, "d");
                    String product = (String) XposedHelpers.getObjectField(metadata, "v");
                    int version = XposedHelpers.getIntField(metadata, "r");
                    if (!device.name.equals(product) || version < 4) return;
                    if (!nativeBokehAvailable(loader)) return;
                    p.setResult(true);
                    if (reports++ < 8) RearMenu.log("REFOCUS_PHYSICAL_MATCH product=" + product
                            + " version=" + version + " reportedProduct=" + android.os.Build.PRODUCT);
                } catch (Throwable error) { RearMenu.log("REFOCUS_PHYSICAL_MATCH_FAILED " + error); }
            }
        });
    }

    private static String market(RearDevice device) {
        return device == RearDevice.PRO_MAX ? "Xiaomi 17 Pro Max" : "Xiaomi 17 Pro";
    }

    private static boolean nativeBokehAvailable(ClassLoader loader) {
        Object capabilities = XposedHelpers.getStaticObjectField(XposedHelpers.findClass("jt.b", loader), "a");
        return capabilities != null && XposedHelpers.getBooleanField(capabilities, "e")
                && new java.io.File("/odm/lib64/libmiphone_capture_bokeh.so").isFile()
                && new java.io.File("/odm/etc/camera/dualcam_bokeh_params_pro.json").isFile();
    }
}
