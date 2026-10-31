package com.tengwear.tengstore.ui.releases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.domain.model.Release
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReleasesUiState(
    val loading: Boolean = false,
    val owner: String = "",
    val repo: String = "",
    val releases: List<Release> = emptyList(),
    val error: String? = null,
)

class ReleasesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ReleasesUiState())
    val uiState: StateFlow<ReleasesUiState> = _uiState.asStateFlow()

    fun load(fullName: String) {
        val parts = fullName.split("/")
        if (parts.size != 2) {
            _uiState.value = _uiState.value.copy(error = "Invalid repository: $fullName")
            return
        }
        val (owner, repo) = parts
        _uiState.value = _uiState.value.copy(
            owner = owner,
            repo = repo,
            loading = true,
            error = null,
            releases = emptyList(),
        )
        viewModelScope.launch {
            runCatching { AppContainer.gitHubRepository.releases(owner, repo) }
                .onSuccess { releases ->
                    _uiState.value = _uiState.value.copy(loading = false, releases = releases)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = e.message ?: "Request failed",
                    )
                }
        }
    }
}
