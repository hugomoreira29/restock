package com.example.restock.workers

// Android - para permissões, notificações e contexto da aplicação
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

// AndroidX - para construir e enviar notificações e o Worker em coroutines
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

// Classes internas da aplicação - recursos, gestor de definições e modelos
import com.example.restock.R
import com.example.restock.data.SettingsManager
import com.example.restock.model.Product
import com.example.restock.model.User

// Firebase - autenticação e base de dados
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// Coroutines - para ler as preferências e aguardar as operações do Firestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

// Java - para calcular o intervalo de três dias em milissegundos
import java.util.concurrent.TimeUnit

/** HUGO MOREIRA - a22402246
 * Worker responsável por verificar periodicamente os produtos a expirar.
 * Corre em background através do WorkManager, verifica se as notificações
 * estão ativas, filtra os produtos com validade nos próximos 3 dias
 * e envia uma notificação ao utilizador caso existam produtos a expirar.
 */
class ExpirationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val settingsManager = SettingsManager(context)

    /**
     * Ponto de entrada do Worker. Executa todas as verificações em sequência:
     * preferências, autenticação, dados da família, filtragem e notificação.
     * Devolve [Result.success] se correr bem, [Result.retry] em caso de erro.
     */
    override suspend fun doWork(): Result {
        return try {
            // Verifica primeiro se as notificações estão ativas nas preferências
            val notificationsEnabled = settingsManager.notificationsEnabledFlow.first()
            if (!notificationsEnabled) {
                Log.d("ExpirationWorker", "Notificações desativadas. A sair.")
                return Result.success()
            }

            // Verifica se há um utilizador autenticado
            val userId = auth.currentUser?.uid ?: return Result.failure()

            // Obtém o familyId do documento do utilizador no Firestore
            val userDoc = db.collection("users").document(userId).get().await()
            val familyId = userDoc.toObject(User::class.java)?.familyId ?: return Result.failure()

            // Obtém todos os produtos do inventário da família
            val productsSnapshot = db.collection("families").document(familyId)
                .collection("products").get().await()
            val products = productsSnapshot.toObjects(Product::class.java)

            // Filtra os produtos que expiram nos próximos 3 dias
            val expiringProducts = checkExpiringProducts(products)

            // Envia notificação apenas se houver produtos a expirar
            if (expiringProducts.isNotEmpty()) {
                sendNotification(expiringProducts)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("ExpirationWorker", "Erro ao verificar validade", e)
            Result.retry()
        }
    }

    /**
     * Filtra os produtos cuja validade se encontra entre agora e os próximos 3 dias.
     * Ignora produtos sem data de validade definida.
     */
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

    /**
     * Envia uma notificação ao utilizador com os produtos a expirar.
     * Verifica a permissão de notificações em Android 13 ou superior antes de enviar.
     */
    private fun sendNotification(products: List<Product>) {
        val context = applicationContext

        // Em Android 13+, verifica se a permissão de notificações foi concedida
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        createNotificationChannel()

        // Adapta o texto da notificação consoante o número de produtos a expirar
        val content = if (products.size == 1) {
            "O produto '${products[0].nome}' vai expirar em breve."
        } else {
            "${products.size} produtos vão expirar em breve. Verifique o inventário."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_restock_logo)
            .setContentTitle("Atenção! Produtos a expirar")
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /**
     * Cria o canal de notificações necessário para Android 8.0 (Oreo) ou superior.
     * Caso o canal já exista, a operação é ignorada pelo sistema.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Avisos de Validade",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificações sobre produtos prestes a expirar"
            }
            val notificationManager = applicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "expiration_channel"  // ID do canal de notificações
        private const val NOTIFICATION_ID = 1001              // ID único da notificação
    }
}