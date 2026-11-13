// Top-level build file where you can add configuration options common to all sub-projects.
// AGP 9.1 内置 Kotlin 支持（不再用 kotlin.android 插件）；
// Compose 编译器插件在内置 Kotlin 下仍需显式应用，版本须与 AGP 内置 Kotlin 一致（2.2.x）。
plugins {
    id("com.android.application") version "9.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
}
