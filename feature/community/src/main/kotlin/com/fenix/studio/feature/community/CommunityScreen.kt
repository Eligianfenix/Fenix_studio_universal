/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 01:31:35 -03:00
 * ARCHIVO: feature/community/src/main/kotlin/com/fenix/studio/feature/community/CommunityScreen.kt
 * ================================================================================
 */

package com.fenix.studio.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fenix.studio.core.designsystem.modifiers.fenixMinTouchTarget
import com.fenix.studio.core.designsystem.theme.FenixTheme

/**
 * Pantalla `P-10` (Hub Comunitario Soberano, Repositorio Solidario y Catálogo de 70 Sitios Útiles, `MF-8.1`).
 */
@Composable
fun CommunityScreen(
    state: CommunityUiState,
    onTabSelected: (CommunityHubTab) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSelectAreaFilter: (AcademicKnowledgeArea?) -> Unit,
    onOpenSiteUrl: (String) -> Unit,
    onSharePackageClick: (CommunitySharedPackage) -> Unit,
    onSubmitNewRequest: (String, AcademicKnowledgeArea) -> Unit,
    onDismissToast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FenixTheme.colors
    val typography = MaterialTheme.typography

    var isNewRequestDialogVisible by remember { mutableStateOf(false) }
    var requestTopicInput by remember { mutableStateOf("") }
    var requestSelectedArea by remember { mutableStateOf(AcademicKnowledgeArea.SOCIAL_SCIENCES) }

    if (isNewRequestDialogVisible) {
        AlertDialog(
            onDismissRequest = { isNewRequestDialogVisible = false },
            title = {
                Text(
                    text = "Nueva Petición Solidaria de Apuntes",
                    style = typography.titleMedium,
                    color = colors.onSurface,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "¿Qué tema o material abierto necesitas que la comunidad comparta?",
                        style = typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    BasicTextField(
                        value = requestTopicInput,
                        onValueChange = { requestTopicInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.background)
                            .border(colors.borderStrokeWidth, colors.outline, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        textStyle = typography.bodyMedium.copy(color = colors.onBackground),
                    )
                    Text(
                        text = "Área de conocimiento: ${requestSelectedArea.displayLabel}",
                        style = typography.labelSmall,
                        color = colors.primary,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (requestTopicInput.isNotBlank()) {
                            onSubmitNewRequest(requestTopicInput, requestSelectedArea)
                            requestTopicInput = ""
                            isNewRequestDialogVisible = false
                        }
                    },
                ) {
                    Text("[ Publicar Petición ]")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { isNewRequestDialogVisible = false }) {
                    Text("[ Cancelar ]")
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Cabecera P-10
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Hub Comunitario y Sitios Útiles (P-10)",
                    style = typography.titleMedium,
                    color = colors.onBackground,
                )
                Text(
                    text = "Catálogo Curado a Costo 0 · Repositorio Solidario P2P",
                    style = typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        // Notificación de Feedback
        state.toastFeedbackMessage?.let { feedback ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.primary.copy(alpha = 0.15f))
                    .border(colors.borderStrokeWidth, colors.primary, RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismissToast)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = feedback,
                    style = typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "[ × ]",
                    style = typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.primary,
                )
            }
        }

        // Selector de Pestañas
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CommunityHubTab.entries.forEach { tab ->
                val isSelected = tab == state.activeTab
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) colors.primary else colors.surface)
                        .border(
                            colors.borderStrokeWidth,
                            if (isSelected) colors.primary else colors.outline,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable { onTabSelected(tab) }
                        .fenixMinTouchTarget()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = tab.displayTitle,
                        style = typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) colors.onPrimary else colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Campo de Búsqueda
        BasicTextField(
            value = state.searchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface)
                .border(colors.borderStrokeWidth, colors.outline, RoundedCornerShape(10.dp))
                .padding(12.dp),
            textStyle = typography.bodyMedium.copy(color = colors.onSurface),
            decorationBox = { innerTextField ->
                if (state.searchQuery.isEmpty()) {
                    Text(
                        text = "🔍 Buscar por palabra clave, etiqueta o repositorio...",
                        style = typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                innerTextField()
            },
        )

        // Chips de Áreas de Conocimiento
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val isAllSelected = state.selectedAreaFilter == null
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isAllSelected) colors.primary else colors.surface)
                    .border(
                        colors.borderStrokeWidth,
                        if (isAllSelected) colors.primary else colors.outline,
                        RoundedCornerShape(16.dp),
                    )
                    .clickable { onSelectAreaFilter(null) }
                    .fenixMinTouchTarget()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Todas las Áreas (70)",
                    style = typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isAllSelected) colors.onPrimary else colors.onSurface,
                )
            }

            AcademicKnowledgeArea.entries.forEach { area ->
                val isSelected = area == state.selectedAreaFilter
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) colors.primary else colors.surface)
                        .border(
                            colors.borderStrokeWidth,
                            if (isSelected) colors.primary else colors.outline,
                            RoundedCornerShape(16.dp),
                        )
                        .clickable { onSelectAreaFilter(area) }
                        .fenixMinTouchTarget()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = area.displayLabel,
                        style = typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isSelected) colors.onPrimary else colors.onSurface,
                    )
                }
            }
        }

        // Contenido según Pestaña Activa
        when (state.activeTab) {
            CommunityHubTab.USEFUL_SITES -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.usefulSites, key = { it.id }) { site ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surface)
                                .border(colors.borderStrokeWidth, colors.outline, RoundedCornerShape(10.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = site.title,
                                    style = typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    color = colors.onSurface,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = site.area.displayLabel,
                                    style = typography.labelSmall,
                                    color = colors.primary,
                                )
                            }

                            Text(
                                text = site.description,
                                style = typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Etiquetas: ${site.tags.joinToString(", ")}",
                                    style = typography.labelSmall,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.primary)
                                        .clickable { onOpenSiteUrl(site.url) }
                                        .fenixMinTouchTarget()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "[ Abrir Sitio ↗ ]",
                                        style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = colors.onPrimary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            CommunityHubTab.SOLIDARY_PACKAGES -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.sharedPackages, key = { it.packageId }) { pkg ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surface)
                                .border(colors.borderStrokeWidth, colors.outline, RoundedCornerShape(10.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = pkg.title,
                                    style = typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    color = colors.onSurface,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                )
                                Text(
                                    text = "[ 🛡 Sanitizado ]",
                                    style = typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFF2E7D32),
                                )
                            }

                            Text(
                                text = "Área: ${pkg.knowledgeArea.displayLabel} · Tamaño: ${pkg.sizeBytes / 1024} KB · Descargas: ${pkg.downloadCount}",
                                style = typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )

                            Text(
                                text = "Checksum SHA-256: ${pkg.checksumSha256.take(16)}...",
                                style = typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.primary)
                                    .clickable { onSharePackageClick(pkg) }
                                    .fenixMinTouchTarget()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "[ Compartir vía Android Sharesheet (.fenix) ]",
                                    style = typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colors.onPrimary,
                                )
                            }
                        }
                    }
                }
            }

            CommunityHubTab.SOLIDARY_REQUESTS -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.primary)
                            .clickable { isNewRequestDialogVisible = true }
                            .fenixMinTouchTarget()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "[ + Publicar Nueva Petición Solidaria ]",
                            style = typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.onPrimary,
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.solidaryRequests, key = { it.requestId }) { req ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.surface)
                                    .border(colors.borderStrokeWidth, colors.outline, RoundedCornerShape(10.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = req.knowledgeArea.displayLabel,
                                        style = typography.labelSmall,
                                        color = colors.primary,
                                    )
                                    Text(
                                        text = "Urgencia: ${req.urgencyLevel}",
                                        style = typography.labelSmall,
                                        color = Color(0xFFE65100),
                                    )
                                }
                                Text(
                                    text = req.topicSummary,
                                    style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
