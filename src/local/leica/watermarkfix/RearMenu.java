package local.leica.watermarkfix;

import android.os.Looper;
import android.view.*;
import java.lang.reflect.Method;
import de.robv.android.xposed.*;

/** Native rear-display controllers with the existing Leica skin. */
final class RearMenu {
    static ClassLoader loader;
    static final boolean DEBUG = false;
    static Object leica;
    static void install(ClassLoader cl) throws Exception {
        loader=cl;
        XposedHelpers.findAndHookMethod(View.class,"setOnClickListener",View.OnClickListener.class,new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p){
                View v=(View)p.thisObject;
                if(v.getId()!=0x7f0b0859||!rear())return;
                Object listener=p.args[0];
                if(listener!=null&&listener.getClass().getName().equals("y4.e"))try{
                    Object action=XposedHelpers.getObjectField(listener,"d");
                    if(action!=null&&action.getClass().getName().equals("Y4.i"))
                        XposedHelpers.setObjectField(listener,"d",XposedHelpers.newInstance(XposedHelpers.findClass("Y4.k",loader),0));
                }catch(Throwable e){log("Filter entrance: "+e);}
            }
        });
        XposedHelpers.findAndHookMethod("androidx.recyclerview.widget.RecyclerView",cl,"setAdapter",XposedHelpers.findClass("androidx.recyclerview.widget.RecyclerView$g",cl),new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p){
                if(!DEBUG)return;final View view=(View)p.thisObject;String id="";try{id=view.getResources().getResourceEntryName(view.getId());}catch(Throwable ignored){}
                log("ADAPTER "+id+" "+(p.args[0]==null?"null":p.args[0].getClass().getName())+" rear="+rear());
                if(id.contains("effect")||id.contains("filter"))view.postDelayed(new Runnable(){public void run(){try{StringBuilder s=new StringBuilder();dump(view.getRootView(),s,0);java.nio.file.Files.write(new java.io.File(view.getContext().getCacheDir(),"local-filter-ui-"+(view.getDisplay()==null?-1:view.getDisplay().getDisplayId())+".txt").toPath(),s.toString().getBytes("UTF-8"));}catch(Throwable e){log("FILTER_DUMP "+e);}}},700);
            }
        });
        // Initialize the genuine LC provider before applying any contextual UI predicate.
        leica=XposedHelpers.getStaticObjectField(XposedHelpers.findClass("o9.a",cl),"a");
        XposedHelpers.getStaticObjectField(XposedHelpers.findClass("i2.a",cl),"a");
        XposedHelpers.getStaticObjectField(XposedHelpers.findClass("X6.i",cl),"a");
        // Reuse the main photo filter fragment (category 7), with its back-camera catalog.
        XposedHelpers.findAndHookMethod("com.android.camera.data.data.i",cl,"v0",new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p){
                if(!rear())return;
                for(StackTraceElement frame:Thread.currentThread().getStackTrace())
                    if(frame.getClassName().equals("v2.P")){p.setResult(false);return;}
            }
        });
        XposedHelpers.findAndHookMethod("com.android.camera.fragment.b0",cl,"initView",View.class,new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p)throws Throwable{
                if(!DEBUG)return;Object component=XposedHelpers.getObjectField(p.thisObject,"Q");
                java.util.List<?> items=(java.util.List<?>)XposedHelpers.callMethod(component,"getItems");
                StringBuilder s=new StringBuilder("PHOTO_FILTER rear="+rear()+" count="+items.size());
                for(Object item:items)s.append(" ").append(XposedHelpers.getObjectField(item,"q")).append(":").append(XposedHelpers.getIntField(item,"j"));
                log(s.toString());
            }
        });
        XposedHelpers.findAndHookMethod("com.android.camera.fragment.p",cl,"onCreateViewHolder",ViewGroup.class,int.class,new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p)throws Throwable{
                if(!rear()||p.getResult()==null)return;
                View root=(View)XposedHelpers.getObjectField(p.getResult(),"itemView");
                styleFilterItem(root);
            }
        });
        XposedBridge.hookAllMethods(XposedHelpers.findClass("O9.i",cl),"Lr",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p)throws Throwable{
                android.widget.ImageView indicator=(android.widget.ImageView)XposedHelpers.getObjectField(p.thisObject,"O");
                if(indicator!=null){android.graphics.drawable.Drawable d=indicator.getDrawable();
                    
                    if(rear()&&d instanceof android.graphics.drawable.GradientDrawable){
                        android.content.res.Resources res=indicator.getResources();float ratio=filterThumb(res)/res.getDimension(2131170402);
                        android.graphics.drawable.GradientDrawable shape=(android.graphics.drawable.GradientDrawable)d.mutate();
                        shape.setCornerRadius(res.getDimension(2131170386)*ratio);
                        shape.setStroke(Math.round(res.getDimension(2131171712)*ratio),res.getColor(2131102301,null));
                    }
                }
            }
        });
        XposedHelpers.findAndHookMethod("O9.i",cl,"updateView4SecondScreen",View.class,android.os.Bundle.class,new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p)throws Throwable{
                android.widget.ImageView indicator=(android.widget.ImageView)XposedHelpers.getObjectField(p.thisObject,"O");
                android.content.res.Resources res=indicator.getResources();int thumb=filterThumb(res);
                
                int frame=Math.round(thumb*68f/56f);ViewGroup.LayoutParams lp=indicator.getLayoutParams();lp.width=frame;lp.height=frame;indicator.setLayoutParams(lp);
                indicator.setTranslationY(-(filterPitch(res)-thumb)/2f);
                keepFilterFrame(indicator);
                View original=find((View)p.args[0],"filter_original");if(original!=null)styleFilterItem(original);
            }
        });
        XposedHelpers.findAndHookMethod("com.android.camera.fragment.e",cl,"v",View.class,new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p)throws Throwable{
                View thumb=find((View)p.args[0],"filter_still_parent");if(thumb==null)return;
                float radius=((Number)XposedHelpers.callMethod(thumb,"getCornerRadius")).floatValue();
                if(rear()){
                    android.content.res.Resources res=thumb.getResources();
                    XposedHelpers.callMethod(thumb,"setCornerRadius",radius*filterThumb(res)/res.getDimension(2131170402));
                }

            }
        });
        XposedHelpers.findAndHookMethod("com.android.camera.fragment.b0",cl,"updateView4SecondScreen",View.class,android.os.Bundle.class,new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p){
                final View root=((View)p.args[0]).getRootView();root.postDelayed(new Runnable(){public void run(){styleFilterTitle(root.getRootView());}},200);
            }
        });
        Object feature=XposedHelpers.getObjectField(XposedHelpers.getStaticObjectField(XposedHelpers.findClass("Je.b$b",cl),"a"),"e");
        log("FEATURE "+feature.getClass().getName()+" j3="+XposedHelpers.callMethod(feature,"j3"));
        Method rearMaterial=XposedHelpers.findMethodBestMatch(feature.getClass(),"j3");
        XposedBridge.hookMethod(rearMaterial,new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p){if(rear())p.setResult(true);}
        });
        final Object physical=XposedHelpers.newInstance(XposedHelpers.findClass("\uc51b\uc517\uc515\uc556\uc515\uc511\uc556\uc51c\uc51d\uc50e\uc511\uc51b\uc51d\uc556\uc528\uc517\uc508\uc50b\uc511\uc51b\uc514\uc51d",cl));
        for(final String name:new String[]{"S6","S4","m5","y5","I5"}){
            final Method nativeMethod=XposedHelpers.findMethodBestMatch(physical.getClass(),name);
            Method portMethod=XposedHelpers.findMethodBestMatch(feature.getClass(),name);
            log("CAPABILITY "+name+" physical="+nativeMethod.invoke(physical)+" port="+portMethod.invoke(feature));
            if(!nativeMethod.equals(portMethod))XposedBridge.hookMethod(portMethod,new XC_MethodHook(XC_MethodHook.PRIORITY_HIGHEST){
                @Override protected void beforeHookedMethod(MethodHookParam p)throws Throwable{if(name.equals("S4")||name.equals("m5")||rear())p.setResult(nativeMethod.invoke(physical));}
            });
        }
        for(String operation:new String[]{"getDimensionPixelSize","getDimensionPixelOffset"})XposedHelpers.findAndHookMethod(android.content.res.Resources.class,operation,int.class,new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p){
                int id=(Integer)p.args[0];
                if(id!=2131171321&&id!=2131171298&&id!=2131171301&&id!=2131170520&&id!=2131170487)return;
                if(!rear())return;
                if(id==2131170487){p.setResult(filterPitch((android.content.res.Resources)p.thisObject));return;}float dp;
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
        for(final String name:new String[]{"E","i","n"}){
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
        XposedHelpers.findAndHookMethod(ViewTreeObserver.class,"addOnPreDrawListener",
                ViewTreeObserver.OnPreDrawListener.class,new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p){
                if(p.args[0]!=null && (p.args[0].getClass().getName().equals("com.kobbin.leicaflavor.d0")||p.args[0].getClass().getName().equals("com.kobbin.leicaflavor.e0")))p.setResult(null);
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
                if(label!=null&&label.getLayoutParams() instanceof ViewGroup.MarginLayoutParams){
                    ViewGroup.MarginLayoutParams textLp=(ViewGroup.MarginLayoutParams)label.getLayoutParams();
                    textLp.topMargin+=Math.round(4*root.getResources().getDisplayMetrics().density);
                    label.setLayoutParams(textLp);
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
                        if(activity.getDisplay().getDisplayId()==0)addPreviewButton(activity);
                        if(activity.getDisplay().getDisplayId()==1)skinPreview(decor);
                        if(DEBUG){StringBuilder state=new StringBuilder();dump(decor,state,0);java.nio.file.Files.write(new java.io.File(activity.getCacheDir(),"local-ui-"+activity.getDisplay().getDisplayId()+".txt").toPath(),state.toString().getBytes("UTF-8"));}
                    }catch(Throwable e){log("Camera controls: "+e);}
                }},delay);
            }
        });
        log("READY 0.8 native rear controllers + Leica resources");
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
    static void keepFilterFrame(final android.widget.ImageView indicator){
        if(indicator.getTag(0x7f0ffff1)!=null)return;indicator.setTag(0x7f0ffff1,Boolean.TRUE);
        indicator.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){
            if(!rear())return true;int thumb=filterThumb(indicator.getResources()),size=Math.round(thumb*68f/56f);
            ViewGroup.LayoutParams lp=indicator.getLayoutParams();if(lp.width!=size||lp.height!=size){lp.width=lp.height=size;indicator.setLayoutParams(lp);}
            indicator.setTranslationY(-(filterPitch(indicator.getResources())-thumb)/2f);return true;
        }});
    }
    static void styleFilterTitle(final View root){
        final View title=find(root,"beauty_operate_menu_p"),preview=find(root,"v6_frame_layout");
        if(title==null||preview==null||title.getTag(0x7f0ffff0)!=null)return;
        title.setTag(0x7f0ffff0,Boolean.TRUE);
        title.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){
            if(!rear()||!title.isAttachedToWindow())return true;
            int[] pt=new int[2],tt=new int[2];preview.getLocationOnScreen(pt);((View)title.getParent()).getLocationOnScreen(tt);
            title.setPivotX(title.getWidth()/2f);title.setPivotY(title.getHeight()/2f);title.setScaleX(.7f);title.setScaleY(.7f);
            float cx=pt[0]+preview.getWidth()/2f;
            float cy=pt[1]+preview.getHeight()-16*title.getResources().getDisplayMetrics().density-title.getHeight()*.35f;
            title.setTranslationX(cx-(tt[0]+title.getLeft()+title.getWidth()/2f));
            title.setTranslationY(cy-(tt[1]+title.getTop()+title.getHeight()/2f));
            return true;
        }});
    }
    static int filterThumb(android.content.res.Resources res){return Math.round(33.5f*res.getDisplayMetrics().density);}
    static int filterPitch(android.content.res.Resources res){return Math.round(filterThumb(res)*96f/56f);}
    static void styleFilterItem(View root){
        View thumb=find(root,"filter_still_parent");if(thumb==null)return;
        android.content.res.Resources res=root.getResources();int size=filterThumb(res);
        ViewGroup.LayoutParams rp=root.getLayoutParams();if(rp!=null){rp.height=root.getId()==root.getResources().getIdentifier("filter_original","id","com.android.camera")?size:filterPitch(res);root.setLayoutParams(rp);}
        if(root instanceof android.widget.LinearLayout)((android.widget.LinearLayout)root).setGravity(android.view.Gravity.TOP|android.view.Gravity.CENTER_HORIZONTAL);
        ViewGroup.MarginLayoutParams tp=(ViewGroup.MarginLayoutParams)thumb.getLayoutParams();tp.width=tp.height=size;tp.topMargin=0;tp.bottomMargin=0;thumb.setLayoutParams(tp);
        XposedHelpers.callMethod(thumb,"setCornerRadius",size*8f/56f);
        View texture=find(root,"effect_item_realtime");if(texture!=null){ViewGroup.LayoutParams tlp=texture.getLayoutParams();tlp.width=tlp.height=size;texture.setLayoutParams(tlp);}
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){
            View child=((ViewGroup)root).getChildAt(i);
            if(child instanceof android.widget.ImageView&&child.getId()==2131428219){
                android.widget.LinearLayout.LayoutParams bp=(android.widget.LinearLayout.LayoutParams)child.getLayoutParams();
                bp.width=bp.height=Math.round(size*36f/56f);bp.topMargin=Math.round(size*4f/56f);child.setLayoutParams(bp);((android.widget.ImageView)child).setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            }
        }
    }
    static void skinEffect(android.widget.ImageView v){
        try{
            if(!v.getResources().getResourceEntryName(v.getId()).equals("popup_right_tip_image"))return;
            int mode=XposedHelpers.getStaticIntField(XposedHelpers.findClass("com.android.camera.module.Y",loader),"a");
            int icon=(Integer)XposedHelpers.callMethod(XposedHelpers.callMethod(leica,"e"),"v",mode);
            v.setImageResource(icon);v.setBackground(null);v.clearColorFilter();
        }catch(Throwable e){log("SKIN "+e);}
    }
    static View find(View root,String name){int id=root.getResources().getIdentifier(name,"id","com.android.camera");return id==0?null:root.findViewById(id);}
    static void skinPreview(View root){
        View effect=find(root,"popup_right_tip_image");if(effect instanceof android.widget.ImageView)skinEffect((android.widget.ImageView)effect);
        View thumb=find(root,"v9_thumbnail_layout");
        if(thumb!=null)try{XposedHelpers.callMethod(thumb,"setCardBackgroundColor",android.graphics.Color.TRANSPARENT);}catch(Throwable ignored){}
    }
    static View description(View root,String prefix){
        if(root.getContentDescription()!=null&&root.getContentDescription().toString().startsWith(prefix))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View v=description(((ViewGroup)root).getChildAt(i),prefix);if(v!=null)return v;}
        return null;
    }
    static void addPreviewButton(final android.app.Activity activity)throws Exception{
        final View decor=activity.getWindow().getDecorView();
        final ViewGroup host=(ViewGroup)find(decor,"top_bar_container");
        final View bar=find(decor,"top_bar_view");
        if(host==null||bar==null||host.findViewWithTag("local-native-rear-preview")!=null)return;
        View flash=description(bar,"闪光灯");
        if(flash==null||flash.getWidth()==0)return;
        final android.widget.ImageView button=new android.widget.ImageView(activity);
        button.setTag("local-native-rear-preview");button.setContentDescription("背屏预览");
        button.setImageDrawable(new RearPreviewIcon());button.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        button.setPadding(0,0,0,0);button.setBackground(null);
        // Preserve OEM ESP: simultaneous preview, rear touch/hand shutter and native cancellation.
        final View.OnClickListener nativeAction=(View.OnClickListener)allocate("V9.B2");
        button.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
            nativeAction.onClick(v);
        }});
        final android.widget.FrameLayout.LayoutParams lp=new android.widget.FrameLayout.LayoutParams(flash.getWidth(),flash.getHeight());
        host.addView(button,lp);
        host.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){
            try{
                Object c=XposedHelpers.callMethod(XposedHelpers.callStaticMethod(XposedHelpers.findClass("g2.a",loader),"a"),"x",XposedHelpers.findClass("r2.q",loader));
                View f=description(bar,"闪光灯"),m=description(bar,"运动抓拍");
                boolean visible=!rear()&&XposedHelpers.getIntField(c,"a")!=0&&f!=null&&f.isShown();
                button.setVisibility(visible?View.VISIBLE:View.GONE);
                if(visible){
                    // Position from layout coordinates: transformed screen origins move
                    // while the native controls rotate, even with a fixed layout.
                    float[] xy=layoutOriginInHost(f,host);
                    int left=Math.round(xy[0]+f.getWidth()+8*f.getResources().getDisplayMetrics().density);
                    if(m!=null&&m.isShown()){float[] mx=layoutOriginInHost(m,host);left=Math.round(mx[0]+(mx[0]-xy[0]));}
                    int top=Math.round(xy[1]);
                    if(lp.leftMargin!=left||lp.topMargin!=top||lp.width!=f.getWidth()||lp.height!=f.getHeight()){
                        lp.leftMargin=left;lp.topMargin=top;lp.width=f.getWidth();lp.height=f.getHeight();button.setLayoutParams(lp);
                    }
                    button.setPivotX(lp.width/2f);button.setPivotY(lp.height/2f);
                    // Follow the native control's current animation, including 270 -> 0.
                    button.setRotation(f.getRotation());
                    boolean enabled=(Boolean)XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.android.camera.data.data.m",loader),"P");
                    button.setSelected(enabled);button.setContentDescription("背屏预览："+(enabled?"开启":"关闭"));
                }
                View extra=find(decor,"extra_main_top");if(extra!=null)hideOldPreview(extra);
            }catch(Throwable e){button.setVisibility(View.GONE);}
            return true;
        }});
        log("MAIN_PREVIEW OEM ESP action=181, no tile, dynamic control-row position");
    }
    static float[] layoutOriginInHost(View child,ViewGroup host){
        float x=0,y=0;View current=child;
        while(current!=host){
            x+=current.getLeft()+current.getTranslationX();y+=current.getTop()+current.getTranslationY();
            android.view.ViewParent parent=current.getParent();
            if(!(parent instanceof ViewGroup))throw new IllegalArgumentException("Control is outside the top bar host");
            ViewGroup group=(ViewGroup)parent;x-=group.getScrollX();y-=group.getScrollY();current=group;
        }
        return new float[]{x,y};
    }
    static void hideOldPreview(View root){
        if(root.getContentDescription()!=null&&root.getContentDescription().toString().startsWith("背屏预览"))root.setVisibility(View.GONE);
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)hideOldPreview(((ViewGroup)root).getChildAt(i));
    }
    /** OEM phone silhouette only; omit the ESP tile and center it like adjacent Leica glyphs. */
    static final class RearPreviewIcon extends android.graphics.drawable.Drawable {
        final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final android.graphics.Path path;
        final android.graphics.RectF glyph=new android.graphics.RectF();
        RearPreviewIcon(){
            path=(android.graphics.Path)XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.util.PathParser",null),"createPathFromPathData","M23.31,15.332C24.427,15.332 24.985,15.333 25.411,15.55C25.786,15.741 26.092,16.046 26.283,16.421C26.5,16.847 26.5,17.406 26.5,18.521V29.477C26.5,30.593 26.5,31.151 26.283,31.577C26.092,31.952 25.786,32.257 25.411,32.448C24.985,32.665 24.427,32.666 23.31,32.666H16.69C15.573,32.666 15.015,32.665 14.589,32.448C14.214,32.257 13.908,31.952 13.717,31.577C13.5,31.151 13.5,30.593 13.5,29.477L13.5,18.521C13.5,17.406 13.5,16.847 13.717,16.421C13.908,16.046 14.214,15.741 14.589,15.55C15.015,15.333 15.573,15.332 16.69,15.332L23.31,15.332ZM15.614,23.132C15.42,23.132 15.323,23.131 15.249,23.169C15.184,23.202 15.131,23.255 15.098,23.32C15.06,23.395 15.06,23.492 15.06,23.687V29.441C15.06,30.024 15.059,30.315 15.173,30.537C15.273,30.733 15.432,30.892 15.627,30.991C15.849,31.105 16.141,31.105 16.724,31.105H23.275C23.858,31.105 24.15,31.105 24.372,30.991C24.568,30.892 24.726,30.733 24.826,30.537C24.94,30.315 24.94,30.024 24.94,29.441V23.687C24.94,23.492 24.94,23.395 24.902,23.32C24.869,23.255 24.815,23.202 24.75,23.169C24.676,23.131 24.579,23.132 24.385,23.132H15.614ZM16.533,19.664C15.815,19.664 15.234,20.246 15.233,20.964C15.234,21.682 15.815,22.264 16.533,22.264C17.251,22.263 17.833,21.681 17.833,20.964C17.833,20.246 17.251,19.664 16.533,19.664ZM16.515,16.632C15.805,16.642 15.233,17.219 15.233,17.931C15.234,18.649 15.815,19.23 16.533,19.23C17.251,19.23 17.833,18.648 17.833,17.931C17.833,17.219 17.261,16.642 16.552,16.632H16.515Z");
            path.computeBounds(glyph,true);paint.setColor(android.graphics.Color.WHITE);path.setFillType(android.graphics.Path.FillType.EVEN_ODD);
        }
        public void draw(android.graphics.Canvas c){
            android.graphics.Rect b=getBounds();float scale=b.height()*.60f/glyph.height();
            c.save();c.translate(b.exactCenterX()-glyph.centerX()*scale,b.exactCenterY()-glyph.centerY()*scale);c.scale(scale,scale);c.drawPath(path,paint);c.restore();
        }
        public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
        public void setColorFilter(android.graphics.ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
        public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
    static void dump(View v,StringBuilder s,int depth){
        if(depth>30)return;for(int i=0;i<depth;i++)s.append(' ');
        String id="";try{id=v.getResources().getResourceEntryName(v.getId());}catch(Throwable ignored){}
        int[] xy=new int[2];v.getLocationOnScreen(xy);
        s.append(v.getClass().getName()).append(' ').append(id).append(" xy=").append(xy[0]).append(',').append(xy[1]).append(" size=").append(v.getWidth()).append('x').append(v.getHeight()).append(" visibility=").append(v.getVisibility()).append(" desc=").append(v.getContentDescription()).append('\n');
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)dump(((ViewGroup)v).getChildAt(i),s,depth+1);
    }
    static void log(String s){XposedBridge.log("NativeRearFix "+s);}
}

