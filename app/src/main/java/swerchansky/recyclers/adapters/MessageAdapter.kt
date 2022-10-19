package swerchansky.recyclers.adapters

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.RecyclerView
import swerchansky.Constants.USERNAME
import swerchansky.messenger.FullScreenImageActivity
import swerchansky.messenger.Message
import swerchansky.messenger.R
import swerchansky.messenger.databinding.PhotoItemBinding
import swerchansky.messenger.databinding.TextItemBinding
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


class MessageAdapter(private val context: Context, private val messages: List<Message>) :
   RecyclerView.Adapter<RecyclerView.ViewHolder>() {
   companion object {
      private const val TEXT = 1
      private const val PHOTO = 2
      private const val MY_TEXT = 3
      private const val MY_PHOTO = 4
   }

   private val dateFormat: DateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ENGLISH)

   override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
      if (viewType == TEXT || viewType == MY_TEXT) {
         return TextViewHolder(
            TextItemBinding.inflate(
               LayoutInflater.from(parent.context),
               parent,
               false
            )
         )
      } else {
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
      when (getItemViewType(position)) {
          TEXT -> {
             val textHolder = holder as TextViewHolder
             setTextMessage(textHolder, message)
          }
          PHOTO -> {
             val photoHolder = holder as PhotoViewHolder
             setPhotoMessage(photoHolder, message, position)
          }
          MY_TEXT -> {
             val textHolder = holder as TextViewHolder
             setTextMessage(textHolder, message)
             textHolder.message.background =
                AppCompatResources.getDrawable(context, R.drawable.my_message_background)
          }
          MY_PHOTO -> {
             val photoHolder = holder as PhotoViewHolder
             setPhotoMessage(photoHolder, message, position)
             photoHolder.message.background =
                AppCompatResources.getDrawable(context, R.drawable.my_message_background)
          }
      }
   }

   private fun setPhotoMessage(photoHolder: PhotoViewHolder, message: Message, position: Int) {
      photoHolder.photo.setImageBitmap(message.data.Image?.bitmap)
      photoHolder.name.text = message.from
      photoHolder.time.text = dateFormat.format(Date(message.time.toLong()))
      photoHolder.photoMessage.setOnClickListener {
         val intent = Intent(context, FullScreenImageActivity::class.java)
         intent.putExtra("messagePosition", position)
         context.startActivity(intent)
      }
   }

   private fun setTextMessage(holder: TextViewHolder, message: Message) {
      holder.text.text = message.data.Text?.text ?: ""
      holder.name.text = message.from
      holder.time.text = dateFormat.format(Date(message.time.toLong()))
   }

   override fun getItemCount() = messages.size

   override fun getItemViewType(position: Int): Int {
      return if (messages[position].data.Image?.link?.isNotEmpty() == true) {
         if (messages[position].from == USERNAME) {
            MY_PHOTO
         } else {
            PHOTO
         }
      } else {
         if (messages[position].from == USERNAME) {
            MY_TEXT
         } else {
            TEXT
         }
      }
   }

   class TextViewHolder(messageItemBinding: TextItemBinding) :
      RecyclerView.ViewHolder(messageItemBinding.root) {
      val message = messageItemBinding.textMessage
      val name = messageItemBinding.name
      val text = messageItemBinding.text
      val time = messageItemBinding.time
   }

   class PhotoViewHolder(messageItemBinding: PhotoItemBinding) :
      RecyclerView.ViewHolder(messageItemBinding.root) {
      val message = messageItemBinding.photoMessage
      val name = messageItemBinding.name
      val photo = messageItemBinding.photo
      val time = messageItemBinding.time
      val photoMessage = messageItemBinding.photoMessage
   }

}