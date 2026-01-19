package com.amar.minimaltimers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amar.minimaltimers.util.secToHm
import com.amar.minimaltimers.util.sumSec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(vm: MainViewModel) {
    val plans = vm.plans.sortedByDescending { it.updatedAt }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Minimal Timers") },
                actions = {
                    TextButton(onClick = { vm.newPlan() }) { Text("+ New") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (plans.isEmpty()) {
                Text("No timers yet.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.newPlan() }) { Text("Create your first timer") }
                return@Column
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    items = plans,
                    key = { it.id } // ✅ stabilno
                ) { p ->
                    val total = sumSec(p.activities)
                    val hasActs = p.activities.isNotEmpty()
                    val title = p.title.trim().ifBlank { "Untitled" }

                    val actsCount = p.activities.size
                    val actsLabel = if (actsCount == 1) "activity" else "activities"

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { vm.openEdit(p.id) } // ✅ mobilno: tap otvara Edit
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            Text("$actsCount $actsLabel • total ${secToHm(total)}", style = MaterialTheme.typography.bodyMedium)

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { vm.openRun(p.id) },
                                    enabled = hasActs
                                ) { Text("Start") }

                                OutlinedButton(onClick = { vm.openEdit(p.id) }) { Text("Edit") }
                            }
                        }
                    }
                }
            }
        }
    }
}
