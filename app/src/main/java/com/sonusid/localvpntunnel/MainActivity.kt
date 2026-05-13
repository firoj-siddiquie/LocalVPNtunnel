package com.sonusid.localvpntunnel

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonusid.localvpntunnel.ui.theme.LocalVPNTunnelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocalVPNTunnelTheme {
                VpnScreen()
            }
        }
    }

    @Composable
    fun VpnScreen(
        initialState: Boolean = false,
        onToggle: (Boolean) -> Unit = {}
    ) {
        var isRunning by remember { mutableStateOf(initialState) }
        
        val prepareVpnLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                startVpnService()
                isRunning = true
                onToggle(true)
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "LocalVpnTunnel",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = (-1).sp
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                StatusCard(isRunning = isRunning)
                
                StatsRow(isRunning = isRunning)

                ConnectionDetails(isRunning = isRunning)

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        if (isRunning) {
                            stopVpnService()
                            isRunning = false
                            onToggle(false)
                        } else {
                            val intent = VpnService.prepare(this@MainActivity)
                            if (intent != null) {
                                prepareVpnLauncher.launch(intent)
                            } else {
                                startVpnService()
                                isRunning = true
                                onToggle(true)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = if (isRunning) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = if (isRunning) "Disconnect" else "Establish Tunnel",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                
                Text(
                    text = "TCP sandbox traffic is captured at 10.0.0.2",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    }

    @Composable
    fun StatusCard(isRunning: Boolean) {
        val containerColor by animateColorAsState(
            if (isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "containerColor"
        )
        
        val contentColor by animateColorAsState(
            if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            label = "contentColor"
        )

        val scale by animateFloatAsState(
            if (isRunning) 1.02f else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "scale"
        )

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.elevatedCardColors(containerColor = containerColor)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = if (isRunning) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isRunning) "TUNNEL ACTIVE" else "TUNNEL READY",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = if (isRunning) MaterialTheme.colorScheme.primary else contentColor,
                    letterSpacing = 2.sp
                )
                Text(
                    text = if (isRunning) "Routing traffic to sandbox" else "Awaiting secure handshake",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }

    @Composable
    fun StatsRow(isRunning: Boolean) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatItem(
                label = "Download",
                value = if (isRunning) "124 KB/s" else "0 KB/s",
                icon = Icons.Default.ArrowDownward,
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "Upload",
                value = if (isRunning) "12 KB/s" else "0 KB/s",
                icon = Icons.Default.ArrowUpward,
                modifier = Modifier.weight(1f)
            )
        }
    }

    @Composable
    fun StatItem(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
        ElevatedCard(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @Composable
    fun ConnectionDetails(isRunning: Boolean) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                DetailItem(icon = Icons.Default.Router, label = "Gateway", value = "10.0.0.2")
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                DetailItem(icon = Icons.Default.Dns, label = "DNS Server", value = "8.8.8.8")
            }
        }
    }

    @Composable
    fun DetailItem(icon: ImageVector, label: String, value: String) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, LocalVpnService::class.java)
        startService(intent)
    }

    private fun stopVpnService() {
        val intent = Intent(this, LocalVpnService::class.java).apply {
            action = LocalVpnService.ACTION_STOP
        }
        startService(intent)
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Composable
fun VpnPreviewLight() {
    LocalVPNTunnelTheme {
        MainActivity().VpnScreen(initialState = false)
    }
}

@Preview(showBackground = true, name = "Connected State")
@Composable
fun VpnPreviewConnected() {
    LocalVPNTunnelTheme {
        MainActivity().VpnScreen(initialState = true)
    }
}
