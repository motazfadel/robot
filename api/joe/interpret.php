<?php

declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode([
        'ok' => false,
        'error' => 'Only POST is allowed.',
    ], JSON_UNESCAPED_UNICODE);
    exit;
}

function loadEnvFile(string $path): void
{
    if (!is_file($path)) {
        return;
    }

    $lines = file($path, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
    if ($lines === false) {
        return;
    }

    foreach ($lines as $line) {
        $trimmed = trim($line);
        if ($trimmed === '' || str_starts_with($trimmed, '#') || !str_contains($trimmed, '=')) {
            continue;
        }

        [$key, $value] = explode('=', $trimmed, 2);
        $key = trim($key);
        if ($key === '' || getenv($key) !== false) {
            continue;
        }

        $value = trim($value, " \t\n\r\0\x0B\"'");
        putenv($key . '=' . $value);
        $_ENV[$key] = $value;
        $_SERVER[$key] = $value;
    }
}

function envValue(string $key, string $default = ''): string
{
    $value = getenv($key);
    if ($value === false || $value === '') {
        return $default;
    }

    return $value;
}

function jsonResponse(int $status, array $data): void
{
    http_response_code($status);
    echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

$root = dirname(__DIR__, 2);
loadEnvFile($root . DIRECTORY_SEPARATOR . '.env');
loadEnvFile($root . DIRECTORY_SEPARATOR . '.env.example');
loadEnvFile(dirname(__DIR__) . DIRECTORY_SEPARATOR . '.env');

$apiKey = envValue('OPENAI_API_KEY');
$model = envValue('JOE_OPENAI_MODEL', 'gpt-5.4-mini');
$endpoint = envValue('JOE_OPENAI_URL', 'https://api.openai.com/v1/responses');

if ($apiKey === '' || $apiKey === 'replace-with-your-openai-api-key') {
    jsonResponse(500, [
        'ok' => false,
        'error' => 'OPENAI_API_KEY is missing on the server.',
        'mode_label' => 'OpenAI غير مضبوط',
    ]);
}

$rawBody = file_get_contents('php://input');
if ($rawBody === false || trim($rawBody) === '') {
    jsonResponse(400, [
        'ok' => false,
        'error' => 'Request body is required.',
    ]);
}

$payload = json_decode($rawBody, true);
if (!is_array($payload)) {
    jsonResponse(400, [
        'ok' => false,
        'error' => 'Invalid JSON body.',
    ]);
}

$message = trim((string)($payload['message'] ?? ''));
$snapshot = $payload['snapshot'] ?? [];

if ($message === '') {
    jsonResponse(400, [
        'ok' => false,
        'error' => 'Message is required.',
    ]);
}

$today = date('Y-m-d');
$snapshotJson = json_encode($snapshot, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);

$prompt = <<<PROMPT
أنت "جو"، مساعد إداري عربي لتطبيق موبايل.
تاريخ اليوم هو {$today}.
مهمتك أن تفهم رسالة المستخدم ثم تعيد JSON فقط بدون أي شرح خارجي.

أعد كائن JSON بهذه الصيغة:
{
  "intent": "add_debt|add_bill|add_reminder|add_shopping_item|today_summary|general_answer|unknown",
  "person_name": "",
  "vendor_name": "",
  "title": "",
  "item_name": "",
  "amount": 0,
  "currency": "USD",
  "due_date": "",
  "bill_date": "",
  "category": "",
  "notes": "",
  "reply": ""
}

قواعد مهمة:
- إذا كان الطلب عن دين فاجعل intent = add_debt
- إذا كان الطلب عن فاتورة فاجعل intent = add_bill
- إذا كان الطلب عن تذكير فاجعل intent = add_reminder
- إذا كان الطلب عن مشتريات فاجعل intent = add_shopping_item
- إذا كان الطلب يطلب ملخصًا أو ماذا عندي اليوم فاجعل intent = today_summary
- إذا كان سؤالًا عامًا أو استشارة عادية فاجعل intent = general_answer
- إذا لم تفهم فاجعل intent = unknown
- استعمل YYYY-MM-DD للتواريخ عند الإمكان
- العملة USD للدولار و SYP لليرة
- reply يجب أن يكون ردًا عربيًا طبيعيًا قصيرًا جاهزًا للعرض للمستخدم
- إذا كان الطلب تنفيذياً فليكن reply بصيغة تؤكد ما سيتم أو ما تم فهمه
- إذا كان الطلب ملخصًا فاستفد من بيانات snapshot

بيانات الحالة الحالية:
{$snapshotJson}

رسالة المستخدم:
{$message}
PROMPT;

$requestBody = [
    'model' => $model,
    'input' => $prompt,
];

$ch = curl_init($endpoint);
curl_setopt_array($ch, [
    CURLOPT_POST => true,
    CURLOPT_HTTPHEADER => [
        'Content-Type: application/json',
        'Authorization: Bearer ' . $apiKey,
    ],
    CURLOPT_POSTFIELDS => json_encode($requestBody, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_TIMEOUT => 60,
]);

$response = curl_exec($ch);
$curlError = curl_error($ch);
$statusCode = (int)curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
curl_close($ch);

if ($response === false) {
    jsonResponse(502, [
        'ok' => false,
        'error' => 'OpenAI request failed: ' . $curlError,
        'mode_label' => 'تعذر الاتصال بـ OpenAI',
    ]);
}

if ($statusCode < 200 || $statusCode >= 300) {
    jsonResponse(502, [
        'ok' => false,
        'error' => 'OpenAI HTTP ' . $statusCode . ': ' . $response,
        'mode_label' => 'OpenAI أعاد خطأ',
    ]);
}

$responseJson = json_decode($response, true);
if (!is_array($responseJson)) {
    jsonResponse(502, [
        'ok' => false,
        'error' => 'Unreadable JSON from OpenAI.',
        'mode_label' => 'استجابة OpenAI غير صالحة',
    ]);
}

$outputText = trim((string)($responseJson['output_text'] ?? ''));
if ($outputText === '' && isset($responseJson['output']) && is_array($responseJson['output'])) {
    $parts = [];
    foreach ($responseJson['output'] as $item) {
        if (!isset($item['content']) || !is_array($item['content'])) {
            continue;
        }
        foreach ($item['content'] as $content) {
            $text = trim((string)($content['text'] ?? ''));
            if ($text !== '') {
                $parts[] = $text;
            }
        }
    }
    $outputText = trim(implode("\n", $parts));
}

if ($outputText === '') {
    jsonResponse(502, [
        'ok' => false,
        'error' => 'OpenAI returned empty output.',
        'mode_label' => 'OpenAI لم يرجع نصًا',
    ]);
}

$command = json_decode($outputText, true);
if (!is_array($command)) {
    jsonResponse(502, [
        'ok' => false,
        'error' => 'OpenAI returned invalid command JSON.',
        'raw' => $outputText,
        'mode_label' => 'صيغة الرد غير مفهومة',
    ]);
}

jsonResponse(200, [
    'ok' => true,
    'provider' => 'openai',
    'mode_label' => 'OpenAI متصل',
    'reply' => (string)($command['reply'] ?? ''),
    'command' => [
        'intent' => (string)($command['intent'] ?? 'unknown'),
        'person_name' => (string)($command['person_name'] ?? ''),
        'vendor_name' => (string)($command['vendor_name'] ?? ''),
        'title' => (string)($command['title'] ?? ''),
        'item_name' => (string)($command['item_name'] ?? ''),
        'amount' => (float)($command['amount'] ?? 0),
        'currency' => (string)($command['currency'] ?? 'USD'),
        'due_date' => (string)($command['due_date'] ?? ''),
        'bill_date' => (string)($command['bill_date'] ?? ''),
        'category' => (string)($command['category'] ?? ''),
        'notes' => (string)($command['notes'] ?? ''),
    ],
]);
