package com.example.duudle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LavenderBackground = Color(0xFFD9C9F0)
private val YellowAccent = Color(0xFFFFC72C)
private val PinkAlert = Color(0xFFFF6F91)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onDesignDoodleClick: () -> Unit = {},
    onFriendsClick: () -> Unit = {},
    onOverlayDebugClick: () -> Unit = {},
    newDoodleFromUsername: String? = null,
    onViewDoodle: () -> Unit = {}
) {
    Box(modifier = modifier.fillMaxSize().background(LavenderBackground)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.padding(top = 32.dp).size(140.dp).background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(text = "🙂", fontSize = 56.sp) }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (newDoodleFromUsername != null) {
                    Button(
                        onClick = onViewDoodle,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PinkAlert, contentColor = Color.White)
                    ) {
                        Text("📬 New doodle from $newDoodleFromUsername!", fontWeight = FontWeight.Bold)
                    }
                }
                HomeButton(text = "Friends", onClick = onFriendsClick)
                HomeButton(text = "Design Your Doodle", onClick = onDesignDoodleClick)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HomeButton(text = "About") { /* TODO */ }
                    HomeButton(text = "How to Use") { /* TODO */ }
                }
                TextButton(onClick = onOverlayDebugClick) {
                    Text("🔧 Overlay Debug Tools", color = Color.DarkGray)
                }
            }
        }
    }
}

@Composable
private fun HomeButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = YellowAccent, contentColor = Color.Black)
    ) { Text(text = text, fontWeight = FontWeight.Bold) }
}