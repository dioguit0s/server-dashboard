Server Dashboard
Um painel de monitoramento web leve, moderno e em tempo real para servidores Linux. Desenvolvido para quem deseja visualizar a saúde do seu hardware de forma simples e direta, sem configurações complexas.

⚡ Funcionalidades
O Server Dashboard oferece monitoramento contínuo com atualizações instantâneas via WebSocket:

Monitoramento em Tempo Real: Atualização automática de métricas a cada segundo (sem refresh na página).

Recursos de Hardware:

CPU: Uso percentual e temperatura do processador.

Memória RAM: Uso total, livre e percentual.

Armazenamento: Monitoramento de espaço em disco (Total/Usado/Livre).

Status do Sistema: Exibe o Uptime (tempo de atividade) e informações do Sistema Operacional.

Visualização Gráfica: Página dedicada com gráficos históricos (últimos 60 segundos) para CPU, RAM e Temperatura.

Interface Responsiva: Design Dark Mode construído com Bootstrap 5, adaptável para desktop e mobile.

🛠️ Tecnologias
Backend: Java 25, Spring Boot 4, Spring WebSocket.

Hardware Info: OSHI (Operating System and Hardware Information).

Frontend: Thymeleaf, Bootstrap 5, Chart.js, SockJS & STOMP.

🚀 Como Rodar no Seu Servidor
Pré-requisitos
Java JDK 25 instalado.

Git.

Instalação
Clone o repositório:

Bash
git clone https://github.com/dioguit0s/server-dashboard.git
cd server-dashboard
Execute a aplicação: Utilize o wrapper do Maven incluído para garantir a versão correta das dependências:

Bash
# Linux
./mvnw spring-boot:run

Acesse o Painel: Abra seu navegador e vá para:

http://localhost:8080

Dashboard Geral: /

Gráficos: /charts

🗺️ Roadmap
O projeto está em constante evolução. Abaixo estão as próximas funcionalidades planejadas:

[ ] Tráfego de Rede: Visualização de taxas de upload e download em tempo real das interfaces de rede.

[ ] Lista de Processos: Tabela interativa com os top processos consumindo CPU/Memória.

[ ] Sistema de Alertas: Notificações visuais ou externas (E-mail/Discord) para picos críticos de uso (ex: CPU > 90%).

<p align="center"> Desenvolvido por <a href="https://github.com/dioguit0s">Dioguit0s</a> </p>
