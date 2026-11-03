package io.github.sumyabazar1.weatherline;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.AsyncTask;

/** Fetches the forecast in the background, and only when there is a network to fetch it over. */
public final class WeatherRefreshJob extends JobService {

    private static final int JOB_ID = 4711;

    static void schedule(Context ctx) {
        JobScheduler js = ctx.getSystemService(JobScheduler.class);
        if (js == null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(ctx, WeatherRefreshJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(30 * 60 * 1000L)
                .setPersisted(true)
                .build();
        js.schedule(job);
    }

    /** One refresh now, for a cache the provider found stale. Cheap enough to run on any query. */
    static void runNow(Context ctx) {
        JobScheduler js = ctx.getSystemService(JobScheduler.class);
        if (js == null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID + 1, new ComponentName(ctx, WeatherRefreshJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(0)
                .build();
        js.schedule(job);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Context ctx = getApplicationContext();
        if (!online(ctx)) {
            return false;               // the job's network requirement normally prevents this
        }
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                return OpenMeteo.fetch(ctx.getString(R.string.place),
                        ctx.getString(R.string.latitude),
                        ctx.getString(R.string.longitude),
                        ctx.getString(R.string.timezone));
            }

            @Override
            protected void onPostExecute(String line) {
                if (line != null && !line.isEmpty()) WeatherCache.save(ctx, line);
                jobFinished(params, false);
            }
        }.execute();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    private static boolean online(Context ctx) {
        ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
        if (cm == null) return false;
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
