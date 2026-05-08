package com.example.restock.ui.inventario

import com.example.restock.model.Product
import com.google.mlkit.vision.text.Text
import java.util.UUID

/**
 * Standalone OCR parser: converts an ML Kit [Text] result into a list of [Product]s.
 * Pass the localised product-category string array (R.array.product_categories) as [cats].
 */
object InvoiceParser {

    private data class LineInfo(
        val text: String,
        val top: Int, val bottom: Int,
        val left: Int, val right: Int
    )

    fun parse(ocrResult: Text, cats: Array<String>): List<Product> {

        val pricePattern     = Regex("""(\d{1,3}(?:[.,]\d{3})*[.,]\d{2}|\d+[.,]\d{2})\s*[A-Za-z]?\s*$""")
        val currencyPrefix   = Regex("""(?i)^\s*(eur\.?|euro|€)\s*""")
        val trailingCurrency = Regex("""(?i)\s*(eur\.?|euro|€)\s*$""")
        // IVA tax codes: Continente "(C) " / "(A) ", Pingo Doce "C  6% * ", Generic "NS "
        val ivaTaxPrefix     = Regex("""^\s*(?:\([A-Za-z]{1,2}\)\s*|[A-Z]\s+\d{1,2}%\s*\*?\s*|[A-Z]{1,2}\s+)""")
        // "4 X 0,99" or "4 X 0,99   3,96"
        val qtyTimesUnitPrice = Regex("""^(\d+(?:[.,]\d+)?)\s*[xX]\s+(\d+[.,]\d{2})$""")
        // "0,864 kg x 6,99"
        val qtyKgPattern      = Regex("""^-?(\d+(?:[.,]\d+)?)\s*[kK][gG]\s*[xX]\s*(\d+[.,]\d+)""")
        // Lidl inline "1,39x 3" at end of name line
        val lidlQtyPattern    = Regex("""\s+(\d+[.,]\d{2})x\s*(\d+(?:[.,]\d+)?)\s*$""")
        // "2x Product Name" prefix
        val qtyPrefixPattern  = Regex("""^(\d+(?:[.,]\d+)?)\s*[xX]\s+([^\d].+)$""")
        // "4 X" alone — OCR split the qty-line in two
        val qtyOnlyPattern    = Regex("""^(\d+(?:[.,]\d+)?)\s*[xX]\s*$""")

        val skipWords = setOf(
            "total", "subtotal", "iva", "troco", "pagamento",
            "desconto", "desconto direto",
            "descent0", "descent0 direto",   // OCR 'O'→'0', 'e'→'3' variants
            "descont0", "descont0 direto",   // OCR 'O'→'0' in "DESCONTO"
            "obrigado", "cartao", "cartão", "mbway", "multibanco",
            "numeracao", "numeração", "atcud", "fatura", "recibo", "talao", "talão",
            "poupar", "poupança", "poupanca", "poupanca imediata",
            "a pagar", "por pagar", "valor total", "montante",
            "contribuinte", "taxa", "tara", "deposito", "depósito",
            "valor de deposito", "volumes", "recapitulativo", "artigo(s)"
        )

        // Build flat, top-sorted list of all OCR lines with their bounding boxes
        val allLines = mutableListOf<LineInfo>()
        for (block in ocrResult.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                allLines.add(LineInfo(line.text.trim(), box.top, box.bottom, box.left, box.right))
            }
        }
        allLines.sortBy { it.top }

        val products   = mutableListOf<Product>()
        val usedLines  = mutableSetOf<LineInfo>()
        var currentReceiptCategory: String? = null

        for (lineInfo in allLines) {
            if (lineInfo in usedLines) continue
            val line = lineInfo.text
            if (line.length < 3) continue

            val lineLower = line.lowercase()
            if (skipWords.any { lineLower.contains(it) }) continue
            if (line.matches(Regex(""".*\d{2}[/\-]\d{2}[/\-]\d{2,4}.*"""))) continue
            if (line.matches(Regex("""[-=*_]{3,}"""))) continue

            // Section headers like "Talho:", "Frutas e Legumes:", "Laticinios/Beb. Veg.:"
            if (line.trimEnd().endsWith(":") && pricePattern.find(line) == null) {
                currentReceiptCategory = categoryFromReceiptSection(line.trimEnd(':').trim(), cats)
                continue
            }

            val priceMatch = pricePattern.find(line) ?: continue
            val totalPrice = priceMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: continue
            if (totalPrice <= 0.0 || totalPrice > 500.0) continue

            var namePart = currencyPrefix.replace(
                line.substring(0, priceMatch.range.first).trim(), ""
            ).trim()
            namePart = ivaTaxPrefix.replace(namePart, "").trim()
            namePart = trailingCurrency.replace(namePart, "").trim()
            namePart = namePart.trimStart('/')

            var quantity  = 1.0
            var unitPrice = totalPrice
            var unidade   = "un"

            val qtyUnitMatch = qtyTimesUnitPrice.find(namePart)
            val qtyKgMatch   = if (qtyUnitMatch == null) qtyKgPattern.find(namePart) else null
            val qtyOnlyMatch = if (qtyUnitMatch == null && qtyKgMatch == null) qtyOnlyPattern.find(namePart) else null
            val lidlQtyMatch = if (qtyUnitMatch == null && qtyKgMatch == null && qtyOnlyMatch == null) lidlQtyPattern.find(namePart) else null

            if (qtyUnitMatch != null || qtyKgMatch != null || qtyOnlyMatch != null) {
                val match = qtyUnitMatch ?: qtyKgMatch
                if (match != null) {
                    quantity  = match.groupValues[1].replace(",", ".").toDoubleOrNull()
                        ?.let { kotlin.math.abs(it) } ?: 1.0
                    unitPrice = match.groupValues[2].replace(",", ".").toDoubleOrNull() ?: totalPrice
                } else {
                    quantity  = qtyOnlyMatch!!.groupValues[1].replace(",", ".").toDoubleOrNull()
                        ?.let { kotlin.math.abs(it) } ?: 1.0
                    unitPrice = totalPrice
                }
                unidade = if (qtyKgMatch != null || quantity < 1.0) "kg" else "un"

                val lineHeight = lineInfo.bottom - lineInfo.top
                val nameLineAbove = allLines
                    .filter { other ->
                        other !== lineInfo &&
                        other !in usedLines &&
                        other.top < lineInfo.top &&
                        other.bottom >= lineInfo.top - lineHeight * 5 &&
                        pricePattern.find(other.text) == null &&
                        !other.text.trimEnd().endsWith(":")
                    }
                    .maxByOrNull { it.top }

                if (nameLineAbove != null) {
                    var cleanAbove = ivaTaxPrefix.replace(
                        currencyPrefix.replace(nameLineAbove.text, "").trim(), ""
                    ).trim()
                    cleanAbove = trailingCurrency.replace(cleanAbove, "").trim().trimStart('/')
                    val cleanAboveLower = cleanAbove.lowercase()
                    if (cleanAbove.length >= 3 && skipWords.none { cleanAboveLower.contains(it) }) {
                        namePart = cleanAbove
                        usedLines.add(nameLineAbove)
                    } else {
                        continue
                    }
                } else {
                    // Primary search failed — the name line may have been merged with the total
                    // by ML Kit (e.g. "(C) SUMOL ANANAS 1L   3,96" read as one OCR line).
                    // Guard: skip usedLineAbove if there is an unused line directly above this
                    // qty line that has its OWN content before the price (e.g. "NS VALOR DE
                    // DEPOSITO UN   0,40" skipped by skipWords). Standalone price blocks like
                    // "3,96" (right-column ML Kit split) are excluded from the guard by
                    // requiring ≥3 characters before the price match.
                    val lineHeight2 = lineInfo.bottom - lineInfo.top
                    val unusedPricedLineAbove = allLines.any { other ->
                        other !== lineInfo &&
                        other !in usedLines &&
                        other.top < lineInfo.top &&
                        other.bottom >= lineInfo.top - lineHeight2 * 3 &&
                        (pricePattern.find(other.text)?.range?.first ?: 0) >= 3
                    }

                    val usedLineAbove = if (unusedPricedLineAbove) null else allLines
                        .filter { other ->
                            other !== lineInfo &&
                            other in usedLines &&
                            other.top < lineInfo.top &&
                            other.bottom >= lineInfo.top - lineHeight2 * 5 &&
                            !other.text.trimEnd().endsWith(":")
                        }
                        .maxByOrNull { it.top }

                    if (usedLineAbove != null) {
                        val priceInAbove = pricePattern.find(usedLineAbove.text)
                        val aboveText = if (priceInAbove != null)
                            usedLineAbove.text.substring(0, priceInAbove.range.first).trim()
                        else usedLineAbove.text
                        var cleanName = ivaTaxPrefix.replace(
                            currencyPrefix.replace(aboveText, "").trim(), ""
                        ).trim()
                        cleanName = trailingCurrency.replace(cleanName, "").trim().trimStart('/')
                        val cleanNameLower = cleanName.lowercase()
                        if (cleanName.length >= 3 && skipWords.none { cleanNameLower.contains(it) }) {
                            val idx = products.indexOfFirst { it.nome == cleanName }
                            if (idx >= 0) {
                                val old = products[idx]
                                // If the product name contains "kg" (e.g. "BATATA VERMELHA CNT KG"),
                                // keep kg as the unit even when qty ≥ 1.0
                                val resolvedUnidade = if (cleanNameLower.contains("kg")) "kg" else unidade
                                products[idx] = old.copy(
                                    quantidade = quantity,
                                    preco      = unitPrice,
                                    unidade    = resolvedUnidade
                                )
                            }
                        }
                    }
                    continue
                }

            } else if (lidlQtyMatch != null) {
                unitPrice = lidlQtyMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: totalPrice
                quantity  = lidlQtyMatch.groupValues[2].replace(",", ".").toDoubleOrNull() ?: 1.0
                namePart  = namePart.substring(0, lidlQtyMatch.range.first).trim()

            } else {
                // Single-line product — may also be two-column (name left, price right in its own block)
                if (namePart.length < 4) {
                    val lineHeight = lineInfo.bottom - lineInfo.top
                    val tolerance  = lineHeight + (lineHeight / 2)
                    val candidate  = allLines
                        .filter { other ->
                            other !== lineInfo &&
                            other !in usedLines &&
                            other.right <= lineInfo.left + 20 &&
                            other.top >= lineInfo.top - tolerance &&
                            other.top <= lineInfo.top + tolerance &&
                            pricePattern.find(other.text) == null &&
                            !other.text.trimEnd().endsWith(":")
                        }
                        .minByOrNull { kotlin.math.abs(it.top - lineInfo.top) }

                    if (candidate != null) {
                        val candidateClean = ivaTaxPrefix.replace(
                            currencyPrefix.replace(candidate.text, "").trim(), ""
                        ).trim()
                        val candidateLower = candidateClean.lowercase()
                        if (candidateClean.length >= 3 && skipWords.none { candidateLower.contains(it) }) {
                            namePart = candidateClean
                            usedLines.add(candidate)
                        }
                    }
                }

                // "2x Product Name" prefix
                val qtyMatch = qtyPrefixPattern.find(namePart)
                if (qtyMatch != null) {
                    quantity  = qtyMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 1.0
                    namePart  = qtyMatch.groupValues[2].trim()
                    unitPrice = if (quantity > 0.0) totalPrice / quantity else totalPrice
                }
            }

            if (namePart.length < 3) continue
            if (skipWords.any { namePart.lowercase().contains(it) }) continue

            products.add(
                Product(
                    id        = UUID.randomUUID().toString(),
                    nome      = namePart.trim(),
                    quantidade = quantity,
                    unidade   = unidade,
                    preco     = unitPrice,
                    categoria = currentReceiptCategory ?: guessCategory(namePart, cats)
                )
            )
            usedLines.add(lineInfo)
        }

        // If one "product" equals the sum of all others it is the invoice total — remove it
        if (products.size >= 3) {
            val maxProduct = products.maxByOrNull { it.preco * it.quantidade }
            if (maxProduct != null) {
                val othersSum = products.sumOf { it.preco * it.quantidade } - (maxProduct.preco * maxProduct.quantidade)
                val diff = maxProduct.preco * maxProduct.quantidade - othersSum
                if (diff in -0.05..0.05) products.remove(maxProduct)
            }
        }

        return products
    }

    // ── Section header → category ─────────────────────────────────────────────

    private fun categoryFromReceiptSection(section: String, cats: Array<String>): String? {
        val s = section.lowercase()
        return when {
            s.contains("latic") || s.contains("beb. veg") || s.contains("beb veg") -> cats[1]
            s.contains("queij") && s.contains("charcut") -> cats[1]
            s.contains("fruta") && s.contains("legume")  -> null
            s.contains("fruta")  -> cats[2]
            s.contains("legume") || s.contains("vegetal") -> cats[3]
            s.contains("bebida") || s.contains("drink") || s.contains("refrigerante") -> cats[4]
            s.contains("lavand") || s.contains("limpez") ||
            s.contains("casa-cozinha") || s.contains("casa cozinha") -> cats[5]
            s.contains("higiene") || s.contains("perfum") || s.contains("cosmet") -> cats[6]
            s.contains("talho") || s.contains("peixaria") ||
            s.contains("peixe") || s.contains("charcut") -> cats[7]
            s.contains("padaria") || s.contains("pastelaria") -> cats[8]
            s.contains("mercearia") || s.contains("almoco") ||
            s.contains("congelad") || s.contains("snack") -> cats[0]
            else -> null
        }
    }

    // ── Name-based category guess ─────────────────────────────────────────────

    private fun guessCategory(name: String, cats: Array<String>): String {
        val n = name.lowercase()
        return when {
            anyMatch(n, "leite", "iogurte", "manteiga", "queijo", "nata", "lactose",
                "requeijão", "requeijao", "yogurte") -> cats[1]
            anyMatch(n, "maçã", "maca", "banana", "pera", "laranja", "limão", "limao",
                "uva", "morango", "kiwi", "ananás", "ananas", "melão", "melao",
                "manga", "fruta") -> cats[2]
            anyMatch(n, "tomate", "batata", "cebola", "alface", "cenoura", "couve",
                "brócolo", "brocolo", "alho", "pimento", "pepino", "espinafre",
                "legume", "vegetal") -> cats[3]
            anyMatch(n, "água", "agua", "sumo", "cerveja", "vinho", "refrigerante",
                "coca", "pepsi", "nespresso", "café", "cafe", "chá", "cha",
                "bebida", "ice tea") -> cats[4]
            anyMatch(n, "detergente", "limpador", "sabão", "sabao", "amaciador",
                "lixívia", "lixivia", "desinfetante", "ajax", "cif", "domestos",
                "skip", "bold", "fairy") -> cats[5]
            anyMatch(n, "shampoo", "champô", "champo", "gel duche", "creme",
                "desodorizante", "pasta dentes", "sabonete", "higiene", "perfume") -> cats[6]
            anyMatch(n, "frango", "carne", "peixe", "atum", "salmão", "salmao",
                "bacalhau", "camarão", "camarao", "lulas", "fiambre", "presunto",
                "chouriço", "chourico", "linguiça", "linguica", "alheira", "morcela",
                "vitela", "porco", "borrego", "peito", "bife", "costela") -> cats[7]
            anyMatch(n, "pão", "pao", "cacetinho", "baguete", "broa", "carcaça",
                "carcaca", "croissant", "tostas", "bolo de", "pastel") -> cats[8]
            else -> cats[0]
        }
    }

    private fun anyMatch(text: String, vararg keywords: String) =
        keywords.any { text.contains(it) }
}
