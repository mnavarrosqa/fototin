package app.fototin.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fototin.data.Settings
import coil.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onPickPhotos: () -> Unit, onAddWidget: () -> Unit) {
    val photos by viewModel.photos.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val interval by viewModel.interval.collectAsState()
    val shuffle by viewModel.shuffle.collectAsState()

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Fototin") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPickPhotos,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Agregar fotos") },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SettingsCard(
                    interval = interval,
                    shuffle = shuffle,
                    onInterval = viewModel::setInterval,
                    onShuffle = viewModel::setShuffle,
                    onAddWidget = onAddWidget,
                )
            }
            if (importing) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = if (photos.isEmpty()) {
                        "Todavía no elegiste fotos. Podés sumarlas desde tu galería o Google Fotos " +
                            "con el botón de abajo, o usando \"Compartir → Fototin\" en cualquier foto."
                    } else {
                        "${photos.size} fotos en tus widgets"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = if (photos.isEmpty()) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            items(photos, key = { it.name }) { file ->
                PhotoThumb(file, onRemove = { viewModel.remove(file) })
            }
        }
    }
}

@Composable
private fun SettingsCard(
    interval: Int,
    shuffle: Boolean,
    onInterval: (Int) -> Unit,
    onShuffle: (Boolean) -> Unit,
    onAddWidget: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Cambiar foto", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                Settings.INTERVAL_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = option == interval,
                        onClick = { onInterval(option) },
                        label = { Text(intervalLabel(option)) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Orden aleatorio", modifier = Modifier.weight(1f))
                Switch(checked = shuffle, onCheckedChange = onShuffle)
            }
            Text(
                "Tocá el widget para pasar a la siguiente foto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onAddWidget, modifier = Modifier.fillMaxWidth()) {
                Text("Agregar widget a la pantalla de inicio")
            }
        }
    }
}

@Composable
private fun PhotoThumb(file: File, onRemove: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        IconButton(
            onClick = onRemove,
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x88000000)),
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp).clip(CircleShape),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Quitar", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

private fun intervalLabel(minutes: Int): String = when {
    minutes == 0 -> "Solo al tocar"
    minutes < 60 -> "$minutes min"
    minutes < 1440 -> "${minutes / 60} h"
    else -> "1 día"
}
