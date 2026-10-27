package app.fototin.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.fototin.R
import app.fototin.data.Settings

/** Qué diálogo de Ajustes está abierto. */
private enum class SettingsDialog { INTERVAL, GRID, GOOGLE_PHOTOS }

/**
 * Pestaña Ajustes, organizada como los ajustes de Android: secciones con un renglón por opción
 * (ícono, nombre y valor actual). Las opciones con varios valores se eligen en un diálogo.
 */
@Composable
fun SettingsTab(
    padding: PaddingValues,
    interval: Int,
    shuffle: Boolean,
    gridSize: Int,
    backupBusy: Boolean,
    photoCount: Int,
    dynamicColor: Boolean,
    onInterval: (Int) -> Unit,
    onShuffle: (Boolean) -> Unit,
    onGridSize: (Int) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    cleaning: Boolean,
    onRemoveDuplicates: () -> Unit,
    installedVersion: String,
    update: UpdateState?,
    checkingUpdates: Boolean,
    autoCheckUpdates: Boolean,
    onCheckUpdates: () -> Unit,
    onAutoCheckUpdates: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    val uriHandler = LocalUriHandler.current

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        // En pantallas anchas, una columna centrada se lee mejor que un renglón de lado a lado.
        modifier = Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 720.dp).fillMaxWidth(),
    ) {
        item {
            Text(
                "Ajustes",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        item {
            Section("Widgets") {
                SettingRow(
                    icon = painterResource(R.drawable.ic_set_schedule),
                    title = "Cambiar de foto",
                    summary = intervalLabel(interval),
                    onClick = { dialog = SettingsDialog.INTERVAL },
                )
                SwitchRow(
                    icon = painterResource(R.drawable.ic_set_shuffle),
                    title = "Orden aleatorio",
                    summary = "Sin repetir hasta mostrar todas",
                    checked = shuffle,
                    onChange = onShuffle,
                )
            }
        }

        item {
            Section("Fotos") {
                SettingRow(
                    icon = painterResource(R.drawable.ic_cloud),
                    title = "Traer de Google Fotos",
                    summary = "Cómo elegir fotos o un álbum entero",
                    onClick = { dialog = SettingsDialog.GOOGLE_PHOTOS },
                )
                SettingRow(
                    icon = painterResource(R.drawable.ic_set_copies),
                    title = "Quitar repetidas",
                    summary = "Se queda la más antigua, con su nota y álbumes",
                    enabled = !cleaning && photoCount > 1,
                    trailing = { if (cleaning) Progress() },
                    onClick = onRemoveDuplicates,
                )
                SettingRow(
                    icon = painterResource(R.drawable.ic_set_grid),
                    title = "Tamaño en la grilla",
                    summary = GRID_LABELS[gridSize.coerceIn(GRID_LABELS.indices)],
                    onClick = { dialog = SettingsDialog.GRID },
                )
            }
        }

        // Material You existe desde Android 12.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item {
                Section("Apariencia") {
                    SwitchRow(
                        icon = painterResource(R.drawable.ic_set_palette),
                        title = "Colores del fondo de pantalla",
                        summary = "En lugar de los colores de Fototin",
                        checked = dynamicColor,
                        onChange = onDynamicColor,
                    )
                }
            }
        }

        item {
            Section("Copia de seguridad") {
                SettingRow(
                    icon = painterResource(R.drawable.ic_set_backup),
                    title = "Guardar copia",
                    summary = "Tus $photoCount fotos con notas, favoritas y álbumes, en un archivo",
                    enabled = !backupBusy && photoCount > 0,
                    trailing = { if (backupBusy) Progress() },
                    onClick = onBackup,
                )
                SettingRow(
                    icon = painterResource(R.drawable.ic_set_restore),
                    title = "Restaurar copia",
                    summary = "Se suma a lo que ya tenés, sin repetir",
                    enabled = !backupBusy,
                    onClick = onRestore,
                )
            }
        }

        item {
            Section("Actualizaciones") {
                SettingRow(
                    icon = painterResource(R.drawable.ic_set_update),
                    title = if (update != null) "Versión ${update.release.versionName} disponible" else "Buscar actualizaciones",
                    summary = when (update) {
                        null -> "Tenés la última que se encontró ($installedVersion)"
                        is UpdateState.Downloading -> "Descargando… ${(update.progress * 100).toInt()} %"
                        is UpdateState.Available -> "Tocá Actualizar para instalarla encima, sin perder nada"
                    },
                    enabled = !checkingUpdates && update !is UpdateState.Downloading,
                    trailing = {
                        when {
                            checkingUpdates || update is UpdateState.Downloading -> Progress()
                            update is UpdateState.Available -> FilledTonalButton(onClick = onUpdate) { Text("Actualizar") }
                            else -> Unit
                        }
                    },
                    onClick = { if (update is UpdateState.Available) onUpdate() else onCheckUpdates() },
                )
                SwitchRow(
                    icon = rememberVectorPainter(Icons.Filled.Refresh),
                    title = "Buscar al abrir la app",
                    summary = "Como mucho cada 6 horas",
                    checked = autoCheckUpdates,
                    onChange = onAutoCheckUpdates,
                )
            }
        }

        item {
            Section("Acerca de") {
                SettingRow(
                    icon = rememberVectorPainter(Icons.Filled.Info),
                    title = "Fototin $installedVersion",
                    summary = "Ver todas las versiones y sus novedades",
                    onClick = { uriHandler.openUri(RELEASES_URL) },
                )
                SettingRow(
                    icon = rememberVectorPainter(Icons.Filled.Lock),
                    title = "Privacidad",
                    summary = "Tus fotos se guardan solo en este teléfono. Fototin se conecta a internet " +
                        "solo para traer las que elijas de Google Fotos y para buscar actualizaciones.",
                )
            }
        }
    }

    when (dialog) {
        SettingsDialog.INTERVAL -> ChoiceDialog(
            title = "Cambiar de foto",
            options = Settings.INTERVAL_OPTIONS,
            selected = interval,
            label = ::intervalLabel,
            onSelect = onInterval,
            onDismiss = { dialog = null },
        )
        SettingsDialog.GRID -> ChoiceDialog(
            title = "Tamaño en la grilla",
            options = GRID_LABELS.indices.toList(),
            selected = gridSize,
            label = { GRID_LABELS[it] },
            onSelect = onGridSize,
            onDismiss = { dialog = null },
        )
        SettingsDialog.GOOGLE_PHOTOS -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Traer de Google Fotos") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "• Agregar fotos → Desde Google Fotos: elegís las fotos, tocás «Listo» y volvés a " +
                            "Fototin. Solo puede ver las que elijas.",
                    )
                    Text("• O desde Google Fotos: elegí fotos y tocá Compartir → Fototin.")
                    Text(
                        "• ¿Un álbum entero? Abrilo en Google Fotos, mantené presionada la primera foto y " +
                            "arrastrá para marcar todas. Al traerlas, tocá «Guardar como álbum».",
                    )
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Entendido") } },
        )
        null -> Unit
    }
}

/** Un grupo de opciones con su título, en una tarjeta. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 4.dp)) { content() }
        }
    }
}

/** Un renglón de ajuste: ícono, nombre, valor o explicación, y algo opcional a la derecha. */
@Composable
private fun SettingRow(
    icon: Painter,
    title: String,
    summary: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        trailing()
    }
}

/** Un renglón con interruptor: tocar cualquier parte del renglón lo cambia. */
@Composable
private fun SwitchRow(icon: Painter, title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Progress() {
    CircularProgressIndicator(Modifier.padding(start = 12.dp).size(22.dp), strokeWidth = 3.dp)
}

/** Diálogo con una lista de opciones: elegir una la aplica y cierra. */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == selected, role = Role.RadioButton) {
                                onSelect(option)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private const val RELEASES_URL = "https://github.com/mnavarrosqa/fototin/releases"

private val GRID_LABELS = listOf("Fotos chicas", "Fotos medianas", "Fotos grandes")

private fun intervalLabel(minutes: Int): String = when {
    minutes == 0 -> "Solo al tocar el widget"
    minutes < 60 -> "Cada $minutes minutos"
    minutes == 60 -> "Cada 1 hora"
    minutes < 1440 -> "Cada ${minutes / 60} horas"
    else -> "Una vez por día"
}
