# بناء APK بسرعة

هذا المشروع مجهز ليُفتح في `Android Studio` ثم يخرج `APK` مباشرة تقريبًا.

## قبل الفتح

- تأكد أن `Android Studio` مثبت
- تأكد أن `Android SDK` مثبت من داخل Android Studio

إذا لم ينشئ Android Studio ملف `local.properties` تلقائيًا:

1. انسخ `local.properties.example` إلى `local.properties`
2. عدّل قيمة `sdk.dir` إلى مسار `Android SDK` عندك

## خطوات إخراج APK

1. افتح المجلد [mobile_joe](/abs/path/C:/xampp/htdocs/robote/mobile_joe) في `Android Studio`
2. انتظر انتهاء `Gradle Sync`
3. من الأعلى اختر:
   `Build > Build Bundle(s) / APK(s) > Build APK(s)`
4. بعد اكتمال البناء افتح:
   `app/build/outputs/apk/debug/app-debug.apk`

## إذا ظهر خطأ عند أول فتح

- وافق على تنزيل مكونات SDK المطلوبة
- اجعل `Gradle JDK` هو `17` أو `21`
- إذا طُلب تحديث Gradle أو Kotlin، لا توافق قبل تجربة البناء الحالي أولًا

## ملاحظات

- النسخة الحالية تبني `debug APK` بسهولة أكبر من `release`
- `debug APK` مناسب للتجربة المباشرة على الهاتف
