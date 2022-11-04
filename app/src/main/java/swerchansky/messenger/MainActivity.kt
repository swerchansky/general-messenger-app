package swerchansky.messenger

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import swerchansky.Constants.CONFLICT
import swerchansky.Constants.ERROR
import swerchansky.Constants.LARGE_PAYLOAD
import swerchansky.Constants.MESSAGES_LOADED
import swerchansky.Constants.NEW_IMAGE
import swerchansky.Constants.NEW_MESSAGES
import swerchansky.Constants.NOT_FOUND
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
   private var messageService: MessageService? = null
   private var isBound = false
   private var lastPosition: Int = 0

   private val boundServiceConnection: ServiceConnection = object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName, service: IBinder) {
         val binderBridge = service as MessageService.MyBinder
         messageService = binderBridge.getService()
         recycler.adapter = MessageAdapter(this@MainActivity, messageService!!.messages)
         recycler.scrollToPosition(lastPosition)
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
            MESSAGES_LOADED -> messageService?.let {
               recycler.scrollToPosition(it.messages.size - 1)
            }
            SEND_MESSAGE_FAILED, SEND_IMAGE_FAILED, ERROR, NOT_FOUND, CONFLICT, LARGE_PAYLOAD ->
               sendToast(
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
      if (savedInstanceState != null) {
         lastPosition = savedInstanceState.getInt("lastPosition")
      }

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

   override fun onSaveInstanceState(outState: Bundle) {
      super.onSaveInstanceState(outState)
      outState.putInt(
         "lastPosition",
         (recycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
      )
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
      recycler.adapter?.notifyItemRangeInserted(initialSize, updatedSize)
      val manager = recycler.layoutManager as LinearLayoutManager
      if (
         initialSize != updatedSize &&
         manager.findLastVisibleItemPosition() == initialSize - 1
      ) {
         recycler.smoothScrollToPosition(updatedSize - 1)
      }
   }

   private fun updateMessageImage(position: Int) {
      recycler.adapter?.notifyItemChanged(position)
   }
}
