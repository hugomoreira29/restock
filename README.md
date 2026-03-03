# ReStock 🍏📦

**ReStock** é uma aplicação Android moderna desenvolvida para ajudar utilizadores e famílias a gerir o inventário da sua despensa de forma eficiente, evitando o desperdício alimentar e otimizando as compras.

Este projeto foi desenvolvido no âmbito da unidade curricular de **Projeto de Dispositivos Móveis**.

---

## 🚀 Funcionalidades Principal

- **Autenticação Segura:** Registo e Login via Email/Password ou **Google Sign-In**.
- **Gestão de Famílias:** Cria ou junta-te a uma "Família" através de um código de convite para partilhar o inventário em tempo real.
- **Inventário Inteligente:** Adiciona produtos com nome, categoria, quantidade e data de validade.
- **Lista de Compras:** Geração de listas de compras sugeridas com base no que falta na despensa.
- **Controlo de Validades:** Notificações e avisos visuais para produtos prestes a expirar.
- **Orçamento e Poupança:** Gráficos e resumos (via MPAndroidChart) para controlar os gastos mensais.
- **Perfil Personalizado:** Edição de dados e foto de perfil (armazenada no Firebase Storage).

---

## 🛠 Tech Stack & Bibliotecas

- **Linguagem:** [Kotlin](https://kotlinlang.org/)
- **Arquitetura:** MVVM (Model-View-ViewModel) com View Binding.
- **Base de Dados & Auth:** [Firebase](https://firebase.google.com/) (Firestore, Auth, Storage).
- **UI/UX:** Material Design 3, Google Fonts, ConstraintLayout e Lottie Animations.
- **Gráficos:** [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart).
- **Imagens:** [Glide](https://github.com/bumptech/glide) & CircleImageView.
- **Background Tasks:** [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) (para verificação de validades).
- **Local Storage:** [DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore).

---

## ⚙️ Configuração do Projeto

Para correr este projeto localmente, segue estes passos:

1. **Clonar o repositório:**
   ```bash
   git clone https://github.com/seu-utilizador/project_dispositivos_moveis.git
   ```

2. **Configuração do Firebase:**
   - Cria um projeto na [Firebase Console](https://console.firebase.google.com/).
   - Adiciona uma App Android com o package name `com.example.restock_pg_dispositivo_moveis`.
   - Faz o download do ficheiro `google-services.json` e coloca-o na pasta `app/`.
   - Ativa o **Email/Password** e o **Google** no Authentication.
   - Ativa o **Firestore** e o **Storage**.

3. **Google Sign-In (Erro 10):**
   - Para que o Login com Google funcione, precisas de adicionar o teu **SHA-1** local às configurações do projeto no Firebase.
   - No Android Studio, abre o terminal e corre:
     ```bash
     ./gradlew signingReport
     ```
   - Copia o SHA-1 e cola-o na consola do Firebase (Project Settings > Your Apps).

---

## 📱 Ecrãs da Aplicação

- **Splash Screen:** Introdução animada com a marca.
- **Login/Registo:** Design moderno e responsivo com Material Cards.
- **Home:** Resumo estatístico do inventário e orçamento.
- **Inventário:** Lista detalhada de produtos com filtros por categoria.
- **Definições:** Gestão de conta, família e preferências de idioma.

---

## 👤 Autor

- **Hugo Moreira** - (a22402246)

---

## 📄 Licença

Este projeto é para fins académicos. Todos os direitos reservados.
