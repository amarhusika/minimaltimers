package com.amar.minimaltimers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amar.minimaltimers.util.secToHms
import com.amar.minimaltimers.util.secToHm
import com.amar.minimaltimers.util.sumSec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunScreen(vm: MainViewModel) {
    val planId = vm.runPlanId ?: run { vm.goList(); return }
    val plan = vm.plans.firstOrNull { it.id == planId } ?: run { vm.goList(); return }
    val acts = plan.activities
    if (acts.isEmpty()) { vm.goList(); return }

    val idx = vm.runIdx.coerceIn(0, acts.size - 1)
    val cur = acts[idx]
    val nxt = acts.getOrNull(idx + 1)

    val total = sumSec(acts)
    val before = sumSec(acts.take(idx))
    val curTotal = cur.durationSec
    val curElapsed = (curTotal - vm.remainingSec).coerceAtLeast(0)
    val elapsed = before + curElapsed
    val progress = if (total <= 0) 0f else (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(plan.title.ifBlank { "Untitled" }) },
                navigationIcon = {
                    TextButton(onClick = { vm.goList() }) { Text("Back") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("CURRENT", style = MaterialTheme.typography.labelMedium)
            Text(cur.title, style = MaterialTheme.typography.titleLarge)

            Text(secToHms(vm.remainingSec), style = MaterialTheme.typography.displaySmall)

            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Step ${idx + 1}/${acts.size}")
                Text("Total ${secToHm(total)}")
            }

            Text("NEXT", style = MaterialTheme.typography.labelMedium)
            Text(nxt?.let { "${it.title} • ${it.durationSec / 60}m" } ?: "Done")

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.startPause() }, modifier = Modifier.weight(1f)) {
                    Text(if (vm.running) "Pause" else "Start")
                }
                OutlinedButton(onClick = { vm.restart() }, modifier = Modifier.weight(1f)) {
                    Text("Restart")
                }
            }
        }
    }
}
