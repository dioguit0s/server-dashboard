# Server Dashboard

![Java](https://img.shields.io/badge/Java-25-orange?style=for-the-badge&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-green?style=for-the-badge&logo=springboot)
![Status](https://img.shields.io/badge/Status-Active-brightgreen?style=for-the-badge)

> **Uma solução de monitoramento de infraestrutura leve e em tempo real, projetada para servidores Linux.**

## 📖 Sobre o Projeto

O **Server Dashboard** é uma aplicação de código aberto desenvolvida como parte do meu portfólio acadêmico no curso de **Engenharia da Computação**.

O objetivo principal deste projeto é demonstrar a implementação de uma arquitetura Full Stack moderna, utilizando **Java 25** e **Spring Boot 4** para criar um sistema de telemetria eficiente. Ele resolve a necessidade de visualizar a saúde do hardware (CPU, RAM, Disco e Temperatura) de forma instantânea, eliminando a complexidade de ferramentas corporativas pesadas para casos de uso em servidores pessoais ou *homelabs*.

## 🚀 Principais Funcionalidades

A aplicação utiliza **WebSockets (STOMP)** para garantir que os dados sejam "empurrados" (*push*) para o cliente, garantindo atualização instantânea sem a necessidade de *polling* constante.


* **Telemetria em Tempo Real:** Atualização de métricas via WebSocket a cada segundo.
* **Monitoramento de Hardware (OSHI):**
    * **CPU:** Carga do sistema e monitoramento térmico.
    * **Memória:** Alocação dinâmica e uso real.
    * **Armazenamento:** Análise de partições e espaço disponível.
* **Health Check de Serviços:** Verificação de disponibilidade de portas TCP locais (ex: verificar se bancos de dados ou containers estão rodando).
* **Visualização de Dados:** Dashboards interativos e gráficos históricos (janela de 60s) renderizados com Chart.js.
* **Interface Responsiva:** UI moderna com *Dark Mode* nativo, construída sobre Bootstrap 5.

## 🛠️ Tech Stack

O projeto foi construído explorando as tecnologias mais recentes do ecossistema Java:

**Backend**
* **Java 25:** Utilizando os recursos mais modernos da linguagem.
* **Spring Boot 4.0.2:** Framework core para injeção de dependência e servidor web.
* **Spring WebSocket:** Para comunicação duplex em tempo real.
* **Spring Security:** Para segregação de informações publicas e apenas para administradores.
* **OSHI (Operating System and Hardware Information):** Biblioteca para coleta de métricas de baixo nível.

**Frontend**
* **Thymeleaf:** Template engine para renderização server-side.
* **Bootstrap 5:** Framework CSS para estilização responsiva.
* **Chart.js:** Biblioteca para plotagem de gráficos de performance.
* **SockJS & STOMP:** Clientes JavaScript para conexão com o WebSocket.

## 📦 Instalação e Execução

### Pré-requisitos
* Java JDK 25 instalado.
* Git.

### Passo a Passo

1.  **Clone o repositório:**
    ```bash
    git clone [https://github.com/dioguit0s/server-dashboard.git](https://github.com/dioguit0s/server-dashboard.git)
    cd server-dashboard
    ```

2.  **Compile e Execute:**
    O projeto inclui o Maven Wrapper, garantindo que você rode com as dependências exatas sem precisar instalar o Maven manualmente.

    **Linux :**
    ```bash
    ./mvnw spring-boot:run
    ```

3.  **Acesse o Dashboard:**
    Navegue até `http://localhost:8080`.

## 🗺️ Roadmap de Desenvolvimento

Como um projeto ativo de estudo, as seguintes melhorias estão planejadas:

- [ ] **Controle de processos:** Possibilidade de encerrar processos diretamente pelo painel de processos.
- [ ] **Painel de controle de Containers:** Feature para verificar uso de CPU/RAM de cada container e controles para Start,Stop e Restart naquele container especificos.
- [ ] **Teste de Ping/Latencia:** Realizar um teste de ping no ip digitado pelo usuario no dashboard

---

<p align="center">
  Desenvolvido por <a href="https://github.com/dioguit0s">Diogo Santos Rodrigues</a> 💻<br>
  <i>Estudante de Engenharia da Computação</i>
</p>
