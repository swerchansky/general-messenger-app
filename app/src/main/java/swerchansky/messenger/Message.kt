package swerchansky.messenger

import android.graphics.Bitmap

data class Message(
   val from: String,
   val to: String,
   val data: Data,
   val time: String,
   val id: Long? = null,
)

data class Data(
   val Image: Image? = null,
   val Text: Text? = null,
)

data class Image(
   val link: String = "",
   var bitmap: Bitmap? = null,
)

data class Text(
   val text: String = "",
)