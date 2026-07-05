package com.cevague.vindex.search

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache mémoire des résultats de recherche ordonnés, adressés par un `sessionId`.
 *
 * Permet à `ViewerSource.Search` de transporter une **référence** (le sessionId)
 * plutôt que la liste d'ids elle-même, qui dépasserait la limite de transaction
 * Binder (~1 Mo) sur de gros résultats. Indispensable aussi en phase 2 : un
 * classement vectoriel n'est pas ré-exprimable en SQL, le viewer doit retrouver
 * l'ordre exact du résultat sans ré-exécuter la requête.
 *
 * LRU borné (seules les dernières recherches sont conservées) et volatil : le
 * contenu est perdu à la mort du process. Les consommateurs doivent gérer un
 * `get` renvoyant `null` (session évincée ou process recréé).
 */
@Singleton
class SearchSessionRepository @Inject constructor() {

    private val sessions = object : LinkedHashMap<String, List<Long>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, List<Long>>): Boolean =
            size > MAX_SESSIONS
    }

    /** Mémorise une liste ordonnée d'ids et retourne son identifiant de session. */
    @Synchronized
    fun put(photoIds: List<Long>): String {
        val sessionId = UUID.randomUUID().toString()
        sessions[sessionId] = photoIds
        return sessionId
    }

    /** Liste ordonnée associée, ou `null` si la session a expiré / été évincée. */
    @Synchronized
    fun get(sessionId: String): List<Long>? = sessions[sessionId]

    private companion object {
        const val MAX_SESSIONS = 4
    }
}
