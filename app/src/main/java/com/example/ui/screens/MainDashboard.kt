package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.RecoveryViewModel
import com.example.viewmodel.ScanState
import com.example.viewmodel.ScannedFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(
    viewModel: RecoveryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // States from view model
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val scanningPath by viewModel.currentScanningPath.collectAsStateWithLifecycle()
    val foundFiles by viewModel.foundFiles.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedFileIds.collectAsStateWithLifecycle()
    val totalBytesScanned by viewModel.totalBytesScanned.collectAsStateWithLifecycle()
    val totalRecoveredCount by viewModel.recoveredCount.collectAsStateWithLifecycle()
    val isRecovering by viewModel.isRecovering.collectAsStateWithLifecycle()
    
    val scanHistory by viewModel.scanHistory.collectAsStateWithLifecycle()
    val recoveredFilesLog by viewModel.recoveredFiles.collectAsStateWithLifecycle()

    var activeTab by remember { mutableIntStateOf(0) } // 0: Scanner, 1: Vault / History, 2: Forensic Guide
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, PHOTO, VIDEO, AUDIO, DOCUMENT
    
    var pendingScanCategory by remember { mutableStateOf<String?>(null) }

    // Permission launcher for storage
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            Toast.makeText(context, "Storage permissions authorized.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Sandboxed cluster scan enabled for safety.", Toast.LENGTH_LONG).show()
        }
        pendingScanCategory?.let { category ->
            viewModel.startScan(context, category)
            pendingScanCategory = null
        }
    }

    // Function to check and request permissions
    val checkAndStartScan: (String) -> Unit = { category ->
        val requiredPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
        
        val allGranted = requiredPerms.all {
            context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        
        if (allGranted) {
            viewModel.startScan(context, category)
        } else {
            pendingScanCategory = category
            permissionLauncher.launch(requiredPerms)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color(0xFFA8C7FA),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Recovery Pro",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE2E2E6),
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF111318),
                    titleContentColor = Color(0xFFE2E2E6)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF1B1D22),
                windowInsets = WindowInsets.navigationBars,
                modifier = Modifier.border(width = 0.5.dp, color = Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Scan") },
                    label = { Text("Scanner") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF111318),
                        selectedTextColor = Color(0xFFA8C7FA),
                        unselectedIconColor = Color(0xFF909094),
                        unselectedTextColor = Color(0xFF909094),
                        indicatorColor = Color(0xFFA8C7FA)
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { 
                        BadgedBox(badge = {
                            if (recoveredFilesLog.isNotEmpty()) {
                                Badge(containerColor = Color(0xFF10B981)) {
                                    Text(recoveredFilesLog.size.toString(), color = Color.White)
                                }
                            }
                        }) {
                            Icon(Icons.Default.List, contentDescription = "Vault")
                        }
                    },
                    label = { Text("Vault") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF111318),
                        selectedTextColor = Color(0xFFA8C7FA),
                        unselectedIconColor = Color(0xFF909094),
                        unselectedTextColor = Color(0xFF909094),
                        indicatorColor = Color(0xFFA8C7FA)
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Default.Info, contentDescription = "Guide") },
                    label = { Text("Forensic Info") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF111318),
                        selectedTextColor = Color(0xFFA8C7FA),
                        unselectedIconColor = Color(0xFF909094),
                        unselectedTextColor = Color(0xFF909094),
                        indicatorColor = Color(0xFFA8C7FA)
                    )
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            if (activeTab == 0 && (foundFiles.isNotEmpty() || scanState == ScanState.SCANNING || scanState == ScanState.FINISHED)) {
                Button(
                    onClick = {
                        viewModel.recoverSelectedFiles(context) { count ->
                            Toast.makeText(context, "Successfully restored $count files to Vault!", Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = selectedIds.isNotEmpty() && !isRecovering,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(56.dp)
                        .testTag("submit_recovery_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981),
                        disabledContainerColor = Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isRecovering) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Securing Files...", color = Color.White, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, tint = if (selectedIds.isNotEmpty()) Color.White else Color.Gray)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "RECOVER ${selectedIds.size} SELECTED FILES",
                            color = if (selectedIds.isNotEmpty()) Color.White else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFF111318),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            val parentFilteredFiles = remember(foundFiles, selectedFilter) {
                if (selectedFilter == "ALL") foundFiles
                else foundFiles.filter { it.type == selectedFilter }
            }
            when (activeTab) {
                0 -> ScannerScreen(
                    scanState = scanState,
                    scanningPath = scanningPath,
                    foundFiles = foundFiles,
                    selectedIds = selectedIds,
                    totalBytesScanned = totalBytesScanned,
                    isRecovering = isRecovering,
                    selectedFilter = selectedFilter,
                    onFilterChange = { selectedFilter = it },
                    onStartScan = { checkAndStartScan(it) },
                    onFileSelected = { viewModel.toggleFileSelection(it) },
                    onSelectAll = { viewModel.selectAllFiltered(parentFilteredFiles.map { it.id }.toSet()) },
                    onClearSelection = { viewModel.clearSelection() },
                    onRecover = {
                        viewModel.recoverSelectedFiles(context) { count ->
                            Toast.makeText(context, "Successfully restored $count files to Vault!", Toast.LENGTH_LONG).show()
                        }
                    },
                    onRecoverSingle = { scannedFile ->
                        viewModel.recoverSingleFile(context, scannedFile) {
                            Toast.makeText(context, "Successfully recovered ${scannedFile.name} to Vault!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCancelScan = { viewModel.cancelScan() },
                    getReadableSize = { viewModel.getReadableSize(it) }
                )
                1 -> VaultScreen(
                    recoveredFilesLog = recoveredFilesLog,
                    scanHistory = scanHistory,
                    onShareFile = { path ->
                        try {
                            val file = File(path).canonicalFile
                            val recoveredRoot = File(context.getExternalFilesDir(null), "RecoveredFiles").canonicalFile
                            if (!file.startsWith(recoveredRoot)) {
                                Toast.makeText(context, "Path outside vault", Toast.LENGTH_SHORT).show()
                                return@VaultScreen
                            }
                            if (file.exists()) {
                                val fileUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val extension = file.extension
                                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = mimeType
                                    putExtra(Intent.EXTRA_STREAM, fileUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Recovered File"))
                            } else {
                                Toast.makeText(context, "File path missing: Standard simulated record.", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDeleteFile = { id, path -> viewModel.deleteRecoveredState(id, path) },
                    onClearHistory = { viewModel.clearHistory() },
                    getReadableSize = { viewModel.getReadableSize(it) }
                )
                2 -> GuideScreen()
            }
        }
    }
}

@Composable
fun ScannerScreen(
    scanState: ScanState,
    scanningPath: String,
    foundFiles: List<ScannedFile>,
    selectedIds: Set<String>,
    totalBytesScanned: Long,
    isRecovering: Boolean,
    selectedFilter: String,
    onFilterChange: (String) -> Unit,
    onStartScan: (String) -> Unit,
    onFileSelected: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRecover: () -> Unit,
    onRecoverSingle: (ScannedFile) -> Unit,
    onCancelScan: () -> Unit,
    getReadableSize: (Long) -> String
) {
    val filteredFiles = remember(foundFiles, selectedFilter) {
        if (selectedFilter == "ALL") foundFiles
        else foundFiles.filter { it.type == selectedFilter }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Core Storage Dial Block
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D22)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "LOCAL STORAGE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF909094),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    StorageDial(totalBytesScanned = totalBytesScanned, getReadableSize = getReadableSize)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Our algorithm indexes standard storage directories to locate cached and unindexed file headers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF909094),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }

        // Active State Controller
        item {
            AnimatedContent(
                targetState = scanState,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "ScanStateAnimation"
            ) { targetState ->
                when (targetState) {
                    ScanState.IDLE, ScanState.CANCELLED -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Choose Scopes to Deep Scan",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFE2E2E6),
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                              ) {
                                ScanButton(
                                    label = "Photos",
                                    icon = Icons.Default.Favorite,
                                    color = Color(0xFFA8C7FA),
                                    modifier = Modifier.weight(1f),
                                    onClick = { onStartScan("PHOTOS") }
                                )
                                ScanButton(
                                    label = "Videos",
                                    icon = Icons.Default.PlayArrow,
                                    color = Color(0xFFF59E0B),
                                    modifier = Modifier.weight(1f),
                                    onClick = { onStartScan("VIDEOS") }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ScanButton(
                                    label = "Audios",
                                    icon = Icons.Default.PlayArrow,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.weight(1f),
                                    onClick = { onStartScan("AUDIOS") }
                                )
                                ScanButton(
                                    label = "Docs",
                                    icon = Icons.Default.Edit,
                                    color = Color(0xFFEC4899),
                                    modifier = Modifier.weight(1f),
                                    onClick = { onStartScan("DOCUMENTS") }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Outer decorative ring
                                Box(
                                    modifier = Modifier
                                        .size(230.dp)
                                        .border(BorderStroke(1.dp, Color(0xFFA8C7FA).copy(alpha = 0.1f)), CircleShape)
                                )
                                // Inner decorative ring
                                Box(
                                    modifier = Modifier
                                        .size(180.dp)
                                        .border(BorderStroke(1.dp, Color(0xFFA8C7FA).copy(alpha = 0.2f)), CircleShape)
                                )
                                // Central Dial action button
                                Button(
                                    onClick = { onStartScan("FULL") },
                                    modifier = Modifier
                                        .size(130.dp)
                                        .testTag("full_scan_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFD3E3FD),
                                        contentColor = Color(0xFF041E49)
                                    ),
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = Color(0xFF041E49),
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "DEEP SCAN",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            letterSpacing = 0.5.sp,
                                            color = Color(0xFF041E49)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    ScanState.SCANNING -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D22)),
                            border = BorderStroke(1.dp, Color(0xFFA8C7FA).copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFFA8C7FA),
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "SCANNING SYSTEM SECTORS...",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFA8C7FA)
                                        )
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFFA8C7FA),
                                    trackColor = Color(0xFF303338)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // Path
                                Text(
                                    text = scanningPath,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = Color(0xFF909094),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Found clusters: ${foundFiles.size} items | Scanned: ${getReadableSize(totalBytesScanned)}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                    color = Color(0xFF10B981)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onCancelScan,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF374151),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                ) {
                                    Text(
                                        text = "Cancel Scan",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                    ScanState.FINISHED -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFA8C7FA).copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, Color(0xFFA8C7FA)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFFA8C7FA), CircleShape),
                                    contentAlignment = Alignment.Center
                                   ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF111318))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "Scan Completed",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE2E2E6)
                                    )
                                    Text(
                                        text = "Discovered ${foundFiles.size} recoverable file signature blocks.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF909094)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Filters and recovery toolbar
        if (foundFiles.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Results Filter",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFFE2E2E6),
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("ALL", "PHOTO", "VIDEO", "AUDIO", "DOCUMENT").forEach { type ->
                            FilterChip(
                                selected = selectedFilter == type,
                                onClick = { onFilterChange(type) },
                                label = { Text(type, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFA8C7FA),
                                    selectedLabelColor = Color(0xFF111318),
                                    containerColor = Color(0xFF1B1D22),
                                    labelColor = Color(0xFF909094)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selectedFilter == type,
                                    borderColor = Color.White.copy(alpha = 0.05f),
                                    selectedBorderColor = Color(0xFFA8C7FA),
                                    borderWidth = 1.dp
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                    
                    // Selection tools
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Selected: ${selectedIds.size} / ${filteredFiles.size}",
                            color = Color(0xFFA8C7FA),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onSelectAll) {
                                Text("Select All", color = Color(0xFF10B981), fontSize = 12.sp)
                            }
                            TextButton(onClick = onClearSelection) {
                                Text("Clear", color = Color(0xFFEC4899), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (filteredFiles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No sector matches for filter: $selectedFilter",
                            color = Color(0xFF94A3B8),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(filteredFiles, key = { it.id }) { file ->
                    FileCard(
                        scannedFile = file,
                        isSelected = selectedIds.contains(file.id),
                        onSelect = { onFileSelected(file.id) },
                        getReadableSize = getReadableSize,
                        onRecoverSingle = { onRecoverSingle(file) }
                    )
                }
            }
        } else {
            if (scanState == ScanState.IDLE || scanState == ScanState.CANCELLED) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D22)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFA8C7FA),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Ready for Diagnostic Analysis",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE2E2E6)
                                )
                                Text(
                                    text = "Press deep scan to begin reconstructing indices. If standard paths are empty, active forensical logs will be simulated.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF909094)
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(84.dp)) }
    }
}

@Composable
fun ScanButton(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val subtext = when (label.uppercase()) {
        "PHOTOS" -> "JPG, PNG, RAW"
        "VIDEOS" -> "MP4, MOV, AVI"
        "AUDIOS" -> "MP3, WAV, AAC"
        else -> "PDF, TXT, DOCX"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF212429)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = modifier
            .height(72.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = label,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE2E2E6),
                    fontSize = 14.sp
                )
                Text(
                    text = subtext,
                    fontSize = 10.sp,
                    color = Color(0xFF909094)
                )
            }
        }
    }
}

@Composable
fun FileCard(
    scannedFile: ScannedFile,
    isSelected: Boolean,
    onSelect: () -> Unit,
    getReadableSize: (Long) -> String,
    onRecoverSingle: () -> Unit
) {
    val typeColor = when (scannedFile.type) {
        "PHOTO" -> Color(0xFFA8C7FA)
        "VIDEO" -> Color(0xFFF59E0B)
        "AUDIO" -> Color(0xFF10B981)
        else -> Color(0xFFEC4899)
    }

    val typeIcon = when (scannedFile.type) {
        "PHOTO" -> Icons.Default.Star
        "VIDEO" -> Icons.Default.PlayArrow
        "AUDIO" -> Icons.Default.Refresh
        else -> Icons.Default.Edit
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF111318) else Color(0xFF1B1D22)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) Color(0xFFA8C7FA) else Color.White.copy(alpha = 0.05f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("file_item_card_${scannedFile.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelect() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFFA8C7FA),
                    checkmarkColor = Color(0xFF111318)
                )
            )
            
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(typeColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(typeIcon, contentDescription = null, tint = typeColor)
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = scannedFile.name,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE2E2E6),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = getReadableSize(scannedFile.sizeBytes),
                        fontSize = 11.sp,
                        color = Color(0xFF909094),
                        fontFamily = FontFamily.Monospace
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = scannedFile.path,
                    fontSize = 10.sp,
                    color = Color(0xFF909094),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = scannedFile.magicHeader,
                        fontSize = 10.sp,
                        color = Color(0xFFA8C7FA),
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.51f)
                    )
                    
                    // Recoverability Pill and Recover Action
                    val pillBg = if (scannedFile.recoverability > 0.8f) Color(0xFF10B981).copy(alpha = 0.1f) else Color(0xFFF59E0B).copy(alpha = 0.1f)
                    val pillBorder = if (scannedFile.recoverability > 0.8f) Color(0xFF10B981) else Color(0xFFF59E0B)
                    val pillText = if (scannedFile.recoverability > 0.8f) "High Integrity" else "Risk of Overwrite"
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = pillBg),
                            border = BorderStroke(0.5.dp, pillBorder),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${(scannedFile.recoverability * 100).toInt()}% $pillText",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = pillBorder,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Button(
                            onClick = onRecoverSingle,
                            modifier = Modifier
                                .height(26.dp)
                                .testTag("recover_single_button_${scannedFile.name}"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981)
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Recover",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StorageDial(
    totalBytesScanned: Long,
    getReadableSize: (Long) -> String
) {
    val stats = remember(totalBytesScanned) {
        try {
            val path = android.os.Environment.getDataDirectory()
            val stat = android.os.StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            val totalBytes = totalBlocks * blockSize
            val freeBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - freeBytes
            val percentage = if (totalBytes > 0) ((usedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt().coerceIn(0, 100) else 0
            val usedReadable = if (totalBytes > 0) getReadableSize(usedBytes) else "0 GB"
            val totalReadable = if (totalBytes > 0) getReadableSize(totalBytes) else "0 GB"
            Triple(percentage, usedReadable, totalReadable)
        } catch (e: Exception) {
            Triple(0, "0 GB", "0 GB")
        }
    }
    val percentage = stats.first
    val usedReadable = stats.second
    val totalReadable = stats.third
    val sweepAngle = 270f * (percentage / 100f)

    Box(
        modifier = Modifier
            .size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw background stroke circle
            drawArc(
                color = Color(0xFF303338),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw progress circle
            drawArc(
                color = Color(0xFFA8C7FA),
                startAngle = 135f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "INDEXED",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF909094),
                letterSpacing = 1.sp
            )
            Text(
                text = getReadableSize(totalBytesScanned),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE2E2E6),
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "$percentage% Full",
                fontSize = 11.sp,
                color = Color(0xFFA8C7FA),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun VaultScreen(
    recoveredFilesLog: List<com.example.data.RecoveredFile>,
    scanHistory: List<com.example.data.ScanHistory>,
    onShareFile: (String) -> Unit,
    onDeleteFile: (Long, String) -> Unit,
    onClearHistory: () -> Unit,
    getReadableSize: (Long) -> String
) {
    var vaultSubTab by remember { mutableIntStateOf(0) } // 0: Recovered Vault, 1: Diagnostic History

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TabRow(
            selectedTabIndex = vaultSubTab,
            containerColor = Color.Transparent,
            contentColor = Color(0xFFA8C7FA),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[vaultSubTab]),
                    color = Color(0xFFA8C7FA)
                )
            },
            divider = {}
        ) {
            Tab(
                selected = vaultSubTab == 0,
                onClick = { vaultSubTab = 0 },
                text = { Text("Vault Records (${recoveredFilesLog.size})", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = vaultSubTab == 1,
                onClick = { vaultSubTab = 1 },
                text = { Text("Diagnostic Log", fontWeight = FontWeight.SemiBold) }
            )
        }

        if (vaultSubTab == 0) {
            if (recoveredFilesLog.isEmpty()) {
                EmptyVaultState()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(recoveredFilesLog) { log ->
                        VaultCard(
                            file = log,
                            onShare = { onShareFile(log.recoveredPath) },
                            onDelete = { onDeleteFile(log.id, log.recoveredPath) },
                            getReadableSize = getReadableSize
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Scan Chronology list", fontWeight = FontWeight.Bold, color = Color(0xFFE2E2E6))
                if (scanHistory.isNotEmpty()) {
                    TextButton(onClick = onClearHistory) {
                        Text("Clear Chronology", color = Color(0xFFEC4899), fontSize = 12.sp)
                    }
                }
            }

            if (scanHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No scans logged yet.", color = Color(0xFF909094))
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(scanHistory) { history ->
                        HistoryCard(history = history)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyVaultState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(Color(0xFF1B1D22), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.List,
                contentDescription = null,
                tint = Color(0xFF909094),
                modifier = Modifier.size(36.dp)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Secure Vault Empty",
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE2E2E6),
                fontSize = 16.sp
            )
            Text(
                "No reconstructed files stored yet. Run a Deep Scan and select clusters to recover on the scan screen.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF909094),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
fun VaultCard(
    file: com.example.data.RecoveredFile,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    getReadableSize: (Long) -> String
) {
    val typeColor = when (file.fileType) {
        "PHOTO" -> Color(0xFFA8C7FA)
        "VIDEO" -> Color(0xFFF59E0B)
        "AUDIO" -> Color(0xFF10B981)
        else -> Color(0xFFEC4899)
    }

    val typeIcon = when (file.fileType) {
        "PHOTO" -> Icons.Default.Star
        "VIDEO" -> Icons.Default.PlayArrow
        "AUDIO" -> Icons.Default.Refresh
        else -> Icons.Default.Edit
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D22)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(typeColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(typeIcon, contentDescription = null, tint = typeColor)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Recovered_" + file.fileName,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE2E2E6),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = getReadableSize(file.sizeBytes),
                    fontSize = 11.sp,
                    color = Color(0xFF909094),
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Path: ${file.recoveredPath}",
                    fontSize = 9.sp,
                    color = Color(0xFF909094),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                val date = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(file.timestamp))
                Text(
                    text = "Restored: $date",
                    fontSize = 10.sp,
                    color = Color(0xFF10B981)
                )
            }
            
            // Actions
            Row {
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color(0xFFA8C7FA))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEC4899))
                }
            }
        }
    }
}

@Composable
fun HistoryCard(history: com.example.data.ScanHistory) {
    val date = SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(history.timestamp))
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D22)),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFA8C7FA).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFA8C7FA))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Scan Type: ${history.scanType}",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE2E2E6),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Discovered: ${history.filesFound} indices",
                        fontSize = 11.sp,
                        color = Color(0xFF10B981)
                    )
                    if (history.filesRecovered > 0) {
                        Text(
                            text = "Secured: ${history.filesRecovered} files",
                            fontSize = 11.sp,
                            color = Color(0xFF34D399),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = date,
                    fontSize = 11.sp,
                    color = Color(0xFF909094)
                )
                Text(
                    text = "${history.durationMs} ms",
                    fontSize = 10.sp,
                    color = Color(0xFF909094),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun GuideScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "FORENSICS & RECOVERY DIRECTIVE",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFFA8C7FA),
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D22)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "1. How file recovery works on flash memory",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2E2E6),
                        fontSize = 13.sp
                    )
                    Text(
                        "On modern storage systems (NAND flash, SSDs), when a file is deleted, Android removes standard Index pointers in key directory nodes. The original raw binary bits of the file remain unlinked in underlying sector clusters until new files write over those blocks.",
                        color = Color(0xFF909094),
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D22)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "2. Our Deep Scan Signature Algorithm",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2E2E6),
                        fontSize = 13.sp
                    )
                    Text(
                        "Our deep scan traverser reads raw accessible binary streams on local folders. By parsing the exact hexadecimal byte sequences at block origins (magic numbers like 0xFFD8FF for JPEG and 0x89504E47 for PNG), we identify hidden, renamed, or orphaned files, restoring them immediately with accurate metadata bindings.",
                        color = Color(0xFF909094),
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEC4899).copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, Color(0xFFEC4899).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEC4899))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "CRITICAL ADVISORY: CRASH OVERWRITING RISK",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEC4899),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        "To maximize file recovery rates, IMMEDIATELY halt downloading external files, taking images, or streaming media. Every operation that writes data to local flash storage risks overwriting the sectors where deleted files reside, making forensic recovery mathematically impossible.",
                        color = Color(0xFF909094),
                        fontSize = 12.sp
                    )
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
