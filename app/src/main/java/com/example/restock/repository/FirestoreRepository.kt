package com.example.restock.repository
// Modelo do produto para mapeamento dos dados do Firestore
import com.example.restock.model.Product

// Firebase - base de dados e resultado de queries
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

// Coroutines - para executar operações assíncronas sem bloquear a aplicação
import kotlinx.coroutines.tasks.await

/** HUGO MOREIRA - a22402246
 * FirestoreRepository.kt
 * Responsável por gerir os dados do utilizador autenticado.
 */
class FirestoreRepository {

    // Instância da base de dados Firestore
    private val db = FirebaseFirestore.getInstance()

    // Referência à coleção de produtos no Firestore
    private val productsCollection = db.collection("products")

    /**
     * Guarda um produto na coleção "products" do Firestore.
     * Se o produto não tiver ID, é criado um novo documento automaticamente.
     * Se já tiver ID, o documento existente é atualizado.
     * Devolve true se a operação for bem sucedida, ou false em caso de erro.
     */
    suspend fun saveProduct(product: Product): Boolean {
        return try {
            // Se o produto não tiver ID, cria uma nova referência com ID automático,
            // caso contrário usa a referência do documento já existente
            val docRef = if (product.id.isEmpty()) {
                productsCollection.document()
            } else {
                productsCollection.document(product.id)
            }

            // Atualiza o produto com o ID do documento gerado pelo Firestore
            val newProduct = product.copy(id = docRef.id)

            // Guarda o produto no Firestore e aguarda a conclusão
            docRef.set(newProduct).await()

            true
        } catch (e: Exception) {
            // Em caso de erro, imprime o mesmo e devolve false
            e.printStackTrace()
            false
        }
    }

    /**
     * Obtém a lista de produtos pertencentes a uma determinada família.
     * Filtra os produtos pelo campo "familiaId" e devolve uma lista de objetos Product.
     * Devolve uma lista vazia caso ocorra algum erro ou não existam produtos.
     */
    suspend fun getProductsByFamily(familiaId: String): List<Product> {
        return try {
            // Pesquisa todos os produtos que pertencem à família indicada
            val snapshot: QuerySnapshot = productsCollection
                .whereEqualTo("familiaId", familiaId)
                .get()
                .await()

            // Converte os documentos obtidos em objetos Product
            snapshot.toObjects(Product::class.java)

        } catch (e: Exception) {
            // Em caso de erro, imprime o mesmo e devolve uma lista vazia
            e.printStackTrace()
            emptyList()
        }
    }
}
