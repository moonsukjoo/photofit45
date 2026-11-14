package com.example.ui.batch

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.BatchFormatChoice
import com.example.model.BatchItemStatus
import com.example.model.BatchProcessItem
import com.example.model.ResizeMode
import com.example.ui.components.CompressionQualitySelector
import com.example.ui.components.LabeledSlider
import com.example.ui.components.OptionSwitchCard
import com.example.ui.components.SavingsBadge
import kotlin.math.roundToInt
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.util.ImageUtils
import com.example.viewmodel.BatchUiState
import com.example.viewmodel.ImageEditorViewModel

@Composable
fun BatchCompressScreen(
    viewModel: ImageEditorViewModel,
    batchState: BatchUiState,
    modifier: Modifier = Modifier,
    onBatchShare: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    var showPermissionDeniedDialog by remember { mutableStateOf(false) }
    var pendingAutoRepairAfterPermission by remember { mutableStateOf(false) }

    val batchPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addBatchImages(context, uris)
        }
    }

    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (hasGalleryReadPermission(context)) {
            viewModel.loadDevicePhotos(context, autoPrepareDateRepair = pendingAutoRepairAfterPermission)
            pendingAutoRepairAfterPermission = false
        } else {
            pendingAutoRepairAfterPermission = false
            showPermissionDeniedDialog = true
        }
    }

    if (showPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedDialog = false },
            title = { Text("갤러리 접근 권한 필요", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "기기 갤러리의 모든 사진을 불러오려면 사진 및 이미지 접근 권한이 필요합니다.\n\n" +
                        "앱 설정에서 '사진 및 동영상' 권한을 허용하시거나, '갤러리에서 사진 다중 선택' 버튼을 이용해 주세요."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDeniedDialog = false
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    }
                ) {
                    Text("설정으로 이동")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDeniedDialog = false }) {
                    Text("닫기")
                }
            }
        )
    }

    batchState.dateRepairPreview?.let { preview ->
        DateRepairPreviewDialog(
            preview = preview,
            onConfirm = { viewModel.executeDateRepair(context) },
            onDismiss = { viewModel.dismissDateRepairPreview() }
        )
    }

    if (batchState.items.isEmpty()) {
        BatchEmptyState(
            isLoadingGallery = batchState.isLoadingGallery,
            onPickBatch = {
                batchPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onLoadDevicePhotos = {
                if (hasFullGalleryReadPermission(context)) {
                    viewModel.loadDevicePhotos(context, autoPrepareDateRepair = false)
                } else {
                    pendingAutoRepairAfterPermission = false
                    galleryPermissionLauncher.launch(requiredGalleryPermissions())
                }
            },
            onLoadAndRepairDates = {
                if (hasFullGalleryReadPermission(context)) {
                    viewModel.loadDevicePhotos(context, autoPrepareDateRepair = true)
                } else {
                    pendingAutoRepairAfterPermission = true
                    galleryPermissionLauncher.launch(requiredGalleryPermissions())
                }
            },
            modifier = modifier
        )
    } else {
        BatchWorkspace(
            viewModel = viewModel,
            batchState = batchState,
            onAddMore = {
                batchPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onStartProcessing = {
                viewModel.startBatchProcessing(context)
            },
            onShareAll = {
                val completedUris = batchState.items
                    .filter { it.status == BatchItemStatus.COMPLETED && it.resultUri != null }
                    .mapNotNull { it.resultUri }
                if (completedUris.isNotEmpty()) {
                    onBatchShare(completedUris)
                }
            },
            modifier = modifier
        )
    }
}

@Composable
private fun BatchEmptyState(
    isLoadingGallery: Boolean,
    onPickBatch: () -> Unit,
    onLoadDevicePhotos: () -> Unit,
    onLoadAndRepairDates: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesomeMotion,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(46.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "갤러리 전체 사진 한 번에 용량 줄이기",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "1만~2만 장의 사진도 전체 선택해 원하는 강도로 줄이고,\n원본 날짜와 갤러리 순서는 그대로 지킬 수 있습니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 3-step visual guide strip
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepBadge("1", "전체 불러오기")
                Text("→", color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold)
                StepBadge("2", "압축 강도 선택")
                Text("→", color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold)
                StepBadge("3", "변환 & 원본 정리")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Primary CTA: Load Entire Device Gallery (Auto-selected)
        Button(
            onClick = onLoadDevicePhotos,
            enabled = !isLoadingGallery,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("load_device_photos_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            if (isLoadingGallery) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "기기 갤러리 사진 불러오는 중...",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "기기 갤러리 전체 사진 불러오기 (자동 전체 선택)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Secondary CTA: Pick Specific Photos Manually
        OutlinedButton(
            onClick = onPickBatch,
            enabled = !isLoadingGallery,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("pick_batch_images_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "원하는 사진만 직접 골라 담기",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Recovery Utility Card: Restore Dates from Filename
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "이미 압축한 사진 날짜·정렬이 뒤섞였나요?",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "IMG_20220501_142030 등 파일명에 날짜가 남아있다면 EXIF 촬영일과 갤러리 날짜 순서를 원래대로 되돌릴 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = onLoadAndRepairDates,
                    enabled = !isLoadingGallery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("empty_repair_dates_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "파일명에서 촬영 날짜·정렬 복구하기",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StepBadge(number: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BatchWorkspace(
    viewModel: ImageEditorViewModel,
    batchState: BatchUiState,
    onAddMore: () -> Unit,
    onStartProcessing: () -> Unit,
    onShareAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Sticky Header Summary
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (batchState.isAllSelected) {
                                "전체 ${String.format("%,d", batchState.items.size)}장 모두 선택됨"
                            } else {
                                "전체 ${String.format("%,d", batchState.items.size)}장 중 ${String.format("%,d", batchState.selectedCount)}장 선택"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "선택된 원본 용량: ${ImageUtils.formatBytes(batchState.selectedOriginalBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!batchState.isProcessing && !batchState.isRepairingDates) {
                            OutlinedButton(
                                onClick = onAddMore,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("사진 추가", fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = { viewModel.clearBatch() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "작업 목록 비우기", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                // Easy Selection Action Toolbar (전체 선택 / 전체 해제 / 목록 제외)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(
                                onClick = { viewModel.selectAllBatchItems(true) },
                                enabled = !batchState.isAllSelected && !batchState.isProcessing && !batchState.isRepairingDates,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("select_all_button")
                            ) {
                                Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("전체 선택", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = { viewModel.selectAllBatchItems(false) },
                                enabled = batchState.selectedCount > 0 && !batchState.isProcessing && !batchState.isRepairingDates,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("deselect_all_button")
                            ) {
                                Icon(Icons.Default.Deselect, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("선택 해제", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (batchState.selectedCount > 0 && !batchState.isProcessing && !batchState.isRepairingDates) {
                            OutlinedButton(
                                onClick = { viewModel.removeSelectedBatchItems() },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(14.dp), tint = ErrorRed)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("목록 제외", fontSize = 11.sp, color = ErrorRed)
                            }
                        }
                    }
                }
            }
        }

        // Processing / Date-Repair Progress Bar
        if (batchState.isProcessing || batchState.isRepairingDates) {
            val targetTotal = if (batchState.processingTargetCount > 0) {
                batchState.processingTargetCount
            } else {
                batchState.totalCount
            }
            val progress = if (targetTotal > 0) {
                (batchState.currentProcessingIndex.toFloat() / targetTotal.toFloat()).coerceIn(0f, 1f)
            } else 0f

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (batchState.isRepairingDates) {
                            "${String.format("%,d", batchState.currentProcessingIndex)} / ${String.format("%,d", targetTotal)} 날짜 복구 중"
                        } else {
                            "${String.format("%,d", batchState.currentProcessingIndex)} / ${String.format("%,d", targetTotal)} 처리 중"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Completion Banner (if finished)
        if (batchState.isCompleted) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Success Summary Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = SuccessGreen.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "일괄 압축 완료!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "총 ${ImageUtils.formatBytes(batchState.comparisonOriginalBytes)} → ${ImageUtils.formatBytes(batchState.totalProcessedBytes)} (${ImageUtils.formatBytes(batchState.totalSavedBytes)} 절약, -${batchState.overallSavingsPercent}%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    viewModel.exportCompletedBatchToPdf(context) { pdfUri ->
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, pdfUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "PDF 문서 공유"))
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("PDF 묶기", fontSize = 12.sp)
                            }

                            Button(
                                onClick = onShareAll,
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("전체 공유", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // DEDICATED DELETE ORIGINALS ACTION CARD (User requested: 용량사이즈 변경후 원본파일 삭제)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = ErrorRed.copy(alpha = 0.08f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, ErrorRed.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "기기 저장 공간 확보: 원본 사진 삭제",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ErrorRed
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "압축된 사진이 갤러리에 안전하게 저장되었습니다. 이제 기기 용량을 대폭 확보하기 위해 원본 사진(${String.format("%,d", batchState.completedCount)}장)을 지금 삭제하시겠습니까?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = { viewModel.requestDeleteCompletedBatchOriginals() },
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("delete_originals_after_batch_button")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("원본 사진 ${String.format("%,d", batchState.completedCount)}개 지금 삭제", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Main scrollable content
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // Batch Options Card
                BatchConfigCard(viewModel, batchState)
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "처리 목록 (${String.format("%,d", batchState.selectedCount)}/${String.format("%,d", batchState.items.size)}개 선택됨)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            items(batchState.items, key = { it.id }) { item ->
                BatchItemCard(
                    item = item,
                    onToggleSelect = { viewModel.toggleBatchItemSelection(item.id) },
                    onRemove = { viewModel.removeBatchItem(item.id) },
                    canRemove = !batchState.isProcessing && !batchState.isRepairingDates
                )
            }

            item {
                Spacer(modifier = Modifier.height(90.dp)) // padding for bottom button
            }
        }

        // Bottom Sticky Action Button
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                val buttonCount = if (batchState.selectedCount > 0) batchState.selectedCount else batchState.items.size
                Button(
                    onClick = onStartProcessing,
                    enabled = !batchState.isProcessing && !batchState.isRepairingDates && batchState.items.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("start_batch_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (batchState.isProcessing) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("일괄 압축 진행 중...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Compress,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                batchState.isCompleted -> "다시 일괄 압축하기"
                                batchState.isAllSelected -> "전체 ${String.format("%,d", buttonCount)}장 한 번에 압축 시작"
                                else -> "선택한 ${String.format("%,d", buttonCount)}장 압축 시작"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchConfigCard(
    viewModel: ImageEditorViewModel,
    batchState: BatchUiState
) {
    val config = batchState.config
    var showAdvancedOptions by remember { mutableStateOf(false) }

    val formatLabel = when (config.formatChoice) {
        BatchFormatChoice.KEEP_ORIGINAL -> "원본 포맷"
        BatchFormatChoice.WEBP -> "WEBP"
        BatchFormatChoice.JPEG -> "JPEG"
        BatchFormatChoice.PNG -> "PNG"
    }
    val resizeLabel = when (config.resizeMode) {
        ResizeMode.ORIGINAL -> "원본 크기"
        ResizeMode.PERCENT_75 -> "75% 축소"
        ResizeMode.PERCENT_50 -> "50% 축소"
        else -> config.resizeMode.displayName
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "간편 일괄 변환 설정",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 1. Quality Presets (최하 / 중간 / 최대 압축 + 예상 용량 예시)
            Text(
                text = "1. 얼마나 줄일까요? (압축 강도 선택)",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            CompressionQualitySelector(
                selectedQuality = config.quality,
                onQualitySelected = { q -> viewModel.updateBatchConfig(config.copy(quality = q)) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Essential Options (Date Preserve + Delete Original)
            Text(
                text = "2. 날짜 · 원본 파일 처리",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Preserve Original Capture/File Date & Gallery Sort Order
            OptionSwitchCard(
                title = "원본 촬영·생성 날짜 유지 (갤러리 정렬 유지)",
                subtitle = "압축된 사진에 원본 날짜·시간 순서를 그대로 기록해 옛날 사진이 최신 날짜로 올라오지 않게 합니다.",
                checked = config.preserveOriginalDate,
                onCheckedChange = { viewModel.updateBatchConfig(config.copy(preserveOriginalDate = it)) },
                highlight = true,
                icon = Icons.Default.Schedule,
                testTag = "batch_preserve_date_toggle"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Delete Original Toggle
            OptionSwitchCard(
                title = "압축 완료 후 원본 자동 삭제 요청",
                subtitle = "새 압축 파일 저장이 끝난 뒤 원본 삭제 확인 창을 띄워 기기 용량을 바로 확보합니다.",
                checked = config.deleteOriginal,
                onCheckedChange = { viewModel.updateBatchConfig(config.copy(deleteOriginal = it)) },
                highlight = true,
                icon = Icons.Default.DeleteOutline,
                testTag = "batch_delete_original_toggle"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filename -> EXIF/MediaStore Date Recovery Tool
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "이미 압축한 사진: 파일명으로 원본 날짜 복구",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "IMG_20220501_142030 등 파일명 속 날짜를 읽어 EXIF 촬영일·수정일을 다시 써서 갤러리 순서를 되돌립니다.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = { viewModel.prepareDateRepairFromFilenames() },
                        enabled = !batchState.isProcessing && !batchState.isRepairingDates,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("repair_dates_from_filename_button")
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (batchState.selectedCount > 0) {
                                "선택한 ${String.format("%,d", batchState.selectedCount)}장 날짜 복구 검사"
                            } else {
                                "전체 목록 날짜 복구 검사"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Collapsible Advanced Options Accordion
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showAdvancedOptions = !showAdvancedOptions },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "세부 설정 (포맷 · 크기 · 파일명 · 워터마크)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$formatLabel · $resizeLabel · ${config.renamePattern.displayName}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = if (showAdvancedOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showAdvancedOptions) "세부 설정 접기" else "세부 설정 펼치기",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = showAdvancedOptions) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    LabeledSlider(
                        label = "세부 화질 직접 조절 (높을수록 고화질·큰 용량)",
                        value = config.quality.toFloat(),
                        onValueChange = { viewModel.updateBatchConfig(config.copy(quality = it.roundToInt())) },
                        valueRange = 10f..100f,
                        unit = "%",
                        defaultValue = 75f,
                        onReset = { viewModel.updateBatchConfig(config.copy(quality = 75)) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Format Selection
                    Text(
                        text = "변환 포맷",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BatchFormatChoice.entries.forEach { choice ->
                            FilterChip(
                                selected = config.formatChoice == choice,
                                onClick = { viewModel.updateBatchConfig(config.copy(formatChoice = choice)) },
                                label = {
                                    Text(
                                        text = when (choice) {
                                            BatchFormatChoice.KEEP_ORIGINAL -> "원본 유지"
                                            BatchFormatChoice.WEBP -> "WEBP (추천)"
                                            BatchFormatChoice.JPEG -> "JPEG"
                                            BatchFormatChoice.PNG -> "PNG"
                                        },
                                        fontSize = 11.sp
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Resize Scale
                    Text(
                        text = "해상도 축소 비율 및 소셜 규격",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf(
                            Pair(ResizeMode.ORIGINAL, "100% 원본"),
                            Pair(ResizeMode.PERCENT_75, "75% 축소"),
                            Pair(ResizeMode.PERCENT_50, "50% 축소"),
                            Pair(ResizeMode.FHD_1080P, "1080p FHD"),
                            Pair(ResizeMode.INSTA_FEED, "인스타 (1080px)"),
                            Pair(ResizeMode.KAKAO_PROFILE, "카카오 (640px)"),
                            Pair(ResizeMode.WEB_THUMB, "웹 썸네일 (500px)")
                        )
                        items(modes) { (mode, label) ->
                            FilterChip(
                                selected = config.resizeMode == mode,
                                onClick = { viewModel.updateBatchConfig(config.copy(resizeMode = mode)) },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Smart Renamer Pattern
                    Text(
                        text = "파일명 일괄 정리 패턴",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(com.example.model.RenamePattern.entries) { pattern ->
                            FilterChip(
                                selected = config.renamePattern == pattern,
                                onClick = { viewModel.updateBatchConfig(config.copy(renamePattern = pattern)) },
                                label = { Text(pattern.displayName, fontSize = 11.sp) }
                            )
                        }
                    }

                    if (config.renamePattern == com.example.model.RenamePattern.CUSTOM_PREFIX) {
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = config.customPrefix,
                            onValueChange = { viewModel.updateBatchConfig(config.copy(customPrefix = it)) },
                            label = { Text("파일명 접두사 (예: 제주여행, 상품, 영수증)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Batch Watermark
                    Text(
                        text = "일괄 워터마크 서명 (선택)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = config.watermarkText,
                        onValueChange = { viewModel.updateBatchConfig(config.copy(watermarkText = it)) },
                        label = { Text("워터마크 텍스트 (미입력 시 워터마크 없음)") },
                        placeholder = { Text("예: © PhotoFit, 닉네임, 상호명") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (config.watermarkText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(com.example.model.WatermarkPosition.entries) { pos ->
                                FilterChip(
                                    selected = config.watermarkPosition == pos,
                                    onClick = { viewModel.updateBatchConfig(config.copy(watermarkPosition = pos)) },
                                    label = { Text(pos.displayName, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Privacy EXIF Wiper
                    OptionSwitchCard(
                        title = "개인정보 보호: GPS 위치 및 기기 정보 제거",
                        subtitle = "사진에 담긴 촬영 장소 GPS 좌표와 카메라 기종 정보 등을 안전하게 삭제합니다. (날짜 유지를 켜면 촬영 날짜만 보존됩니다)",
                        checked = config.removeExifMetadata,
                        onCheckedChange = { viewModel.updateBatchConfig(config.copy(removeExifMetadata = it)) },
                        icon = Icons.Default.Shield
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchItemCard(
    item: BatchProcessItem,
    onToggleSelect: () -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleSelect() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isSelected) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        border = if (item.isSelected) {
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox for selection
            Checkbox(
                checked = item.isSelected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Thumbnail
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.uri)
                    .crossfade(true)
                    .size(160)
                    .build(),
                contentDescription = item.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val dateLabel = remember(item.originalDateTakenMs) {
                    if (item.originalDateTakenMs > 0L) {
                        ImageUtils.formatReadableDateTime(item.originalDateTakenMs).take(16)
                    } else null
                }
                val metaLine = buildString {
                    if (item.originalWidth > 0 && item.originalHeight > 0) {
                        append("${item.originalWidth}×${item.originalHeight} • ")
                    }
                    append(ImageUtils.formatBytes(item.originalSizeBytes))
                    if (dateLabel != null) {
                        append(" • $dateLabel")
                    }
                }
                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Processed size & Savings badge
                if (item.status == BatchItemStatus.COMPLETED && item.processedSizeBytes != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "→ ${ImageUtils.formatBytes(item.processedSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        SavingsBadge(
                            originalBytes = item.originalSizeBytes,
                            newBytes = item.processedSizeBytes
                        )
                    }
                } else if (item.status == BatchItemStatus.FAILED) {
                    Text(
                        text = item.errorMessage ?: "오류 발생",
                        style = MaterialTheme.typography.labelSmall,
                        color = ErrorRed
                    )
                }
            }

            // Status Indicator or Remove Button
            when (item.status) {
                BatchItemStatus.PENDING -> {
                    if (canRemove) {
                        IconButton(onClick = onRemove) {
                            Icon(Icons.Default.Close, contentDescription = "목록에서 제거", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                BatchItemStatus.PROCESSING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                BatchItemStatus.COMPLETED -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "완료",
                        tint = SuccessGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
                BatchItemStatus.FAILED -> {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "실패",
                        tint = ErrorRed,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

private fun hasFullGalleryReadPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private fun hasGalleryReadPermission(context: Context): Boolean {
    if (hasFullGalleryReadPermission(context)) return true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        ) == PackageManager.PERMISSION_GRANTED
    }
    return false
}

private fun requiredGalleryPermissions(): Array<String> {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES
        )
        else -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }
}

@Composable
private fun DateRepairPreviewDialog(
    preview: com.example.viewmodel.DateRepairPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
                text = "파일명에서 촬영 날짜 복구",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "• 검사 대상: ${String.format("%,d", preview.totalChecked)}장",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• 날짜 추출 가능: ${String.format("%,d", preview.matchedCount)}장",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (preview.matchedCount > 0) SuccessGreen else ErrorRed
                        )
                        if (preview.skippedCount > 0) {
                            Text(
                                text = "• 날짜 형식 없음 (건너뜀): ${String.format("%,d", preview.skippedCount)}장",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (preview.matchedCount > 0) {
                    Text(
                        text = "인식 예시 (미리보기)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        preview.samples.forEach { (name, dateMs) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text(
                                        text = name,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "→ ${ImageUtils.formatReadableDateTime(dateMs)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = "EXIF 촬영일과 갤러리 수정일을 파일명 날짜로 다시 기록합니다. (안드로이드 보안상 다른 앱/이전 버전 파일은 500장씩 수정 허용 팝업이 표시될 수 있습니다)",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "IMG_20220501_142030, 20220501_..., KakaoTalk_20240315_... 처럼 파일명에 날짜가 포함된 사진만 복구할 수 있습니다.\n\n'PhotoFit_001'처럼 날짜 정보가 없는 파일명은 복구할 수 없습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (preview.matchedCount > 0) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.testTag("confirm_repair_dates_button")
                ) {
                    Text(
                        text = "${String.format("%,d", preview.matchedCount)}장 날짜 복구 시작",
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("확인")
                }
            }
        },
        dismissButton = {
            if (preview.matchedCount > 0) {
                TextButton(onClick = onDismiss) {
                    Text("취소")
                }
            }
        }
    )
}
