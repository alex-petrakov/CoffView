package me.alex.pet.coffview

import java.io.*

class CoffFile(private val source: File) {

    private lateinit var file: RandomAccessFile

    private var symbolTableOffset: Long = -1L

    private var numberOfSymbolTableEntries: Long = -1L

    private var stringTableSizeInBytes: Long = -1L

    private val symbolNames = mutableListOf<String>()

    private val stringTableOffset: Long
        get() = symbolTableOffset + numberOfSymbolTableEntries * SYMBOL_TABLE_ENTRY_SIZE_IN_BYTES


    fun read(): CoffDetails {
        file = RandomAccessFile(source, MODE_READ)
        parseCoffFile()
        return CoffDetails(
            symbolTableOffset,
            numberOfSymbolTableEntries,
            stringTableOffset,
            stringTableSizeInBytes,
            symbolNames.toList()
        )
    }

    private fun parseCoffFile() {
        file.use {
            parseHeader()
            parseStringTableSize()
            parseSymbolNames()
        }
    }

    private fun parseHeader() {
        file.seek(8)
        symbolTableOffset = file.read4Bytes()
        numberOfSymbolTableEntries = file.read4Bytes()
    }

    private fun parseStringTableSize() {
        file.seek(stringTableOffset)
        stringTableSizeInBytes = file.read4Bytes()
    }

    private fun parseSymbolNames() {
        file.seek(symbolTableOffset)
        for (entryNumber in 0 until numberOfSymbolTableEntries) {
            val symbolName = readSymbolNameFor(entryNumber)
            symbolNames.add(symbolName)
        }
    }

    private fun readSymbolNameFor(entryNumber: Long): String {
        val entryOffset = computeOffsetForSymbolTableEntry(entryNumber)
        file.seek(entryOffset)
        val lowest4Bytes = file.read4Bytes()
        return if (lowest4Bytes == 0L) {
            val offsetWithinStringTable = file.read4Bytes()
            readStringTableEntry(offsetWithinStringTable)
        } else {
            readImmediateName(entryOffset)
        }
    }

    private fun computeOffsetForSymbolTableEntry(entryNumber: Long): Long {
        return symbolTableOffset + entryNumber * SYMBOL_TABLE_ENTRY_SIZE_IN_BYTES
    }

    private fun readImmediateName(entryOffset: Long): String {
        file.seek(entryOffset)
        val symbolBytes = ByteArrayOutputStream(8)
        file.transferTo(symbolBytes) { byte, numOfWrittenBytes -> byte != 0 && numOfWrittenBytes < 8 }
        return symbolBytes.toString(Charsets.US_ASCII.name())
    }

    private fun readStringTableEntry(offsetWithinStringTable: Long): String {
        file.seek(stringTableOffset + offsetWithinStringTable)
        val symbolBytes = ByteArrayOutputStream(16)
        file.transferTo(symbolBytes) { byte, _ -> byte != 0 }
        return symbolBytes.toString(Charsets.US_ASCII.name())
    }
}

private const val SYMBOL_TABLE_ENTRY_SIZE_IN_BYTES = 18

private const val MODE_READ = "r"

private fun RandomAccessFile.read4Bytes(): Long {
    val first = read().toLong()
    val second = read().toLong()
    val third = read().toLong()
    val fourth = read().toLong()
    if ((first or second or third or fourth) < 0) {
        throw IOException()
    }
    return (fourth shl 24) or (third shl 16) or (second shl 8) or first
}

private fun RandomAccessFile.transferTo(outputStream: OutputStream, condition: (Int, Int) -> Boolean) {
    var numOfWrittenBytes = 0
    var b = read()
    while (condition(b, numOfWrittenBytes)) {
        outputStream.write(b)
        numOfWrittenBytes++
        b = read()
    }
}