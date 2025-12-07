package com.example.restock_pg_dispositivo_moveis.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class User(
    var uid: String = "",
    var name: String = "",
    var email: String = "",
    var photoUrl: String? = null,
    @ServerTimestamp
    var createdAt: Date? = null
)
