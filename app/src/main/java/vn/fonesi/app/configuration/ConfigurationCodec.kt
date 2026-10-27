package vn.fonesi.app.configuration

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

data class ConfigurationEntry(val reference: Int, val packageName: String, val label: String, val group: String?)
data class ConfigurationArchive(val entries: List<ConfigurationEntry>)

/** Explicit allowlist, no guest identifiers, accounts, engine control, paths or security policy. */
object ConfigurationCodec {
    const val MAX_BYTES = 2 * 1024 * 1024
    private const val FORMAT = "vn.fonesi.configuration"
    private val packagePattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")

    fun read(input: InputStream): ConfigurationArchive {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) {
                val byte = input.read(); if (byte < 0) break
                require(output.size() < MAX_BYTES); output.write(byte)
            } else {
                require(count <= MAX_BYTES - output.size()) { "Configuration exceeds size budget" }
                output.write(buffer, 0, count)
            }
        }
        return decode(output.toByteArray())
    }
    fun decode(bytes: ByteArray): ConfigurationArchive {
        require(bytes.size <= MAX_BYTES)
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = decoder.decode(ByteBuffer.wrap(bytes)).toString()
        JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            val fields = mutableSetOf<String>()
            var format: String? = null; var version: Int? = null; var entries: List<ConfigurationEntry>? = null
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName(); require(fields.add(name)) { "Duplicate configuration field" }
                when (name) {
                    "format" -> format = string(reader)
                    "version" -> version = integer(reader)
                    "clones" -> {
                        val rows = mutableListOf<ConfigurationEntry>()
                        reader.beginArray(); while (reader.hasNext()) rows += entry(reader); reader.endArray()
                        entries = rows.toList()
                    }
                    else -> error("Unsupported configuration field")
                }
            }
            reader.endObject()
            require(reader.peek() == JsonToken.END_DOCUMENT && fields == setOf("format", "version", "clones") && format == FORMAT && version == 1)
            return ConfigurationArchive(checkNotNull(entries)).also(::validate)
        }
    }
    fun encode(archive: ConfigurationArchive): ByteArray {
        validate(archive)
        val output = ByteArrayOutputStream()
        val bounded = object : OutputStream() {
            override fun write(b: Int) { require(output.size() < MAX_BYTES); output.write(b) }
            override fun write(b: ByteArray, off: Int, len: Int) {
                require(len <= MAX_BYTES - output.size()); output.write(b, off, len)
            }
        }
        JsonWriter(OutputStreamWriter(bounded, Charsets.UTF_8)).use { writer ->
            writer.beginObject().name("format").value(FORMAT).name("version").value(1L).name("clones").beginArray()
            archive.entries.forEach { row ->
                writer.beginObject().name("ref").value(row.reference.toLong()).name("package").value(row.packageName)
                    .name("label").value(row.label).name("group")
                if (row.group == null) writer.nullValue() else writer.value(row.group)
                writer.endObject()
            }
            writer.endArray().endObject()
        }
        return output.toByteArray()
    }
    fun validate(archive: ConfigurationArchive) {
        require(archive.entries.map { it.reference }.distinct().size == archive.entries.size)
        archive.entries.forEach { row ->
            require(row.reference > 0 && row.packageName.length <= 255 && (row.packageName == "android" || packagePattern.matches(row.packageName)))
            validText(row.label, 80)
            row.group?.let { validText(it, 40) }
        }
    }
    private fun validText(value: String, max: Int) {
        require(value.length in 1..max && value == value.trim() && value.none { it.isISOControl() })
        var index = 0
        while (index < value.length) {
            val char = value[index++]
            if (Character.isHighSurrogate(char)) require(index < value.length && Character.isLowSurrogate(value[index++]))
            else require(!Character.isLowSurrogate(char))
        }
    }
    private fun entry(reader: JsonReader): ConfigurationEntry {
        val fields = mutableSetOf<String>()
        var reference: Int? = null; var pkg: String? = null; var label: String? = null; var group: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName(); require(fields.add(name)) { "Duplicate entry field" }
            when (name) {
                "ref" -> reference = integer(reader)
                "package" -> pkg = string(reader)
                "label" -> label = string(reader)
                "group" -> if (reader.peek() == JsonToken.NULL) reader.nextNull() else group = string(reader)
                else -> error("Unsupported entry field")
            }
        }
        reader.endObject()
        require(fields == setOf("ref", "package", "label", "group"))
        return ConfigurationEntry(checkNotNull(reference), checkNotNull(pkg), checkNotNull(label), group)
    }
    private fun string(reader: JsonReader): String { require(reader.peek() == JsonToken.STRING); return reader.nextString() }
    private fun integer(reader: JsonReader): Int {
        require(reader.peek() == JsonToken.NUMBER)
        val value = reader.nextString(); require(Regex("0|[1-9][0-9]*").matches(value))
        return requireNotNull(value.toIntOrNull())
    }
}
