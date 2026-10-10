package io.github.vontk.tally

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras

class TallyApplication : Application() {
    val repository by lazy { TallyRepository(this) }
}

class MainActivity : ComponentActivity() {
    private val model: TallyViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                TallyViewModel(
                    (application as TallyApplication).repository,
                    extras.createSavedStateHandle(),
                )
                    as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TallyApp(model) { light ->
                enableEdgeToEdge(
                    statusBarStyle =
                        if (light)
                            SystemBarStyle.light(
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                            )
                        else SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                    navigationBarStyle =
                        if (light)
                            SystemBarStyle.light(
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                            )
                        else SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.refreshClock()
    }
}
