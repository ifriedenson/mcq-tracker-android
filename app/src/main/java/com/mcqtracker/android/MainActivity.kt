package com.mcqtracker.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.mcqtracker.android.ui.MCQApp
import com.mcqtracker.android.ui.MCQTrackerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            val persisted = runCatching {
                contentResolver.takePersistableUriPermission(uri, readWrite)
            }.isSuccess
            if (!persisted) {
                // Fall back to read-only access when the provider will not grant write.
                runCatching {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            viewModel.openVault(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MCQTrackerTheme {
                MCQApp(viewModel = viewModel, onPickFolder = { pickFolder.launch(null) })
            }
        }
    }
}
