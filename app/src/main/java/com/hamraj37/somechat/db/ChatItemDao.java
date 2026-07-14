package com.hamraj37.somechat.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.hamraj37.somechat.models.ChatItemEntity;

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

    @Query("DELETE FROM chat_items WHERE uid = :uid")
    void deleteByUid(String uid);
}
