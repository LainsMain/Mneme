package com.lainsmain.mneme.ui.diary

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lainsmain.mneme.data.DiaryPage
import com.lainsmain.mneme.data.DiaryRepository
import com.lainsmain.mneme.data.DaySummary
import com.lainsmain.mneme.data.DiaryAttachment
import com.lainsmain.mneme.data.DatedAttachment
import com.lainsmain.mneme.data.MonthlyRecap
import com.lainsmain.mneme.data.PlaceSearchRepository
import com.lainsmain.mneme.data.PlaceSuggestion
import com.lainsmain.mneme.model.DiaryDate
import com.lainsmain.mneme.model.RichTextDocument
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

data class DiaryUiState(
    val selectedDate: LocalDate,
    val document: RichTextDocument = RichTextDocument(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val savedRevision: Long = 0,
    val yesterdaySuggestion: LocalDate? = null,
    val visibleMonth: YearMonth,
    val monthDays: Map<LocalDate, DaySummary> = emptyMap(),
    val attachments: List<DiaryAttachment> = emptyList(),
    val isImportingPhotos: Boolean = false,
    val photoImportFailures: Int = 0,
    val allDays: List<DaySummary> = emptyList(),
    val allMedia: List<DatedAttachment> = emptyList(),
    val location: DiaryLocation? = null,
    val calendarJumpKey: Int = 0,
    val placeSuggestions: List<PlaceSuggestion> = emptyList(),
    val isSearchingPlaces: Boolean = false,
    val placeSearchMessage: String? = null,
    val recapMonth: YearMonth? = null,
    val recapDocument: RichTextDocument = RichTextDocument(),
    val recapIsLoading: Boolean = false,
    val recapIsSaving: Boolean = false,
    val recapSavedRevision: Long = 0,
    val recapMonths: Set<YearMonth> = emptySet(),
)

data class DiaryLocation(
    val name: String,
    val latitude: Double?,
    val longitude: Double?,
    val isManual: Boolean,
)

class DiaryViewModel(
    private val repository: DiaryRepository,
    private val placeSearchRepository: PlaceSearchRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val today = LocalDate.now(clock)
    private val _uiState = MutableStateFlow(
        DiaryUiState(
            selectedDate = today,
            visibleMonth = YearMonth.from(today),
            yesterdaySuggestion = DiaryDate.yesterdaySuggestion(clock),
        ),
    )
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()

    private var currentPage: DiaryPage? = null
    private var observeJob: Job? = null
    private var saveJob: Job? = null
    private var observeMonthJob: Job? = null
    private var observeAttachmentsJob: Job? = null
    private var observeAllDaysJob: Job? = null
    private var observeMediaJob: Job? = null
    private var observeRecapJob: Job? = null
    private var observeRecapMonthsJob: Job? = null
    private var recapSaveJob: Job? = null
    private var placeSearchJob: Job? = null
    private var automaticLocationJob: Job? = null
    private var yesterdayPromptJob: Job? = null
    private var automaticLocationKey: String? = null
    private var attachmentsLoaded = false
    private var dirty = false
    private var recapDirty = false
    private var currentRecap: MonthlyRecap? = null

    init {
        observeDate(today)
        observeMonth(YearMonth.from(today))
        observeAllContent()
        updateYesterdayPromptCutoffHour(6)
    }

    fun updateYesterdayPromptCutoffHour(hour: Int) {
        require(hour in 1..12)
        yesterdayPromptJob?.cancel()
        yesterdayPromptJob = viewModelScope.launch {
            while (true) {
                _uiState.value = _uiState.value.copy(
                    yesterdaySuggestion = DiaryDate.yesterdaySuggestion(clock, hour),
                )
                val now = ZonedDateTime.now(clock)
                val nextTransition = if (now.hour < hour) {
                    now.toLocalDate().atTime(hour, 0).atZone(clock.zone)
                } else {
                    now.toLocalDate().plusDays(1).atStartOfDay(clock.zone)
                }
                delay(Duration.between(now, nextTransition).toMillis().coerceAtLeast(1_000L) + 250L)
            }
        }
    }

    fun previousDay() = selectDate(_uiState.value.selectedDate.minusDays(1))

    fun nextDay() = selectDate(_uiState.value.selectedDate.plusDays(1))

    fun today() = selectDate(today)

    fun previousMonth() = showMonth(_uiState.value.visibleMonth.minusMonths(1))

    fun nextMonth() = showMonth(_uiState.value.visibleMonth.plusMonths(1))

    fun currentMonth() {
        showMonth(YearMonth.from(today))
        _uiState.value = _uiState.value.copy(calendarJumpKey = _uiState.value.calendarJumpKey + 1)
    }

    fun openRecap(month: YearMonth) {
        if (_uiState.value.recapMonth == month) return
        recapSaveJob?.cancel()
        viewModelScope.launch {
            saveRecapImmediately()
            observeRecap(month)
        }
    }

    fun closeRecap() {
        recapSaveJob?.cancel()
        viewModelScope.launch {
            saveRecapImmediately()
            observeRecapJob?.cancel()
            currentRecap = null
            recapDirty = false
            _uiState.value = _uiState.value.copy(recapMonth = null)
        }
    }

    fun previousRecapMonth() = _uiState.value.recapMonth?.let { openRecap(it.minusMonths(1)) }

    fun nextRecapMonth() = _uiState.value.recapMonth?.let { openRecap(it.plusMonths(1)) }

    fun updateRecapDocument(document: RichTextDocument) {
        val month = _uiState.value.recapMonth ?: return
        recapDirty = true
        _uiState.value = _uiState.value.copy(recapDocument = document, recapIsSaving = true)
        recapSaveJob?.cancel()
        recapSaveJob = viewModelScope.launch {
            delay(450)
            saveRecap(month, document)
        }
    }

    fun showMonth(month: YearMonth) {
        if (month == _uiState.value.visibleMonth) return
        observeMonth(month)
    }

    fun selectDate(date: LocalDate) {
        if (date == _uiState.value.selectedDate) return
        saveJob?.cancel()
        viewModelScope.launch {
            saveImmediately()
            observeDate(date)
        }
    }

    fun updateDocument(document: RichTextDocument) {
        _uiState.value = _uiState.value.copy(document = document, isSaving = true)
        dirty = true
        saveJob?.cancel()
        val dateAtEdit = _uiState.value.selectedDate
        saveJob = viewModelScope.launch {
            delay(450)
            save(dateAtEdit, document)
        }
    }

    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty() || _uiState.value.isImportingPhotos) return
        saveJob?.cancel()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImportingPhotos = true, photoImportFailures = 0)
            try {
                saveImmediately()
                val page = currentPage ?: repository.save(
                    date = _uiState.value.selectedDate,
                    existing = null,
                    document = _uiState.value.document,
                ).also { currentPage = it }
                val result = repository.importPhotos(page.id, uris)
                _uiState.value = _uiState.value.copy(photoImportFailures = result.failed)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(photoImportFailures = uris.size)
            } finally {
                _uiState.value = _uiState.value.copy(isImportingPhotos = false)
            }
        }
    }

    fun makePhotoPrimary(attachmentId: String) {
        viewModelScope.launch { repository.makePhotoPrimary(attachmentId) }
    }

    fun deletePhoto(attachmentId: String) {
        viewModelScope.launch { repository.deletePhoto(attachmentId) }
    }

    fun setLocation(name: String, latitude: Double?, longitude: Double?) {
        viewModelScope.launch {
            val resolvedName = name.trim().ifBlank {
                if (latitude != null && longitude != null) {
                    placeSearchRepository.reverse(latitude, longitude, Locale.getDefault().language)
                        .getOrNull()
                        ?.name
                        .orEmpty()
                        .ifBlank { formatCoordinates(latitude, longitude) }
                } else {
                    "Custom location"
                }
            }
            val page = pageForLocation()
            repository.setManualLocation(page.id, resolvedName, latitude, longitude)
        }
    }

    fun setLocationFromMap(latitude: Double, longitude: Double) {
        setLocation("", latitude, longitude)
    }

    fun usePrimaryPhotoLocation() {
        currentPage?.let { page ->
            viewModelScope.launch { repository.usePrimaryPhotoLocation(page.id) }
        }
    }

    fun searchPlaces(query: String) {
        placeSearchJob?.cancel()
        if (query.trim().length < 2) {
            clearPlaceSearch()
            return
        }
        placeSearchJob = viewModelScope.launch {
            delay(450)
            _uiState.value = _uiState.value.copy(isSearchingPlaces = true, placeSearchMessage = null)
            placeSearchRepository.search(query, java.util.Locale.getDefault().language).fold(
                onSuccess = { suggestions ->
                    _uiState.value = _uiState.value.copy(
                        placeSuggestions = suggestions,
                        isSearchingPlaces = false,
                        placeSearchMessage = if (suggestions.isEmpty()) "No matching places." else null,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        placeSuggestions = emptyList(),
                        isSearchingPlaces = false,
                        placeSearchMessage = error.message ?: "Could not search places.",
                    )
                },
            )
        }
    }

    fun clearPlaceSearch() {
        placeSearchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            placeSuggestions = emptyList(),
            isSearchingPlaces = false,
            placeSearchMessage = null,
        )
    }

    private fun observeDate(date: LocalDate) {
        observeJob?.cancel()
        observeAttachmentsJob?.cancel()
        dirty = false
        currentPage = null
        automaticLocationJob?.cancel()
        automaticLocationKey = null
        attachmentsLoaded = false
        _uiState.value = _uiState.value.copy(
            selectedDate = date,
            document = RichTextDocument(),
            isLoading = true,
            isSaving = false,
            savedRevision = 0,
            attachments = emptyList(),
            photoImportFailures = 0,
        )
        observeJob = viewModelScope.launch {
            repository.observePage(date).collect { page ->
                currentPage = page
                if (!dirty) {
                    _uiState.value = _uiState.value.copy(
                        document = page?.document ?: RichTextDocument(),
                        isLoading = false,
                        isSaving = false,
                        savedRevision = page?.revision ?: 0,
                        location = effectiveLocation(page, _uiState.value.attachments),
                    )
                }
                if (attachmentsLoaded) refreshAutomaticPhotoLocation(page, _uiState.value.attachments)
            }
        }
        observeAttachmentsJob = viewModelScope.launch {
            repository.observeAttachments(date).collect { attachments ->
                attachmentsLoaded = true
                _uiState.value = _uiState.value.copy(
                    attachments = attachments,
                    location = effectiveLocation(currentPage, attachments),
                )
                refreshAutomaticPhotoLocation(currentPage, attachments)
            }
        }
    }

    private fun observeAllContent() {
        observeAllDaysJob = viewModelScope.launch {
            repository.observeAllDays().collect { days ->
                _uiState.value = _uiState.value.copy(allDays = days)
            }
        }
        observeMediaJob = viewModelScope.launch {
            repository.observeAllMedia().collect { media ->
                _uiState.value = _uiState.value.copy(allMedia = media)
            }
        }
        observeRecapMonthsJob = viewModelScope.launch {
            repository.observeRecapMonths().collect { months ->
                _uiState.value = _uiState.value.copy(recapMonths = months)
            }
        }
    }

    private fun observeRecap(month: YearMonth) {
        observeRecapJob?.cancel()
        currentRecap = null
        recapDirty = false
        _uiState.value = _uiState.value.copy(
            recapMonth = month,
            recapDocument = RichTextDocument(),
            recapIsLoading = true,
            recapIsSaving = false,
            recapSavedRevision = 0,
        )
        observeRecapJob = viewModelScope.launch {
            repository.observeRecap(month).collect { recap ->
                currentRecap = recap
                if (!recapDirty) {
                    _uiState.value = _uiState.value.copy(
                        recapDocument = recap?.document ?: RichTextDocument(),
                        recapIsLoading = false,
                        recapIsSaving = false,
                        recapSavedRevision = recap?.revision ?: 0,
                    )
                }
            }
        }
    }

    private fun effectiveLocation(
        page: DiaryPage?,
        attachments: List<DiaryAttachment>,
    ): DiaryLocation? {
        if (page?.locationIsManual == true) {
            return DiaryLocation(
                name = page.locationName ?: "Custom location",
                latitude = page.latitude,
                longitude = page.longitude,
                isManual = true,
            )
        }
        val primary = attachments.firstOrNull()
            ?.takeIf { it.latitude != null && it.longitude != null }
            ?: return null
        return DiaryLocation(
            name = page?.locationName ?: "Primary photo location",
            latitude = primary.latitude,
            longitude = primary.longitude,
            isManual = false,
        )
    }

    private fun refreshAutomaticPhotoLocation(
        page: DiaryPage?,
        attachments: List<DiaryAttachment>,
    ) {
        if (page == null || page.locationIsManual) {
            automaticLocationJob?.cancel()
            automaticLocationKey = null
            return
        }
        val primary = attachments.firstOrNull()?.takeIf {
            it.latitude != null && it.longitude != null
        }
        if (primary == null) {
            val emptyKey = "${page.id}:none"
            if (automaticLocationKey == emptyKey) return
            automaticLocationKey = emptyKey
            automaticLocationJob?.cancel()
            if (page.locationName != null) {
                automaticLocationJob = viewModelScope.launch {
                    repository.setAutomaticLocationName(page.id, null)
                }
            }
            return
        }
        val latitude = primary.latitude!!
        val longitude = primary.longitude!!
        val key = "${page.id}:${primary.id}:$latitude:$longitude"
        if (automaticLocationKey == key) return
        automaticLocationKey = key
        automaticLocationJob?.cancel()
        automaticLocationJob = viewModelScope.launch {
            val resolvedName = placeSearchRepository.reverse(
                latitude,
                longitude,
                Locale.getDefault().language,
            ).getOrNull()?.name
            if (automaticLocationKey == key && currentPage?.locationIsManual != true) {
                repository.setAutomaticLocationName(page.id, resolvedName)
            }
        }
    }

    private fun observeMonth(month: YearMonth) {
        observeMonthJob?.cancel()
        _uiState.value = _uiState.value.copy(visibleMonth = month, monthDays = emptyMap())
        observeMonthJob = viewModelScope.launch {
            repository.observeMonth(month).collect { days ->
                _uiState.value = _uiState.value.copy(monthDays = days)
            }
        }
    }

    private suspend fun saveImmediately() {
        if (!dirty) return
        save(_uiState.value.selectedDate, _uiState.value.document)
    }

    private suspend fun saveRecapImmediately() {
        if (!recapDirty) return
        val month = _uiState.value.recapMonth ?: return
        saveRecap(month, _uiState.value.recapDocument)
    }

    private suspend fun saveRecap(month: YearMonth, document: RichTextDocument) {
        if (month != _uiState.value.recapMonth) return
        val saved = repository.saveRecap(month, currentRecap, document)
        currentRecap = saved
        if (_uiState.value.recapDocument == document) {
            recapDirty = false
            _uiState.value = _uiState.value.copy(
                recapIsSaving = false,
                recapSavedRevision = saved.revision,
            )
        }
    }

    private suspend fun pageForLocation(): DiaryPage {
        saveImmediately()
        return currentPage ?: repository.save(
            date = _uiState.value.selectedDate,
            existing = null,
            document = _uiState.value.document,
        ).also { currentPage = it }
    }

    private fun formatCoordinates(latitude: Double, longitude: Double): String =
        "${"%.5f".format(Locale.ROOT, latitude)}, ${"%.5f".format(Locale.ROOT, longitude)}"

    private suspend fun save(date: LocalDate, document: RichTextDocument) {
        if (date != _uiState.value.selectedDate) return
        val saved = repository.save(date, currentPage, document)
        currentPage = saved
        if (_uiState.value.document == document) {
            dirty = false
            _uiState.value = _uiState.value.copy(isSaving = false, savedRevision = saved.revision)
        }
    }

    class Factory(
        private val repository: DiaryRepository,
        private val placeSearchRepository: PlaceSearchRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DiaryViewModel(repository, placeSearchRepository) as T
    }
}
