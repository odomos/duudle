package com.example.duudle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LavenderBackground = Color(0xFFD9C9F0)
private val YellowAccent = Color(0xFFFFC72C)

@Composable
fun WelcomeScreen(
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onGetStarted: (String) -> Unit
) {
    var username by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize().background(LavenderBackground)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🖍", fontSize = 64.sp)
            Spacer(Modifier.height(16.dp))
            Text("Welcome to Duudle", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Pick a username so your friends can find you", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                placeholder = { Text("e.g. arnav123") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.8f)
            )
            Spacer(Modifier.height(16.dp))
            if (errorMessage != null) {
                Text(errorMessage, color = Color.Red, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = { if (username.isNotBlank()) onGetStarted(username.trim()) },
                enabled = !isLoading && username.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = YellowAccent, contentColor = Color.Black)
            ) {
                Text(if (isLoading) "Setting up..." else "Get Started", fontWeight = FontWeight.Bold)
            }
        }
    }
}