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
import com.example.restock.model.Family
import com.example.restock.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** HUGO MOREIRA - a22402246
 * Worker que verifica mensalmente se o Admin da família ainda não atualizou
 * o orçamento do mês e envia uma notificação de lembrete se necessário.
 * Corre a cada 12 horas mas só notifica uma vez por mês.
 */
class BudgetReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val settingsManager = SettingsManager(context)

    override suspend fun doWork(): Result {
        return try {
            val notificationsEnabled = settingsManager.notificationsEnabledFlow.first()
            if (!notificationsEnabled) return Result.success()

            val userId = auth.currentUser?.uid ?: return Result.success()

            val cal = Calendar.getInstance()
            val currentYearMonth = "${cal.get(Calendar.YEAR)}-${String.format("%02d", cal.get(Calendar.MONTH) + 1)}"

            // Verifica se já enviou lembrete este mês
            val lastReminder = settingsManager.lastBudgetReminderFlow.first()
            if (lastReminder == currentYearMonth) return Result.success()

            val userDoc = db.collection("users").document(userId).get().await()
            val familyId = userDoc.toObject(User::class.java)?.familyId ?: return Result.success()

            val familyDoc = db.collection("families").document(familyId).get().await()
            val family = familyDoc.toObject(Family::class.java) ?: return Result.success()

            // Só notifica se o utilizador for Admin da família
            if (family.roles[userId] != "Admin") return Result.success()

            val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                .format(cal.time)
                .replaceFirstChar { it.uppercaseChar() }

            sendNotification(monthName)
            settingsManager.setLastBudgetReminder(currentYearMonth)

            Result.success()
        } catch (e: Exception) {
            Log.e("BudgetReminderWorker", "Erro ao verificar lembrete de orçamento", e)
            Result.retry()
        }
    }

    private fun sendNotification(monthName: String) {
        val context = applicationContext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_budget_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_budget_channel_desc)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_budget)
            .setContentTitle(context.getString(R.string.notif_budget_title))
            .setContentText(context.getString(R.string.notif_budget_content, monthName))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(context.getString(R.string.notif_budget_content, monthName)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "budget_reminder_channel"
        private const val NOTIFICATION_ID = 1003
    }
}
