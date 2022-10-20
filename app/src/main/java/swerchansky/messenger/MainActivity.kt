package swerchansky.messenger

import android.content.*
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import swerchansky.Constants.ERROR
import swerchansky.Constants.MESSAGES_LOADED
import swerchansky.Constants.NEW_IMAGE
import swerchansky.Constants.NEW_MESSAGES
import swerchansky.Constants.SEND_IMAGE
import swerchansky.Constants.SEND_IMAGE_FAILED
import swerchansky.Constants.SEND_MESSAGE
import swerchansky.Constants.SEND_MESSAGE_FAILED
import swerchansky.Constants.SERVER_ERROR
import swerchansky.ToastUtil.sendToast
import swerchansky.messenger.databinding.ActivityMainBinding
import swerchansky.recyclers.adapters.MessageAdapter
import swerchansky.services.MessageService


class MainActivity : AppCompatActivity() {
   companion object {
      const val TAG = "MainActivity"
      const val MESSAGE_SERVICE_TAG = "MessageService"
   }

   private lateinit var mainActivity: ActivityMainBinding
   private lateinit var recycler: RecyclerView
   private lateinit var messageServiceIntent: Intent
   private val mainHandler = Handler(Looper.getMainLooper())
   private var messageService: MessageService? = null
   private var isBound = false

   private val boundServiceConnection: ServiceConnection = object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName, service: IBinder) {
         val binderBridge = service as MessageService.MyBinder
         messageService = binderBridge.getService()
         recycler.adapter = MessageAdapter(this@MainActivity, messageService!!.messages)
//         recycler.scrollToPosition(messageService!!.messages.size - 1)
         isBound = true
      }

      override fun onServiceDisconnected(name: ComponentName) {
         isBound = false
         messageService = null
      }
   }

   private val messageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent) {
         when (intent.getIntExtra("type", -1)) {
            NEW_MESSAGES -> updateMessages(
               intent.getIntExtra("initialSize", 0),
               intent.getIntExtra("updatedSize", 0)
            )
            NEW_IMAGE -> updateMessageImage(
               intent.getIntExtra("position", -1)
            )
            MESSAGES_LOADED -> recycler.scrollToPosition(messageService!!.messages.size - 1)
            SEND_MESSAGE_FAILED -> sendToast(
               "server error with code: ${intent.getStringExtra("text")}",
               this@MainActivity
            )
            SEND_IMAGE_FAILED -> sendToast(
               "Cannot send image: ${intent.getStringExtra("text")}",
               this@MainActivity
            )
            ERROR -> sendToast(
               "${intent.getStringExtra("text")}",
               this@MainActivity
            )
            SERVER_ERROR -> sendToast(
               "Failed to connect to server",
               this@MainActivity
            )
         }
      }
   }

   private var launchImageChoose = registerForActivityResult(
      ActivityResultContracts.StartActivityForResult()
   ) { result: ActivityResult ->
      if (result.resultCode == RESULT_OK) {
         val data = result.data
         data?.data?.let { selectedPhotoUri ->
            val intent = Intent(TAG)
            intent.putExtra("type", SEND_IMAGE)
            intent.putExtra("uri", selectedPhotoUri.toString())
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
         }
      }
   }

   override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      mainActivity = ActivityMainBinding.inflate(layoutInflater)
      setContentView(mainActivity.root)

      initRecycler()
      initSendListener()

      messageServiceIntent = Intent(this, MessageService::class.java)
      startService(messageServiceIntent)
      bindService(messageServiceIntent, boundServiceConnection, BIND_AUTO_CREATE)

      mainActivity.attachmentButton.setOnClickListener {
         imageChoose()
      }

      mainActivity.scrollButton.setOnClickListener {
         if (mainActivity.scrollButton.visibility == View.VISIBLE) {
            recycler.adapter?.let {
               recycler.smoothScrollToPosition(it.itemCount - 1)
            }
            mainActivity.scrollButton.visibility = View.INVISIBLE
         }
      }
//      deleteDatabase("messages") // TODO: remove
   }

   override fun onStart() {
      super.onStart()
      LocalBroadcastManager.getInstance(this)
         .registerReceiver(messageReceiver, IntentFilter(MESSAGE_SERVICE_TAG))
   }

   override fun onStop() {
      super.onStop()
      LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver)
   }

   override fun onDestroy() {
      super.onDestroy()
      if (isBound) {
         unbindService(boundServiceConnection)
      }
   }

   private fun initSendListener() {
      mainActivity.sendButton.setOnClickListener {
         sendTextMessage(mainActivity.messageField.text.toString())
      }
   }

   private fun initRecycler() {
      recycler = mainActivity.messagesRecycler

      val manager = LinearLayoutManager(this)
      manager.isSmoothScrollbarEnabled = true

      recycler.apply {
         layoutManager = manager
         adapter = MessageAdapter(this@MainActivity, mutableListOf())
      }

      recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
         override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            recycler.adapter?.let {
               if (dy < 0) {
                  mainActivity.scrollButton.visibility = View.VISIBLE
               } else {
                  mainActivity.scrollButton.visibility = View.INVISIBLE
               }
            }
         }
      })
   }

   private fun imageChoose() {
      val intent = Intent()
      intent.type = "image/*"
      intent.action = Intent.ACTION_GET_CONTENT
      launchImageChoose.launch(intent)
   }

   private fun sendTextMessage(text: String) {
      mainActivity.messageField.setText("")
      val intent = Intent(TAG)
      intent.putExtra("type", SEND_MESSAGE)
      intent.putExtra("text", text)
      LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
   }

   private fun updateMessages(initialSize: Int, updatedSize: Int) {
      mainHandler.post {
         recycler.post {
            recycler.adapter?.notifyItemRangeInserted(initialSize, updatedSize)
         }
         val manager = recycler.layoutManager as LinearLayoutManager
         if (
            initialSize != updatedSize &&
            manager.findLastVisibleItemPosition() == initialSize - 1
         ) {
            recycler.smoothScrollToPosition(updatedSize - 1)
         }
      }
   }

   private fun updateMessageImage(position: Int) {
      recycler.adapter?.notifyItemChanged(position)
   }
}
