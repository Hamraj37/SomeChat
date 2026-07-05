package com.samechat37.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.samechat37.models.ChatItemEntity;

import java.util.List;

@Dao
public interface ChatItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ChatItemEntity> chatItems);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ChatItemEntity chatItem);

    @Query("SELECT * FROM chat_items ORDER BY timestamp DESC")
    LiveData<List<ChatItemEntity>> getAllChatItems();

    @Query("DELETE FROM chat_items")
    void deleteAll();
}
