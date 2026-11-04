package com.example.restock.data

/** Regras dos códigos de convite, sem dependências do Firebase. */
object InviteCodes {

    const val VALID_FOR_MS = 24 * 60 * 60 * 1000L

    private const val LENGTH = 6
    private val ALLOWED_CHARS = ('A'..'Z') + ('0'..'9')
    private val VALID_FORMAT = Regex("[A-Z0-9]{$LENGTH}")

    fun generate(): String =
        (1..LENGTH).map { ALLOWED_CHARS.random() }.joinToString("")

    /**
     * Devolve o código no formato guardado, ou null se for inválido.
     * O código serve de ID de documento, por isso nunca pode ter caracteres como "/".
     */
    fun normalize(rawCode: String): String? =
        rawCode.trim().uppercase().takeIf { VALID_FORMAT.matches(it) }

    fun isExpired(expiresAt: Long, now: Long): Boolean = now >= expiresAt
}
