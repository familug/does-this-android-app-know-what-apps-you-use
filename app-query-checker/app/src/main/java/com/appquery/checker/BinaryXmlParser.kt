package com.appquery.checker

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses Android's binary XML format (AXML) to extract the <queries> section
 * from AndroidManifest.xml files inside APKs.
 *
 * The binary XML format consists of:
 * - File header (magic + size)
 * - String pool (all strings referenced by index)
 * - Resource ID table (optional)
 * - XML tree nodes (namespace, element start/end, text)
 */
class BinaryXmlParser {

    companion object {
        private const val CHUNK_AXML_FILE = 0x00080003
        private const val CHUNK_STRING_POOL = 0x001C0001
        private const val CHUNK_RESOURCE_IDS = 0x00080180
        private const val CHUNK_START_NAMESPACE = 0x00100100
        private const val CHUNK_END_NAMESPACE = 0x00100101
        private const val CHUNK_START_ELEMENT = 0x00100102
        private const val CHUNK_END_ELEMENT = 0x00100103

        private const val TYPE_STRING = 0x03
        private const val TYPE_INT_DEC = 0x10

        private const val ATTR_NAME = "name"
    }

    data class QueriesResult(
        val queriesPackages: List<String>,
        val queriesIntentActions: List<String>,
        val hasQueryAllPackages: Boolean
    )

    fun parseManifestQueries(inputStream: InputStream): QueriesResult {
        val bytes = inputStream.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val magic = buf.int
        if (magic != CHUNK_AXML_FILE) {
            return QueriesResult(emptyList(), emptyList(), false)
        }
        buf.int // fileSize

        // Parse string pool
        val strings = parseStringPool(buf)

        // Parse remaining chunks (resource IDs + XML tree)
        val packages = mutableListOf<String>()
        val intentActions = mutableListOf<String>()
        var insideQueries = false
        var queriesDepth = 0
        var currentDepth = 0
        var currentElement = ""
        var parentElement = ""
        val elementStack = mutableListOf<String>()
        var hasQueryAllPackages = false

        while (buf.hasRemaining()) {
            val chunkStart = buf.position()
            if (buf.remaining() < 8) break

            val chunkType = buf.int
            val chunkSize = buf.int

            if (chunkSize < 8 || chunkStart + chunkSize > bytes.size) break

            when (chunkType) {
                CHUNK_RESOURCE_IDS -> {
                    // Skip resource IDs
                    buf.position(chunkStart + chunkSize)
                }
                CHUNK_START_NAMESPACE -> {
                    buf.position(chunkStart + chunkSize)
                }
                CHUNK_END_NAMESPACE -> {
                    buf.position(chunkStart + chunkSize)
                }
                CHUNK_START_ELEMENT -> {
                    // Header: chunkType(4) + chunkSize(4) + lineNumber(4) + comment(4)
                    // + namespaceUri(4) + name(4) + attrStart(2) + attrSize(2) + attrCount(2)
                    // + idIndex(2) + classIndex(2) + styleIndex(2)
                    if (buf.remaining() < 20) {
                        buf.position(chunkStart + chunkSize)
                        continue
                    }
                    buf.int // lineNumber
                    buf.int // comment
                    buf.int // namespaceUri
                    val nameIdx = buf.int
                    buf.short // attrStart
                    val attrSize = buf.short.toInt() and 0xFFFF
                    val attrCount = buf.short.toInt() and 0xFFFF
                    buf.short // idIndex
                    buf.short // classIndex
                    buf.short // styleIndex

                    val elemName = if (nameIdx >= 0 && nameIdx < strings.size) strings[nameIdx] else ""
                    parentElement = if (elementStack.isNotEmpty()) elementStack.last() else ""
                    currentElement = elemName
                    elementStack.add(elemName)
                    currentDepth++

                    if (elemName == "queries") {
                        insideQueries = true
                        queriesDepth = currentDepth
                    }

                    // Parse attributes
                    val attrNames = mutableMapOf<String, String>()
                    for (i in 0 until attrCount) {
                        val attrStart = buf.position()
                        if (buf.remaining() < 20) break
                        buf.int // attr namespace
                        val attrNameIdx = buf.int
                        val rawValueIdx = buf.int
                        buf.short // typedValue size
                        buf.get()  // typedValue res0
                        val dataType = buf.get().toInt() and 0xFF
                        val data = buf.int

                        val attrName = if (attrNameIdx >= 0 && attrNameIdx < strings.size) strings[attrNameIdx] else ""

                        val attrValue = when (dataType) {
                            TYPE_STRING -> {
                                if (rawValueIdx >= 0 && rawValueIdx < strings.size) {
                                    strings[rawValueIdx]
                                } else if (data >= 0 && data < strings.size) {
                                    strings[data]
                                } else ""
                            }
                            else -> {
                                if (rawValueIdx >= 0 && rawValueIdx < strings.size) {
                                    strings[rawValueIdx]
                                } else data.toString()
                            }
                        }

                        attrNames[attrName] = attrValue

                        // Ensure we advance to next attribute position
                        if (attrSize > 0) {
                            buf.position(attrStart + attrSize)
                        }
                    }

                    if (insideQueries) {
                        val nameValue = attrNames[ATTR_NAME] ?: attrNames[""] ?: ""
                        if (elemName == "package" && nameValue.isNotEmpty()) {
                            packages.add(nameValue)
                        } else if (elemName == "action" && nameValue.isNotEmpty()) {
                            intentActions.add(nameValue)
                        }
                    }

                    // Check for uses-permission QUERY_ALL_PACKAGES
                    if (elemName == "uses-permission") {
                        val nameValue = attrNames[ATTR_NAME] ?: attrNames[""] ?: ""
                        if (nameValue == "android.permission.QUERY_ALL_PACKAGES") {
                            hasQueryAllPackages = true
                        }
                    }

                    buf.position(chunkStart + chunkSize)
                }
                CHUNK_END_ELEMENT -> {
                    if (elementStack.isNotEmpty()) {
                        elementStack.removeAt(elementStack.size - 1)
                    }
                    if (insideQueries && currentDepth == queriesDepth) {
                        insideQueries = false
                    }
                    currentDepth--
                    buf.position(chunkStart + chunkSize)
                }
                else -> {
                    buf.position(chunkStart + chunkSize)
                }
            }
        }

        return QueriesResult(
            queriesPackages = packages,
            queriesIntentActions = intentActions,
            hasQueryAllPackages = hasQueryAllPackages
        )
    }

    private fun parseStringPool(buf: ByteBuffer): List<String> {
        val poolStart = buf.position()
        val chunkType = buf.int
        if (chunkType != CHUNK_STRING_POOL) return emptyList()

        val chunkSize = buf.int
        val stringCount = buf.int
        buf.int // styleCount
        val flags = buf.int
        val stringsStart = buf.int
        buf.int // stylesOffset

        val isUtf8 = (flags and (1 shl 8)) != 0

        // Read string offsets
        val offsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
            offsets[i] = buf.int
        }

        val strings = mutableListOf<String>()
        val dataStart = poolStart + stringsStart

        for (i in 0 until stringCount) {
            val strPos = dataStart + offsets[i]
            if (strPos >= buf.limit()) {
                strings.add("")
                continue
            }
            buf.position(strPos)
            try {
                if (isUtf8) {
                    strings.add(readUtf8String(buf))
                } else {
                    strings.add(readUtf16String(buf))
                }
            } catch (_: Exception) {
                strings.add("")
            }
        }

        buf.position(poolStart + chunkSize)
        return strings
    }

    private fun readUtf8String(buf: ByteBuffer): String {
        // UTF-8 encoding: charCount (1-2 bytes), byteCount (1-2 bytes), then UTF-8 bytes
        var charCount = buf.get().toInt() and 0xFF
        if (charCount and 0x80 != 0) {
            charCount = ((charCount and 0x7F) shl 8) or (buf.get().toInt() and 0xFF)
        }
        var byteCount = buf.get().toInt() and 0xFF
        if (byteCount and 0x80 != 0) {
            byteCount = ((byteCount and 0x7F) shl 8) or (buf.get().toInt() and 0xFF)
        }
        val strBytes = ByteArray(byteCount)
        buf.get(strBytes)
        return String(strBytes, Charsets.UTF_8)
    }

    private fun readUtf16String(buf: ByteBuffer): String {
        var charCount = buf.short.toInt() and 0xFFFF
        if (charCount and 0x8000 != 0) {
            charCount = ((charCount and 0x7FFF) shl 16) or (buf.short.toInt() and 0xFFFF)
        }
        val chars = CharArray(charCount)
        for (i in 0 until charCount) {
            chars[i] = buf.short.toInt().toChar()
        }
        return String(chars)
    }
}
