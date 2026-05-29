# تشغيل جو مع Docker Compose

هذه الطريقة مخصصة لتشغيل الذكاء المحلي الخاص بـ `جو` كحزمة قابلة للنقل بين الأجهزة بسهولة.

## ما الذي سيعمل داخل Docker؟

- `Ollama`
- تنزيل الموديل المحلي تلقائيًا
- حفظ ملفات الموديل داخل volume دائم

## ما الذي سيبقى خارج Docker؟

- نسخة `EXE` الخاصة بـ `JoeDesktop`

السبب:

- تطبيق `EXE` هو واجهة ويندوز محلية
- بينما `Ollama` خدمة محلية تعمل على المنفذ `11434`
- ونسخة `EXE` الحالية مهيأة أصلًا للاتصال بهذا المنفذ

## الملفات

- [docker-compose.yml](/C:/xampp/htdocs/robote/docker-compose.yml)
- [.env.example](/C:/xampp/htdocs/robote/.env.example)

## الإعداد

1. انسخ `.env.example` إلى `.env`
2. عدّل اسم الموديل إذا أردت
3. شغّل:

```powershell
docker compose up -d
```

## التحقق

افحص الحاويات:

```powershell
docker compose ps
```

افحص الموديل:

```powershell
docker compose logs ollama-init
```

## كيف يتصل JoeDesktop؟

نسخة `EXE` تحاول افتراضيًا الوصول إلى:

`http://127.0.0.1:11434/api/generate`

وهذا متوافق مباشرة مع `docker-compose.yml` الحالي لأننا فتحنا المنفذ:

`11434:11434`

## تغيير الموديل

مثال:

```env
OLLAMA_MODEL=qwen2.5:7b-instruct
```

أو:

```env
OLLAMA_MODEL=llama3.1:8b-instruct
```

## ملاحظات مهمة

- أول تشغيل قد يأخذ وقتًا لأن تنزيل الموديل كبير
- بعد أول تنزيل سيُحفظ داخل volume باسم `ollama_data`
- نقل المشروع يصبح أسهل بكثير لأن إعداد Ollama يصبح موثقًا وثابتًا

## القيود الحالية

- هذا يشغّل طبقة الذكاء المحلي فقط
- لا يغلّف واجهة `EXE` داخل Docker
- هذا هو التصميم الصحيح لويندوز في أغلب الحالات

