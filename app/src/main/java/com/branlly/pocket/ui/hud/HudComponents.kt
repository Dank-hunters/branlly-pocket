package com.branlly.pocket.ui.hud

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat

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
    val Panel = 12.dp
    val Gap = 10.dp
    val Tight = 6.dp
    val CompactWidth = 370.dp
    val NarrowWidth = 340.dp
}

@Composable
fun isHudCompact(maxWidth: Dp): Boolean = maxWidth < HudSpacing.CompactWidth || LocalDensity.current.fontScale >= 1.25f

@Composable
fun HudSurfaceTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    DisposableEffect(activity) {
        val controller = activity?.window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        val previousLightStatusBars = controller?.isAppearanceLightStatusBars
        val previousLightNavigationBars = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            previousLightStatusBars?.let { controller?.isAppearanceLightStatusBars = it }
            previousLightNavigationBars?.let { controller?.isAppearanceLightNavigationBars = it }
        }
    }
    val baseTypography = MaterialTheme.typography
    MaterialTheme(
        colorScheme =
            darkColorScheme(
                primary = HudColors.CyanBright,
                onPrimary = HudColors.Background,
                primaryContainer = HudColors.Card,
                onPrimaryContainer = HudColors.TextPrimary,
                secondary = HudColors.Cyan,
                onSecondary = HudColors.Background,
                secondaryContainer = HudColors.Cyan.copy(alpha = 0.16f),
                onSecondaryContainer = HudColors.TextPrimary,
                tertiary = HudColors.CyanMuted,
                onTertiary = HudColors.TextPrimary,
                tertiaryContainer = HudColors.Card,
                onTertiaryContainer = HudColors.TextPrimary,
                background = HudColors.Background,
                onBackground = HudColors.TextPrimary,
                surface = HudColors.BackgroundRaised,
                onSurface = HudColors.TextPrimary,
                surfaceVariant = HudColors.Card,
                onSurfaceVariant = HudColors.TextSecondary,
                error = HudColors.Error,
                onError = HudColors.Background,
                errorContainer = HudColors.Error.copy(alpha = 0.12f),
                onErrorContainer = HudColors.TextPrimary,
                outline = HudColors.CyanMuted,
                outlineVariant = HudColors.Grid,
                surfaceTint = HudColors.Cyan,
                scrim = Color.Black,
            ),
        typography =
            baseTypography.copy(
                headlineMedium = baseTypography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
                headlineSmall = baseTypography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                titleLarge = baseTypography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                titleMedium = baseTypography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                labelLarge = baseTypography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                labelMedium = baseTypography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                labelSmall = baseTypography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            ),
        content = content,
    )
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
    borderColor: Color = HudColors.CyanMuted,
    backgroundColor: Color = HudColors.Panel,
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
                ).background(backgroundColor, HudCutCornerShape)
                .border(1.dp, borderColor, HudCutCornerShape)
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
                .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        content = content,
    )
}

@Composable
fun HudChoiceCard(
    badge: String,
    title: String,
    description: String,
    glyph: String,
    prominent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val accent = if (prominent) HudColors.CyanBright else HudColors.CyanMuted
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = if (pressed) 0.99f else 1f
                    scaleY = if (pressed) 0.99f else 1f
                }.background(
                    if (pressed) HudColors.Cyan.copy(alpha = 0.13f) else HudColors.Card,
                    HudCutCornerShape,
                ).border(1.dp, accent.copy(alpha = if (prominent) 0.8f else 0.55f), HudCutCornerShape)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudIconContainer(glyph, Modifier.size(42.dp), if (prominent) HudColors.CyanBright else HudColors.TextSecondary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (badge.isNotEmpty()) {
                Text(
                    badge.uppercase(),
                    color = if (prominent) HudColors.CyanBright else HudColors.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 0.7.sp,
                )
            }
            Text(
                title,
                color = HudColors.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                maxLines = 3,
            )
            Text(
                description,
                color = HudColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("›", color = accent, fontFamily = FontFamily.Monospace, fontSize = 22.sp)
    }
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
                .padding(horizontal = 9.dp, vertical = 3.dp),
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
    labelFontSize: TextUnit = 15.sp,
    showLeadingGlyph: Boolean = true,
) {
    val accent = if (enabled) HudColors.CyanBright else HudColors.Disabled
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier =
            modifier
                .height(54.dp)
                .graphicsLayer {
                    scaleX = if (pressed && enabled) 0.985f else 1f
                    scaleY = if (pressed && enabled) 0.985f else 1f
                    alpha = if (enabled) 1f else 0.62f
                }.shadow(
                    elevation = if (enabled && !pressed) 9.dp else 2.dp,
                    shape = HudCutCornerShape,
                    ambientColor = HudColors.Cyan.copy(alpha = 0.28f),
                    spotColor = HudColors.Cyan.copy(alpha = 0.36f),
                ).background(
                    Brush.horizontalGradient(
                        listOf(
                            HudColors.Cyan.copy(alpha = if (enabled) 0.12f else 0.04f),
                            HudColors.Cyan.copy(alpha = if (enabled && !pressed) 0.36f else 0.24f),
                            HudColors.Cyan.copy(alpha = if (enabled) 0.12f else 0.04f),
                        ),
                    ),
                    HudCutCornerShape,
                ).border(1.dp, accent.copy(alpha = if (enabled) 0.9f else 0.45f), HudCutCornerShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            if (showLeadingGlyph) {
                Text("▶", modifier = Modifier.align(Alignment.CenterStart), color = accent, fontSize = 15.sp)
            }
            Text(
                text.uppercase(),
                modifier = Modifier.align(Alignment.Center),
                color = accent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = labelFontSize,
                letterSpacing = 1.1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun HudSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = HudColors.CyanBright,
    height: Dp = 44.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val resolvedAccent = if (enabled) accent else HudColors.Disabled
    Box(
        modifier =
            modifier
                .height(height)
                .graphicsLayer {
                    scaleX = if (pressed && enabled) 0.985f else 1f
                    scaleY = if (pressed && enabled) 0.985f else 1f
                    alpha = if (enabled) 1f else 0.55f
                }.background(
                    resolvedAccent.copy(alpha = if (pressed) 0.14f else 0.06f),
                    HudCutCornerShape,
                ).border(1.dp, resolvedAccent.copy(alpha = 0.65f), HudCutCornerShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            modifier = Modifier.padding(horizontal = 12.dp),
            color = resolvedAccent,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.7.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun HudValidationMessage(
    message: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(HudColors.Error.copy(alpha = 0.08f), HudCutCornerShape)
                .border(1.dp, HudColors.Error.copy(alpha = 0.55f), HudCutCornerShape)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("!", color = HudColors.Error, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(message, modifier = Modifier.weight(1f), color = HudColors.TextPrimary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun HudSectionHeader(
    title: String,
    detail: String? = null,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (detail != null && isHudCompact(maxWidth)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                HudSectionTitle(title)
                Text(detail.uppercase(), color = HudColors.TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HudSectionTitle(title, Modifier.weight(1f))
                detail?.let {
                    Text(it.uppercase(), color = HudColors.TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun HudSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        title.uppercase(),
        modifier = modifier,
        color = HudColors.CyanBright,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 0.8.sp,
    )
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
    Box(modifier = modifier.fillMaxWidth().height(58.dp)) {
        if (showConnector) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 9.5.dp)
                    .width(1.dp)
                    .height(29.dp)
                    .background(HudColors.CyanMuted),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(
                            20.dp,
                        ).background(HudColors.BackgroundRaised, CircleShape)
                        .border(1.dp, HudColors.CyanMuted, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(index.toString(), color = HudColors.CyanBright, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            }
            Spacer(Modifier.width(8.dp))
            HudIconContainer(glyph = glyph, modifier = Modifier.size(38.dp), accent = statusColor)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title.uppercase(),
                    color = HudColors.TextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    summary,
                    color = HudColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            HudStatusBadge(status, statusColor, Modifier.widthIn(min = 64.dp))
        }
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
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .background(HudColors.BackgroundRaised)
                .border(1.dp, HudColors.Grid, HudCutCornerShape)
                .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudNavItem("⌂", "ACCUEIL", selected = true, onClick = onHome)
        HudNavItem("＋", "CRÉER", selected = false, onClick = onCreate)
        HudNavItem("⇩", "IMPORTER", selected = false, onClick = onImport)
    }
}

@Composable
private fun RowScope.HudNavItem(
    glyph: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = if (selected) HudColors.CyanBright else HudColors.TextSecondary
    Column(
        modifier =
            Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .background(if (selected) HudColors.Cyan.copy(alpha = 0.07f) else Color.Transparent, HudCutCornerShape)
                .then(if (selected) Modifier.border(1.dp, HudColors.Grid, HudCutCornerShape) else Modifier)
                .clickable(onClick = onClick)
                .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(glyph, color = color, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(1.dp))
        Text(
            label,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = 0.6.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}
