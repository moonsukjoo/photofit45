package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.BatchConfig
import com.example.model.BatchFormatChoice
import com.example.model.BatchItemStatus
import com.example.model.BatchProcessItem
import com.example.model.FilterPreset
import com.example.model.ImageAdjustment
import com.example.model.ImageFormat
import com.example.model.ResizeMode
import com.example.model.SavedHistoryItem
import com.example.model.SingleEditConfig
import com.example.util.ImageMetadata
import com.example.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class AppTab(val title: String) {
    SINGLE_EDIT("단일 편집"),
    BATCH_COMPRESS("일괄 압축"),
    SAVED_GALLERY("저장 내역")
}

data class SingleEditUiState(
    val selectedUri: Uri? = null,
    val metadata: ImageMetadata? = null,
    val previewBitmap: Bitmap? = null,
    val filteredPreviewBitmap: Bitmap? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val config: SingleEditConfig = SingleEditConfig(),
    val estimatedSizeBytes: Long? = null,
    val estimatedSavingsPercent: Int? = null,
    val saveSuccessUri: Uri? = null,
    val saveSuccessBytes: Long? = null,
    val statusMessage: String? = null,
    val deleteOriginalPromptUris: List<Uri>? = null
)

data class BatchUiState(
    val items: List<BatchProcessItem> = emptyList(),
    val config: BatchConfig = BatchConfig(),
    val isProcessing: Boolean = false,
    val currentProcessingIndex: Int = 0,
    val totalOriginalBytes: Long = 0L,
    val totalProcessedBytes: Long = 0L,
    val isCompleted: Boolean = false,
    val statusMessage: String? = null,
    val deleteOriginalPromptUris: List<Uri>? = null
) {
    val totalCount: Int get() = items.size
    val selectedCount: Int get() = items.count { it.isSelected }
    val isAllSelected: Boolean get() = items.isNotEmpty() && items.all { it.isSelected }
    val selectedOriginalBytes: Long get() = items.filter { it.isSelected }.sumOf { it.originalSizeBytes }
    val completedCount: Int get() = items.count { it.status == BatchItemStatus.COMPLETED }
    val totalSavedBytes: Long
        get() = if (totalProcessedBytes in 1 until totalOriginalBytes) {
            totalOriginalBytes - totalProcessedBytes
        } else 0L
    val overallSavingsPercent: Int
        get() = if (totalOriginalBytes > 0 && totalProcessedBytes > 0) {
            val p = (1.0 - (totalProcessedBytes.toDouble() / totalOriginalBytes.toDouble())) * 100
            p.coerceAtLeast(0.0).toInt()
        } else 0
}

class ImageEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentTab = MutableStateFlow(AppTab.SINGLE_EDIT)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _singleState = MutableStateFlow(SingleEditUiState())
    val singleState: StateFlow<SingleEditUiState> = _singleState.asStateFlow()

    private val _batchState = MutableStateFlow(BatchUiState())
    val batchState: StateFlow<BatchUiState> = _batchState.asStateFlow()

    private val _savedHistory = MutableStateFlow<List<SavedHistoryItem>>(emptyList())
    val savedHistory: StateFlow<List<SavedHistoryItem>> = _savedHistory.asStateFlow()

    private var filterPreviewJob: Job? = null
    private var estimateJob: Job? = null

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    // -------------------------------------------------------------
    // Single Edit Operations
    // -------------------------------------------------------------

    fun loadSingleImage(uri: Uri) {
        viewModelScope.launch {
            _singleState.update { it.copy(isLoading = true, statusMessage = "이미지를 불러오는 중입니다...") }

            val context = getApplication<Application>()
            val meta = withContext(Dispatchers.IO) {
                ImageUtils.getImageMetadata(context, uri)
            }

            val format = when {
                meta.mimeType.contains("png", ignoreCase = true) -> ImageFormat.PNG
                meta.mimeType.contains("webp", ignoreCase = true) -> ImageFormat.WEBP
                else -> ImageFormat.JPEG
            }

            val preview = withContext(Dispatchers.IO) {
                ImageUtils.loadBitmap(context, uri, maxDimension = 1200)
            }

            _singleState.update { current ->
                current.copy(
                    selectedUri = uri,
                    metadata = meta,
                    previewBitmap = preview,
                    filteredPreviewBitmap = preview,
                    isLoading = false,
                    config = current.config.copy(
                        format = format,
                        customWidth = meta.width,
                        customHeight = meta.height,
                        adjustments = ImageAdjustment()
                    ),
                    saveSuccessUri = null,
                    saveSuccessBytes = null,
                    statusMessage = null
                )
            }

            recomputePreviewAndEstimate()
        }
    }

    fun updateSingleAdjustment(transform: (ImageAdjustment) -> ImageAdjustment) {
        _singleState.update { state ->
            val newAdj = transform(state.config.adjustments)
            state.copy(config = state.config.copy(adjustments = newAdj))
        }
        scheduleFilteredPreviewUpdate()
    }

    fun setFilterPreset(preset: FilterPreset) {
        updateSingleAdjustment { it.copy(filterPreset = preset) }
    }

    fun rotateSingleClockwise() {
        updateSingleAdjustment {
            it.copy(rotation = (it.rotation + 90) % 360)
        }
    }

    fun flipSingleHorizontal() {
        updateSingleAdjustment {
            it.copy(flipHorizontal = !it.flipHorizontal)
        }
    }

    fun resetAdjustments() {
        _singleState.update { state ->
            state.copy(config = state.config.copy(adjustments = ImageAdjustment()))
        }
        _singleState.update { it.copy(filteredPreviewBitmap = it.previewBitmap) }
        recomputePreviewAndEstimate()
    }

    fun updateSingleQuality(quality: Int) {
        _singleState.update { state ->
            state.copy(config = state.config.copy(quality = quality))
        }
        recomputePreviewAndEstimate()
    }

    fun updateSingleFormat(format: ImageFormat) {
        _singleState.update { state ->
            state.copy(config = state.config.copy(format = format))
        }
        recomputePreviewAndEstimate()
    }

    fun updateSingleResizeMode(
        mode: ResizeMode,
        customWidth: Int? = null,
        customHeight: Int? = null,
        lockAspect: Boolean? = null
    ) {
        _singleState.update { state ->
            val w = customWidth ?: state.config.customWidth
            val h = customHeight ?: state.config.customHeight
            val lock = lockAspect ?: state.config.lockAspectRatio
            state.copy(
                config = state.config.copy(
                    resizeMode = mode,
                    customWidth = w,
                    customHeight = h,
                    lockAspectRatio = lock
                )
            )
        }
        recomputePreviewAndEstimate()
    }

    fun updateSingleTargetKb(targetKb: Int?) {
        _singleState.update { state ->
            state.copy(config = state.config.copy(targetMaxKb = targetKb))
        }
        recomputePreviewAndEstimate()
    }

    fun updateSingleDeleteOriginal(delete: Boolean) {
        _singleState.update { state ->
            state.copy(config = state.config.copy(deleteOriginalOnSave = delete))
        }
    }

    fun updateWatermark(text: String, position: com.example.model.WatermarkPosition) {
        _singleState.update { s ->
            s.copy(config = s.config.copy(watermarkText = text, watermarkPosition = position))
        }
        scheduleFilteredPreviewUpdate()
    }

    private fun scheduleFilteredPreviewUpdate() {
        filterPreviewJob?.cancel()
        filterPreviewJob = viewModelScope.launch(Dispatchers.Default) {
            delay(40) // debounce rapid slider drag
            val state = _singleState.value
            val source = state.previewBitmap ?: return@launch
            val adj = state.config.adjustments

            val filtered = ImageUtils.applyAdjustments(source, adj)
            val withWatermark = if (state.config.watermarkText.isNotBlank()) {
                val wm = ImageUtils.drawWatermark(filtered, state.config.watermarkText, state.config.watermarkPosition)
                if (wm != filtered && filtered != source) filtered.recycle()
                wm
            } else filtered

            withContext(Dispatchers.Main) {
                _singleState.update { it.copy(filteredPreviewBitmap = withWatermark) }
            }
            recomputePreviewAndEstimate()
        }
    }

    private fun recomputePreviewAndEstimate() {
        estimateJob?.cancel()
        estimateJob = viewModelScope.launch(Dispatchers.Default) {
            delay(120)
            val state = _singleState.value
            val preview = state.filteredPreviewBitmap ?: state.previewBitmap ?: return@launch
            val meta = state.metadata ?: return@launch

            // Compute rough target dimension ratio
            val (targetW, targetH) = ImageUtils.calculateTargetDimensions(
                meta.width, meta.height, state.config.resizeMode,
                state.config.customWidth, state.config.customHeight
            )

            // Compress sample preview
            val sampleBytes = ImageUtils.compressBitmap(
                preview, state.config.format, state.config.quality
            )

            // Scale estimate by full-res vs preview pixel ratio
            val fullPixelCount = targetW.toDouble() * targetH.toDouble()
            val previewPixelCount = preview.width.toDouble() * preview.height.toDouble()
            val ratio = if (previewPixelCount > 0) fullPixelCount / previewPixelCount else 1.0

            var estimatedBytes = (sampleBytes.size * ratio).toLong()

            // Target max KB override
            val targetMaxBytes = state.config.targetMaxKb?.let { it * 1024L }
            if (targetMaxBytes != null && estimatedBytes > targetMaxBytes) {
                estimatedBytes = (targetMaxBytes * 0.95).toLong()
            }

            val savings = if (meta.sizeBytes > 0) {
                val diff = (1.0 - (estimatedBytes.toDouble() / meta.sizeBytes.toDouble())) * 100
                diff.toInt()
            } else 0

            withContext(Dispatchers.Main) {
                _singleState.update {
                    it.copy(
                        estimatedSizeBytes = estimatedBytes,
                        estimatedSavingsPercent = savings
                    )
                }
            }
        }
    }

    fun saveSingleImage(context: Context, onComplete: (Uri?) -> Unit) {
        val state = _singleState.value
        val uri = state.selectedUri ?: return
        val meta = state.metadata ?: return

        viewModelScope.launch {
            _singleState.update { it.copy(isSaving = true, statusMessage = "이미지를 고화질로 렌더링 및 압축 중...") }

            val savedResult: Pair<Uri, Long>? = withContext(Dispatchers.IO) {
                try {
                    // 1. Load full resolution bitmap
                    val fullBitmap = ImageUtils.loadBitmap(context, uri, maxDimension = 4096)
                        ?: return@withContext null

                    // 2. Apply adjustments
                    val adjusted = ImageUtils.applyAdjustments(fullBitmap, state.config.adjustments)
                    if (adjusted != fullBitmap) {
                        fullBitmap.recycle()
                    }

                    // 3. Resize if requested
                    val (targetW, targetH) = ImageUtils.calculateTargetDimensions(
                        meta.width, meta.height, state.config.resizeMode,
                        state.config.customWidth, state.config.customHeight
                    )
                    val resized = ImageUtils.resizeBitmap(adjusted, targetW, targetH)
                    if (resized != adjusted) {
                        adjusted.recycle()
                    }

                    val watermarked = if (state.config.watermarkText.isNotBlank()) {
                        val wm = ImageUtils.drawWatermark(resized, state.config.watermarkText, state.config.watermarkPosition)
                        if (wm != resized && resized != adjusted) resized.recycle()
                        wm
                    } else resized

                    // 4. Compress to bytes
                    val bytes = if (state.config.targetMaxKb != null) {
                        val (compBytes, _) = ImageUtils.compressToTargetSize(
                            watermarked, state.config.format, state.config.targetMaxKb * 1024L
                        )
                        compBytes
                    } else {
                        ImageUtils.compressBitmap(watermarked, state.config.format, state.config.quality)
                    }

                    // 5. Save to MediaStore Gallery
                    val resultUri = ImageUtils.saveBitmapToGallery(
                        context, bytes, meta.name, state.config.format
                    )

                    if (watermarked != resized && watermarked != adjusted) {
                        watermarked.recycle()
                    }
                    resized.recycle()

                    if (resultUri != null) {
                        Pair(resultUri, bytes.size.toLong())
                    } else null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            if (savedResult != null) {
                val (resultUri, sizeBytes) = savedResult
                val historyItem = SavedHistoryItem(
                    id = UUID.randomUUID().toString(),
                    uri = resultUri,
                    fileName = meta.name,
                    originalSizeBytes = meta.sizeBytes,
                    savedSizeBytes = sizeBytes,
                    width = meta.width,
                    height = meta.height,
                    format = state.config.format
                )
                _savedHistory.update { listOf(historyItem) + it }

                _singleState.update {
                    it.copy(
                        isSaving = false,
                        saveSuccessUri = resultUri,
                        saveSuccessBytes = sizeBytes,
                        statusMessage = "사진이 갤러리에 성공적으로 저장되었습니다!"
                    )
                }

                if (state.config.deleteOriginalOnSave && state.selectedUri != null) {
                    _singleState.update { it.copy(deleteOriginalPromptUris = listOf(uri)) }
                }

                onComplete(resultUri)
            } else {
                _singleState.update {
                    it.copy(
                        isSaving = false,
                        statusMessage = "저장 중 오류가 발생했습니다. 다시 시도해 주세요."
                    )
                }
                onComplete(null)
            }
        }
    }

    fun shareSingleImage(context: Context, onReady: (Uri) -> Unit) {
        val state = _singleState.value
        val uri = state.selectedUri ?: return
        val meta = state.metadata ?: return

        viewModelScope.launch {
            _singleState.update { it.copy(isSaving = true, statusMessage = "공유 파일 준비 중...") }

            val shareUri = withContext(Dispatchers.IO) {
                try {
                    val fullBitmap = ImageUtils.loadBitmap(context, uri, maxDimension = 3000)
                        ?: return@withContext null
                    val adjusted = ImageUtils.applyAdjustments(fullBitmap, state.config.adjustments)
                    val (targetW, targetH) = ImageUtils.calculateTargetDimensions(
                        meta.width, meta.height, state.config.resizeMode,
                        state.config.customWidth, state.config.customHeight
                    )
                    val resized = ImageUtils.resizeBitmap(adjusted, targetW, targetH)
                    val watermarked = if (state.config.watermarkText.isNotBlank()) {
                        ImageUtils.drawWatermark(resized, state.config.watermarkText, state.config.watermarkPosition)
                    } else resized
                    val bytes = ImageUtils.compressBitmap(watermarked, state.config.format, state.config.quality)
                    resized.recycle()
                    ImageUtils.saveToCacheForSharing(context, bytes, meta.name, state.config.format)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            _singleState.update { it.copy(isSaving = false, statusMessage = null) }
            if (shareUri != null) {
                onReady(shareUri)
            }
        }
    }

    // -------------------------------------------------------------
    // Batch Processing Operations
    // -------------------------------------------------------------

    fun addBatchImages(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            val newItems = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    val meta = ImageUtils.getImageMetadata(context, uri)
                    BatchProcessItem(
                        id = UUID.randomUUID().toString(),
                        uri = uri,
                        fileName = meta.name,
                        originalSizeBytes = meta.sizeBytes,
                        originalWidth = meta.width,
                        originalHeight = meta.height,
                        mimeType = meta.mimeType,
                        status = BatchItemStatus.PENDING
                    )
                }
            }

            _batchState.update { state ->
                val combined = state.items + newItems
                val totalOriginal = combined.sumOf { it.originalSizeBytes }
                state.copy(
                    items = combined,
                    totalOriginalBytes = totalOriginal,
                    isCompleted = false,
                    statusMessage = "${combined.size}개 이미지가 추가되었습니다."
                )
            }
        }
    }

    fun removeBatchItem(id: String) {
        _batchState.update { state ->
            val updated = state.items.filterNot { it.id == id }
            state.copy(
                items = updated,
                totalOriginalBytes = updated.sumOf { it.originalSizeBytes }
            )
        }
    }

    fun clearBatch() {
        _batchState.update {
            BatchUiState(config = it.config)
        }
    }

    fun selectAllBatchItems(selected: Boolean) {
        _batchState.update { state ->
            val updated = state.items.map { it.copy(isSelected = selected) }
            state.copy(items = updated)
        }
    }

    fun toggleBatchItemSelection(id: String) {
        _batchState.update { state ->
            val updated = state.items.map {
                if (it.id == id) it.copy(isSelected = !it.isSelected) else it
            }
            state.copy(items = updated)
        }
    }

    fun removeSelectedBatchItems() {
        _batchState.update { state ->
            val remaining = state.items.filterNot { it.isSelected }
            state.copy(
                items = remaining,
                totalOriginalBytes = remaining.sumOf { it.originalSizeBytes }
            )
        }
    }

    fun loadDevicePhotos(context: Context) {
        viewModelScope.launch {
            _batchState.update { it.copy(statusMessage = "기기 갤러리 사진을 불러오는 중...") }
            val uris = withContext(Dispatchers.IO) {
                ImageUtils.queryDevicePhotos(context, limit = 500)
            }
            if (uris.isNotEmpty()) {
                addBatchImages(context, uris)
            } else {
                _batchState.update { it.copy(statusMessage = "불러올 수 있는 기기 사진이 없습니다.") }
            }
        }
    }

    fun requestDeleteCompletedBatchOriginals() {
        val completedUris = _batchState.value.items
            .filter { it.status == BatchItemStatus.COMPLETED }
            .map { it.uri }
        if (completedUris.isNotEmpty()) {
            _batchState.update { it.copy(deleteOriginalPromptUris = completedUris) }
        } else {
            _batchState.update { it.copy(statusMessage = "삭제할 수 있는 완료된 원본 파일이 없습니다.") }
        }
    }

    fun requestDeleteSingleOriginal() {
        val uri = _singleState.value.selectedUri
        if (uri != null) {
            _singleState.update { it.copy(deleteOriginalPromptUris = listOf(uri)) }
        }
    }

    fun exportCompletedBatchToPdf(context: Context, onReady: (Uri) -> Unit) {
        val completedUris = _batchState.value.items
            .filter { it.status == BatchItemStatus.COMPLETED && it.resultUri != null }
            .mapNotNull { it.resultUri }
        if (completedUris.isEmpty()) {
            _batchState.update { it.copy(statusMessage = "PDF로 변환할 완료된 이미지가 없습니다.") }
            return
        }
        viewModelScope.launch {
            _batchState.update { it.copy(statusMessage = "${completedUris.size}장 사진을 하나의 PDF 문서로 묶는 중...") }
            val pdfUri = withContext(Dispatchers.IO) {
                ImageUtils.exportImagesToPdf(context, completedUris, "PhotoFit_Document")
            }
            if (pdfUri != null) {
                _batchState.update { it.copy(statusMessage = "PDF 문서가 성공적으로 생성되었습니다!") }
                onReady(pdfUri)
            } else {
                _batchState.update { it.copy(statusMessage = "PDF 생성 중 오류가 발생했습니다.") }
            }
        }
    }

    fun updateBatchConfig(config: BatchConfig) {
        _batchState.update { it.copy(config = config) }
    }

    fun startBatchProcessing(context: Context) {
        val state = _batchState.value
        if (state.items.isEmpty() || state.isProcessing) return

        // Process selected items or all if none selected
        val targetItems = if (state.items.any { it.isSelected }) {
            state.items.filter { it.isSelected }
        } else {
            state.items
        }

        viewModelScope.launch {
            _batchState.update {
                it.copy(
                    isProcessing = true,
                    isCompleted = false,
                    currentProcessingIndex = 0,
                    totalProcessedBytes = 0L,
                    statusMessage = "${targetItems.size}개 일괄 압축 처리를 시작합니다..."
                )
            }

            var processedSum = 0L
            val completedList = mutableListOf<BatchProcessItem>()
            val itemsToProcess = targetItems
            val config = state.config

            for (index in itemsToProcess.indices) {
                val item = itemsToProcess[index]
                _batchState.update {
                    it.copy(
                        currentProcessingIndex = index + 1,
                        statusMessage = "(${index + 1}/${itemsToProcess.size}) '${item.fileName}' 압축 중..."
                    )
                }

                val processedItem = withContext(Dispatchers.IO) {
                    try {
                        val bitmap = ImageUtils.loadBitmap(context, item.uri, maxDimension = 3000)
                            ?: return@withContext item.copy(
                                status = BatchItemStatus.FAILED,
                                errorMessage = "이미지 디코딩 실패"
                            )

                        // Determine target format
                        val targetFormat = config.formatChoice.format ?: when {
                            item.mimeType.contains("png", ignoreCase = true) -> ImageFormat.PNG
                            item.mimeType.contains("webp", ignoreCase = true) -> ImageFormat.WEBP
                            else -> ImageFormat.JPEG
                        }

                        // Resize
                        val (targetW, targetH) = ImageUtils.calculateTargetDimensions(
                            item.originalWidth, item.originalHeight, config.resizeMode
                        )
                        val resized = ImageUtils.resizeBitmap(bitmap, targetW, targetH)
                        if (resized != bitmap) {
                            bitmap.recycle()
                        }

                        // Watermark if configured
                        val finalBitmap = if (config.watermarkText.isNotBlank()) {
                            ImageUtils.drawWatermark(resized, config.watermarkText, config.watermarkPosition)
                        } else {
                            resized
                        }

                        // Compress
                        val bytes = if (config.targetMaxKb != null) {
                            val (compBytes, _) = ImageUtils.compressToTargetSize(
                                finalBitmap, targetFormat, config.targetMaxKb * 1024L
                            )
                            compBytes
                        } else {
                            ImageUtils.compressBitmap(finalBitmap, targetFormat, config.quality)
                        }

                        // Generate clean filename
                        val dateStr = ImageUtils.getExifDateFormatted(context, item.uri)
                        val targetFileName = ImageUtils.generateTargetFileName(
                            config.renamePattern,
                            config.customPrefix,
                            item.fileName,
                            index,
                            targetFormat,
                            dateStr
                        )

                        // Save to gallery
                        val savedUri = ImageUtils.saveBitmapToGallery(
                            context, bytes, targetFileName, targetFormat, "PhotoFit_Batch"
                        )

                        if (finalBitmap != resized) {
                            finalBitmap.recycle()
                        }
                        resized.recycle()

                        if (savedUri != null) {
                            item.copy(
                                fileName = targetFileName,
                                status = BatchItemStatus.COMPLETED,
                                processedSizeBytes = bytes.size.toLong(),
                                resultUri = savedUri
                            )
                        } else {
                            item.copy(
                                status = BatchItemStatus.FAILED,
                                errorMessage = "저장 실패"
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        item.copy(
                            status = BatchItemStatus.FAILED,
                            errorMessage = e.message ?: "처리 중 오류"
                        )
                    }
                }

                completedList.add(processedItem)
                if (processedItem.processedSizeBytes != null) {
                    processedSum += processedItem.processedSizeBytes
                }

                _batchState.update { s ->
                    val updatedItems = s.items.toMutableList()
                    val targetIdx = updatedItems.indexOfFirst { it.id == item.id }
                    if (targetIdx != -1) {
                        updatedItems[targetIdx] = processedItem
                    }
                    s.copy(
                        items = updatedItems,
                        totalProcessedBytes = processedSum
                    )
                }
            }

            // Record to saved history
            val newHistoryItems = completedList
                .filter { it.status == BatchItemStatus.COMPLETED && it.resultUri != null }
                .map { item ->
                    SavedHistoryItem(
                        id = UUID.randomUUID().toString(),
                        uri = item.resultUri!!,
                        fileName = item.fileName,
                        originalSizeBytes = item.originalSizeBytes,
                        savedSizeBytes = item.processedSizeBytes ?: 0L,
                        width = item.originalWidth,
                        height = item.originalHeight,
                        format = config.formatChoice.format ?: ImageFormat.JPEG
                    )
                }
            _savedHistory.update { newHistoryItems + it }

            val successfulCount = completedList.count { it.status == BatchItemStatus.COMPLETED }
            val savedMb = (state.totalOriginalBytes - processedSum) / (1024.0 * 1024.0)

            _batchState.update { s ->
                s.copy(
                    isProcessing = false,
                    isCompleted = true,
                    statusMessage = "총 ${successfulCount}개 이미지 압축 완료! (약 ${String.format("%.1f", savedMb)} MB 절약)"
                )
            }

            // If user selected to delete originals
            if (config.deleteOriginal) {
                val originalUrisToDelete = completedList
                    .filter { it.status == BatchItemStatus.COMPLETED }
                    .map { it.uri }
                if (originalUrisToDelete.isNotEmpty()) {
                    _batchState.update { it.copy(deleteOriginalPromptUris = originalUrisToDelete) }
                }
            }
        }
    }

    // -------------------------------------------------------------
    // Deletion Request Management (Scoped Storage safe)
    // -------------------------------------------------------------

    fun getDeleteIntentSenderRequest(context: Context, uris: List<Uri>): IntentSenderRequest? {
        return ImageUtils.createDeleteIntentRequest(context, uris)
    }

    fun onOriginalsDeleted(deletedUris: List<Uri>) {
        _singleState.update { it.copy(deleteOriginalPromptUris = null, statusMessage = "원본 이미지가 안전하게 삭제되었습니다.") }
        _batchState.update { it.copy(deleteOriginalPromptUris = null, statusMessage = "원본 ${deletedUris.size}개 이미지가 삭제되었습니다.") }
    }

    fun dismissDeletePrompt() {
        _singleState.update { it.copy(deleteOriginalPromptUris = null) }
        _batchState.update { it.copy(deleteOriginalPromptUris = null) }
    }

    fun clearSavedHistory() {
        _savedHistory.value = emptyList()
    }
}
