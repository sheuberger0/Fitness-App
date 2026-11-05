package com.sheuberger.fitness.ui.nutrition

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sheuberger.fitness.ui.AppViewModelProvider

/** Der Tab "Essen": Tagebuch eines Tages mit vier Mahlzeiten. */
@Composable
fun NutritionScreen(
    viewModel: NutritionViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold { innerPadding ->
        Column(Modifier.padding(innerPadding).padding(start = 20.dp, top = 20.dp)) {
            Text("Essen", style = MaterialTheme.typography.headlineLarge)
            Text(
                NutritionFormat.day(state.date, state.today),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
