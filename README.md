# ReStock - Sistema Inteligente de Gestão de Despensa 🍏📦

**Projeto Final de Curso (PFC)**
**Licenciatura em Engenharia Informática**
**Autor:** Hugo Moreira (a22402246)

---

## 📑 Resumo do Projeto

O **ReStock** é uma solução móvel desenvolvida para mitigar o problema do desperdício alimentar doméstico e otimizar a gestão de recursos em ambientes familiares. A aplicação permite o controlo rigoroso de inventários, monitorização de prazos de validade e a colaboração em tempo real entre múltiplos utilizadores (família/amigos) através de uma infraestrutura em nuvem.

Este projeto foca-se na aplicabilidade de conceitos de **Sincronização em Tempo Real**, **Arquitetura Reativa** e **Persistência de Dados Híbrida**.

---

## 🚀 Funcionalidades Implementadas

### 🔐 Segurança e Acesso
- **Autenticação Multi-Fator:** Integração com **Firebase Authentication** permitindo login via e-mail/password e **Google Sign-In**.
- **Segurança de Dados:** Implementação de *Security Rules* no Firestore para garantir que apenas membros de uma família acedam aos seus dados.

### 🏠 Gestão Colaborativa (Sistema de Famílias)
- **Protocolo de Convite:** Sistema baseado em códigos únicos para adesão a grupos.
- **Hierarquia de Permissões:** Diferenciação entre administradores (criadores da família) e membros.
- **Sincronização:** Atualizações em tempo real em todos os dispositivos ligados à mesma família.

### 📦 Inventário e Logística
- **CRUD de Produtos:** Gestão completa de itens com metadados (nome, categoria, validade, quantidade).
- **Lista de Compras Dinâmica:** Algoritmo que sugere compras baseado nos níveis críticos de stock.
- **Notificações Push:** Alertas agendados via **WorkManager** para avisar sobre produtos próximos do fim da validade.

### 📊 Análise e Estatística
- **Dashboard Financeiro:** Visualização de gastos e orçamentos mensais utilizando a biblioteca **MPAndroidChart**.
- **Gestão de Perfil:** Upload de imagens e persistência de preferências do utilizador via **Firebase Storage** e **DataStore**.

---

## 🛠 Arquitetura e Engenharia de Software

O projeto segue os princípios de **Clean Architecture** e o padrão de desenho **MVVM (Model-View-ViewModel)**:

- **View:** Atividades e Fragmentos utilizando **View Binding** para uma interação segura com o layout.
- **ViewModel:** Gestão do estado da UI e persistência de dados durante mudanças de configuração (usando `LiveData` e `StateFlow`).
- **Model/Repository:** Camada de dados que abstrai as fontes de informação (Firestore para dados remotos e DataStore para configurações locais).
- **Padrões Adicionais:** Singleton (para instâncias Firebase) e Factory.

---

## 📚 Tecnologias Utilizadas

- **Linguagem:** Kotlin 1.9+
- **Asincronismo:** Kotlin Coroutines & Flow (para operações não bloqueantes).
- **Jetpack Components:** Navigation Component, WorkManager, DataStore, Lifecycle.
- **Backend as a Service (BaaS):** Firebase (Firestore, Auth, Storage).
- **UI:** Material Components (Material 3), ConstraintLayout, Glide (processamento de imagem).

---

## ⚙️ Configuração e Instalação

### Pré-requisitos
- Android Studio Jellyfish ou superior.
- SDK Android 30 (Android 11) ou superior.

### Configuração do Firebase
1. Criar projeto na [Firebase Console](https://console.firebase.google.com/).
2. Adicionar app com o package `com.example.restock`.
3. Inserir o ficheiro `google-services.json` na pasta `/app`.
4. **Importante (Google Sign-In):** No terminal do Android Studio, execute `./gradlew signingReport` e registe o seu **SHA-1** local nas configurações do Firebase para evitar o **Erro 10**.

---

## 🎓 Contexto Académico

Este software foi desenvolvido e submetido como **Projeto Final de Curso (PFC)** para a obtenção do grau de Licenciado em **Engenharia Informática**, demonstrando competências avançadas em desenvolvimento nativo Android, arquitetura de sistemas escaláveis e integração de serviços cloud de última geração.

---

## 👤 Autor
- **Hugo Moreira** - [GitHub](https://github.com/hugomoreira29) (Link opcional)

---
*Este projeto foi desenvolvido com fins exclusivamente académicos.*
