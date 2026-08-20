# LSPosed 模块不要混淆，否则 xposed_init 里的类名失效
-dontobfuscate
-dontshrink
-keepclassmembers class * {
    *;
}
