package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.domain.model.BusinessReview
import com.example.domain.model.DirectoryBusiness
import com.example.domain.model.PatagonianLocalities
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/**
 * Repositorio central de Cloud Firestore para Guía Genio:
 * - Comercios publicados (`businesses/{businessId}`)
 * - Reseñas y calificaciones (`businesses/{businessId}/reviews/{reviewId}`)
 * - Favoritos por usuario (`users/{userId}/favorites/{businessId}`)
 * - Perfiles de usuarios/dueños (`users/{userId}`)
 */
class FirestoreDirectoryRepository(context: Context) {

  private val appContext = context.applicationContext
  private val firestore: FirebaseFirestore? = resolveNamedFirestore(appContext)

  val isConfigured: Boolean
    get() = firestore != null

  private fun requireUserId(): String {
    if (FirebaseApp.getApps(appContext).isEmpty()) {
      throw IllegalStateException("Firebase no está inicializado en esta compilación.")
    }
    return Firebase.auth.currentUser?.uid
      ?: throw IllegalStateException("Debés iniciar sesión con Google para realizar esta acción.")
  }

  /**
   * Observa en tiempo real los comercios activos publicados en Firestore.
   * Si Firebase aún no está provisionado, emite lista vacía sin fallar.
   */
  fun observeActiveBusinesses(): Flow<List<DirectoryBusiness>> {
    val db = firestore ?: return flowOf(emptyList())
    val path = FirestoreCollections.BUSINESSES
    return callbackFlow {
      val registration =
        db.collection(path)
          .whereEqualTo("isActive", true)
          .addSnapshotListener { snapshot, error ->
            if (error != null) {
              handleFirestoreError(error, OperationType.LIST, path)
              trySend(emptyList())
              return@addSnapshotListener
            }
            val list =
              snapshot?.documents
                ?.mapNotNull { doc ->
                  FirestoreBusinessListing.fromSnapshot(doc)?.toDirectoryBusiness()
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
   * Crea o actualiza un comercio en Firestore vinculado al UID del dueño autenticado.
   * Devuelve el ID del documento Firestore guardado.
   */
  suspend fun saveBusiness(
    listing: FirestoreBusinessListing,
    existingDocId: String? = null,
  ): Result<String> {
    val db =
      firestore
        ?: return Result.failure(
          IllegalStateException(
            "Firebase no está configurado en el proyecto (falta google-services.json / firestore_database_id)."
          )
        )
    val path =
      if (existingDocId.isNullOrBlank()) FirestoreCollections.BUSINESSES
      else "${FirestoreCollections.BUSINESSES}/$existingDocId"

    return try {
      val uid = requireUserId()
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
        val payload = listing.toMap(ownerUid = uid, isCreate = true)
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
    val db = firestore ?: return Result.failure(IllegalStateException("Firestore no configurado."))
    val path = "${FirestoreCollections.BUSINESSES}/$docId"
    return try {
      requireUserId()
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
    val db = firestore ?: return Result.failure(IllegalStateException("Firestore no configurado."))
    val path = "${FirestoreCollections.BUSINESSES}/$docId"
    return try {
      requireUserId()
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
    val db = firestore ?: return flowOf(emptyList())
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
    val db =
      firestore
        ?: return Result.failure(
          IllegalStateException(
            "Configurá Firebase (google-services.json) para publicar reseñas en Firestore."
          )
        )
    val safeBizId = sanitizeDocId(businessId)
    val path = "${FirestoreCollections.BUSINESSES}/$safeBizId/${FirestoreCollections.REVIEWS}"
    return try {
      val uid = requireUserId()
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
    val db = firestore ?: return flowOf(emptyList())
    if (userId.isBlank()) return flowOf(emptyList())
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
    val db = firestore ?: return Result.failure(IllegalStateException("Firestore no configurado."))
    val safeFavId = sanitizeDocId(business.id)
    val path = "${FirestoreCollections.USERS}/$userId/${FirestoreCollections.FAVORITES}/$safeFavId"
    return try {
      val uid = requireUserId()
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
    val db = firestore ?: return Result.failure(IllegalStateException("Firestore no configurado."))
    val path = "${FirestoreCollections.USERS}/$userId"
    return try {
      val uid = requireUserId()
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
          "isActive" to listing.isActive,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      @Suppress("UNCHECKED_CAST")
      return map.filterValues { it != null } as Map<String, Any>
    }

    private fun resolveNamedFirestore(context: Context): FirebaseFirestore? {
      return try {
        val apps = FirebaseApp.getApps(context)
        val app = apps.firstOrNull() ?: FirebaseApp.initializeApp(context) ?: return null
        val resId =
          context.resources.getIdentifier(
            "firestore_database_id",
            "string",
            context.packageName,
          )
        if (resId == 0) {
          Log.w(TAG, "Recurso firestore_database_id no encontrado; Firestore remoto en espera.")
          return null
        }
        val dbId = context.getString(resId).trim()
        if (dbId.isBlank()) {
          Log.w(TAG, "firestore_database_id vacío; Firestore remoto en espera.")
          return null
        }
        FirebaseFirestore.getInstance(app, dbId)
      } catch (e: Exception) {
        Log.w(TAG, "No se pudo inicializar Firestore con nombre de base de datos: ${e.message}")
        null
      }
    }
  }
}
