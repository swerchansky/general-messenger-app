package swerchansky.db.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import swerchansky.db.daos.FailedMessagesDAO
import swerchansky.db.entities.FailedMessagesEntity

@Database(entities = [FailedMessagesEntity::class], version = 1)

abstract class FailedMessagesDatabase : RoomDatabase() {
   abstract fun failedMessagesDAO(): FailedMessagesDAO

   companion object {

      @Volatile
      private var INSTANCE: FailedMessagesDatabase? = null

      fun getDatabase(context: Context): FailedMessagesDatabase {
         if (INSTANCE == null) {
            synchronized(this) {
               INSTANCE = buildDatabase(context)
            }
         }
         return INSTANCE!!
      }

      private fun buildDatabase(context: Context): FailedMessagesDatabase {
         return Room.databaseBuilder(
            context.applicationContext,
            FailedMessagesDatabase::class.java,
            "failedMessages"
         ).build()
      }
   }

}