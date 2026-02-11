package com.amar.minimaltimers.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.amar.minimaltimers.data.TimerActivity
import com.amar.minimaltimers.util.secToHms
import com.amar.minimaltimers.util.sumSec
import kotlinx.coroutines.launch

@Composable
fun TimeColumn(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var text    by remember(value) { mutableStateOf(value.toString().padStart(2, '0')) }

    fun commit() {
        val v = text.trim().toIntOrNull()?.coerceIn(range) ?: 0
        onChange(v)
        editing = false
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.6f), letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))

        if (editing) {
            OutlinedTextField(
                value = text,
                onValueChange = { s ->
                    if (s.length <= 2 && s.all { it.isDigit() }) {
                        text = s
                        if (s.length == 2) {
                            val v = s.toIntOrNull()?.coerceIn(range) ?: 0
                            onChange(v)
                            editing = false
                        }
                    }
                },
                modifier = Modifier.width(72.dp),
                textStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, textAlign = TextAlign.Center),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                    cursorColor = Color.White,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            )
        } else {
            Box(
                Modifier.width(72.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.22f))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .clickable { text = ""; editing = true }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(value.toString().padStart(2, '0'), fontSize = 32.sp,
                    fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun TimePicker(hours: Int, minutes: Int, seconds: Int,
               onH: (Int) -> Unit, onM: (Int) -> Unit, onS: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        TimeColumn("HH", hours,   0..99, onH)
        Text(":", fontSize = 32.sp, fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(horizontal = 8.dp))
        TimeColumn("MM", minutes, 0..59, onM)
        Text(":", fontSize = 32.sp, fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(horizontal = 8.dp))
        TimeColumn("SS", seconds, 0..59, onS)
    }
}

@Composable
fun ConfirmDialog(title: String, message: String,
                  confirmLabel: String = "Delete", confirmColor: Color = Color(0xFFEF5350),
                  onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text  = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = confirmColor, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Color(0xFF2D2D3A), titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.8f)
    )
}

@Composable
fun EditActivityDialog(activity: TimerActivity, onSave: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var name    by remember { mutableStateOf(activity.title) }
    var hours   by remember { mutableIntStateOf(activity.durationSec / 3600) }
    var minutes by remember { mutableIntStateOf((activity.durationSec % 3600) / 60) }
    var seconds by remember { mutableIntStateOf(activity.durationSec % 60) }

    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF667eea), Color(0xFF764ba2))))
            .padding(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Edit Activity", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)

                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Name", color = Color.White.copy(alpha = 0.7f)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                        cursorColor = Color.White))

                Text("DURATION", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f), letterSpacing = 2.sp)

                TimePicker(hours, minutes, seconds,
                    onH = { hours = it }, onM = { minutes = it }, onS = { seconds = it })

                val sec = hours * 3600 + minutes * 60 + seconds
                if (sec > 0) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.1f)).padding(8.dp),
                        contentAlignment = Alignment.Center) {
                        Text(secToHms(sec), fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Medium)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) { Text("Cancel") }

                    Button(onClick = { if (name.isNotBlank() && sec > 0) onSave(name.trim(), sec) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        enabled = name.isNotBlank() && sec > 0
                    ) { Text("Save", color = Color(0xFF667eea), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(vm: MainViewModel) {
    val planId = vm.editingPlanId ?: run { vm.goList(); return }
    val plan   = vm.plans.firstOrNull { it.id == planId } ?: run { vm.goList(); return }

    var title    by remember { mutableStateOf(plan.title) }
    var actName  by remember { mutableStateOf("") }
    var hours    by remember { mutableIntStateOf(0) }
    var minutes  by remember { mutableIntStateOf(1) }
    var seconds  by remember { mutableIntStateOf(0) }
    var titleErr by remember { mutableStateOf<String?>(null) }

    var confirmDeleteTimer by remember { mutableStateOf(false) }
    var confirmDeleteActId by remember { mutableStateOf<String?>(null) }
    var editingActivity    by remember { mutableStateOf<TimerActivity?>(null) }
    var showUnsavedDialog  by remember { mutableStateOf(false) }

    LaunchedEffect(planId, plan.title) { title = plan.title }

    val total     = sumSec(plan.activities)
    val actsCount = plan.activities.size
    val snackHost = remember { SnackbarHostState() }
    val scope     = rememberCoroutineScope()
    val gradient  = Brush.verticalGradient(listOf(Color(0xFF667eea), Color(0xFF764ba2)))

    val hasUnsaved = title != plan.title || actName.isNotBlank()
    fun attemptLeave() { if (hasUnsaved) showUnsavedDialog = true else vm.goList() }
    BackHandler { attemptLeave() }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Unsaved changes", fontWeight = FontWeight.Bold) },
            text  = { Text("You have unsaved changes. Save before leaving?") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        if (vm.savePlanTitle(planId, title)) { showUnsavedDialog = false; vm.goList() }
                        else titleErr = "Please enter a name."
                    }) { Text("Save") }
                    TextButton(onClick = { showUnsavedDialog = false; vm.goList() }) {
                        Text("Discard", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showUnsavedDialog = false }) { Text("Cancel") } },
            containerColor = Color(0xFF2D2D3A), titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.8f)
        )
    }

    if (confirmDeleteTimer) {
        ConfirmDialog(
            title = "Delete Timer",
            message = "Delete \"${plan.title.ifBlank { "this timer" }}\"? Cannot be undone.",
            onConfirm = { vm.deletePlan(planId) },
            onDismiss = { confirmDeleteTimer = false }
        )
    }

    confirmDeleteActId?.let { actId ->
        val act = plan.activities.firstOrNull { it.id == actId }
        ConfirmDialog(
            title = "Delete Activity",
            message = "Delete \"${act?.title ?: "this activity"}\"?",
            onConfirm = { vm.removeActivity(planId, actId); confirmDeleteActId = null },
            onDismiss = { confirmDeleteActId = null }
        )
    }

    editingActivity?.let { act ->
        EditActivityDialog(
            activity  = act,
            onSave    = { name, sec -> vm.editActivity(planId, act.id, name, sec); editingActivity = null },
            onDismiss = { editingActivity = null }
        )
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    Box(Modifier.fillMaxSize().background(gradient)) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackHost) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(if (plan.draft) "Create Timer" else "Edit Timer",
                            fontWeight = FontWeight.Bold, color = Color.White)
                    },
                    navigationIcon = {
                        TextButton(onClick = { attemptLeave() }) {
                            Text("← Back", color = Color.White) }
                    },
                    actions = {
                        TextButton(onClick = {
                            if (vm.savePlanTitle(planId, title)) {
                                titleErr = null
                                scope.launch { snackHost.showSnackbar("Saved ✅") }
                            } else titleErr = "Please enter a name."
                        }) { Text("Save", color = Color.White, fontWeight = FontWeight.Bold) }

                        TextButton(onClick = { confirmDeleteTimer = true }) {
                            Text("Delete", color = Color(0xFFFF8A80)) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { pad ->
            LazyColumn(
                Modifier.padding(pad).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
            ) {

                // ── Timer Name ────────────────────────────────────────────────
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.15f)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("TIMER NAME", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f), letterSpacing = 2.sp)
                            OutlinedTextField(
                                value = title, onValueChange = { title = it; titleErr = null },
                                placeholder = { Text("Timer name", color = Color.White.copy(alpha = 0.4f)) },
                                modifier = Modifier.fillMaxWidth(),
                                isError = titleErr != null,
                                shape = RoundedCornerShape(12.dp), singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                                    cursorColor = Color.White)
                            )
                            if (titleErr != null)
                                Text(titleErr!!, fontSize = 12.sp, color = Color(0xFFFFCDD2))
                        }
                    }
                }

                // ── Add Activity ──────────────────────────────────────────────
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.15f)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("ADD ACTIVITY", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f), letterSpacing = 2.sp)

                            OutlinedTextField(
                                value = actName, onValueChange = { actName = it },
                                placeholder = { Text("Activity name", color = Color.White.copy(alpha = 0.4f)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp), singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                                    cursorColor = Color.White)
                            )

                            Text("DURATION", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f), letterSpacing = 2.sp)

                            TimePicker(hours, minutes, seconds,
                                onH = { hours = it }, onM = { minutes = it }, onS = { seconds = it })

                            val previewSec = hours * 3600 + minutes * 60 + seconds
                            if (previewSec > 0) {
                                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.1f)).padding(10.dp),
                                    contentAlignment = Alignment.Center) {
                                    Text(secToHms(previewSec), fontSize = 15.sp,
                                        color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Medium)
                                }
                            }

                            val canAdd = actName.isNotBlank() && previewSec > 0
                            Button(
                                onClick = {
                                    if (canAdd) {
                                        vm.addActivity(planId, actName.trim(), previewSec)
                                        actName = ""; hours = 0; minutes = 1; seconds = 0
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(25.dp), enabled = canAdd,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    disabledContainerColor = Color.White.copy(alpha = 0.25f))
                            ) {
                                Text("➕  Add Activity",
                                    color = if (canAdd) Color(0xFF667eea) else Color.White.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }

                // ── Activities header ─────────────────────────────────────────
                item {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("ACTIVITIES", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.7f), letterSpacing = 2.sp)
                        Text("$actsCount ${if (actsCount == 1) "activity" else "activities"} • ${secToHms(total)}",
                            fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }

                if (plan.activities.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f)).padding(28.dp),
                            contentAlignment = Alignment.Center) {
                            Text("No activities yet.\nAdd one above!",
                                color = Color.White.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center, fontSize = 14.sp, lineHeight = 22.sp)
                        }
                    }
                }

                // ── Activity rows ─────────────────────────────────────────────
                items(items = plan.activities, key = { it.id }) { act ->
                    val idx = plan.activities.indexOf(act) + 1
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center) {
                            Text("$idx", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(Modifier.weight(1f)) {
                            Text(act.title, fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text(secToHms(act.durationSec), fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.65f), fontWeight = FontWeight.Medium)
                        }

                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(17.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { editingActivity = act },
                            contentAlignment = Alignment.Center) {
                            Text("✎", fontSize = 15.sp, color = Color.White)
                        }

                        Spacer(Modifier.width(8.dp))

                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(17.dp))
                            .background(Color(0xFFEF5350).copy(alpha = 0.3f))
                            .clickable { confirmDeleteActId = act.id },
                            contentAlignment = Alignment.Center) {
                            Text("✕", fontSize = 14.sp, color = Color(0xFFFF8A80))
                        }
                    }
                }
            }
        }
    }
}
