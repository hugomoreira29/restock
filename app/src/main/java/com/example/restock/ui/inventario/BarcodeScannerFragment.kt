package com.example.restock.ui.inventario

// Android - para permissões, logs e vistas do fragmento
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast

// AndroidX - para permissões, câmara e navegação
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

// Classes internas da aplicação - recursos e binding
import com.example.restock.R
import com.example.restock.databinding.FragmentBarcodeScannerBinding

// Google - para o futuro da câmara e o leitor de códigos de barras com ML Kit
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

// Java - para executar a análise de imagem numa thread separada
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** HUGO MOREIRA - a22402246
 * Fragmento responsável pela leitura de códigos de barras com a câmara.
 * Utiliza o CameraX para a pré-visualização e o ML Kit para a deteção.
 * Ao detetar um código, navega automaticamente para o ecrã de adição
 * de produto com o valor do código de barras como argumento.
 */
class BarcodeScannerFragment : Fragment() {

    private var _binding: FragmentBarcodeScannerBinding? = null
    private val binding get() = _binding!!

    // Executor dedicado para processar as imagens da câmara em background
    private lateinit var cameraExecutor: ExecutorService

    // Cliente do ML Kit para a leitura de códigos de barras
    private lateinit var barcodeScanner: BarcodeScanner

    // Controla se já está a processar um código para evitar navegações duplicadas
    private var isProcessing = false

    // Lançador para solicitar permissão de acesso à câmara
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(context, getString(R.string.camera_permission_denied), Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBarcodeScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()
        barcodeScanner = BarcodeScanning.getClient()

        binding.backButton.setOnClickListener { findNavController().navigateUp() }

        // Inicia a câmara se já tiver permissão, caso contrário solicita-a
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * Verifica se a permissão de câmara já foi concedida.
     */
    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Inicializa e liga a câmara traseira ao ciclo de vida do fragmento.
     * Configura a pré-visualização e o analisador de imagens para leitura de códigos.
     */
    private fun startCamera() {
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                // Configura a pré-visualização da câmara no ecrã
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

                // Configura o analisador de imagens, mantendo apenas o frame mais recente
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImageProxy(imageProxy)
                        }
                    }

                // Remove todas as ligações anteriores antes de criar novas
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("BarcodeScanner", "Falha na câmara", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /**
     * Processa cada frame da câmara e tenta detetar um código de barras.
     * Fecha sempre o ImageProxy no final para libertar recursos.
     */
    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    // Processa apenas o primeiro código detetado
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        if (rawValue != null) {
                            Log.d("BarcodeScanner", "Barcode detected: $rawValue")
                            onBarcodeDetected(rawValue)
                            break
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    /**
     * Invocado quando um código de barras é detetado com sucesso.
     * Navega para o ecrã de adição de produto passando o código como argumento.
     * Usa uma flag [isProcessing] para evitar navegações duplicadas.
     */
    private fun onBarcodeDetected(code: String) {
        if (isProcessing) return
        isProcessing = true

        activity?.runOnUiThread {
            Log.d("BarcodeScanner", "Navigating back with code: $code")
            try {
                // Tenta navegar com as direções geradas pelo Safe Args
                val action = BarcodeScannerFragmentDirections
                    .actionBarcodeScannerFragmentToAdicionarProdutoFragment(
                        produtoId = null,
                        barcodeValue = code
                    )
                findNavController().navigate(action)
            } catch (e: Exception) {
                // Caso a navegação com Safe Args falhe, usa um Bundle como alternativa
                Log.e("BarcodeScanner", "Navigation error", e)
                val bundle = Bundle().apply {
                    putString("produtoId", null)
                    putString("barcodeValue", code)
                }
                findNavController().navigate(
                    R.id.action_barcodeScannerFragment_to_adicionarProdutoFragment, bundle
                )
            }
        }
    }

    /**
     * Desliga o executor da câmara e limpa o binding
     * quando a vista é destruída para evitar fugas de memória.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}
