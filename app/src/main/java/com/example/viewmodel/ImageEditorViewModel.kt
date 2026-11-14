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
    val isLoadingGallery: Boolean = false,
    val isProcessing: Boolean = false,
    val currentProcessingIndex: Int = 0,
    val processingTargetCount: Int = 0,
    val totalOriginalBytes: Long = 0L,
    val processedOriginalBytes: Long = 0L,
    val totalProcessedBytes: Long = 0L,
    val isCompleted: Boolean = false,
    val statusMessage: String? = null,
    val deleteOriginalPromptUris: List<Uri>? = null,
    val pendingDeleteQueue: List<Uri> = emptyList(),
    val totalDeleteBatchCount: Int = 0
) {
    val totalCount: Int get() = items.size

    val selectedCount: Int
        get() {
            var count = 0
            for (item in items) {
                if (item.isSelected) count++
            }
            return count
        }

    val isAllSelected: Boolean
        get() {
            if (items.isEmpty()) return false
            for (item in items) {
                if (!item.isSelected) return false
            }
            return true
        }

    val selectedOriginalBytes: Long
        get() {
            var sum = 0L
            for (item in items) {
                if (item.isSelected) sum += item.originalSizeBytes
            }
            return sum
        }

    val completedCount: Int
        get() {
            var count = 0
            for (item in items) {
                if (item.status == BatchItemStatus.COMPLETED) count++
            }
            return count
        }

    val comparisonOriginalBytes: Long
        get() = if (processedOriginalBytes > 0L) processedOriginalBytes else totalOriginalBytes

    val totalSavedBytes: Long
        get() {
            val base = comparisonOriginalBytes
            return if (totalProcessedBytes in 1 until base) {
                base - totalProcessedBytes
            } else 0L
        }

    val overallSavingsPercent: Int
        get() {
            val base = comparisonOriginalBytes
            return if (base > 0 && totalProcessedBytes > 0) {
                val p = (1.0 - (totalProcessedBytes.toDouble() / base.toDouble())) * 100
                p.coerceAtLeast(0.0).toInt()
            } else 0
        }
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
            val updated = state.items.map { item ->
                if (item.isSelected == selected) item else item.copy(isSelected = selected)
            }
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
        if (_batchState.value.isLoadingGallery) return
        viewModelScope.launch {
            _batchState.update {
                it.copy(
                    isLoadingGallery = true,
                    statusMessage = "기기 갤러리의 모든 사진을 스캔하는 중..."
                )
            }
            val newItems = withContext(Dispatchers.IO) {
                val entries = ImageUtils.queryDevicePhotosWithMetadata(context)
                entries.map { entry ->
                    BatchProcessItem(
                        id = entry.uri.toString(),
                        uri = entry.uri,
                        fileName = entry.name,
                        originalSizeBytes = entry.sizeBytes,
                        originalWidth = entry.width,
                        originalHeight = entry.height,
                        mimeType = entry.mimeType,
                        status = BatchItemStatus.PENDING
                    )
                }
            }
            if (newItems.isNotEmpty()) {
                val mergeResult = withContext(Dispatchers.Default) {
                    val currentItems = _batchState.value.items
                    val existingUris = HashSet<Uri>(currentItems.size + 16)
                    for (item in currentItems) {
                        existingUris.add(item.uri)
                    }
                    val filteredNew = newItems.filterNot { it.uri in existingUris }
                    val combined = currentItems + filteredNew
                    var totalOriginal = 0L
                    for (item in combined) {
                        totalOriginal += item.originalSizeBytes
                    }
                    Triple(combined, filteredNew.size, totalOriginal)
                }
                val (combined, addedCount, totalOriginal) = mergeResult
                _batchState.update { state ->
                    state.copy(
                        items = combined,
                        isLoadingGallery = false,
                        totalOriginalBytes = totalOriginal,
                        isCompleted = false,
                        statusMessage = if (addedCount > 0) {
                            "총 ${String.format("%,d", addedCount)}개의 기기 사진을 불러왔습니다."
                        } else {
                            "새로 추가할 기기 사진이 없습니다."
                        }
                    )
                }
            } else {
                _batchState.update {
                    it.copy(
                        isLoadingGallery = false,
                        statusMessage = "불러올 수 있는 기기 사진이 없습니다. (권한 또는 갤러리를 확인해 주세요)"
                    )
                }
            }
        }
    }

    private fun enqueueBatchDelete(uris: List<Uri>) {
        if (uris.isEmpty()) {
            _batchState.update { it.copy(statusMessage = "삭제할 수 있는 완료된 원본 파일이 없습니다.") }
            return
        }
        val firstChunk = uris.take(ImageUtils.MAX_DELETE_BATCH_SIZE)
        val remaining = if (uris.size > ImageUtils.MAX_DELETE_BATCH_SIZE) {
            uris.drop(ImageUtils.MAX_DELETE_BATCH_SIZE)
        } else {
            emptyList()
        }
        _batchState.update {
            it.copy(
                deleteOriginalPromptUris = firstChunk,
                pendingDeleteQueue = remaining,
                totalDeleteBatchCount = uris.size
            )
        }
    }

    fun requestDeleteCompletedBatchOriginals() {
        val completedUris = _batchState.value.items
            .filter { it.status == BatchItemStatus.COMPLETED }
            .map { it.uri }
        enqueueBatchDelete(completedUris)
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
        if (completedUris.size > 100) {
            _batchState.update {
                it.copy(statusMessage = "PDF 변환은 한 번에 최대 100장까지 지원됩니다. (현재 ${completedUris.size}장)")
            }
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
        val itemsToProcess = if (state.items.any { it.isSelected }) {
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
                    processingTargetCount = itemsToProcess.size,
                    processedOriginalBytes = 0L,
                    totalProcessedBytes = 0L,
                    statusMessage = "${String.format("%,d", itemsToProcess.size)}개 일괄 압축 처리를 시작합니다..."
                )
            }

            var processedSum = 0L
            var processedOrigSum = 0L
            var successfulCount = 0
            val originalUrisToDelete = ArrayList<Uri>(itemsToProcess.size)
            val recentCompletedForHistory = ArrayDeque<BatchProcessItem>(500)
            val config = state.config

            val workingList = state.items.toMutableList()
            val indexById = HashMap<String, Int>(workingList.size)
            for (i in workingList.indices) {
                indexById[workingList[i].id] = i
            }

            var lastUiSyncTimeMs = 0L

            for (index in itemsToProcess.indices) {
                val item = itemsToProcess[index]

                val processedItem = withContext(Dispatchers.IO) {
                    try {
                        val bitmap = ImageUtils.loadBitmap(context, item.uri, maxDimension = 3000)
                            ?: return@withContext item.copy(
                                status = BatchItemStatus.FAILED,
                                errorMessage = "이미지 디코딩 실패"
                            )

                        val resolvedOrigW = if (item.originalWidth > 0) item.originalWidth else bitmap.width
                        val resolvedOrigH = if (item.originalHeight > 0) item.originalHeight else bitmap.height

                        // Determine target format
                        val targetFormat = config.formatChoice.format ?: when {
                            item.mimeType.contains("png", ignoreCase = true) -> ImageFormat.PNG
                            item.mimeType.contains("webp", ignoreCase = true) -> ImageFormat.WEBP
                            else -> ImageFormat.JPEG
                        }

                        // Resize
                        val (targetW, targetH) = ImageUtils.calculateTargetDimensions(
                            resolvedOrigW, resolvedOrigH, config.resizeMode
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

                        // Generate clean filename (only read EXIF date if pattern requires it)
                        val dateStr = if (config.renamePattern == com.example.model.RenamePattern.DATE_TIME) {
                            ImageUtils.getExifDateFormatted(context, item.uri)
                        } else ""
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
                                originalWidth = resolvedOrigW,
                                originalHeight = resolvedOrigH,
                                status = BatchItemStatus.COMPLETED,
                                processedSizeBytes = bytes.size.toLong(),
                                resultUri = savedUri
                            )
                        } else {
                            item.copy(
                                originalWidth = resolvedOrigW,
                                originalHeight = resolvedOrigH,
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

                if (processedItem.status == BatchItemStatus.COMPLETED) {
                    successfulCount++
                    originalUrisToDelete.add(processedItem.uri)
                    processedOrigSum += processedItem.originalSizeBytes
                    if (processedItem.processedSizeBytes != null) {
                        processedSum += processedItem.processedSizeBytes
                    }
                    if (recentCompletedForHistory.size >= 500) {
                        recentCompletedForHistory.removeFirst()
                    }
                    recentCompletedForHistory.addLast(processedItem)
                }

                val targetIdx = indexById[item.id]
                if (targetIdx != null) {
                    workingList[targetIdx] = processedItem
                }

                // Throttle full list snapshot emissions for O(N) performance on 10k~20k batches
                val now = System.currentTimeMillis()
                if (now - lastUiSyncTimeMs >= 200L || index == 0 || index == itemsToProcess.lastIndex) {
                    lastUiSyncTimeMs = now
                    val snapshot = workingList.toList()
                    _batchState.update { s ->
                        s.copy(
                            items = snapshot,
                            currentProcessingIndex = index + 1,
                            processedOriginalBytes = processedOrigSum,
                            totalProcessedBytes = processedSum,
                            statusMessage = "(${index + 1}/${itemsToProcess.size}) '${item.fileName}' 압축 중..."
                        )
                    }
                }
            }

            // Record recent completed items to saved history (capped to keep UI responsive)
            val newHistoryItems = recentCompletedForHistory
                .asReversed()
                .filter { it.resultUri != null }
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
            _savedHistory.update { existing ->
                (newHistoryItems + existing).take(1000)
            }

            val savedMb = ((processedOrigSum - processedSum).coerceAtLeast(0L)) / (1024.0 * 1024.0)
            val finalSnapshot = workingList.toList()

            _batchState.update { s ->
                s.copy(
                    items = finalSnapshot,
                    isProcessing = false,
                    isCompleted = true,
                    currentProcessingIndex = itemsToProcess.size,
                    processingTargetCount = itemsToProcess.size,
                    processedOriginalBytes = processedOrigSum,
                    totalProcessedBytes = processedSum,
                    statusMessage = "총 ${String.format("%,d", successfulCount)}개 이미지 압축 완료! (약 ${String.format("%.1f", savedMb)} MB 절약)"
                )
            }

            // If user selected to delete originals
            if (config.deleteOriginal && originalUrisToDelete.isNotEmpty()) {
                enqueueBatchDelete(originalUrisToDelete)
            }
        }
    }

    // -------------------------------------------------------------
    // Deletion Request Management (Scoped Storage & Binder safe)
    // -------------------------------------------------------------

    fun getDeleteIntentSenderRequest(context: Context, uris: List<Uri>): IntentSenderRequest? {
        return ImageUtils.createDeleteIntentRequest(context, uris)
    }

    fun onOriginalsDeleted(deletedUris: List<Uri>) {
        if (_singleState.value.deleteOriginalPromptUris != null) {
            _singleState.update {
                it.copy(deleteOriginalPromptUris = null, statusMessage = "원본 이미지가 안전하게 삭제되었습니다.")
            }
        }
        val currentBatch = _batchState.value
        if (currentBatch.deleteOriginalPromptUris != null) {
            val remaining = currentBatch.pendingDeleteQueue
            if (remaining.isNotEmpty()) {
                val nextChunk = remaining.take(ImageUtils.MAX_DELETE_BATCH_SIZE)
                val nextQueue = if (remaining.size > ImageUtils.MAX_DELETE_BATCH_SIZE) {
                    remaining.drop(ImageUtils.MAX_DELETE_BATCH_SIZE)
                } else {
                    emptyList()
                }
                _batchState.update {
                    it.copy(
                        deleteOriginalPromptUris = nextChunk,
                        pendingDeleteQueue = nextQueue,
                        statusMessage = "${deletedUris.size}장 삭제 완료 — 남은 ${remaining.size}장 삭제를 이어서 진행합니다."
                    )
                }
            } else {
                val totalDeleted = if (currentBatch.totalDeleteBatchCount > 0) {
                    currentBatch.totalDeleteBatchCount
                } else {
                    deletedUris.size
                }
                _batchState.update {
                    it.copy(
                        deleteOriginalPromptUris = null,
                        pendingDeleteQueue = emptyList(),
                        totalDeleteBatchCount = 0,
                        statusMessage = "원본 ${String.format("%,d", totalDeleted)}개 이미지 삭제가 완료되었습니다."
                    )
                }
            }
        }
    }

    fun dismissDeletePrompt() {
        _singleState.update { it.copy(deleteOriginalPromptUris = null) }
        _batchState.update {
            it.copy(
                deleteOriginalPromptUris = null,
                pendingDeleteQueue = emptyList(),
                totalDeleteBatchCount = 0
            )
        }
    }

    fun clearSavedHistory() {
        _savedHistory.value = emptyList()
    }
}
