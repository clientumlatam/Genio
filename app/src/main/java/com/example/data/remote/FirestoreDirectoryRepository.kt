package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.domain.model.BusinessReview
import com.example.domain.model.BusinessStatus
import com.example.domain.model.DirectoryBusiness
import com.example.domain.model.DirectoryCategories
import com.example.domain.model.PatagonianLocalities
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.Normalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Repositorio central de Cloud Firestore para Guía Genio:
 * - Comercios publicados (`businesses/{businessId}`)
 * - Reseñas y calificaciones (`businesses/{businessId}/reviews/{reviewId}`)
 * - Favoritos por usuario (`users/{userId}/favorites/{businessId}`)
 * - Perfiles de usuarios/dueños (`users/{userId}`)
 */
class FirestoreDirectoryRepository internal constructor(
  private val appContext: Context,
  private val staticFirestore: FirebaseFirestore?,
  private val staticConfigStatus: FirestoreConfigStatus,
  private val authProvider: () -> FirebaseAuth? = {
    if (FirebaseApp.getApps(appContext).isNotEmpty()) Firebase.auth else null
  },
  val configService: FirebaseConfigurationService? = null,
) {

  constructor(
    firebaseConfigService: FirebaseConfigurationService,
  ) : this(
    appContext = firebaseConfigService.appContext,
    staticFirestore = null,
    staticConfigStatus = firebaseConfigService.configurationState.value.configStatus,
    configService = firebaseConfigService,
  )

  constructor(
    context: Context,
    databaseIdOverride: String? = null,
  ) : this(
    firebaseConfigService = FirebaseConfigurationService(context, databaseIdOverride),
  )

  val configStatus: FirestoreConfigStatus
    get() = configService?.configurationState?.value?.configStatus ?: staticConfigStatus

  val configurationState: StateFlow<FirebaseConfigurationSnapshot>
    get() =
      configService?.configurationState
        ?: MutableStateFlow(
          FirebaseConfigurationSnapshot(
            configStatus = staticConfigStatus,
            projectId = (staticConfigStatus as? FirestoreConfigStatus.Ready)?.projectId,
            databaseId = (staticConfigStatus as? FirestoreConfigStatus.Ready)?.databaseId,
            hasGoogleServicesProject = staticConfigStatus.isConfigured,
            hasWebClientId = (staticConfigStatus as? FirestoreConfigStatus.Ready)?.hasWebClientId == true,
            authenticatedUserId = runCatching { authProvider()?.currentUser?.uid }.getOrNull(),
            isFirestoreInitialized = staticFirestore != null,
          )
        ).asStateFlow()

  val isConfigured: Boolean
    get() = activePublicFirestore() != null && configStatus.isConfigured

  fun disabledReasonMessage(): String {
    return when (val status = configStatus) {
      is FirestoreConfigStatus.Disabled -> status.userMessage
      is FirestoreConfigStatus.Ready -> "Firestore operativo (${status.databaseId})."
    }
  }

  private fun activePublicFirestore(): FirebaseFirestore? {
    return if (configService != null) {
      configService.getPublicFirestore().firestore
    } else {
      staticFirestore
    }
  }

  private fun requireAuthenticatedFirestore(expectedUid: String? = null): Pair<FirebaseFirestore, String> {
    if (configService != null) {
      val resolution = configService.getAuthenticatedFirestore(expectedUid)
      val db = resolution.firestore ?: throw IllegalStateException(disabledReasonMessage())
      val uid =
        configService.currentAuthenticatedUid()
          ?: authProvider()?.currentUser?.uid
          ?: throw IllegalStateException("Debés iniciar sesión con Google para realizar esta acción.")
      return db to uid
    }

    val db = staticFirestore ?: throw IllegalStateException(disabledReasonMessage())
    if (!staticConfigStatus.isConfigured) {
      throw IllegalStateException(disabledReasonMessage())
    }
    if (FirebaseApp.getApps(appContext).isEmpty()) {
      throw IllegalStateException("Firebase no está inicializado en esta compilación.")
    }
    val uid =
      authProvider()?.currentUser?.uid
        ?: throw IllegalStateException("Debés iniciar sesión con Google para realizar esta acción.")
    return db to uid
  }

  /**
   * Observa en tiempo real los comercios públicos aprobados (`status == 'APPROVED'`) en Firestore.
   * Si Firebase aún no está provisionado, emite lista vacía sin simular error de red.
   */
  fun observeApprovedBusinesses(): Flow<List<DirectoryBusiness>> {
    val db = activePublicFirestore() ?: return flowOf(emptyList())
    val path = FirestoreCollections.BUSINESSES
    return callbackFlow {
      val registration =
        publicBusinessesQuery(db)
          .addSnapshotListener { snapshot, error ->
            if (error != null) {
              handleFirestoreError(error, OperationType.LIST, path)
              trySend(emptyList())
              return@addSnapshotListener
            }
            val list =
              snapshot?.documents
                ?.mapNotNull { doc ->
                  FirestoreBusinessListing.fromSnapshot(doc)
                    ?.toDirectoryBusiness()
                    ?.takeIf { it.status == BusinessStatus.APPROVED }
                }
                .orEmpty()
            trySend(list)
          }
      awaitClose { registration.remove() }
    }.catch { error ->
      if (error is CancellationException) throw error
      handleFirestoreError(error, OperationType.LIST, path)
      emit(emptyList())
    }
  }

  /**
   * Filtra en tiempo real los comercios aprobados (`status == 'APPROVED'`) de Firestore
   * por nombre o categoría/rubro conforme cambia [queryFlow], reutilizando el listener activo.
   */
  fun observeApprovedBusinesses(queryFlow: Flow<String>): Flow<List<DirectoryBusiness>> {
    val normalizedQueryFlow = queryFlow.map { it.trim() }.distinctUntilChanged()
    return combine(observeApprovedBusinesses(), normalizedQueryFlow) { approvedBusinesses, query ->
      filterApprovedBusinessesByNameOrCategory(approvedBusinesses, query)
    }
  }

  /**
   * Alias de compatibilidad para el directorio público aprobado (`status == 'APPROVED'`).
   */
  fun observeActiveBusinesses(): Flow<List<DirectoryBusiness>> = observeApprovedBusinesses()

  /**
   * Crea o actualiza un comercio en Firestore vinculado al UID del dueño autenticado.
   * Las altas nuevas siempre inician con `status = PENDING` y el cliente no puede modificar `status`.
   */
  suspend fun saveBusiness(
    listing: FirestoreBusinessListing,
    existingDocId: String? = null,
  ): Result<String> {
    val path =
      if (existingDocId.isNullOrBlank()) FirestoreCollections.BUSINESSES
      else "${FirestoreCollections.BUSINESSES}/$existingDocId"

    return try {
      val (db, uid) = requireAuthenticatedFirestore()
      val cleanName = listing.name.trim()
      val cleanAddress = listing.address.trim()
      require(cleanName.length in 2..120) {
        "El nombre del comercio debe tener entre 2 y 120 caracteres."
      }
      require(cleanAddress.length in 2..160) {
        "La dirección debe tener entre 2 y 160 caracteres."
      }

      if (existingDocId.isNullOrBlank()) {
        val docRef = db.collection(FirestoreCollections.BUSINESSES).document()
        val pendingListing = listing.copy(status = BusinessStatus.PENDING.name, isActive = false)
        val payload = pendingListing.toMap(ownerUid = uid, isCreate = true)
        docRef.set(payload).await()
        Result.success(docRef.id)
      } else {
        val docRef = db.collection(FirestoreCollections.BUSINESSES).document(existingDocId)
        val updateMap = buildBusinessUpdateMap(listing)
        docRef.update(updateMap).await()
        Result.success(existingDocId)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(
        e,
        if (existingDocId.isNullOrBlank()) OperationType.CREATE else OperationType.UPDATE,
        path,
      )
      Result.failure(e)
    }
  }

  suspend fun updateBusinessOperationalFlags(
    docId: String,
    isOpenNow: Boolean,
    hasDelivery: Boolean,
  ): Result<Unit> {
    val path = "${FirestoreCollections.BUSINESSES}/$docId"
    return try {
      val (db, _) = requireAuthenticatedFirestore()
      db.document(path)
        .update(
          mapOf(
            "isOpenNow" to isOpenNow,
            "hasDelivery" to hasDelivery,
            "openingHoursSummary" to
              listOf(if (isOpenNow) "Atendiendo ahora" else "Cerrado temporalmente"),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun deleteBusiness(docId: String): Result<Unit> {
    val path = "${FirestoreCollections.BUSINESSES}/$docId"
    return try {
      val (db, _) = requireAuthenticatedFirestore()
      db.document(path).delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }

  /**
   * Observa las reseñas con calificación de estrellas de un comercio en tiempo real.
   */
  fun observeReviews(businessId: String): Flow<List<BusinessReview>> {
    val db = activePublicFirestore() ?: return flowOf(emptyList())
    val safeBizId = sanitizeDocId(businessId)
    if (safeBizId.isBlank()) return flowOf(emptyList())
    val path = "${FirestoreCollections.BUSINESSES}/$safeBizId/${FirestoreCollections.REVIEWS}"

    return callbackFlow {
      val registration =
        db.collection(path)
          .orderBy("createdAt", Query.Direction.DESCENDING)
          .addSnapshotListener { snapshot, error ->
            if (error != null) {
              handleFirestoreError(error, OperationType.LIST, path)
              trySend(emptyList())
              return@addSnapshotListener
            }
            val list =
              snapshot?.documents
                ?.mapNotNull { doc -> FirestoreReview.fromSnapshot(doc)?.toDomain() }
                .orEmpty()
            trySend(list)
          }
      awaitClose { registration.remove() }
    }.catch { error ->
      if (error is CancellationException) throw error
      handleFirestoreError(error, OperationType.LIST, path)
      emit(emptyList())
    }
  }

  /**
   * Guarda una nueva reseña (estrellas 1..5 y comentario) en Firestore para el comercio indicado.
   */
  suspend fun submitReview(
    businessId: String,
    rating: Int,
    comment: String,
    authorName: String,
  ): Result<Unit> {
    val safeBizId = sanitizeDocId(businessId)
    val path = "${FirestoreCollections.BUSINESSES}/$safeBizId/${FirestoreCollections.REVIEWS}"
    return try {
      val (db, uid) = requireAuthenticatedFirestore()
      val cleanComment = comment.trim()
      require(rating in 1..5) { "Elegí una calificación entre 1 y 5 estrellas." }
      require(cleanComment.isNotEmpty()) { "Escribí un comentario para publicar tu reseña." }
      require(cleanComment.length <= 600) { "El comentario no puede superar los 600 caracteres." }

      val review =
        FirestoreReview(
          businessId = safeBizId,
          userId = uid,
          authorName = authorName.trim().ifBlank { "Usuario" }.take(80),
          rating = rating.toLong(),
          comment = cleanComment,
        )
      db.collection(path).document().set(review.toMap(ownerUid = uid, isCreate = true)).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  /**
   * Observa la lista personalizada de comercios favoritos del usuario autenticado.
   */
  fun observeFavorites(userId: String): Flow<List<DirectoryBusiness>> {
    if (userId.isBlank()) return flowOf(emptyList())
    val db =
      if (configService != null) {
        configService.getAuthenticatedFirestore(userId).firestore
      } else {
        staticFirestore
      } ?: return flowOf(emptyList())
    val path = "${FirestoreCollections.USERS}/$userId/${FirestoreCollections.FAVORITES}"

    return callbackFlow {
      val registration =
        db.collection(path)
          .orderBy("createdAt", Query.Direction.DESCENDING)
          .addSnapshotListener { snapshot, error ->
            if (error != null) {
              handleFirestoreError(error, OperationType.LIST, path)
              trySend(emptyList())
              return@addSnapshotListener
            }
            val favorites =
              snapshot?.documents
                ?.mapNotNull { doc -> FirestoreFavorite.fromSnapshot(doc)?.toDirectoryBusiness() }
                .orEmpty()
            trySend(favorites)
          }
      awaitClose { registration.remove() }
    }.catch { error ->
      if (error is CancellationException) throw error
      handleFirestoreError(error, OperationType.LIST, path)
      emit(emptyList())
    }
  }

  /**
   * Agrega o quita un comercio de la lista de favoritos en Firestore.
   */
  suspend fun toggleFavorite(
    userId: String,
    business: DirectoryBusiness,
    currentlyFavorite: Boolean,
  ): Result<Unit> {
    val safeFavId = sanitizeDocId(business.id)
    val path = "${FirestoreCollections.USERS}/$userId/${FirestoreCollections.FAVORITES}/$safeFavId"
    return try {
      val (db, uid) = requireAuthenticatedFirestore(expectedUid = userId)
      require(uid == userId) { "La sesión activa no coincide con el usuario solicitado." }
      if (currentlyFavorite) {
        db.document(path).delete().await()
      } else {
        val fav = FirestoreFavorite.fromDirectoryBusiness(business, userId = uid)
        db.document(path).set(fav.toMap(ownerUid = uid, isCreate = true)).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(
        e,
        if (currentlyFavorite) OperationType.DELETE else OperationType.WRITE,
        path,
      )
      Result.failure(e)
    }
  }

  suspend fun upsertUserProfile(
    userId: String,
    displayName: String,
    preferredLocalityId: String = PatagonianLocalities.NeuquenCapital.id,
  ): Result<Unit> {
    val path = "${FirestoreCollections.USERS}/$userId"
    return try {
      val (db, uid) = requireAuthenticatedFirestore(expectedUid = userId)
      require(uid == userId)
      val docRef = db.document(path)
      val snap = docRef.get().await()
      if (snap.exists()) {
        docRef
          .update(
            mapOf(
              "displayName" to displayName.trim().ifBlank { "Comerciante" }.take(80),
              "preferredLocalityId" to preferredLocalityId,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else {
        val profile =
          FirestoreUserProfile(
            userId = uid,
            displayName = displayName.trim().ifBlank { "Comerciante" }.take(80),
            preferredLocalityId = preferredLocalityId,
          )
        docRef.set(profile.toMap(ownerUid = uid, isCreate = true)).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  companion object {
    private const val TAG = "FirestoreDirectoryRepo"
    const val FIELD_STATUS = "status"
    val PUBLIC_VISIBLE_STATUS: String = BusinessStatus.APPROVED.name
    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")

    fun publicBusinessesQuery(db: FirebaseFirestore): Query =
      db.collection(FirestoreCollections.BUSINESSES)
        .whereEqualTo(FIELD_STATUS, PUBLIC_VISIBLE_STATUS)

    internal fun normalizeSearchText(raw: String): String {
      val trimmed = raw.trim()
      if (trimmed.isEmpty()) return ""
      val decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
      return DIACRITICS_REGEX.replace(decomposed, "").lowercase()
    }

    /**
     * Filtra únicamente comercios con estado [BusinessStatus.APPROVED] coincidiendo por nombre o
     * categoría/rubro (insensible a mayúsculas y tildes).
     */
    fun filterApprovedBusinessesByNameOrCategory(
      businesses: List<DirectoryBusiness>,
      query: String,
    ): List<DirectoryBusiness> {
      val approvedOnly = businesses.filter { it.status == BusinessStatus.APPROVED }
      val normalizedQuery = normalizeSearchText(query)
      if (normalizedQuery.isEmpty()) return approvedOnly

      val tokens = normalizedQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }
      return approvedOnly.filter { business ->
        val category = DirectoryCategories.findById(business.categoryId)
        val searchableFields =
          listOf(
            normalizeSearchText(business.name),
            normalizeSearchText(business.categoryName),
            normalizeSearchText(business.categoryId),
            normalizeSearchText(category.name),
            normalizeSearchText(category.shortName),
          )
        tokens.all { token -> searchableFields.any { field -> field.contains(token) } }
      }
    }

    fun sanitizeDocId(rawId: String): String =
      rawId.trim().replace("/", "_").replace(" ", "_").take(150)

    fun buildBusinessUpdateMap(listing: FirestoreBusinessListing): Map<String, Any> {
      val map =
        mutableMapOf<String, Any?>(
          "name" to listing.name.trim(),
          "categoryId" to listing.categoryId,
          "categoryName" to listing.categoryName.trim(),
          "localityId" to listing.localityId,
          "localityName" to listing.localityName.trim(),
          "address" to listing.address.trim(),
          "latitude" to listing.latitude,
          "longitude" to listing.longitude,
          "phoneNumber" to listing.phoneNumber?.trim()?.takeIf { it.isNotEmpty() },
          "whatsappNumber" to listing.whatsappNumber?.trim()?.takeIf { it.isNotEmpty() },
          "websiteUri" to listing.websiteUri?.trim()?.takeIf { it.isNotEmpty() },
          "description" to listing.description?.trim()?.takeIf { it.isNotEmpty() },
          "openingHoursSummary" to listing.openingHoursSummary,
          "isOpenNow" to listing.isOpenNow,
          "hasDelivery" to listing.hasDelivery,
          "markerColorArgb" to listing.markerColorArgb,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      @Suppress("UNCHECKED_CAST")
      return map.filterValues { it != null } as Map<String, Any>
    }

    internal fun isPlaceholderConfigValue(raw: String?): Boolean =
      FirebaseConfigurationService.isPlaceholderConfigValue(raw)

    internal fun isInvalidDatabaseId(raw: String?): Boolean =
      FirebaseConfigurationService.isInvalidDatabaseId(raw)

    internal fun readStringResourceSafely(context: Context, resourceName: String): String? =
      FirebaseConfigurationService.readNonSecretStringResource(context, resourceName)

    /**
     * Resuelve el ID nombrado de Firestore (generado por AI Studio en `firebase_applet_config.xml`)
     * mediante `FirebaseConfigurationService`, sin utilizar la base `(default)` ni credenciales privadas.
     */
    fun resolveNamedFirestoreWithStatus(
      context: Context,
      databaseIdOverride: String? = null,
    ): NamedFirestoreResolution {
      val service = FirebaseConfigurationService(context, databaseIdOverride)
      val resolution = service.getPublicFirestore()
      service.release()
      return resolution
    }

    fun resolveNamedFirestore(
      context: Context,
      databaseIdOverride: String? = null,
    ): FirebaseFirestore? = resolveNamedFirestoreWithStatus(context, databaseIdOverride).firestore
  }
}
