package com.example.mycommute.data;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;
import com.example.mycommute.alarms.AlertLabMode;
import com.example.mycommute.model.Routine;
import java.util.HashSet;
import java.util.Set;

public final class RouteRefreshJob extends JobService {
    private final Set<JobParameters> running=new HashSet<>();

    public static void enqueue(Context context,String direction,long windowEnd){
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);
        if(scheduler==null)return;
        int id=jobId(direction);
        JobInfo pending=scheduler.getPendingJob(id);
        if(pending!=null&&pending.getExtras().getLong("windowEnd")==windowEnd
                &&Session.accountId(context).equals(pending.getExtras().getString("account")))return;
        PersistableBundle extras=new PersistableBundle();
        extras.putString("account",Session.accountId(context));
        extras.putString("direction",direction);
        extras.putLong("windowEnd",windowEnd);
        scheduler.schedule(new JobInfo.Builder(id,new ComponentName(context,RouteRefreshJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setExtras(extras).build());
    }

    public static void cancel(Context context){
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);
        if(scheduler!=null)for(String direction:RoutineStore.IDS)scheduler.cancel(jobId(direction));
    }

    private static int jobId(String direction){return RoutineStore.RETURN.equals(direction)?652:651;}

    @Override public boolean onStartJob(JobParameters params){
        PersistableBundle extras=params.getExtras();
        long end=extras.getLong("windowEnd");
        if(AlertLabMode.enabled(this)||!Session.signedIn(this)||System.currentTimeMillis()>=end
                ||!Session.accountId(this).equals(extras.getString("account")))return false;
        Routine routine=RoutineStore.read(this,RoutineStore.defaultId(this,extras.getString("direction")));
        running.add(params);
        boolean started=PinnedRouteRefresh.request(this,routine,end,ok->{
            if(running.remove(params))jobFinished(params,false);
        });
        if(!started)running.remove(params);
        return started;
    }

    @Override public boolean onStopJob(JobParameters params){
        running.remove(params);
        return false;
    }
}
