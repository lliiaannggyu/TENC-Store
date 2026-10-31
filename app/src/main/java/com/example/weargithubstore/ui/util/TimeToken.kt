package com.example.weargithubstore.ui.util

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 口令消息加解密：用消息密码派生 AES 密钥，把发送时间加密成一段令牌。
 * 接收方解出时间后与当前时间对比，误差 ≤ 12 分钟才算校验通过。
 */
object TimeToken {

    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    /** 把 [timeMillis] 用 [password] 加密为可复制粘贴的令牌字符串。 */
    fun encrypt(timeMillis: Long, password: String): String =
        encryptText(timeMillis.toString(), password)

    /** 解密令牌；密码错误或内容损坏时返回 null。 */
    fun decrypt(token: String, password: String): Long? =
        decryptText(token, password)?.toLongOrNull()

    /** 把任意文本用 [password] 加密为令牌字符串（如开发者加密邮箱）。 */
    fun encryptText(text: String, password: String): String {
        val key = keyOf(password)
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val body = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + body, Base64.NO_WRAP)
    }

    /** 解密任意文本令牌；密码错误或内容损坏时返回 null。 */
    fun decryptText(token: String, password: String): String? = runCatching {
        val data = Base64.decode(token.trim(), Base64.NO_WRAP)
        if (data.size <= IV_LEN) return null
        val iv = data.copyOfRange(0, IV_LEN)
        val body = data.copyOfRange(IV_LEN, data.size)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, keyOf(password), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(body), Charsets.UTF_8)
    }.getOrNull()

    /** 校验时间误差是否在 12 分钟内。 */
    fun withinWindow(timeMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean =
        kotlin.math.abs(nowMillis - timeMillis) <= 12 * 60 * 1000L

    private fun keyOf(password: String): SecretKeySpec {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(password.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }
}
