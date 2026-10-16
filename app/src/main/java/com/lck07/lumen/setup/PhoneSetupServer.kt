package com.lck07.lumen.setup

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.security.SecureRandom

/** What the phone form sends. */
data class PhoneForm(val server: String, val username: String, val password: String)

/** Texts for the phone page, in the TV's language. */
data class PhonePageText(
    val title: String,
    val subtitle: String,
    val server: String,
    val serverHint: String,
    val username: String,
    val password: String,
    val send: String,
    val sent: String,
)

/**
 * A tiny web server on the local network that serves a sign-in form for the owner's phone.
 * The URL carries a one-time token; the server accepts one submission and only answers
 * requests from the local network. It runs only while the setup screen is open.
 */
class PhoneSetupServer(
    private val text: PhonePageText,
    private val onForm: (PhoneForm) -> Unit,
) : NanoHTTPD(0) {
    private val token: String = ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    @Volatile private var used = false

    fun url(ip: String) = "http://$ip:$listeningPort/s/$token"

    override fun serve(session: IHTTPSession): Response {
        val remote = session.remoteIpAddress.orEmpty()
        if (!isLocal(remote)) return plain(Response.Status.FORBIDDEN, "Forbidden")
        if (session.uri != "/s/$token") return plain(Response.Status.NOT_FOUND, "Not found")
        if (used) return html(page(done = true))
        return when (session.method) {
            Method.GET -> html(page(done = false))
            Method.POST -> {
                val files = HashMap<String, String>()
                runCatching { session.parseBody(files) }
                val p = session.parms
                val form = PhoneForm(p["server"].orEmpty().trim(), p["username"].orEmpty().trim(), p["password"].orEmpty())
                if (form.server.isEmpty() || form.username.isEmpty() || form.password.isEmpty()) html(page(done = false))
                else { used = true; onForm(form); html(page(done = true)) }
            }
            else -> plain(Response.Status.METHOD_NOT_ALLOWED, "Not allowed")
        }
    }

    private fun isLocal(ip: String): Boolean =
        ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("127.") || ip.startsWith("169.254.") ||
            Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(ip) || ip.startsWith("fe80") || ip.startsWith("fd") || ip == "::1"

    private fun plain(status: Response.Status, msg: String) = newFixedLengthResponse(status, "text/plain", msg)
    private fun html(body: String) = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body).apply {
        addHeader("Cache-Control", "no-store")
        addHeader("Referrer-Policy", "no-referrer")
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun page(done: Boolean): String = """
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Lumen</title><style>
:root{color-scheme:dark}*{box-sizing:border-box}
body{margin:0;background:#000;color:#fff;font:17px/1.4 -apple-system,BlinkMacSystemFont,"Inter",system-ui,sans-serif;padding:32px 20px}
main{max-width:420px;margin:0 auto}h1{font-size:28px;letter-spacing:-.02em;margin:0 0 6px}p{color:rgba(235,235,245,.68);margin:0 0 24px}
label{display:block;font-size:13px;font-weight:600;color:rgba(235,235,245,.68);margin:16px 0 6px}
input{width:100%;font:inherit;color:#fff;background:#1c1c1e;border:1px solid rgba(84,84,88,.6);border-radius:12px;padding:14px}
input:focus{outline:2px solid #0091ff;outline-offset:1px}
button{margin-top:28px;width:100%;font:600 17px/1 inherit;font-family:inherit;color:#000;background:#fff;border:0;border-radius:14px;padding:16px}
.done{font-size:22px;font-weight:600;margin-top:40px}
</style></head><body><main>
${if (done) "<h1>Lumen</h1><div class=\"done\">${esc(text.sent)}</div>" else """
<h1>${esc(text.title)}</h1><p>${esc(text.subtitle)}</p>
<form method="post" autocomplete="off">
<label for="s">${esc(text.server)}</label><input id="s" name="server" type="url" inputmode="url" placeholder="${esc(text.serverHint)}" autocapitalize="none" autocorrect="off" spellcheck="false" required>
<label for="u">${esc(text.username)}</label><input id="u" name="username" autocapitalize="none" autocorrect="off" spellcheck="false" required>
<label for="p">${esc(text.password)}</label><input id="p" name="password" type="password" required>
<button type="submit">${esc(text.send)}</button></form>"""}
</main></body></html>""".trimIndent()

    companion object {
        /** The device's IPv4 address on the current network, if any. */
        fun localIp(context: Context): String? = runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
                ?.map { it.address }
                ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull()

        fun qr(content: String, size: Int = 512): Bitmap {
            val m = QRCodeWriter().encode(
                content, BarcodeFormat.QR_CODE, size, size,
                mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
            )
            val pixels = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
            return Bitmap.createBitmap(pixels, m.width, m.height, Bitmap.Config.RGB_565)
        }
    }
}
