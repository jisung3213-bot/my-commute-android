package com.example.mycommute.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities={AlarmLogEntity.class},version=1,exportSchema=false)
public abstract class CommuteDatabase extends RoomDatabase {
    private static volatile CommuteDatabase instance;
    public abstract AlarmLogDao logs();
    public static CommuteDatabase get(Context context) {
        if(instance==null) synchronized(CommuteDatabase.class) {
            if(instance==null) instance=Room.databaseBuilder(context.getApplicationContext(),
                    CommuteDatabase.class,"commute_logs.db").build();
        }
        return instance;
    }
}
