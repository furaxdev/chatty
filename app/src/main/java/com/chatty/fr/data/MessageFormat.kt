package com.chatty.fr.data

/**
 * Conventions textuelles qui passent par SMS et restent lisibles sur n'importe quel téléphone :
 * citations (réponses), réactions (façon Google Messages / iPhone) et codes de vérification.
 */
object MessageFormat {

    // --- Réponses / citations ---

    private const val QUOTE_PREFIX = "↪ « "
    private val QuoteRegex = Regex("^↪ « (.*?) »\\n", RegexOption.DOT_MATCHES_ALL)

    fun encodeReply(quoted: String, text: String): String {
        val flat = quoted.replace('\n', ' ').trim()
        val short = if (flat.length > 60) flat.take(57).trimEnd() + "…" else flat
        return "$QUOTE_PREFIX$short »\n$text"
    }

    /** Renvoie (citation, texte). */
    fun decodeReply(body: String): Pair<String?, String> {
        val m = QuoteRegex.find(body) ?: return null to body
        return m.groupValues[1] to body.substring(m.range.last + 1)
    }

    // --- Réactions ---

    data class ParsedReaction(val emoji: String, val target: String, val removed: Boolean)

    fun encodeReaction(emoji: String, target: String, removed: Boolean = false): String {
        val flat = target.replace('\n', ' ').trim()
        val short = if (flat.length > 60) flat.take(57).trimEnd() + "…" else flat
        return if (removed) "A retiré $emoji de « $short »" else "A réagi avec $emoji à « $short »"
    }

    private const val Q = "[«\"“]\\s?(.+?)\\s?[»\"”]"

    /** [emoji] fixe (Tapback iPhone) ou null = emoji capturé dans le groupe 1. */
    private class Rule(pattern: String, val emoji: String?, val removed: Boolean) {
        val regex = Regex("^$pattern$", RegexOption.DOT_MATCHES_ALL)
    }

    private val rules: List<Rule> = buildList {
        // Chatty / Google Messages
        add(Rule("A réagi avec (.+?) à $Q", null, false))
        add(Rule("A retiré (.+?) de $Q", null, true))
        add(Rule("Reacted (.+?) to $Q", null, false))
        add(Rule("Removed (.+?) from $Q", null, true))
        // Tapback iPhone (FR + EN)
        listOf(
            "A adoré" to "❤️", "A aimé" to "👍", "N’a pas aimé" to "👎", "N'a pas aimé" to "👎",
            "A ri de" to "😂", "A mis en évidence" to "‼️", "A posé une question sur" to "❓",
            "Loved" to "❤️", "Liked" to "👍", "Disliked" to "👎", "Laughed at" to "😂",
            "Emphasized" to "‼️", "Questioned" to "❓",
        ).forEach { (verb, emoji) -> add(Rule("${Regex.escape(verb)} $Q", emoji, false)) }
        listOf(
            "A retiré un j’adore de" to "❤️", "A retiré un j'adore de" to "❤️",
            "Removed a heart from" to "❤️", "Removed a like from" to "👍", "Removed a dislike from" to "👎",
            "Removed a laugh from" to "😂", "Removed an exclamation from" to "‼️", "Removed a question mark from" to "❓",
        ).forEach { (verb, emoji) -> add(Rule("${Regex.escape(verb)} $Q", emoji, true)) }
    }

    fun parseReaction(body: String): ParsedReaction? {
        val text = body.trim()
        if (text.length > 400) return null
        for (rule in rules) {
            val m = rule.regex.find(text) ?: continue
            return if (rule.emoji == null) ParsedReaction(m.groupValues[1].trim(), m.groupValues[2], rule.removed)
            else ParsedReaction(rule.emoji, m.groupValues[1], rule.removed)
        }
        return null
    }

    /** Le texte cité correspond-il à ce message ? (gère les troncatures « … »). */
    fun matches(target: String, body: String): Boolean {
        val t = target.trim().removeSuffix("…").removeSuffix("...").trim()
        val b = body.replace('\n', ' ').trim()
        if (t.isEmpty()) return false
        return b == t || b.startsWith(t) || (t.length >= 12 && b.contains(t))
    }

    // --- Codes de vérification ---

    private val OtpKeywords = Regex(
        "(code|otp|vérification|verification|validation|confirmation|mot de passe|password|passcode|pin|connexion|login|authentification)",
        RegexOption.IGNORE_CASE,
    )
    private val OtpRegex = Regex("(?<!\\d[.,]?)(\\d{4,8}|\\d{3}[- ]\\d{3})(?![.,]?\\d)")

    fun findOtp(body: String): String? {
        if (!OtpKeywords.containsMatchIn(body)) return null
        return OtpRegex.find(body)?.value?.filter { it.isDigit() }
    }

    // --- Réponses suggérées ---

    fun suggestions(lastIncoming: String): List<String> {
        val t = lastIncoming.lowercase().trim()
        return when {
            Regex("\\b(merci|thx|thanks)\\b").containsMatchIn(t) -> listOf("De rien 😊", "Avec plaisir !", "🙏")
            Regex("(ça va|ca va|comment vas|tu vas bien)").containsMatchIn(t) -> listOf("Ça va bien et toi ?", "Super ! 😄", "Bof…")
            Regex("^(salut|coucou|bonjour|bonsoir|hello|hey|yo|slt|cc)\\b").containsMatchIn(t) -> listOf("Salut !", "Coucou 👋", "Ça va ?")
            Regex("(où es|t'es où|tu es où|ou es)").containsMatchIn(t) -> listOf("J'arrive !", "Je suis en route 🚗", "5 min")
            Regex("(bonne nuit|dors bien)").containsMatchIn(t) -> listOf("Bonne nuit 😴", "Toi aussi ❤️", "À demain !")
            Regex("(anniv|joyeux)").containsMatchIn(t) -> listOf("Merci beaucoup ! 🥳", "Trop gentil ❤️", "🎉")
            t.endsWith("?") -> listOf("Oui", "Non", "Je ne sais pas")
            else -> listOf("👍", "D'accord", "Ok !")
        }
    }
}
