package com.robote.joe.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HomeSnapshot(
    val todayReminders: Int = 0,
    val dueTodayDebts: Int = 0,
    val overdueDebts: Int = 0,
    val openBills: Int = 0,
    val shoppingItems: Int = 0,
    val totalOpenDebtAmount: Double = 0.0,
    val reminders: List<ReminderEntity> = emptyList(),
    val debts: List<DebtEntity> = emptyList(),
    val bills: List<BillEntity> = emptyList(),
    val shopping: List<ShoppingItemEntity> = emptyList()
)

class JoeRepository(
    private val dao: JoeDao
) {
    fun observeSnapshot(): Flow<HomeSnapshot> {
        return combine(
            dao.observeReminders(),
            dao.observeDebts(),
            dao.observeBills(),
            dao.observeShoppingItems()
        ) { reminders, debts, bills, shopping ->
            val today = LocalDate.now()
            val openDebts = debts.filterNot { it.isPaid }
            HomeSnapshot(
                todayReminders = reminders.count { !it.isDone && it.dueDate == today },
                dueTodayDebts = openDebts.count { it.dueDate == today },
                overdueDebts = openDebts.count { it.dueDate.isBefore(today) },
                openBills = bills.count { !it.isPaid },
                shoppingItems = shopping.count { !it.isDone },
                totalOpenDebtAmount = openDebts.sumOf { it.amount },
                reminders = reminders,
                debts = debts,
                bills = bills,
                shopping = shopping
            )
        }
    }

    suspend fun ensureSeedData() {
        if (dao.reminderCount() == 0) {
            dao.insertReminder(ReminderEntity(title = "طبيب الأسنان", dueDate = LocalDate.now(), notes = "الساعة 10 صباحًا"))
            dao.insertReminder(ReminderEntity(title = "مقابلة شركة النماء الزراعية", dueDate = LocalDate.now(), notes = "الساعة 2 ظهرًا"))
        }
        if (dao.debtCount() == 0) {
            dao.insertDebt(DebtEntity(personName = "علي في صافيتا", amount = 500.0, currency = "USD", dueDate = LocalDate.now()))
            dao.insertDebt(DebtEntity(personName = "أبو أحمد في طرطوس", amount = 200.0, currency = "USD", dueDate = LocalDate.now().minusDays(15), notes = "متأخر"))
        }
        if (dao.billCount() == 0) {
            dao.insertBill(BillEntity(vendorName = "الكيميائيات السورية", amount = 750.0, currency = "USD", billDate = LocalDate.now().minusDays(7), category = "أسمدة زراعية"))
        }
        if (dao.shoppingCount() == 0) {
            dao.insertShoppingItem(ShoppingItemEntity(itemName = "خضار", addedBy = "البيت"))
            dao.insertShoppingItem(ShoppingItemEntity(itemName = "بيض", addedBy = "البيت"))
            dao.insertShoppingItem(ShoppingItemEntity(itemName = "سكر", addedBy = "الزوجة"))
        }
    }

    suspend fun addReminder(title: String, dueDate: LocalDate, notes: String = "") {
        dao.insertReminder(ReminderEntity(title = title, dueDate = dueDate, notes = notes))
    }

    suspend fun addDebt(personName: String, amount: Double, currency: String, dueDate: LocalDate, notes: String = "") {
        dao.insertDebt(DebtEntity(personName = personName, amount = amount, currency = currency, dueDate = dueDate, notes = notes))
    }

    suspend fun addBill(vendorName: String, amount: Double, currency: String, billDate: LocalDate, category: String) {
        dao.insertBill(BillEntity(vendorName = vendorName, amount = amount, currency = currency, billDate = billDate, category = category))
    }

    suspend fun addShopping(itemName: String, addedBy: String) {
        dao.insertShoppingItem(ShoppingItemEntity(itemName = itemName, addedBy = addedBy))
    }
}

data class JoeExecutionResult(
    val reply: String,
    val source: String,
    val modeLabel: String
)

data class JoeIntentPayload(
    val intent: String,
    val personName: String = "",
    val vendorName: String = "",
    val title: String = "",
    val itemName: String = "",
    val amount: Double = 0.0,
    val currency: String = "USD",
    val dueDate: LocalDate? = null,
    val billDate: LocalDate? = null,
    val category: String = "",
    val notes: String = ""
)

sealed class JoeRemoteResult {
    data class Success(
        val payload: JoeIntentPayload,
        val reply: String,
        val provider: String,
        val modeLabel: String
    ) : JoeRemoteResult()

    data class Failure(
        val message: String,
        val modeLabel: String
    ) : JoeRemoteResult()
}

sealed class JoeCommandResult(val reply: String) {
    class Inform(reply: String) : JoeCommandResult(reply)
}

class JoeSmartAssistant(
    private val repository: JoeRepository,
    private val remoteBrain: JoeRemoteBrain,
    private val localBrain: JoeLocalBrain
) {
    suspend fun handle(text: String, snapshot: HomeSnapshot): JoeExecutionResult {
        return when (val remote = remoteBrain.handle(text, snapshot)) {
            is JoeRemoteResult.Success -> executeRemotePayload(remote, snapshot)
            is JoeRemoteResult.Failure -> {
                val local = localBrain.handle(text, snapshot)
                JoeExecutionResult(
                    reply = "${local.reply}\n\nملاحظة: تعذر الوصول إلى الذكاء السحابي، لذلك استخدمت الفهم المحلي. ${remote.message}",
                    source = "local",
                    modeLabel = remote.modeLabel
                )
            }
        }
    }

    private suspend fun executeRemotePayload(
        result: JoeRemoteResult.Success,
        snapshot: HomeSnapshot
    ): JoeExecutionResult {
        val payload = result.payload
        val reply = when (payload.intent) {
            "add_debt" -> {
                if (payload.personName.isBlank() || payload.amount <= 0.0) {
                    "فهمت أنك تريد تسجيل دين، لكنني أحتاج الاسم والمبلغ بشكل أوضح."
                } else {
                    val dueDate = payload.dueDate ?: LocalDate.now()
                    repository.addDebt(payload.personName, payload.amount, payload.currency, dueDate, payload.notes)
                    result.reply.ifBlank {
                        "تم تسجيل دين على ${payload.personName} بقيمة ${formatAmount(payload.amount)} ${payload.currency} وتاريخ استحقاق ${dueDate.formatArabic()}."
                    }
                }
            }

            "add_reminder" -> {
                if (payload.title.isBlank()) {
                    "أحتاج نص التذكير بشكل أوضح حتى أحفظه."
                } else {
                    val dueDate = payload.dueDate ?: LocalDate.now()
                    repository.addReminder(payload.title, dueDate, payload.notes)
                    result.reply.ifBlank {
                        "تم تسجيل التذكير ${payload.title} بتاريخ ${dueDate.formatArabic()}."
                    }
                }
            }

            "add_shopping_item" -> {
                if (payload.itemName.isBlank()) {
                    "فهمت أنك تريد إضافة عنصر للمشتريات، لكن اسم العنصر غير واضح."
                } else {
                    repository.addShopping(payload.itemName, "علاء")
                    result.reply.ifBlank { "تمت إضافة ${payload.itemName} إلى قائمة المشتريات." }
                }
            }

            "add_bill" -> {
                if (payload.vendorName.isBlank() || payload.amount <= 0.0) {
                    "أستطيع حفظ الفاتورة إذا كتبت اسم البائع والمبلغ والفئة بشكل أوضح."
                } else {
                    val billDate = payload.billDate ?: LocalDate.now()
                    repository.addBill(payload.vendorName, payload.amount, payload.currency, billDate, payload.category.ifBlank { "غير مصنف" })
                    result.reply.ifBlank {
                        "تم حفظ فاتورة ${payload.vendorName} بقيمة ${formatAmount(payload.amount)} ${payload.currency} تحت فئة ${payload.category.ifBlank { "غير مصنف" }}."
                    }
                }
            }

            "today_summary" -> result.reply.ifBlank { localBrain.buildTodayReply(snapshot) }
            "general_answer" -> result.reply.ifBlank { "أنا جاهز لمساعدتك." }
            else -> result.reply.ifBlank { "سمعتك يا سيدي، لكنني أحتاج صياغة أوضح قليلًا." }
        }

        return JoeExecutionResult(
            reply = reply,
            source = result.provider,
            modeLabel = result.modeLabel
        )
    }
}

class JoeRemoteBrain(
    private val baseUrl: String
) {
    suspend fun handle(text: String, snapshot: HomeSnapshot): JoeRemoteResult {
        return withContext(Dispatchers.IO) {
            val endpoint = "${baseUrl.trimEnd('/')}/interpret.php"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 40_000
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            try {
                val payload = buildPayload(text, snapshot)
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload.toString())
                }

                val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
                }.orEmpty()

                if (connection.responseCode !in 200..299) {
                    val details = body.ifBlank { "HTTP ${connection.responseCode}" }
                    return@withContext JoeRemoteResult.Failure(
                        message = "الخادم أعاد خطأ: $details",
                        modeLabel = "تعذر الاتصال بالسحابة"
                    )
                }

                val json = JSONObject(body)
                if (!json.optBoolean("ok", false)) {
                    return@withContext JoeRemoteResult.Failure(
                        message = json.optString("error", "فشل تفسير الطلب عبر OpenAI."),
                        modeLabel = "OpenAI غير متاح"
                    )
                }

                val command = json.optJSONObject("command") ?: JSONObject()
                JoeRemoteResult.Success(
                    payload = JoeIntentPayload(
                        intent = command.optString("intent", "unknown"),
                        personName = command.optString("person_name"),
                        vendorName = command.optString("vendor_name"),
                        title = command.optString("title"),
                        itemName = command.optString("item_name"),
                        amount = command.optDouble("amount", 0.0),
                        currency = command.optString("currency", "USD"),
                        dueDate = command.optString("due_date").toLocalDateOrNull(),
                        billDate = command.optString("bill_date").toLocalDateOrNull(),
                        category = command.optString("category"),
                        notes = command.optString("notes")
                    ),
                    reply = json.optString("reply"),
                    provider = json.optString("provider", "openai"),
                    modeLabel = json.optString("mode_label", "OpenAI متصل")
                )
            } catch (_: Exception) {
                JoeRemoteResult.Failure(
                    message = "تعذر الاتصال بخدمة OpenAI. تأكد من تشغيل XAMPP وضبط المفتاح والرابط.",
                    modeLabel = "وضع محلي احتياطي"
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun buildPayload(text: String, snapshot: HomeSnapshot): JSONObject {
        return JSONObject().apply {
            put("message", text)
            put("snapshot", JSONObject().apply {
                put("today_reminders", snapshot.todayReminders)
                put("due_today_debts", snapshot.dueTodayDebts)
                put("overdue_debts", snapshot.overdueDebts)
                put("open_bills", snapshot.openBills)
                put("shopping_items", snapshot.shoppingItems)
                put("total_open_debt_amount", snapshot.totalOpenDebtAmount)
                put("debts", JSONArray(snapshot.debts.take(5).map {
                    JSONObject().apply {
                        put("person_name", it.personName)
                        put("amount", it.amount)
                        put("currency", it.currency)
                        put("due_date", it.dueDate.toString())
                    }
                }))
                put("shopping", JSONArray(snapshot.shopping.take(8).map { it.itemName }))
                put("reminders", JSONArray(snapshot.reminders.take(5).map {
                    JSONObject().apply {
                        put("title", it.title)
                        put("due_date", it.dueDate.toString())
                    }
                }))
            })
        }
    }
}

class JoeLocalBrain(
    private val repository: JoeRepository
) {
    suspend fun handle(text: String, snapshot: HomeSnapshot): JoeCommandResult {
        val input = text.trim()
        val normalized = normalizeArabic(input)

        return when {
            normalized.contains("شو عندي اليوم") || normalized.contains("ماذا عندي اليوم") -> {
                JoeCommandResult.Inform(buildTodayReply(snapshot))
            }

            normalized.contains("شو صار معي اليوم") || normalized.contains("ملخص اليوم") -> {
                JoeCommandResult.Inform(buildSummaryReply(snapshot))
            }

            normalized.contains("سجل") && normalized.contains("دين") -> {
                parseDebtCommand(input)?.let { parsed ->
                    repository.addDebt(parsed.personName, parsed.amount, parsed.currency, parsed.dueDate, parsed.notes)
                    JoeCommandResult.Inform(
                        "تم تسجيل دين على ${parsed.personName} بقيمة ${formatAmount(parsed.amount)} ${parsed.currency} وتاريخ استحقاق ${parsed.dueDate.formatArabic()}."
                    )
                } ?: JoeCommandResult.Inform("فهمت أنك تريد تسجيل دين، لكنني أحتاج الاسم والمبلغ بشكل أوضح.")
            }

            (normalized.contains("سجل") || normalized.contains("اضف") || normalized.contains("أضف")) &&
                (normalized.contains("تذكير") || normalized.contains("ذكرني")) -> {
                parseReminderCommand(input)?.let { parsed ->
                    repository.addReminder(parsed.title, parsed.dueDate, parsed.notes)
                    JoeCommandResult.Inform("تم تسجيل التذكير ${parsed.title} بتاريخ ${parsed.dueDate.formatArabic()}.")
                } ?: JoeCommandResult.Inform("أحتاج نص التذكير بشكل أوضح حتى أحفظه.")
            }

            (normalized.contains("اضف") || normalized.contains("أضف") || normalized.contains("سجل")) &&
                (normalized.contains("مشتريات") || normalized.contains("القائمة")) -> {
                parseShoppingCommand(input)?.let { item ->
                    repository.addShopping(item, "علاء")
                    JoeCommandResult.Inform("تمت إضافة $item إلى قائمة المشتريات.")
                } ?: JoeCommandResult.Inform("فهمت أنك تريد إضافة عنصر للمشتريات، لكن اسم العنصر غير واضح.")
            }

            normalized.contains("فاتوره") || normalized.contains("فاتورة") -> {
                parseBillCommand(input)?.let { parsed ->
                    repository.addBill(parsed.vendorName, parsed.amount, parsed.currency, parsed.billDate, parsed.category)
                    JoeCommandResult.Inform(
                        "تم حفظ فاتورة ${parsed.vendorName} بقيمة ${formatAmount(parsed.amount)} ${parsed.currency} تحت فئة ${parsed.category}."
                    )
                } ?: JoeCommandResult.Inform("أستطيع حفظ الفاتورة إذا كتبت اسم البائع والمبلغ والفئة بشكل أوضح.")
            }

            normalized.contains("وضع الضيوف") || normalized.contains("الخصوصيه") || normalized.contains("الخصوصية") -> {
                JoeCommandResult.Inform("تم فهم وضع الخصوصية. في النسخة القادمة سأحوّله إلى وضع صامت فعلي مع كلمة سر بديلة.")
            }

            normalized.contains("سعر") && normalized.contains("الدولار") -> {
                JoeCommandResult.Inform("هذا النوع من الأسئلة يحتاج اتصالًا بالإنترنت ومصدر أسعار مباشر. في النسخة المتقدمة سأطلب إذنك قبل الاتصال.")
            }

            else -> JoeCommandResult.Inform(
                "سمعتك يا سيدي. أستطيع الآن إدارة يومك محليًا: ملخص اليوم، التذكيرات، الديون، الفواتير، والمشتريات."
            )
        }
    }

    fun buildTodayReply(snapshot: HomeSnapshot): String {
        val today = LocalDate.now()
        val dueToday = snapshot.debts.filter { !it.isPaid && it.dueDate == today }
        val overdue = snapshot.debts.filter { !it.isPaid && it.dueDate.isBefore(today) }
        val items = snapshot.shopping.take(5).joinToString("، ") { it.itemName }
        return buildString {
            append("سيدي، اليوم لديك ${snapshot.todayReminders} تذكيرات، و${snapshot.dueTodayDebts} ديون مستحقة اليوم، و${snapshot.overdueDebts} ديون متأخرة.")
            if (dueToday.isNotEmpty() || overdue.isNotEmpty()) {
                append(" أبرز الديون: ")
                append((overdue + dueToday).take(3).joinToString("، ") {
                    "${it.personName} ${formatAmount(it.amount)} ${it.currency}"
                })
                append(".")
            }
            if (items.isNotBlank()) {
                append(" وقائمة البيت الحالية: $items.")
            }
        }
    }

    private fun buildSummaryReply(snapshot: HomeSnapshot): String {
        return "ملخص اليوم: لديك ${snapshot.reminders.size} تذكيرات، ${snapshot.debts.count { !it.isPaid }} ديون مفتوحة، ${snapshot.openBills} فواتير غير مدفوعة، و${snapshot.shoppingItems} عناصر مشتريات."
    }
}

data class ParsedDebtCommand(
    val personName: String,
    val amount: Double,
    val currency: String,
    val dueDate: LocalDate,
    val notes: String = ""
)

data class ParsedReminderCommand(
    val title: String,
    val dueDate: LocalDate,
    val notes: String = ""
)

data class ParsedBillCommand(
    val vendorName: String,
    val amount: Double,
    val currency: String,
    val billDate: LocalDate,
    val category: String
)

fun normalizeArabic(text: String): String {
    return text.trim()
        .replace("أ", "ا")
        .replace("إ", "ا")
        .replace("آ", "ا")
        .replace("ى", "ي")
        .replace("ة", "ه")
        .lowercase()
}

private fun parseDebtCommand(text: String): ParsedDebtCommand? {
    val amountMatch = Regex("(\\d+(?:\\.\\d+)?)").find(text) ?: return null
    val amount = amountMatch.groupValues[1].toDoubleOrNull() ?: return null
    val personMatch = Regex("(?:ل|على|لـ)\\s+(.+?)\\s+(?:ب|بـ)?\\s*\\d").find(text)
        ?: Regex("دين\\s+(.+?)\\s+(?:ب|بـ)?\\s*\\d").find(text)
        ?: return null
    return ParsedDebtCommand(
        personName = personMatch.groupValues[1].trim(' ', '،', '.'),
        amount = amount,
        currency = if (text.contains("ليرة")) "SYP" else "USD",
        dueDate = extractRelativeDate(text)
    )
}

private fun parseReminderCommand(text: String): ParsedReminderCommand? {
    val cleaned = text
        .replace("سجل تذكير", "")
        .replace("اضف تذكير", "")
        .replace("أضف تذكير", "")
        .replace("ذكرني", "")
        .trim()
    if (cleaned.isBlank()) return null
    return ParsedReminderCommand(
        title = cleaned.trim(' ', '،', '.'),
        dueDate = extractRelativeDate(text)
    )
}

private fun parseShoppingCommand(text: String): String? {
    return Regex("(?:اضف|أضف|سجل)\\s+(.+?)\\s+(?:الى|إلى)?\\s*(?:المشتريات|القائمه|القائمة)")
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim(' ', '،', '.')
}

private fun parseBillCommand(text: String): ParsedBillCommand? {
    val amountMatch = Regex("(\\d+(?:\\.\\d+)?)").find(text) ?: return null
    val vendorMatch = Regex("(?:على|من)\\s+(.+?)\\s+\\d").find(text)
        ?: Regex("فاتورة\\s+(.+?)\\s+\\d").find(text)
        ?: return null
    val category = Regex("فئه\\s+(.+)$|فئة\\s+(.+)$").find(text)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() } ?: "غير مصنف"
    return ParsedBillCommand(
        vendorName = vendorMatch.groupValues[1].trim(' ', '،', '.'),
        amount = amountMatch.groupValues[1].toDoubleOrNull() ?: return null,
        currency = if (text.contains("ليرة")) "SYP" else "USD",
        billDate = extractRelativeDate(text),
        category = category.trim(' ', '،', '.')
    )
}

fun extractRelativeDate(text: String): LocalDate {
    val today = LocalDate.now()
    val normalized = normalizeArabic(text)
    return when {
        Regex("\\d{4}-\\d{2}-\\d{2}").find(text) != null -> LocalDate.parse(Regex("\\d{4}-\\d{2}-\\d{2}").find(text)!!.value)
        normalized.contains("بعد شهرين") -> today.plusDays(60)
        normalized.contains("بعد شهر") -> today.plusDays(30)
        normalized.contains("الاسبوع القادم") || normalized.contains("الأسبوع القادم") || normalized.contains("بعد اسبوع") || normalized.contains("بعد أسبوع") -> today.plusDays(7)
        normalized.contains("غدا") || normalized.contains("بكرا") -> today.plusDays(1)
        else -> today
    }
}

fun LocalDate.formatArabic(): String = format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ar")))

fun formatAmount(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(value)

private fun String?.toLocalDateOrNull(): LocalDate? {
    if (this.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(this) }.getOrNull()
}
