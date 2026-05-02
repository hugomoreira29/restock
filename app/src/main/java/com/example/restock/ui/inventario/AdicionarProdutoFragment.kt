package com.example.restock.ui.inventario

// Android - para o seletor de datas, URIs, logs e vistas do fragmento
import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast

// AndroidX - para permissões, câmara, navegação e coroutines
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs

// Glide - para carregar imagens do produto de forma eficiente
import com.bumptech.glide.Glide

// Classes internas da aplicação - recursos, binding e modelo do produto
import com.example.restock.R
import com.example.restock.databinding.FragmentAdicionarProdutoBinding
import com.example.restock.model.Product

// Firebase - para o armazenamento de imagens
import com.google.firebase.storage.FirebaseStorage

// Coroutines - para chamadas à API em background sem bloquear a interface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// JSON e rede - para processar as respostas das APIs de código de barras
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/** HUGO MOREIRA - a22402246
 * Fragmento para adicionar ou editar um produto no inventário familiar.
 * Suporta preenchimento automático de dados via código de barras,
 * captura de fotografia com a câmara e upload para o Firebase Storage.
 * Funciona em dois modos: adição de novo produto e edição de produto existente.
 */
class AdicionarProdutoFragment : Fragment() {

    private var _binding: FragmentAdicionarProdutoBinding? = null
    private val binding get() = _binding!!

    // ViewModel partilhado com o fragmento do inventário
    private val viewModel: InventarioViewModel by activityViewModels()
    private val args: AdicionarProdutoFragmentArgs by navArgs()

    // Indica se o fragmento está em modo de edição ou adição
    private var isEditMode = false

    // URI da imagem selecionada ou tirada com a câmara
    private var imageUri: Uri? = null

    // Data de validade selecionada pelo utilizador
    private var selectedDate: Date? = null

    // Lançador para capturar uma fotografia com a câmara
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            imageUri?.let { uri -> binding.productImageView.setImageURI(uri) }
        }
    }

    // Lançador para solicitar permissão de acesso à câmara
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, getString(R.string.camera_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdicionarProdutoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupClickListeners()
        setupCalculationListeners()
        checkMode()
        checkBarcode()
    }

    /**
     * Verifica se foi recebido um código de barras nos argumentos de navegação.
     * Se sim, inicia a pesquisa automática dos dados do produto.
     */
    private fun checkBarcode() {
        args.barcodeValue?.let { barcode ->
            Log.d("AdicionarProduto", "Barcode received: $barcode")
            binding.productNameEditText.setText(getString(R.string.loading))
            fetchProductDetails(barcode)
        }
    }

    /**
     * Consulta múltiplas APIs públicas para obter os dados do produto pelo código de barras.
     * Tenta as APIs por ordem até encontrar o produto ou esgotar as opções.
     */
    private fun fetchProductDetails(barcode: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            // Lista de APIs consultadas por ordem: alimentar, geral e beleza/cosméticos
            val apis = listOf(
                "https://world.openfoodfacts.org/api/v2/product/$barcode.json",
                "https://world.openproductsfacts.org/api/v2/product/$barcode.json",
                "https://world.openbeautyfacts.org/api/v2/product/$barcode.json"
            )

            var productFound = false

            for (apiUrl in apis) {
                try {
                    val url = URL(apiUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.setRequestProperty("User-Agent", "ReStockApp - Android - Version 1.0")

                    if (connection.responseCode == 200) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(response)

                        if (json.optInt("status") == 1) {
                            val productJson = json.getJSONObject("product")

                            // Prioriza o nome em inglês, usando o nome genérico como alternativa
                            val nameEn = productJson.optString("product_name_en", "")
                            val nameGeneric = productJson.optString("product_name", "")
                            val finalName = if (nameEn.isNotEmpty()) nameEn else nameGeneric

                            val imageUrl = productJson.optString("image_front_url", "")
                                .ifEmpty { productJson.optString("image_url", "") }

                            val categoriesText = productJson.optString("categories", "")

                            withContext(Dispatchers.Main) {
                                if (isAdded && _binding != null) {
                                    binding.productNotFoundBanner.visibility = View.GONE
                                }
                                updateUIWithProductDetails(finalName, imageUrl, categoriesText, barcode)
                            }
                            productFound = true
                            break // Para o loop assim que encontrar o produto
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AdicionarProduto", "Erro ao aceder a $apiUrl", e)
                }
            }

            // Se nenhuma API encontrou o produto, mostra banner de aviso e usa o código de barras como nome
            if (!productFound) {
                withContext(Dispatchers.Main) {
                    if (!isAdded || _binding == null) return@withContext
                    binding.productNameEditText.setText(getString(R.string.product_barcode_default, barcode))
                    binding.productNotFoundBanner.visibility = View.VISIBLE
                }
            }
        }
    }

    /**
     * Preenche a interface com os dados obtidos da API.
     * Tenta mapear a categoria do produto para uma das categorias da aplicação.
     */
    private fun updateUIWithProductDetails(name: String, imageUrl: String, categoriesText: String, barcode: String) {
        if (!isAdded || _binding == null) return

        binding.productNameEditText.setText(name.ifEmpty { getString(R.string.product_barcode_default, barcode) })

        // Carrega a imagem do produto caso exista URL válido
        if (imageUrl.isNotEmpty()) {
            imageUri = Uri.parse(imageUrl)
            Glide.with(this)
                .load(imageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(binding.productImageView)
        }

        val appCategories = resources.getStringArray(R.array.product_categories)

        // Por defeito usa "outro" ou o primeiro item da lista
        var matchedCategory = appCategories.find { it.equals("outro", ignoreCase = true) } ?: appCategories[0]
        var foundMatch = false

        // Tenta corresponder a categoria do produto às categorias da aplicação
        if (categoriesText.contains("limpeza", ignoreCase = true) ||
            categoriesText.contains("cleaning", ignoreCase = true) ||
            categoriesText.contains("detergent", ignoreCase = true)) {
            matchedCategory = appCategories.find { it.equals("Limpeza", ignoreCase = true) } ?: matchedCategory
            foundMatch = true
        } else if (categoriesText.contains("higiene", ignoreCase = true) ||
            categoriesText.contains("beauty", ignoreCase = true) ||
            categoriesText.contains("deodorant", ignoreCase = true) ||
            categoriesText.contains("shampoo", ignoreCase = true) ||
            categoriesText.contains("care", ignoreCase = true)) {
            matchedCategory = appCategories.find { it.equals("Higiene", ignoreCase = true) } ?: matchedCategory
            foundMatch = true
        }

        // Se não encontrou correspondência específica, tenta uma comparação genérica
        if (!foundMatch) {
            for (category in appCategories) {
                if (category.equals("outro", ignoreCase = true)) continue
                if (categoriesText.contains(category, ignoreCase = true)) {
                    matchedCategory = category
                    break
                }
            }
        }

        binding.categoryAutoCompleteTextView.setText(matchedCategory, false)
        Toast.makeText(context, getString(R.string.product_autofill_success), Toast.LENGTH_SHORT).show()
    }

    /**
     * Verifica o modo do fragmento com base nos argumentos de navegação.
     * Em modo de edição, carrega os dados do produto existente.
     */
    private fun checkMode() {
        val productId = args.produtoId
        if (productId != null) {
            isEditMode = true
            binding.toolbar.title = getString(R.string.edit_product_title)
            binding.addProductButton.text = getString(R.string.update_product_button)
            viewModel.loadProduct(productId)
            observeSelectedProduct()
        } else {
            isEditMode = false
            binding.toolbar.title = getString(R.string.add_product_title)
            binding.addProductButton.text = getString(R.string.add_product)
            binding.unitAutoCompleteTextView.setText("un", false)
        }
    }

    /**
     * Observa o produto selecionado no ViewModel e preenche a interface com os seus dados.
     */
    private fun observeSelectedProduct() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedProduct.collect { product ->
                product?.let { bindProductData(it) }
            }
        }
    }

    /**
     * Preenche os campos da interface com os dados do produto a editar.
     */
    private fun bindProductData(product: Product) {
        binding.productNameEditText.setText(product.nome)

        // Para unidades inteiras, apresenta sem casas decimais
        binding.quantityEditText.setText(
            if (product.unidade == "un") product.quantidade.toInt().toString()
            else product.quantidade.toString()
        )

        binding.unitAutoCompleteTextView.setText(product.unidade, false)
        binding.categoryAutoCompleteTextView.setText(CategoryUtils.localize(product.categoria, requireContext()), false)
        binding.priceEditText.setText(product.preco.toString())

        // Preenche a data de validade se existir
        product.validade?.let {
            selectedDate = Date(it)
            binding.expiryDateEditText.setText(
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selectedDate!!)
            )
        }

        // Carrega a imagem do produto se existir URL válido
        product.imagemUrl?.let {
            if (it.isNotEmpty()) {
                imageUri = Uri.parse(it)
                Glide.with(this).load(it).into(binding.productImageView)
            }
        }
        calculateTotal()
    }

    /**
     * Configura os adaptadores dos campos de seleção de categoria e unidade.
     */
    private fun setupUI() {
        val categories = resources.getStringArray(R.array.product_categories)
        binding.categoryAutoCompleteTextView.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)
        )

        val units = arrayOf("un", "kg", "g", "L", "ml")
        binding.unitAutoCompleteTextView.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, units)
        )
    }

    /**
     * Configura os listeners que recalculam o preço total
     * sempre que a quantidade, o preço ou a unidade são alterados.
     */
    private fun setupCalculationListeners() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { calculateTotal() }
        }
        binding.quantityEditText.addTextChangedListener(textWatcher)
        binding.priceEditText.addTextChangedListener(textWatcher)

        // Atualiza o rótulo do preço consoante a unidade selecionada
        binding.unitAutoCompleteTextView.setOnItemClickListener { _, _, _, _ ->
            val unit = binding.unitAutoCompleteTextView.text.toString()
            binding.priceLabel.text = if (unit == "un")
                getString(R.string.price_unit_label)
            else
                getString(R.string.price_per_unit_label, unit)
            calculateTotal()
        }
    }

    /**
     * Calcula e apresenta o preço total com base na quantidade e preço unitário.
     */
    private fun calculateTotal() {
        val quantity = binding.quantityEditText.text.toString().toDoubleOrNull() ?: 0.0
        val price = binding.priceEditText.text.toString().toDoubleOrNull() ?: 0.0
        binding.totalPriceEditText.setText(String.format(Locale.getDefault(), "%.2f", quantity * price))
    }

    /**
     * Configura todos os listeners de clique do fragmento.
     */
    private fun setupClickListeners() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.expiryDateEditText.setOnClickListener { showDatePicker() }
        binding.addImageButton.setOnClickListener { requestCameraPermission() }
        binding.addProductButton.setOnClickListener { saveOrUpdateProduct() }

        // Navega para o leitor de código de barras ao clicar no ícone do campo
        binding.barcodeInputLayout.setEndIconOnClickListener {
            findNavController().navigate(R.id.action_adicionarProdutoFragment_to_barcodeScannerFragment)
        }
    }

    /**
     * Valida os campos e decide se deve fazer upload da imagem antes de guardar,
     * ou guardar diretamente caso a imagem já seja uma URL remota ou não exista.
     */
    private fun saveOrUpdateProduct() {
        val name = binding.productNameEditText.text.toString().trim()
        val quantity = binding.quantityEditText.text.toString().toDoubleOrNull() ?: 0.0
        val unit = binding.unitAutoCompleteTextView.text.toString()
        val category = CategoryUtils.toNeutralKey(binding.categoryAutoCompleteTextView.text.toString())
        val price = binding.priceEditText.text.toString().toDoubleOrNull() ?: 0.0

        if (name.isEmpty() || quantity <= 0 || category.isEmpty()) {
            Toast.makeText(context, getString(R.string.fill_required_fields), Toast.LENGTH_SHORT).show()
            return
        }

        // Se a imagem for local (não começa por "http"), faz upload antes de guardar
        if (imageUri != null && !imageUri.toString().startsWith("http")) {
            uploadImageAndSaveProduct(name, quantity, unit, category, price, selectedDate)
        } else {
            val finalImageUrl = when {
                imageUri?.toString()?.startsWith("http") == true -> imageUri.toString()
                isEditMode -> viewModel.selectedProduct.value?.imagemUrl
                else -> null
            }
            saveProduct(name, quantity, unit, category, price, selectedDate, finalImageUrl)
        }
    }

    /**
     * Faz o upload da imagem local para o Firebase Storage
     * e guarda o produto com o URL da imagem após o upload ser concluído.
     */
    private fun uploadImageAndSaveProduct(name: String, quantity: Double, unit: String, category: String, price: Double, expiryDate: Date?) {
        val storageRef = FirebaseStorage.getInstance().reference.child("product_images/${UUID.randomUUID()}")
        storageRef.putFile(imageUri!!).addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                saveProduct(name, quantity, unit, category, price, expiryDate, downloadUrl.toString())
            }
        }.addOnFailureListener {
            Toast.makeText(context, getString(R.string.image_upload_fail), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Cria o objeto Product e chama o ViewModel para o adicionar ou atualizar.
     * Após guardar, navega de volta ao ecrã anterior.
     */
    private fun saveProduct(name: String, quantity: Double, unit: String, category: String, price: Double, expiryDate: Date?, imageUrl: String?) {
        val productId = if (isEditMode) args.produtoId!! else UUID.randomUUID().toString()
        val product = Product(
            id = productId,
            nome = name,
            quantidade = quantity,
            unidade = unit,
            categoria = category,
            preco = price,
            validade = expiryDate?.time,
            imagemUrl = imageUrl
        )

        if (isEditMode) viewModel.updateProduct(product)
        else viewModel.addProduct(product)

        findNavController().navigateUp()
    }

    /**
     * Solicita permissão de acesso à câmara antes de a abrir.
     */
    private fun requestCameraPermission() {
        requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    /**
     * Cria um ficheiro temporário e abre a câmara para capturar uma fotografia do produto.
     */
    private fun launchCamera() {
        val imagePath = File(requireContext().cacheDir, "images").also { it.mkdirs() }
        val newFile = File(imagePath, "product_image_${System.currentTimeMillis()}.jpg")
        imageUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", newFile)
        takePictureLauncher.launch(imageUri)
    }

    /**
     * Apresenta um seletor de data para definir a validade do produto.
     * Atualiza o campo de validade com a data selecionada.
     */
    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                selectedDate = selectedCalendar.time
                binding.expiryDateEditText.setText(
                    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selectedDate!!)
                )
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    /**
     * Limpa o produto selecionado no ViewModel e o binding
     * quando a vista é destruída para evitar fugas de memória.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.clearSelectedProduct()
        _binding = null
    }
}
