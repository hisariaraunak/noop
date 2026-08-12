package com.noop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.noop.ui.designsystem.NoopSpacing
import com.noop.ui.designsystem.NoopType

@Composable
internal fun RebuildSectionScreen(
    title: String,
    description: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NoopSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(NoopSpacing.md, Alignment.CenterVertically),
    ) {
        Text(title, style = NoopType.editorialHeadline)
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
