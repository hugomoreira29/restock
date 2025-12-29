package com.example.restock_pg_dispositivo_moveis.workers

// HUGO MOREIRA - a22402246

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
import com.example.restock_pg_dispositivo_moveis.R
import com.example.restock_pg_dispositivo_moveis.data.SettingsManager
import com.example.restock_pg_dispositivo_moveis.model.Product
import com.example.restock_pg_dispositivo_moveis.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class ExpirationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val settingsManager = SettingsManager(context)

    override suspend fun doWork(): Result {
        return try {
            // ANTES de fazer qualquer coisa, verifica se as notificações estão ativadas
            val notificationsEnabled = settingsManager.notificationsEnabledFlow.first()
            if (!notificationsEnabled) {
                Log.d("ExpirationWorker", "Notificações desativadas. A sair.")
                return Result.success()
            }

            // 1. Verificar se o utilizador está logado
            val userId = auth.currentUser?.uid ?: return Result.failure()

            // 2. Obter o familyId do utilizador
            val userDoc = db.collection("users").document(userId).get().await()
            val user = userDoc.toObject(User::class.java)
            val familyId = user?.familyId ?: return Result.failure()

            // 3. Obter produtos da família
            val productsSnapshot = db.collection("families").document(familyId)
                .collection("products").get().await()
            
            val products = productsSnapshot.toObjects(Product::class.java)

            // 4. Filtrar produtos a expirar nos próximos 3 dias
            val expiringProducts = checkExpiringProducts(products)

            // 5. Enviar notificação se houver produtos
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
            if (product.validade != null) {
                val diff = product.validade!! - currentTime
                diff in 0..threeDaysInMillis
            } else {
                false
            }
        }
    }

    private fun sendNotification(products: List<Product>) {
        val context = applicationContext
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        createNotificationChannel()

        val title = "Atenção! Produtos a expirar"
        val content = if (products.size == 1) {
            "O produto '${products[0].nome}' vai expirar em breve."
        } else {
            "${products.size} produtos vão expirar em breve. Verifique o inventário."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_restock_logo)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Avisos de Validade"
            val descriptionText = "Notificações sobre produtos prestes a expirar"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "expiration_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
