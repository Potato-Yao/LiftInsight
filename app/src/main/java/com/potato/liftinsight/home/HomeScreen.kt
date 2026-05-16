package com.potato.liftinsight.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.potato.liftinsight.R
import com.potato.liftinsight.common.MetricCard

@Composable
internal fun HomeScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = stringResource(R.string.home_welcome_back),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        MetricCard(
            title = stringResource(R.string.home_todays_task_label),
            subtitle = stringResource(R.string.home_welcome_back),
            highlighted = true,
            modifier = Modifier.fillMaxWidth(),
            leadingContent = {
                Icon(
                    imageVector = Icons.Rounded.Today,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            Text(
                text = stringResource(R.string.home_no_task_placeholder),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        MetricCard(
            title = stringResource(R.string.home_start_training_title),
            subtitle = stringResource(R.string.home_start_training_subtitle),
            modifier = Modifier.fillMaxWidth(),
            onClick = {},
            leadingContent = {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        ) {}

        MetricCard(
            title = stringResource(R.string.home_body_recovery_percentage),
            subtitle = stringResource(R.string.home_no_data_placeholder),
            modifier = Modifier.fillMaxWidth(),
            leadingContent = {
                Icon(
                    imageVector = Icons.Rounded.QueryStats,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        ) {}

        MetricCard(
            title = stringResource(R.string.home_todays_task_intensity),
            subtitle = stringResource(R.string.home_no_data_placeholder),
            modifier = Modifier.fillMaxWidth(),
            leadingContent = {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        ) {}
    }
}
