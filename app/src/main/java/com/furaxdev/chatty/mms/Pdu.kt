package com.furaxdev.chatty.mms

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Encodage / décodage minimal des PDU MMS (OMA-TS-MMS-ENC 1.2, encodage binaire WSP).
 * Couvre ce dont une appli SMS a besoin : notification (m-notification-ind),
 * message reçu (m-retrieve-conf), envoi (m-send-req) et accusé (m-notifyresp-ind).
 */
object Pdu {
    // Types de message
    const val SEND_REQ = 128
    const val SEND_CONF = 129
    const val NOTIFICATION_IND = 130
    const val NOTIFYRESP_IND = 131
    const val RETRIEVE_CONF = 132

    // Champs d'en-tête (sans le bit de poids fort)
    const val BCC = 0x01
    const val CC = 0x02
    const val CONTENT_LOCATION = 0x03
    const val CONTENT_TYPE = 0x04
    const val DATE = 0x05
    const val EXPIRY = 0x08
    const val FROM = 0x09
    const val MESSAGE_ID = 0x0B
    const val MESSAGE_TYPE = 0x0C
    const val MMS_VERSION = 0x0D
    const val MESSAGE_SIZE = 0x0E
    const val RESPONSE_STATUS = 0x12
    const val STATUS = 0x15
    const val SUBJECT = 0x16
    const val TO = 0x17
    const val TRANSACTION_ID = 0x18

    const val CHARSET_UTF8 = 106
    const val VERSION_1_2 = 0x12

    /** Types de contenu « bien connus » de WSP (index = valeur codée). */
    val WellKnownTypes = listOf(
        "*/*", "text/*", "text/html", "text/plain", "text/x-hdml", "text/x-ttml", "text/x-vCalendar",
        "text/x-vCard", "text/vnd.wap.wml", "text/vnd.wap.wmlscript", "text/vnd.wap.wta-event", "multipart/*",
        "multipart/mixed", "multipart/form-data", "multipart/byterantes", "multipart/alternative", "application/*",
        "application/java-vm", "application/x-www-form-urlencoded", "application/x-hdmlc", "application/vnd.wap.wmlc",
        "application/vnd.wap.wmlscriptc", "application/vnd.wap.wta-eventc", "application/vnd.wap.uaprof",
        "application/vnd.wap.wtls-ca-certificate", "application/vnd.wap.wtls-user-certificate",
        "application/x-x509-ca-cert", "application/x-x509-user-cert", "image/*", "image/gif", "image/jpeg",
        "image/tiff", "image/png", "image/vnd.wap.wbmp", "application/vnd.wap.multipart.*",
        "application/vnd.wap.multipart.mixed", "application/vnd.wap.multipart.form-data",
        "application/vnd.wap.multipart.byteranges", "application/vnd.wap.multipart.alternative", "application/xml",
        "text/xml", "application/vnd.wap.wbxml", "application/x-x968-cross-cert", "application/x-x968-ca-cert",
        "application/x-x968-user-cert", "text/vnd.wap.si", "application/vnd.wap.sic", "text/vnd.wap.sl",
        "application/vnd.wap.slc", "text/vnd.wap.co", "application/vnd.wap.coc", "application/vnd.wap.multipart.related",
        "application/vnd.wap.sia", "text/vnd.wap.connectivity-xml", "application/vnd.wap.connectivity-wbxml",
        "application/pkcs7-mime", "application/vnd.wap.hashed-certificate", "application/vnd.wap.signed-certificate",
        "application/vnd.wap.cert-response", "application/xhtml+xml", "application/wml+xml", "text/css",
        "application/vnd.wap.mms-message", "application/vnd.wap.rollover-certificate",
    )

    data class Part(
        val contentType: String,
        val data: ByteArray,
        val charset: Int = 0,
        val name: String? = null,
        val contentId: String? = null,
        val contentLocation: String? = null,
    ) {
        val isText get() = contentType.startsWith("text/plain", ignoreCase = true)
        val isSmil get() = contentType.equals("application/smil", ignoreCase = true)
        fun text(): String = String(data, charsetFor(charset))
    }

    /** En-têtes et parties d'un PDU décodé. */
    class Message {
        var type = 0
        var transactionId: String? = null
        var contentLocation: String? = null
        var from: String? = null
        val to = ArrayList<String>()
        val cc = ArrayList<String>()
        var subject: String? = null
        var date: Long = 0 // secondes
        var messageId: String? = null
        var contentType: String? = null
        var messageSize: Long = 0
        var expiry: Long = 0
        var responseStatus = 0
        val parts = ArrayList<Part>()
    }

    fun charsetFor(mib: Int): Charset = when (mib) {
        3 -> Charsets.US_ASCII
        4 -> Charsets.ISO_8859_1
        1000, 1015 -> Charsets.UTF_16BE
        1013 -> Charsets.UTF_16BE
        1014 -> Charsets.UTF_16LE
        else -> Charsets.UTF_8
    }

    /** Retire le suffixe « /TYPE=PLMN » d'une adresse MMS. */
    fun cleanAddress(raw: String): String = raw.substringBefore("/TYPE=").trim()

    // ------------------------------------------------------------------ décodage

    fun parse(bytes: ByteArray): Message? = runCatching { Reader(bytes).readMessage() }.getOrNull()

    private class Reader(val b: ByteArray) {
        var pos = 0
        fun hasMore() = pos < b.size
        fun peek() = b[pos].toInt() and 0xFF
        fun byte() = b[pos++].toInt() and 0xFF

        fun uintvar(): Long {
            var result = 0L
            while (true) {
                val v = byte()
                result = (result shl 7) or (v and 0x7F).toLong()
                if (v and 0x80 == 0) return result
            }
        }

        fun valueLength(): Int {
            val first = byte()
            return if (first < 31) first else if (first == 31) uintvar().toInt() else error("value-length invalide")
        }

        fun textString(): String {
            if (peek() == 0x7F) pos++ // guillemet d'échappement
            val start = pos
            while (pos < b.size && b[pos].toInt() != 0) pos++
            val s = String(b, start, pos - start, Charsets.UTF_8)
            if (pos < b.size) pos++ // NUL
            return s
        }

        fun rawText(): ByteArray {
            if (peek() == 0x7F) pos++
            val start = pos
            while (pos < b.size && b[pos].toInt() != 0) pos++
            val out = b.copyOfRange(start, pos)
            if (pos < b.size) pos++
            return out
        }

        fun longInteger(): Long {
            val len = byte()
            var v = 0L
            repeat(len) { v = (v shl 8) or byte().toLong() }
            return v
        }

        fun integer(): Long = if (peek() >= 0x80) (byte() and 0x7F).toLong() else longInteger()

        fun encodedString(): String {
            val first = peek()
            if (first < 32) {
                val len = valueLength()
                val end = pos + len
                val charset = integer().toInt()
                val raw = rawText()
                pos = end
                return String(raw, charsetFor(charset))
            }
            return textString()
        }

        /** Saute une valeur quelconque (utilisé pour les champs ignorés). */
        fun skipValue() {
            val first = peek()
            when {
                first < 31 -> { pos++; pos += first }
                first == 31 -> { pos++; pos += uintvar().toInt() }
                first < 128 -> textString()
                else -> pos++
            }
        }

        /** Lit un Content-Type et ses paramètres. Renvoie (type, paramètres). */
        fun contentType(): Pair<String, Map<Int, Any>> {
            val first = peek()
            if (first >= 0x80) {
                val idx = byte() and 0x7F
                return (WellKnownTypes.getOrNull(idx) ?: "application/octet-stream") to emptyMap()
            }
            if (first >= 32) return textString() to emptyMap()
            val len = valueLength()
            val end = pos + len
            val type = when {
                peek() >= 0x80 -> WellKnownTypes.getOrNull(byte() and 0x7F) ?: "application/octet-stream"
                peek() < 32 -> WellKnownTypes.getOrNull(longInteger().toInt()) ?: "application/octet-stream"
                else -> textString()
            }
            val params = HashMap<Int, Any>()
            while (pos < end) {
                val p = peek()
                if (p >= 0x80) {
                    val token = byte() and 0x7F
                    when (token) {
                        0x01 -> params[0x01] = integer().toInt() // charset
                        0x05, 0x06, 0x17, 0x18 -> params[token] = textString() // name / filename
                        0x09 -> params[0x09] = if (peek() >= 0x80) (WellKnownTypes.getOrNull(byte() and 0x7F) ?: "") else textString()
                        0x0A, 0x19 -> params[0x0A] = textString() // start
                        else -> skipValue()
                    }
                } else if (p >= 32) {
                    textString(); skipValue() // paramètre non typé
                } else {
                    skipValue()
                }
            }
            pos = end
            return type to params
        }

        fun readMessage(): Message {
            val m = Message()
            headers@ while (hasMore()) {
                val field = byte()
                if (field < 0x80) {
                    // En-tête applicatif : nom texte + valeur texte
                    pos--; textString(); textString(); continue
                }
                when (field and 0x7F) {
                    MESSAGE_TYPE -> m.type = byte()
                    TRANSACTION_ID -> m.transactionId = textString()
                    CONTENT_LOCATION -> m.contentLocation = textString()
                    MESSAGE_ID -> m.messageId = textString()
                    SUBJECT -> m.subject = encodedString()
                    TO -> m.to += cleanAddress(encodedString())
                    CC -> m.cc += cleanAddress(encodedString())
                    BCC -> encodedString()
                    DATE -> m.date = longInteger()
                    MESSAGE_SIZE -> m.messageSize = integer()
                    RESPONSE_STATUS -> m.responseStatus = byte()
                    FROM -> {
                        val len = valueLength()
                        val end = pos + len
                        if (peek() == 0x80) { pos++; m.from = cleanAddress(encodedString()) }
                        pos = end
                    }
                    EXPIRY -> {
                        val len = valueLength()
                        val end = pos + len
                        val token = byte()
                        val v = longInteger()
                        m.expiry = if (token == 0x80) v else System.currentTimeMillis() / 1000 + v
                        pos = end
                    }
                    CONTENT_TYPE -> {
                        m.contentType = contentType().first
                        break@headers // Content-Type est toujours le dernier en-tête
                    }
                    else -> skipValue()
                }
            }
            if (m.contentType?.contains("multipart", ignoreCase = true) == true && hasMore()) readParts(m)
            else if (m.contentType != null && hasMore()) m.parts += Part(m.contentType!!, b.copyOfRange(pos, b.size))
            return m
        }

        fun readParts(m: Message) {
            val count = uintvar().toInt()
            repeat(count) {
                val headersLen = uintvar().toInt()
                val dataLen = uintvar().toInt()
                val headersEnd = pos + headersLen
                val (type, params) = contentType()
                var contentId: String? = null
                var location: String? = null
                while (pos < headersEnd) {
                    val h = peek()
                    if (h >= 0x80) {
                        when (byte() and 0x7F) {
                            0x40 -> contentId = textString().removePrefix("\"")
                            0x0E -> location = textString()
                            else -> skipValue()
                        }
                    } else {
                        val name = textString()
                        val value = textString()
                        if (name.equals("Content-ID", true)) contentId = value
                        if (name.equals("Content-Location", true)) location = value
                    }
                }
                pos = headersEnd
                val data = b.copyOfRange(pos, (pos + dataLen).coerceAtMost(b.size))
                pos += dataLen
                m.parts += Part(
                    contentType = type,
                    data = data,
                    charset = (params[0x01] as? Int) ?: 0,
                    name = (params[0x05] ?: params[0x17] ?: params[0x06] ?: params[0x18]) as? String,
                    contentId = contentId,
                    contentLocation = location,
                )
            }
        }
    }

    // ------------------------------------------------------------------ encodage

    private class Writer {
        val out = ByteArrayOutputStream()
        fun byte(v: Int) = out.write(v and 0xFF)
        fun bytes(v: ByteArray) = out.write(v)
        fun shortInt(v: Int) = byte(v or 0x80)
        fun text(s: String) {
            val raw = s.toByteArray(Charsets.UTF_8)
            if (raw.isNotEmpty() && (raw[0].toInt() and 0xFF) >= 0x80) byte(0x7F)
            bytes(raw); byte(0)
        }
        fun quoted(s: String) { byte(0x22); bytes(s.toByteArray(Charsets.UTF_8)); byte(0) }
        fun uintvar(value: Long) {
            var v = value
            val stack = ArrayList<Int>()
            stack += (v and 0x7F).toInt()
            v = v shr 7
            while (v > 0) { stack += ((v and 0x7F) or 0x80).toInt(); v = v shr 7 }
            stack.asReversed().forEach(::byte)
        }
        fun valueLength(len: Int) = if (len < 31) byte(len) else { byte(31); uintvar(len.toLong()) }
        fun longInt(v: Long) {
            var n = v
            val raw = ArrayList<Int>()
            do { raw += (n and 0xFF).toInt(); n = n shr 8 } while (n > 0)
            byte(raw.size); raw.asReversed().forEach(::byte)
        }
        fun field(code: Int) = byte(code or 0x80)
        fun toByteArray(): ByteArray = out.toByteArray()
    }

    private fun mediaType(w: Writer, type: String) {
        val idx = WellKnownTypes.indexOf(type)
        if (idx >= 0) w.shortInt(idx) else w.text(type)
    }

    /** Content-Type complet (forme générale avec paramètres). */
    private fun contentTypeBytes(type: String, charset: Int?, name: String?, start: String? = null, related: String? = null): ByteArray {
        val inner = Writer()
        mediaType(inner, type)
        if (charset != null) { inner.shortInt(0x01); inner.shortInt(charset) }
        if (start != null) { inner.shortInt(0x0A); inner.text(start) }
        if (related != null) { inner.shortInt(0x09); inner.text(related) }
        if (name != null) { inner.shortInt(0x05); inner.text(name) }
        val body = inner.toByteArray()
        val w = Writer()
        w.valueLength(body.size)
        w.bytes(body)
        return w.toByteArray()
    }

    data class Attachment(val contentType: String, val data: ByteArray, val name: String)

    /** Construit un m-send-req (texte + pièces jointes, multipart/related avec SMIL). */
    fun buildSendReq(recipients: List<String>, text: String?, attachments: List<Attachment>, subject: String? = null): ByteArray {
        val parts = ArrayList<Part>()
        val smil = StringBuilder("<smil><head><layout><root-layout/>")
        smil.append("<region id=\"Image\" fit=\"meet\" top=\"0\" left=\"0\" height=\"80%\" width=\"100%\"/>")
        smil.append("<region id=\"Text\" top=\"80%\" left=\"0\" height=\"20%\" width=\"100%\"/>")
        smil.append("</layout></head><body>")
        attachments.forEachIndexed { i, a ->
            val cl = a.name.ifBlank { "file_$i" }
            val tag = when {
                a.contentType.startsWith("image/") -> "img"
                a.contentType.startsWith("video/") -> "video"
                a.contentType.startsWith("audio/") -> "audio"
                else -> "ref"
            }
            smil.append("<par dur=\"5000ms\"><$tag src=\"$cl\" region=\"Image\"/></par>")
            parts += Part(a.contentType, a.data, name = cl, contentId = "<$cl>", contentLocation = cl)
        }
        if (!text.isNullOrEmpty()) {
            smil.append("<par dur=\"5000ms\"><text src=\"text_0.txt\" region=\"Text\"/></par>")
            parts += Part("text/plain", text.toByteArray(Charsets.UTF_8), CHARSET_UTF8, "text_0.txt", "<text_0>", "text_0.txt")
        }
        smil.append("</body></smil>")
        parts.add(0, Part("application/smil", smil.toString().toByteArray(Charsets.UTF_8), name = "smil.xml", contentId = "<smil>", contentLocation = "smil.xml"))

        val w = Writer()
        w.field(MESSAGE_TYPE); w.shortInt(SEND_REQ)
        w.field(TRANSACTION_ID); w.text("T" + java.lang.Long.toHexString(System.currentTimeMillis()))
        w.field(MMS_VERSION); w.shortInt(VERSION_1_2)
        w.field(DATE); w.longInt(System.currentTimeMillis() / 1000)
        w.field(FROM); w.byte(1); w.byte(0x81) // insert-address-token : l'opérateur met notre numéro
        recipients.forEach { r ->
            w.field(TO)
            val addr = if (r.contains('@')) r else r.filter { it.isDigit() || it == '+' } + "/TYPE=PLMN"
            w.text(addr)
        }
        if (!subject.isNullOrBlank()) {
            w.field(SUBJECT)
            val raw = subject.toByteArray(Charsets.UTF_8)
            w.valueLength(raw.size + 2); w.shortInt(CHARSET_UTF8); w.bytes(raw); w.byte(0)
        }
        w.field(0x0A); w.shortInt(0x00) // Message-Class : Personal (0x80)
        w.field(0x06); w.shortInt(0x01) // Delivery-Report : non (0x81)
        w.field(0x10); w.shortInt(0x01) // Read-Report : non (0x81)
        w.field(CONTENT_TYPE)
        w.bytes(contentTypeBytes("application/vnd.wap.multipart.related", null, null, start = "<smil>", related = "application/smil"))
        writeParts(w, parts)
        return w.toByteArray()
    }

    private fun writeParts(w: Writer, parts: List<Part>) {
        w.uintvar(parts.size.toLong())
        for (p in parts) {
            val headers = Writer()
            headers.bytes(contentTypeBytes(p.contentType, if (p.charset != 0) p.charset else null, p.name))
            p.contentId?.let { headers.byte(0xC0); headers.quoted(it) }
            p.contentLocation?.let { headers.byte(0x8E); headers.text(it) }
            val h = headers.toByteArray()
            w.uintvar(h.size.toLong())
            w.uintvar(p.data.size.toLong())
            w.bytes(h)
            w.bytes(p.data)
        }
    }

    /** Accusé « message récupéré » envoyé à l'opérateur après un téléchargement. */
    fun buildNotifyRespInd(transactionId: String): ByteArray {
        val w = Writer()
        w.field(MESSAGE_TYPE); w.shortInt(NOTIFYRESP_IND)
        w.field(TRANSACTION_ID); w.text(transactionId)
        w.field(MMS_VERSION); w.shortInt(VERSION_1_2)
        w.field(STATUS); w.shortInt(0x01) // Retrieved (0x81)
        return w.toByteArray()
    }
}
