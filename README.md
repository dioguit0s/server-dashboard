# Server Dashboard

Um painel de controle web simples e eficiente para monitoramento de recursos de servidor. Este projeto está em desenvolvimento ativo e tem como objetivo fornecer métricas vitais de hardware (CPU, RAM, Sistema Operacional) para administração de sistemas.

## 🚀 Sobre o Projeto

Atualmente, o **Server Dashboard** é uma aplicação MVP (Minimum Viable Product) que exibe um "snapshot" instantâneo do estado do servidor. Ele foi construído utilizando **Java** e **Spring Boot**, aproveitando a biblioteca **OSHI** para extração de dados de baixo nível do hardware.

O objetivo é evoluir desta versão estática para uma central de monitoramento completa e em tempo real.

## 🛠 Tecnologias Utilizadas

* **Java 25**
* **Spring Boot** (Web, Thymeleaf, DevTools)
* **OSHI (Operating System and Hardware Information)** - Para coleta de métricas do sistema.
* **HTML/CSS** - Interface frontend inicial.

## 📊 Funcionalidades Atuais

Nesta fase inicial, o dashboard oferece:
* **Identificação do Sistema:** Exibe o nome e versão do Sistema Operacional.
* **Monitoramento de CPU:** Mostra a porcentagem de uso atual do processador.
* **Monitoramento de RAM:** Exibe a memória total disponível e a memória livre atual formatada em GB.

## 🗺 Roadmap & Melhorias Futuras

Este projeto está no ínicio de desenvolvimento servirá como base para implementações avançadas. Abaixo estão as melhorias planejadas e ideias para o futuro:

### 🔄 Atualização em Tempo Real (Prioridade)
- [ ] Adicionar gráficos dinâmicos para visualizar o histórico de consumo nos últimos minutos.

### 🎨 Design e UX
- [ ] **Responsividade:** Garantir que o painel funcione bem em dispositivos móveis.

### 💡 Features previstas
- **Tráfego de Rede:** Mostrar taxas de upload e download em tempo real da interface de rede principal.
- **Uptime do Sistema:** Exibir há quanto tempo o servidor está ligado.
- **Informações de Temperatura:** Mostrar a temperatura da CPU (se o hardware permitir acesso aos sensores).
- **Lista de Processos:** Uma tabela com os top 5 processos que mais consomem memória ou CPU no momento.
- **Sistema de Alertas:** Configurar notificações visuais (ou por e-mail/Discord) caso a CPU passe de 90% ou a RAM fique abaixo de 10%.

## 🚀 Como Executar

1. Clone o repositório:
   ```bash
   git clone [https://github.com/seu-usuario/server-dashboard.git](https://github.com/seu-usuario/server-dashboard.git)
Navegue até a pasta do projeto e execute com o Maven Wrapper:

```Bash
  ./mvnw spring-boot:run
```

Acesse no navegador:
http://localhost:8080
