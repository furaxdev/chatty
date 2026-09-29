package com.furaxdev.chatty.effects

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

    companion object {
        val bubble = entries.filter { it.kind == EffectKind.BUBBLE }
        val screen = entries.filter { it.kind == EffectKind.SCREEN }
    }
}

object EffectCodec {
    private const val START = "⁣⁣"
    private const val UNIT = '​'
    private const val END = '⁣'
    private val PATTERN = Regex("⁣⁣(​+)⁣$")

    fun encode(text: String, effect: MessageEffect?): String {
        if (effect == null) return text
        return text + START + UNIT.toString().repeat(effect.ordinal + 1) + END
    }

    /** Renvoie le texte nettoyé et l'effet éventuel. */
    fun decode(raw: String): Pair<String, MessageEffect?> {
        val match = PATTERN.find(raw) ?: return raw to null
        val index = match.groupValues[1].length - 1
        val effect = MessageEffect.entries.getOrNull(index)
        return raw.substring(0, match.range.first) to effect
    }
}
