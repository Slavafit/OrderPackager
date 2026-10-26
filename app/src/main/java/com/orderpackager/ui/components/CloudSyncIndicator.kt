package com.orderpackager.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orderpackager.R
import com.orderpackager.data.sync.CloudSyncManager
import com.orderpackager.data.sync.CloudSyncStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CloudSyncIndicator(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = rememberCloudSyncManager(context)
    val status by manager.status.collectAsStateWithLifecycle()
    val canRetry = status is CloudSyncStatus.Offline
    val containerColor = when (status) {
        is CloudSyncStatus.Synced -> MaterialTheme.colorScheme.primaryContainer
        is CloudSyncStatus.Offline -> MaterialTheme.colorScheme.errorContainer
        CloudSyncStatus.NotConfigured -> MaterialTheme.colorScheme.secondaryContainer
        CloudSyncStatus.Syncing -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (status) {
        is CloudSyncStatus.Offline -> MaterialTheme.colorScheme.onErrorContainer
        is CloudSyncStatus.Synced -> MaterialTheme.colorScheme.onPrimaryContainer
        CloudSyncStatus.NotConfigured -> MaterialTheme.colorScheme.onSecondaryContainer
        CloudSyncStatus.Syncing -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .fillMaxWidth()
            .then(if (canRetry) Modifier.clickable { manager.enqueue() } else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            when (status) {
                CloudSyncStatus.Syncing -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
                is CloudSyncStatus.Synced -> Icon(Icons.Default.CloudDone, null, Modifier.size(18.dp))
                is CloudSyncStatus.Offline -> Icon(Icons.Default.CloudOff, null, Modifier.size(18.dp))
                CloudSyncStatus.NotConfigured -> Icon(Icons.Default.Settings, null, Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = when (val current = status) {
                    CloudSyncStatus.Syncing -> stringResource(R.string.cloud_syncing)
                    is CloudSyncStatus.Synced -> stringResource(
                        R.string.cloud_synced_at,
                        formatSyncTime(current.completedAt)
                    )
                    is CloudSyncStatus.Offline -> current.lastSuccessAt?.let {
                        stringResource(R.string.cloud_offline_last_sync, formatSyncTime(it))
                    } ?: stringResource(R.string.cloud_offline)
                    CloudSyncStatus.NotConfigured -> stringResource(R.string.cloud_not_configured)
                },
                fontSize = 12.sp
            )
            if (canRetry) {
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.cloud_retry), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun rememberCloudSyncManager(context: android.content.Context): CloudSyncManager =
    androidx.compose.runtime.remember(context) { CloudSyncManager.getInstance(context) }

private fun formatSyncTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
