package com.flashpoint.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flashpoint.admin.ui.AdminHome
import com.flashpoint.admin.ui.auth.AuthViewModel
import com.flashpoint.admin.ui.auth.LoginScreen
import com.flashpoint.admin.ui.theme.FlashPointTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FlashPointTheme { Surface(Modifier.fillMaxSize()) { Root() } } }
    }
}

@Composable
private fun Root(vm: AuthViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    when {
        s.checking -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        s.signedIn -> AdminHome(onSignOut = vm::signOut)
        else -> LoginScreen(s, vm::signIn)
    }
}
