package com.wavv.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

@androidx.compose.runtime.Composable
internal fun SongListArtworkBackdrop(song: Song?, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(modifier.clipToBounds().background(WavvBackground)) {
        if (song != null) {
            AnimatedContent(
                targetState = song,
                transitionSpec = {
                    fadeIn(tween(720, easing = WavvScreenEasing)) togetherWith
                        fadeOut(tween(520, easing = WavvScreenEasing))
                },
                contentKey = { it.id },
                label = "collection edge to edge artwork",
            ) { artworkSong -> NowPlayingAmbientBackdrop(artworkSong, isPlaying = true) }
        }
    }
}

internal fun Modifier.edgeBlendedArtworkMask(): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                .12f to Color.Black.copy(alpha = .22f),
                .30f to Color.Black,
                .58f to Color.Black,
                .84f to Color.Black.copy(alpha = .72f),
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
