package ai.storyteller.lightcraft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A message, as the reference's toasts: white 17 sp words on its blue, 4 dp corners, at least
 * 60 dp; its action in bold, then a faint rule and a close button.
 */
@Composable
fun LcToast(data: SnackbarData) {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .widthIn(max = 560.dp)
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Lc.primary)
            .padding(start = 20.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(data.visuals.message, color = Color.White, fontFamily = Inter, fontSize = 17.sp, modifier = Modifier.weight(1f, fill = false).padding(vertical = 6.dp))
        data.visuals.actionLabel?.let { label ->
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                color = Color.White,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button) { data.performAction() }.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = 0.2f)))
        Box(
            Modifier.size(40.dp).clickable(role = Role.Button) { data.dismiss() }.semantics { contentDescription = "Close" },
            contentAlignment = Alignment.Center,
        ) {
            MdIcon(MdIcon.Close, null, tint = Color.White, size = 18.dp)
        }
    }
}
