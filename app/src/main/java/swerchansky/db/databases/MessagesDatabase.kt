package swerchansky.db.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import swerchansky.db.daos.MessagesDAO
import swerchansky.db.entities.MessageEntity

@Database(entities = [MessageEntity::class], version = 1)

abstract class MessagesDatabase : RoomDatabase() {
   abstract fun messagesDAO(): MessagesDAO

   companion object {

      @Volatile
      private var INSTANCE: MessagesDatabase? = null

      fun getDatabase(context: Context): MessagesDatabase {
         if (INSTANCE == null) {
            synchronized(this) {
               INSTANCE = buildDatabase(context)
            }
         }
         return INSTANCE!!
      }

      private fun buildDatabase(context: Context): MessagesDatabase {
         return Room.databaseBuilder(
            context.applicationContext,
            MessagesDatabase::class.java,
            "messages"
         ).build()
      }
   }

}