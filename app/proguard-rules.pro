-adaptresourcefilecontents META-INF/xposed/java_init.list

-keep,allowobfuscation,allowoptimization class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

-dontwarn io.github.libxposed.api.**
-dontwarn io.github.libxposed.annotation.**

-keep class io.github.libxposed.service.XposedProvider { *; }

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-repackageclasses
-allowaccessmodification
