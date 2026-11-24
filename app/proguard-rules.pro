# kotlinx.serialization keeps generated serializers through its own rules; protocol classes are referenced by name.
-keep,includedescriptorclasses class dev.axkit.core.**$$serializer { *; }
-keepclassmembers class dev.axkit.core.** { *** Companion; }
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# WorkManager (CatchUpWorker) keeps its jobs in a Room database that it creates by reflection; without this the release
# build crashes at start ("NoSuchMethodException: WorkDatabase_Impl.<init>").
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
