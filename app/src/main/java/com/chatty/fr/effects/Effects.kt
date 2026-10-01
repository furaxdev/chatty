package com.chatty.fr.effects

/**
 * Effets d'envoi façon iMessage.
 *
 * Un SMS ne transporte que du texte : l'effet est donc encodé à la fin du message
 * avec des caractères invisibles (largeur nulle). Les autres applis affichent le
 * texte normalement, Chatty détecte le marqueur et rejoue l'animation.
 */
enum class EffectKind { BUBBLE, SCREEN }

enum class MessageEffect(val label: String, val kind: EffectKind, val emoji: String) {
    // Effets de bulle
    SLAM("Claquer", EffectKind.BUBBLE, "💥"),
    LOUD("Fort", EffectKind.BUBBLE, "📢"),
    GENTLE("Doux", EffectKind.BUBBLE, "🪶"),
    INVISIBLE_INK("Encre invisible", EffectKind.BUBBLE, "🫥"),
    SHAKE("Secousse", EffectKind.BUBBLE, "🫨"),

    // Effets plein écran
    ECHO("Écho", EffectKind.SCREEN, "🔁"),
    SPOTLIGHT("Projecteur", EffectKind.SCREEN, "🔦"),
    BALLOONS("Ballons", EffectKind.SCREEN, "🎈"),
    CONFETTI("Confettis", EffectKind.SCREEN, "🎊"),
    LOVE("Amour", EffectKind.SCREEN, "❤️"),
    LASERS("Lasers", EffectKind.SCREEN, "🪩"),
    FIREWORKS("Feux d'artifice", EffectKind.SCREEN, "🎆"),
    CELEBRATION("Célébration", EffectKind.SCREEN, "✨"),
    SNOW("Neige", EffectKind.SCREEN, "❄️"),

    // Ajoutés ensuite (toujours à la fin : l'ordre sert à l'encodage dans le SMS)
    EMOJI_RAIN("Pluie d'emojis", EffectKind.SCREEN, "🤩"),
    RAINBOW("Arc-en-ciel", EffectKind.SCREEN, "🌈"),
    MONEY("Pluie de billets", EffectKind.SCREEN, "💸"),
    SHOOTING_STARS("Étoiles filantes", EffectKind.SCREEN, "🌠"),
    JELLY("Gelée", EffectKind.BUBBLE, "🍮");

    /** L'effet existe aussi dans iMessage sur iPhone. */
    val isIMessageEffect: Boolean
        get() = this !in setOf(SHAKE, SNOW, EMOJI_RAIN, RAINBOW, MONEY, JELLY)

    companion object {
        val bubble = entries.filter { it.kind == EffectKind.BUBBLE }
        val screen = entries.filter { it.kind == EffectKind.SCREEN }
    }
}

object EffectCodec {
    private const val START = "\u2063\u2063"
    private const val UNIT = '\u200B'
    private const val END = '\u2063'
    private val PATTERN = Regex("\u2063\u2063(\u200B+)\u2063$")

    /**
     * Ajoute l'effet au texte. Avec [iPhoneLabel], les effets qui existent sur iMessage sont
     * aussi indiqués en clair, comme le fait un iPhone quand un iMessage passe en SMS :
     * « (Envoyé avec l'effet « Ballons ») ». Chatty le reconnaît dans les deux sens.
     */
    fun encode(text: String, effect: MessageEffect?, iPhoneLabel: Boolean = false): String {
        if (effect == null) return text
        val visible = if (iPhoneLabel && effect.isIMessageEffect) text + IMessageCompat.suffix(effect) else text
        return visible + START + UNIT.toString().repeat(effect.ordinal + 1) + END
    }

    /** Renvoie le texte nettoyé et l'effet éventuel (marqueur Chatty ou mention iPhone). */
    fun decode(raw: String): Pair<String, MessageEffect?> {
        val match = PATTERN.find(raw)
        val withoutMarker = if (match != null) raw.substring(0, match.range.first) else raw
        val marked = match?.let { MessageEffect.entries.getOrNull(it.groupValues[1].length - 1) }
        val (clean, fromIPhone) = IMessageCompat.decode(withoutMarker)
        return clean to (marked ?: fromIPhone)
    }
}

/**
 * Compatibilité avec les effets iMessage d'iOS.
 *
 * - Un iPhone qui envoie un iMessage avec effet en SMS ajoute « (Sent with Confetti) » (ou sa
 *   traduction) : Chatty retire cette mention et joue l'effet correspondant.
 * - Comme iMessage, certains mots déclenchent un effet tout seul (« Joyeux anniversaire » → ballons…).
 */
object IMessageCompat {

    /** Noms des effets iMessage (anglais et français) → effet Chatty. */
    private val names: Map<String, MessageEffect> = mapOf(
        "slam" to MessageEffect.SLAM, "claquer" to MessageEffect.SLAM, "claqué" to MessageEffect.SLAM,
        "claquement" to MessageEffect.SLAM, "impact" to MessageEffect.SLAM,
        "loud" to MessageEffect.LOUD, "fort" to MessageEffect.LOUD,
        "gentle" to MessageEffect.GENTLE, "doux" to MessageEffect.GENTLE, "doucement" to MessageEffect.GENTLE,
        "invisible ink" to MessageEffect.INVISIBLE_INK, "encre invisible" to MessageEffect.INVISIBLE_INK,
        "echo" to MessageEffect.ECHO, "écho" to MessageEffect.ECHO, "échos" to MessageEffect.ECHO,
        "spotlight" to MessageEffect.SPOTLIGHT, "projecteur" to MessageEffect.SPOTLIGHT, "projecteurs" to MessageEffect.SPOTLIGHT,
        "balloons" to MessageEffect.BALLOONS, "ballons" to MessageEffect.BALLOONS, "ballon" to MessageEffect.BALLOONS,
        "confetti" to MessageEffect.CONFETTI, "confettis" to MessageEffect.CONFETTI,
        "love" to MessageEffect.LOVE, "amour" to MessageEffect.LOVE,
        "lasers" to MessageEffect.LASERS, "laser" to MessageEffect.LASERS,
        "fireworks" to MessageEffect.FIREWORKS, "feux d'artifice" to MessageEffect.FIREWORKS,
        "feux d’artifice" to MessageEffect.FIREWORKS, "feu d'artifice" to MessageEffect.FIREWORKS,
        "celebration" to MessageEffect.CELEBRATION, "célébration" to MessageEffect.CELEBRATION,
        "celebrations" to MessageEffect.CELEBRATION, "célébrations" to MessageEffect.CELEBRATION, "fête" to MessageEffect.CELEBRATION,
        "shooting star" to MessageEffect.SHOOTING_STARS, "étoile filante" to MessageEffect.SHOOTING_STARS,
        "étoiles filantes" to MessageEffect.SHOOTING_STARS,
    )

    private val SUFFIX = Regex(
        "\\s*[(\\[](?:sent with|envoyé avec|envoye avec)\\s+(?:l['’]effet\\s+|the\\s+|effet\\s+)?[«\"“]?\\s*" +
            "([^()\\[\\]«»\"“”]+?)\\s*[»\"”]?(?:\\s+effect)?[)\\]]\\s*$",
        RegexOption.IGNORE_CASE,
    )

    /** Articles et mots ajoutés par iOS (« des lasers », « de l'amour », « l'effet Écho »…). */
    private val ARTICLE = Regex(
        "^(?:(?:l['’]effet|effet|the|des|du|de la|de|les|le|la|une|un|an|a)\\s+|de l['’]|l['’])",
        RegexOption.IGNORE_CASE,
    )
    private val TRAILING = Regex("\\s+(?:effect|effet)$", RegexOption.IGNORE_CASE)

    private fun normalize(raw: String): String {
        var n = raw.trim().replace(Regex("\\s+"), " ")
        repeat(2) { n = ARTICLE.replace(n, "") }
        return TRAILING.replace(n, "").trim().lowercase()
    }

    fun suffix(effect: MessageEffect) = " (Envoyé avec l'effet « ${effect.label} »)"

    /** Retire la mention « (Sent with …) » et renvoie l'effet reconnu. */
    fun decode(text: String): Pair<String, MessageEffect?> {
        val m = SUFFIX.find(text) ?: return text to null
        val name = normalize(m.groupValues[1])
        val effect = names[name] ?: return text to null
        return text.substring(0, m.range.first) to effect
    }

    private class Trigger(val regex: Regex, val effect: MessageEffect)

    /** Mots déclencheurs, comme sur iMessage (versions anglaise et française d'iOS). */
    private val triggers = listOf(
        Trigger(Regex("\\b(happy birthday|joyeux anniversaire|bon anniversaire)\\b", RegexOption.IGNORE_CASE), MessageEffect.BALLOONS),
        Trigger(Regex("\\b(congratulations|congrats|félicitations|felicitations)\\b", RegexOption.IGNORE_CASE), MessageEffect.CONFETTI),
        Trigger(Regex("(happy new year|bonne année|bonne annee)", RegexOption.IGNORE_CASE), MessageEffect.FIREWORKS),
        Trigger(Regex("(happy lunar new year|happy chinese new year|joyeux nouvel an chinois|bonne année lunaire)", RegexOption.IGNORE_CASE), MessageEffect.CELEBRATION),
        Trigger(Regex("\\bpew pew\\b", RegexOption.IGNORE_CASE), MessageEffect.LASERS),
        Trigger(Regex("\\b(selamat)\\b", RegexOption.IGNORE_CASE), MessageEffect.CONFETTI),
    )

    /** Effet déclenché par un mot-clé (ex. « Joyeux anniversaire » → ballons). */
    fun keywordEffect(text: String): MessageEffect? {
        // Le Nouvel An lunaire est testé avant « bonne année ».
        triggers.firstOrNull { it.effect == MessageEffect.CELEBRATION && it.regex.containsMatchIn(text) }?.let { return it.effect }
        return triggers.firstOrNull { it.regex.containsMatchIn(text) }?.effect
    }
}
