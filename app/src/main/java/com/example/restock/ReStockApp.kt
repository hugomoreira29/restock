package com.example.restock

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Configuração global da aplicação.
 * O App Check fica aqui para estar ativo antes de qualquer acesso ao Firebase,
 * incluindo os workers, que correm sem passar pela SplashActivity.
 */
class ReStockApp : Application() {

    override fun onCreate() {
        super.onCreate()
        installAppCheck()
        registerActivityLifecycleCallbacks(EdgeToEdgeInsets)
    }

    /**
     * A partir do Android 16 as apps são sempre desenhadas de ponta a ponta.
     * Reserva o espaço das barras do sistema, do recorte do ecrã e do teclado
     * em todas as atividades, para que o conteúdo nunca fique por baixo deles.
     */
    private object EdgeToEdgeInsets : ActivityLifecycleCallbacks {

        override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
            val content = activity.findViewById<View>(android.R.id.content) ?: return
            ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime()
                )
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                WindowInsetsCompat.CONSUMED
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
