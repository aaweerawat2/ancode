package com.tom.rv2ide.experimental.depsupdater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class DependencyParserTest {

    @Test
    fun benchmarkParseVersionCatalog() {
        val parser = DependencyParser()

        // Generate a large version catalog
        val sb = StringBuilder()
        sb.append("[versions]\n")
        sb.append("kotlin = \"1.9.0\"\n")
        sb.append("coroutines = \"1.7.3\"\n")
        sb.append("\n[libraries]\n")

        val iterations = 1000
        for (i in 0 until iterations) {
            sb.append("lib$i = { group = \"com.example\", name = \"library$i\", version = \"1.0.$i\" }\n")
            sb.append("libRef$i = { group = \"com.example\", name = \"libraryRef$i\", version.ref = \"kotlin\" }\n")
        }

        val content = sb.toString()

        // Warmup
        parser.parseVersionCatalog(content)

        // Measure
        val time = measureTimeMillis {
            parser.parseVersionCatalog(content)
        }

        println("Parsing $iterations libraries took $time ms")

        // Verification
        val result = parser.parseVersionCatalog(content)
        assertEquals(iterations * 2, result.size)
        assertEquals("com.example", result["lib0"]?.group)
        assertEquals("1.0.0", result["lib0"]?.version)
    }
}
