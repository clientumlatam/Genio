package com.example.domain.model

enum class DirectoryViewMode {
  LIST,
  MAP,
}

data class DirectoryFilters(
  val openNowOnly: Boolean = false,
  val deliveryOnly: Boolean = false,
  val whatsappOnly: Boolean = false,
  val userPublishedOnly: Boolean = false,
) {
  val activeCount: Int
    get() =
      listOf(openNowOnly, deliveryOnly, whatsappOnly, userPublishedOnly).count { it }
}

sealed interface DirectoryResultsState {
  data object Loading : DirectoryResultsState
  data class Success(
    val businesses: List<DirectoryBusiness>,
    val placesWarning: String? = null,
  ) : DirectoryResultsState

  data class Error(val message: String) : DirectoryResultsState
}
