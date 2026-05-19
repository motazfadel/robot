package com.robote.joe.mobile

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private val viewModel by viewModels<JoeViewModel> { JoeViewModel.factory(application) }

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val transcript = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()

        if (transcript.isNotEmpty()) {
            viewModel.handleUserMessage(transcript, ::speak)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tts = TextToSpeech(this, this)

        setContent {
            JoeApp(
                viewModel = viewModel,
                onStartVoice = ::startVoiceRecognition
            )
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("ar")
        }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث إلى جو")
        }
        voiceLauncher.launch(intent)
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "joe_mobile_reply")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun JoeApp(
    viewModel: JoeViewModel,
    onStartVoice: () -> Unit
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()

    MaterialTheme {
        JoeHomeScreen(
            snapshot = snapshot,
            conversation = conversation,
            onSendMessage = { text -> viewModel.handleUserMessage(text) },
            onStartVoice = onStartVoice
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JoeHomeScreen(
    snapshot: HomeSnapshot,
    conversation: List<ConversationMessage>,
    onSendMessage: (String) -> Unit,
    onStartVoice: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val quickActions = remember {
        listOf(
            "شو عندي اليوم؟",
            "ملخص اليوم",
            "سجل دين على أبو رامي 300 دولار بعد شهر",
            "أضف سكر إلى المشتريات",
            "سجل تذكير زيارة الطبيب غدا"
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("جو", fontWeight = FontWeight.Bold)
                        Text("رفيق علاء اليومي", style = MaterialTheme.typography.labelMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFE6F0E8)
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F5F0))
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                HeroSection(snapshot = snapshot, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }

            item {
                SnapshotGrid(snapshot = snapshot, modifier = Modifier.padding(horizontal = 16.dp))
            }

            item {
                QuickActionRow(actions = quickActions, onPick = {
                    input = it
                    onSendMessage(it)
                }, modifier = Modifier.padding(horizontal = 16.dp))
            }

            item {
                CommandComposer(
                    value = input,
                    onValueChange = { input = it },
                    onSend = {
                        val message = input.trim()
                        if (message.isNotEmpty()) {
                            onSendMessage(message)
                            input = ""
                        }
                    },
                    onStartVoice = onStartVoice,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            item {
                SectionTitle("لوحة اليوم", Modifier.padding(horizontal = 16.dp))
            }

            item {
                TodayBoard(snapshot = snapshot, modifier = Modifier.padding(horizontal = 16.dp))
            }

            item {
                SectionTitle("المحادثة التنفيذية", Modifier.padding(horizontal = 16.dp))
            }

            items(conversation) { message ->
                ConversationCard(message = message, modifier = Modifier.padding(horizontal = 16.dp))
            }

            item {
                Box(modifier = Modifier.heightIn(min = 24.dp))
            }
        }
    }
}

@Composable
private fun HeroSection(
    snapshot: HomeSnapshot,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF264653), Color(0xFF4C956C), Color(0xFFF4A261))
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "مركز قيادة علاء",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "اليوم لديك ${snapshot.todayReminders} تذكيرات، ${snapshot.overdueDebts} ديون متأخرة، و${snapshot.shoppingItems} عناصر بيت تحتاج متابعة.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.92f)
                )
            }
        }
    }
}

@Composable
private fun SnapshotGrid(
    snapshot: HomeSnapshot,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SnapshotTile("تذكيرات اليوم", snapshot.todayReminders.toString(), Color(0xFFD9ED92), Modifier.weight(1f))
            SnapshotTile("ديون اليوم", snapshot.dueTodayDebts.toString(), Color(0xFFFFD166), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SnapshotTile("المتأخرات", snapshot.overdueDebts.toString(), Color(0xFFF4978E), Modifier.weight(1f))
            SnapshotTile("فواتير مفتوحة", snapshot.openBills.toString(), Color(0xFFA9DEF9), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SnapshotTile(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActionRow(
    actions: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("أوامر سريعة")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            actions.forEach { action ->
                AssistChip(onClick = { onPick(action) }, label = { Text(action) })
            }
        }
    }
}

@Composable
private fun CommandComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStartVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("أرسل أمرًا طبيعيًا كما يتكلم علاء", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                label = { Text("مثال: سجل دين على أبو رامي 300 دولار بعد شهر") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onSend, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Send, contentDescription = null)
                    Box(modifier = Modifier.width(8.dp))
                    Text("تنفيذ")
                }
                Button(onClick = onStartVoice, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Mic, contentDescription = null)
                    Box(modifier = Modifier.width(8.dp))
                    Text("صوت")
                }
            }
        }
    }
}

@Composable
private fun TodayBoard(
    snapshot: HomeSnapshot,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InsightSection(
            title = "الديون ذات الأولوية",
            rows = snapshot.debts
                .filterNot { it.isPaid }
                .sortedWith(compareBy<DebtEntity> { !it.dueDate.isBefore(java.time.LocalDate.now()) }.thenBy { it.dueDate })
                .take(3)
                .map {
                    val badge = if (it.dueDate.isBefore(java.time.LocalDate.now())) "متأخر" else if (it.dueDate == java.time.LocalDate.now()) "اليوم" else "لاحقًا"
                    "${it.personName} • ${formatAmount(it.amount)} ${it.currency} • $badge"
                }
        )
        InsightSection(
            title = "تذكيرات اليوم",
            rows = snapshot.reminders.take(3).map { "${it.title} • ${it.dueDate.formatArabic()}" }
        )
        InsightSection(
            title = "مشتريات البيت",
            rows = snapshot.shopping.take(5).map { "${it.itemName} • أضافه ${it.addedBy.ifBlank { "البيت" }}" }
        )
    }
}

@Composable
private fun InsightSection(
    title: String,
    rows: List<String>
) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (rows.isEmpty()) {
                Text("لا توجد بيانات بعد.", color = Color.Gray)
            } else {
                rows.forEachIndexed { index, row ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4C956C))
                            )
                            Text(row, style = MaterialTheme.typography.bodyLarge)
                        }
                        if (index != rows.lastIndex) {
                            Divider(color = Color(0xFFE9ECEF))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationCard(
    message: ConversationMessage,
    modifier: Modifier = Modifier
) {
    val isJoe = message.sender == "جو"
    val containerColor = if (isJoe) Color(0xFFE3F2E8) else Color.White
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = message.sender,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isJoe) Color(0xFF2D6A4F) else Color(0xFF6C757D)
            )
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF1F2937)
    )
}
