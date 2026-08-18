package com.heartguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.heartguard.ui.theme.ConfirmGreen

@Composable
internal fun UserAvatar(
    nickname: String,
    avatarUri: String,
    modifier: Modifier = Modifier,
    showEditBadge: Boolean = false,
    fallbackFontSize: TextUnit = 28.sp,
    badgeSize: Dp = 32.dp,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val fallbackText = nickname.trim().take(1)
    var imageLoadFailed by remember(avatarUri) { mutableStateOf(false) }
    val hasAvatarImage = avatarUri.isNotBlank() && !imageLoadFailed
    val avatarModifier = if (onClick == null) {
        modifier
    } else {
        modifier.clickable(onClick = onClick)
    }

    Box(
        modifier = avatarModifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(ConfirmGreen.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            if (fallbackText.isBlank()) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = ConfirmGreen,
                    modifier = Modifier.size(badgeSize * 1.25f),
                )
            } else {
                Text(
                    text = fallbackText,
                    color = ConfirmGreen,
                    fontSize = fallbackFontSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (hasAvatarImage) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(avatarUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = {
                    imageLoadFailed = true
                },
                onSuccess = {
                    imageLoadFailed = false
                },
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape),
            )
        }

        if (showEditBadge) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(badgeSize),
                shape = CircleShape,
                color = ConfirmGreen,
                shadowElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(badgeSize * 0.58f),
                    )
                }
            }
        }
    }
}
