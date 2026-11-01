package com.mdkdw1.mypdf

import android.content.Context
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * ⭐ HWPX (\uD55C\uAE00 \uAC1C\uBC29 \uD45C\uC900) \uD30C\uC11C
 * - ZIP + OWPML XML
 * - \ubcf8\ubb38 \ud14d\uc2a4\ud2b8 + \ud45c \uad6c\uc870 \ucd94\ucd9c
 */
object HwpxParser {

    data class Block(
        val type: Type,
        val text: String,
        val rows: List<List<String>> = emptyList()
    ) { enum class Type { PARAGRAPH, TABLE, HEADING } }

    data class ParseResult(
        val blocks: List<Block>,
        val plainText: String
    )

    fun parse(context: Context, file: File): Result<ParseResult> {
        return try {
            if (!file.exists()) return Result.failure(Exception("\ud30c\uc77c \uc5c6\uc74c"))

            ZipFile(file).use { zip ->
                val sections = zip.entries().toList()
                    .filter { it.name.matches(Regex("Contents/section\\d+\\.xml")) }
                    .sortedBy { it.name }

                if (sections.isEmpty()) {
                    return Result.failure(Exception("section*.xml \uc5c6\uc74c (HWPX \uc544\ub2d8)"))
                }

                val blocks = mutableListOf<Block>()
                for (entry in sections) {
                    val xml = zip.getInputStream(entry).use { it.readBytes() }
                    blocks.addAll(parseSection(xml))
                }

                val plain = blocks.joinToString("\n\n") { b ->
                    when (b.type) {
                        Block.Type.TABLE -> b.rows.joinToString("\n") { it.joinToString(" | ") }
                        else -> b.text
                    }
                }
                Result.success(ParseResult(blocks, plain))
            }
        } catch (e: Exception) {
            CrashLogger.e(context, "HwpxParser", "\ud30c\uc2f1 \uc2e4\ud328", e)
            Result.failure(e)
        }
    }

    private fun parseSection(xml: ByteArray): List<Block> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false   // hp: prefix \uadf8\ub300\ub85c \ucc98\ub9ac
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder()
            .parse(xml.inputStream())

        val blocks = mutableListOf<Block>()
        // \ud45c: <hp:tbl>, \ubb38\ub2e8: <hp:p>
        collectBlocks(doc.documentElement, blocks)
        return blocks
    }

    private fun collectBlocks(node: Node, out: MutableList<Block>) {
        if (node is Element) {
            val tag = node.tagName.removePrefix("hp:")
            when (tag) {
                "tbl" -> {
                    val rows = extractTable(node)
                    if (rows.isNotEmpty()) {
                        out.add(Block(Block.Type.TABLE, "", rows))
                    }
                    return
                }
                "p" -> {
                    val text = extractText(node).trim()
                    if (text.isNotEmpty()) {
                        // \uac04\ub2e8 \ud5e4\ub529 \ud310\ubcc4 (\uc22b\uc790 \uc2dc\uc791 + \uc9e7\uc74c)
                        val isHeading = text.length < 50 &&
                                (text.matches(Regex("^\\d+[\\.)]?\\s.*")) ||
                                 text.startsWith("\u25a0") || text.startsWith("\u25cf"))
                        out.add(Block(
                            if (isHeading) Block.Type.HEADING else Block.Type.PARAGRAPH,
                            text
                        ))
                    }
                    return
                }
            }
        }
        var child = node.firstChild
        while (child != null) {
            collectBlocks(child, out)
            child = child.nextSibling
        }
    }

    /** <hp:t> \ub178\ub4dc \ubaa8\uc544\uc11c \ud14d\uc2a4\ud2b8 */
    private fun extractText(element: Element): String {
        val sb = StringBuilder()
        fun walk(n: Node) {
            if (n is Element) {
                val t = n.tagName.removePrefix("hp:")
                if (t == "t") {
                    sb.append(n.textContent ?: "")
                    return
                }
                if (t == "lineBreak" || t == "br") {
                    sb.append("\n")
                    return
                }
                if (t == "tab") {
                    sb.append("\t")
                    return
                }
            }
            var c = n.firstChild
            while (c != null) {
                walk(c)
                c = c.nextSibling
            }
        }
        walk(element)
        return sb.toString()
    }

    /** <hp:tbl> \u2192 rows */
    private fun extractTable(tbl: Element): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val trList = tbl.getElementsByTagName("hp:tr")
        for (i in 0 until trList.length) {
            val tr = trList.item(i) as? Element ?: continue
            val tcList = tr.getElementsByTagName("hp:tc")
            val row = mutableListOf<String>()
            for (j in 0 until tcList.length) {
                val tc = tcList.item(j) as? Element ?: continue
                row.add(extractText(tc).trim().replace("\n", " "))
            }
            if (row.isNotEmpty()) rows.add(row)
        }
        return rows
    }
}
