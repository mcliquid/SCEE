# EditType / QuestType / Overlay ordinals and DB keys use Kotlin class simple names
# (see ObjectTypeRegistry, EditType.name). R8 must not shorten those names or unrelated
# classes can collide (e.g. two types both becoming "u9").
-keepnames class de.westnordost.streetcomplete.quests.** { *; }
-keepnames class de.westnordost.streetcomplete.overlays.** { *; }

# kotlinx-serialization (consumer rules; see also default R8 rules)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-dontwarn org.slf4j.impl.StaticLoggerBinder

-keep,includedescriptorclasses class de.westnordost.streetcomplete.**$$serializer { *; }
-keepclassmembers class de.westnordost.streetcomplete.** {
    *** Companion;
}
-keepclasseswithmembers class de.westnordost.streetcomplete.** {
    kotlinx.serialization.KSerializer serializer(...);
}
