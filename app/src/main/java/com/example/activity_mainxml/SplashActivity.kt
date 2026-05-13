package com.example.activity_mainxml

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            // 💡 2초 대기 후 메인으로 이동하는 로직을 setContent 내부로 이동
            LaunchedEffect(Unit) {
                delay(2000)
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                finish()
            }
            
            SplashScreen()
        }
    }
}

@Composable
fun SplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.main_logo),
            contentDescription = "Rotating Logo",
            modifier = Modifier
                .size(250.dp)
                .rotate(rotation)
        )
    }
}
