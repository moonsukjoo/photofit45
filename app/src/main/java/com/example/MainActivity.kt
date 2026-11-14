package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.batch.BatchCompressScreen
import com.example.ui.history.SavedGalleryScreen
import com.example.ui.single.SingleEditorScreen
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.MyApplicationTheme
import com.example.util.ImageUtils
import com.example.viewmodel.AppTab
import com.example.viewmodel.ImageEditorViewModel
import kotlinx.coroutines.launch
import java.util.ArrayList

class MainActivity : ComponentActivity() {

    private val viewModel: ImageEditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen(
                    viewModel = viewModel,
                    onShareSingle = { uri -> shareSingleUri(uri) },
                    onShareMultiple = { uris -> shareMultipleUris(uris) }
                )
            }
        }
    }

    private fun shareSingleUri(uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "편집된 이미지 공유"))
    }

    private fun shareMultipleUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val safeUris = if (uris.size > 100) uris.take(100) else uris
        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(safeUris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "압축된 이미지 공유 (${safeUris.size}장)"))
    }
}

@Composable
fun MainAppScreen(
    viewModel: ImageEditorViewModel,
    onShareSingle: (Uri) -> Unit,
    onShareMultiple: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val singleState by viewModel.singleState.collectAsStateWithLifecycle()
    val batchState by viewModel.batchState.collectAsStateWithLifecycle()
    val savedHistory by viewModel.savedHistory.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Activity result launcher for Scoped Storage delete request (Android 11+)
    val deleteIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pendingUris = singleState.deleteOriginalPromptUris
                ?: batchState.deleteOriginalPromptUris
                ?: emptyList()
            viewModel.onOriginalsDeleted(pendingUris)
        } else {
            viewModel.dismissDeletePrompt()
        }
    }

    // Activity result launcher for Scoped Storage write permission request (Android 11+)
    val writeIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onWritePermissionGranted(context)
        } else {
            viewModel.dismissWritePermissionPrompt()
        }
    }

    // Show status messages as snackbars
    LaunchedEffect(singleState.statusMessage) {
        singleState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(batchState.statusMessage) {
        batchState.statusMessage?.let { msg ->
            if (!batchState.isProcessing && !batchState.isRepairingDates) {
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    // Pending delete URIs check from either single or batch
    val pendingDeleteUris = singleState.deleteOriginalPromptUris ?: batchState.deleteOriginalPromptUris

    if (pendingDeleteUris != null && pendingDeleteUris.isNotEmpty()) {
        val isBatchChunked = singleState.deleteOriginalPromptUris == null &&
            batchState.totalDeleteBatchCount > pendingDeleteUris.size
        val remainingAfterThis = if (singleState.deleteOriginalPromptUris == null) {
            batchState.pendingDeleteQueue.size
        } else 0
        val alreadyDone = if (isBatchChunked) {
            (batchState.totalDeleteBatchCount - pendingDeleteUris.size - remainingAfterThis).coerceAtLeast(0)
        } else 0

        AlertDialog(
            onDismissRequest = { viewModel.dismissDeletePrompt() },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (isBatchChunked && alreadyDone > 0) {
                        "원본 사진 연속 삭제 (${String.format("%,d", alreadyDone)}/${String.format("%,d", batchState.totalDeleteBatchCount)}장 완료)"
                    } else {
                        "원본 사진 삭제 확인"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val message = if (isBatchChunked) {
                    "압축된 새로운 사진이 갤러리에 안전하게 저장되었습니다.\n\n" +
                        "안드로이드 시스템 제한으로 인해 한 번에 최대 ${ImageUtils.MAX_DELETE_BATCH_SIZE}장씩 나누어 삭제 승인을 진행합니다.\n" +
                        "• 전체 대상: ${String.format("%,d", batchState.totalDeleteBatchCount)}장\n" +
                        "• 이번 삭제 요청: ${pendingDeleteUris.size}장\n" +
                        "• 남은 대기: ${String.format("%,d", remainingAfterThis)}장"
                } else {
                    "압축된 새로운 사진이 갤러리에 안전하게 저장되었습니다.\n\n선택하신 옵션에 따라 기기 용량 확보를 위해 원본 사진 (${pendingDeleteUris.size}장)을 지금 삭제하시겠습니까?"
                }
                Text(
                    text = message,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val request = viewModel.getDeleteIntentSenderRequest(context, pendingDeleteUris)
                            if (request != null) {
                                deleteIntentLauncher.launch(request)
                            } else {
                                ImageUtils.deleteUrisDirectly(context, pendingDeleteUris)
                                viewModel.onOriginalsDeleted(pendingDeleteUris)
                            }
                        } else {
                            ImageUtils.deleteUrisDirectly(context, pendingDeleteUris)
                            viewModel.onOriginalsDeleted(pendingDeleteUris)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed),
                    modifier = Modifier.testTag("confirm_delete_original_button")
                ) {
                    Text(
                        text = if (isBatchChunked) {
                            if (alreadyDone > 0) "다음 ${pendingDeleteUris.size}장 삭제 진행" else "${pendingDeleteUris.size}장씩 삭제 시작"
                        } else {
                            "원본 삭제"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeletePrompt() }) {
                    Text(if (isBatchChunked && alreadyDone > 0) "나머지 중단" else "원본 유지 (취소)")
                }
            }
        )
    }

    // Pending Scoped Storage write permission request for filename-based date recovery
    val pendingWriteUris = batchState.writePermissionPromptUris
    if (pendingWriteUris != null && pendingWriteUris.isNotEmpty()) {
        val isWriteChunked = batchState.totalWritePermissionCount > pendingWriteUris.size
        val remainingWriteAfterThis = batchState.pendingWritePermissionQueue.size
        val alreadyWritePrompted = if (isWriteChunked) {
            (batchState.totalWritePermissionCount - pendingWriteUris.size - remainingWriteAfterThis).coerceAtLeast(0)
        } else 0

        AlertDialog(
            onDismissRequest = { viewModel.dismissWritePermissionPrompt() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (isWriteChunked && alreadyWritePrompted > 0) {
                        "사진 날짜 수정 허용 (${String.format("%,d", alreadyWritePrompted)}/${String.format("%,d", batchState.totalWritePermissionCount)}장)"
                    } else {
                        "사진 파일 수정 허용 필요"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val message = if (isWriteChunked) {
                    "다른 앱이나 이전 버전에서 저장된 사진의 촬영·수정 날짜를 바꾸려면 안드로이드 보안상 수정 허용이 필요합니다.\n\n" +
                        "시스템 제한에 따라 한 번에 최대 ${ImageUtils.MAX_DELETE_BATCH_SIZE}장씩 나누어 진행합니다.\n" +
                        "• 수정 허용 필요: ${String.format("%,d", batchState.totalWritePermissionCount)}장\n" +
                        "• 이번 허용 요청: ${pendingWriteUris.size}장\n" +
                        "• 남은 대기: ${String.format("%,d", remainingWriteAfterThis)}장"
                } else {
                    "다른 앱이나 이전 버전에서 저장된 사진 ${pendingWriteUris.size}장의 EXIF 촬영일·수정일을 덮어쓰려면 시스템 수정 허용이 필요합니다."
                }
                Text(
                    text = message,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val request = viewModel.getWriteIntentSenderRequest(context, pendingWriteUris)
                            if (request != null) {
                                writeIntentLauncher.launch(request)
                            } else {
                                viewModel.dismissWritePermissionPrompt()
                            }
                        } else {
                            viewModel.dismissWritePermissionPrompt()
                        }
                    },
                    modifier = Modifier.testTag("confirm_write_permission_button")
                ) {
                    Text(
                        text = if (isWriteChunked && alreadyWritePrompted > 0) {
                            "다음 ${pendingWriteUris.size}장 수정 허용"
                        } else {
                            "수정 허용하기"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissWritePermissionPrompt() }) {
                    Text(if (isWriteChunked && alreadyWritePrompted > 0) "나머지 중단" else "취소")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // App Logo Badge
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "포토핏",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "이미지 편집 & 용량 압축",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == AppTab.SINGLE_EDIT,
                    onClick = { viewModel.selectTab(AppTab.SINGLE_EDIT) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = AppTab.SINGLE_EDIT.title
                        )
                    },
                    label = { Text(AppTab.SINGLE_EDIT.title, fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_single_edit")
                )

                NavigationBarItem(
                    selected = currentTab == AppTab.BATCH_COMPRESS,
                    onClick = { viewModel.selectTab(AppTab.BATCH_COMPRESS) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesomeMotion,
                            contentDescription = AppTab.BATCH_COMPRESS.title
                        )
                    },
                    label = { Text(AppTab.BATCH_COMPRESS.title, fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.secondary,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.testTag("nav_batch_compress")
                )

                NavigationBarItem(
                    selected = currentTab == AppTab.SAVED_GALLERY,
                    onClick = { viewModel.selectTab(AppTab.SAVED_GALLERY) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Collections,
                            contentDescription = AppTab.SAVED_GALLERY.title
                        )
                    },
                    label = { Text(AppTab.SAVED_GALLERY.title, fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_saved_gallery")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppTab.SINGLE_EDIT -> {
                    SingleEditorScreen(
                        viewModel = viewModel,
                        uiState = singleState,
                        onShare = onShareSingle
                    )
                }
                AppTab.BATCH_COMPRESS -> {
                    BatchCompressScreen(
                        viewModel = viewModel,
                        batchState = batchState,
                        onBatchShare = onShareMultiple
                    )
                }
                AppTab.SAVED_GALLERY -> {
                    SavedGalleryScreen(
                        viewModel = viewModel,
                        savedItems = savedHistory,
                        onShareUri = onShareSingle
                    )
                }
            }
        }
    }
}
