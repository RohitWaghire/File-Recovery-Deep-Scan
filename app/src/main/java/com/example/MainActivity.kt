package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.RecoveryDatabase
import com.example.data.RecoveryRepository
import com.example.ui.screens.MainDashboard
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.RecoveryViewModel
import com.example.viewmodel.RecoveryViewModelFactory

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    // Initialize Room components asynchronously/lazily via lifecycle viewmodel factory
    val database = RecoveryDatabase.getDatabase(this)
    val repository = RecoveryRepository(database.recoveryDao())
    val factory = RecoveryViewModelFactory(repository)

    setContent {
      MyApplicationTheme {
        val viewModel: RecoveryViewModel = viewModel(factory = factory)
        MainDashboard(
          viewModel = viewModel,
          modifier = Modifier.fillMaxSize()
        )
      }
    }
  }
}

