package swerchansky.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
class MessageEntity (
    @PrimaryKey(autoGenerate = true) val id: Long,
    val from: String,
    val to: String,
    val text: String?,
    val link: String?,
    val time: String,
)