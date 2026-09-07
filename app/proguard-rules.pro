# R8 is currently disabled for the release build (see app/build.gradle.kts). These rules are the
# starting point for when it is switched on.

# Room generates implementations reflectively named after the @Database class.
-keep class dev.local.fasting.data.db.** { *; }

# Glance/AppWidget receivers and the alarm/boot receivers are referenced from the manifest only.
-keep class dev.local.fasting.widget.** { *; }
-keep class dev.local.fasting.tracking.** { *; }

# Kotlin metadata is needed for reflection over data classes in Room converters.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
