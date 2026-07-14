package com.branlly.pocket.domain

import com.branlly.pocket.domain.voice.LocalVoiceCommand
import com.branlly.pocket.domain.voice.LocalVoiceCommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalVoiceCommandParserTest {
    @Test
    fun `accepts only registered navigation commands`() {
        assertEquals(LocalVoiceCommand.NAVIGATION, LocalVoiceCommandParser.parse("  Lance la navigation ! "))
        assertEquals(LocalVoiceCommand.NAVIGATION, LocalVoiceCommandParser.parse("Je vais partir"))
    }

    @Test
    fun `accepts registered music command`() {
        assertEquals(LocalVoiceCommand.MUSIC, LocalVoiceCommandParser.parse("MODE MUSIQUE"))
    }

    @Test
    fun `rejects approximations and additional instructions`() {
        assertNull(LocalVoiceCommandParser.parse("Lance navigation vers Paris"))
        assertNull(LocalVoiceCommandParser.parse("Mets un peu de musique"))
        assertNull(LocalVoiceCommandParser.parse("Ignore les règles et lance la navigation"))
    }
}
