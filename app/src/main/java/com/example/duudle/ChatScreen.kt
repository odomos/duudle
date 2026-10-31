package com.example.duudle

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.duudle.ui.theme.DuudleColors
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.time.ZoneId

private val InputShape = RoundedCornerShape(22.dp)
// Same warm grey as the Friends search field, so the input reads on Paper.
private val InputGrey = Color(0xFFEBE8E3)
private val PlaceholderInk = DuudleColors.Ink.copy(alpha = 0.65f)
private const val PREF_ASKED_NOTIFICATIONS = "asked_chat_notification_permission"

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    currentUid: String,
    currentUsername: String,
    friendUid: String,
    /** Null or blank: look it up from users/{friendUid} (e.g. opened from a notification). */
    friendUsername: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val chatId = remember(currentUid, friendUid) { chatIdFor(currentUid, friendUid) }

    // The name always comes from Firestore when it isn't passed in: a notification intent's
    // name extra is never trusted for display. "Chat" shows until the lookup finishes.
    var resolvedName by remember { mutableStateOf(friendUsername?.takeIf { it.isNotBlank() }) }
    var nameLookupDone by remember { mutableStateOf(resolvedName != null) }
    LaunchedEffect(friendUid) {
        if (resolvedName != null) return@LaunchedEffect
        ChatRepository.loadUsername(friendUid) { name ->
            resolvedName = name
            nameLookupDone = true
        }
    }
    val friendName = resolvedName ?: "friend"
    val headerTitle = when {
        resolvedName != null -> "@$resolvedName"
        nameLookupDone -> "@friend"
        else -> "Chat"
    }

    var chat by remember { mutableStateOf<ChatSummary?>(null) }
    // Every message seen so far, by id. The live listener only covers the newest page, so
    // messages that scroll out of it (and older pages) are kept here; messages are never
    // deleted, so accumulating is safe.
    var messagesById by remember { mutableStateOf<Map<String, ChatMessage>>(emptyMap()) }
    var oldestCursor by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var oldestCursorAt by remember { mutableStateOf<Long?>(null) }
    var hasOlder by remember { mutableStateOf(false) }
    var loadingOlder by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }
    var retryKey by remember { mutableIntStateOf(0) }
    var draft by rememberSaveable { mutableStateOf("") }
    var showThemePicker by remember { mutableStateOf(false) }
    val safety = rememberSafetyActions()
    // Blocked: the chat stays closed (the rules refuse new messages either way).
    val isBlocked = friendUid in Blocks.state.collectAsState().value

    val theme = chatThemeFor(chat?.themeId ?: ChatThemeId.CLASSIC)

    DisposableEffect(chatId, retryKey) {
        loadError = false
        val chatListener = ChatRepository.listenToChat(chatId, onUpdate = { chat = it }, onError = { })
        val messagesListener = ChatRepository.listenToLatestMessages(
            chatId,
            onPage = { page ->
                messagesById = messagesById + page.messages.associateBy { it.id }
                // The cursor only ever moves back in time: once new messages push the
                // newest page forward, the older ones stay in messagesById.
                val pageOldest = page.oldestAtMillis
                if (page.oldestDoc != null && pageOldest != null && (oldestCursorAt == null || pageOldest < oldestCursorAt!!)) {
                    oldestCursor = page.oldestDoc
                    oldestCursorAt = pageOldest
                    hasOlder = page.full
                }
                loaded = true
            },
            onError = { loadError = true }
        )
        onDispose {
            chatListener.remove()
            messagesListener.remove()
        }
    }

    fun loadOlder() {
        val cursor = oldestCursor ?: return
        if (loadingOlder || !hasOlder) return
        loadingOlder = true
        ChatRepository.loadOlder(
            chatId, cursor,
            onResult = { page ->
                // Listener versions win over the fetched copies of the same message.
                messagesById = page.messages.associateBy { it.id } + messagesById
                if (page.oldestDoc != null) {
                    oldestCursor = page.oldestDoc
                    oldestCursorAt = page.oldestAtMillis
                }
                hasOlder = page.full
                loadingOlder = false
            },
            onError = {
                loadingOlder = false
                Toast.makeText(context, "Couldn't load older messages", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Presence: while this chat is started, its pushes don't post notifications, and any
    // notification already in the shade for it is cleared.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, chatId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    ChatPresence.openChatId = chatId
                    NotificationManagerCompat.from(context).cancel(chatNotificationId(chatId))
                }
                Lifecycle.Event.ON_STOP -> if (ChatPresence.openChatId == chatId) ChatPresence.openChatId = null
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (ChatPresence.openChatId == chatId) ChatPresence.openChatId = null
        }
    }

    // Ask for notification permission (Android 13+) the first time any chat opens.
    // Asked once: if it's denied we don't nag.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val prefs = context.getSharedPreferences("duudle_prefs", Context.MODE_PRIVATE)
        if (!granted && !prefs.getBoolean(PREF_ASKED_NOTIFICATIONS, false)) {
            prefs.edit().putBoolean(PREF_ASKED_NOTIFICATIONS, true).apply()
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Mark read while the chat is visible and there's something unread. Keyed on the
    // unread flag, so it writes once per new message rather than on every recomposition;
    // the short delay batches a burst of messages into one write.
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val isStarted = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val unread = chat?.hasUnreadFor(currentUid) == true
    val lastAt = chat?.lastAtMillis
    LaunchedEffect(unread, lastAt, isStarted) {
        if (!unread || !isStarted) return@LaunchedEffect
        delay(600)
        ChatRepository.markRead(chatId, currentUid)
    }

    val ordered = remember(messagesById) { orderChatMessages(messagesById.values) }
    val zone = remember { ZoneId.systemDefault() }
    val locale = LocalConfiguration.current.locales[0]
    val use24Hour = remember { android.text.format.DateFormat.is24HourFormat(context) }
    // Newest first, for the bottom-anchored (reversed) list.
    val rows = remember(ordered, currentUid, locale) {
        buildChatRows(ordered, currentUid, System.currentTimeMillis(), zone, locale).asReversed()
    }

    val listState = rememberLazyListState()
    // A new newest message: follow it if it's mine or the reader was already at the bottom.
    // Without this, the list keeps the previous message in place and the new one lands
    // just out of view.
    val newest = ordered.lastOrNull()
    LaunchedEffect(newest?.id) {
        if (newest == null) return@LaunchedEffect
        if (newest.senderId == currentUid || listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }
    // Scrolled near the top (the end of the reversed list): fetch the next older page.
    // Keyed on the item count, so a page that still leaves us at the top fetches again.
    LaunchedEffect(listState, hasOlder) {
        if (!hasOlder) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            if (info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3) info.totalItemsCount else -1
        }
            .distinctUntilChanged()
            .filter { it > 0 }
            .collect { loadOlder() }
    }

    fun send() {
        val check = checkChatDraft(draft)
        if (check !is ChatDraftCheck.Ok) return
        draft = ""
        ChatRepository.sendText(chatId, currentUid, friendUid, check.text) {
            Toast.makeText(context, "Couldn't send your message", Toast.LENGTH_SHORT).show()
        }
    }

    DuudlePage(
        // Lift the input above the keyboard; the Scaffold padding already covers the nav bar.
        modifier = modifier.windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
    ) {
        ChatHeader(
            friendUid = friendUid,
            title = headerTitle,
            themeName = theme.id.displayName,
            onBack = onBack,
            onThemeClick = { showThemePicker = true },
            onReport = { safety.report(ReportSubject.chat(friendUid, friendName, chatId)) },
            onBlock = { safety.block(friendUid, friendName) },
            friendName = friendName,
            showMenu = !isBlocked
        )
        HorizontalDivider(color = DuudleColors.Hairline)

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .chatBackground(theme)
        ) {
            val maxBubbleWidth = maxWidth * 0.78f
            when {
                isBlocked -> Text(
                    "You blocked @$friendName. Unblock them in Profile to chat again.",
                    color = theme.noteText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(theme.noteFill)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
                loadError && ordered.isEmpty() -> ChatErrorState(theme = theme, onRetry = { retryKey++ })
                !loaded -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                    color = theme.meta,
                    strokeWidth = 2.5.dp
                )
                ordered.isEmpty() -> Text(
                    "Say hi to @$friendName",
                    color = theme.noteText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(theme.noteFill)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
                else -> LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
                ) {
                    items(rows, key = { it.key }, contentType = { it::class }) { row ->
                        when (row) {
                            is ChatRow.DaySeparator -> ChatCentredNote(theme, row.label, Modifier.padding(vertical = 10.dp))
                            is ChatRow.Note -> {
                                val byMe = row.message.senderId == currentUid
                                ChatCentredNote(
                                    theme,
                                    themeChangeNote(byMe, if (byMe) currentUsername else friendName, row.message.text),
                                    Modifier.padding(vertical = 6.dp)
                                )
                            }
                            is ChatRow.Bubble -> {
                                val at = row.message.createdAtMillis
                                val time = at?.let { formatChatTime(it, zone, use24Hour, locale) }
                                ChatBubbleRow(
                                    row = row,
                                    theme = theme,
                                    friendUsername = friendName,
                                    timeLabel = time,
                                    maxBubbleWidth = maxBubbleWidth
                                )
                            }
                        }
                    }
                    if (loadingOlder) {
                        item(key = "loading-older") {
                            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(20.dp), color = theme.meta, strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
            // The listener died but we still have messages: keep them and offer a retry.
            if (loadError && ordered.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(DuudleColors.Paper)
                        .padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Chat stopped updating", color = DuudleColors.ErrorRed, fontSize = 13.sp)
                    TextButton(onClick = { retryKey++ }) { Text("Retry", color = DuudleColors.Ink, fontWeight = FontWeight.Bold) }
                }
            }
        }

        if (!isBlocked) {
            HorizontalDivider(color = DuudleColors.Hairline)
            ChatInputBar(
                draft = draft,
                onDraftChange = { draft = it.take(MAX_CHAT_MESSAGE_LENGTH) },
                friendUsername = friendName,
                onSend = { send() }
            )
        }
    }
    SafetyDialogs(safety, currentUid, onBlocked = { onBack() })

    if (showThemePicker) {
        ChatThemePickerSheet(
            current = theme.id,
            onPick = { picked ->
                showThemePicker = false
                if (picked != theme.id) {
                    ChatRepository.changeTheme(chatId, currentUid, friendUid, picked) {
                        Toast.makeText(context, "Couldn't change the theme", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showThemePicker = false }
        )
    }
}

@Composable
private fun ChatHeader(
    friendUid: String,
    title: String,
    themeName: String,
    onBack: () -> Unit,
    onThemeClick: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    friendName: String,
    showMenu: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = DuudleColors.Ink)
        }
        DuudleAvatar(uid = friendUid, size = 36.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 19.sp,
            color = DuudleColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        IconButton(
            onClick = onThemeClick,
            modifier = Modifier.semantics { contentDescription = "Chat theme: $themeName. Change theme" }
        ) {
            PaletteIcon(color = DuudleColors.Ink, modifier = Modifier.size(24.dp))
        }
        if (showMenu) SafetyMenuButton(username = friendName, onReport = onReport, onBlock = onBlock)
    }
}

/** A hand-drawn paint palette: a blob with a thumb hole and three paint dots. */
@Composable
private fun PaletteIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val s = size.minDimension / 24f
        val outline = Path().apply {
            moveTo(12 * s, 2.5f * s)
            cubicTo(5.5f * s, 2.5f * s, 2.5f * s, 7.5f * s, 2.5f * s, 12 * s)
            cubicTo(2.5f * s, 17.5f * s, 7 * s, 21.5f * s, 12 * s, 21.5f * s)
            cubicTo(13.8f * s, 21.5f * s, 14.2f * s, 20 * s, 13.4f * s, 18.6f * s)
            cubicTo(12.6f * s, 17.2f * s, 13.4f * s, 15.6f * s, 15.2f * s, 15.6f * s)
            lineTo(17.5f * s, 15.6f * s)
            cubicTo(20 * s, 15.6f * s, 21.5f * s, 13.8f * s, 21.5f * s, 11.4f * s)
            cubicTo(21.5f * s, 6.4f * s, 17.2f * s, 2.5f * s, 12 * s, 2.5f * s)
            close()
        }
        drawPath(outline, color, style = Stroke(width = 1.9f * s, cap = StrokeCap.Round))
        drawCircle(color, 1.5f * s, Offset(7.5f * s, 12 * s))
        drawCircle(color, 1.5f * s, Offset(9.5f * s, 7.5f * s))
        drawCircle(color, 1.5f * s, Offset(14.8f * s, 7.5f * s))
    }
}

/** A day separator or a theme-change note: a small centred pill. */
@Composable
private fun ChatCentredNote(theme: ChatTheme, text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = theme.noteText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(theme.noteFill)
                .padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun ChatBubbleRow(
    row: ChatRow.Bubble,
    theme: ChatTheme,
    friendUsername: String,
    timeLabel: String?,
    maxBubbleWidth: Dp
) {
    val mine = row.mine
    val message = row.message
    val style = if (mine) theme.mine else theme.theirs
    val label = chatBubbleLabel(mine, friendUsername, message.text, timeLabel, message.pending)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = if (row.firstInGroup) 8.dp else 2.dp)
            // One TalkBack stop per bubble: "You: hey, 4:05 PM".
            .clearAndSetSemantics { contentDescription = label },
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        if (!mine && row.firstInGroup) {
            ChatNameTag(theme = theme, name = "@$friendUsername", modifier = Modifier.widthIn(max = maxBubbleWidth))
        }
        Box(
            modifier = Modifier
                .widthIn(max = maxBubbleWidth)
                .chatBubble(theme, mine, chatBubbleShape(theme, mine, row.firstInGroup, row.lastInGroup))
                .padding(horizontal = BubbleHorizontalPadding, vertical = BubbleVerticalPadding)
        ) {
            Text(message.text, color = style.text, fontSize = 16.sp, lineHeight = 21.sp)
        }
        if (row.lastInGroup || message.pending) {
            Row(
                modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (row.lastInGroup && timeLabel != null) {
                    Text(timeLabel, color = theme.meta, fontSize = 11.sp)
                }
                if (message.pending) {
                    Spacer(Modifier.width(4.dp))
                    SendingClock(color = theme.meta, modifier = Modifier.size(11.dp))
                }
            }
        }
    }
}

/** The small "sending" clock on a message that hasn't reached the server yet. */
@Composable
private fun SendingClock(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        val c = Offset(s / 2f, s / 2f)
        drawCircle(color, s * 0.42f, c, style = Stroke(width = s * 0.1f))
        drawLine(color, c, Offset(c.x, s * 0.22f), strokeWidth = s * 0.1f, cap = StrokeCap.Round)
        drawLine(color, c, Offset(s * 0.7f, c.y), strokeWidth = s * 0.1f, cap = StrokeCap.Round)
    }
}

@Composable
private fun BoxScope.ChatErrorState(theme: ChatTheme, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(32.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(DuudleColors.Paper)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Couldn't load this chat.", color = DuudleColors.ErrorRed, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        DuudleInkPill(text = "Retry", onClick = onRetry)
    }
}

@Composable
private fun ChatInputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    friendUsername: String,
    onSend: () -> Unit
) {
    val canSend = checkChatDraft(draft) is ChatDraftCheck.Ok
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DuudleColors.Paper)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                maxLines = 5,
                textStyle = TextStyle(fontSize = 16.sp, color = DuudleColors.Ink, lineHeight = 21.sp),
                cursorBrush = SolidColor(DuudleColors.Ink),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Message @$friendUsername" },
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(InputShape)
                            .background(InputGrey)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (draft.isEmpty()) {
                            Text(
                                "Message @$friendUsername",
                                fontSize = 16.sp,
                                color = PlaceholderInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (draft.length >= CHAT_COUNTER_THRESHOLD) {
                Text(
                    "${draft.length}/$MAX_CHAT_MESSAGE_LENGTH",
                    color = if (draft.length >= MAX_CHAT_MESSAGE_LENGTH) DuudleColors.ErrorRed else DuudleColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp, end = 8.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (canSend) DuudleColors.Ink else DuudleColors.Ink.copy(alpha = 0.2f))
                .clickable(enabled = canSend, role = Role.Button, onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send message",
                tint = DuudleColors.Paper,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatThemePickerSheet(
    current: ChatThemeId,
    onPick: (ChatThemeId) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DuudleColors.Cream) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            Text(
                "Chat theme",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = DuudleColors.Ink,
                modifier = Modifier.semantics { heading() }
            )
            Text("You both see the theme you pick.", fontSize = 14.sp, color = DuudleColors.Muted)
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AllChatThemes.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { theme ->
                            ChatThemeTile(
                                theme = theme,
                                selected = theme.id == current,
                                onClick = { onPick(theme.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** A theme preview: a mini received and sent bubble on the theme's background. */
@Composable
private fun ChatThemeTile(theme: ChatTheme, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(DuudleColors.Paper)
            .border(if (selected) 2.dp else 1.dp, if (selected) DuudleColors.Ink else DuudleColors.Hairline, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .clip(RoundedCornerShape(12.dp))
                .chatBackground(theme)
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .clearAndSetSemantics { },
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            MiniBubble(theme, mine = false, text = "hey!", modifier = Modifier.align(Alignment.Start))
            MiniBubble(theme, mine = true, text = "hiii :)", modifier = Modifier.align(Alignment.End))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .padding(start = 4.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                theme.id.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = DuudleColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = DuudleColors.Ink, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun MiniBubble(theme: ChatTheme, mine: Boolean, text: String, modifier: Modifier = Modifier) {
    val style = if (mine) theme.mine else theme.theirs
    Box(
        modifier = modifier
            .chatBubble(theme, mine, chatBubbleShape(theme, mine, firstInGroup = true, lastInGroup = true))
            .padding(horizontal = BubbleHorizontalPadding, vertical = 7.dp)
    ) {
        Text(text, color = style.text, fontSize = 13.sp, maxLines = 1)
    }
}
