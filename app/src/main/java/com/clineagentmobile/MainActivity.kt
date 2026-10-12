package com.clineagentmobile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("MainActivity", "onResume: app resumed")
    }

    override fun onPause() {
        super.onPause()
        android.util.Log.d("MainActivity", "onPause: app paused")
    }
}
