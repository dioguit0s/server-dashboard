# Server Dashboard

![Java](https://img.shields.io/badge/Java-25-orange?style=for-the-badge&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-green?style=for-the-badge&logo=springboot)
![Spring Security](https://img.shields.io/badge/Spring_Security-6.0-6db33f?style=for-the-badge&logo=springsecurity)
![Status](https://img.shields.io/badge/Status-Active-brightgreen?style=for-the-badge)
[![CI](https://github.com/dioguit0s/server-dashboard/actions/workflows/ci.yml/badge.svg)](https://github.com/dioguit0s/server-dashboard/actions/workflows/ci.yml)

> **Uma solução completa de monitoramento de infraestrutura leve e em tempo real, projetada para servidores Linux com foco em segurança e usabilidade.**

## 📖 Sobre o Projeto Atual

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
* **Múltiplos usuários:** contas persistidas no H2, com senha em hash BCrypt. O admin inicial
  continua vindo das variáveis de ambiente.
* **Papéis (roles):**
    * **`VIEWER`** — lê métricas, containers, logs, processos e a lista de serviços.
    * **`ADMIN`** — acrescenta as ações de escrita (start/stop/restart de container, edição de
      serviços) e a gestão de usuários.
* **2FA (TOTP):** verificação em duas etapas opcional por conta, compatível com Google
  Authenticator, Aegis, 1Password e afins, com QR code gerado pelo próprio servidor e 10 códigos de
  recuperação de uso único. Pode ser tornado obrigatório para todo `ADMIN`.
* **Proteção contra brute-force:** Rate limiting no login — após N falhas o IP de origem é bloqueado por uma janela configurável, com uma trava global de reserva e registro em `WARN` para auditoria. Vale para os dois passos do login (senha e segundo fator).
* **Segregação:** Dados públicos (Dashboard) vs Dados sensíveis (Processos, Serviços e Containers).

### ⚙️ Gestão Avançada (Área Restrita)
Ferramentas da área logada:
* **Gestão de Usuários** (`/admin/users`, só `ADMIN`):
    * Criar contas, alternar entre `VIEWER` e `ADMIN`, habilitar/desabilitar e remover.
    * Redefinir a senha de outro usuário e desligar o 2FA de quem perdeu o autenticador.
    * Travas contra se trancar para fora: ninguém remove ou desabilita a própria conta, nem remove,
      rebaixa ou desabilita o **último administrador ativo**.
* **Minha Conta** (`/account`): trocar a própria senha e gerenciar o 2FA (ativar via QR code,
  reemitir códigos de recuperação, desativar).

* **Top Processos:** Visualização em tempo real dos processos que mais consomem recursos, com ordenação dinâmica por **CPU** ou **RAM**.
* **Monitoramento de Serviços Dinâmico:**
    * Adicione ou remova portas TCP para monitoramento (Health Check) diretamente pela interface.
    * Verificação de status (Online/Offline) de containers Docker, Bancos de Dados, etc.
    * **Persistência:** As configurações de serviços são salvas automaticamente em JSON (`data/monitored-services.json`), mantendo o estado entre reinícios.
* **Painel de Containers Docker** (`/containers`):
    * Uso de CPU e RAM por container em tempo real via Docker Engine.
    * Controles de **Start**, **Stop** e **Restart** por container.
    * Filtro por estado (running/parados) e detecção de Docker indisponível.
* **Visualizador de Logs** (`/logs?container=ID`):
    * Exibe as últimas linhas de log de um container com atualização manual ou auto-refresh.

## 🛠️ Tech Stack

**Backend**
* **Java 25:** Utilizando os recursos mais modernos da linguagem.
* **Spring Boot 4.0.2:** Framework core para injeção de dependência e servidor web.
* **Spring WebSocket:** Para comunicação duplex em tempo real.
* **Spring Security:** Autenticação, papéis (`VIEWER`/`ADMIN`) e segregação entre informações públicas e restritas.
* **Spring Data JPA + H2:** Persistência das métricas históricas e dos usuários do dashboard.
* **TOTP (RFC 6238) + ZXing:** Verificação em duas etapas implementada no projeto, com o QR code do enrollment gerado no servidor.
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
* **Docker CLI** disponível no `PATH` (necessário para o painel de containers e logs).
* No Linux, o usuário que executa a aplicação deve pertencer ao grupo `docker` para permitir `docker stats`, `docker start|stop|restart` e `docker logs`.

### Passo a Passo

1.  **Clone o repositório:**
    ```bash
    git clone [https://github.com/dioguit0s/server-dashboard.git](https://github.com/dioguit0s/server-dashboard.git)
    cd server-dashboard
    ```

2.  **Configuração de Segurança:**
    Crie um arquivo `.env` na raiz ou configure as variáveis de ambiente com as credenciais do admin.
    A forma recomendada é fornecer a senha **já em hash BCrypt**, para que ela não fique em texto
    puro no ambiente do processo:
    ```properties
    DASHBOARD_ADMIN_USERNAME=admin
    DASHBOARD_ADMIN_PASSWORD_HASH=$2a$10$....
    ```

    Para gerar o hash, use qualquer ferramenta BCrypt — por exemplo:
    ```bash
    # com apache2-utils (htpasswd)
    htpasswd -bnBC 12 "" 'sua_senha_segura' | tr -d ':\n'

    # ou com Python (pip install bcrypt)
    python3 -c "import bcrypt; print(bcrypt.hashpw(b'sua_senha_segura', bcrypt.gensalt(12)).decode())"
    ```

    > O valor pode ser colado com ou sem o prefixo de algoritmo (`$2a$10$...` ou `{bcrypt}$2a$10$...`).
    > No `.env`, cifrões não precisam de escape; em um shell, use aspas simples ao exportar a variável
    > para o `$` não ser interpretado.

    Alternativamente, a senha em texto puro continua funcionando (ela é convertida em hash na
    inicialização), mas emite um `WARN` recomendando a migração:
    ```properties
    DASHBOARD_ADMIN_USERNAME=admin
    DASHBOARD_ADMIN_PASSWORD=sua_senha_segura
    ```

    > ⚠️ Se nenhuma das duas variáveis estiver definida, a aplicação **não sobe** — em vez de subir
    > com uma senha vazia. `DASHBOARD_ADMIN_PASSWORD_HASH` tem precedência sobre
    > `DASHBOARD_ADMIN_PASSWORD` quando ambas estão presentes.

    > 🧑‍🤝‍🧑 Essas variáveis provisionam o **admin inicial**: no primeiro boot ele é criado no banco
    > com o papel `ADMIN`. A partir daí o banco é a verdade — uma senha trocada em `/account` ou os
    > usuários criados em `/admin/users` **não** são sobrescritos por um restart. A variável continua
    > sendo lida a cada boot (e a aplicação continua exigindo que ela exista), mas só cria a conta
    > quando ela ainda não existe.

    **Verificação em duas etapas (opcional):**
    Cada usuário ativa o próprio 2FA em `/account`. Para exigir 2FA de todos os administradores:
    ```properties
    DASHBOARD_TOTP_REQUIRE_ADMIN=true
    ```

    > ⚠️ Ligue isso **depois** de o admin ter concluído o enrollment. Com a exigência ativa, um
    > `ADMIN` sem 2FA só consegue chegar à tela da própria conta — e as ações de escrita via API
    > respondem `403` até ele ativar. Se ninguém puder escanear o QR no momento, o dashboard fica
    > inutilizável até que a variável seja desligada.

    > Se um usuário perder o autenticador, outro admin desliga o 2FA dele em `/admin/users`; se for
    > o próprio (e único) admin, os códigos de recuperação são a saída — por isso vale guardá-los.

    Opcionalmente, ajuste o rate limiting do login (valores abaixo são os padrões):
    ```properties
    # Falhas do mesmo IP antes do bloqueio
    DASHBOARD_LOGIN_MAX_ATTEMPTS=5
    # Duração do bloqueio, em minutos
    DASHBOARD_LOGIN_LOCKOUT_MINUTES=15
    # Trava global: total de falhas de quaisquer IPs antes de travar o login inteiro (0 desativa)
    DASHBOARD_LOGIN_GLOBAL_MAX_ATTEMPTS=25
    # Teto de IPs rastreados em memória
    DASHBOARD_LOGIN_MAX_TRACKED_IPS=10000
    ```

    > ⚠️ O IP de origem é lido respeitando `X-Forwarded-For` (`server.forward-headers-strategy=framework`).
    > Se houver um reverse proxy na frente, configure-o para **sobrescrever** esse header em vez de anexá-lo —
    > caso contrário um cliente pode forjar o IP e escapar do bloqueio. A trava global
    > (`DASHBOARD_LOGIN_GLOBAL_MAX_ATTEMPTS`) existe justamente como rede de proteção para esse cenário.

    **Origens do WebSocket (obrigatório fora de desenvolvimento):**
    O handshake SockJS/STOMP é liberado por origem. O padrão de fábrica é o curinga `*`, e com ele
    qualquer página aberta em outra aba por um admin autenticado consegue abrir a conexão com o
    cookie de sessão dele e ler os tópicos protegidos (containers, processos, serviços). Por isso a
    aplicação **não sobe** com o curinga fora de um perfil de desenvolvimento — liste as origens
    pelas quais o dashboard é acessado:
    ```properties
    # produção: as origens reais do dashboard, separadas por vírgula
    DASHBOARD_WS_ORIGINS=https://meudominio.com
    ```

    > Padrões de subdomínio (`https://*.meudominio.com`) e porta (`http://localhost:*`) continuam
    > valendo — o que é recusado é o curinga de host (`*`, `http://*`, `https://*`, `*://*`).

    Em desenvolvimento, use o perfil `dev` (passo 3), que já limita as origens ao `localhost`.

    > Se a instância estiver em uma LAN fechada e o curinga for aceitável, é possível assumir o
    > risco explicitamente com `DASHBOARD_WS_ALLOW_WILDCARD=true` — a inicialização passa a emitir
    > um `WARN` em vez de falhar.

3.  **Execute a Aplicação:**
    O projeto utiliza Maven Wrapper:
    ```bash
    # desenvolvimento (origens do WebSocket limitadas ao localhost)
    ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

    # produção (com DASHBOARD_WS_ORIGINS configurado)
    ./mvnw spring-boot:run
    ```

4.  **Acesse:**
    * **Dashboard Público:** `http://localhost:8080`
    * **Área Admin:** Clique em "Login" e use as credenciais configuradas.
    * **Containers:** `http://localhost:8080/containers` (após login)
    * **Logs de container:** `http://localhost:8080/logs?container=<ID>` (após login)
    * **Minha conta / 2FA:** `http://localhost:8080/account` (após login)
    * **Usuários:** `http://localhost:8080/admin/users` (após login, só `ADMIN`)

- [x] **Painel de controle de Containers:** CPU/RAM por container, Start/Stop/Restart e visualizador de logs.
- [x] **Múltiplos usuários, papéis e 2FA:** contas no banco, separação entre leitura (`VIEWER`) e escrita (`ADMIN`) e verificação em duas etapas por TOTP.
- [ ] **Teste de Ping/Latencia:** Realizar um teste de ping no ip digitado pelo usuario no dashboard

---

<p align="center">
  Desenvolvido por <a href="https://github.com/dioguit0s">Diogo Santos Rodrigues</a> 💻<br>
  <i>Estudante de Engenharia da Computação</i>
</p>
