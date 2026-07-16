package io.s2qtech.shenk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PackageZeroApp()
        }
    }
}

@Composable
fun PackageZeroApp() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF7F9F6),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 56.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "身刻",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF183229),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "原生 Android 基座",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFF4E665C),
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    HorizontalDivider(color = Color(0xFFD5DED8))
                    FoundationLine("领域模型", "就绪")
                    FoundationLine("本地同步边界", "就绪")
                    FoundationLine("计时引擎接口", "就绪")
                }

                Text(
                    text = "Package 0 · 仅用于验证原生工程",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF786B45),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF3D2))
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun FoundationLine(label: String, status: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color(0xFF283C34))
        Text(status, color = Color(0xFF4F7A61), fontWeight = FontWeight.Medium)
    }
    HorizontalDivider(color = Color(0xFFE2E8E4))
}

@Preview(showBackground = true)
@Composable
private fun PackageZeroPreview() {
    PackageZeroApp()
}
