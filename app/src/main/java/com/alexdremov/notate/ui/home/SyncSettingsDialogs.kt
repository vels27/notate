package com.alexdremov.notate.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.alexdremov.notate.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RemoteStorageListDialog(
    onDismiss: () -> Unit,
    onManageStorage: (RemoteStorageConfig?) -> Unit,
    refreshTrigger: Int = 0,
) {
    val context = LocalContext.current
    var storages by remember(refreshTrigger) { mutableStateOf(SyncPreferencesManager.getRemoteStorages(context)) }

    AlertDialog(
        modifier = Modifier.border(2.dp, Color.Black, RoundedCornerShape(28.dp)),
        onDismissRequest = onDismiss,
        title = { Text("Known Storages") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(storages) { storage ->
                        ListItem(
                            headlineContent = { Text(storage.name) },
                            supportingContent = { Text(storage.type.name) },
                            leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) },
                            trailingContent = {
                                IconButton(onClick = {
                                    val newList = storages.filter { it.id != storage.id }
                                    SyncPreferencesManager.saveRemoteStorages(context, newList)
                                    SyncPreferencesManager.deletePassword(context, storage.id)
                                    storages = newList
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            },
                            modifier = Modifier.clickable { onManageStorage(storage) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { onManageStorage(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Storage")
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
fun EditRemoteStorageDialog(
    storage: RemoteStorageConfig? = null,
    onDismiss: () -> Unit,
    onConfirm: (RemoteStorageConfig, String) -> Unit,
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf(storage?.name ?: "") }
    val type = RemoteStorageType.WEBDAV
    var baseUrl by remember { mutableStateOf(storage?.baseUrl ?: "") }
    var username by remember { mutableStateOf(storage?.username ?: "") }
    var password by remember {
        mutableStateOf(if (storage != null) SyncPreferencesManager.getPassword(context, storage.id) ?: "" else "")
    }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        modifier = Modifier.border(2.dp, Color.Black, RoundedCornerShape(28.dp)),
        onDismissRequest = onDismiss,
        title = { Text(if (storage == null) "Add Storage" else "Edit Storage") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Storage Name (e.g. My Nextcloud)") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password / App Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isTesting = true
                                testStatus = "Testing..."
                                try {
                                    val config = RemoteStorageConfig("", "", type, baseUrl, username)
                                    val provider = WebDavProvider(config, password)
                                    if (provider.testConnection()) {
                                        testStatus = "Success!"
                                    } else {
                                        testStatus = "Connection Failed"
                                    }
                                } catch (e: Exception) {
                                    testStatus = "Error: ${e.message ?: e.javaClass.simpleName}"
                                } finally {
                                    isTesting = false
                                }
                            }
                        },
                        enabled = !isTesting && baseUrl.isNotBlank(),
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Test Connection")
                    }

                    testStatus?.let {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                when {
                                    it.contains("Success") -> Color(0xFF1B5E20)
                                    it.contains("Testing") -> Color.Gray
                                    else -> Color.Red
                                },
                        )
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = {
                    if (name.isNotBlank()) {
                        val id = storage?.id ?: UUID.randomUUID().toString()
                        val newConfig = RemoteStorageConfig(id, name, type, baseUrl, username)
                        onConfirm(newConfig, password)
                    }
                },
                enabled = name.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun ProjectSyncConfigDialog(
    projectId: String,
    onDismiss: () -> Unit,
    onSyncNow: () -> Unit,
) {
    val context = LocalContext.current
    var syncConfig by remember { mutableStateOf(SyncPreferencesManager.getProjectSyncConfig(context, projectId)) }
    var refreshStoragesTrigger by remember { mutableStateOf(0) }
    val storages = SyncPreferencesManager.getRemoteStorages(context)

    var showEditStorage by remember { mutableStateOf(false) }
    var showStorageList by remember { mutableStateOf(false) }

    AlertDialog(
        modifier = Modifier.border(2.dp, Color.Black, RoundedCornerShape(28.dp)),
        onDismissRequest = onDismiss,
        title = { Text("Project Synchronization") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = syncConfig?.isEnabled ?: false,
                        onCheckedChange = { enabled ->
                            val current = syncConfig ?: ProjectSyncConfig(projectId, "", "")
                            val updated = current.copy(isEnabled = enabled)
                            SyncPreferencesManager.updateProjectSyncConfig(context, updated)
                            syncConfig = updated
                        },
                    )
                    Text("Enable Synchronization")
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Text("Remote Storage", style = MaterialTheme.typography.titleSmall)
                val selectedStorage = storages.find { it.id == syncConfig?.remoteStorageId }

                OutlinedButton(
                    onClick = { showStorageList = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(selectedStorage?.name ?: "Select Storage...")
                }

                if (syncConfig?.isEnabled == true) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = syncConfig?.remotePath ?: "",
                        onValueChange = { path ->
                            val updated = (syncConfig ?: ProjectSyncConfig(projectId, "", "")).copy(remotePath = path)
                            SyncPreferencesManager.updateProjectSyncConfig(context, updated)
                            syncConfig = updated
                        },
                        label = { Text("Remote Directory Path") },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = syncConfig?.syncPdf ?: true,
                            onCheckedChange = { enabled ->
                                val updated = (syncConfig ?: ProjectSyncConfig(projectId, "", "")).copy(syncPdf = enabled)
                                SyncPreferencesManager.updateProjectSyncConfig(context, updated)
                                syncConfig = updated
                            },
                        )
                        Text("Upload PDF alongside .notate files")
                    }

                    if (syncConfig?.lastSyncTimestamp != 0L) {
                        Text(
                            "Last Sync: ${SimpleDateFormat(
                                "yyyy-MM-dd HH:mm",
                                Locale.getDefault(),
                            ).format(Date(syncConfig!!.lastSyncTimestamp))}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (syncConfig?.isEnabled == true && syncConfig?.remoteStorageId?.isNotBlank() == true) {
                    OutlinedButton(onClick = onSyncNow) {
                        Text("Sync Now")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                OutlinedButton(onClick = onDismiss) {
                    Text("Done")
                }
            }
        },
    )

    if (showStorageList) {
        RemoteStorageListDialog(
            onDismiss = { showStorageList = false },
            onManageStorage = { storage ->
                if (storage != null) {
                    val updated = (syncConfig ?: ProjectSyncConfig(projectId, "", "")).copy(remoteStorageId = storage.id)
                    SyncPreferencesManager.updateProjectSyncConfig(context, updated)
                    syncConfig = updated
                    showStorageList = false
                } else {
                    showEditStorage = true
                }
            },
            refreshTrigger = refreshStoragesTrigger,
        )
    }

    if (showEditStorage) {
        EditRemoteStorageDialog(
            onDismiss = { showEditStorage = false },
            onConfirm = { config, password ->
                val current = SyncPreferencesManager.getRemoteStorages(context).toMutableList()
                current.add(config)
                SyncPreferencesManager.saveRemoteStorages(context, current)
                SyncPreferencesManager.savePassword(
                    context,
                    config.id,
                    if (config.type == RemoteStorageType.WEBDAV) password else "",
                )

                val updatedSync = (syncConfig ?: ProjectSyncConfig(projectId, "", "")).copy(remoteStorageId = config.id)
                SyncPreferencesManager.updateProjectSyncConfig(context, updatedSync)
                syncConfig = updatedSync

                showEditStorage = false
                refreshStoragesTrigger++
            },
        )
    }
}

private val SimpleDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
