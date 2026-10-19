package local.leica.watermarkfix;

import android.os.Looper;
import android.view.*;
import java.lang.reflect.Method;
import de.robv.android.xposed.*;

/** Native rear-display controllers with the existing Leica skin. */
final class RearMenu {
    static ClassLoader loader;
    static int rearDisplayId = -1;
    static boolean installationAttempted;

    static void installWhenReady(final ClassLoader cl) {
        android.app.Application app = (android.app.Application) XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.app.ActivityThread", cl), "currentApplication");
        if (app != null) installForHardware(cl, app);
        else XposedHelpers.findAndHookMethod(android.app.Application.class, "attach",
                android.content.Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                installForHardware(cl, (android.content.Context) p.args[0]);
            }
        });
    }

    static synchronized void installForHardware(ClassLoader cl, android.content.Context context) {
        if (installationAttempted) return;
        installationAttempted = true;
        try {
            android.hardware.display.DisplayManager manager = (android.hardware.display.DisplayManager)
                    context.getSystemService(android.content.Context.DISPLAY_SERVICE);
            if (manager != null) for (Display display : manager.getDisplays()) {
                int type = (Integer) XposedHelpers.callMethod(display, "getType");
                Display.Mode mode = display.getMode();
                RearDevice device = RearDevice.forPanel(display.getDisplayId(), type,
                        mode.getPhysicalWidth(), mode.getPhysicalHeight());
                if (device != null) {
                    rearDisplayId = display.getDisplayId();
                    log("HARDWARE rear=" + rearDisplayId + " panel=" + mode.getPhysicalWidth()
                            + "x" + mode.getPhysicalHeight() + " profile=" + device.name
                            + " reportedDevice=" + android.os.Build.DEVICE);
                    install(cl, device);
                    return;
                }
            }
            log("SKIPPED rear UI: no supported internal rear panel");
        } catch (Throwable failure) {
            log("REAR_HOOK_ERROR: " + failure);
        }
    }

    static void install(ClassLoader cl, RearDevice device) throws Exception {
        loader=cl;
        // Initialize the genuine LC provider before applying any contextual UI predicate.
        XposedHelpers.getStaticObjectField(XposedHelpers.findClass("o9.a",cl),"a");
        XposedHelpers.getStaticObjectField(XposedHelpers.findClass("i2.a",cl),"a");
        XposedHelpers.getStaticObjectField(XposedHelpers.findClass("X6.i",cl),"a");
        // Use the shipped native rear beauty/filter view factory as a whole.
        // Its own adapter owns item sizing, corners, labels and selection frame.
        final Object nativeEffects=allocate("p9.v");
        XposedHelpers.findAndHookMethod("y9.c",cl,"a",new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p){if(rear())p.setResult(nativeEffects);}
        });
        final Object nativeFilterData=allocate("j2.c");
        XposedHelpers.findAndHookMethod("k2.a",cl,"c",new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p){if(rear())p.setResult(nativeFilterData);}
        });
        // Color only: retain the stock indicator drawable and all native geometry.
        XposedBridge.hookAllMethods(XposedHelpers.findClass("O9.i",cl),"Lr",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p)throws Throwable{
                if(!rear())return;
                android.widget.ImageView indicator=(android.widget.ImageView)XposedHelpers.getObjectField(p.thisObject,"O");
                if(indicator!=null)indicator.setColorFilter(indicator.getResources().getColor(2131102301,null),android.graphics.PorterDuff.Mode.SRC_IN);
            }
        });
        Object feature=XposedHelpers.getObjectField(XposedHelpers.getStaticObjectField(XposedHelpers.findClass("Je.b$b",cl),"a"),"e");
        log("FEATURE "+feature.getClass().getName()+" j3="+XposedHelpers.callMethod(feature,"j3"));
        Method rearMaterial=XposedHelpers.findMethodBestMatch(feature.getClass(),"j3");
        XposedBridge.hookMethod(rearMaterial,new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p){if(rear())p.setResult(true);}
        });
        final Object physical=XposedHelpers.newInstance(XposedHelpers.findClass(device.featureClass,cl));
        NativeControls.install(cl, feature, physical);
        for(String operation:new String[]{"getDimensionPixelSize","getDimensionPixelOffset"})XposedHelpers.findAndHookMethod(android.content.res.Resources.class,operation,int.class,new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p){
                int id=(Integer)p.args[0];
                if(id!=2131171321&&id!=2131171298&&id!=2131171301&&id!=2131170520)return;
                if(!rear())return;
                float dp;
                android.content.res.Resources res=(android.content.res.Resources)p.thisObject;
                int sw=res.getConfiguration().smallestScreenWidthDp;
                if(id==2131171321||id==2131171298)dp=sw>=210?80:77;
                else if(id==2131171301)dp=0;
                else if(id==2131170520)dp=sw>=210?41:40;
                else return;
                p.setResult(Math.round(dp*res.getDisplayMetrics().density));
            }
        });
        final Object nativeMenu=allocate("De.c");
        final Object nativeTop=allocate("p9.t");
        XposedHelpers.findAndHookMethod("y9.c",cl,"r",new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p){if(rear())p.setResult(nativeMenu);}
        });
        for(final String name:new String[]{"E","i","n","v","P"}){
            for(final Method method:XposedHelpers.findClass("y9.u",cl).getDeclaredMethods()){
                if(!method.getName().equals(name))continue;
                final Method nativeMethod=nativeTop.getClass().getDeclaredMethod(name,method.getParameterTypes());
                nativeMethod.setAccessible(true);
                XposedBridge.hookMethod(method,new XC_MethodHook(){
                    @Override protected void beforeHookedMethod(MethodHookParam p)throws Throwable{if(rear())p.setResult(nativeMethod.invoke(nativeTop,p.args));}
                });
            }
        }
        XposedBridge.hookAllMethods(XposedHelpers.findClass("com.android.camera.features.mode.capture.i0",cl),"g",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p){if(rear()&&p.args[0] instanceof android.widget.ImageView)skinEffect((android.widget.ImageView)p.args[0]);}
        });
        XposedHelpers.findAndHookMethod("Je.b",cl,"V",new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p){
                if(Looper.myLooper()!=Looper.getMainLooper()||!rear())return;
                for(StackTraceElement frame:Thread.currentThread().getStackTrace()){
                    String c=frame.getClassName();
                    if(c.equals("o5.G")||c.equals("o5.N")||c.equals("o5.c")||c.equals("com.android.camera2.compat.theme.custom.mm.top.MenuExpendViewMM")){p.setResult(false);return;}
                }
            }
        });
        XposedHelpers.findAndHookMethod("o5.G",cl,"Dr",new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p)throws Throwable{
                if(!rear())return;replaceMenuBackground(p.thisObject);p.setResult(null);
            }
        });
        XposedHelpers.findAndHookMethod("o5.I",cl,"Zq",new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p)throws Throwable{
                if(!rear())return;
                android.content.res.Resources res=(android.content.res.Resources)XposedHelpers.callMethod(p.thisObject,"getResources");
                p.setResult(menuBorder(res));
            }
        });
        XposedBridge.hookAllMethods(XposedHelpers.findClass("o5.c",cl),"onBindViewHolder",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p){
                if(!rear()||p.hasThrowable())return;
                View row=(View)XposedHelpers.getObjectField(p.args[0],"itemView");
                View border=row.findViewById(2131428359);
                if(border==null)return;
                ViewGroup.LayoutParams lp=border.getLayoutParams();
                lp.width=row.getResources().getDimensionPixelSize(2131171321)-Math.round(8*row.getResources().getDisplayMetrics().density);
                border.setLayoutParams(lp);

            }
        });
        XposedBridge.hookAllConstructors(XposedHelpers.findClass("o5.N",cl),new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p){
                if(!rear())return;View root=(View)XposedHelpers.getObjectField(p.thisObject,"t");
                ViewGroup.LayoutParams lp=root.getLayoutParams();
                lp.height=root.getResources().getDimensionPixelSize(2131171298);
                lp.width=root.getResources().getDimensionPixelSize(2131171321);
                root.setLayoutParams(lp);
                View label=root.findViewById(2131429642);
                if(label!=null&&label.getTag(2131754995)==null&&label.getLayoutParams() instanceof ViewGroup.MarginLayoutParams){
                    ViewGroup.MarginLayoutParams textLp=(ViewGroup.MarginLayoutParams)label.getLayoutParams();
                    textLp.topMargin+=Math.round(4*root.getResources().getDisplayMetrics().density);
                    label.setLayoutParams(textLp);
                    label.setTag(2131754995,Boolean.TRUE);
                }
            }
        });
        for(String operation:new String[]{"er","Gr"})XposedBridge.hookAllMethods(XposedHelpers.findClass("o5.G",cl),operation,new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p)throws Throwable{
                if(!rear())return;
                View n=(View)XposedHelpers.getObjectField(p.thisObject,"n");
                View blur=(View)XposedHelpers.getObjectField(p.thisObject,"o");
                replaceMenuBackground(p.thisObject);
                if(blur!=null)blur.setVisibility(View.GONE);
                View c=(View)XposedHelpers.getObjectField(p.thisObject,"c");
                ViewGroup.MarginLayoutParams lp=(ViewGroup.MarginLayoutParams)c.getLayoutParams();
                lp.leftMargin=0;lp.rightMargin=Math.round(16*n.getResources().getDisplayMetrics().density);
                c.setLayoutParams(lp);
            }
        });
        XposedBridge.hookAllMethods(XposedHelpers.findClass("o5.G",cl),"Yq",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p){
                if(!rear())return;View m=(View)XposedHelpers.getObjectField(p.thisObject,"m");
                ViewGroup.MarginLayoutParams lp=(ViewGroup.MarginLayoutParams)m.getLayoutParams();
                lp.rightMargin=Math.round(16*m.getResources().getDisplayMetrics().density);m.setLayoutParams(lp);
            }
        });
        XposedHelpers.findAndHookMethod(android.app.Activity.class,"onResume",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p){
                final android.app.Activity activity=(android.app.Activity)p.thisObject;
                if(!activity.getClass().getName().equals("com.android.camera.Camera"))return;
                final View decor=activity.getWindow().getDecorView();
                for(int delay:new int[]{300,1200,2500})decor.postDelayed(new Runnable(){public void run(){
                    try{
                        if(activity.isFinishing())return;
                        if(activity.getDisplay().getDisplayId()==rearDisplayId)skinPreview(decor);
                    }catch(Throwable e){log("Camera controls: "+e);}
                }},delay);
            }
        });
        log("READY 1.2.2 native hardware controls + native filters");
    }
    static boolean rear(){
        try{return (Boolean)XposedHelpers.callStaticMethod(XposedHelpers.findClass("K2.b",loader),"a0");}
        catch(Throwable ignored){return false;}
    }
    static Object allocate(String name)throws Exception{
        Class<?> unsafeClass=Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field field=unsafeClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe=field.get(null);
        return unsafeClass.getMethod("allocateInstance",Class.class).invoke(unsafe,XposedHelpers.findClass(name,loader));
    }
    static android.graphics.drawable.GradientDrawable menuBorder(android.content.res.Resources res){
        android.graphics.drawable.GradientDrawable bg=(android.graphics.drawable.GradientDrawable)res.getDrawable(2131233525,null).mutate();
        int color=(Integer)XposedHelpers.callStaticMethod(XposedHelpers.findClass("f2.b",loader),"a",2131102692);
        bg.setStroke(res.getDimensionPixelSize(2131171323),color);return bg;
    }
    static void replaceMenuBackground(Object menu){
        View base=(View)XposedHelpers.getObjectField(menu,"n"),blur=(View)XposedHelpers.getObjectField(menu,"o");
        if(base==null)return;
        // The native palette switches both foreground and background for flash halo
        // and sticker mode. Resolve the LC background through that same palette.
        int color=(Integer)XposedHelpers.callStaticMethod(XposedHelpers.findClass("f2.b",loader),"a",2131100061);
        base.setBackgroundColor(color);base.setClipToOutline(false);
        if(blur!=null){XposedHelpers.callMethod(blur,"setEnableBlur",false);blur.setBackground(null);blur.setVisibility(View.GONE);}
    }
    static void skinEffect(android.widget.ImageView v){
        try{
            if(!v.getResources().getResourceEntryName(v.getId()).equals("popup_right_tip_image"))return;
            // The native binder chooses the drawable, hitbox and selected state.
            // Recolor only its active tint; never swap the beauty/filter icon.
            if(v.isSelected())v.setColorFilter(v.getResources().getColor(2131102301,null),android.graphics.PorterDuff.Mode.SRC_IN);
        }catch(Throwable e){log("SKIN "+e);}
    }
    static View find(View root,String name){int id=root.getResources().getIdentifier(name,"id","com.android.camera");return id==0?null:root.findViewById(id);}
    static void skinPreview(View root){
        View effect=find(root,"popup_right_tip_image");if(effect instanceof android.widget.ImageView)skinEffect((android.widget.ImageView)effect);
        View thumb=find(root,"v9_thumbnail_layout");
        if(thumb!=null)try{XposedHelpers.callMethod(thumb,"setCardBackgroundColor",android.graphics.Color.TRANSPARENT);}catch(Throwable ignored){}
    }
    static void log(String s){XposedBridge.log("NativeRearFix "+s);}
}
