# Server Dashboard

![Java](https://img.shields.io/badge/Java-25-orange?style=for-the-badge&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-green?style=for-the-badge&logo=springboot)
![Spring Security](https://img.shields.io/badge/Spring_Security-6.0-6db33f?style=for-the-badge&logo=springsecurity)
![Status](https://img.shields.io/badge/Status-Active-brightgreen?style=for-the-badge)

> **Uma solução completa de monitoramento de infraestrutura leve e em tempo real, projetada para servidores Linux com foco em segurança e usabilidade.**

## 📖 Sobre o Projeto

O **Server Dashboard** é uma aplicação Full Stack desenvolvida para demonstrar a implementação de uma arquitetura moderna utilizando **Java 25** e **Spring Boot 4**.

O projeto vai além da simples visualização de métricas, oferecendo agora um sistema robusto de gerenciamento administrativo protegido. Ele resolve a necessidade de monitorar a saúde do hardware (CPU, RAM, Disco, Rede e Temperatura) e gerenciar serviços críticos em servidores pessoais ou *homelabs*, eliminando a complexidade de ferramentas corporativas pesadas.

## 🚀 Funcionalidades

### 📊 Monitoramento em Tempo Real (Público)
Acesso instantâneo às métricas vitais do servidor via **WebSockets (STOMP)** com fallback automático para Polling.
* **Hardware (OSHI):**
    * **CPU:** Carga do sistema e monitoramento térmico por núcleo.
    * **Memória:** Uso real e disponível.
    * **Armazenamento:** Análise de partições e espaço livre.
    * **Rede:** Taxas de Download e Upload em tempo real.
* **Visualização:** Dashboards interativos e gráficos históricos (janela de 60s) renderizados com Chart.js.

### 🛡️ Segurança e Administração
Implementação de **Spring Security** para proteção de áreas sensíveis.
* **Controle de Acesso:** Rotas administrativas protegidas (Login necessário).
* **Autenticação:** Sistema de login customizado para administrador.
* **Segregação:** Dados públicos (Dashboard) vs Dados sensíveis (Processos e Serviços).

### ⚙️ Gestão Avançada (Área Restrita)
Ferramentas exclusivas para o administrador logado:
* **Top Processos:** Visualização em tempo real dos processos que mais consomem recursos, com ordenação dinâmica por **CPU** ou **RAM**.
* **Monitoramento de Serviços Dinâmico:**
    * Adicione ou remova portas TCP para monitoramento (Health Check) diretamente pela interface.
    * Verificação de status (Online/Offline) de containers Docker, Bancos de Dados, etc.
    * **Persistência:** As configurações de serviços são salvas automaticamente em JSON (`data/monitored-services.json`), mantendo o estado entre reinícios.

## 🛠️ Tech Stack

**Backend**
* **Java 25:** Utilizando os recursos mais modernos da linguagem.
* **Spring Boot 4.0.2:** Framework core para injeção de dependência e servidor web.
* **Spring WebSocket:** Para comunicação duplex em tempo real.
* **Spring Security:** Para segregação de informações publicas e apenas para administradores.
* **OSHI (Operating System and Hardware Information):** Biblioteca para coleta de métricas de baixo nível.

**Frontend**
* **Thymeleaf + Extras Spring Security:** Renderização server-side com controle de exibição baseado em permissões.
* **Bootstrap 5:** Interface responsiva e Dark Mode nativo.
* **Chart.js:** Gráficos de performance dinâmicos.
* **SockJS & STOMP:** Cliente WebSocket com reconexão automática e resiliência (fallback para HTTP polling).

## 📦 Instalação e Configuração

### Pré-requisitos
* Java JDK 25 instalado.
* Git.

### Passo a Passo

1.  **Clone o repositório:**
    ```bash
    git clone [https://github.com/dioguit0s/server-dashboard.git](https://github.com/dioguit0s/server-dashboard.git)
    cd server-dashboard
    ```

2.  **Configuração de Segurança:**
    Crie um arquivo `.env` na raiz ou configure as variáveis de ambiente para definir a senha do admin:
    ```properties
    DASHBOARD_ADMIN_USERNAME=admin
    DASHBOARD_ADMIN_PASSWORD=sua_senha_segura
    ```

3.  **Execute a Aplicação:**
    O projeto utiliza Maven Wrapper:
    ```bash
    ./mvnw spring-boot:run
    ```

4.  **Acesse:**
    * **Dashboard Público:** `http://localhost:8080`
    * **Área Admin:** Clique em "Login" e use as credenciais configuradas.

- [ ] **Controle de processos:** Possibilidade de encerrar processos diretamente pelo painel de processos.
- [ ] **Painel de controle de Containers:** Feature para verificar uso de CPU/RAM de cada container e controles para Start,Stop e Restart naquele container especificos.
- [ ] **Teste de Ping/Latencia:** Realizar um teste de ping no ip digitado pelo usuario no dashboard

---

<p align="center">
  Desenvolvido por <a href="https://github.com/dioguit0s">Diogo Santos Rodrigues</a> 💻<br>
  <i>Estudante de Engenharia da Computação</i>
</p>
