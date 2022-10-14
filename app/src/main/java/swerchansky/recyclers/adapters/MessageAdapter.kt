package swerchansky.recyclers.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import swerchansky.messenger.Message
import swerchansky.messenger.databinding.PhotoItemBinding
import swerchansky.messenger.databinding.TextItemBinding
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


class MessageAdapter(private val messages: List<Message>) :
   RecyclerView.Adapter<RecyclerView.ViewHolder>() {
   companion object {
      private const val TEXT = 1
      private const val PHOTO = 2
   }

   private val dateFormat: DateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.ENGLISH)

   override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
      if (viewType == TEXT) {
         return TextViewHolder(
            TextItemBinding.inflate(
               LayoutInflater.from(parent.context),
               parent,
               false
            )
         )
      }
      else {
         return PhotoViewHolder(
            PhotoItemBinding.inflate(
               LayoutInflater.from(parent.context),
               parent,
               false
            )
         )
      }
   }

   override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
      val message = messages[position]
      if (getItemViewType(position) == TEXT) {
         val textHolder = holder as TextViewHolder
         textHolder.text.text = message.data.text.text
         textHolder.name.text = message.id.toString()
         textHolder.time.text = dateFormat.format(Date(message.time.toLong()))
      } else {
         val photoHolder = holder as PhotoViewHolder
         photoHolder.photo.setImageBitmap(message.data.image.bitmap)
         photoHolder.name.text = message.id.toString()
         photoHolder.time.text = dateFormat.format(Date(message.time.toLong()))
      }
   }

   override fun getItemCount() = messages.size

   override fun getItemViewType(position: Int): Int {
      return if (messages[position].data.image.link.isNotEmpty()) {
         PHOTO
      } else {
         TEXT
      }
   }

   class TextViewHolder(messageItemBinding: TextItemBinding) :
      RecyclerView.ViewHolder(messageItemBinding.root) {
      val name = messageItemBinding.name
      val text = messageItemBinding.text
      val time = messageItemBinding.time
   }

   class PhotoViewHolder(messageItemBinding: PhotoItemBinding) :
      RecyclerView.ViewHolder(messageItemBinding.root) {
      val name = messageItemBinding.name
      val photo = messageItemBinding.photo
      val time = messageItemBinding.time
   }

}