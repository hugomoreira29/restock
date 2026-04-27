package com.example.restock.workers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.restock.R
import com.example.restock.data.SettingsManager
import com.example.restock.model.Product
import com.example.restock.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/** HUGO MOREIRA - a22402246 */
class ExpirationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val settingsManager = SettingsManager(context)

    override suspend fun doWork(): Result {
        return try {
            val notificationsEnabled = settingsManager.notificationsEnabledFlow.first()
            if (!notificationsEnabled) {
                Log.d("ExpirationWorker", "Notificações desativadas.")
                return Result.success()
            }

            // Utilizador não autenticado é um estado válido, não um erro
            val userId = auth.currentUser?.uid ?: return Result.success()

            val userDoc = db.collection("users").document(userId).get().await()
            val familyId = userDoc.toObject(User::class.java)?.familyId

            // Utilizador sem família é um estado válido, não um erro
            if (familyId == null) return Result.success()

            val productsSnapshot = db.collection("families").document(familyId)
                .collection("products").get().await()
            val products = productsSnapshot.toObjects(Product::class.java)

            val expiringProducts = checkExpiringProducts(products)
            if (expiringProducts.isNotEmpty()) {
                sendNotification(expiringProducts)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("ExpirationWorker", "Erro ao verificar validade", e)
            Result.retry()
        }
    }

    private fun checkExpiringProducts(products: List<Product>): List<Product> {
        val currentTime = System.currentTimeMillis()
        val threeDaysInMillis = TimeUnit.DAYS.toMillis(3)

        return products.filter { product ->
            product.validade?.let {
                val diff = it - currentTime
                diff in 0..threeDaysInMillis
            } ?: false
        }
    }

    private fun sendNotification(products: List<Product>) {
        val context = applicationContext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        createNotificationChannel()

        val content = if (products.size == 1) {
            context.getString(R.string.notif_expiry_single, products[0].nome)
        } else {
            context.getString(R.string.notif_expiry_multiple, products.size)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(context.getString(R.string.notif_expiry_title))
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = applicationContext.getString(R.string.notif_channel_desc)
            }
            val notificationManager = applicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "expiration_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
