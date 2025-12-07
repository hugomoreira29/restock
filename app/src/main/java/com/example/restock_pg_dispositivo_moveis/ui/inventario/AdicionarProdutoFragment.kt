package com.example.restock_pg_dispositivo_moveis.ui.inventario

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.example.restock_pg_dispositivo_moveis.databinding.FragmentAdicionarProdutoBinding
import com.example.restock_pg_dispositivo_moveis.model.Product
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class AdicionarProdutoFragment : Fragment() {

    private var _binding: FragmentAdicionarProdutoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InventarioViewModel by viewModels()
    private val args: AdicionarProdutoFragmentArgs by navArgs()
    private var isEditMode = false

    private var imageUri: Uri? = null
    private var selectedDate: Date? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            imageUri?.let { uri ->
                binding.productImageView.setImageURI(uri)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Permissão da câmara negada.", Toast.LENGTH_SHORT).show()
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
        checkMode()
    }

    private fun checkMode() {
        val productId = args.produtoId
        if (productId != null) {
            isEditMode = true
            binding.toolbar.title = "EDITAR PRODUTO"
            binding.addProductButton.text = "Atualizar Produto"
            viewModel.loadProduct(productId)
            observeSelectedProduct()
        } else {
            isEditMode = false
            binding.toolbar.title = "ADICIONAR PRODUTO"
            binding.addProductButton.text = "+ Adicionar Produto"
        }
    }

    private fun observeSelectedProduct() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedProduct.collect { product ->
                product?.let { bindProductData(it) }
            }
        }
    }

    private fun bindProductData(product: Product) {
        binding.productNameEditText.setText(product.nome)
        binding.quantityEditText.setText(product.quantidade.toString())
        binding.categoryAutoCompleteTextView.setText(product.categoria, false)
        binding.priceEditText.setText(product.preco.toString())

        product.validade?.let {
            selectedDate = Date(it)
            binding.expiryDateEditText.setText(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selectedDate!!))
        }

        product.imagemUrl?.let {
            imageUri = Uri.parse(it)
            Glide.with(this).load(it).into(binding.productImageView)
        }
    }

    private fun setupUI() {
        val categories = listOf("Mercearia", "Lacticínios", "Frutas", "Legumes", "Bebidas", "Limpeza")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)
        binding.categoryAutoCompleteTextView.setAdapter(adapter)
    }

    private fun setupClickListeners() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.expiryDateEditText.setOnClickListener { showDatePicker() }
        binding.addImageButton.setOnClickListener { requestCameraPermission() }
        binding.addProductButton.setOnClickListener { saveOrUpdateProduct() }
    }
    
    private fun saveOrUpdateProduct(){
        val name = binding.productNameEditText.text.toString().trim()
        val quantity = binding.quantityEditText.text.toString().toDoubleOrNull() ?: 0.0
        val category = binding.categoryAutoCompleteTextView.text.toString()
        val price = binding.priceEditText.text.toString().toDoubleOrNull() ?: 0.0

        if (name.isEmpty() || quantity <= 0 || category.isEmpty()) {
            Toast.makeText(context, "Preencha os campos obrigatórios.", Toast.LENGTH_SHORT).show()
            return
        }

        if (imageUri != null && !imageUri.toString().startsWith("http")) { // Apenas faz upload de novas imagens
            uploadImageAndSaveProduct(name, quantity, category, price, selectedDate)
        } else {
            val existingImageUrl = if (isEditMode) viewModel.selectedProduct.value?.imagemUrl else null
            saveProduct(name, quantity, category, price, selectedDate, existingImageUrl)
        }
    }

    private fun uploadImageAndSaveProduct(name: String, quantity: Double, category: String, price: Double, expiryDate: Date?) {
        val storageRef = FirebaseStorage.getInstance().reference.child("product_images/${UUID.randomUUID()}")
        storageRef.putFile(imageUri!!).addOnSuccessListener { 
            storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                saveProduct(name, quantity, category, price, expiryDate, downloadUrl.toString())
            }
        }.addOnFailureListener {
            Toast.makeText(context, "Falha no upload da imagem.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveProduct(name: String, quantity: Double, category: String, price: Double, expiryDate: Date?, imageUrl: String?) {
        val productId = if (isEditMode) args.produtoId!! else UUID.randomUUID().toString()
        val product = Product(
            id = productId,
            nome = name,
            quantidade = quantity,
            categoria = category,
            preco = price,
            validade = expiryDate?.time,
            imagemUrl = imageUrl
        )

        if (isEditMode) {
            viewModel.updateProduct(product)
        } else {
            viewModel.addProduct(product)
        }
        findNavController().navigateUp()
    }

    private fun requestCameraPermission() {
        requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        val imagePath = File(requireContext().cacheDir, "images")
        imagePath.mkdirs()
        val newFile = File(imagePath, "product_image_${System.currentTimeMillis()}.jpg")

        imageUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", newFile)
        takePictureLauncher.launch(imageUri)
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                selectedDate = selectedCalendar.time
                binding.expiryDateEditText.setText(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selectedDate!!))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }
    
     override fun onDestroyView() {
        super.onDestroyView()
        viewModel.clearSelectedProduct() 
        _binding = null
    }
}
