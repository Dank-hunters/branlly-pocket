package com.branlly.pocket.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object HudColors {
    val Background = Color(0xFF020812)
    val BackgroundRaised = Color(0xFF06111E)
    val Panel = Color(0xE6071524)
    val Card = Color(0xF20A1929)
    val Cyan = Color(0xFF24C8FF)
    val CyanBright = Color(0xFF75E6FF)
    val CyanMuted = Color(0xFF286A86)
    val Grid = Color(0xFF123149)
    val TextPrimary = Color(0xFFE7F8FF)
    val TextSecondary = Color(0xFF83A9BC)
    val Success = Color(0xFF35E58A)
    val Warning = Color(0xFFFFB91F)
    val Error = Color(0xFFFF5361)
    val Disabled = Color(0xFF647786)
}

object HudSpacing {
    val Screen = 16.dp
    val Panel = 14.dp
    val Gap = 12.dp
    val Tight = 7.dp
}

val HudCutCornerShape =
    GenericShape { size, _ ->
        val cut = 14f
        moveTo(cut, 0f)
        lineTo(size.width - cut, 0f)
        lineTo(size.width, cut)
        lineTo(size.width, size.height - cut)
        lineTo(size.width - cut, size.height)
        lineTo(cut, size.height)
        lineTo(0f, size.height - cut)
        lineTo(0f, cut)
        close()
    }

@Composable
fun HudPanel(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .then(
                    if (glow) {
                        Modifier.shadow(
                            elevation = 10.dp,
                            shape = HudCutCornerShape,
                            ambientColor = HudColors.Cyan.copy(alpha = 0.28f),
                            spotColor = HudColors.Cyan.copy(alpha = 0.32f),
                        )
                    } else {
                        Modifier
                    },
                ).background(HudColors.Panel, HudCutCornerShape)
                .border(1.dp, HudColors.CyanMuted, HudCutCornerShape)
                .padding(HudSpacing.Panel),
        verticalArrangement = Arrangement.spacedBy(HudSpacing.Tight),
        content = content,
    )
}

@Composable
fun HudCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .background(HudColors.Card, HudCutCornerShape)
                .border(1.dp, HudColors.Grid, HudCutCornerShape)
                .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
fun HudStatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .background(color.copy(alpha = 0.09f), HudCutCornerShape)
                .border(1.dp, color.copy(alpha = 0.5f), HudCutCornerShape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
fun HudPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val accent = if (enabled) HudColors.CyanBright else HudColors.Disabled
    Box(
        modifier =
            modifier
                .height(58.dp)
                .shadow(
                    elevation = if (enabled) 12.dp else 0.dp,
                    shape = HudCutCornerShape,
                    ambientColor = HudColors.Cyan.copy(alpha = 0.35f),
                    spotColor = HudColors.Cyan.copy(alpha = 0.45f),
                ).background(
                    Brush.horizontalGradient(
                        listOf(
                            HudColors.Cyan.copy(alpha = if (enabled) 0.22f else 0.05f),
                            HudColors.Cyan.copy(alpha = if (enabled) 0.46f else 0.08f),
                            HudColors.Cyan.copy(alpha = if (enabled) 0.18f else 0.05f),
                        ),
                    ),
                    HudCutCornerShape,
                ).border(1.dp, accent, HudCutCornerShape)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("▶", color = accent, fontSize = 18.sp)
            Spacer(Modifier.width(12.dp))
            Text(
                text.uppercase(),
                color = accent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 1.2.sp,
            )
        }
    }
}

@Composable
fun HudIconContainer(
    glyph: String,
    modifier: Modifier = Modifier,
    accent: Color = HudColors.Cyan,
) {
    Box(
        modifier =
            modifier
                .size(44.dp)
                .background(accent.copy(alpha = 0.08f), CircleShape)
                .border(1.dp, accent.copy(alpha = 0.62f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatusRing(
    label: String,
    detail: String,
    modifier: Modifier = Modifier,
    color: Color = HudColors.Cyan,
) {
    Box(modifier = modifier.size(176.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outer = size.minDimension * 0.88f
            drawCircle(HudColors.Cyan.copy(alpha = 0.06f), radius = outer / 2f, center = center)
            drawCircle(HudColors.Grid, radius = outer / 2f, center = center, style = Stroke(10.dp.toPx()))
            drawArc(
                color = color.copy(alpha = 0.28f),
                startAngle = -82f,
                sweepAngle = 312f,
                useCenter = false,
                topLeft = Offset((size.width - outer) / 2f, (size.height - outer) / 2f),
                size = Size(outer, outer),
                style = Stroke(13.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -82f,
                sweepAngle = 276f,
                useCenter = false,
                topLeft = Offset((size.width - outer) / 2f, (size.height - outer) / 2f),
                size = Size(outer, outer),
                style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
            )
            drawCircle(color.copy(alpha = 0.85f), radius = 3.dp.toPx(), center = Offset(center.x, 14.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label.uppercase(),
                color = color,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 1.3.sp,
            )
            Text(
                detail.uppercase(),
                color = HudColors.TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
            Text("•••", color = color, letterSpacing = 2.sp)
        }
    }
}

@Composable
fun ActionStepRow(
    index: Int,
    glyph: String,
    title: String,
    summary: String,
    status: String,
    statusColor: Color,
    showConnector: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(22.dp).border(1.dp, HudColors.CyanMuted, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(index.toString(), color = HudColors.CyanBright, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
            if (showConnector) {
                Box(Modifier.width(1.dp).height(48.dp).background(HudColors.CyanMuted))
            }
        }
        Spacer(Modifier.width(8.dp))
        HudIconContainer(glyph = glyph, modifier = Modifier.size(42.dp), accent = statusColor)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title.uppercase(),
                color = HudColors.TextPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Text(summary, color = HudColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
        HudStatusBadge(status, statusColor)
    }
}

@Composable
fun HudBottomNavigation(
    onHome: () -> Unit,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(HudColors.BackgroundRaised)
                .border(1.dp, HudColors.Grid, HudCutCornerShape)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudNavItem("⌂", "ACCUEIL", selected = true, onClick = onHome)
        HudNavItem("＋", "CRÉER", selected = false, onClick = onCreate)
        HudNavItem("⇩", "IMPORTER", selected = false, onClick = onImport)
    }
}

@Composable
private fun HudNavItem(
    glyph: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = if (selected) HudColors.CyanBright else HudColors.TextSecondary
    Column(
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(glyph, color = color, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(label, color = color, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 0.6.sp)
    }
}
