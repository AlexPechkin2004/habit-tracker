# Зберігати сигнатури класів для Gson
-keep class com.alexpechkin.habittracker.** { *; }
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Зберігати поля та методи для серіалізації/десеріалізації
-keepclassmembers class com.alexpechkin.habittracker.** {
    private <fields>;
    public <fields>;
    protected <fields>;
}

# Запобігти обфускації TypeToken
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }