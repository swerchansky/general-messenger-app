package swerchansky.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import swerchansky.db.entities.FailedMessagesEntity

@Dao
interface FailedMessagesDAO {
   @Insert
   fun insertFailedMessage(message: FailedMessagesEntity)

   @Query("select * from failedMessages")
   fun getAllFailedMessages(): List<FailedMessagesEntity>

   @Delete
   fun deleteFailedMessage(message: FailedMessagesEntity)
}