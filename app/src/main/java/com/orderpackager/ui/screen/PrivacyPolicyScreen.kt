package com.orderpackager.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orderpackager.BuildConfig
import com.orderpackager.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sections = listOf(
        R.string.privacy_data_title to R.string.privacy_data_body,
        R.string.privacy_use_title to R.string.privacy_use_body,
        R.string.privacy_storage_title to R.string.privacy_storage_body,
        R.string.privacy_retention_title to R.string.privacy_retention_body,
        R.string.privacy_security_title to R.string.privacy_security_body,
        R.string.privacy_contact_title to R.string.privacy_contact_body,
        R.string.privacy_changes_title to R.string.privacy_changes_body
    )

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.privacy_title), fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
            }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.privacy_effective_date), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.privacy_intro), style = MaterialTheme.typography.bodyLarge)
            sections.forEach { (title, body) ->
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
            }
            if (BuildConfig.PRIVACY_POLICY_URL.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL)))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.OpenInBrowser, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.privacy_open_online))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
