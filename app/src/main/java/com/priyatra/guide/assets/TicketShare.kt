package com.priyatra.guide.assets

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object TicketShare {
    fun openPdfFromAssets(context: Context, assetFileName: String) {
        val dir = File(context.cacheDir, "tickets").apply { mkdirs() }
        val out = File(dir, assetFileName)
        if (!out.exists() || out.length() == 0L) {
            context.assets.open(assetFileName).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            out,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open ticket PDF"))
    }
}
