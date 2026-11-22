package com.example.mycommute.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;

public final class AlarmLogRepository {
    public interface Callback { void onLoaded(List<AlarmLogEntity> rows); }
    private static final ExecutorService DB=Executors.newSingleThreadExecutor();
    private AlarmLogRepository() { }
    public static void write(Context context,String type,String title,String message,boolean important,Runnable finished) {
        Context app=context.getApplicationContext();
        AlarmLogEntity e=new AlarmLogEntity();e.accountId=Session.accountId(app);e.type=type;
        e.title=title;e.message=message;e.important=important;e.createdAt=System.currentTimeMillis();
        DB.execute(() -> {
            try { CommuteDatabase.get(app).logs().insert(e); }
            finally { if(finished!=null) finished.run(); }
        });
    }
    public static void list(Context context,Callback callback) {
        Context app=context.getApplicationContext();String account=Session.accountId(context);
        DB.execute(() -> { List<AlarmLogEntity> logs=CommuteDatabase.get(app).logs().latest(account);
            new Handler(Looper.getMainLooper()).post(() -> callback.onLoaded(logs)); });
    }
    public static void markRead(Context context,Runnable done) {
        Context app=context.getApplicationContext();String account=Session.accountId(context);
        DB.execute(() -> { CommuteDatabase.get(app).logs().markAllRead(account);
            new Handler(Looper.getMainLooper()).post(done); });
    }
    public static void countByTypePrefix(Context context, String prefix, IntConsumer callback) {
        Context app = context.getApplicationContext();
        String account = Session.accountId(app);
        DB.execute(() -> {
            int count = CommuteDatabase.get(app).logs().countByTypePrefix(account, prefix);
            new Handler(Looper.getMainLooper()).post(() -> callback.accept(count));
        });
    }
}
