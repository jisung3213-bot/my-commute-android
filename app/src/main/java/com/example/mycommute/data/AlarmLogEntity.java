package com.example.mycommute.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName="alarm_logs")
public class AlarmLogEntity {
    @PrimaryKey(autoGenerate=true) public long id;
    public long createdAt;
    public String accountId;
    public String type;
    public String title;
    public String message;
    public boolean important;
    public boolean read;
}
