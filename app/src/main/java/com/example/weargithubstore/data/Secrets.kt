package com.example.weargithubstore.data

import com.example.weargithubstore.BuildConfig

/**
 * 应用内置密钥配置：编译期写死在 APK 内，
 * 不在任何界面展示、不可配置，避免被随意查看或篡改。
 *
 * 实际值来自根目录的 secrets.properties（已被 .gitignore 排除，不入仓库），
 * 编译时由 build.gradle.kts 注入 BuildConfig.LOG_CODEBOOK。
 */
object Secrets {

    /**
     * 日志口令密码本：内置，收发双方的应用内含同一把即可互通。
     * 测试端用它把日志发送时间加密成口令，开发者端用它解密并与邮件接收时间比对。
     */
    val LOG_CODEBOOK: String = BuildConfig.LOG_CODEBOOK
}
