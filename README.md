# Server Dashboard

![Java](https://img.shields.io/badge/Java-25-orange) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-green) ![Status](https://img.shields.io/badge/Status-Active-brightgreen)

Um painel de monitoramento web leve, moderno e em tempo real para servidores Linux. Desenvolvido para quem deseja visualizar a saúde do seu hardware de forma simples e direta, sem configurações complexas.

## ⚡ Funcionalidades

O **Server Dashboard** oferece monitoramento contínuo com atualizações instantâneas via WebSocket:

* **Monitoramento em Tempo Real:** Atualização automática de métricas a cada segundo (sem *refresh* na página).
* **Recursos de Hardware:**
    * **CPU:** Uso percentual e temperatura do processador.
    * **Memória RAM:** Uso total, livre e percentual.
    * **Armazenamento:** Monitoramento de espaço em disco (Total/Usado/Livre).
* **Status do Sistema:** Exibe o *Uptime* (tempo de atividade) e informações do Sistema Operacional.
* **Visualização Gráfica:** Página dedicada com gráficos históricos (últimos 60 segundos) para CPU, RAM e Temperatura.
* **Interface Responsiva:** Design *Dark Mode* construído com Bootstrap 5, adaptável para desktop e mobile.

## 🛠️ Tecnologias

* **Backend:** Java 25, Spring Boot 4, Spring WebSocket.
* **Hardware Info:** OSHI (Operating System and Hardware Information).
* **Frontend:** Thymeleaf, Bootstrap 5, Chart.js, SockJS & STOMP.

## 🚀 Como Rodar no Seu Servidor

### Pré-requisitos
* Java JDK 25 instalado.
* Git.

### Instalação

1.  **Clone o repositório:**
    ```bash
    git clone [https://github.com/dioguit0s/server-dashboard.git](https://github.com/dioguit0s/server-dashboard.git)
    cd server-dashboard
    ```

2.  **Execute a aplicação:**
    Utilize o *wrapper* do Maven incluído para garantir a versão correta das dependências:
    ```bash
    # Linux / macOS
    ./mvnw spring-boot:run

    # Windows
    mvnw.cmd spring-boot:run
    ```

3.  **Acesse o Painel:**
    Abra seu navegador e vá para:
    > **http://localhost:8080**

    * **Dashboard Geral:** `/`
    * **Gráficos:** `/charts`

## 🗺️ Roadmap

O projeto está em constante evolução. Abaixo estão as próximas funcionalidades planejadas:

- [ ] **Monitoramento de containers:** Listar quantos containers estão Running, Stopped e Paused, e talvez listar os nomes dos ativos.
- [ ] **Health check de serviços:** Um painel com Cards coloridos (Verde/Vermelho) indicando se um serviço específico está rodando.

---
<p align="center">
  Desenvolvido por <a href="https://github.com/dioguit0s">Diogo</a>
</p>
