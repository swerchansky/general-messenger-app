package swerchansky.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import swerchansky.messenger.Message
import swerchansky.messenger.databinding.MessageItemBinding
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


class Adapter(private val context: Context, private val messages: List<Message>) :
   RecyclerView.Adapter<Adapter.ViewHolder>() {
   private var messageItem: MessageItemBinding =
      MessageItemBinding.inflate(LayoutInflater.from(context))
   private val obj: DateFormat = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.ENGLISH)

   override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
      return ViewHolder(
         MessageItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
         )
      )
   }

   override fun onBindViewHolder(holder: ViewHolder, position: Int) {
      val message = messages[position]

      holder.text.text = message.data.text.text
      holder.name.text = message.from
      holder.time.text = obj.format(Date(message.time.toLong()))
   }

   override fun getItemCount() = messages.size

   class ViewHolder(messageItemBinding: MessageItemBinding) :
      RecyclerView.ViewHolder(messageItemBinding.root) {
      val name = messageItemBinding.name
      val text = messageItemBinding.text
      val time = messageItemBinding.time
   }
}