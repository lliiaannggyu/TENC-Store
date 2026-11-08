package com.tengwear.tengstore.ui.filemanager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.tengwear.tengstore.ui.util.RotaryScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT
import com.tengwear.tengstore.ui.util.formatSize
import java.io.File
import com.tengwear.tengstore.ui.util.edgeSpacers
import com.tengwear.tengstore.ui.util.GrayButton

/** 是否已获得文件读取权限（R+ 需「所有文件访问」，低版本为 READ_EXTERNAL_STORAGE）。 */
private fun hasStorageAccess(context: Context): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
    else -> ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_EXTERNAL_STORAGE,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

/**
 * 内置文件管理器：浏览手表存储目录，选择一个 APK 返回给应用上传页。
 * 选中的文件绝对路径通过 previousBackStackEntry 的 savedStateHandle 回传（key: picked_apk）。
 */
@Composable
fun FileManagerScreen(navController: NavHostController) {
    val context = LocalContext.current
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)
    var granted by rememberSaveable { mutableStateOf(hasStorageAccess(context)) }
    var dir by rememberSaveable {
        mutableStateOf(
            Environment.getExternalStorageDirectory()?.absolutePath ?: "/sdcard",
        )
    }
    var tick by rememberSaveable { mutableStateOf(0) } // 刷新触发器

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = hasStorageAccess(context); tick++ }

    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { granted = hasStorageAccess(context); tick++ }

    fun requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            runCatching { context.startActivity(intent) }.onFailure {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        } else {
            legacyPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    // 从系统设置页返回后刷新授权状态
    LaunchedEffect(Unit) { granted = hasStorageAccess(context); tick++ }

    val entries = remember(granted, tick, dir) {
        if (!granted) {
            emptyList()
        } else {
            runCatching {
                File(dir).listFiles()?.let { files ->
                    files.filter { it.isDirectory || it.extension.equals("apk", true) }
                        .sortedWith(
                            compareByDescending<File> { it.isDirectory }
                                .thenBy { it.name.lowercase() },
                        )
                }
            }.getOrNull().orEmpty()
        }
    }

    ScreenScaffold(
            scrollState = listState,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        edgeSpacers()
            item { ListHeader { Text("选择 APK 文件") } }

            if (!granted) {
                item {
                    GrayButton(
                        onClick = { requestAccess() },
                        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                        label = { Text("授予文件访问权限", maxLines = 1) },
                        icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                    )
                }
                return@ScalingLazyColumn
            }

            item {
                Text(
                    dir,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (dir != Environment.getExternalStorageDirectory()?.absolutePath) {
                item {
                    GrayButton(
                        onClick = {
                            dir = File(dir).parent ?: dir
                            tick++
                        },
                        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                        label = { Text("上一级", maxLines = 1) },
                        icon = { Icon(Icons.Default.ArrowBack, contentDescription = null) },
                    )
                }
            }

            if (entries.isEmpty()) {
                item { Text("此目录没有子目录或 APK。", modifier = Modifier.padding(8.dp)) }
            }
            items(entries.size) { i ->
                val entry = entries[i]
                if (entry.isDirectory) {
                    GrayButton(
                        onClick = { dir = entry.absolutePath; tick++ },
                        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                        label = { Text(entry.name, maxLines = 1) },
                        icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    )
                } else {
                    GrayButton(
                        onClick = {
                            navController.previousBackStackEntry
                                ?.savedStateHandle?.set("picked_apk", entry.absolutePath)
                            navController.popBackStack()
                        },
                        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                        label = { Text(entry.name, maxLines = 1) },
                        secondaryLabel = { Text(formatSize(entry.length()), maxLines = 1) },
                        icon = { Icon(Icons.Default.Android, contentDescription = null) },
                    )
                }
            }

            item {
                GrayButton(
                    onClick = { tick++ },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("刷新", maxLines = 1) },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                )
            }
        }
    }
}
