# 🏛️ Especificação Técnica: Framework Athena (v3.3)

**Projeto:** Arquitetura Híbrida Neuro-Simbólica Assíncrona (Jason/BDI + LLM)  
**Data:** 16 de Fevereiro de 2026  
**Hardware Alvo:** Raspberry Pi 5 (8GB RAM)  
**Paradigma:** Processamento Paralelo (Córtex vs. Reflexo).  

---

## 1. Visão Geral da Arquitetura

O **Framework Athena** é um middleware para o Jason (BDI) que separa estritamente a Cognição (LLM) da Execução (Simbólica).
**Filosofia:** O Logos observa sem travar, o Syllabus processa em background, o Praxis injeta quando pronto e gerencia a memória.

### A Trindade Arquitetural

1.  **LOGOS:** O Observador (Dispara processos sem travar o agente).
2.  **SYLLABUS:** O Córtex (Processamento pesado em Thread separada).
3.  **PRAXIS:** O Injetor (Callback, Validação e Gestor de Memória).
4.  *(Infra)* **AI SERVICE:** O Driver bruto do Ollama. Acessado por ações simples (ex `.ask_llm`)

---

## 2. Componente: LOGOS (O Observador)

*Responsabilidade: Monitorar estados e disparar processos em background.*

### 2.1. Configuração e Inicialização
O Logos é configurado via diretiva no código `.asl`.

    // Athena.Logos(TimeoutOcio_ms, [Gatilhos], [PlanosPosCognicao])
    Athena.Logos(10000, [bateria, perigo], [atualizar_dashboard]).

### 2.2. Comportamento "Non-Blocking"
1.  **Estado LIVRE (Idle):** Monitora tempo e crenças gatilho. Dispara Syllabus assincronamente.
2.  **Estado PENSANDO (Busy):** Ignora novos gatilhos para evitar sobrecarga, delegando para o próximo ciclo.

---

## 3. Componente: SYLLABUS (O Córtex)

*Responsabilidade: Processamento pesado, tradução e gestão de contexto.*

### 3.1. Execução Isolada
Roda em **Thread Dedicada**. Se o Ollama demorar, o Jason continua vivo.

### 3.2. Tradução Estrita
Aplica Regex na saída da LLM para garantir que apenas comandos KQML válidos cheguem ao Praxis.

### 3.3. Gestão de Contexto
Mantém o `context_vector`. Se a Persona mudar, invalida o cache automaticamente.

---

## 4. Componente: PRAXIS (O Injetor e Zelador)

*Responsabilidade: Validação, Injeção e Políticas de Memória.*

### 4.1. Callback e Injeção
Quando o Syllabus termina:
1.  **Valida:** Regex + Parser Jason.
2.  **Carimba:** Adiciona `[source(athena)]`.
3.  **Injeta:** Adiciona à base de crenças/planos.
4.  **Finaliza:** Executa os planos de `[PlanosPosCognicao]` definidos no Logos.

### 4.2. Gestão de Memória Automática (Políticas)

* **Modo Crítico (Nap):** `Athena.Praxis.nap(ItensManter, Ram%)`. Monitora RAM física. Se estourar, mantém X itens e apaga o resto.
* **Modo Frequência (Collector):** `Athena.Praxis.garbage_counter_collector(ItensManter, Minutos)`. A cada X minutos, mantém os mais usados e apaga os efêmeros não utilizados.

---

## 5. Referência da API (StdLib)

Ações internas disponíveis para uso direto no `.asl` para controlar a arquitetura.

### 5.1. Gestão de Identidade e Contexto
Prepara o "cérebro" antes de iniciar o pensamento.

* `.addPersona(Texto/Arquivo)`: Define a personalidade.
* `.reflectPlans`: Lê o código fonte (`.asl`) do agente e carrega na memória da LLM (Self-Reflection).
* `.addContext(Tipo, Conteúdo)`: Adiciona dados extras (ex: `image`, `logs`).
* `.removeContext(Tipo)`: Limpa contextos específicos.

### 5.2. Ciclo de Vida Cognitivo (Sessão)
Controla a conexão e a memória de curto prazo.

* `.startThink([Modelo])`: Inicializa a sessão com a LLM. Adiciona a crença `incorporated` quando pronto.
* `.stopThink`: Encerra a sessão e limpa a memória da conversa.
* `.ask_llm(Pergunta, [Var])`: Envia pergunta direta e unifica a resposta na variável (bypass do ciclo Logos).

### 5.3. Diretivas Arquiteturais
Invocação direta dos componentes.

* `Athena.Syllabus(Persona, Modelo, Msg, [Contextos])`: Força um pensamento fora do ciclo.
* `Athena.Praxis(StringKQML)`: Força a validação e injeção de uma string bruta.

---

## 6. Fluxos de Orquestração (Workflows)

Descrição detalhada de como os componentes interagem durante a vida do agente.

### 6.1. Fluxo de Inicialização (Bootstrap)
1.  **Jason:** Inicia e executa o plano `!start`.
2.  **Agente:** Chama `.addPersona(...)` e `.reflectPlans`.
3.  **Agente:** Chama `.startThink(...)`.
4.  **Sistema:** Carrega o modelo no Ollama (Lazy Load).
5.  **Sistema:** Adiciona crença `incorporated` na base do Jason.
6.  **Agente:** Configura o Logos (`Athena.Logos(...)`) e as políticas do Praxis (`Athena.Praxis.nap(...)`).

### 6.2. O "Heartbeat" (Ciclo Cognitivo Automático)
Este é o loop infinito gerenciado pelo Logos.

1.  **Monitoramento:** O Logos checa a cada ciclo do Jason se `Tempo > Ociosidade` ou `Gatilho Ativado`.
2.  **Disparo:** Se verdadeiro, Logos chama `Syllabus.processKQMLSemanticParsing()`.
3.  **Processamento (Background):**
    * Syllabus coleta snapshot das crenças.
    * Syllabus monta prompt com Persona + Planos + Contexto.
    * Ollama gera resposta.
4.  **Callback (Praxis):**
    * Praxis recebe a string bruta.
    * Limpa via Regex e valida sintaxe.
    * Injeta `+!plano_sugerido[source(athena)]` no Jason.
    * Atualiza contadores de uso (Usage Tracker).
5.  **Manutenção (Pós-Cognição):**
    * O Logos detecta que o fluxo terminou.
    * O Logos injeta automaticamente os eventos definidos em `[PlanosPosCognicao]` (ex: `!atualizar_dashboard`).

### 6.3. Fluxo de Limpeza de Memória
Executado em paralelo pelo Praxis (Zelador).

1.  **Gatilho:** Timer do `garbage_counter_collector` estoura (ex: 5 min).
2.  **Análise:** Praxis verifica lista de planos com `source(athena)`.
3.  **Seleção:** Ordena por frequência de uso. Mantém o Top N.
4.  **Expurgo:** Remove da `PlanLibrary` e `BeliefBase` todos os itens efêmeros que não entraram no corte.
5.  **Log:** Registra *"Memory Consolidate: X items promoted, Y items deleted"*.