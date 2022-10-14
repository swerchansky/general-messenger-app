package swerchansky

import android.content.Context
import android.os.CountDownTimer
import android.view.Gravity
import android.widget.Toast

object ToastUtil {
   fun sendToast(message: String, context: Context, time: Long = 2000) {
      val toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
      toast.setGravity(Gravity.BOTTOM, 0, 0)
      object : CountDownTimer(time, 200) {
         override fun onTick(millisUntilFinished: Long) {
            toast.show()
         }

         override fun onFinish() {
            toast.cancel()
         }
      }.start()
   }
}