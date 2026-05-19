# بناء APK بدون Android Studio

يوجد عندك الآن طريقتان:

## 1. عبر GitHub Actions

هذه هي الأسهل إذا رفعت المشروع إلى GitHub.

### الخطوات

1. ارفع المشروع إلى GitHub
2. افتح تبويب `Actions`
3. اختر workflow باسم:
   `Build Joe Mobile APK`
4. اضغط `Run workflow`
5. بعد انتهاء البناء نزّل الـ artifact باسم:
   `joe-mobile-debug-apk`

الملف الناتج سيكون:

`app-debug.apk`

## 2. من الطرفية مباشرة

تحتاج:

- `Android SDK`
- `Gradle`
- `Java 17`

ثم من داخل [mobile_joe](/abs/path/C:/xampp/htdocs/robote/mobile_joe):

```powershell
build_apk.bat
```

أو:

```powershell
gradle assembleDebug
```

وسيخرج الـ APK غالبًا هنا:

`app/build/outputs/apk/debug/app-debug.apk`

## ملاحظة

إذا كان هدفك فقط إخراج APK بسرعة وبدون تثبيت Android Studio، فخيار `GitHub Actions` هو الأسهل.
