package com.lainsmain.mneme

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.lainsmain.mneme.data.UpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdateInstallActivity : ComponentActivity() {
    private var verifiedUri: Uri? = null
    private val unknownSourcesPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (packageManager.canRequestPackageInstalls()) {
            launchPackageInstaller()
        } else {
            Toast.makeText(this, "Allow Mneme to install its update, then try again.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
        lifecycleScope.launch {
            val verified = withContext(Dispatchers.IO) {
                UpdateRepository(this@UpdateInstallActivity).verifiedDownloadUri(downloadId)
            }
            verified.fold(
                onSuccess = { uri ->
                    verifiedUri = uri
                    requestPermissionOrInstall()
                },
                onFailure = { error ->
                    Toast.makeText(
                        this@UpdateInstallActivity,
                        error.message ?: "The update could not be verified.",
                        Toast.LENGTH_LONG,
                    ).show()
                    finish()
                },
            )
        }
    }

    private fun requestPermissionOrInstall() {
        if (packageManager.canRequestPackageInstalls()) {
            launchPackageInstaller()
        } else {
            unknownSourcesPermission.launch(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
    }

    private fun launchPackageInstaller() {
        val uri = verifiedUri ?: return finish()
        startActivity(
            Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true),
        )
        finish()
    }

    companion object {
        const val EXTRA_DOWNLOAD_ID = "com.lainsmain.mneme.extra.UPDATE_DOWNLOAD_ID"
    }
}
