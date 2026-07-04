package com.example.listacompracompose

// Evita variantes de mayúsculas/minúsculas del mismo producto (p. ej. "leche" vs "Leche"),
// que Supabase trataría como productos distintos al sincronizar (unique(name) es case-sensitive).
fun normalizeProductName(name: String): String = name.trim().replaceFirstChar { it.uppercase() }

object TextImporter {

    data class ParsedItem(
        val name: String,
        val quantity: Int = 1,
        val price: Double = 0.0
    )

    fun parse(raw: String): List<ParsedItem> =
        raw.split("\n", ";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { parseLine(it) }

    private fun parseLine(line: String): ParsedItem? {
        // Strip leading bullet chars
        var work = line.trimStart('-', '•', '*', '·', ' ')
        if (work.isBlank()) return null

        var price = 0.0
        var qty = 1

        // --- 1. Extract price ---
        // Matches: 3.50€  3,50€  €3.50  €3,50  (digits with . or , separator)
        val priceRegex = Regex("""(\d+[.,]\d{1,2})\s*€|€\s*(\d+[.,]\d{1,2})|(\d+[.,]\d{1,2})(?=\s*$)""")
        val priceMatch = priceRegex.find(work)
        if (priceMatch != null) {
            val raw = priceMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: ""
            price = raw.replace(',', '.').toDoubleOrNull() ?: 0.0
            work = (work.substring(0, priceMatch.range.first) + work.substring(priceMatch.range.last + 1)).trim()
        }

        // --- 2. Extract quantity ---
        // Matches: x2  X3  ×2  *3  2ud  2u  2uds
        val qtyPrefixRegex = Regex("""[xX×*]\s*(\d+)""")
        val qtySuffixRegex = Regex("""(\d+)\s*u(?:ds?|nidades?)?(?:\s|$)""", RegexOption.IGNORE_CASE)

        val qtyMatch = qtyPrefixRegex.find(work) ?: qtySuffixRegex.find(work)
        if (qtyMatch != null) {
            qty = qtyMatch.groupValues[1].toIntOrNull() ?: 1
            work = (work.substring(0, qtyMatch.range.first) + work.substring(qtyMatch.range.last + 1)).trim()
        }

        val name = normalizeProductName(work)
        if (name.isBlank()) return null

        return ParsedItem(name = name, quantity = qty, price = price)
    }
}
