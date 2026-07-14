package com.heartguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.heartguard.R
import com.heartguard.data.local.AppDao
import com.heartguard.ui.theme.BackgroundWarm
import com.heartguard.ui.theme.ConfirmGreen
import com.heartguard.ui.theme.TextPrimary
import com.heartguard.ui.theme.TextSecondary
import com.heartguard.ui.theme.WarningRed

private val FraudRiskBackground = Color(0xFFFFE3E0)
private val FraudMediumRiskBackground = Color(0xFFFFEACC)
private val FraudMediumRiskText = Color(0xFFE46D00)
private val FraudDrillTagBackground = Color(0xFFE7F1E7)
private val FraudChipBackground = Color(0xFFECECEC)
private val FraudChatBackground = Color(0xFFFFFAF8)
private val AntiFraudDetailHorizontalPadding = 20.dp
private val AntiFraudDetailBottomPadding = 128.dp
private val AntiFraudDetailScriptHeight = 240.dp

@Composable
fun AntiFraudScreen(
    appDao: AppDao,
    onScenarioClick: (FraudScenario) -> Unit,
) {
    val completedDrillCount by remember(appDao) {
        appDao.observeFraudRecordCount()
    }.collectAsState(initial = 0)
    val successfulDrillCount by remember(appDao) {
        appDao.observeFraudPassedRecordCount()
    }.collectAsState(initial = 0)
    val latestScenarioTypes by remember(appDao) {
        appDao.observeLatestFraudScenarioTypes()
    }.collectAsState(initial = emptyList<String>())
    val recentRiskType = stringResource(latestScenarioTypes.firstOrNull().toRecentRiskTypeRes())

    FraudPracticeHall(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundWarm)
            .padding(horizontal = 20.dp),
        completedDrillCount = completedDrillCount,
        successfulDrillCount = successfulDrillCount,
        recentRiskType = recentRiskType,
        onScenarioSelected = onScenarioClick,
    )
}

@Composable
fun AntiFraudScenarioDetailScreen(
    scenario: FraudScenario,
    onBack: () -> Unit,
    onStartPractice: (FraudScenario) -> Unit,
) {
    val startPracticeLabel = stringResource(scenario.practiceType.startButtonRes)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundWarm),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AntiFraudDetailHorizontalPadding),
            contentPadding = PaddingValues(
                top = 16.dp,
                bottom = AntiFraudDetailBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AntiFraudDetailHeader(
                    scenario = scenario,
                    onBack = onBack,
                )
            }

            item {
                DetailSectionCard(
                    title = stringResource(R.string.anti_fraud_detail_script_title),
                ) {
                    FraudScriptScrollCard(script = scenario.script)
                }
            }

            item {
                DetailSectionCard(
                    title = stringResource(R.string.anti_fraud_detail_keywords_title),
                ) {
                    DetailTagRows(
                        tags = scenario.keywords,
                        backgroundColor = FraudRiskBackground,
                        textColor = WarningRed,
                    )
                }
            }

            item {
                DetailSectionCard(
                    title = stringResource(R.string.anti_fraud_detail_actions_title),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        scenario.correctActions.forEach { action ->
                            Text(
                                text = "• $action",
                                color = TextPrimary,
                                fontSize = 17.sp,
                                lineHeight = 25.sp,
                            )
                        }
                    }
                }
            }
        }

        AntiFraudDetailBottomBar(
            buttonText = startPracticeLabel,
            onClick = { onStartPractice(scenario) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun FraudScriptScrollCard(
    script: String,
) {
    val scriptScrollState = rememberScrollState()
    val scrollTip = stringResource(R.string.anti_fraud_detail_scroll_tip)
    Text(
        text = scrollTip,
        color = ConfirmGreen,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(10.dp))
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(AntiFraudDetailScriptHeight)
            .semantics {
                contentDescription = scrollTip
            },
        shape = RoundedCornerShape(18.dp),
        color = FraudChatBackground,
    ) {
        Text(
            text = script,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scriptScrollState)
                .padding(16.dp),
            color = TextPrimary,
            fontSize = 17.sp,
            lineHeight = 27.sp,
        )
    }
}

@Composable
private fun AntiFraudDetailBottomBar(
    buttonText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = BackgroundWarm,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AntiFraudDetailHorizontalPadding)
                .padding(top = 12.dp, bottom = 20.dp),
        ) {
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .semantics {
                        contentDescription = buttonText
                    },
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ConfirmGreen,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = buttonText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun AntiFraudDetailHeader(
    scenario: FraudScenario,
    onBack: () -> Unit,
) {
    val backLabel = stringResource(R.string.anti_fraud_detail_back)
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.Start)
                .semantics {
                    contentDescription = backLabel
                },
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = null,
                tint = ConfirmGreen,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = backLabel,
                color = ConfirmGreen,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(
            text = stringResource(scenario.labelRes),
            color = TextPrimary,
            fontSize = 27.sp,
            lineHeight = 35.sp,
            fontWeight = FontWeight.Bold,
        )

        ScenarioTag(
            text = stringResource(
                R.string.anti_fraud_detail_risk_label,
                stringResource(scenario.riskLabelRes),
            ),
            backgroundColor = scenario.riskLevel.backgroundColor,
            textColor = scenario.riskLevel.textColor,
        )
    }
}

@Composable
private fun DetailSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 21.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}

@Composable
private fun DetailTagRows(
    tags: List<String>,
    backgroundColor: Color,
    textColor: Color,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.chunked(2).forEach { rowTags ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowTags.forEach { tag ->
                    ScenarioTag(
                        text = tag,
                        backgroundColor = backgroundColor,
                        textColor = textColor,
                    )
                }
            }
        }
    }
}

@Composable
fun AntiFraudResultScreen(
    userInitiatedHangUp: Boolean,
    onPracticeAgain: () -> Unit,
    onReturnHome: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundWarm)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.anti_fraud_result_title),
            color = TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )

        ResultCard {
            Text(
                text = if (userInitiatedHangUp) {
                    stringResource(R.string.anti_fraud_result_success)
                } else {
                    stringResource(R.string.anti_fraud_result_finished)
                },
                color = TextPrimary,
                fontSize = 21.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.anti_fraud_result_risk_level),
                color = WarningRed,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        ResultCard {
            ResultSectionTitle(text = stringResource(R.string.anti_fraud_result_keywords_title))
            Spacer(modifier = Modifier.height(10.dp))
            KeywordRows(keywords = fraudRiskKeywords.map { stringResource(it) })
        }

        ResultCard(
            modifier = Modifier.weight(1f),
        ) {
            ResultSectionTitle(text = stringResource(R.string.anti_fraud_result_correct_actions_title))
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                fraudCorrectActions.forEach { actionRes ->
                    Text(
                        text = stringResource(actionRes),
                        color = TextPrimary,
                        fontSize = 17.sp,
                        lineHeight = 24.sp,
                    )
                }
            }
        }

        Button(
            onClick = onPracticeAgain,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ConfirmGreen,
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = stringResource(R.string.anti_fraud_result_practice_again),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Button(
            onClick = onReturnHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FraudChipBackground,
                contentColor = TextPrimary,
            ),
        ) {
            Text(
                text = stringResource(R.string.anti_fraud_result_return_home),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ResultCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = content,
        )
    }
}

@Composable
private fun ResultSectionTitle(text: String) {
    Text(
        text = text,
        color = TextPrimary,
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun KeywordRows(keywords: List<String>) {
    val rows = listOf(
        keywords.take(2),
        keywords.drop(2).take(2),
        keywords.drop(4),
    ).filter { it.isNotEmpty() }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { rowKeywords ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowKeywords.forEach { keyword ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = FraudRiskBackground,
                    ) {
                        Text(
                            text = keyword,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = WarningRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FraudPracticeHall(
    modifier: Modifier = Modifier,
    completedDrillCount: Int,
    successfulDrillCount: Int,
    recentRiskType: String,
    onScenarioSelected: (FraudScenario) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.anti_fraud_title),
                    color = TextPrimary,
                    fontSize = 26.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.anti_fraud_subtitle),
                    color = TextSecondary,
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                )
            }
        }

        item {
            FraudStatsCard(
                completedDrillCount = completedDrillCount,
                successfulDrillCount = successfulDrillCount,
                recentRiskType = recentRiskType,
            )
        }

        item {
            Text(
                text = stringResource(R.string.anti_fraud_scene_list_title),
                color = TextPrimary,
                fontSize = 22.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        items(fraudScenarios) { scenario ->
            FraudScenarioListCard(
                scenario = scenario,
                onClick = {
                    onScenarioSelected(scenario)
                },
            )
        }
    }
}

@Composable
private fun FraudStatsCard(
    completedDrillCount: Int,
    successfulDrillCount: Int,
    recentRiskType: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FraudStatRow(
                label = stringResource(R.string.anti_fraud_stat_completed),
                value = stringResource(R.string.anti_fraud_stat_count, completedDrillCount),
            )
            FraudStatRow(
                label = stringResource(R.string.anti_fraud_stat_success),
                value = stringResource(R.string.anti_fraud_stat_count, successfulDrillCount),
            )
            FraudStatRow(
                label = stringResource(R.string.anti_fraud_stat_recent),
                value = recentRiskType,
                valueColor = WarningRed,
            )
        }
    }
}

@Composable
private fun FraudStatRow(
    label: String,
    value: String,
    valueColor: Color = ConfirmGreen,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = TextPrimary,
            fontSize = 17.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun FraudScenarioListCard(
    scenario: FraudScenario,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(ConfirmGreen.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = scenario.avatarText,
                    color = ConfirmGreen,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(scenario.labelRes),
                    color = TextPrimary,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(scenario.descriptionRes),
                    color = TextSecondary,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ScenarioTag(
                        text = stringResource(scenario.riskLabelRes),
                        backgroundColor = scenario.riskLevel.backgroundColor,
                        textColor = scenario.riskLevel.textColor,
                    )
                    ScenarioTag(
                        text = stringResource(scenario.practiceType.labelRes),
                        backgroundColor = FraudDrillTagBackground,
                        textColor = ConfirmGreen,
                    )
                }
            }

            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.anti_fraud_view_detail),
                tint = TextSecondary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun ScenarioTag(
    text: String,
    backgroundColor: Color,
    textColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = backgroundColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = textColor,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

data class FraudScenario(
    val id: String,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val riskLabelRes: Int,
    val riskLevel: FraudRiskLevel,
    val practiceType: PracticeType,
    val npcTitle: String,
    val avatarText: String,
    val script: String,
    val keywords: List<String>,
    val correctActions: List<String>,
)

enum class FraudRiskLevel(
    val backgroundColor: Color,
    val textColor: Color,
) {
    High(
        backgroundColor = FraudRiskBackground,
        textColor = WarningRed,
    ),
    Medium(
        backgroundColor = FraudMediumRiskBackground,
        textColor = FraudMediumRiskText,
    ),
}

enum class PracticeType(
    @StringRes val labelRes: Int,
    @StringRes val startButtonRes: Int,
) {
    VOICE(
        labelRes = R.string.fraud_drill_voice,
        startButtonRes = R.string.anti_fraud_detail_start_voice,
    ),
    VIDEO(
        labelRes = R.string.fraud_drill_video,
        startButtonRes = R.string.anti_fraud_detail_start_video,
    ),
}

private val defaultFraudCorrectActions = listOf(
    "不透露身份证号、银行卡号、验证码",
    "不点击陌生链接",
    "不向陌生账户转账",
    "挂断后联系家人或官方客服核实",
)

private val bankFraudScript = """
    您好，我是 XX 银行风险控制中心工作人员，系统检测到您的银行卡存在异常交易。

    这笔交易涉及异地大额转账，如果不及时处理，账户可能会被冻结。

    为了帮您核验身份，请不要挂断电话，也不要告诉家人，避免影响风险处置。

    请把身份证号、银行卡号和手机验证码告诉我，我们会帮您拦截异常交易。

    如果您不配合，账户损失需要自行承担，请马上按照我的提示操作。
""".trimIndent()

private val policeFraudScript = """
    我是某市公安局民警，你名下银行卡涉嫌一起跨境洗钱案，现在需要配合调查。

    案件已经由检察院立案，调查期间必须保密，不要挂电话，也不要告诉家人。

    你现在把身份证号、银行卡号发过来，我们要核验资金来源。

    稍后会给你开通安全账户，请把账户里的钱转过去接受审查。

    如果拒不配合，可能会影响案件处理，请立即按要求完成操作。
""".trimIndent()

private val aiVideoFraudScript = """
    我这边视频信号不好，先用这个号联系你。

    家里现在急需一笔钱周转，情况比较着急，你先别问太多。

    我不方便用原来的手机号，也不方便一直开视频，说话声音可能有点不清楚。

    你先按我发的账户转过去，晚点我再给你解释。

    这件事先不要告诉别人，免得耽误处理。
""".trimIndent()

private val relativeFraudScript = """
    妈，我手机摔坏了，这是同学的号码。我现在在医院，急着交治疗费。

    现在不方便说话，医生一直在催，先转两万元到这个账户。

    千万别打我原来的号码，会耽误处理，我回家再慢慢解释。

    你转完把截图发给我，我这边马上交费。

    时间真的很紧，先帮我处理一下。
""".trimIndent()

private val fraudScenarios = listOf(
    FraudScenario(
        id = "bank",
        labelRes = R.string.fraud_scenario_bank,
        descriptionRes = R.string.fraud_scenario_bank_desc,
        riskLabelRes = R.string.fraud_risk_high,
        riskLevel = FraudRiskLevel.High,
        practiceType = PracticeType.VOICE,
        npcTitle = "XX 银行风险控制中心",
        avatarText = "银",
        script = bankFraudScript,
        keywords = listOf(
            "银行卡异常",
            "身份核验",
            "验证码",
            "转账",
            "不要挂断电话",
        ),
        correctActions = defaultFraudCorrectActions,
    ),
    FraudScenario(
        id = "police",
        labelRes = R.string.fraud_scenario_police,
        descriptionRes = R.string.fraud_scenario_police_desc,
        riskLabelRes = R.string.fraud_risk_high,
        riskLevel = FraudRiskLevel.High,
        practiceType = PracticeType.VOICE,
        npcTitle = "某市公安局反洗钱专案组",
        avatarText = "法",
        script = policeFraudScript,
        keywords = listOf(
            "涉嫌案件",
            "配合调查",
            "保密要求",
            "安全账户",
            "身份核验",
        ),
        correctActions = defaultFraudCorrectActions,
    ),
    FraudScenario(
        id = "ai_video",
        labelRes = R.string.fraud_scenario_ai_video,
        descriptionRes = R.string.fraud_scenario_ai_video_desc,
        riskLabelRes = R.string.fraud_risk_high,
        riskLevel = FraudRiskLevel.High,
        practiceType = PracticeType.VIDEO,
        npcTitle = "伪装熟人的视频来电",
        avatarText = "AI",
        script = aiVideoFraudScript,
        keywords = listOf(
            "视频信号不好",
            "换号联系",
            "急需周转",
            "先转账",
            "晚点解释",
        ),
        correctActions = listOf(
            "通过原手机号或当面核实对方身份",
            "要求对方说出只有家人知道的信息",
            "不向陌生账户转账",
            "联系其他家人共同确认",
        ),
    ),
    FraudScenario(
        id = "relative",
        labelRes = R.string.fraud_scenario_relative,
        descriptionRes = R.string.fraud_scenario_relative_desc,
        riskLabelRes = R.string.fraud_risk_medium,
        riskLevel = FraudRiskLevel.Medium,
        practiceType = PracticeType.VOICE,
        npcTitle = "冒充亲友的陌生号码",
        avatarText = "亲",
        script = relativeFraudScript,
        keywords = listOf(
            "手机坏了",
            "医院急用钱",
            "不方便说话",
            "先转账",
            "不要打原号码",
        ),
        correctActions = listOf(
            "先拨打家人原来的号码确认",
            "不要因为催促立刻转账",
            "联系其他家属共同核实",
            "保留聊天记录并提醒家人",
        ),
    ),
)

internal fun findFraudScenarioById(scenarioId: String?): FraudScenario {
    return fraudScenarios.firstOrNull { scenario ->
        scenario.id == scenarioId
    } ?: fraudScenarios.first()
}

internal fun FraudScenario.toCompactPracticeScript(): String {
    val paragraphs = script.lines()
        .map { line -> line.trim() }
        .filter { line -> line.isNotEmpty() }

    return paragraphs
        .take(2)
        .joinToString(separator = "\n\n")
        .ifBlank { script }
}

private val fraudRiskKeywords = listOf(
    R.string.anti_fraud_result_keyword_bank_card,
    R.string.anti_fraud_result_keyword_identity,
    R.string.anti_fraud_result_keyword_investigation,
    R.string.anti_fraud_result_keyword_no_hang_up,
    R.string.anti_fraud_result_keyword_transfer_code,
)

private val fraudCorrectActions = listOf(
    R.string.anti_fraud_result_action_private_info,
    R.string.anti_fraud_result_action_unknown_link,
    R.string.anti_fraud_result_action_transfer,
    R.string.anti_fraud_result_action_verify,
)

private fun String?.toRecentRiskTypeRes(): Int {
    return when (this) {
        "relative_help" -> R.string.anti_fraud_recent_risk_relative
        else -> R.string.anti_fraud_recent_risk_bank
    }
}
