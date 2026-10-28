package io.github.xevrion.hatchzip

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object RustBridge {
    init {
        System.loadLibrary("hatchzip_android")
    }
    external fun extractArchive(archivePath: String, outDir: String, password: String, deleteAfter: Byte): String
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CupertinoHatchZipApp(
                onRequestAllFilesAccess = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    }
                }
            )
        }
    }
}

@Composable
fun CupertinoHatchZipApp(onRequestAllFilesAccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val iosBg = Color(0xFF000000)
    val iosCard = Color(0xFF1C1C1E)
    val iosBlue = Color(0xFF0A84FF)
    val iosSecondaryText = Color(0xFF8E8E93)
    val iosSeparator = Color(0xFF38383A)

    var selectedArchivePath by remember { mutableStateOf<String?>(null) }
    var selectedArchiveName by remember { mutableStateOf("No Archive Selected") }
    var outputDirPath by remember { mutableStateOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath) }
    var password by remember { mutableStateOf("") }
    var lowSpaceMode by remember { mutableStateOf(false) }
    var isExtracting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Ready to extract (.zip, .7z, .rar, .tar.gz)") }
    var hasFullStorageAccess by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager())
    }

    val archivePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            val resolved = resolveUriToFilePath(context, it)
            selectedArchivePath = resolved.first
            selectedArchiveName = resolved.second
            statusMessage = "Selected: ${resolved.second}"
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            val path = resolveTreeUriToPath(it)
            if (path != null) {
                outputDirPath = path
                statusMessage = "Output set to: $path"
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = iosBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "HatchZip",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "iOS Storage Manager • Low-Space Engine",
                color = iosSecondaryText,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 24.dp)
            )

            if (!hasFullStorageAccess) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF2C1B18))
                        .clickable {
                            onRequestAllFilesAccess()
                            hasFullStorageAccess = true
                        }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.FolderSpecial, contentDescription = null, tint = Color(0xFFFF453A))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Grant Full Storage Access", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Tap here to allow direct zero-copy extraction in Downloads.", color = iosSecondaryText, fontSize = 12.sp)
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = iosSecondaryText)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            Text("ARCHIVE & DESTINATION", color = iosSecondaryText, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(iosCard)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { archivePicker.launch(arrayOf("*/*")) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(iosBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.FolderZip, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Select Archive", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(selectedArchiveName, color = iosSecondaryText, fontSize = 13.sp, maxLines = 1)
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = iosSecondaryText)
                }

                HorizontalDivider(color = iosSeparator, thickness = 0.5.dp, modifier = Modifier.padding(start = 66.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { folderPicker.launch(null) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF30D158)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Extract To", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(outputDirPath, color = iosSecondaryText, fontSize = 13.sp, maxLines = 1)
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = iosSecondaryText)
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("EXTRACTION OPTIONS", color = iosSecondaryText, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(iosCard)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Low-Space Mode", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("Reclaim space during extraction (Deletes archive)", color = iosSecondaryText, fontSize = 12.sp)
                    }
                    Switch(
                        checked = lowSpaceMode,
                        onCheckedChange = { lowSpaceMode = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF30D158))
                    )
                }

                HorizontalDivider(color = iosSeparator, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Archive Password (Optional)", color = iosSecondaryText) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = iosBlue,
                        unfocusedBorderColor = iosSeparator
                    )
                )
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = {
                    val archive = selectedArchivePath
                    if (archive == null) {
                        statusMessage = "Please pick an archive file first!"
                        return@Button
                    }
                    isExtracting = true
                    statusMessage = "Extracting with HatchZip Rust Engine..."
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            RustBridge.extractArchive(
                                archivePath = archive,
                                outDir = outputDirPath,
                                password = password,
                                deleteAfter = if (lowSpaceMode) 1 else 0
                            )
                        }
                        isExtracting = false
                        statusMessage = result
                    }
                },
                enabled = !isExtracting,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = iosBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                if (isExtracting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Extracting...", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                } else {
                    Icon(Icons.Rounded.Unarchive, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Extract Now", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(iosCard)
                    .padding(14.dp)
            ) {
                Text(
                    text = statusMessage,
                    color = iosSecondaryText,
                    fontSize = 13.sp
                )
            }
        }
    }
}

fun resolveUriToFilePath(context: Context, uri: Uri): Pair<String, String> {
    val docId = try { DocumentsContract.getDocumentId(uri) } catch (_: Exception) { "" }
    if (docId.startsWith("raw:")) {
        val rawPath = docId.removePrefix("raw:")
        return Pair(rawPath, File(rawPath).name)
    }
    val split = docId.split(":")
    if (split.size == 2 && split[0].equals("primary", ignoreCase = true)) {
        val realPath = "${Environment.getExternalStorageDirectory().absolutePath}/${split[1]}"
        if (File(realPath).exists()) {
            return Pair(realPath, File(realPath).name)
        }
    }
    val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "archive.zip"
    val tempFile = File(context.cacheDir, fileName)
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
    }
    return Pair(tempFile.absolutePath, fileName)
}

fun resolveTreeUriToPath(treeUri: Uri): String? {
    val docId = try { DocumentsContract.getTreeDocumentId(treeUri) } catch (_: Exception) { return null }
    if (docId.startsWith("raw:")) return docId.removePrefix("raw:")
    val split = docId.split(":")
    if (split.size == 2 && split[0].equals("primary", ignoreCase = true)) {
        return "${Environment.getExternalStorageDirectory().absolutePath}/${split[1]}"
    }
    return null
}
