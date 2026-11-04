package com.example.restock.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.restock.R

/** Abre a política de privacidade no navegador do dispositivo. */
fun Context.openPrivacyPolicy() {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_policy_url)))
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, getString(R.string.open_link_error), Toast.LENGTH_SHORT).show()
    }
}
