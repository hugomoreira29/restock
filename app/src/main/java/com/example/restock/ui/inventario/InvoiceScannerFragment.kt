package com.example.restock.ui.inventario

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.restock.R
import com.example.restock.databinding.FragmentInvoiceScannerBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class InvoiceScannerFragment : Fragment() {

    private var _binding: FragmentInvoiceScannerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InventarioViewModel by activityViewModels()

    /** Gallery picker — used when the user picks an existing image from storage. */
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { processImage(it) }
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

        // Camera → open the guided camera viewfinder fragment
        binding.cameraCard.setOnClickListener {
            findNavController().navigate(
                R.id.action_invoiceScannerFragment_to_invoiceCameraFragment
            )
        }

        // Gallery → pick from existing photos
        binding.galleryCard.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    // ── Gallery image processing ───────────────────────────────────────────────

    private fun processImage(uri: Uri) {
        binding.loadingLayout.visibility = View.VISIBLE

        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val image = withContext(Dispatchers.IO) {
                    InputImage.fromFilePath(ctx, uri)
                }
                val recognizer  = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val result      = recognizer.process(image).await()
                val cats        = ctx.resources.getStringArray(R.array.product_categories)
                val products    = InvoiceParser.parse(result, cats)

                binding.loadingLayout.visibility = View.GONE

                if (products.isEmpty()) {
                    Toast.makeText(ctx, getString(R.string.invoice_no_products), Toast.LENGTH_LONG).show()
                } else {
                    viewModel.setPendingInvoiceProducts(products)
                    findNavController().navigate(
                        R.id.action_invoiceScannerFragment_to_invoiceReviewFragment
                    )
                }
            } catch (e: Exception) {
                Log.e("InvoiceScanner", "Error processing invoice image", e)
                binding.loadingLayout.visibility = View.GONE
                Toast.makeText(ctx, getString(R.string.invoice_no_products), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
