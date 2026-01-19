package com.amar.minimaltimers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amar.minimaltimers.util.parseHhMm
import com.amar.minimaltimers.util.secToHm
import com.amar.minimaltimers.util.sumSec
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(vm: MainViewModel) {
    val planId = vm.editingPlanId ?: run { vm.goList(); return }
    val plan = vm.plans.firstOrNull { it.id == planId } ?: run { vm.goList(); return }

    var title by remember { mutableStateOf(plan.title) }
    var actName by remember { mutableStateOf("") }
    var actDur by remember { mutableStateOf("00:50") }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(planId, plan.title) { title = plan.title }

    val total = sumSec(plan.activities)
    val actsCount = plan.activities.size
    val actsLabel = if (actsCount == 1) "activity" else "activities"

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun doSave() {
        val ok = vm.savePlanTitle(planId, title)
        if (!ok) {
            err = "Please enter timer name."
        } else {
            err = null
            scope.launch { snackbarHostState.showSnackbar("Saved successfully ✅") }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (plan.draft) "Create timer" else "Edit timer") },
                navigationIcon = { TextButton(onClick = { vm.goList() }) { Text("Back") } },
                actions = { TextButton(onClick = { doSave() }) { Text("Save") } }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it; if (err != null) err = null },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text("Total ${secToHm(total)} • $actsCount $actsLabel")

            Button(
                onClick = { vm.deletePlan(planId) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (plan.draft) "Cancel" else "Delete timer") }

            Divider()

            Text("Add activity", style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = actName,
                onValueChange = { actName = it; if (err != null) err = null },
                label = { Text("Activity name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = actDur,
                onValueChange = { actDur = it; if (err != null) err = null },
                label = { Text("Duration (HH:MM)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    val nameTrim = actName.trim()
                    val dur = parseHhMm(actDur)
                    when {
                        nameTrim.isEmpty() -> err = "Activity name is required."
                        dur == null -> err = "Duration must be HH:MM (e.g. 00:50)."
                        else -> {
                            val ok = vm.addActivity(planId, nameTrim, dur)
                            err = if (!ok) "Could not add activity." else null
                            if (ok) {
                                actName = ""
                                scope.launch { snackbarHostState.showSnackbar("Activity added ✅") }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add") }

            Divider()

            Text("Activities", style = MaterialTheme.typography.titleSmall)

            if (plan.activities.isEmpty()) {
                Text("No activities yet.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(items = plan.activities, key = { it.id }) { a ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(a.title, style = MaterialTheme.typography.bodyLarge)
                                    Text(secToHm(a.durationSec), style = MaterialTheme.typography.bodyMedium)
                                }
                                TextButton(onClick = { vm.removeActivity(planId, a.id) }) { Text("Remove") }
                            }
                        }
                    }
                }
            }
        }
    }
}
