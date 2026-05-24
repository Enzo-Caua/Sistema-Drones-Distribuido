# Infraestrutura Distribuída para Coordenação de Drones - Estreito de Ormuz

Este projeto consiste em uma infraestrutura distribuída e descentralizada para a coordenação de uma frota compartilhada de drones autônomos. O sistema utiliza algoritmos de consenso para gerenciar o monitoramento marítimo no Estreito de Ormuz, garantindo a exclusão mútua e a resiliência a falhas sem depender de um servidor central.

## 📌 Sumário

* [Softwares Utilizados](#-softwares-utilizados)
* [Arquitetura do Sistema](#-arquitetura-do-sistema)
* [Algoritmos Distribuídos](#-algoritmos-distribuídos)
* [Estrutura do Projeto](#-estrutura-do-projeto)
* [Instalação e Execução](#-instalação-e-execução)
* [Visualização das Interfaces (Attach)](#-visualização-das-interfaces-attach)
* [Testes de Resiliência (Tolerância a Falhas)](#-testes-de-resiliência-tolerância-a-falhas)
* [Autores](#-autores)

---

## 💻 Softwares Utilizados

* **Java JDK 21** – Linguagem base para a implementação da lógica distribuída.
* **Maven** – Gerenciador de automação de build e dependências.
* **Docker & Docker Compose** – Conteinerização e isolamento de cada nó da rede.
* **Docker Swarm** – Orquestrador para gerenciamento da rede *overlay* entre múltiplos computadores.
* **Java Sockets API** – Comunicação TCP confiável entre Brokers, Drones e Sensores.

---

## 🏗 Arquitetura do Sistema

O sistema é totalmente descentralizado e composto por seis tipos de componentes:

1.  **Brokers de Setor:** Orquestradores distribuídos que gerenciam a fila global e decidem o despacho de drones.
2.  **Drones (Atuadores):** Executam missões de monitoramento e reportam status/falhas.
3.  **Sensores:** Detectam ocorrências e as reportam aos brokers com diferentes níveis de urgência.
4.  **Dashboard de Saúde:** Monitora *heartbeats* e exibe **IP e Porta** de cada componente.
5.  **Monitores Globais:** Visualização da frota e da **fila global** (exibindo origem e total de pendências).
6.  **Validador (Auditor):** Garante a exclusão mútua, gerando o `log_consistencia.txt`.

---

## 🛠 Algoritmos Distribuídos Implementados

*   **Ricart-Agrawala (Exclusão Mútua):** Adaptado para ignorar nós falhos e evitar bloqueios de quórum.
*   **Relógios Lógicos de Lamport:** Garantem a ordenação causal e filas idênticas em todos os brokers.
*   **Fila com Aging (Envelhecimento):** Evita *starvation* aumentando a prioridade de missões antigas.
*   **Service Discovery via DNS:** Localização de contêineres por nome através da rede *overlay* do Docker.

---

## 🚀 Instalação e Execução

O sistema possui dois arquivos de configuração para diferentes cenários de teste:

### 1. Execução Local (Máquina Única)
Utiliza o arquivo `docker-compose.local.yaml`. Ideal para testes rápidos de lógica.
```bash
# Na raiz do projeto, execute em modo detached:
docker-compose -f docker-compose.local.yaml up -d --build
```

### 2. Execução em Rede (Docker Swarm)
Utiliza o arquivo `docker-compose.rede.yaml` para interligar múltiplos computadores físicos.

**No computador Gerente (Manager):**
```bash
# 1. Inicializa o cluster (Anote o token gerado)
docker swarm init --advertise-addr <IP_DO_MANAGER>

# 2. Realiza o deploy da stack (Apos iniciar os Workers)
docker stack deploy -c docker-compose.rede.yaml frota_drones
```

**Nos computadores Trabalhadores (Workers):**
```bash
# Utilize o token gerado no Manager para entrar no cluster
docker swarm join --token <TOKEN_GERADO> <IP_DO_MANAGER>:2377
```

---

## 📺 Visualização das Interfaces (Attach)

As interfaces do sistema (Dashboard, Monitores e Validador) rodam em terminais ASCII. Todas exibem o **horário (HH:mm:ss) da última atualização**. Para visualizá-las, anexe seu terminal ao contêiner:

```bash
# Conectar ao Dashboard (IPs, Portas e Saúde)
docker attach dashboard

# Conectar ao Monitor da Frota (Status dos Drones)
docker attach monitor-global

# Conectar ao Monitor da Fila (Pendências, Origens e Aging)
docker attach monitor-fila

# Conectar ao Validador (Auditoria de Consistência)
docker attach validator
```
> [!IMPORTANTE]
> **Para sair do modo attach sem matar o contêiner:** Pressione a sequência **`Ctrl + P`** seguida de **`Ctrl + Q`**.

---

## 🧪 Testes de Resiliência (Tolerância a Falhas)

Utilize os comandos abaixo para testar a robustez da solução:

1.  **Morte de um Broker:** `docker stop broker1`. 
    *   *Resultado:* O quórum de votos do algoritmo Ricart-Agrawala deve se ajustar automaticamente, e o despacho deve continuar.
2.  **Falha de um Drone:** `docker stop drone3`.
    *   *Resultado:* O broker detectará a falha e reinserirá a missão no topo da fila global para que outro drone a assuma.
3.  **Recuperação de Nó:** `docker start broker1`.
    *   *Resultado:* Ao subir, o broker executará o `SYNC_QUEUE_REQUEST` para sincronizar sua fila local com os vizinhos ativos.

---

## 👥 Autores

*   **Enzo Cauã da S. Barbosa**

Tutoria: **Prof. Dr. José Amancio Macedo Santos**
Disciplina: **TEC502 - Sistemas Distribuídos (UEFS)**
