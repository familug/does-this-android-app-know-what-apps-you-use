package com.appquery.checker

import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppQueryCheckerTheme {
                AppQueryCheckerScreen()
            }
        }
    }
}

@Composable
fun AppQueryCheckerTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = Color(0xFFBB86FC),
        secondary = Color(0xFF03DAC5),
        background = Color(0xFF121212),
        surface = Color(0xFF1E1E1E),
        onPrimary = Color.Black,
        onSecondary = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White,
        error = Color(0xFFCF6679)
    )
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppQueryCheckerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<AppQueryInfo>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(0f) }
    var progressText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf(FilterMode.ALL) }
    var hasScanned by remember { mutableStateOf(false) }

    val filteredApps = remember(apps, searchQuery, filterMode) {
        apps.filter { app ->
            val matchesSearch = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (filterMode) {
                FilterMode.ALL -> true
                FilterMode.HIGH_RISK -> app.riskLevel == RiskLevel.HIGH
                FilterMode.MEDIUM_RISK -> app.riskLevel == RiskLevel.MEDIUM
                FilterMode.HAS_QUERIES -> app.riskLevel != RiskLevel.LOW
            }
            matchesSearch && matchesFilter
        }
    }

    val stats = remember(apps) {
        if (apps.isEmpty()) null
        else Stats(
            total = apps.size,
            high = apps.count { it.riskLevel == RiskLevel.HIGH },
            medium = apps.count { it.riskLevel == RiskLevel.MEDIUM },
            low = apps.count { it.riskLevel == RiskLevel.LOW }
        )
    }

    LaunchedEffect(Unit) {
        isScanning = true
        scope.launch {
            val scanner = AppScanner(context)
            val result = withContext(Dispatchers.IO) {
                scanner.scanAllApps { current, total ->
                    scanProgress = current.toFloat() / total
                    progressText = "$current / $total"
                }
            }
            apps = result
            isScanning = false
            hasScanned = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("App Query Checker", fontWeight = FontWeight.Bold)
                        if (stats != null) {
                            Text(
                                "${stats.total} apps scanned",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isScanning) {
                ScanningIndicator(scanProgress, progressText)
            }

            if (hasScanned && stats != null) {
                StatsBar(stats)
                SearchAndFilter(
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    filterMode = filterMode,
                    onFilterChange = { filterMode = it }
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        AppCard(app)
                    }
                    if (filteredApps.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No apps match the filter", color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScanningIndicator(progress: Float, text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Scanning installed apps...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

data class Stats(val total: Int, val high: Int, val medium: Int, val low: Int)

@Composable
fun StatsBar(stats: Stats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatChip(
            label = "Sees all apps",
            count = stats.high,
            color = Color(0xFFE53935),
            modifier = Modifier.weight(1f)
        )
        StatChip(
            label = "Queries specific",
            count = stats.medium,
            color = Color(0xFFFFA726),
            modifier = Modifier.weight(1f)
        )
        StatChip(
            label = "No queries",
            count = stats.low,
            color = Color(0xFF66BB6A),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun StatChip(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                count.toString(),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = color
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.8f)
            )
        }
    }
}

enum class FilterMode(val label: String) {
    ALL("All"),
    HIGH_RISK("Sees all"),
    MEDIUM_RISK("Specific"),
    HAS_QUERIES("Any queries")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAndFilter(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    filterMode: FilterMode,
    onFilterChange: (FilterMode) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search apps...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterMode.entries.forEach { mode ->
                FilterChip(
                    selected = filterMode == mode,
                    onClick = { onFilterChange(mode) },
                    label = { Text(mode.label, fontSize = 12.sp) }
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
fun AppCard(app: AppQueryInfo) {
    var expanded by remember { mutableStateOf(false) }

    val riskColor = when (app.riskLevel) {
        RiskLevel.HIGH -> Color(0xFFE53935)
        RiskLevel.MEDIUM -> Color(0xFFFFA726)
        RiskLevel.LOW -> Color(0xFF66BB6A)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (app.riskLevel != RiskLevel.LOW) expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // App icon
                AppIcon(app.icon)

                Spacer(modifier = Modifier.width(12.dp))

                // App name and package
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        app.appName,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Risk badge
                RiskBadge(app, riskColor)

                if (app.riskLevel != RiskLevel.LOW) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color.Gray
                    )
                }
            }

            // Risk summary line
            if (app.canSeeAllApps || app.queriesPackages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (app.canSeeAllApps) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = riskColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            buildString {
                                if (app.queriesIntentActions.contains("android.intent.action.MAIN"))
                                    append("Can see ALL installed apps (ACTION_MAIN)")
                                if (app.hasQueryAllPackages) {
                                    if (isNotEmpty()) append(" + ")
                                    append("QUERY_ALL_PACKAGES")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = riskColor,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = null,
                            tint = riskColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Queries ${app.queriesPackages.size} specific app(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = riskColor
                        )
                    }
                }
            }

            // Expanded details
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    if (app.queriesIntentActions.isNotEmpty()) {
                        Text(
                            "Intent actions queried:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        app.queriesIntentActions.forEach { action ->
                            Text(
                                "  $action",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (action == "android.intent.action.MAIN") riskColor else Color.Gray
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    if (app.queriesPackages.isNotEmpty()) {
                        Text(
                            "Specific packages queried (${app.queriesPackages.size}):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        app.queriesPackages.forEach { pkg ->
                            Text(
                                "  $pkg",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppIcon(icon: Drawable?) {
    if (icon != null) {
        Image(
            bitmap = icon.toBitmap(48, 48).asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Gray.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Color.Gray)
        }
    }
}

@Composable
fun RiskBadge(app: AppQueryInfo, color: Color) {
    val text = when (app.riskLevel) {
        RiskLevel.HIGH -> "SEES ALL"
        RiskLevel.MEDIUM -> "${app.queriesPackages.size} apps"
        RiskLevel.LOW -> "NONE"
    }
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
