package com.randaladams.cakeday

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.randaladams.cakeday.notify.Notifier
import com.randaladams.cakeday.ui.AppRoot
import com.randaladams.cakeday.ui.CakedayTheme
import com.randaladams.cakeday.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.createChannel(this)
        setContent {
            CakedayTheme { AppRoot(vm) }
        }
    }
}
