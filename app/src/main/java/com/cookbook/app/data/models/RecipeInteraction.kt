package com.cookbook.app.data.models

import java.io.Serializable

/**
 * Kochhistorie und Favoriten.
 *
 * Beides lebt auf dem Server, damit Web und App denselben Stand sehen — es wird
 * bewusst nicht lokal zwischengespeichert. Herz und "Fertig" schalten optimistisch
 * und rollen bei einem Fehler zurück.
 */

/** Body von POST /recipes/:id/cooked. servings ist optional. */
data class CookedRequest(
    val servings: Int? = null
)

/** Antwort von POST /recipes/:id/cooked und DELETE /recipes/:id/cooked/last. */
data class CookedResponse(
    val cookCount: Int = 0,
    val lastCookedAt: String? = null
) : Serializable

/** Antwort von PUT/DELETE /recipes/:id/favorite. */
data class FavoriteResponse(
    val isFavorite: Boolean = false
) : Serializable

/**
 * Antwort von GET /recipes/featured — das Rezept der Woche.
 *
 * reason ist "most_cooked" oder "newest"; bei "newest" fehlt die Historie und der
 * Aufmacher zeigt keinen Häufigkeitssatz.
 */
data class FeaturedRecipe(
    val id: String,
    val title: String,
    val images: List<String>? = null,
    val thumbnail: String? = null,
    val totalTime: Int = 0,
    val servings: Int = 4,
    val categories: List<String>? = null,
    val collections: List<RecipeCollection>? = null,
    val cookCount: Int = 0,
    val lastCookedAt: String? = null,
    val isFavorite: Boolean = false,
    val reason: String? = null
) : Serializable {

    val image: String?
        get() = images?.firstOrNull() ?: thumbnail

    val collectionLabel: String?
        get() = collections?.firstOrNull()?.name ?: categories?.firstOrNull()

    /** Nur wenn tatsächlich gekocht wurde, trägt der Aufmacher den Häufigkeitssatz. */
    val hasCookHistory: Boolean
        get() = cookCount > 0 && reason != "newest"
}
