# 터미널: sshj finds its algorithms and BouncyCastle by name (reflection / JCA provider lookup).
-keep class net.schmizz.sshj.** { *; }
-keep class com.hierynomus.sshj.** { *; }
# BouncyCastle registers its algorithms as class-name strings; keep the provider packages (the rest R8 can trace).
-keep class org.bouncycastle.jce.provider.** { *; }
-keep class org.bouncycastle.jcajce.provider.** { *; }
-dontwarn net.schmizz.**
-dontwarn com.hierynomus.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
