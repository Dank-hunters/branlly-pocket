package com.branlly.pocket.domain.voice

enum class LocalVoiceCommand { NAVIGATION, MUSIC }

/** Correspondance exacte après normalisation mécanique. Aucun classement ni interprétation. */
object LocalVoiceCommandParser {
    private val commands: Map<String, LocalVoiceCommand> = mapOf(
        "lance la navigation" to LocalVoiceCommand.NAVIGATION,
        "lancer la navigation" to LocalVoiceCommand.NAVIGATION,
        "mode navigation" to LocalVoiceCommand.NAVIGATION,
        "lance le mode navigation" to LocalVoiceCommand.NAVIGATION,
        "je vais partir" to LocalVoiceCommand.NAVIGATION,
        "lance la musique" to LocalVoiceCommand.MUSIC,
        "lancer la musique" to LocalVoiceCommand.MUSIC,
        "mode musique" to LocalVoiceCommand.MUSIC,
        "lance le mode musique" to LocalVoiceCommand.MUSIC,
    )

    fun parse(transcript: String): LocalVoiceCommand? = commands[normalize(transcript)]

    private fun normalize(value: String): String = value
        .lowercase()
        .map(::withoutFrenchDiacritic)
        .joinToString(separator = "")
        .map { character -> if (character.isLetter() || character.isWhitespace()) character else ' ' }
        .joinToString(separator = "")
        .trim()
        .split(Regex("\\s+"))
        .joinToString(" ")

    private fun withoutFrenchDiacritic(character: Char): Char = when (character) {
        'à', 'â', 'ä' -> 'a'
        'ç' -> 'c'
        'é', 'è', 'ê', 'ë' -> 'e'
        'î', 'ï' -> 'i'
        'ô', 'ö' -> 'o'
        'ù', 'û', 'ü' -> 'u'
        'ÿ' -> 'y'
        else -> character
    }
}
