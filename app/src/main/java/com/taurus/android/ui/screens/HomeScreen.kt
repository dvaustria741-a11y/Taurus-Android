package com.taurus.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taurus.android.ui.components.AdaptiveLiquidGlass
import com.taurus.android.ui.components.GlassScene

@Composable
fun HomeScreen() {
    GlassScene(
        modifier = Modifier.fillMaxSize(),
        background = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
                        ),
                    ),
            )
            // colour blobs so the blur behind the glass is visible
            Box(Modifier.offset(x = (-60).dp, y = 120.dp).size(240.dp).background(Color(0xFF7C4DFF).copy(alpha = 0.85f), CircleShape))
            Box(Modifier.align(Alignment.CenterEnd).offset(x = 70.dp, y = (-40).dp).size(220.dp).background(Color(0xFF00E5A8).copy(alpha = 0.80f), CircleShape))
            Box(Modifier.align(Alignment.BottomStart).offset(x = 40.dp, y = (-90).dp).size(200.dp).background(Color(0xFFFF6E40).copy(alpha = 0.80f), CircleShape))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AdaptiveLiquidGlass(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Taurus", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        "Liquid glass on Android.",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
            AdaptiveLiquidGlass(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                Text(
                    "Blur needs Android 12 (API 31). Older phones get a frosted fill.",
                    modifier = Modifier.padding(20.dp),
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}
