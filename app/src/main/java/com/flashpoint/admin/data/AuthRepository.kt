package com.flashpoint.admin.data

import com.flashpoint.admin.core.NotAdminException
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class AuthRepository(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {
    val hasUser: Boolean get() = auth.currentUser != null

    /** Admin rights come ONLY from the server-set custom claim `admin: true`. */
    suspend fun isAdmin(): Boolean {
        val result = auth.currentUser?.getIdToken(true)?.await() ?: return false
        return result.claims["admin"] == true
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
        if (!isAdmin()) {
            auth.signOut()
            throw NotAdminException()
        }
    }

    fun signOut() = auth.signOut()
}
