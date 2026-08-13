package com.phoneagent.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseDecoderTest {
    @Test
    fun decodesChunkedMultilineEvent() {
        val decoder = SseDecoder()
        assertNull(decoder.accept("event: response.output_text.delta"))
        assertNull(decoder.accept("id: evt_1"))
        assertNull(decoder.accept("data: {\"delta\":\"hello\"}"))
        assertNull(decoder.accept("data: second-line"))
        val event = decoder.accept("")
        assertEquals("response.output_text.delta", event?.event)
        assertEquals("evt_1", event?.id)
        assertEquals("{\"delta\":\"hello\"}\nsecond-line", event?.data)
    }

    @Test
    fun ignoresCommentsAndEmptyEvents() {
        val decoder = SseDecoder()
        assertNull(decoder.accept(": keepalive"))
        assertNull(decoder.accept(""))
    }
}

