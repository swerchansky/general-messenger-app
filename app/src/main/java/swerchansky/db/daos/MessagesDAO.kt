package swerchansky.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import swerchansky.db.entities.MessageEntity

@Dao
interface MessagesDAO {
   @Insert
   fun insertMessage(message: MessageEntity)

   @Query("select * from messages")
   fun getAllMessages(): List<MessageEntity>

   @Update
   fun updateMessage(message: MessageEntity)

   @Query("select imageId from messages where id=:id")
   fun getMessageItemIdById(id: Long): Long
}