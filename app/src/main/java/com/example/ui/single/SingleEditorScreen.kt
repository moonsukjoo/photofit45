package com.example.ui.single

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoSizeSelectActual
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FilterPreset
import com.example.model.ImageAdjustment
import com.example.model.ImageFormat
import com.example.model.ResizeMode
import com.example.ui.components.CompressionQualitySelector
import com.example.ui.components.LabeledSlider
import com.example.ui.components.OptionSwitchCard
import com.example.ui.components.SavingsBadge
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.util.ImageUtils
import com.example.viewmodel.ImageEditorViewModel
import com.example.viewmodel.SingleEditUiState
import kotlin.math.roundToInt

private enum class ToolTab(val title: String) {
    COMPRESS("용량 압축"),
    RESIZE("크기/회전"),
    FORMAT("포맷 변환"),
    FILTER("필터/보정"),
    WATERMARK("워터마크")
}

@Composable
fun SingleEditorScreen(
    viewModel: ImageEditorViewModel,
    uiState: SingleEditUiState,
    modifier: Modifier = Modifier,
    onShare: (Uri) -> Unit
) {
    val context = LocalContext.current
    val singlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.loadSingleImage(uri)
        }
    }

    if (uiState.selectedUri == null) {
        SingleEditorEmptyState(
            onPickImage = {
                singlePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            modifier = modifier
        )
    } else {
        SingleEditorWorkspace(
            viewModel = viewModel,
            uiState = uiState,
            onChangePhoto = {
                singlePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onSave = {
                viewModel.saveSingleImage(context) { /* handled via state */ }
            },
            onShare = {
                viewModel.shareSingleImage(context) { shareUri ->
                    onShare(shareUri)
                }
            },
            modifier = modifier
        )
    }
}

@Composable
private fun SingleEditorEmptyState(
    onPickImage: () -> Unit,
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
                .size(110.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AddPhotoAlternate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "편집할 사진을 선택하세요",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "용량 줄이기, 사이즈 조절, 포맷 변환 및\n감성 필터 보정을 한 곳에서 자유롭게 편집할 수 있습니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onPickImage,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("pick_single_image_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "갤러리에서 사진 불러오기",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Feature highlights row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FeatureHighlightPill(
                icon = Icons.Default.Compress,
                title = "용량 대폭 압축",
                modifier = Modifier.weight(1f)
            )
            FeatureHighlightPill(
                icon = Icons.Default.PhotoSizeSelectActual,
                title = "사이즈 조절",
                modifier = Modifier.weight(1f)
            )
            FeatureHighlightPill(
                icon = Icons.Default.Tune,
                title = "감성 필터 보정",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FeatureHighlightPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SingleEditorWorkspace(
    viewModel: ImageEditorViewModel,
    uiState: SingleEditUiState,
    onChangePhoto: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedToolTab by remember { mutableStateOf(ToolTab.COMPRESS) }
    var isHoldingOriginal by remember { mutableStateOf(false) }
    var showExifDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val activeBitmap = if (isHoldingOriginal) {
        uiState.previewBitmap
    } else {
        uiState.filteredPreviewBitmap ?: uiState.previewBitmap
    }

    if (showExifDialog && uiState.selectedUri != null) {
        val exifDetails = remember(uiState.selectedUri) {
            ImageUtils.getExifDetails(context, uiState.selectedUri)
        }
        com.example.ui.components.ExifDetailDialog(
            details = exifDetails,
            fileName = uiState.metadata?.name ?: "사진",
            onDismiss = { showExifDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Action Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Change photo button
                FilledTonalButton(
                    onClick = onChangePhoto,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "다른 사진 선택",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("다른 사진", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                // File name & original size
                uiState.metadata?.let { meta ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = meta.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${meta.width}×${meta.height} • ${ImageUtils.formatBytes(meta.sizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action Buttons (EXIF, Share & Save)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showExifDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "EXIF 메타데이터 상세",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    IconButton(
                        onClick = onShare,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("share_single_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "공유",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Button(
                        onClick = onSave,
                        enabled = !uiState.isSaving,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("save_single_button")
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.SaveAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("저장", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Post-save notification & on-demand delete original button
        if (uiState.saveSuccessUri != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                color = SuccessGreen.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "갤러리에 새 사진 저장 완료!",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                        Text(
                            text = "기기 저장 공간을 확보하려면 원본을 삭제할 수 있습니다.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { viewModel.requestDeleteSingleOriginal() },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("delete_original_single_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("원본 삭제", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Preview Canvas Viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.05f)
                .background(Color(0xFF0F121A)),
            contentAlignment = Alignment.Center
        ) {
            if (activeBitmap != null) {
                Image(
                    bitmap = activeBitmap.asImageBitmap(),
                    contentDescription = "편집 미리보기",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentScale = ContentScale.Fit
                )
            } else if (uiState.isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            // Real-time Estimated Reduction Floating Badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
            ) {
                val origBytes = uiState.metadata?.sizeBytes ?: 0L
                val estBytes = uiState.estimatedSizeBytes

                if (estBytes != null && origBytes > 0) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${ImageUtils.formatBytes(origBytes)} → ",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                            Text(
                                text = ImageUtils.formatBytes(estBytes),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            SavingsBadge(originalBytes = origBytes, newBytes = estBytes)
                        }
                    }
                }
            }

            // "Hold to compare with original" floating pill at bottom right
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isHoldingOriginal = true
                                tryAwaitRelease()
                                isHoldingOriginal = false
                            }
                        )
                    },
                shape = RoundedCornerShape(16.dp),
                color = if (isHoldingOriginal) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.65f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Compare,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isHoldingOriginal) "원본 표시 중" else "누르고 있어 원본 비교",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Tool Tabs Navigation
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            TabRow(
                selectedTabIndex = ToolTab.entries.indexOf(selectedToolTab),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                ToolTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedToolTab == tab,
                        onClick = { selectedToolTab = tab },
                        text = {
                            Text(
                                text = tab.title,
                                fontSize = 13.sp,
                                fontWeight = if (selectedToolTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        }

        // Active Tool Options Panel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f)
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            when (selectedToolTab) {
                ToolTab.COMPRESS -> CompressToolPanel(viewModel, uiState)
                ToolTab.RESIZE -> ResizeToolPanel(viewModel, uiState)
                ToolTab.FORMAT -> FormatToolPanel(viewModel, uiState)
                ToolTab.FILTER -> FilterToolPanel(viewModel, uiState)
                ToolTab.WATERMARK -> WatermarkToolPanel(viewModel, uiState)
            }
        }
    }
}

// -------------------------------------------------------------
// Sub-Panel: Compress & Quality
// -------------------------------------------------------------
@Composable
private fun CompressToolPanel(
    viewModel: ImageEditorViewModel,
    uiState: SingleEditUiState
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "압축 강도 선택 (예상 용량 감소)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Preset quality tier cards (최하 / 중간 / 최대 압축 + 9MB examples)
        CompressionQualitySelector(
            selectedQuality = uiState.config.quality,
            onQualitySelected = { q -> viewModel.updateSingleQuality(q) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Quality slider
        LabeledSlider(
            label = "세부 화질 직접 조절 (높을수록 고화질·큰 용량)",
            value = uiState.config.quality.toFloat(),
            onValueChange = { viewModel.updateSingleQuality(it.roundToInt()) },
            valueRange = 1f..100f,
            unit = "%",
            defaultValue = 75f,
            onReset = { viewModel.updateSingleQuality(75) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Target File Size limiter option
        OptionSwitchCard(
            title = "목표 용량 이하로 맞추기",
            subtitle = if (uiState.config.targetMaxKb != null) {
                "최대 ${uiState.config.targetMaxKb} KB 이하가 되도록 화질과 크기를 자동 조절합니다."
            } else {
                "원하는 파일 크기(KB) 상한선을 설정할 수 있습니다."
            },
            checked = uiState.config.targetMaxKb != null,
            onCheckedChange = { checked ->
                if (checked) {
                    val defaultTarget = ((uiState.metadata?.sizeBytes ?: 2_000_000L) / 2048).coerceAtLeast(300).toInt()
                    viewModel.updateSingleTargetKb(defaultTarget)
                } else {
                    viewModel.updateSingleTargetKb(null)
                }
            },
            icon = Icons.Default.Compress
        )

        if (uiState.config.targetMaxKb != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(300, 500, 1000, 2000).forEach { kb ->
                    FilterChip(
                        selected = uiState.config.targetMaxKb == kb,
                        onClick = { viewModel.updateSingleTargetKb(kb) },
                        label = { Text(if (kb >= 1000) "${kb / 1000}MB" else "${kb}KB", fontSize = 12.sp) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Preserve Original Date toggle
        OptionSwitchCard(
            title = "원본 촬영·생성 날짜 유지 (갤러리 정렬 유지)",
            subtitle = "저장 시 원본 사진의 촬영 날짜를 유지해 갤러리에서 최신 날짜로 올라오는 현상을 막습니다.",
            checked = uiState.config.preserveOriginalDate,
            onCheckedChange = { viewModel.updateSinglePreserveDate(it) },
            icon = Icons.Default.Schedule,
            testTag = "single_preserve_date_toggle"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Delete Original Toggle Option (Requested by user!)
        OptionSwitchCard(
            title = "저장 시 원본 사진 삭제",
            subtitle = "새로 압축된 이미지를 저장한 뒤, 원본 사진을 삭제하여 기기 용량을 절약합니다. (시스템 확인 창이 나타납니다)",
            checked = uiState.config.deleteOriginalOnSave,
            onCheckedChange = { viewModel.updateSingleDeleteOriginal(it) },
            highlight = true,
            icon = Icons.Default.DeleteOutline,
            testTag = "delete_original_toggle"
        )
    }
}

// -------------------------------------------------------------
// Sub-Panel: Resize & Rotation
// -------------------------------------------------------------
@Composable
private fun ResizeToolPanel(
    viewModel: ImageEditorViewModel,
    uiState: SingleEditUiState
) {
    val meta = uiState.metadata
    val (targetW, targetH) = ImageUtils.calculateTargetDimensions(
        meta?.width ?: 100, meta?.height ?: 100,
        uiState.config.resizeMode,
        uiState.config.customWidth, uiState.config.customHeight
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        // Current vs Target Dimension Banner
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "현재 해상도",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${meta?.width ?: 0} × ${meta?.height ?: 0} px",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = Icons.Default.Transform,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "변경될 해상도",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$targetW × $targetH px",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "크기 비율 선택",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Preset Resize chips
        val modes = listOf(
            ResizeMode.ORIGINAL,
            ResizeMode.PERCENT_75,
            ResizeMode.PERCENT_50,
            ResizeMode.PERCENT_25,
            ResizeMode.FHD_1080P,
            ResizeMode.HD_720P,
            ResizeMode.INSTA_FEED,
            ResizeMode.KAKAO_PROFILE,
            ResizeMode.WEB_THUMB,
            ResizeMode.CUSTOM
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(modes) { mode ->
                FilterChip(
                    selected = uiState.config.resizeMode == mode,
                    onClick = { viewModel.updateSingleResizeMode(mode) },
                    label = { Text(mode.displayName, fontSize = 12.sp) }
                )
            }
        }

        // Custom inputs if CUSTOM is selected
        if (uiState.config.resizeMode == ResizeMode.CUSTOM) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.config.customWidth.toString(),
                    onValueChange = { str ->
                        val w = str.filter { it.isDigit() }.toIntOrNull() ?: 1
                        val h = if (uiState.config.lockAspectRatio && meta != null && meta.width > 0) {
                            ((w.toFloat() / meta.width.toFloat()) * meta.height).roundToInt()
                        } else uiState.config.customHeight
                        viewModel.updateSingleResizeMode(ResizeMode.CUSTOM, customWidth = w, customHeight = h)
                    },
                    label = { Text("가로(W)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                IconButton(
                    onClick = {
                        viewModel.updateSingleResizeMode(
                            ResizeMode.CUSTOM,
                            lockAspect = !uiState.config.lockAspectRatio
                        )
                    }
                ) {
                    Icon(
                        imageVector = if (uiState.config.lockAspectRatio) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "비율 고정 토글",
                        tint = if (uiState.config.lockAspectRatio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = uiState.config.customHeight.toString(),
                    onValueChange = { str ->
                        val h = str.filter { it.isDigit() }.toIntOrNull() ?: 1
                        val w = if (uiState.config.lockAspectRatio && meta != null && meta.height > 0) {
                            ((h.toFloat() / meta.height.toFloat()) * meta.width).roundToInt()
                        } else uiState.config.customWidth
                        viewModel.updateSingleResizeMode(ResizeMode.CUSTOM, customWidth = w, customHeight = h)
                    },
                    label = { Text("세로(H)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "회전 및 반전",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.rotateSingleClockwise() },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("90° 회전 (${uiState.config.adjustments.rotation}°)", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = { viewModel.flipSingleHorizontal() },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (uiState.config.adjustments.flipHorizontal) "반전 해제" else "좌우 반전",
                    fontSize = 12.sp
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Sub-Panel: Format Conversion
// -------------------------------------------------------------
@Composable
private fun FormatToolPanel(
    viewModel: ImageEditorViewModel,
    uiState: SingleEditUiState
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "변환할 이미지 포맷 선택",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        ImageFormat.entries.forEach { format ->
            val isSelected = uiState.config.format == format
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { viewModel.updateSingleFormat(format) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                ),
                border = if (isSelected) {
                    androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                } else null
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
                            Text(
                                text = format.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (format == ImageFormat.WEBP) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text(
                                        text = "추천",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = format.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "선택됨",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Sub-Panel: Filters & Fine Tuning
// -------------------------------------------------------------
@Composable
private fun FilterToolPanel(
    viewModel: ImageEditorViewModel,
    uiState: SingleEditUiState
) {
    val adj = uiState.config.adjustments

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "감성 필터 프리셋",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (!adj.isDefault) {
                OutlinedButton(
                    onClick = { viewModel.resetAdjustments() },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("초기화", fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal filter presets
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(FilterPreset.entries) { preset ->
                val isSelected = adj.filterPreset == preset
                Surface(
                    modifier = Modifier
                        .clickable { viewModel.setFilterPreset(preset) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = preset.displayName,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = preset.subtitle,
                            fontSize = 10.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "세부 보정 조절",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Brightness
        LabeledSlider(
            label = "밝기 (Brightness)",
            value = adj.brightness,
            onValueChange = { v -> viewModel.updateSingleAdjustment { it.copy(brightness = v) } },
            valueRange = -100f..100f,
            defaultValue = 0f,
            onReset = { viewModel.updateSingleAdjustment { it.copy(brightness = 0f) } }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Contrast
        LabeledSlider(
            label = "대비 (Contrast)",
            value = adj.contrast,
            onValueChange = { v -> viewModel.updateSingleAdjustment { it.copy(contrast = v) } },
            valueRange = -100f..100f,
            defaultValue = 0f,
            onReset = { viewModel.updateSingleAdjustment { it.copy(contrast = 0f) } }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Saturation
        LabeledSlider(
            label = "채도 (Saturation)",
            value = adj.saturation,
            onValueChange = { v -> viewModel.updateSingleAdjustment { it.copy(saturation = v) } },
            valueRange = -100f..100f,
            defaultValue = 0f,
            onReset = { viewModel.updateSingleAdjustment { it.copy(saturation = 0f) } }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Warmth
        LabeledSlider(
            label = "색온도 (Warmth)",
            value = adj.warmth,
            onValueChange = { v -> viewModel.updateSingleAdjustment { it.copy(warmth = v) } },
            valueRange = -100f..100f,
            defaultValue = 0f,
            onReset = { viewModel.updateSingleAdjustment { it.copy(warmth = 0f) } }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Vignette
        LabeledSlider(
            label = "비네트 효과 (Vignette)",
            value = adj.vignette,
            onValueChange = { v -> viewModel.updateSingleAdjustment { it.copy(vignette = v) } },
            valueRange = 0f..100f,
            defaultValue = 0f,
            onReset = { viewModel.updateSingleAdjustment { it.copy(vignette = 0f) } }
        )
    }
}

// -------------------------------------------------------------
// Sub-Panel: Watermark
// -------------------------------------------------------------
@Composable
private fun WatermarkToolPanel(
    viewModel: ImageEditorViewModel,
    uiState: SingleEditUiState
) {
    val config = uiState.config

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "워터마크 및 서명 설정",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = config.watermarkText,
            onValueChange = { viewModel.updateWatermark(it, config.watermarkPosition) },
            label = { Text("워터마크 텍스트 (예: © PhotoFit, 닉네임, 상호명)") },
            placeholder = { Text("텍스트를 입력하면 사진에 실시간 반영됩니다") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Preset text chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val dateStr = java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.getDefault()).format(java.util.Date())
            listOf(
                Pair("© PhotoFit", "브랜드"),
                Pair(dateStr, "촬영일"),
                Pair("SAMPLE", "샘플"),
                Pair("", "지우기")
            ).forEach { (presetText, label) ->
                FilterChip(
                    selected = config.watermarkText == presetText,
                    onClick = { viewModel.updateWatermark(presetText, config.watermarkPosition) },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "워터마크 위치",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            com.example.model.WatermarkPosition.entries.forEach { pos ->
                FilterChip(
                    selected = config.watermarkPosition == pos,
                    onClick = { viewModel.updateWatermark(config.watermarkText, pos) },
                    label = { Text(pos.displayName, fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
