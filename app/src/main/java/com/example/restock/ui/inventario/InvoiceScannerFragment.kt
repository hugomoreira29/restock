package com.example.restock.ui.inventario

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.restock.R
import com.example.restock.databinding.FragmentInvoiceScannerBinding
import com.example.restock.model.Product
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class InvoiceScannerFragment : Fragment() {

    private var _binding: FragmentInvoiceScannerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InventarioViewModel by activityViewModels()

    private var photoUri: Uri? = null

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) photoUri?.let { processImage(it) }
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { processImage(it) }
        }

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCamera()
            else Toast.makeText(context, getString(R.string.camera_permission_denied), Toast.LENGTH_SHORT).show()
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoiceScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.cameraCard.setOnClickListener {
            requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }

        binding.galleryCard.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    private fun launchCamera() {
        val imageDir = File(requireContext().cacheDir, "invoice_images").also { it.mkdirs() }
        val imageFile = File(imageDir, "invoice_${System.currentTimeMillis()}.jpg")
        photoUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            imageFile
        )
        takePictureLauncher.launch(photoUri)
    }

    private fun processImage(uri: Uri) {
        binding.loadingLayout.visibility = View.VISIBLE

        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val image = withContext(Dispatchers.IO) {
                    InputImage.fromFilePath(ctx, uri)
                }
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val result = recognizer.process(image).await()
                val parsedProducts = parseInvoiceText(result)

                binding.loadingLayout.visibility = View.GONE

                if (parsedProducts.isEmpty()) {
                    Toast.makeText(context, getString(R.string.invoice_no_products), Toast.LENGTH_LONG).show()
                } else {
                    viewModel.setPendingInvoiceProducts(parsedProducts)
                    findNavController().navigate(
                        R.id.action_invoiceScannerFragment_to_invoiceReviewFragment
                    )
                }
            } catch (e: Exception) {
                Log.e("InvoiceScanner", "Error processing invoice image", e)
                binding.loadingLayout.visibility = View.GONE
                Toast.makeText(context, getString(R.string.invoice_no_products), Toast.LENGTH_LONG).show()
            }
        }
    }

    private data class LineInfo(val text: String, val top: Int, val bottom: Int, val left: Int, val right: Int)

    private fun parseInvoiceText(ocrResult: Text): List<Product> {
        val pricePattern = Regex("""(\d{1,3}(?:[.,]\d{3})*[.,]\d{2}|\d+[.,]\d{2})\s*[A-Za-z]?\s*$""")
        val qtyPrefixPattern = Regex("""^(\d+(?:[.,]\d+)?)\s*[xX]\s+(.+)$""")
        // Strips leading currency symbols like "Eur", "EUR", "€", "Euro"
        val currencyPrefix = Regex("""(?i)^\s*(eur\.?|euro|€)\s*""")
        val skipWords = setOf(
            "total", "subtotal", "iva", "troco", "pagamento", "desconto",
            "obrigado", "cartao", "cartão", "mbway", "multibanco", "numeracao",
            "numeração", "atcud", "fatura", "recibo", "talao", "talão",
            "continente", "pingo", "lidl", "aldi", "mercadona", "intermarche",
            "minipreco", "minipreço", "modelo", "el corte", "poupar", "poupança",
            "a pagar", "por pagar", "valor total", "montante", "contribuinte", "taxa"
        )

        // Recolhe todas as linhas de todos os blocos com posição
        val allLines = mutableListOf<LineInfo>()
        for (block in ocrResult.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                allLines.add(LineInfo(line.text.trim(), box.top, box.bottom, box.left, box.right))
            }
        }
        allLines.sortBy { it.top }

        val products = mutableListOf<Product>()
        val usedNameLines = mutableSetOf<LineInfo>()

        for (lineInfo in allLines) {
            val line = lineInfo.text
            if (line.length < 3) continue

            val lineLower = line.lowercase()
            if (skipWords.any { lineLower.contains(it) }) continue
            if (line.matches(Regex(""".*\d{2}[/\-]\d{2}[/\-]\d{2,4}.*"""))) continue
            if (line.matches(Regex("""[-=*_]{3,}"""))) continue

            val priceMatch = pricePattern.find(line) ?: continue
            val priceStr = priceMatch.groupValues[1].replace(",", ".")
            val totalPrice = priceStr.toDoubleOrNull() ?: continue
            if (totalPrice <= 0.0 || totalPrice > 500.0) continue

            // Extrai o nome da mesma linha e remove prefixo de moeda
            var namePart = currencyPrefix.replace(
                line.substring(0, priceMatch.range.first).trim(), ""
            ).trim()

            // Se o nome ficou curto (coluna separada), procura o texto à esquerda
            // na mesma faixa vertical
            if (namePart.length < 4) {
                val lineHeight = lineInfo.bottom - lineInfo.top
                val tolerance = lineHeight + (lineHeight / 2)

                val candidate = allLines
                    .filter { other ->
                        other !== lineInfo &&
                        other !in usedNameLines &&
                        other.right <= lineInfo.left + 20 &&
                        other.top >= lineInfo.top - tolerance &&
                        other.top <= lineInfo.top + tolerance &&
                        pricePattern.find(other.text) == null
                    }
                    .minByOrNull { Math.abs(it.top - lineInfo.top) }

                if (candidate != null) {
                    val candidateClean = currencyPrefix.replace(candidate.text, "").trim()
                    val candidateLower = candidateClean.lowercase()
                    if (candidateClean.length >= 3 && skipWords.none { candidateLower.contains(it) }) {
                        namePart = candidateClean
                        usedNameLines.add(candidate)
                    }
                }
            }

            if (namePart.length < 3) continue
            if (skipWords.any { namePart.lowercase().contains(it) }) continue

            var quantity = 1.0
            val qtyMatch = qtyPrefixPattern.find(namePart)
            if (qtyMatch != null) {
                quantity = qtyMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 1.0
                namePart = qtyMatch.groupValues[2].trim()
            }

            if (namePart.length < 3) continue

            val unitPrice = if (quantity > 1.0) totalPrice / quantity else totalPrice

            products.add(
                Product(
                    id = UUID.randomUUID().toString(),
                    nome = namePart.trim(),
                    quantidade = quantity,
                    unidade = "un",
                    preco = unitPrice,
                    categoria = guessCategory(namePart)
                )
            )
        }

        // Remove a linha que corresponde ao total da fatura:
        // se um produto tem preço = soma de todos os outros, é o total e não um produto real
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

    private fun guessCategory(name: String): String {
        val cats = context?.resources?.getStringArray(R.array.product_categories)
            ?: return "Mercearia"
        val n = name.lowercase()
        return when {
            anyMatch(n, "leite", "iogurte", "manteiga", "queijo", "nata", "lactose", "requeijão", "requeijao", "yogurte") -> cats[1]
            anyMatch(n, "maçã", "maca", "banana", "pera", "laranja", "limão", "limao", "uva", "morango", "kiwi", "ananás", "ananas", "melão", "melao", "manga", "fruta") -> cats[2]
            anyMatch(n, "tomate", "batata", "cebola", "alface", "cenoura", "couve", "brócolo", "brocolo", "alho", "pimento", "pepino", "espinafre", "legume", "vegetal") -> cats[3]
            anyMatch(n, "água", "agua", "sumo", "cerveja", "vinho", "refrigerante", "coca", "pepsi", "nespresso", "café", "cafe", "chá", "cha", "bebida", "ice tea") -> cats[4]
            anyMatch(n, "detergente", "limpador", "sabão", "sabao", "amaciador", "lixívia", "lixivia", "desinfetante", "ajax", "cif", "domestos", "skip", "bold", "fairy") -> cats[5]
            anyMatch(n, "shampoo", "champô", "champo", "gel duche", "creme", "desodorizante", "pasta dentes", "sabonete", "higiene", "perfume") -> cats[6]
            else -> cats[0]
        }
    }

    private fun anyMatch(text: String, vararg keywords: String) = keywords.any { text.contains(it) }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
