# 📦 CARGA-IA - Mensageria com RabbitMQ

Este projeto demonstra um **sistema distribuído para classificação de imagens**.  
Um **serviço produtor** em Java envia imagens de **faces** e **times** para uma fila no **RabbitMQ**.  
Dois **serviços consumidores com IA** processam, classificam e salvam as imagens em pastas de saída.  
Todo o ambiente é **orquestrado com Docker Compose**.  


## 🚀 Tecnologias  
- **Linguagem:** Java 11 (Maven)  
- **Mensageria:** RabbitMQ  
- **Machine Learning:** Smile ML  
- **Containerização:** Docker + Docker Compose  


## 📂 Estrutura de Diretórios  
.
├── docker-compose.yml
├── gerador-de-mensagens/ # Serviço produtor (Java)
├── consumidor-face/ # Serviço consumidor de faces
├── consumidor-time/ # Serviço consumidor de times
├── imagens/ # Imagens para ENVIO
│ ├── image-faces/
│ └── image-times/
├── imagenstreino/ # Imagens para TREINO
│ ├── image-faces/
│ └── image-times/
├── imagens-recebidas-face/ # Saída das imagens de faces
└── imagens-recebidas-time/ # Saída das imagens de times


## ▶️ Como Executar  

### 🔧 Pré-requisitos  
- Docker  
- Docker Compose  

### 📑 Preparação  
1. Clone este repositório.  
2. Adicione suas imagens de **envio** em:  
   - `imagens/image-faces/`  
   - `imagens/image-times/`  
3. Adicione imagens de **treino** (exemplo: `FELIZ_1.jpg`, `flamengo.png`) em:  
   - `imagenstreino/image-faces/`  
   - `imagenstreino/image-times/`  

### ▶️ Rodar o projeto  
```bash
docker-compose up --build
🔍 Verificar
Imagens classificadas aparecerão em:

imagens-recebidas-face/

imagens-recebidas-time/

Acesse a interface do RabbitMQ em:
👉 http://localhost:15672

Usuário: user

Senha: password

🛑 Parar o ambiente
Pressione Ctrl + C no terminal e depois rode:

bash
Copiar código
docker-compose down