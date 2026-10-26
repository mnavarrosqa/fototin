package app.fototin.ui

import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.fototin.data.Settings

/** Pestaña Ajustes: rotación, copia de seguridad y ayuda. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsTab(
    padding: PaddingValues,
    interval: Int,
    shuffle: Boolean,
    backupBusy: Boolean,
    photoCount: Int,
    dynamicColor: Boolean,
    onInterval: (Int) -> Unit,
    onShuffle: (Boolean) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
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
            SettingsCard("Cambiar de foto") {
                Text(
                    "Cada cuánto cambian solos todos los widgets. Tocarlos siempre pasa a la siguiente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                ) {
                    Settings.INTERVAL_OPTIONS.forEach { option ->
                        FilterChip(
                            selected = option == interval,
                            onClick = { onInterval(option) },
                            label = { Text(intervalLabel(option)) },
                            leadingIcon = if (option == interval) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else {
                                null
                            },
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp, horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Orden aleatorio", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Sin repetir hasta mostrar todas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = shuffle, onCheckedChange = onShuffle)
                }
            }
        }
        // Material You existe desde Android 12.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item {
                SettingsCard("Apariencia") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Colores del fondo de pantalla", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "La app toma los colores de tu fondo en lugar de los tonos terracota",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = dynamicColor, onCheckedChange = onDynamicColor)
                    }
                }
            }
        }
        item {
            SettingsCard("Copia de seguridad") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "Guardá tus $photoCount fotos con sus notas, favoritas y álbumes en un archivo. " +
                            "Restauralo en otro teléfono o después de reinstalar: se suma a lo que haya, sin repetir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (backupBusy) {
                        CircularProgressIndicator(Modifier.padding(start = 12.dp).size(24.dp), strokeWidth = 3.dp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                // FlowRow: con letra grande, el segundo botón baja a otra línea en vez de aplastarse.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    FilledTonalButton(onClick = onBackup, enabled = !backupBusy && photoCount > 0) { Text("Guardar copia") }
                    OutlinedButton(onClick = onRestore, enabled = !backupBusy) { Text("Restaurar") }
                }
            }
        }
        item {
            SettingsCard("Fotos de Google Fotos") {
                Text(
                    "• En «Agregar fotos», el selector muestra también tus fotos de Google Fotos si está " +
                        "activado. Si no aparecen, buscá en los ajustes del teléfono «Selector de fotos» o " +
                        "«Contenido multimedia en la nube» y elegí Google Fotos.\n" +
                        "• O abrí Google Fotos, elegí una o varias fotos y tocá Compartir → Fototin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        item {
            val context = LocalContext.current
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
            }
            Text(
                "Fototin ${version ?: ""}\nTus fotos se guardan solo en este teléfono.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

private fun intervalLabel(minutes: Int): String = when {
    minutes == 0 -> "Solo al tocar"
    minutes < 60 -> "$minutes min"
    minutes < 1440 -> "${minutes / 60} h"
    else -> "1 día"
}
