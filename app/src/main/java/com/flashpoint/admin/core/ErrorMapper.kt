package com.flashpoint.admin.core

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NotAdminException : Exception("Not an administrator")

fun Throwable.toUserMessage(): String = when {
    this is NotAdminException -> "You do not have permission to perform this action."
    this is FirebaseNetworkException || this is UnknownHostException ->
        "No internet connection. Please check your network."
    this is SocketTimeoutException -> "Connection timed out. Please try again."
    this is FirebaseAuthInvalidCredentialsException || this is FirebaseAuthInvalidUserException ->
        "Incorrect email or password."
    this is FirebaseTooManyRequestsException -> "Service temporarily unavailable."
    message?.contains("permission", ignoreCase = true) == true ->
        "You do not have permission to perform this action."
    else -> "Service temporarily unavailable."
}
