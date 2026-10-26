package com.orderpackager.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.orderpackager.R
import com.orderpackager.data.auth.AuthState
import com.orderpackager.data.auth.SupabaseAuthManager

@Composable
fun LoginScreen(auth: SupabaseAuthManager, state: AuthState) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val loading = state is AuthState.Loading

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Default.Cloud, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.cloud_login_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.cloud_login_description), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.email)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state is AuthState.Error) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = { auth.signIn(email, password) },
                    enabled = email.isNotBlank() && password.isNotBlank() && !loading,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.sign_in))
                }
                Text(stringResource(R.string.password_not_saved), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
