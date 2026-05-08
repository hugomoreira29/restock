package com.example.restock.ui.inventario

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.restock.R
import com.example.restock.databinding.FragmentInvoiceCameraBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

class InvoiceCameraFragment : Fragment() {

    private var _binding: FragmentInvoiceCameraBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InventarioViewModel by activityViewModels()

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.camera_permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
                findNavController().navigateUp()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoiceCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.shutterButton.setOnClickListener { takePhoto() }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermission.launch(Manifest.permission.CAMERA)
        }
    }

    // ── CameraX setup ─────────────────────────────────────────────────────────

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(requireContext())
        future.addListener({
            cameraProvider = future.get()
            bindUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindUseCases() {
        val provider = cameraProvider ?: return

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.previewView.surfaceProvider)
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                viewLifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                getString(R.string.photo_capture_error),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ── Photo capture ─────────────────────────────────────────────────────────

    private fun takePhoto() {
        val capture = imageCapture ?: return
        binding.shutterButton.isEnabled = false

        val imageDir  = File(requireContext().cacheDir, "invoice_images").also { it.mkdirs() }
        val imageFile = File(imageDir, "invoice_${System.currentTimeMillis()}.jpg")
        val options   = ImageCapture.OutputFileOptions.Builder(imageFile).build()

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    binding.loadingLayout.visibility = View.VISIBLE
                    processImage(imageFile)
                }

                override fun onError(exc: ImageCaptureException) {
                    binding.shutterButton.isEnabled = true
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.photo_capture_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    // ── OCR processing ────────────────────────────────────────────────────────

    private fun processImage(file: File) {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val image = withContext(Dispatchers.IO) {
                    InputImage.fromFilePath(ctx, android.net.Uri.fromFile(file))
                }
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val ocrResult  = recognizer.process(image).await()

                val cats    = ctx.resources.getStringArray(R.array.product_categories)
                val products = InvoiceParser.parse(ocrResult, cats)

                binding.loadingLayout.visibility = View.GONE

                if (products.isEmpty()) {
                    binding.shutterButton.isEnabled = true
                    Toast.makeText(ctx, getString(R.string.invoice_no_products), Toast.LENGTH_LONG).show()
                } else {
                    viewModel.setPendingInvoiceProducts(products)
                    findNavController().navigate(
                        R.id.action_invoiceCameraFragment_to_invoiceReviewFragment
                    )
                }
            } catch (e: Exception) {
                binding.loadingLayout.visibility = View.GONE
                binding.shutterButton.isEnabled = true
                Toast.makeText(ctx, getString(R.string.invoice_no_products), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraProvider?.unbindAll()
        _binding = null
    }
}
