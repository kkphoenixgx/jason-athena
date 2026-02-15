# Fluxos de Execução do Framework Athena

Esta documentação detalha o ciclo de vida da "consciência" do agente e como os dados fluem entre o Jason e o LLM.

## 1. Fluxo Principal: Inicialização da Consciência (Bootstrapping)
Este fluxo prepara o "cérebro" do agente, carregando o contexto (quem ele é e o que sabe fazer) na memória do LLM.

1.  **Definição de Identidade e Conhecimento:**
    *   O agente executa `.addPersona("Texto...")` no arquivo `.asl`.
        *   **Código:** `Athena.handleAddPersona` armazena o texto na variável `personaContext`.
    *   O agente executa `.reflectPlans` (via `ReflectPlans.java`).
        *   **Código:** Lê o código fonte `.asl` do agente e o armazena no `StringBuilder masContext` dentro da classe `Athena`.
2.  **Ativação (`.startThink`):**
    *   O agente chama a ação interna `.startThink`.
    *   **Athena:** Coleta o `masContext` (planos) e o `personaContext` e chama `aiService.initialize`.
    *   **OllamaManager:**
        *   Combina o Template + Persona + Planos em um único prompt inicial.
        *   Envia para o Ollama.
        *   **Crucial:** O Ollama retorna um vetor de contexto (`long[] context`). Este vetor representa o estado da rede neural após ler os planos do agente.
        *   O `OllamaManager` salva esse vetor no mapa `activeSessions`.
    *   **Retorno:** Ao finalizar, a classe `Athena` adiciona a crença `incorporated` na base de conhecimento do agente.
    *   **Resultado:** O agente agora "sabe" que a IA leu seus planos e está pronta.

## 2. Fluxo Principal: Ciclo de Raciocínio (Reasoning Loop)
É o fluxo onde o agente faz perguntas e recebe orientações baseadas no contexto carregado anteriormente.

1.  **A Pergunta (`ask_llm`):**
    *   O agente executa `ask_llm("O que devo fazer agora?")`.
    *   **Athena (`handleAskLlm`):** Verifica se existem imagens pendentes na lista `pendingImages`.
2.  **Processamento (AIService -> OllamaManager):**
    *   O `OllamaManager` recupera o vetor de contexto (`long[]`) da sessão atual.
    *   Monta uma requisição (`IAGenerateRequest`) contendo:
        *   O modelo (ex: gemma2).
        *   A pergunta do usuário.
        *   As imagens (se houver).
        *   O vetor de contexto anterior (para manter a memória da conversa).
        *   A `personaContext` (reenviada como *System Prompt* para reforçar o comportamento).
3.  **Resposta do LLM:**
    *   O Ollama processa e retorna o texto gerado + um **novo** vetor de contexto atualizado.
    *   O `OllamaManager` atualiza a sessão com o novo vetor (aprendizado contínuo durante a conversa).
    *   O texto bruto é fatiado em linhas (lista de Strings) pelo método `parseKqmlMessages`.
4.  **Percepção (O Elo Perdido):**
    *   A lista de Strings volta para a classe `Athena`.
    *   **Atenção:** As mensagens devem ser convertidas em crenças (`addBel`) para que o agente possa reagir a elas.

## 3. Fluxos Alternativos (Gestão de Contexto Dinâmico)

### A. Injeção de Imagens (Visão)
Diferente de um chat normal, o agente pode "ver" coisas antes de perguntar.
1.  O agente chama `.addContext("image", "/caminho/foto.jpg")`.
2.  **Athena:** Lê o arquivo, converte para Base64 e **apenas armazena** na lista `pendingImages`.
3.  A imagem **não** é enviada imediatamente ao LLM. Ela fica na fila de espera.
4.  Na próxima vez que o agente chamar `ask_llm`, todas as imagens da fila são enviadas junto com a pergunta e a fila é limpa.

### B. Mudança de Personalidade em Tempo Real
1.  O agente chama `.addPersona("Agora você é agressivo")`.
2.  **Athena:** Atualiza a variável `personaContext`.
3.  **OllamaManager:** O método `setPersonaContext` atualiza a referência volátil.
4.  Na próxima requisição ao Ollama, o campo `system` do JSON levará a nova instrução, alterando o comportamento da IA imediatamente, mas mantendo a memória (`context vector`) do que já foi conversado.

## 4. Fluxo de Encerramento
1.  O agente chama `.stopThink`.
2.  **Athena:** Remove a crença `incorporated`.
3.  **OllamaManager:** Remove a sessão do mapa `activeSessions` (o vetor de contexto é descartado, a "memória" da conversa é apagada).

## 5. Fluxos de Exceção (O que acontece quando dá erro?)

*   **Falha na Inicialização (Rede/Ollama desligado):**
    *   Se o `initialize` falhar, a `CompletableFuture` lança exceção.
    *   O `Athena` loga "CRITICAL FAILURE".
    *   A crença `incorporated` **nunca** é adicionada. O agente fica esperando indefinidamente (se usar `.wait({+incorporated})`) ou falha o plano.
*   **Erro no `ask_llm`:**
    *   Se o Ollama retornar erro 500 ou timeout.
    *   A ação `ask_llm` retorna `false` para o Jason.
    *   Uma anotação de erro é gerada: `llm_error("mensagem do erro")`. O agente pode tratar isso com um plano de falha (`-!plano_falhou`).

## 6. Resumo Visual dos Dados

```mermaid
Agente (.asl) 
   | 
   v
Athena (Java) --[Acumula Contexto]--> StringBuilder (Planos)
   |
   v
.startThink ------------------------> OllamaManager.initialize()
                                            |
                                            v
                                      Ollama API (Gera Contexto Inicial)
                                            |
                                            v
Agente <----[Crença 'incorporated']--- Athena
   |
   v
ask_llm("Pergunta") ----------------> OllamaManager.translate()
                                      (Envia: Pergunta + Imagens + Contexto[])
                                            |
                                            v
                                      Ollama API (Responde + Novo Contexto[])
                                            |
                                            v
Agente <----[addBel(Resposta)]-------- Athena (Necessita Patch)
```