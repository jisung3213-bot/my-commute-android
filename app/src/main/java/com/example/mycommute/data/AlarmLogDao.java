package com.example.mycommute.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface AlarmLogDao {
    @Insert void insert(AlarmLogEntity entity);
    @Query("SELECT * FROM alarm_logs WHERE accountId=:account ORDER BY createdAt DESC LIMIT 200")
    List<AlarmLogEntity> latest(String account);
    @Query("UPDATE alarm_logs SET read=1 WHERE accountId=:account")
    void markAllRead(String account);
}
