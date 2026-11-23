package com.farm.customer.ui

import com.farm.customer.browse.NEAR_ME

import com.farm.core.ui.mePin

import androidx.compose.runtime.collectAsState

import com.farm.customer.prefs.LocalLists

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.farm.core.ui.ButtonVariant
import com.farm.core.ui.FText
import com.farm.core.ui.FarmButton
import com.farm.core.ui.FarmCard
import com.farm.core.ui.FarmColors
import com.farm.core.ui.FarmInput
import com.farm.core.ui.Pill
import com.farm.core.ui.MapPin
import com.farm.core.ui.TextVariant
import com.farm.core.ui.TileMap
import com.farm.core.ui.hex
import com.farm.customer.browse.FilterState
import com.farm.customer.browse.LocationCoords
import com.farm.customer.browse.ProduceListing
import com.farm.customer.browse.ProducePhoto
import com.farm.customer.browse.SortOption
import com.farm.customer.browse.cropEmoji
import com.farm.customer.browse.formatDistance
import com.farm.customer.browse.formatRating
import com.farm.customer.browse.ratingLabel
import com.farm.customer.browse.ratingStars
import com.farm.customer.browse.formatHarvestDate
import com.farm.customer.checkout.CartItem
import com.farm.customer.checkout.CartStore
import com.farm.customer.checkout.formatRupees

fun ProduceListing.toCartItem(qty: Double = 1.0) = CartItem(
    listingId = id, farmId = farmId, farmName = farmName, name = displayName, unit = unit,
    unitPrice = pricePerUnit, qty = qty, maxQty = qtyAvailable,
)

fun qtyText(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) FarmColors.Primary else Color.White)
            .border(1.dp, if (selected) FarmColors.Primary else FarmColors.Border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else FarmColors.TextBody)
    }
}

@Composable
fun ListingCard(listing: ProduceListing, onOpen: () -> Unit, onAdded: (ProduceListing) -> Unit) {
    val photo = listing.photos.firstOrNull()
    FarmCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(0.dp), onClick = onOpen) {
        Box(Modifier.fillMaxWidth().height(150.dp).background(FarmColors.PrimaryLight)) {
            if (photo != null && photo.url.isNotEmpty()) {
                AsyncImage(model = photo.thumbUrl.ifEmpty { photo.url }, contentDescription = listing.cropName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text("🥕", fontSize = 48.sp, modifier = Modifier.align(Alignment.Center))
            }
            Row(Modifier.align(Alignment.TopEnd).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (listing.isOrganic) Pill("🌱 Organic", FarmColors.Primary, Color.White)
                FavouriteButton(listing.farmId, listing.farmName)
            }
        }
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    FText(listing.cropName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FarmColors.TextDark)
                    listing.variety?.let { FText(it, variant = TextVariant.Caption) }
                }
            }
            FText("🏡 ${listing.farmName}", variant = TextVariant.Label, modifier = Modifier.padding(top = 6.dp))
            FarmRatingText(listing.farmRating, listing.farmRatingCount, Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("📍 ${formatDistance(listing.distanceM)} away", fontSize = 12.sp, color = FarmColors.Primary, fontWeight = FontWeight.SemiBold)
                Text("  •  ", fontSize = 12.sp, color = FarmColors.InputBorder)
                FText(formatHarvestDate(listing.harvestDate), variant = TextVariant.Caption)
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(formatRupees(listing.pricePerUnit), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FarmColors.PrimaryDark)
                        Text(" / ${listing.unit}", fontSize = 13.sp, color = FarmColors.TextMuted)
                    }
                    FText("${qtyText(listing.qtyAvailable)} ${listing.unit} in stock", variant = TextVariant.Caption)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(FarmColors.Primary)
                        .clickable { CartStore.add(listing.toCartItem()); onAdded(listing) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) { Text("+ Add", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            }
        }
    }
}

/** Filters & sort sheet (port of FilterSheet.tsx). Edits a draft that is applied on "Apply". */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(filters: FilterState, onApply: (FilterState) -> Unit, onReset: () -> Unit, onClose: () -> Unit) {
    var draft by remember { mutableStateOf(filters) }
    var minText by remember { mutableStateOf(filters.minPrice?.let(::qtyText) ?: "") }
    var maxText by remember { mutableStateOf(filters.maxPrice?.let(::qtyText) ?: "") }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            FText("Filters & Sort", variant = TextVariant.H2)
            Section("Search Radius") {
                listOf(10, 25, 50, 100).forEach { r -> Chip("$r km", draft.radiusKm == r, { draft = draft.copy(radiusKm = r) }) }
            }
            Section("Sort By") {
                SortOption.entries.forEach { o -> Chip(o.label, draft.sort == o, { draft = draft.copy(sort = o) }) }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp).clip(RoundedCornerShape(10.dp)).background(FarmColors.Background)
                    .clickable { draft = draft.copy(organicOnly = !draft.organicOnly) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    FText("🌱 Organic Certified Only", variant = TextVariant.Label)
                    FText("Only show listings grown with natural organic methods", variant = TextVariant.Caption)
                }
                Box(
                    Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                        .background(if (draft.organicOnly) FarmColors.Primary else Color.White)
                        .border(1.5.dp, FarmColors.Primary, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) { if (draft.organicOnly) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
            }
            FText("Price Range (₹ per unit)", variant = TextVariant.Label, modifier = Modifier.padding(top = 18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FarmInput(minText, { minText = it }, Modifier.weight(1f), placeholder = "Min (e.g. 10)", keyboardType = KeyboardType.Decimal)
                Text("  to  ", color = FarmColors.TextMuted)
                FarmInput(maxText, { maxText = it }, Modifier.weight(1f), placeholder = "Max (e.g. 100)", keyboardType = KeyboardType.Decimal)
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FarmButton("Reset", { onReset(); onClose() }, Modifier.weight(1f), variant = ButtonVariant.Secondary)
                FarmButton("Apply Filters", {
                    onApply(draft.copy(minPrice = minText.toDoubleOrNull(), maxPrice = maxText.toDoubleOrNull()))
                    onClose()
                }, Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    FText(title, variant = TextVariant.Label, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** Heart that marks a farm as a favourite on this phone (48 dp touch target). */
@Composable
fun FavouriteButton(farmId: String, farmName: String, modifier: Modifier = Modifier) {
    val favourites by LocalLists.favourites.collectAsState()
    val on = favourites.any { it.farmId == farmId }
    Box(
        modifier.size(48.dp).clickable { LocalLists.toggleFavourite(farmId, farmName) }
            .semantics { contentDescription = if (on) "Remove $farmName from favourite farms" else "Add $farmName to favourite farms" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xE6FFFFFF)), contentAlignment = Alignment.Center) {
            Text(if (on) "♥" else "♡", fontSize = 18.sp, color = if (on) FarmColors.Error else FarmColors.TextMuted)
        }
    }
}

/** Stars and count, or "Not rated yet" (search hits and listings carry farmRating / farmRatingCount). */
@Composable
fun FarmRatingText(rating: Double?, count: Int, modifier: Modifier = Modifier) {
    val label = ratingLabel(rating, count)
    Text(
        if (rating != null) "${ratingStars(rating)} ${formatRating(rating)} ($count)" else label,
        fontSize = 12.sp,
        color = if (rating != null) FarmColors.Warning else FarmColors.TextMuted,
        fontWeight = if (rating != null) FontWeight.SemiBold else FontWeight.Normal,
        modifier = modifier.semantics { contentDescription = if (rating != null) "Farm rated ${formatRating(rating)} out of 5 from $count ratings" else label },
    )
}

/** The same results as the list, as markers on OpenStreetMap tiles; a tapped marker opens a summary card. */
@Composable
fun ProduceMapView(listings: List<ProduceListing>, location: LocationCoords, onOpen: (ProduceListing) -> Unit, onAdded: (ProduceListing) -> Unit) {
    var selectedId by remember { mutableStateOf(listings.firstOrNull()?.id) }
    val selected = listings.find { it.id == selectedId } ?: listings.firstOrNull()
    Column(Modifier.fillMaxSize()) {
        TileMap(
            pins = listings.map {
                MapPin(
                    it.id, it.pickupCoordinates.getOrElse(1) { 0.0 }, it.pickupCoordinates.getOrElse(0) { 0.0 },
                    "${it.cropName} · ${formatRupees(it.pricePerUnit)}/${it.unit}", it.farmName,
                )
            } + mePin(location.latitude, location.longitude, if (location.name == NEAR_ME) "You are here" else "Searching around ${location.name}"),
            centerLat = location.latitude,
            centerLng = location.longitude,
            selectedKey = selected?.id,
            onSelect = { selectedId = it },
            description = "Map of ${listings.size} listings near ${location.name}",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        if (selected != null) {
            FarmCard(Modifier.fillMaxWidth().padding(12.dp)) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FText(selected.cropName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FarmColors.TextDark)
                            if (selected.isOrganic) Pill("🌱 Organic", FarmColors.PrimaryLight, FarmColors.Primary, Modifier.padding(start = 6.dp))
                        }
                        FText("🏡 ${selected.farmName}" + placeOf(selected)?.let { " · $it" }.orEmpty(), variant = TextVariant.Label)
                        FarmRatingText(selected.farmRating, selected.farmRatingCount)
                        FText("📍 ${formatDistance(selected.distanceM)} away  •  ${qtyText(selected.qtyAvailable)} ${selected.unit} left", variant = TextVariant.Caption)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatRupees(selected.pricePerUnit), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FarmColors.PrimaryDark)
                        FText("per ${selected.unit}", variant = TextVariant.Caption)
                    }
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FarmButton("View Details", { onOpen(selected) }, Modifier.weight(1f), variant = ButtonVariant.Secondary)
                    FarmButton("+ Add to Cart", { CartStore.add(selected.toCartItem()); onAdded(selected) }, Modifier.weight(1f))
                }
            }
        }
    }
}

/** "Village, District", whichever parts the catalog returned; null when neither. */
fun placeOf(listing: ProduceListing): String? =
    listOfNotNull(listing.village?.takeIf { it.isNotBlank() }, listing.district.takeIf { it.isNotBlank() }).joinToString(", ").ifEmpty { null }
