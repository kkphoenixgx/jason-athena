# Jason-Athena

O Athena é um framework de extensão para o Jason (BDI) que integra Large Language Models (LLMs) para habilitar a Autonomia Generativa em sistemas multiagentes.

Diferente de arquiteturas baseadas apenas em prompt engineering ou scripts estáticos, o Atena propõe uma arquitetura híbrida que funde o rigor lógico do modelo Belief-Desire-Intention (BDI) com a flexibilidade cognitiva das LLMs.

## 📍 Contexto e Motivação: A Singularidade do Agente

O projeto Atena aborda a rigidez dos sistemas BDI clássicos, onde os agentes são frequentemente limitados por bibliotecas de planos pré-definidas pelo desenvolvedor.

> "Agentes devem possuir autonomia plena: a capacidade de deliberar, revisar suas próprias crenças e, fundamentalmente, sintetizar novas estratégias de ação."

Enquanto o BDI tradicional garante a execução confiável de comportamentos conhecidos, o Atena introduz o Córtex Generativo. O objetivo é permitir que o agente:

- Identifique lacunas de conhecimento: Reconhecer quando nenhum plano disponível satisfaz um objetivo atual.

- Sintetize novos planos: Utilizar a LLM para gerar sequências de ações inéditas que são, então, validadas pelo motor do Jason antes da execução.

- Mantenha Independência Crítica: Desenvolver um "senso de eu" que permite ao agente avaliar a qualidade de instruções externas, inclusive do usuário, promovendo uma interação homem-agente mais autêntica e segura.

## 🏗️ Arquitetura e Implementação

O framework opera através da extensão da classe AgArch (Agent Architecture) do Jason, interceptando o ciclo de raciocínio clássico para injetar capacidades cognitivas modernas.

## 📚 Referência da API (StdLib)

Abaixo estão todas as ações internas disponíveis para uso no arquivo `.asl` do agente.

### 1. Gestão de Identidade e Contexto
Preparam o "cérebro" do agente antes ou durante o pensamento.

| Ação | Descrição | Exemplo |
| :--- | :--- | :--- |
| `.addPersona(Texto)` | Define a personalidade/papel. Aceita texto ou caminho de arquivo. | `.addPersona("Você é um piloto.");` |
| `.reflectPlans` | Lê o código fonte `.asl` e carrega na memória da LLM (Self-Reflection). | `.reflectPlans;` |
| `.addContext(Tipo, Conteúdo)` | Adiciona dados extras. Tipos: `"mas"` (planos), `"image"` (caminho). | `.addContext("image", "cam1.jpg");` |
| `.removeContext(Tipo)` | Remove contextos específicos. | `.removeContext("image");` |

### 2. Ciclo de Vida Cognitivo (Sessão)
Controlam a conexão com o Ollama e a memória de curto prazo.

| Ação | Descrição | Exemplo |
| :--- | :--- | :--- |
| `.startThink([Modelo])` | Inicializa a sessão. Adiciona a crença `incorporated` quando pronto. | `.startThink("ministral-3:3b");` |
| `.stopThink` | Encerra a sessão e limpa a memória da conversa. | `.stopThink;` |
| `.ask_llm(Pergunta, [Var])` | Envia pergunta direta (síncrona/assíncrona) e unifica a resposta. | `.ask_llm("O que vejo?", R);` |

### 3. Diretivas de Componentes (Arquitetura Avançada)
Configuração dos módulos autônomos do Athena.

#### **Logos (O Observador)**
Monitora ociosidade e gatilhos sem bloquear o agente.
```jason
// Athena.Logos(Timeout_ms, [Gatilhos], [GatilhosCriticos], [PlanosPosCognicao])
Athena.Logos(10000, [bateria_fraca], [fogo], [atualizar_dashboard]);
```

#### **Syllabus (O Córtex)**
Processamento cognitivo direto (bypass do Logos).
```jason
// Athena.Syllabus(Persona, Modelo, Mensagem, [ListaImagens])
Athena.Syllabus("Você é analista", "llama3", "Analise os logs", []);
```

#### **Praxis (O Injetor e Zelador)**
Gestão de memória e injeção manual.
```jason
// Injeção Manual
Athena.Praxis("!voar <- decolar.");

// Modo Nap (Limpeza por RAM): Mantém 10 itens se RAM > 80%
Athena.Praxis.nap(10, 80);

// Garbage Collector (Limpeza por Tempo): A cada 5 min, mantém 20 itens
Athena.Praxis.garbage_counter_collector(20, 5);
```

---

## 🚀 Exemplo de Inicialização Agente (.asl)

```jason
//? ----------- Initial goals -----------

!start.

//? ----------- Plans -----------

+!start : true <-
  .print("Iniciando...");
  
  // 1. Definição de Identidade
  addPersona("Você é um especialista em segurança predial.");
  reflectPlans;
  
  // 2. Inicialização da Consciência
  startThink("ministral-3:3b");
  .print("Aguardando inicialização da IA...");
  .wait(incorporated);

  // 3. Configuração de Autonomia (Logos)
  // Se ocioso por 10s ou se detectar 'movimento', a IA pensa.
  Athena.Logos(10000, [movimento_detectado], [], []);
  
  .print("Agente Autônomo Ativo.");
.
```

## 📘 Manual de Operação e Fluxos: Framework Athena (v3.3)

Este documento detalha o ciclo de vida da "consciência" do agente, os fluxos de execução assíncronos e a referência da API para desenvolvimento de agentes `.asl`.

---

### 1. Fluxo de Inicialização (Bootstrapping)
*O Despertar da Consciência*

Este fluxo prepara o "cérebro" do agente, carregando o contexto na memória da LLM e iniciando os monitores de segurança.

1.  **Start (Jason):** O agente inicia e executa a meta inicial `!start`.
2.  **Identidade (Java):** O agente define quem é.
    * Chamada: `.addPersona("Texto ou Arquivo")`.
    * *Efeito:* O Athena carrega o texto na memória.
3.  **Auto-Reflexão (Syllabus):** O agente lê a si mesmo.
    * Chamada: `.reflectPlans`.
    * *Efeito:* O Athena varre o arquivo `.asl`, converte os planos estáticos em texto e os anexa ao contexto da IA.
4.  **Conexão (AI Service):** O agente "acorda" a LLM.
    * Chamada: `.startThink("modelo")`.
    * *Efeito:* Inicializa o `OllamaManager` (Lazy Load). Quando a conexão é estabelecida, a crença `incorporated` é adicionada à base de crenças.
5.  **Autonomia (Logos & Praxis):** O agente liga os sistemas autônomos.
    * Chamada: `Athena.Logos(...)` para monitoramento.
    * Chamada: `Athena.Praxis.garbage_counter_collector(...)` para limpeza de memória.

---

### 2. Fluxo Principal: O Ciclo Cognitivo (The Heartbeat)
*O Pensamento Assíncrono*

Este é o loop autônomo gerenciado pelo **Logos**. Diferente de sistemas tradicionais, este fluxo **não bloqueia** o agente.

1.  **Monitoramento (Logos - Thread A):**
    * A cada ciclo do Jason, o Logos verifica: `(Tempo Ocioso > Timeout)` OU `(Crença Gatilho Ativa)`.
    * Se verdadeiro e o Syllabus estiver livre: Dispara a Task Assíncrona.
    * *O Agente Jason continua operando normalmente.*
2.  **Processamento (Syllabus - Thread B):**
    * Recebe snapshot do estado (Persona, Planos, Percepções).
    * **Verificação de Persona:** Se mudou desde o último ciclo, reinicializa o contexto do Ollama.
    * **Geração:** Envia prompt para o Ollama.
    * **Tradução:** Recebe texto bruto e aplica **Regex Estrito** (filtra apenas comandos KQML válidos).
3.  **Injeção (Praxis - Callback):**
    * Recebe a lista de comandos higienizados.
    * Valida sintaxe Jason (`parseLiteral`).
    * Adiciona anotação de proveniência: `[source(athena)]`.
    * **Injeta** na Base de Crenças ou Biblioteca de Planos.
    * Incrementa o contador de uso do plano (para gestão de memória).
4.  **Pós-Processamento (Logos):**
    * Detecta o fim do ciclo cognitivo.
    * Executa planos definidos em `postPlans` (ex: `!atualizar_interface`).

---

### 3. Fluxos Alternativos

#### A. Interação Direta (`.ask_llm`)
*O Reflexo Imediato*

Usado quando o agente precisa de uma resposta direta para uma variável, sem passar pelo ciclo complexo de tradução KQML.

1.  Agente chama `.ask_llm("O que é isso?", Resposta)`.
2.  Athena envia direto ao `AIService` (bypass do Syllabus).
3.  A string retornada é unificada na variável `Resposta`.
4.  *Nota:* Não gera planos, apenas texto. Não passa pelo Regex estrito.

#### B. Gestão de Memória (Praxis)
*O Zelador Cognitivo*

O Praxis atua como um coletor de lixo (GC) em background para evitar vazamento de memória na Raspberry Pi.

* **Modo Nap (Crítico):**
    * Configurado via `Athena.Praxis.nap(Itens, Ram%)`.
    * Monitora RAM do Sistema (`java.lang.Runtime`).
    * Se `RAM > Threshold`: Executa limpeza agressiva, mantendo apenas os `Itens` mais recentes.
* **Modo Collector (Preventivo):**
    * Configurado via `Athena.Praxis.garbage_counter_collector(Itens, Minutos)`.
    * A cada `N` minutos, verifica a frequência de uso dos planos com `source(athena)`.
    * Mantém os `Top X` mais usados (promovendo-os ou mantendo-os).
    * Remove o restante da `PlanLibrary`.

---

### 4. Diagrama de Sequência

    sequenceDiagram
        participant Jason as Agente (.asl)
        participant Logos as Logos (Observer)
        participant Syllabus as Syllabus (Cortex)
        participant Ollama as AI Service
        participant Praxis as Praxis (Injector)
    
        Note over Jason, Logos: Inicialização
        Jason->>Logos: Athena.Logos(Timeout, [Gatilhos])
        
        Note over Jason, Logos: Ciclo de Vida (Loop Infinito)
        loop Monitoramento
            Logos->>Jason: Check Idle / Beliefs
            
            opt Gatilho Disparado & Syllabus Livre
                Logos-->>Syllabus: processKQMLSemanticParsing(Snapshot)
                Note right of Logos: Jason continua rodando!
                
                activate Syllabus
                Syllabus->>Ollama: Generate(Prompt)
                Ollama-->>Syllabus: Resposta Bruta
                Syllabus->>Syllabus: Apply Regex & KQML Filter
                Syllabus->>Praxis: Callback(Comandos)
                deactivate Syllabus
                
                activate Praxis
                Praxis->>Praxis: Validate & Add Source
                Praxis->>Praxis: Update Usage Stats
                Praxis->>Jason: +!plano[source(athena)]
                deactivate Praxis
                
                Logos->>Jason: Execute PostPlans (Manutenção)
            end
        end

---

### 5. Referência da API (StdLib)

Guia rápido das ações internas disponíveis para o desenvolvedor `.asl`.

#### Gestão de Identidade
| Ação | Descrição | Exemplo |
| :--- | :--- | :--- |
| `.addPersona(Str)` | Define a personalidade (Texto ou Arquivo). | `.addPersona("security_guard.txt");` |
| `.reflectPlans` | Lê o próprio `.asl` e ensina à IA. | `.reflectPlans;` |

#### Sessão & Contexto
| Ação | Descrição | Exemplo |
| :--- | :--- | :--- |
| `.startThink(Mod)` | Conecta na IA. Gera `incorporated`. | `.startThink("llama3");` |
| `.stopThink` | Desconecta e limpa sessão. | `.stopThink;` |
| `.addContext(T, C)` | Adiciona dados extras (img/txt). | `.addContext("image", "cam.jpg");` |

#### Diretivas Arquiteturais
| Ação | Descrição | Exemplo |
| :--- | :--- | :--- |
| `Athena.Logos` | Configura monitoramento. | `Athena.Logos(10000, [perigo], []);` |
| `Athena.Syllabus` | Força pensamento manual. | `Athena.Syllabus("persona", "model", "msg");` |
| `Athena.Praxis` | Injeção manual de KQML. | `Athena.Praxis("!voar.");` |
| `Athena.Praxis.nap` | Configura GC por RAM. | `Athena.Praxis.nap(10, 85);` |
| `Athena.Praxis.garbage_counter_collector` | Configura GC por Frequência. | `Athena.Praxis.garbage_counter_collector(20, 5);` |

---

### 6. Exemplo de Implementação (.asl)

    !start.

    +!start : true <-
        .print("--- Iniciando Sistema Athena v3.3 ---");
        
        // 1. Identidade
        .addPersona("Você é um drone de vigilância autônomo.");
        .reflectPlans;
        
        // 2. Conexão
        .startThink("ministral-3:3b");
        .wait(incorporated);
        .print("IA Incorporada e Pronta.");

        // 3. Configuração de Autonomia (Logos - Heartbeat)
        // Se ocioso por 10s ou se detectar 'movimento', dispara IA.
        // Ao final do pensamento do ciclo, executa '!update_leds' (Feedback visual).
        Athena.Logos(10000, [movimento], [update_leds]);
        
        // 4. Configuração de Memória (Praxis)
        // A cada 2 minutos, mantém apenas os 15 planos mais úteis.
        Athena.Praxis.garbage_counter_collector(15, 2);
    .

    +!update_leds <-
        .print("Ciclo cognitivo finalizado. Piscando LEDs.");
        // Lógica de hardware aqui
    .
