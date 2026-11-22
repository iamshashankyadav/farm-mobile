package com.farm.customer.land

import com.farm.core.api.LandApi
import com.farm.core.api.ReservationCreate
import com.farm.core.api.ReservationOut
import com.farm.customer.browse.haversineM
import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

@Serializable
data class PlotSeasonItem(
    val id: String,
    val plotId: String = "",
    val season: String,
    val startDate: String = "",
    val endDate: String = "",
    val pricePerKg: Double = 0.0,
    val allowedCrops: List<String> = emptyList(),
)

@Serializable
data class PlotItem(
    val id: String,
    val farmerId: String = "",
    /** Not returned by the land API yet; shown only when known. */
    val farmerName: String = "",
    val farmerPhone: String? = null,
    val title: String,
    val description: String? = null,
    val areaAcres: Double = 0.0,
    val soilType: String? = null,
    val waterSource: String? = null,
    val status: String = "AVAILABLE",
    /** Not returned by the plot search yet; shown only when known. */
    val village: String = "",
    val district: String = "",
    val distanceM: Double = 0.0,
    /** [lng, lat] */
    val centroid: List<Double> = listOf(0.0, 0.0),
    val boundary: List<List<List<Double>>> = emptyList(),
    val seasons: List<PlotSeasonItem> = emptyList(),
) {
    val minPrice: Double? get() = seasons.minOfOrNull { it.pricePerKg }
}

@Serializable
data class ReservationItem(
    val id: String,
    val plotId: String = "",
    val plotTitle: String = "",
    val plotSeasonId: String = "",
    val seasonName: String = "",
    val farmerId: String = "",
    val farmerName: String = "",
    val farmerPhone: String? = null,
    val cropId: String = "",
    val cropName: String = "",
    val expectedQtyKg: Double = 0.0,
    val status: String = "PENDING",
    val deposit: Double = 0.0,
    val total: Double = 0.0,
    val createdAt: String = "",
    /** When a PENDING request lapses if the farmer does not answer. */
    val expiresAt: String? = null,
)

enum class LandSortOption { DISTANCE, ACRES_DESC, ACRES_ASC, PRICE_ASC, PRICE_DESC }

data class LandFilterState(
    val query: String = "",
    val season: String? = null,
    val crop: String? = null,
    val soilType: String? = null,
    val radiusKm: Int = 25,
    val minAcres: Double? = null,
    val maxAcres: Double? = null,
    val sort: LandSortOption = LandSortOption.DISTANCE,
)

fun filterPlots(plots: List<PlotItem>, f: LandFilterState): List<PlotItem> = plots.filter { plot ->
    val q = f.query.trim().lowercase()
    if (q.isNotEmpty()) {
        val hit = plot.title.lowercase().contains(q) || plot.description?.lowercase()?.contains(q) == true ||
            plot.farmerName.lowercase().contains(q) || plot.district.lowercase().contains(q) ||
            plot.village.lowercase().contains(q) || plot.seasons.any { s -> s.allowedCrops.any { it.lowercase().contains(q) } }
        if (!hit) return@filter false
    }
    if (f.season != null && f.season != "ALL" && plot.seasons.none { it.season.equals(f.season, true) }) return@filter false
    if (f.crop != null && f.crop != "ALL" && plot.seasons.none { s -> s.allowedCrops.any { it.equals(f.crop, true) } }) return@filter false
    if (f.soilType != null && f.soilType != "ALL" && plot.soilType?.equals(f.soilType, true) != true) return@filter false
    if (f.radiusKm > 0 && plot.distanceM > f.radiusKm * 1000) return@filter false
    if (f.minAcres != null && f.minAcres > 0 && plot.areaAcres < f.minAcres) return@filter false
    if (f.maxAcres != null && f.maxAcres > 0 && plot.areaAcres > f.maxAcres) return@filter false
    true
}

fun sortPlots(plots: List<PlotItem>, sort: LandSortOption): List<PlotItem> = when (sort) {
    LandSortOption.DISTANCE -> plots.sortedBy { it.distanceM }
    LandSortOption.ACRES_DESC -> plots.sortedByDescending { it.areaAcres }
    LandSortOption.ACRES_ASC -> plots.sortedBy { it.areaAcres }
    LandSortOption.PRICE_ASC -> plots.sortedBy { it.minPrice ?: 999.0 }
    LandSortOption.PRICE_DESC -> plots.sortedByDescending { it.seasons.maxOfOrNull { s -> s.pricePerKg } ?: 0.0 }
}

private fun r2(v: Double) = (v * 100).roundToLong() / 100.0

/** Deposit (20%) and total estimated reservation cost. */
fun reservationEstimate(pricePerKg: Double, qtyKg: Double): Pair<Double, Double> {
    val total = r2(pricePerKg * maxOf(0.0, qtyKg))
    return r2(total * 0.2) to total
}

/** "wheat" -> "Wheat", "green_chilli" -> "Green chilli". */
fun cropTitle(cropId: String): String = cropId.replace('_', ' ').trim().lowercase().replaceFirstChar { it.uppercase() }

/** A reservation as the server returned it; [plot] and [season] fill the names when known. */
fun ReservationOut.toItem(plot: PlotItem? = null, season: PlotSeasonItem? = null): ReservationItem = ReservationItem(
    id = id, plotId = plot?.id ?: "", plotTitle = plot?.title ?: plotTitle ?: "",
    plotSeasonId = plotSeasonId, seasonName = season?.season ?: seasonName ?: "",
    farmerId = farmerId, farmerName = plot?.farmerName ?: "", farmerPhone = plot?.farmerPhone,
    cropId = cropId, cropName = cropTitle(cropId), expectedQtyKg = expectedQtyKg, status = status,
    deposit = deposit ?: 0.0, total = total ?: 0.0, createdAt = createdAt ?: "", expiresAt = expiresAt,
)

/** The land service only. A failed call throws; nothing is invented. */
object LandRepository {
    suspend fun fetchPlots(lat: Double, lng: Double, radiusKm: Int): List<PlotItem> =
        LandApi.searchPlots(lat, lng, radiusKm).map { p ->
            val c = p.centroid?.coordinates?.takeIf { it.size >= 2 }
            PlotItem(
                id = p.id, farmerId = p.farmerId, title = p.title, description = p.description, areaAcres = p.areaAcres,
                soilType = p.soilType, waterSource = p.waterSource, status = p.status ?: "ACTIVE",
                distanceM = c?.let { haversineM(lat, lng, it[1], it[0]) } ?: 0.0,
                centroid = c ?: listOf(lng, lat), boundary = p.boundary?.coordinates ?: emptyList(),
                seasons = p.seasons.map { s -> PlotSeasonItem(s.id, s.plotId, s.season, s.startDate, s.endDate, s.price, s.allowedCrops) },
            )
        }

    suspend fun fetchReservations(): List<ReservationItem> = LandApi.getMyReservations().map { it.toItem() }

    /** POST /land/reservations with the plot-season id. Throws the server's error (e.g. SEASON_ENDED). */
    suspend fun submitReservation(plot: PlotItem, season: PlotSeasonItem, cropId: String, qtyKg: Double): ReservationItem =
        LandApi.createReservation(ReservationCreate(season.id, cropId, qtyKg)).toItem(plot, season)
}
