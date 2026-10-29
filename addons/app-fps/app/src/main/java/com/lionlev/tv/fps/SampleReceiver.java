package com.lionlev.tv.fps;
import android.content.*;
public final class SampleReceiver extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){if("com.lionlev.tv.fps.STOP".equals(i.getAction())){c.stopService(new Intent(c,FpsService.class));return;}if(!"com.lionlev.tv.fps.SAMPLE".equals(i.getAction()))return;FpsService s=FpsService.current;if(s!=null && !s.localMode()){double value=-1;try{value=Double.parseDouble(i.getStringExtra("fps"));}catch(RuntimeException ignored){}s.sample(value,i.getStringExtra("app"));}}
}