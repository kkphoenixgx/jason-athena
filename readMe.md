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

## 🚀 Guia de Uso Rápido

### 1. Pré-requisitos
Certifique-se de ter o **Ollama** instalado e rodando localmente com um modelo baixado (recomendado: `qwen2.5:0.5b` para mais leves e testes ou `ministral-3:3b` para computadores melhores ou ambientes de produção).

```bash
ollama serve
ollama pull model
```

### 2. Implementação do Agente (.asl)
Para ativar o Athena, defina a arquitetura do agente e utilize as ações internas para carregar a consciência.

#### Configuração (.jcm)
Se estiver usando a extensão JaCaMo no VSCode, crie um arquivo `.jcm` na raiz do projeto:

```java
mas athena_test {

  agent bob {
    ag-arch: br.com.kkphoenix.Athena
  }
  
  uses package: jasonEmbedded "com.github.chon-group:jasonEmbedded:25.8.20"
  class-path: "lib/jason-athena-0.0.0.jar"
}

```


```jason
//? ----------- Initial goals -----------

!start.

//? ----------- Plans -----------

+!start : true <-
  .print("Iniciando...");
  
  addPersona("Você é um especialista em segurança predial.");
  reflectPlans;
  startThink("<<model>>");
  
  .print("Aguardando inicialização da IA...");
  .wait(incorporated);

  .print("Mente carregada. Consultando...");
  
  ask_llm("Analise meus planos. O que faço se perder a chave?");
  
  .print("Fim")
.
```
