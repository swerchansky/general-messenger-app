package swerchansky.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import swerchansky.db.entities.MessageEntity

@Dao
interface MessagesDAO {
   @Insert
   fun insertMessage(message: MessageEntity)

   @Query("select * from messages")
   fun getAllMessages(): List<MessageEntity>

   @Query("select imageId from messages where id=:id")
   fun getMessageItemIdById(id: Long): Long
}