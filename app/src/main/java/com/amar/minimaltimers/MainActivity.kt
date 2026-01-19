package com.amar.minimaltimers
import com.amar.minimaltimers.ui.*
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
            when (vm.screen) {
            Screen.LIST -> ListScreen(vm)
            Screen.EDIT -> EditScreen(vm)
            Screen.RUN  -> RunScreen(vm)
            }
        }
    }
}
