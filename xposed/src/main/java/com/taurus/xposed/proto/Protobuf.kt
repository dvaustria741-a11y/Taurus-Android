package com.taurus.xposed.proto

/**
 * Bare-minimum protobuf wire codec.
 *
 * Mirrors the SGPBParse / SGPBSerialize / SGPBEdit helpers from the iOS tweak.
 * Only handles the three wire types the Spotify UCS/remote-config messages use:
 *   0 = varint, 2 = length-delimited.
 * No descriptors, no generated code — raw field-number / wire-type pairs only.
 */

data class PBField(val number: Int, val wire: Int, var varint: Long = 0L, var payload: ByteArray = ByteArray(0)) {
    override fun equals(other: Any?): Boolean = other is PBField &&
            number == other.number && wire == other.wire &&
            varint == other.varint && payload.contentEquals(other.payload)

    override fun hashCode(): Int {
        var r = number; r = 31 * r + wire; r = 31 * r + varint.hashCode()
        r = 31 * r + payload.contentHashCode(); return r
    }
}

// ── read ──────────────────────────────────────────────────────────────────────

fun pbParse(data: ByteArray): MutableList<PBField>? {
    val out = mutableListOf<PBField>()
    var pos = 0
    while (pos < data.size) {
        val (tag, tagLen) = readVarint(data, pos) ?: return null
        pos += tagLen
        val number = (tag shr 3).toInt()
        val wire = (tag and 0x7L).toInt()
        when (wire) {
            0 -> {
                val (v, vLen) = readVarint(data, pos) ?: return null
                pos += vLen
                out += PBField(number, 0, varint = v)
            }
            2 -> {
                val (len, lenLen) = readVarint(data, pos) ?: return null
                pos += lenLen
                val bytes = data.copyOfRange(pos, pos + len.toInt())
                pos += len.toInt()
                out += PBField(number, 2, payload = bytes)
            }
            else -> return null
        }
    }
    return out
}

fun pbFirst(fields: List<PBField>?, number: Int): PBField? =
    fields?.firstOrNull { it.number == number }

fun pbText(field: PBField?): String? =
    if (field?.wire == 2) field.payload.toString(Charsets.UTF_8) else null

// ── write ─────────────────────────────────────────────────────────────────────

fun pbSerialize(fields: List<PBField>): ByteArray {
    val buf = mutableListOf<Byte>()
    for (f in fields) {
        writeVarint(buf, (f.number.toLong() shl 3) or f.wire.toLong())
        when (f.wire) {
            0 -> writeVarint(buf, f.varint)
            2 -> { writeVarint(buf, f.payload.size.toLong()); buf.addAll(f.payload.toList()) }
        }
    }
    return buf.toByteArray()
}

fun pbVarint(number: Int, v: Long) = PBField(number, 0, varint = v)
fun pbVarint(number: Int, b: Boolean) = PBField(number, 0, varint = if (b) 1L else 0L)
fun pbString(number: Int, s: String) = PBField(number, 2, payload = s.toByteArray(Charsets.UTF_8))
fun pbBytes(number: Int, data: ByteArray) = PBField(number, 2, payload = data)

/** Applies [transform] to the nested message at [path] inside [data], returning patched bytes. */
fun pbEdit(data: ByteArray, path: List<Int>, transform: (ByteArray) -> ByteArray?): ByteArray? {
    if (path.isEmpty()) return transform(data)
    val fields = pbParse(data) ?: return null
    val number = path[0]
    val field = pbFirst(fields, number) ?: return null
    if (field.wire != 2) return null
    val inner = pbEdit(field.payload, path.drop(1), transform) ?: return null
    field.payload = inner
    return pbSerialize(fields)
}

// ── varint helpers ────────────────────────────────────────────────────────────

private fun readVarint(data: ByteArray, start: Int): Pair<Long, Int>? {
    var v = 0L; var shift = 0; var i = start
    while (i < data.size) {
        val b = data[i++].toLong() and 0xFF
        v = v or ((b and 0x7F) shl shift)
        shift += 7
        if (b and 0x80 == 0L) return Pair(v, i - start)
        if (shift >= 64) return null
    }
    return null
}

private fun writeVarint(buf: MutableList<Byte>, v: Long) {
    var n = v
    while (n and 0x7FL.inv() != 0L) { buf += ((n and 0x7F) or 0x80).toByte(); n = n ushr 7 }
    buf += n.toByte()
}
