package com.heartguard.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
abstract class ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertChatMessage(message: ChatEntity)

    @Query(
        """
        SELECT * FROM chat_messages
        ORDER BY timestamp DESC
        LIMIT 50
        """
    )
    abstract suspend fun getRecentChatMessages(): List<ChatEntity>
}
