package swerchansky.messenger

import android.graphics.Bitmap

data class Message(
   val id: Long,
   val data: Data,
   val from: String,
   val to: String,
   val time: String,
)

data class Data(
   val image: Image = Image(""),
   val text: Text = Text(""),
)

data class Image(
   val link: String,
   var bitmap: Bitmap? = null,
)

data class Text(
   val text: String,
)