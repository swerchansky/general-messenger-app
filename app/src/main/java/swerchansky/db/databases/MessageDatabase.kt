package swerchansky.db.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import swerchansky.db.daos.MessagesDAO
import swerchansky.db.entities.MessageEntity

@Database(entities = [MessageEntity::class], version = 1)

abstract class MessageDatabase : RoomDatabase() {
   abstract fun messagesDAO(): MessagesDAO

   companion object {

      @Volatile
      private var INSTANCE: MessageDatabase? = null

      fun getDatabase(context: Context): MessageDatabase {
         if (INSTANCE == null) {
            synchronized(this) {
               INSTANCE = buildDatabase(context)
            }
         }
         return INSTANCE!!
      }

      private fun buildDatabase(context: Context): MessageDatabase {
         return Room.databaseBuilder(
            context.applicationContext,
            MessageDatabase::class.java,
            "messages"
         ).build()
      }
   }

}