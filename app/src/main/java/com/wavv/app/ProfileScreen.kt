package com.wavv.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.max
import java.util.Locale

internal enum class ProfileSettingContent { Info, Theme, Folders, Storage, Notifications, History, UHQ, Equalizer, Standby }

internal data class ProfileSetting(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val value: String,
    val detail: String,
    val content: ProfileSettingContent = ProfileSettingContent.Info,
)

private fun LibrarySource?.folderUris(): List<String> = when (this) {
    is LibrarySource.Folder -> listOf(uri)
    is LibrarySource.Folders -> uris.distinct().sorted()
    else -> emptyList()
}

private fun LibrarySource?.sourceSummary(): String = when (this) {
    LibrarySource.AllDevice -> "All device music"
    is LibrarySource.Files -> "${uris.size} selected files"
    is LibrarySource.Folder -> "1 folder"
    is LibrarySource.Folders -> if (uris.isEmpty()) "No folders selected" else "${uris.size} folders"
    null -> "Not selected"
}

private fun folderDisplayName(uriString: String): String = runCatching {
    Uri.decode(Uri.parse(uriString).lastPathSegment.orEmpty())
        .substringAfter(':')
        .substringAfterLast('/')
        .ifBlank { "Storage folder" }
}.getOrDefault("Selected folder")

@Composable
internal fun YouScreen(
    songs: List<Song>,
    source: LibrarySource?,
    analysisProgress: MusicAnalysisProgress?,
    onRetryAnalysis: () -> Unit,
    onAddMusicFolder: () -> Unit,
    onRemoveMusicFolder: (String) -> Unit,
    onChooseAllDevice: () -> Unit,
    onClearListeningHistory: () -> Unit,
    standbyEnabled: Boolean,
) {
    val context = LocalContext.current
    val trackCount = songs.size
    val artistCount = songs.asSequence()
        .map { it.artist.trim() }
        .filter(String::isNotEmpty)
        .distinctBy { it.lowercase(Locale.ROOT) }
        .count()
    val albumCount = songs.asSequence()
        .map { it.album.trim() }
        .filter(String::isNotEmpty)
        .distinctBy { it.lowercase(Locale.ROOT) }
        .count()
    val storageBytes = songs.sumOf(Song::fileSizeBytes)
    val storageUsed = remember(songs, context) {
        storageBytes.takeIf { it > 0L }?.let { android.text.format.Formatter.formatShortFileSize(context, it) } ?: "—"
    }
    val notificationsEnabled = context.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()
    var uhqEnabled by remember(context) { mutableStateOf(UHQPlaybackPreference.isEnabled(context)) }
    var equalizerEnabled by remember(context) { mutableStateOf(AudioEqualizerSettings.isEnabled(context)) }
    var equalizerProfile by remember(context) { mutableStateOf(AudioEqualizerSettings.activeProfile(context)) }
    var savedEqualizerProfiles by remember(context) { mutableStateOf(AudioEqualizerSettings.savedProfiles(context)) }
    var standbyEnabledState by remember(context) { mutableStateOf(standbyEnabled) }
    var deviceAdminEnabled by remember(context) { mutableStateOf(StandbyDeviceLock.isEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val deviceAdminLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) {
        deviceAdminEnabled = StandbyDeviceLock.isEnabled(context)
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) deviceAdminEnabled = StandbyDeviceLock.isEnabled(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val settings = listOf(
        ProfileSetting(Icons.Outlined.Palette, "Theme", "Dark", "Dark appearance is currently the only theme available.", ProfileSettingContent.Theme),
        ProfileSetting(Icons.Outlined.Notifications, "Notifications", if (notificationsEnabled) "Allowed" else "Blocked", "Notification access is controlled by Android for this app.", ProfileSettingContent.Notifications),
        ProfileSetting(Icons.Default.Folder, "Music Folders", source.sourceSummary(), "Choose one or more folders that Wavv scans for audio and adds to your playback library.", ProfileSettingContent.Folders),
        ProfileSetting(Icons.Default.LibraryMusic, "Storage Used", storageUsed, "Storage reported for the audio files currently indexed in your library.", ProfileSettingContent.Storage),
        ProfileSetting(Icons.Default.LibraryMusic, "UHQ AAC", if (uhqEnabled) "On" else "Off", "Experimental AAC enhancement with a cached copy. Original music files are never changed.", ProfileSettingContent.UHQ),
        ProfileSetting(Icons.Default.Equalizer, "Equalizer", if (equalizerEnabled) equalizerProfile.name else "Off", "Ten-band equalizer with local presets and saved profiles. Android audio-effect support varies by device.", ProfileSettingContent.Equalizer),
        ProfileSetting(Icons.Default.LibraryMusic, "Standby", if (standbyEnabledState) "On" else "Off", "Clock and music artwork while your device is docked or resting.", ProfileSettingContent.Standby),
        ProfileSetting(Icons.Outlined.PrivacyTip, "Listening History", "Local · 1 year", "Listening activity stays on this device for up to one year and is used to personalize recommendations.", ProfileSettingContent.History),
        ProfileSetting(Icons.Outlined.PrivacyTip, "Privacy", "", "Your selected library and playback history are stored locally on this device."),
        ProfileSetting(Icons.AutoMirrored.Outlined.HelpOutline, "Help & Support", "", "Wavv is a local music player. For app permissions, open Android app settings."),
    )
    var activeSetting by remember { mutableStateOf<ProfileSetting?>(null) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("You")
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 30.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Box(Modifier.size(96.dp).shadow(24.dp, CircleShape).clip(CircleShape).background(Color(0xFF6250D6)).border(3.dp, Color(0x667B3FF5), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, contentDescription = "You", tint = Color.White, modifier = Modifier.size(46.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("You", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.6).sp, lineHeight = 29.sp)
                            Text("Your library, on this device", color = Color.White.copy(.62f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Surface(
                                Modifier.padding(top = 8.dp),
                                shape = CircleShape,
                                color = Color.White.copy(.12f),
                                border = BorderStroke(1.dp, Color.White.copy(.18f)),
                            ) {
                                Text("NO ACCOUNT REQUIRED", Modifier.padding(horizontal = 18.dp, vertical = 8.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShelfLabel("On this device")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard(trackCount.toString(), "Tracks", Modifier.weight(1f))
                            StatCard(artistCount.toString(), "Artists", Modifier.weight(1f))
                            StatCard(albumCount.toString(), "Albums", Modifier.weight(1f))
                        }
                    }
                }
                item {
                    if (songs.isNotEmpty()) {
                        analysisProgress?.let { progress ->
                            MusicAnalysisCard(progress, onRetryAnalysis, Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp))
                        }
                    }
                }
                item { SettingsBlock(settings) { activeSetting = it } }
                item { Text("Wavv · Local Music Player", Modifier.fillMaxWidth().padding(24.dp), color = Color.White.copy(.2f), fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
        }
        activeSetting?.let { setting ->
            val displayedSettingValue = when (setting.content) {
                ProfileSettingContent.UHQ -> if (uhqEnabled) "On" else "Off"
                ProfileSettingContent.Equalizer -> if (equalizerEnabled) equalizerProfile.name else "Off"
                ProfileSettingContent.Standby -> if (standbyEnabledState) "On" else "Off"
                else -> setting.value
            }
            val sheetHeight = when (setting.content) {
                ProfileSettingContent.Equalizer -> 760.dp
                ProfileSettingContent.Standby -> 740.dp
                else -> 460.dp
            }
            WavvBottomSheet(onDismissRequest = { activeSetting = null }, maxHeight = sheetHeight) { _ ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(.09f)), contentAlignment = Alignment.Center) {
                        Icon(setting.icon, contentDescription = null, tint = Color.White.copy(.85f), modifier = Modifier.size(21.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(setting.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Text(displayedSettingValue, color = Color.White.copy(.52f), fontSize = 12.sp)
                    }
                }
                val detail = if (setting.content == ProfileSettingContent.Equalizer) {
                    "Ten octave-spaced bands, mapped onto bands supported by the active Android playback session. Device support can vary."
                } else {
                    setting.detail
                }
                Text(detail, Modifier.padding(top = 16.dp, bottom = 12.dp), color = Color.White.copy(.62f), fontSize = 14.sp, lineHeight = 20.sp)
                when (setting.content) {
                    ProfileSettingContent.Theme -> SettingInfoRow(setting.value, "Current app appearance", "Selected")
                    ProfileSettingContent.Folders -> {
                        val folders = source.folderUris()
                        if (folders.isEmpty()) {
                            SettingInfoRow("Current source", source.sourceSummary(), null)
                        } else {
                            LazyColumn(
                                Modifier.heightIn(max = 210.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                items(folders, key = { it }) { folderUri ->
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                            .background(Color.White.copy(.065f))
                                            .border(1.dp, Color.White.copy(.07f), RoundedCornerShape(16.dp))
                                            .padding(start = 14.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White.copy(.68f), modifier = Modifier.size(18.dp))
                                        Text(folderDisplayName(folderUri), Modifier.weight(1f), color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        TextButton(onClick = { onRemoveMusicFolder(folderUri) }) { Text("Remove") }
                                    }
                                }
                            }
                        }
                        TextButton(onClick = onAddMusicFolder, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add folder")
                        }
                        TextButton(onClick = onChooseAllDevice, modifier = Modifier.fillMaxWidth()) {
                            Text("Scan all device music")
                        }
                    }
                    ProfileSettingContent.Storage -> {
                        SettingInfoRow("Indexed audio", storageUsed, null)
                        Spacer(Modifier.height(8.dp))
                        SettingInfoRow("Tracks in library", trackCount.toString(), null)
                    }
                    ProfileSettingContent.Notifications -> {
                        SettingInfoRow("Android notifications", if (notificationsEnabled) "Allowed" else "Blocked", null)
                        Spacer(Modifier.height(10.dp))
                        TextButton(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Open notification settings") }
                    }
                    ProfileSettingContent.History -> {
                        SettingInfoRow("Stored on this device", "Older activity is removed automatically", null)
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = { confirmClearHistory = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Clear listening history", color = WavvPink)
                        }
                    }
                    ProfileSettingContent.UHQ -> {
                        Text(
                            "Experimental AAC-only enhancement. Other formats and AAC files this device cannot decode continue playing the original.",
                            color = Color.White.copy(.62f),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Use UHQ for AAC playback", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Playback starts from the original and may switch to the cached version when ready.", color = Color.White.copy(.48f), fontSize = 12.sp, lineHeight = 17.sp)
                            }
                            Switch(
                                checked = uhqEnabled,
                                onCheckedChange = { enabled ->
                                    uhqEnabled = enabled
                                    UHQPlaybackPreference.setEnabled(context, enabled)
                                },
                            )
                        }
                    }
                    ProfileSettingContent.Equalizer -> EqualizerSettingsContent(
                        modifier = Modifier.weight(1f),
                        enabled = equalizerEnabled,
                        profile = equalizerProfile,
                        savedProfiles = savedEqualizerProfiles,
                        onEnabledChange = { enabled ->
                            equalizerEnabled = enabled
                            AudioEqualizerSettings.setEnabled(context, enabled)
                        },
                        onProfileSelected = { profile ->
                            equalizerProfile = profile
                            equalizerEnabled = true
                            AudioEqualizerSettings.setActiveProfile(context, profile)
                            AudioEqualizerSettings.setEnabled(context, true)
                        },
                        onBandChange = { index, gainDb ->
                            val gains = equalizerProfile.gainsDb.toMutableList().apply { this[index] = gainDb }
                            equalizerProfile = EqualizerProfile("Custom", gains)
                            AudioEqualizerSettings.setActiveProfile(context, equalizerProfile)
                        },
                        onSaveProfile = { name ->
                            val profile = equalizerProfile.copy(name = name)
                            equalizerProfile = profile
                            AudioEqualizerSettings.setActiveProfile(context, profile)
                            savedEqualizerProfiles = AudioEqualizerSettings.saveProfile(context, profile)
                        },
                    )
                    ProfileSettingContent.Standby -> {
                        Column(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Image(
                                painter = painterResource(R.drawable.standby_demo),
                                contentDescription = "Standby clock, album artwork and now playing pill preview",
                                modifier = Modifier.fillMaxWidth().aspectRatio(2400f / 1080f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .border(1.dp, Color.White.copy(.12f), RoundedCornerShape(20.dp)),
                                contentScale = ContentScale.Fit,
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Enable Standby", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Swipe down on the expanded Now Playing card to enter.", color = Color.White.copy(.52f), fontSize = 12.sp, lineHeight = 17.sp)
                                }
                                Switch(
                                    checked = standbyEnabledState,
                                    onCheckedChange = { enabled ->
                                        standbyEnabledState = enabled
                                        StandbyPreference.setEnabled(context, enabled)
                                    },
                                )
                            }
                            SettingInfoRow(
                                "Device lock permission",
                                if (deviceAdminEnabled) "Enabled. Android will keep its lock screen and require your usual unlock." else "Required for Wavv to immediately lock the phone on detected motion.",
                                null,
                            )
                            if (!deviceAdminEnabled) {
                                TextButton(
                                    onClick = { deviceAdminLauncher.launch(StandbyDeviceLock.activationIntent(context)) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Allow Wavv to lock this phone") }
                            } else {
                                TextButton(
                                    onClick = { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Manage this permission in Android settings") }
                            }
                            SettingInfoRow("Controls", "Tap artwork to play or pause; swipe left for previous and right for next. Tap the pill in portrait to return Home.", null)
                            Text(
                                "After 5 seconds without touch or detected motion, the acrylic background fades to black. After 5 continuous minutes without touch or detected motion, Standby enters secure mode. Motion then locks the phone when Device lock permission is enabled; otherwise Wavv stops keeping the screen awake so Android can apply its timeout. While unplugged, Standby also locks after 20 minutes without activity. While charging, it keeps running until motion triggers the secure action. In black mode, the clock, artwork and pill shift by a few pixels every minute to reduce OLED image retention. Every 10 minutes without activity, the whole display fades to black for 3 seconds, then fades back in. Standby may use extra battery.",
                                color = Color.White.copy(.62f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(horizontal = 2.dp, vertical = 3.dp),
                            )
                        }
                    }
                    ProfileSettingContent.Info -> SettingInfoRow(setting.title, setting.value, null)
                }
            }
        }
        if (confirmClearHistory) {
            AlertDialog(
                onDismissRequest = { confirmClearHistory = false },
                title = { Text("Clear listening history?") },
                text = { Text("This removes listening activity and recently played items from this device. Favorites and playlists will stay.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmClearHistory = false
                            activeSetting = null
                            onClearListeningHistory()
                        },
                    ) { Text("Clear history", color = WavvPink) }
                },
                dismissButton = { TextButton(onClick = { confirmClearHistory = false }) { Text("Cancel") } },
                containerColor = WavvSurface,
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(.72f),
            )
        }
    }
}

@Composable
private fun EqualizerSettingsContent(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    profile: EqualizerProfile,
    savedProfiles: List<SavedEqualizerProfile>,
    onEnabledChange: (Boolean) -> Unit,
    onProfileSelected: (EqualizerProfile) -> Unit,
    onBandChange: (Int, Float) -> Unit,
    onSaveProfile: (String) -> Unit,
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var profileName by remember(profile.name) {
        mutableStateOf(profile.name.takeUnless { it == "Custom" }.orEmpty())
    }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Equalizer", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("${profile.name} · 10 bands", color = Color.White.copy(.5f), fontSize = 12.sp)
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        Text("Presets", color = Color.White.copy(.56f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EqualizerProfiles.presets.forEach { preset ->
                TextButton(onClick = { onProfileSelected(preset) }) { Text(preset.name) }
            }
        }
        if (savedProfiles.isNotEmpty()) {
            Text("Saved profiles", color = Color.White.copy(.56f), fontSize = 12.sp)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                savedProfiles.forEach { saved ->
                    TextButton(onClick = { onProfileSelected(saved.profile) }) { Text(saved.profile.name) }
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(EqualizerProfiles.frequenciesHz.size) { index ->
                val frequencyHz = EqualizerProfiles.frequenciesHz[index]
                Row(
                    Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(EqualizerProfiles.formatFrequency(frequencyHz), Modifier.width(42.dp), color = Color.White.copy(.72f), fontSize = 11.sp)
                    Slider(
                        value = profile.gainsDb[index],
                        onValueChange = { onBandChange(index, it) },
                        valueRange = EqualizerProfiles.MIN_GAIN_DB..EqualizerProfiles.MAX_GAIN_DB,
                        steps = 23,
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                    )
                    Text(EqualizerProfiles.formatGain(profile.gainsDb[index]), Modifier.width(54.dp), color = Color.White.copy(.62f), fontSize = 10.sp)
                }
            }
        }
        TextButton(onClick = { showSaveDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Save profile")
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save equalizer profile") },
            text = {
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it.take(32) },
                    label = { Text("Profile name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = profileName.isNotBlank(),
                    onClick = {
                        onSaveProfile(profileName.trim())
                        showSaveDialog = false
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } },
            containerColor = WavvSurface,
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(.72f),
        )
    }
}

@Composable
private fun MusicAnalysisCard(
    progress: MusicAnalysisProgress,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    val targetProgress = progress.fraction
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress ?: 0f,
        animationSpec = tween(260, easing = WavvMotionEasing),
        label = "music analysis progress",
    )
    Column(
        modifier.fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xCC23171E), Color(0xB51B1A24), Color(0xA51B2028)),
                ),
            )
            .border(
                1.dp,
                Brush.verticalGradient(0f to Color.White.copy(.24f), 1f to Color.White.copy(.07f)),
                shape,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(WavvPink.copy(.25f), Color(0x33FFB85C))))
                    .border(1.dp, Color.White.copy(.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = Color(0xFFFFB5C7), modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Music insights", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(progress.detail, color = Color.White.copy(.58f), fontSize = 12.sp, lineHeight = 16.sp)
            }
            when {
                progress.canRetry -> TextButton(onClick = onRetry) { Text("Retry", color = WavvPink, fontWeight = FontWeight.Bold) }
                progress.status == MusicAnalysisStatus.QUEUED || progress.status == MusicAnalysisStatus.RUNNING ->
                    CircularProgressIndicator(color = WavvPink, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                progress.status == MusicAnalysisStatus.COMPLETE ->
                    Icon(Icons.Default.Check, contentDescription = "Complete", tint = Color(0xFFFFC76C), modifier = Modifier.size(19.dp))
            }
        }
        if (targetProgress != null && progress.status == MusicAnalysisStatus.RUNNING) {
            Box(
                Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(.12f)),
            ) {
                Box(
                    Modifier.fillMaxWidth(animatedProgress).fillMaxHeight().background(
                        Brush.horizontalGradient(listOf(Color(0xFFFFC76C), WavvPink, Color(0xFFB579FF))),
                    ),
                )
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(.06f)).border(1.dp, Color.White.copy(.07f), RoundedCornerShape(12.dp)).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(label, color = Color.White.copy(.4f), fontSize = 11.sp)
    }
}

@Composable
private fun SettingInfoRow(title: String, value: String, trailing: String?) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(.065f))
            .border(1.dp, Color.White.copy(.07f), RoundedCornerShape(22.dp))
            .padding(horizontal = 17.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (value.isNotBlank()) Text(value, color = Color.White.copy(.48f), fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        trailing?.let { Text(it, color = WavvPink, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
internal fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(CircleShape)
            .background(Color.White.copy(.07f))
            .border(1.dp, Color.White.copy(.08f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(.82f), modifier = Modifier.size(21.dp))
        Text(title, Modifier.weight(1f), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(.3f), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SettingsBlock(settings: List<ProfileSetting>, onSelect: (ProfileSetting) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShelfLabel("Settings")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(.05f)).border(1.dp, Color.White.copy(.07f), RoundedCornerShape(14.dp))) {
            settings.forEachIndexed { index, setting ->
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = { onSelect(setting) }).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(.06f)), contentAlignment = Alignment.Center) {
                        Icon(setting.icon, contentDescription = null, tint = Color.White.copy(.72f), modifier = Modifier.size(18.dp))
                    }
                    Text(setting.title, Modifier.weight(1f), color = Color.White, fontSize = 15.sp)
                    Text(setting.value, color = Color.White.copy(.35f), fontSize = 13.sp)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(.2f), modifier = Modifier.size(14.dp))
                }
                if (index != settings.lastIndex) Hairline()
            }
        }
    }
}
