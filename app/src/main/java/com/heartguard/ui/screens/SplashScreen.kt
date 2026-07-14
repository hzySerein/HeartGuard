package com.heartguard.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heartguard.R
import com.heartguard.ui.theme.BackgroundWarm
import com.heartguard.ui.theme.TextPrimary
import com.heartguard.ui.theme.TextSecondary

@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val brandSplashResourceId = remember(context) {
        context.resources.getIdentifier(
            "splash_brand",
            "drawable",
            context.packageName,
        )
    }
    var visible by remember { mutableStateOf(false) }
    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 360),
        label = "splashContentAlpha",
    )

    LaunchedEffect(Unit) {
        visible = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundWarm),
    ) {
        if (brandSplashResourceId != 0) {
            Image(
                painter = painterResource(brandSplashResourceId),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(contentAlpha),
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 104.dp, start = 24.dp, end = 24.dp)
                    .alpha(contentAlpha),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.app_icon_art),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(132.dp),
                )
                Spacer(modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    color = TextPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = stringResource(R.string.splash_tagline),
                    color = TextSecondary,
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }

            Text(
                text = stringResource(R.string.splash_ai_support),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
                    .alpha(contentAlpha),
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
