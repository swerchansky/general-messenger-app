package swerchansky.recyclers.adapters

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import androidx.recyclerview.widget.RecyclerView
import swerchansky.Constants.USERNAME
import swerchansky.messenger.FullScreenImageActivity
import swerchansky.messenger.Message
import swerchansky.messenger.R
import swerchansky.messenger.databinding.MyPhotoItemBinding
import swerchansky.messenger.databinding.MyTextItemBinding
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

   private val dateFormat: DateFormat = SimpleDateFormat("HH:mm  dd MMM", Locale.ENGLISH)

   override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
      when (viewType) {
         TEXT -> {
            return TextViewHolder(
               TextItemBinding.inflate(
                  LayoutInflater.from(parent.context),
                  parent,
                  false
               )
            )
         }
         MY_TEXT -> {
            return MyTextViewHolder(
               MyTextItemBinding.inflate(
                  LayoutInflater.from(parent.context),
                  parent,
                  false
               )
            )
         }
         MY_PHOTO -> {
            return MyPhotoViewHolder(
               MyPhotoItemBinding.inflate(
                  LayoutInflater.from(parent.context),
                  parent,
                  false
               )
            )
         }
         else -> {
            return PhotoViewHolder(
               PhotoItemBinding.inflate(
                  LayoutInflater.from(parent.context),
                  parent,
                  false
               )
            )
         }
      }
   }

   override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
      val message = messages[position]
      when (getItemViewType(position)) {
         TEXT -> {
            val textHolder = holder as TextViewHolder
            textHolder.text.text = message.data.Text?.text ?: ""
            textHolder.name.text = message.from
            textHolder.time.text = dateFormat.format(Date(message.time.toLong()))
         }
         PHOTO -> {
            val photoHolder = holder as PhotoViewHolder
            if (message.data.Image?.bitmap == null) {
               photoHolder.photo.setImageBitmap(
                  AppCompatResources.getDrawable(
                     context,
                     R.drawable.loading
                  )?.toBitmap(400, 400)
               )
            } else {
               photoHolder.photo.setImageBitmap(message.data.Image.bitmap)
            }
            photoHolder.name.text = message.from
            photoHolder.time.text = dateFormat.format(Date(message.time.toLong()))
            photoHolder.photoMessage.setOnClickListener {
               if (message.data.Image?.bitmap != null) {
                  val intent = Intent(context, FullScreenImageActivity::class.java)
                  intent.putExtra("messagePosition", position)
                  context.startActivity(intent)
               }
            }
         }
         MY_TEXT -> {
            val textHolder = holder as MyTextViewHolder
            textHolder.text.text = message.data.Text?.text ?: ""
            if (message.time.isEmpty()) {
               textHolder.time.text = "....."
            } else {
               textHolder.time.text = dateFormat.format(Date(message.time.toLong()))
            }
         }
         MY_PHOTO -> {
            val photoHolder = holder as MyPhotoViewHolder
            if (message.data.Image?.bitmap == null) {
               photoHolder.photo.setImageBitmap(
                  AppCompatResources.getDrawable(
                     context,
                     R.drawable.loading
                  )?.toBitmap(400, 400)
               )
            } else {
               photoHolder.photo.setImageBitmap(message.data.Image.bitmap)
            }
            if (message.time.isEmpty()) {
               photoHolder.time.text = "....."
            } else {
               photoHolder.time.text = dateFormat.format(Date(message.time.toLong()))
            }
            photoHolder.photoMessage.setOnClickListener {
               if (message.data.Image?.bitmap != null) {
                  val intent = Intent(context, FullScreenImageActivity::class.java)
                  intent.putExtra("messagePosition", position)
                  context.startActivity(intent)
               }
            }
         }
      }
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

   class MyTextViewHolder(messageItemBinding: MyTextItemBinding) :
      RecyclerView.ViewHolder(messageItemBinding.root) {
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

   class MyPhotoViewHolder(messageItemBinding: MyPhotoItemBinding) :
      RecyclerView.ViewHolder(messageItemBinding.root) {
      val photo = messageItemBinding.photo
      val time = messageItemBinding.time
      val photoMessage = messageItemBinding.photoMessage
   }

}