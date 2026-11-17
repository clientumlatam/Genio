package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.FirestoreDirectoryRepository
import com.example.domain.model.BusinessSource
import com.example.domain.model.BusinessStatus
import com.example.domain.model.DirectoryBusiness
import com.example.ui.components.ApprovedBusinessesSearchBar
import com.example.ui.theme.GuiaGenioTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ApprovedBusinessesSearchBarRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val sampleCatalog =
    listOf(
      DirectoryBusiness(
        id = "biz_approved_gastro",
        name = "Parrilla del Limay",
        categoryId = "gastronomia",
        categoryName = "Gastronomía",
        localityName = "Neuquén Capital",
        address = "Av. Olascoaga 320",
        latitude = -38.955,
        longitude = -68.059,
        isOpenNow = true,
        source = BusinessSource.USER_PUBLISHED,
        status = BusinessStatus.APPROVED,
      ),
      DirectoryBusiness(
        id = "biz_pending_gastro",
        name = "Café Pendiente Río",
        categoryId = "gastronomia",
        categoryName = "Gastronomía",
        localityName = "Neuquén Capital",
        address = "Mitre 120",
        latitude = -38.952,
        longitude = -68.058,
        source = BusinessSource.USER_PUBLISHED,
        status = BusinessStatus.PENDING,
      ),
      DirectoryBusiness(
        id = "biz_rejected_gastro",
        name = "Bar Rechazado",
        categoryId = "gastronomia",
        categoryName = "Gastronomía",
        localityName = "Cipolletti",
        address = "España 400",
        latitude = -38.933,
        longitude = -67.99,
        source = BusinessSource.USER_PUBLISHED,
        status = BusinessStatus.REJECTED,
      ),
      DirectoryBusiness(
        id = "biz_approved_industria",
        name = "Servicios Petroleros Patagónicos",
        categoryId = "vaca_muerta_industria",
        categoryName = "Industria y Vaca Muerta",
        localityName = "Añelo",
        address = "Ruta 7 Km 94",
        latitude = -38.352,
        longitude = -68.785,
        source = BusinessSource.USER_PUBLISHED,
        status = BusinessStatus.APPROVED,
      ),
      DirectoryBusiness(
        id = "biz_suspended_industria",
        name = "Taller Suspendido",
        categoryId = "vaca_muerta_industria",
        categoryName = "Industria y Vaca Muerta",
        localityName = "Añelo",
        address = "Parque Industrial 12",
        latitude = -38.35,
        longitude = -68.79,
        source = BusinessSource.USER_PUBLISHED,
        status = BusinessStatus.SUSPENDED,
      ),
    )

  @Test
  fun `filterApprovedBusinessesByNameOrCategory filters only APPROVED by name and accent-insensitive category`() {
    val allApproved =
      FirestoreDirectoryRepository.filterApprovedBusinessesByNameOrCategory(sampleCatalog, "")
    assertEquals(listOf("biz_approved_gastro", "biz_approved_industria"), allApproved.map { it.id })

    val byCategoryNoAccent =
      FirestoreDirectoryRepository.filterApprovedBusinessesByNameOrCategory(
        sampleCatalog,
        "gastronomia",
      )
    assertEquals(listOf("biz_approved_gastro"), byCategoryNoAccent.map { it.id })

    val byNameAccentInsensitive =
      FirestoreDirectoryRepository.filterApprovedBusinessesByNameOrCategory(
        sampleCatalog,
        "patagonicos",
      )
    assertEquals(listOf("biz_approved_industria"), byNameAccentInsensitive.map { it.id })

    val byCategoryVacaMuerta =
      FirestoreDirectoryRepository.filterApprovedBusinessesByNameOrCategory(
        sampleCatalog,
        "vaca muerta",
      )
    assertEquals(listOf("biz_approved_industria"), byCategoryVacaMuerta.map { it.id })
  }

  @Test
  fun `disabled Firestore repository observeApprovedBusinesses query flow emits empty list safely`() =
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val repo = FirestoreDirectoryRepository(context)
      assertFalse(repo.isConfigured)

      val queryFlow = MutableStateFlow("gastronomia")
      val emissions = repo.observeApprovedBusinesses(queryFlow).first()
      assertTrue(emissions.isEmpty())
    }

  @Test
  fun `ApprovedBusinessesSearchBar filters real-time results by name or category and triggers selection`() {
    var clickedBusiness: DirectoryBusiness? = null

    composeTestRule.setContent {
      GuiaGenioTheme {
        var query by remember { mutableStateOf("") }
        ApprovedBusinessesSearchBar(
          query = query,
          onQueryChange = { query = it },
          approvedBusinesses = sampleCatalog,
          onBusinessClick = { clickedBusiness = it },
        )
      }
    }

    composeTestRule.onNodeWithTag("approved_businesses_search_bar").assertIsDisplayed()

    // Filter by category (accent-insensitive) -> only APPROVED gastronomy shows
    composeTestRule.onNodeWithTag("approved_search_input").performTextInput("gastronomia")
    composeTestRule.onNodeWithTag("approved_search_result_biz_approved_gastro").assertIsDisplayed()
    composeTestRule.onNodeWithTag("approved_search_result_biz_pending_gastro").assertDoesNotExist()
    composeTestRule.onNodeWithTag("approved_search_result_biz_rejected_gastro").assertDoesNotExist()
    composeTestRule.onNodeWithTag("approved_search_result_biz_approved_industria").assertDoesNotExist()

    // Clear and filter by name -> only APPROVED industrial business shows
    composeTestRule.onNodeWithTag("approved_search_clear").performClick()
    composeTestRule.onNodeWithTag("approved_search_input").performTextInput("patagonicos")
    composeTestRule.onNodeWithTag("approved_search_result_biz_approved_industria").assertIsDisplayed()
    composeTestRule.onNodeWithTag("approved_search_result_biz_suspended_industria").assertDoesNotExist()

    // Select matching business
    composeTestRule.onNodeWithTag("approved_search_result_biz_approved_industria").performClick()
    assertEquals("biz_approved_industria", clickedBusiness?.id)
  }

  @Test
  fun `ApprovedBusinessesSearchBar displays empty state when no approved business matches query`() {
    composeTestRule.setContent {
      GuiaGenioTheme {
        var query by remember { mutableStateOf("") }
        ApprovedBusinessesSearchBar(
          query = query,
          onQueryChange = { query = it },
          approvedBusinesses = sampleCatalog,
          onBusinessClick = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("approved_search_input").performTextInput("rubro_inexistente")
    composeTestRule.onNodeWithTag("approved_search_empty").assertIsDisplayed()
    composeTestRule.onNodeWithTag("approved_search_reset_button").assertIsDisplayed()
  }
}
