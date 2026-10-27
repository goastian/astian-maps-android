package org.astian.suite.maps.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.astian.suite.maps.R
import org.astian.suite.maps.ui.QuickCategories
import org.astian.suite.maps.ui.AstianMenu
import org.astian.suite.maps.ui.dpadHighlight
import org.astian.suite.maps.ui.icons.Sym
import org.astian.suite.maps.ui.item

/**
 * The last chip on the map's category row (issue #779): a menu of every chip in the user's
 * order, the ones switched off included, so a hidden category is still one tap away. A pick
 * runs that search like the chip would.
 */
@Composable
internal fun MoreCategoriesChip(onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ElevatedAssistChip(
            onClick = { open = true },
            modifier = Modifier.dpadHighlight(RoundedCornerShape(8.dp)),
            label = { Text(stringResource(R.string.cat_more)) },
            leadingIcon = { Icon(Sym.MoreHoriz, contentDescription = null, modifier = Modifier.size(18.dp)) },
            shape = CircleShape,
            colors = AssistChipDefaults.elevatedAssistChipColors(leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        )
        AstianMenu(expanded = open, onDismissRequest = { open = false }) {
            QuickCategories.arranged().forEach { chip ->
                item(stringResource(chip.label), chip.icon) { open = false; onPick(chip.query) }
            }
        }
    }
}
