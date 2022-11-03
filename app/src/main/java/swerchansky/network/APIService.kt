package swerchansky.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*
import swerchansky.messenger.Message


interface APIService {
   @GET("1ch")
   fun getLastMessages(
      @Query("lastKnownId") lastKnownId: Long,
      @Query("limit") limit: Long
   ): Call<List<Message>>

   @GET("/img/{link}")
   fun getFullImage(@Path("link") link: String): Call<ResponseBody>

   @Headers("Content-Type: application/json;charset=UTF-8")
   @POST("1ch")
   fun sendTextMessage(@Body json: String): Call<Int>

   @Multipart
   @POST("1ch")
   fun sendImageMessage(
      @Part("msg") json: RequestBody,
      @Part image: MultipartBody.Part
   ): Call<Int>
}