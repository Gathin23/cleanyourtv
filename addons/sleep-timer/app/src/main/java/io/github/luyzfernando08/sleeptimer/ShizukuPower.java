package io.github.luyzfernando08.sleeptimer;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.util.concurrent.atomic.AtomicBoolean;
import rikka.shizuku.Shizuku;

/** A fresh asynchronous binding also works when AlarmReceiver starts a previously killed app process. */
final class ShizukuPower {
    private ShizukuPower() { }
    static boolean running() {
        try{return Shizuku.pingBinder()&&Shizuku.getUid()==2000;}catch(RuntimeException e){return false;}
    }
    static boolean ready() {
        try{return running()&&Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;}catch(RuntimeException e){return false;}
    }
    static void connect(android.app.Activity activity) {
        if(running()) {if(!ready())Shizuku.requestPermission(71);return;}
        Intent manager=activity.getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if(manager!=null)activity.startActivity(manager);
    }
    static void setAwake(Context context,boolean awake,Runnable finished) {
        if(!ready()){android.util.Log.w("SleepPower","Shizuku is not ready");finished.run();return;}
        Handler handler=new Handler(Looper.getMainLooper());
        Shizuku.UserServiceArgs args=new Shizuku.UserServiceArgs(new ComponentName(context,PowerUserService.class))
                .tag("sleep-timer-power").processNameSuffix("power").daemon(true).version(1);
        AtomicBoolean completed=new AtomicBoolean();
        final ServiceConnection[] holder=new ServiceConnection[1];
        Runnable complete=()->{
            if(!completed.compareAndSet(false,true))return;
            try{Shizuku.unbindUserService(args,holder[0],false);}catch(RuntimeException ignored){ }
            finished.run();
        };
        Runnable timeout=()->{android.util.Log.w("SleepPower","Shizuku power request timed out");complete.run();};
        holder[0]=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName name,IBinder binder){
                if(completed.get())return;
                new Thread(()->{
                    try {
                        if(completed.get())return;
                        IPowerControl power=IPowerControl.Stub.asInterface(binder);
                        boolean ok=power.uid()==2000&&!completed.get()&&power.setAwake(awake);
                        android.util.Log.i("SleepPower","awake="+awake+" success="+ok);
                    }catch(RemoteException|RuntimeException e){android.util.Log.e("SleepPower","Shizuku power failed",e);}
                    handler.post(()->{handler.removeCallbacks(timeout);complete.run();});
                },"tv-power").start();
            }
            @Override public void onServiceDisconnected(ComponentName name){handler.removeCallbacks(timeout);complete.run();}
        };
        handler.postDelayed(timeout,9000);
        try{Shizuku.bindUserService(args,holder[0]);}catch(RuntimeException e){handler.removeCallbacks(timeout);complete.run();}
    }
}
