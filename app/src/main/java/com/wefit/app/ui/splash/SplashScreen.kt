package com.wefit.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wefit.app.R
import com.wefit.app.ui.theme.BrandColors
import com.wefit.app.ui.theme.WeFitTheme
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(startRoute: String?, onFinished: (String) -> Unit) {
    val anim = remember { Animatable(0f) }
    var minTimeElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        anim.animateTo(1f, tween(700))
        delay(700)
        minTimeElapsed = true
    }
    LaunchedEffect(minTimeElapsed, startRoute) {
        if (minTimeElapsed && startRoute != null) onFinished(startRoute)
    }

    Box(Modifier.fillMaxSize().background(WeFitTheme.colors.splashGradient), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(anim.value).scale(0.9f + 0.1f * anim.value)) {
            Image(painterResource(R.drawable.ic_wefit_bird), contentDescription = "WeFit logo", modifier = Modifier.size(180.dp, 153.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White)) { append("We") }
                    withStyle(SpanStyle(color = BrandColors.Orange)) { append("Fit") }
                },
                fontSize = 52.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.tagline),
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
        Box(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp)
                .width(72.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.5f)),
        )
    }
}
