package com.app.surge.up

import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ===== Warna tema Surge Up (ubah di sini untuk ganti tampilan) =====
val CBg = Color(0xFF09090B)
val CPanel = Color(0xE60B0B10)
val CPanel2 = Color(0xFF14141A)
val CRed = Color(0xFFE5233A)
val CRedDark = Color(0xFF6E0E1C)
val CGold = Color(0xFFFFC933)
val CSoft = Color(0xFFA6A6B6)
val CLine = Color(0xFF34343E)
val CGreen = Color(0xFF3DDC84)

/** Jajar genjang miring seperti tombol di desain lobby. */
class SlantShape(private val skewDp: Float = 16f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = skewDp * density.density
        val p = Path()
        p.moveTo(s, 0f)
        p.lineTo(size.width, 0f)
        p.lineTo(size.width - s, size.height)
        p.lineTo(0f, size.height)
        p.close()
        return Outline.Generic(p)
    }
}

fun Modifier.press(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.94f else 1f, tween(90), label = "press")
    this.graphicsLayer {
        scaleX = sc
        scaleY = sc
    }.clickable(interactionSource = src, indication = null, enabled = enabled) { onClick() }
}

fun nameColor(id: String): Color {
    val p = listOf(Color(0xFF4FC3F7), Color(0xFFFFB74D), Color(0xFF81C784), Color(0xFFF06292), Color(0xFFBA68C8))
    return p[(id.hashCode() and 0x7fffffff) % p.size]
}

@Composable
fun RedButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp))
            .background(if (enabled) CRed else CRedDark)
            .press(enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 14.sp)
    }
}

@Composable
fun GoldSlantButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(SlantShape(14f))
            .background(CGold)
            .press { onClick() }
            .padding(horizontal = 22.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color(0xFF1A1205), fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 15.sp)
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF101015))
            .border(1.dp, CLine, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (value.isEmpty()) Text(hint, color = Color(0xFF6A6A78), fontSize = 14.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(CRed),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun Avatar(name: String, size: Int, color: Color = CRed, ava: String = "") {
    val bmp = remember(ava) { Avatars.decode(ava) }
    if (bmp != null) {
        Image(
            bitmap = bmp, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(CircleShape)
        )
    } else {
        Box(
            Modifier.size(size.dp).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (name.isEmpty()) "?" else name.substring(0, 1).uppercase(),
                color = Color.White, fontWeight = FontWeight.Black, fontSize = (size / 2).sp
            )
        }
    }
}
