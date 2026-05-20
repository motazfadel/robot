# Joe Mobile

نسخة أندرويد من `جو` أصبحت الآن مبنية لتعمل كمساعد إداري ذكي:

- تخزين محلي عبر `Room`
- إدخال نصي وصوتي
- تنفيذ أوامر عربية مثل التذكيرات والديون والفواتير والمشتريات
- ربط مباشر مع API داخل المشروع يستدعي `OpenAI` فقط
- fallback محلي بسيط إذا تعذر الوصول إلى الخدمة

## كيف تعمل النسخة الحالية

1. المستخدم يرسل رسالة داخل التطبيق
2. التطبيق يرسلها إلى:

`/robote/api/joe/interpret.php`

3. الخادم يستدعي `OpenAI Responses API`
4. الخادم يرجع أمرًا منظمًا مثل:

- `add_debt`
- `add_bill`
- `add_reminder`
- `add_shopping_item`
- `today_summary`
- `general_answer`

5. تطبيق الموبايل ينفذ العملية ويحفظها محليًا في `Room`

## إعداد الخادم

ضع ملف `.env` في جذر المشروع أو عدّل `.env.example` محليًا:

```env
JOE_AI_MODE=online
JOE_OPENAI_MODEL=gpt-5.4-mini
JOE_OPENAI_URL=https://api.openai.com/v1/responses
OPENAI_API_KEY=replace-with-your-openai-api-key
```

ثم شغّل Apache من `XAMPP`.

## عنوان الـ API في الموبايل

الافتراضي داخل التطبيق:

`http://10.0.2.2/robote/api`

وهذا مناسب لمحاكي أندرويد عندما يعمل الخادم محليًا على نفس الجهاز.

إذا كنت تستخدم جهازًا حقيقيًا، مرّر قيمة Gradle مثل:

`JOE_API_BASE_URL=http://192.168.1.50/robote/api`

ويمكنك وضعها في `local.properties`.

## ملاحظات

- لا تضع `OPENAI_API_KEY` داخل تطبيق أندرويد نفسه
- هذه النسخة لا تعتمد على `Ollama`
- إذا تعطل الاتصال، سيعود التطبيق مؤقتًا إلى الفهم المحلي البسيط

## إخراج APK

راجع:

- `BUILD_APK_AR.md`
- `BUILD_WITHOUT_ANDROID_STUDIO_AR.md`
