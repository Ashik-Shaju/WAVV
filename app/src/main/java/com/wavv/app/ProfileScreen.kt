package com.wavv.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.max
import java.util.Locale

internal enum class ProfileSettingContent { Info, Theme, Folders, Storage, Notifications, History, UHQ, Crossfade, BitPerfect, Equalizer, Standby, AutomaticLyrics }

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
    activeTrackId: Long?,
) {
    val context = LocalContext.current
    val automaticLyricsSearchEnabled by AutomaticLyricsSearchPreference.state(context).collectAsStateWithLifecycle()
    val trackCount = songs.size
    val artistCount = songs.asSequence()
        .flatMap { it.artistNames().asSequence() }
        .filter(String::isNotEmpty)
        .distinctBy { it.lowercase(Locale.ROOT) }
        .count()
    val albumCount = songs.asSequence()
        .map { "${it.album.trim()}\u0000${(it.albumArtist ?: it.artist).trim()}" }
        .filter(String::isNotEmpty)
        .distinctBy { it.lowercase(Locale.ROOT) }
        .count()
    val storageBytes = songs.sumOf(Song::fileSizeBytes)
    val storageUsed = remember(songs, context) {
        storageBytes.takeIf { it > 0L }?.let { android.text.format.Formatter.formatShortFileSize(context, it) } ?: "—"
    }
    val notificationsEnabled = context.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()
    var uhqEnabled by remember(context) { mutableStateOf(UHQPlaybackPreference.isEnabled(context)) }
    var bitPerfectEnabled by remember(context) { mutableStateOf(BitPerfectSettings.isEnabled(context)) }
    var crossfadeSeconds by remember(context) { mutableStateOf(CrossfadeSettings.durationSeconds(context)) }
    var equalizerEnabled by remember(context) { mutableStateOf(AudioEqualizerSettings.isEnabled(context)) }
    var equalizerProfile by remember(context) { mutableStateOf(AudioEqualizerSettings.activeProfile(context)) }
    var savedEqualizerProfiles by remember(context) { mutableStateOf(AudioEqualizerSettings.savedProfiles(context)) }
    val playbackEffects by PlaybackEffectStatus.state.collectAsStateWithLifecycle()
    val uhqPlaybackLabel = uhqSettingLabel(uhqEnabled, activeTrackId, playbackEffects)
    val bitPerfectPlaybackLabel = bitPerfectSettingLabel(bitPerfectEnabled, playbackEffects.bitPerfect)
    val equalizerPlaybackLabel = equalizerSettingLabel(
        equalizerEnabled,
        equalizerProfile.name,
        playbackEffects.equalizer,
        playbackEffects.gain,
        equalizerProfile.gainDb,
    )
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
        ProfileSetting(Icons.Default.LibraryMusic, "UHQ AAC", uhqPlaybackLabel, "Experimental AAC enhancement with a cached copy. Original music files are never changed.", ProfileSettingContent.UHQ),
        ProfileSetting(Icons.Default.Equalizer, "Crossfade", if (crossfadeSeconds == 0) "Off" else "${crossfadeSeconds}s", "Blend into the next queued track. Temporarily bypassed while bit-perfect mode is active; the saved duration is preserved.", ProfileSettingContent.Crossfade),
        ProfileSetting(Icons.Default.Equalizer, "Bit-perfect mode", bitPerfectPlaybackLabel, "Requests Android's bit-perfect mixer mode only when a connected USB output supports the active PCM format.", ProfileSettingContent.BitPerfect),
        ProfileSetting(Icons.Default.Equalizer, "Equalizer", equalizerPlaybackLabel, "Ten-band EQ and session gain with local profiles.", ProfileSettingContent.Equalizer),
        ProfileSetting(Icons.Default.LibraryMusic, "Standby", if (standbyEnabledState) "On" else "Off", "Clock and music artwork while your device is docked or resting.", ProfileSettingContent.Standby),
        ProfileSetting(
            Icons.Default.LibraryMusic,
            "Automatically check for lyrics",
            if (automaticLyricsSearchEnabled) "On" else "Off",
            "When a track starts playing without embedded lyrics, look for a matching .ttml or .lrc file. Wavv searches selected music folders, or Downloads only when Android grants all-files access.",
            ProfileSettingContent.AutomaticLyrics,
        ),
        ProfileSetting(Icons.Outlined.PrivacyTip, "Listening History", "Local · 1 year", "Listening activity stays on this device for up to one year and is used to personalize recommendations.", ProfileSettingContent.History),
        ProfileSetting(Icons.Outlined.PrivacyTip, "Privacy", "", "Your selected library and playback history are stored locally on this device."),
        ProfileSetting(Icons.AutoMirrored.Outlined.HelpOutline, "Help & Support", "", "Wavv is a local music player. Open Android app settings to manage permissions. No in-app support contact is configured."),
    )
    var activeSetting by remember { mutableStateOf<ProfileSetting?>(null) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Settings")
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
                item {
                    Column(Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                ProfileSettingContent.UHQ -> uhqPlaybackLabel
                ProfileSettingContent.Crossfade -> if (crossfadeSeconds == 0) "Off" else "${crossfadeSeconds}s"
                ProfileSettingContent.BitPerfect -> bitPerfectPlaybackLabel
                ProfileSettingContent.Equalizer -> equalizerPlaybackLabel
                ProfileSettingContent.Standby -> if (standbyEnabledState) "On" else "Off"
                ProfileSettingContent.AutomaticLyrics -> if (automaticLyricsSearchEnabled) "On" else "Off"
                else -> setting.value
            }
            val sheetHeight = when (setting.content) {
                ProfileSettingContent.Equalizer -> 760.dp
                ProfileSettingContent.Crossfade -> 350.dp
                ProfileSettingContent.Standby -> 740.dp
                else -> 460.dp
            }
            WavvBottomSheet(
                onDismissRequest = { activeSetting = null },
                maxHeight = sheetHeight,
                artworkSong = songs.firstOrNull { it.id == activeTrackId } ?: songs.firstOrNull(),
            ) { _ ->
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
                    "Ten EQ bands and signed session gain, applied through Android session effects where supported."
                } else if (setting.content == ProfileSettingContent.BitPerfect) {
                    "Uses Android 14+ BIT_PERFECT mixer mode only when the routed USB output advertises the exact PCM format used by the track. Android bypasses framework mixing and volume scaling; Wavv pauses EQ, UHQ enhancement, and crossfade while enabled. Use the DAC's hardware volume control. Other outputs continue with normal playback."
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
                        SettingInfoRow("Current playback", if (activeTrackId == null) "No track playing" else uhqPlaybackLabel, null)
                        Spacer(Modifier.height(8.dp))
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
                    ProfileSettingContent.Crossfade -> {
                        Text(
                            "Crossfade queued tracks",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (crossfadeSeconds == 0) "Off" else "$crossfadeSeconds seconds",
                            color = Color.White.copy(.62f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Slider(
                            value = crossfadeSeconds.toFloat(),
                            onValueChange = { crossfadeSeconds = it.toInt() },
                            onValueChangeFinished = { CrossfadeSettings.setDurationSeconds(context, crossfadeSeconds) },
                            valueRange = 0f..CrossfadeSettings.MAX_SECONDS.toFloat(),
                            steps = CrossfadeSettings.MAX_SECONDS - 1,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Off", color = Color.White.copy(.45f), fontSize = 11.sp)
                            Text("12 seconds", color = Color.White.copy(.45f), fontSize = 11.sp)
                        }
                    }
                    ProfileSettingContent.BitPerfect -> {
                        SettingInfoRow("Current status", bitPerfectPlaybackLabel, null)
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Use USB bit-perfect mixer mode", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Only active when Android confirms a matching USB format.", color = Color.White.copy(.48f), fontSize = 12.sp, lineHeight = 17.sp)
                            }
                            Switch(
                                checked = bitPerfectEnabled,
                                onCheckedChange = { enabled ->
                                    bitPerfectEnabled = enabled
                                    BitPerfectSettings.setEnabled(context, enabled)
                                },
                            )
                        }
                    }
                    ProfileSettingContent.Equalizer -> EqualizerSettingsContent(
                        modifier = Modifier.weight(1f),
                        artworkSong = songs.firstOrNull { it.id == activeTrackId } ?: songs.firstOrNull(),
                        enabled = equalizerEnabled,
                        profile = equalizerProfile,
                        savedProfiles = savedEqualizerProfiles,
                        playbackStatus = equalizerPlaybackLabel,
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
                        onProfileChange = { profile ->
                            equalizerProfile = profile
                            AudioEqualizerSettings.setActiveProfile(context, profile)
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
                    ProfileSettingContent.AutomaticLyrics -> {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Automatically check for lyrics", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Checks only when a track starts playing and has no embedded lyrics. Matching files are remembered for that track; TTML is preferred over LRC.",
                                    color = Color.White.copy(.52f),
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                )
                            }
                            Switch(
                                checked = automaticLyricsSearchEnabled,
                                onCheckedChange = { enabled -> AutomaticLyricsSearchPreference.setEnabled(context, enabled) },
                            )
                        }
                    }
                    ProfileSettingContent.Info -> {
                        SettingInfoRow(setting.title, setting.value, null)
                        if (setting.title == "Help & Support") {
                            Spacer(Modifier.height(10.dp))
                            TextButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            .setData(Uri.fromParts("package", context.packageName, null)),
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Open app settings") }
                        }
                    }
                }
            }
        }
        if (confirmClearHistory) {
            WavvBottomSheet(
                onDismissRequest = { confirmClearHistory = false },
                maxHeight = 300.dp,
                artworkSong = songs.firstOrNull { it.id == activeTrackId } ?: songs.firstOrNull(),
            ) { close ->
                Text("Clear listening history?", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "This removes listening activity and recently played items from this device. Favorites and playlists will stay.",
                    Modifier.padding(top = 10.dp),
                    color = Color.White.copy(.72f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = close) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            confirmClearHistory = false
                            activeSetting = null
                            onClearListeningHistory()
                            close()
                        },
                    ) { Text("Clear history", color = WavvPink) }
                }
            }
        }
    }
}

@Composable
private fun EqualizerSettingsContent(
    modifier: Modifier = Modifier,
    artworkSong: Song?,
    enabled: Boolean,
    profile: EqualizerProfile,
    savedProfiles: List<SavedEqualizerProfile>,
    playbackStatus: String,
    onEnabledChange: (Boolean) -> Unit,
    onProfileSelected: (EqualizerProfile) -> Unit,
    onProfileChange: (EqualizerProfile) -> Unit,
    onSaveProfile: (String) -> Unit,
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var overwriteProfileName by remember { mutableStateOf<String?>(null) }
    var profileName by remember(profile.name) {
        mutableStateOf(profile.name.takeUnless { it == "Custom" }.orEmpty())
    }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Equalizer", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("${profile.name} · EQ, gain", color = Color.White.copy(.5f), fontSize = 12.sp)
                Text("Playback: $playbackStatus", color = Color.White.copy(.42f), fontSize = 11.sp)
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
            item {
                Text("Session gain", color = Color.White.copy(.56f), fontSize = 12.sp)
            }
            item {
                EqualizerSliderRow("Gain", profile.gainDb, enabled) {
                    onProfileChange(profile.copy(name = "Custom", gainDb = it))
                }
            }
            item {
                Text(
                    "Positive gain can clip loud tracks.",
                    Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp),
                    color = Color.White.copy(.36f),
                    fontSize = 10.sp,
                )
            }
            item {
                Text("10-band equalizer", color = Color.White.copy(.56f), fontSize = 12.sp)
            }
            itemsIndexed(EqualizerProfiles.frequenciesHz) { index, frequencyHz ->
                EqualizerSliderRow(EqualizerProfiles.formatFrequency(frequencyHz), profile.gainsDb[index], enabled) {
                    val gains = profile.gainsDb.toMutableList().apply { this[index] = it }
                    onProfileChange(profile.copy(name = "Custom", gainsDb = gains))
                }
            }
        }
        TextButton(onClick = { showSaveDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Save profile")
        }
    }

    if (showSaveDialog) {
        WavvBottomSheet(
            onDismissRequest = { showSaveDialog = false },
            maxHeight = 320.dp,
            artworkSong = artworkSong,
        ) { close ->
            Text("Save equalizer profile", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            OutlinedTextField(
                value = profileName,
                onValueChange = { profileName = it.take(32) },
                label = { Text("Profile name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = close) { Text("Cancel") }
                TextButton(
                    enabled = profileName.isNotBlank(),
                    onClick = {
                        val normalizedName = profileName.trim()
                        if (hasEqualizerProfileName(savedProfiles, normalizedName)) {
                            overwriteProfileName = normalizedName
                        } else {
                            onSaveProfile(normalizedName)
                        }
                        showSaveDialog = false
                        close()
                    },
                ) { Text("Save") }
            }
        }
    }

    overwriteProfileName?.let { name ->
        WavvBottomSheet(
            onDismissRequest = {
                overwriteProfileName = null
                showSaveDialog = true
            },
            maxHeight = 300.dp,
            artworkSong = artworkSong,
        ) { close ->
            Text("Replace saved profile?", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "A profile named \"$name\" already exists. Replace its saved equalizer curve?",
                Modifier.padding(top = 10.dp),
                color = Color.White.copy(.72f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = {
                        overwriteProfileName = null
                        showSaveDialog = true
                        close()
                    },
                ) { Text("Cancel") }
                TextButton(
                    onClick = {
                        onSaveProfile(name)
                        overwriteProfileName = null
                        close()
                    },
                ) { Text("Replace", color = WavvPink) }
            }
        }
    }
}

@Composable
private fun EqualizerSliderRow(
    label: String,
    gainDb: Float,
    enabled: Boolean,
    onGainChange: (Float) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, Modifier.width(64.dp), color = Color.White.copy(.72f), fontSize = 11.sp, maxLines = 1)
        Slider(
            value = gainDb,
            onValueChange = { onGainChange(EqualizerProfiles.quantizeGainDb(it)) },
            valueRange = EqualizerProfiles.MIN_GAIN_DB..EqualizerProfiles.MAX_GAIN_DB,
            steps = ((EqualizerProfiles.MAX_GAIN_DB - EqualizerProfiles.MIN_GAIN_DB) /
                EqualizerProfiles.GAIN_STEP_DB).toInt() - 1,
            modifier = Modifier.weight(1f),
            enabled = enabled,
        )
        Text(EqualizerProfiles.formatGain(gainDb), Modifier.width(54.dp), color = Color.White.copy(.62f), fontSize = 10.sp)
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
) = SheetActionRowLayout(title, onClick) {
    Icon(icon, contentDescription = null, tint = Color.White.copy(.82f), modifier = Modifier.size(21.dp))
}

@Composable
internal fun SheetActionRow(
    iconRes: Int,
    title: String,
    onClick: () -> Unit,
) = SheetActionRowLayout(title, onClick) {
    Icon(
        androidx.compose.ui.res.painterResource(iconRes),
        contentDescription = null,
        tint = Color.White.copy(.82f),
        modifier = Modifier.size(21.dp),
    )
}

@Composable
private fun SheetActionRowLayout(
    title: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(CircleShape)
            .background(Color.White.copy(.09f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        icon()
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
