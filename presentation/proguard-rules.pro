-dontobfuscate

# ez-vcard
-dontwarn ezvcard.**
-dontwarn org.apache.log.**
-dontwarn org.apache.log4j.**
-dontwarn org.python.core.**

# Dagger
# This is to allow the restore functionality to work
-keep class dagger.** { *; }

-keep class androidx.activity.result.** { *; }
-keep class io.openmessages.** { *; }
