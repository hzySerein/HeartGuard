package com.heartguard.ui.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heartguard.R
import com.heartguard.ui.theme.ConfirmGreen
import com.heartguard.ui.theme.WarningRed
import com.heartguard.viewmodel.FakeCallState
import com.heartguard.viewmodel.FakeCallViewModel
import kotlinx.coroutines.delay

private val FakeCallBackground = Color(0xFF101820)
private val FakeCallPanel = Color(0xFF1B2834)
private val FakeCallMutedText = Color(0xFFB8C2CC)
private val FakeCallChip = Color.White.copy(alpha = 0.12f)

@Composable
fun FakeCallScreen(
    fakeCallViewModel: FakeCallViewModel,
    scenarioId: String? = null,
    onDrillFinished: (Boolean) -> Unit = {},
) {
    val scenario = remember(scenarioId) { findFraudScenarioById(scenarioId) }
    val voicePracticeScript = remember(scenario) { scenario.toCompactPracticeScript() }
    val callState by fakeCallViewModel.callState.collectAsStateWithLifecycle()
    val sessionId by fakeCallViewModel.sessionId.collectAsStateWithLifecycle()
    var elapsedSeconds by remember(sessionId) { mutableStateOf(0) }

    LaunchedEffect(callState, sessionId) {
        when (callState) {
            FakeCallState.ANSWERED -> {
                elapsedSeconds = 0
                while (true) {
                    delay(1_000L)
                    elapsedSeconds += 1
                }
            }

            FakeCallState.HUNG_UP -> {
                elapsedSeconds = 0
                fakeCallViewModel.finishDrill()
                onDrillFinished(true)
            }

            FakeCallState.RINGING -> {
                elapsedSeconds = 0
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = FakeCallBackground,
    ) {
        when (callState) {
            FakeCallState.RINGING -> RingingContent(
                scenario = scenario,
                onAnswer = {
                    fakeCallViewModel.answerCall(voicePracticeScript)
                },
                onHangUp = fakeCallViewModel::hangUpCall,
            )

            FakeCallState.ANSWERED -> AnsweredContent(
                scenario = scenario,
                script = voicePracticeScript,
                elapsedTime = elapsedSeconds.formatCallDuration(),
                onHangUp = fakeCallViewModel::hangUpCall,
            )

            FakeCallState.HUNG_UP -> HungUpContent(
                onDrillFinished = {
                    fakeCallViewModel.finishDrill()
                    onDrillFinished(true)
                },
            )
        }
    }
}

@Composable
private fun RingingContent(
    scenario: FraudScenario,
    onAnswer: () -> Unit,
    onHangUp: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        DrillHeader(
            scenario = scenario,
            modifier = Modifier.align(Alignment.TopStart),
        )

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CallAvatar(avatarText = scenario.avatarText)
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = scenario.npcTitle,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.fake_call_caller_number),
                color = FakeCallMutedText,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(14.dp))
            SimulationTag()
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FakeCallActionButton(
                text = stringResource(R.string.fake_call_answer),
                containerColor = ConfirmGreen,
                contentColor = Color.White,
                height = 64,
                onClick = onAnswer,
            )
            FakeCallActionButton(
                text = stringResource(R.string.fake_call_hang_up),
                containerColor = WarningRed,
                contentColor = Color.White,
                height = 82,
                onClick = onHangUp,
            )
        }
    }
}

@Composable
private fun AnsweredContent(
    scenario: FraudScenario,
    script: String,
    elapsedTime: String,
    onHangUp: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        DrillHeader(
            scenario = scenario,
            modifier = Modifier.align(Alignment.TopStart),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 104.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.fake_call_in_call),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = elapsedTime,
                color = FakeCallMutedText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "${scenario.npcTitle} - ${stringResource(R.string.fake_call_incoming_number)}",
                color = FakeCallMutedText,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .background(
                    color = FakeCallPanel,
                    shape = RoundedCornerShape(20.dp),
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.fake_call_simulation_tag),
                color = FakeCallMutedText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = script,
                color = Color.White,
                fontSize = 20.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        FakeCallActionButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            text = stringResource(R.string.fake_call_hang_up),
            containerColor = WarningRed,
            contentColor = Color.White,
            height = 82,
            onClick = onHangUp,
        )
    }
}

private fun Int.formatCallDuration(): String {
    val minutes = this / 60
    val seconds = this % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun HungUpContent(
    onDrillFinished: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.fake_call_finished),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )

        FakeCallActionButton(
            modifier = Modifier.padding(top = 32.dp),
            text = stringResource(R.string.fake_call_return_anti_fraud),
            containerColor = ConfirmGreen,
            contentColor = Color.White,
            height = 72,
            onClick = onDrillFinished,
        )
    }
}

@Composable
private fun DrillHeader(
    scenario: FraudScenario,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.fake_call_drill_title),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(scenario.labelRes),
            color = FakeCallMutedText,
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun CallAvatar(
    avatarText: String,
) {
    Box(
        modifier = Modifier
            .size(92.dp)
            .background(FakeCallChip, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = avatarText,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SimulationTag() {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = FakeCallChip,
    ) {
        Text(
            text = stringResource(R.string.fake_call_simulation_tag),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FakeCallActionButton(
    modifier: Modifier = Modifier,
    text: String,
    containerColor: Color,
    contentColor: Color,
    height: Int,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(height.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Text(
            text = text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
