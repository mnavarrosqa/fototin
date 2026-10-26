package app.fototin.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.map

/** Pantalla ancha (foldable desplegado, tablet o teléfono acostado): 600dp o más. */
@Composable
fun isWideScreen(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

/**
 * La bisagra o el pliegue de la pantalla, si hay. Se actualiza solo al plegar, desplegar o cambiar
 * la postura (por ejemplo, al apoyar el teléfono medio plegado).
 */
@Composable
fun rememberFoldingFeature(): FoldingFeature? {
    val activity = LocalContext.current.findActivity() ?: return null
    val features = remember(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity)
            .map { info -> info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull() }
    }
    val feature by features.collectAsState(initial = null)
    return feature
}

/** Modo mesa: medio plegado con la bisagra horizontal, apoyado como una notebook. */
val FoldingFeature.isTabletop: Boolean
    get() = state == FoldingFeature.State.HALF_OPENED && orientation == FoldingFeature.Orientation.HORIZONTAL

/** Modo libro: bisagra vertical que separa la pantalla en dos mitades. */
val FoldingFeature.isBook: Boolean
    get() = orientation == FoldingFeature.Orientation.VERTICAL && isSeparating

/** Posición de la bisagra en dp: comienzo y fin (x si es vertical, y si es horizontal). */
@Composable
fun FoldingFeature.hingeDp(): Pair<Dp, Dp> {
    val density = LocalDensity.current
    return with(density) {
        if (orientation == FoldingFeature.Orientation.VERTICAL) {
            bounds.left.toDp() to bounds.right.toDp()
        } else {
            bounds.top.toDp() to bounds.bottom.toDp()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
