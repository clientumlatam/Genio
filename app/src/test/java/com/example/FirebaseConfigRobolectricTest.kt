package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.GuiaGenioDatabase
import com.example.data.local.PublishedBusinessEntity
import com.example.data.local.PublishedBusinessRepository
import com.example.data.places.LocationTracker
import com.example.data.places.PlacesRepository
import com.example.data.remote.FirebaseConfigurationService
import com.example.data.remote.FirestoreBusinessListing
import com.example.data.remote.FirestoreConfigStatus
import com.example.data.remote.FirestoreDirectoryRepository
import com.example.domain.model.DirectoryBusiness
import com.example.domain.model.PatagonianLocalities
import com.example.ui.DirectoryViewModel
import com.example.ui.MyBusinessesViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirebaseConfigRobolectricTest {

  private lateinit var context: Context
  private lateinit var database: GuiaGenioDatabase
  private lateinit var publishedRepo: PublishedBusinessRepository

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    clearFirebaseApps()
    database = Room.inMemoryDatabaseBuilder(context, GuiaGenioDatabase::class.java).build()
    publishedRepo = PublishedBusinessRepository(database.businessDao())
  }

  @After
  fun tearDown() {
    clearFirebaseApps()
    database.close()
  }

  private fun clearFirebaseApps() {
    FirebaseApp.getApps(context).toList().forEach { app ->
      runCatching { app.delete() }
    }
  }

  @Test
  fun `resolveNamedFirestore returns MissingGoogleServices when no FirebaseApp exists`() {
    val resolution = FirestoreDirectoryRepository.resolveNamedFirestoreWithStatus(context)
    assertNull(resolution.firestore)
    assertFalse(resolution.status.isConfigured)
    assertTrue(resolution.status is FirestoreConfigStatus.Disabled)
    val disabled = resolution.status as FirestoreConfigStatus.Disabled
    assertEquals(FirestoreConfigStatus.Disabled.Reason.MISSING_GOOGLE_SERVICES, disabled.reason)
  }

  @Test
  fun `resolveNamedFirestore rejects placeholder projectId`() {
    FirebaseApp.initializeApp(
      context,
      FirebaseOptions.Builder()
        .setProjectId("remixed-project-id")
        .setApplicationId("1:123456:android:abcdef")
        .setApiKey("AIzaSyPlaceholderTestKeyOnly")
        .build(),
    )

    val resolution =
      FirestoreDirectoryRepository.resolveNamedFirestoreWithStatus(
        context = context,
        databaseIdOverride = "guia-genio-db",
      )
    assertNull(resolution.firestore)
    val disabled = resolution.status as FirestoreConfigStatus.Disabled
    assertEquals(
      FirestoreConfigStatus.Disabled.Reason.INVALID_PLACEHOLDER_PROJECT,
      disabled.reason,
    )
  }

  @Test
  fun `resolveNamedFirestore returns MissingDatabaseId when resource and override are absent`() {
    FirebaseApp.initializeApp(
      context,
      FirebaseOptions.Builder()
        .setProjectId("guia-genio-prod")
        .setApplicationId("1:123456:android:abcdef")
        .setApiKey("AIzaSyTestOnlyNotARealKey123")
        .build(),
    )

    val resolution = FirestoreDirectoryRepository.resolveNamedFirestoreWithStatus(context)
    assertNull(resolution.firestore)
    val disabled = resolution.status as FirestoreConfigStatus.Disabled
    assertEquals(FirestoreConfigStatus.Disabled.Reason.MISSING_DATABASE_ID, disabled.reason)
  }

  @Test
  fun `resolveNamedFirestore rejects default and placeholder database ids`() {
    FirebaseApp.initializeApp(
      context,
      FirebaseOptions.Builder()
        .setProjectId("guia-genio-prod")
        .setApplicationId("1:123456:android:abcdef")
        .setApiKey("AIzaSyTestOnlyNotARealKey123")
        .build(),
    )

    val defaultRes =
      FirestoreDirectoryRepository.resolveNamedFirestoreWithStatus(context, "(default)")
    assertNull(defaultRes.firestore)
    assertEquals(
      FirestoreConfigStatus.Disabled.Reason.INVALID_DATABASE_ID,
      (defaultRes.status as FirestoreConfigStatus.Disabled).reason,
    )

    val remixedRes =
      FirestoreDirectoryRepository.resolveNamedFirestoreWithStatus(context, "remixed-database-id")
    assertNull(remixedRes.firestore)
    assertEquals(
      FirestoreConfigStatus.Disabled.Reason.INVALID_DATABASE_ID,
      (remixedRes.status as FirestoreConfigStatus.Disabled).reason,
    )
  }

  @Test
  fun `resolveNamedFirestore succeeds with valid FirebaseApp and named database id`() {
    FirebaseApp.initializeApp(
      context,
      FirebaseOptions.Builder()
        .setProjectId("guia-genio-prod")
        .setApplicationId("1:123456:android:abcdef")
        .setApiKey("AIzaSyTestOnlyNotARealKey123")
        .build(),
    )

    val resolution =
      FirestoreDirectoryRepository.resolveNamedFirestoreWithStatus(
        context = context,
        databaseIdOverride = "guia-genio-db",
      )
    assertNotNull(resolution.firestore)
    assertTrue(resolution.status.isConfigured)
    val ready = resolution.status as FirestoreConfigStatus.Ready
    assertEquals("guia-genio-prod", ready.projectId)
    assertEquals("guia-genio-db", ready.databaseId)
  }

  @Test
  fun `disabled repository fails cloud mutations honestly without false success`() = runBlocking {
    val firestoreRepo = FirestoreDirectoryRepository(context)
    assertFalse(firestoreRepo.isConfigured)

    val saveResult =
      firestoreRepo.saveBusiness(
        FirestoreBusinessListing(
          name = "Panadería Limay",
          address = "Av. Olascoaga 210",
          categoryId = "gastronomia",
          categoryName = "Gastronomía",
          localityId = PatagonianLocalities.NeuquenCapital.id,
          localityName = PatagonianLocalities.NeuquenCapital.name,
        )
      )
    assertTrue(saveResult.isFailure)

    val reviewResult =
      firestoreRepo.submitReview(
        businessId = "biz_1",
        rating = 5,
        comment = "Excelente",
        authorName = "Ana",
      )
    assertTrue(reviewResult.isFailure)

    val favResult =
      firestoreRepo.toggleFavorite(
        userId = "uid_1",
        business =
          DirectoryBusiness(
            id = "biz_1",
            name = "Panadería Limay",
            categoryId = "gastronomia",
            categoryName = "Gastronomía",
            localityName = "Neuquén Capital",
            address = "Av. Olascoaga 210",
            latitude = -38.95,
            longitude = -68.05,
          ),
        currentlyFavorite = false,
      )
    assertTrue(favResult.isFailure)

    val myVm =
      MyBusinessesViewModel(
        repository = publishedRepo,
        placesRepository = PlacesRepository(context, database.businessDao()),
        authService = null,
        firestoreRepository = firestoreRepo,
      )
    assertFalse(myVm.isFirestoreConfigured)

    myVm.toggleOpenNow(
      PublishedBusinessEntity(
        id = 1L,
        name = "Local Prueba",
        categoryId = "gastronomia",
        localityId = "neuquen_capital",
        address = "Calle 1",
        latitude = -38.95,
        longitude = -68.05,
        phone = "2994000000",
        whatsapp = "",
        website = "",
        description = "",
        isOpenNow = true,
        hasDelivery = false,
      )
    )
    // Wait briefly for ViewModel coroutine on Main/Default dispatcher if needed
    val dirVm =
      DirectoryViewModel(
        placesRepository = PlacesRepository(context, database.businessDao()),
        publishedRepository = publishedRepo,
        locationTracker = LocationTracker(context),
        firestoreRepository = firestoreRepo,
        authService = null,
      )
    assertFalse(dirVm.isFirestoreConfigured)
  }

  @Test
  fun `configuration service blocks reading secret resource names and exposes zero api keys`() {
    assertNull(
      FirebaseConfigurationService.readNonSecretStringResource(context, "google_api_key")
    )
    assertNull(
      FirebaseConfigurationService.readNonSecretStringResource(context, "firebase_private_secret")
    )
    assertNull(
      FirebaseConfigurationService.readNonSecretStringResource(context, "app_auth_token")
    )

    FirebaseApp.initializeApp(
      context,
      FirebaseOptions.Builder()
        .setProjectId("guia-genio-prod")
        .setApplicationId("1:123456:android:abcdef")
        .setApiKey("AIzaSySensitiveKeyShouldNeverLeak")
        .build(),
    )

    val configService =
      FirebaseConfigurationService(
        context = context,
        databaseIdOverride = "guia-genio-db",
      )
    val snapshot = configService.configurationState.value
    assertTrue(snapshot.isFirebaseProjectConfigured)
    assertEquals("guia-genio-prod", snapshot.projectId)
    assertEquals("guia-genio-db", snapshot.databaseId)
    assertFalse(snapshot.toString().contains("AIzaSySensitiveKeyShouldNeverLeak"))
    configService.release()
  }

  @Test
  fun `configuration service initializes authenticated firestore when user is present and resets on sign out`() {
    FirebaseApp.initializeApp(
      context,
      FirebaseOptions.Builder()
        .setProjectId("guia-genio-prod")
        .setApplicationId("1:123456:android:abcdef")
        .setApiKey("AIzaSyTestOnlyNotARealKey123")
        .build(),
    )

    var simulatedUid: String? = null
    val configService =
      FirebaseConfigurationService(
        context = context,
        databaseIdOverride = "guia-genio-db",
        externalUidProvider = { simulatedUid },
      )

    // Sin usuario autenticado aún
    val unauthState = configService.configurationState.value
    assertTrue(unauthState.isFirestoreInitialized)
    assertFalse(unauthState.isAuthenticatedUserPresent)
    assertFalse(unauthState.isAuthenticatedFirestoreReady)
    assertNull(unauthState.authenticatedUserId)

    // Al estar presente el usuario autenticado, inicializa sesión Firestore nombrada
    simulatedUid = "owner_uid_neuquen"
    val authState = configService.onAuthenticatedUserChanged("owner_uid_neuquen")
    assertTrue(authState.isFirestoreInitialized)
    assertTrue(authState.isAuthenticatedUserPresent)
    assertTrue(authState.isAuthenticatedFirestoreReady)
    assertEquals("owner_uid_neuquen", authState.authenticatedUserId)

    val authResolution = configService.getAuthenticatedFirestore("owner_uid_neuquen")
    assertNotNull(authResolution.firestore)
    assertTrue(authResolution.status.isConfigured)
    assertEquals(
      "guia-genio-db",
      (authResolution.status as FirestoreConfigStatus.Ready).databaseId,
    )

    // Al cerrar sesión, limpia el estado autenticado sin perder validación de proyecto
    simulatedUid = null
    val signedOutState = configService.onAuthenticatedUserChanged(null)
    assertFalse(signedOutState.isAuthenticatedUserPresent)
    assertFalse(signedOutState.isAuthenticatedFirestoreReady)
    assertNull(signedOutState.authenticatedUserId)
    configService.release()
  }
}
