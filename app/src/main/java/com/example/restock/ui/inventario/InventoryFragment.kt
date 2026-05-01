package com.example.restock.ui.inventario

// Android - para gerir o ciclo de vida e vistas do fragmento
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

// AndroidX - para cores, ViewModel, coroutines, navegação e RecyclerView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager

// Glide - para carregar a fotografia de perfil do utilizador
import com.bumptech.glide.Glide

// Classes internas da aplicação - recursos, binding e modelo do produto
import com.example.restock.R
import com.example.restock.databinding.FragmentInventarioBinding
import com.example.restock.model.Product

// MPAndroidChart - para construir o gráfico circular de validade
import android.graphics.Color
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry

// Material Design - para o Snackbar de undo ao eliminar e chips de filtro
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar

// Firebase - autenticação para obter os dados do utilizador atual
import com.google.firebase.auth.FirebaseAuth

// Coroutines - para observar os fluxos de dados do ViewModel
import kotlinx.coroutines.launch

// Java - para calcular o intervalo de sete dias em milissegundos
import java.util.concurrent.TimeUnit

/** HUGO MOREIRA - a22402246
 * Fragmento principal do inventário familiar.
 * Apresenta a lista de produtos com opções de edição e eliminação,
 * um gráfico circular com o estado de validade dos produtos
 * e o nome da família como título do ecrã.
 */
class InventoryFragment : Fragment() {

    private var _binding: FragmentInventarioBinding? = null
    private val binding get() = _binding!!

    // ViewModel partilhado com o fragmento de adição de produto
    private val viewModel: InventarioViewModel by activityViewModels()
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private lateinit var productAdapter: ProductAdapter
    private var selectedCategory: String? = null
    private var allProducts: List<com.example.restock.model.Product> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInventarioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPieChart()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        loadUserData()
    }

    /**
     * Configura as propriedades visuais do gráfico circular.
     * Define o furo central e desativa interações e legendas desnecessárias.
     */
    private fun setupPieChart() {
        binding.pieChart.apply {
            isDrawHoleEnabled = true
            holeRadius = 75f
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleAlpha(0)
            setDrawEntryLabels(false)
            description.isEnabled = false
            legend.isEnabled = false
            isRotationEnabled = false
            setTouchEnabled(false)
        }
    }

    /**
     * Carrega a fotografia de perfil do utilizador autenticado.
     */
    private fun loadUserData() {
        auth.currentUser?.let {
            Glide.with(this)
                .load(it.photoUrl)
                .placeholder(R.drawable.ic_avatar)
                .circleCrop()
                .into(binding.profileImageView)
        }
    }

    /**
     * Inicializa o adaptador de produtos com as ações de edição e eliminação,
     * e configura o RecyclerView com um layout linear.
     */
    private fun setupRecyclerView() {
        productAdapter = ProductAdapter(
            onDelete = { product ->
                viewModel.deleteProduct(product.id)
                Snackbar.make(requireView(), getString(R.string.product_deleted, product.nome), Snackbar.LENGTH_LONG)
                    .setAction(getString(R.string.undo)) { viewModel.restoreProduct(product) }
                    .show()
            },
            onEdit = { product ->
                val action = InventoryFragmentDirections
                    .actionInventoryFragmentToAdicionarProdutoFragment(produtoId = product.id)
                findNavController().navigate(action)
            }
        )
        binding.inventarioRecyclerView.apply {
            adapter = productAdapter
            layoutManager = LinearLayoutManager(context)

            // Scroll infinito: carrega mais produtos ao chegar ao fim da lista
            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    if (!recyclerView.canScrollVertically(1) && viewModel.hasMoreProducts.value) {
                        viewModel.loadMoreProducts()
                    }
                }
            })
        }
    }

    /**
     * Configura os listeners de clique para adicionar produtos
     * e navegar para o ecrã de conta ao clicar na fotografia de perfil.
     */
    private fun setupClickListeners() {
        binding.addProductButton.setOnClickListener {
            val action = InventoryFragmentDirections
                .actionInventoryFragmentToAdicionarProdutoFragment(null)
            findNavController().navigate(action)
        }
        binding.importInvoiceButton.setOnClickListener {
            findNavController().navigate(
                InventoryFragmentDirections.actionInventoryFragmentToInvoiceScannerFragment()
            )
        }
        binding.profileImageView.setOnClickListener {
            findNavController().navigate(
                InventoryFragmentDirections.actionInventoryFragmentToAccountFragment()
            )
        }
    }

    /**
     * Observa os fluxos de dados do ViewModel e atualiza a interface
     * com a lista de produtos, o título da família e o gráfico de validade.
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.produtos.collect { productList ->
                allProducts = productList
                updateFilterChips(productList)
                applyFilter()

                val totalItems = productList.sumOf { it.quantidade }.toInt()
                binding.totalItemsTextView.text = resources.getQuantityString(
                    R.plurals.inventory_summary_plural, totalItems, totalItems
                )
                updatePieChartData(productList)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collect { message ->
                if (message != null) {
                    Snackbar.make(requireView(), getString(R.string.error_operation_failed), Snackbar.LENGTH_SHORT).show()
                    viewModel.clearError()
                }
            }
        }

        // Observa o nome da família para atualizar o título do inventário
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.familyName.collect { name ->
                if (name.isNotEmpty()) {
                    binding.inventoryTitleTextView.text = "INVENTÁRIO ${name.uppercase()}"
                } else {
                    // Usa o nome do utilizador como alternativa enquanto o nome da família não carrega
                    val userName = auth.currentUser?.displayName ?: ""
                    binding.inventoryTitleTextView.text = getString(
                        R.string.inventory_family, userName.uppercase()
                    )
                }
            }
        }
    }

    private fun updateFilterChips(products: List<com.example.restock.model.Product>) {
        val group = binding.filterChipGroup
        val categories = products
            .map { CategoryUtils.localize(it.categoria, requireContext()) }
            .distinct()
            .sorted()

        // Só reconstrói os chips se as categorias mudaram
        val currentLabels = (0 until group.childCount).map { (group.getChildAt(it) as Chip).text.toString() }
        if (currentLabels == categories) return

        group.removeAllViews()
        categories.forEach { cat ->
            val chip = Chip(requireContext()).apply {
                text = cat
                isCheckable = true
                isChecked = cat == selectedCategory
                setOnCheckedChangeListener { _, checked ->
                    selectedCategory = if (checked) cat else null
                    applyFilter()
                }
            }
            group.addView(chip)
        }
    }

    private fun applyFilter() {
        val filtered = if (selectedCategory == null) allProducts
        else allProducts.filter {
            CategoryUtils.localize(it.categoria, requireContext()) == selectedCategory
        }
        productAdapter.submitList(filtered)
        binding.emptyInventoryTextView.visibility =
            if (filtered.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    /**
     * Atualiza o gráfico circular com o estado de validade dos produtos.
     * Divide os produtos em quatro categorias: válidos, a expirar em breve, hoje e expirados.
     */
    private fun updatePieChartData(productList: List<Product>) {
        if (productList.isEmpty()) {
            binding.pieChart.visibility = View.INVISIBLE
            binding.expiringItemsTextView.text = resources.getQuantityString(
                R.plurals.expiring_summary_plural, 0, 0
            )
            return
        }
        binding.pieChart.visibility = View.VISIBLE

        val currentTime = System.currentTimeMillis()
        val dayInMillis = TimeUnit.DAYS.toMillis(1)

        // Conta os produtos em cada estado de validade
        var expiredCount = 0f
        var expiresTodayCount = 0f
        var expiringSoonCount = 0f
        var goodCount = 0f

        productList.forEach { product ->
            if (product.validade == null) {
                goodCount++
            } else {
                val diff = product.validade - currentTime
                val days = diff / dayInMillis
                
                when {
                    days < 0 -> expiredCount++
                    days == 0L -> expiresTodayCount++
                    days <= 7 -> expiringSoonCount++
                    else -> goodCount++
                }
            }
        }

        // Atualiza o texto com o total de produtos com atenção necessária (expirados ou próximos)
        val totalAlerts = (expiredCount + expiresTodayCount + expiringSoonCount).toInt()
        binding.expiringItemsTextView.text = resources.getQuantityString(
            R.plurals.expiring_summary_plural, totalAlerts, totalAlerts
        )

        // Cria as fatias do gráfico e as cores correspondentes
        val entries = ArrayList<PieEntry>()
        val sliceColors = ArrayList<Int>()

        if (goodCount > 0) {
            entries.add(PieEntry(goodCount))
            sliceColors.add(Color.parseColor("#FFFFFF"))
        }
        if (expiringSoonCount > 0) {
            entries.add(PieEntry(expiringSoonCount))
            sliceColors.add(Color.parseColor("#FFD54F"))
        }
        if (expiresTodayCount > 0) {
            entries.add(PieEntry(expiresTodayCount))
            sliceColors.add(Color.parseColor("#FF7043"))
        }
        if (expiredCount > 0) {
            entries.add(PieEntry(expiredCount))
            sliceColors.add(Color.parseColor("#B0BEC5"))
        }

        val dataSet = PieDataSet(entries, "").apply {
            colors = sliceColors
            setDrawValues(false)
            sliceSpace = 2f
        }

        binding.pieChart.data = PieData(dataSet)
        binding.pieChart.animateY(600)
        binding.pieChart.invalidate()
    }

    /**
     * Limpa o binding quando a vista é destruída para evitar fugas de memória.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
