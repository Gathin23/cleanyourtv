package io.github.luyzfernando08.sleeptimer;

import android.os.Process;
import java.io.*;
import java.util.concurrent.TimeUnit;

/** Runs as ADB shell in Shizuku. Only fixed power commands are exposed, never arbitrary shell input. */
public final class PowerUserService extends IPowerControl.Stub {
    public PowerUserService() { }
    @Override public int uid() { return Process.myUid(); }
    @Override public void destroy() { System.exit(0); }
    private String command(String... args) throws Exception {
        java.lang.Process process=new ProcessBuilder(args).redirectErrorStream(true).start();
        if(!process.waitFor(3,TimeUnit.SECONDS)){process.destroy();throw new IOException("Power command timed out");}
        String line;
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream()))){line=reader.readLine();}
        if(process.exitValue()!=0)throw new IOException("Power command failed");
        return line==null?"":line.trim();
    }
    @Override public synchronized boolean setAwake(boolean awake) {
        if(uid()!=2000)return false; // This integration intentionally uses ADB, never root.
        try {
            boolean nativeStandby="true".equals(command("/system/bin/settings","get","global","fake_standby_switch"));
            if(nativeStandby) {
                String state=command("/system/bin/settings","get","global","IS_FAKE_STANDBY_STATUS");
                if(!"0".equals(state)&&!"1".equals(state))return false;
                if(("0".equals(state))==awake)return true;
                command("/system/bin/input","keyevent","26");
                Thread.sleep(600);
                String after=command("/system/bin/settings","get","global","IS_FAKE_STANDBY_STATUS");
                return (awake?"0":"1").equals(after);
            }
            command("/system/bin/input","keyevent",awake?"224":"223");
            return true;
        } catch(Exception e) {android.util.Log.e("SleepPower","Power command failed",e);return false;}
    }
}
