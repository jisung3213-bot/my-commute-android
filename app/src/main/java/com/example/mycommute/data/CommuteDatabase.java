package com.example.mycommute.data;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities={AlarmLogEntity.class},version=2,exportSchema=false)
public abstract class CommuteDatabase extends RoomDatabase {
    private static final Migration MIGRATION_1_2=new Migration(1,2) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DELETE FROM alarm_logs WHERE type LIKE 'test:%'");
        }
    };
    private static volatile CommuteDatabase instance;
    public abstract AlarmLogDao logs();
    public static CommuteDatabase get(Context context) {
        if(instance==null) synchronized(CommuteDatabase.class) {
            if(instance==null) instance=Room.databaseBuilder(context.getApplicationContext(),
                    CommuteDatabase.class,"commute_logs.db").addMigrations(MIGRATION_1_2).build();
        }
        return instance;
    }
}
