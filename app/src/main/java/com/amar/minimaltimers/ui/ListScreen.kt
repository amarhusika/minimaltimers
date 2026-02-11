package com.amar.minimaltimers.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amar.minimaltimers.util.secToHms
import com.amar.minimaltimers.util.sumSec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(vm: MainViewModel) {
    val plans = vm.plans.sortedByDescending { it.updatedAt }
    
    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF667eea),
            Color(0xFF764ba2)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            "Minimal Timers",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ) 
                    },
                    actions = {
                        TextButton(onClick = { vm.newPlan() }) { 
                            Text("+ New", color = Color.White, fontWeight = FontWeight.SemiBold) 
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent
        ) { pad ->
            Column(
                Modifier
                    .padding(pad)
                    .padding(16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (plans.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("⏱️", fontSize = 64.sp)
                            Text(
                                "No timers yet",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "Create your first timer to get started",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { vm.newPlan() },
                                modifier = Modifier.fillMaxWidth(0.6f).height(56.dp),
                                shape = RoundedCornerShape(28.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                            ) { 
                                Text("Create Timer", color = Color(0xFF667eea), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    return@Column
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(items = plans, key = { it.id }) { p ->
                        val total = sumSec(p.activities)
                        val hasActs = p.activities.isNotEmpty()
                        val title = p.title.trim().ifBlank { "Untitled" }
                        val actsCount = p.activities.size
                        val actsLabel = if (actsCount == 1) "activity" else "activities"

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { vm.openEdit(p.id) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("$actsCount $actsLabel • ${secToHms(total)}", fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { vm.openRun(p.id) },
                                        enabled = hasActs,
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(24.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White,
                                            disabledContainerColor = Color.White.copy(alpha = 0.3f)
                                        )
                                    ) {
                                        Text("▶ Start", color = if (hasActs) Color(0xFF667eea) else Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.SemiBold)
                                    }
                                    OutlinedButton(
                                        onClick = { vm.openEdit(p.id) },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(24.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                    ) {
                                        Text("✎ Edit", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
