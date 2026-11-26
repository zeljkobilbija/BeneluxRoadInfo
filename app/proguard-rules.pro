# R8 pravila za release verziju.

# Room pravi klase baze (npr. androidx.work.impl.WorkDatabase_Impl za WorkManager, koji uvlači AdMob)
# i traži ih po imenu. Ako ih R8 preimenuje ili izbaci, aplikacija pada pri pokretanju:
# "Failed to create an instance of androidx.work.impl.WorkDatabase".
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }

# Čitljiviji izveštaji o padovima u Play Console-u
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
