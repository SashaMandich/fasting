package dev.local.fasting.ui.info

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.local.fasting.domain.FastStage
import dev.local.fasting.domain.MealComposition
import dev.local.fasting.domain.StageEngine
import dev.local.fasting.ui.components.SectionHeader
import dev.local.fasting.ui.format.formatHoursDecimal
import dev.local.fasting.ui.theme.Viz

private data class Term(val term: String, val meaning: String)

/**
 * Plain-language glossary for every piece of jargon the app puts on screen. Written so the numbers
 * elsewhere in the app can be interpreted without leaving it — and so the estimates are clearly
 * labelled as estimates.
 */
@Composable
fun InfoScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = "What the numbers in this app mean, in plain language.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { SectionHeader(title = "The five phases", subtitle = "Hours shown for a balanced pre-fast meal") }

        items(FastStage.entries.size) { index ->
            val stage = FastStage.entries[index]
            PhaseCard(stage)
        }

        item {
            GlossaryCard(
                title = "Autophagy terms",
                terms = listOf(
                    Term(
                        "Autophagy",
                        "Literally \"self-eating\": the cell's recycling process. It breaks down damaged " +
                            "proteins and worn-out organelles and reuses the parts. Fasting is one trigger, " +
                            "because autophagy ramps up when nutrients — especially amino acids — are scarce.",
                    ),
                    Term(
                        "Early autophagy",
                        "The stage this app marks from roughly 18h in. Recycling is measurably switched on " +
                            "but still building; the app counts hours from here as \"autophagic hours\".",
                    ),
                    Term(
                        "Deep autophagy",
                        "From roughly 24h. Recycling has been running long enough to work through more " +
                            "substantial cellular debris, and growth signalling stays suppressed.",
                    ),
                    Term(
                        "Extended repair / peak autophagy",
                        "From roughly 48h. Growth-hormone levels rise and repair signalling peaks. Fasts " +
                            "this long are a different proposition physiologically — worth planning, not improvising.",
                    ),
                    Term(
                        "mTOR",
                        "The cell's \"grow now\" switch, active when food (especially protein) is plentiful. " +
                            "While mTOR is high, autophagy is largely off.",
                    ),
                    Term(
                        "AMPK",
                        "The \"energy is low\" sensor. As fuel drops, AMPK rises, mTOR falls, and autophagy " +
                            "is switched on. This seesaw is why the stage boundaries exist at all.",
                    ),
                    Term(
                        "Glycogen",
                        "Carbohydrate stored in the liver and muscles — roughly 12–24 hours of liver supply. " +
                            "Autophagy meaningfully picks up only after the liver store is largely drained, " +
                            "which is why the pre-fast meal shifts every boundary.",
                    ),
                    Term(
                        "Ketosis",
                        "Once glycogen runs low the liver converts fat into ketones for fuel. It usually " +
                            "overlaps the glycogen-depletion stage and is a sign the switch has happened.",
                    ),
                ),
            )
        }

        item {
            GlossaryCard(
                title = "How this app measures things",
                terms = listOf(
                    Term(
                        "Onset modifier",
                        "The pre-fast meal scales every boundary after the digestive phase: " +
                            MealComposition.entries.joinToString(", ") {
                                "${it.displayName} ×${"%.2f".format(it.onsetMultiplier)}"
                            } +
                            ". So after a keto meal, early autophagy is credited from " +
                            formatHoursDecimal(
                                StageEngine.onsetHours(FastStage.EARLY_AUTOPHAGY, MealComposition.KETO_LOW_CARB)
                            ) +
                            " instead of 18.0h.",
                    ),
                    Term(
                        "Autophagic hours",
                        "Hours of a fast that fall at or past that fast's early-autophagy boundary. A 20h " +
                            "fast after a keto meal banks 5.6 of them; the same fast after a high-carb meal banks none.",
                    ),
                    Term(
                        "Deep autophagy ratio",
                        "For a calendar month: autophagic hours ÷ total fasted hours. It answers \"how much " +
                            "of my fasting time actually reached the recycling window\" rather than \"how long did I fast\".",
                    ),
                    Term(
                        "Extended fast (24h+)",
                        "Any fast of 24 hours or more. On the commitment calendar, every day such a fast " +
                            "touches carries a dot.",
                    ),
                    Term(
                        "7-day EMA (weight)",
                        "Exponential moving average — a trendline that weights recent weigh-ins most heavily " +
                            "(smoothing factor 2 ÷ (days + 1)). Day-to-day water shifts are noise; the EMA is the signal. " +
                            "A missed day leaves the trend untouched rather than dragging it.",
                    ),
                    Term(
                        "Energy / clarity / hunger 1–5",
                        "Your own subjective scores, logged during a fast. The insights screen plots them " +
                            "against how deep into the fast they were recorded and which pre-fast meal preceded it.",
                    ),
                    Term(
                        "Pearson r",
                        "A correlation coefficient between −1 and +1: how tightly two things move together. " +
                            "It shows association, never causation — and with one person's log, treat it as a hint " +
                            "to investigate rather than a finding.",
                    ),
                ),
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "About these timings",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = "Stage boundaries are population estimates, not measurements of your body. " +
                            "Real onset depends on your last meal, activity, sleep, muscle mass, medication " +
                            "and more — the app cannot see any of that. Nothing here is medical advice; " +
                            "extended fasting is not appropriate for everyone (including during pregnancy, " +
                            "with diabetes or an eating-disorder history, or alongside certain medication). " +
                            "Talk to a clinician before making a habit of long fasts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PhaseCard(stage: FastStage) {
    val viz = Viz.colors
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(viz.forStage(stage)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stage.displayName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stage.baseEndHours?.let { "${stage.baseStartHours.toInt()}–${it.toInt()}h" }
                        ?: "${stage.baseStartHours.toInt()}h+",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stage.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GlossaryCard(title: String, terms: List<Term>) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            terms.forEachIndexed { index, term ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = term.term,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = term.meaning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
