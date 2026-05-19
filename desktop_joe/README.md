# Joe Desktop

نسخة مكتبية خفيفة من `جو` موجهة للتوزيع كـ `exe` صغير نسبيًا على ويندوز.

## الاتجاه الحالي

أفضل حجم للتطبيق يتحقق عندما تكون النسخة:

- `online-first`
- بدون `Ollama` داخل الحزمة
- بدون مكتبات الصوت و`Excel` الاختيارية داخل نسخة التوزيع الأساسية

بهذا يبقى التطبيق خفيفًا، بينما تعمل ميزات الذكاء عبر `OpenAI` عند ضبط `OPENAI_API_KEY`.

## ما الذي تدعمه النسخة الخفيفة؟

- إدارة التذكيرات
- إدارة الديون
- إدارة الفواتير
- قائمة مشتريات منزلية
- ملخص سريع لليوم
- محادثة نصية مع `جو`
- رد صوتي على ويندوز
- فهم أوامر أساسية بالعربية
- وضع `online` مع `OpenAI Responses API`

## ميزات اختيارية غير مضمنة في البناء الخفيف

- الميكروفون
- تصدير `Excel`
- `Ollama` المحلي داخل الحزمة

يبقى الكود داعمًا لهذه الميزات عند توفر مكتباتها، لكن سكربت البناء الخفيف يستبعدها لتقليل الحجم.

## التشغيل

```powershell
cd desktop_joe
python main.py
```

## الإعداد

أنشئ ملف `.env` بجانب المشروع أو بجانب `app.py` وضع فيه مثلًا:

```env
JOE_AI_MODE=online
JOE_OPENAI_MODEL=gpt-5.4-mini
JOE_OPENAI_URL=https://api.openai.com/v1/responses
OPENAI_API_KEY=replace-with-your-openai-api-key
```

## إنشاء exe خفيف

```powershell
cd desktop_joe
build_exe.bat
```

هذا البناء يستبعد:

- `numpy`
- `sounddevice`
- `speech_recognition`
- `openpyxl`

وسيظهر الملف النهائي داخل:

`desktop_joe\dist\JoeDesktop.exe`
