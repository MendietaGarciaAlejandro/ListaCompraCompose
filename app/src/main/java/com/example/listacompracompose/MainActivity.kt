// MainActivity.kt
package com.example.listacompracompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.listacompracompose.ui.theme.ListaCompraComposeTheme

class MainActivity : ComponentActivity() {
    private val vm: CompraViewModel by viewModels { CompraViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ListaCompraComposeTheme {
                CompraApp(vm)
            }
        }
    }
}
