package com.farm.customer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farm.core.ui.FText
import com.farm.core.ui.FarmColors
import com.farm.core.ui.TextVariant
import com.farm.customer.orders.RAIL_STAGES
import com.farm.customer.orders.formatDateTime

private val RailGrey = Color(0xFFDFE3E6)

/**
 * The order status rail: eight segments, the first [stage] green and the rest grey, with [label]
 * (the current stage in words) underneath. Same element as the website's StatusRail.
 */
@Composable
fun StatusRail(stage: Int, label: String, modifier: Modifier = Modifier, total: Int = RAIL_STAGES.size) {
    Column(modifier.semantics { contentDescription = "Status: $label, step $stage of $total" }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(total) { i ->
                Box(
                    Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (i < stage) FarmColors.Primary else RailGrey),
                )
            }
        }
        FText(label, variant = TextVariant.Caption, color = FarmColors.TextBody, modifier = Modifier.padding(top = 6.dp))
    }
}

/**
 * The same stages as a vertical list for the order detail: a dot per stage (filled when reached),
 * who acts at that step, and when it happened. [times] is aligned with [RAIL_STAGES].
 */
@Composable
fun VerticalRail(stage: Int, times: List<String?>) {
    Column {
        RAIL_STAGES.forEachIndexed { i, s ->
            val done = i < stage
            val current = i == stage - 1
            Row(verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(14.dp).clip(CircleShape).background(if (done) FarmColors.Primary else RailGrey),
                    )
                    if (i < RAIL_STAGES.size - 1) {
                        Box(Modifier.width(2.dp).height(30.dp).background(if (i < stage - 1) FarmColors.Primary else RailGrey))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    FText(
                        s.label,
                        variant = TextVariant.Body,
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                        color = if (done) FarmColors.TextDark else FarmColors.TextMuted,
                    )
                    val at = times.getOrNull(i)
                    FText(
                        s.actor + if (done && at != null) " · ${formatDateTime(at)}" else "",
                        variant = TextVariant.Caption,
                    )
                }
            }
        }
    }
}
