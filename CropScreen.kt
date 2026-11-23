package com.headsetconnector.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headsetconnector.util.ImageTools

@Composable
fun CropScreen(source: Bitmap, onConfirm: (String) -> Unit, onCancel: () -> Unit) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val viewportDp = config.screenWidthDp.dp
    val viewportPx = with(density) { viewportDp.toPx() }

    var rotation by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    val rotated = remember(source, rotation) { ImageTools.rotate(source, rotation) }
    val bitmap = remember(rotated) { rotated.asImageBitmap() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Spacer(Modifier.weight(1f))

        Box(
            Modifier
                .size(viewportDp)
                .align(Alignment.CenterHorizontally)
                .clipToBounds()
                .border(2.dp, Color.White.copy(alpha = 0.6f))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoomChange, _ ->
                        panX += pan.x
                        panY += pan.y
                        zoom = (zoom * zoomChange).coerceIn(1f, 5f)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = panX
                        translationY = panY
                    },
            )
        }

        Spacer(Modifier.weight(1f))

        Column(
            Modifier
                .fillMaxWidth()
                .background(Card)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Зум", color = TextPrimary, fontSize = 13.sp)
                Spacer(Modifier.size(10.dp))
                Slider(
                    value = zoom,
                    onValueChange = { zoom = it },
                    valueRange = 1f..5f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Accent2,
                        activeTrackColor = Accent2,
                        inactiveTrackColor = CardHi,
                    ),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(
                    text = "Отмена",
                    modifier = Modifier.weight(1f),
                    onClick = onCancel,
                )
                GhostButton(
                    text = "Повернуть",
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Refresh,
                    tint = Accent2,
                    onClick = { rotation = (rotation + 90) % 360 },
                )
                PrimaryButton(
                    text = "Готово",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val result = ImageTools.cropSquareBase64(source, rotation, zoom, panX, panY, viewportPx)
                        if (result != null) onConfirm(result) else onCancel()
                    },
                )
            }
        }
    }
}
