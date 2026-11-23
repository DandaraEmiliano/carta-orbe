# Arquitetura

A interface e a API compartilham a origem HTTP. O navegador mantém as informações do formulário enquanto a página está aberta. O servidor valida cada entrada e processa as cartas em memória, com uma chamada de geração por vez.

## Fluxo da candidatura

```mermaid
flowchart TD
    A["Preencher nome e currículo"] --> B{"Arquivo PDF, TXT ou TEX?"}
    B -- Sim --> C["Importar e revisar o texto extraído"]
    B -- Não --> D["Informar vaga"]
    C --> D
    D --> E{"Link compatível?"}
    E -- Sim --> F["Importar a descrição"]
    E -- Não --> G["Colar descrição ou requisitos"]
    F -- Importado --> H["Confirmar idioma e escolher tom"]
    F -- Indisponível --> G
    G --> H
    H --> I{"Dados do exemplo?"}
    I -- Sim --> J["Obter carta fictícia no idioma"]
    I -- Não --> K["Validar dados e chamar o modelo"]
    K --> L{"Formato e citações válidos?"}
    L -- Sim --> M["Editar a carta e conferir as citações"]
    L -- Não --> N["Tentar corrigir uma vez"]
    N -- Válido --> M
    N -- Inválido --> O["Exibir erro e preservar formulário"]
    J --> M
    M --> P["Copiar, baixar TXT ou baixar PDF"]
```

## Componentes

| Componente | Responsabilidade |
| --- | --- |
| Frontend | Formulário, importações, revisão de trechos, edição e exportação |
| `LetterController` | Contratos HTTP, validação de tamanho e erros de entrada |
| `ResumeText` | Normalização de texto e extração básica de LaTeX sem compilação |
| `VacancyImport` | Consulta às APIs públicas do Greenhouse, Lever e Ashby |
| `PdfDocuments` | Extração de texto de PDF e exportação de cartas em A4 |
| `OllamaLetterWriter` | Chamada ao modelo, limite de concorrência e uma tentativa de correção |
| `Letter` | Formato por idioma, contagem de palavras e verificação das citações |
| `DemoLetters` | Validação e carregamento dos exemplos incluídos no JAR |

## API

| Método e caminho | Entrada | Saída |
| --- | --- | --- |
| GET `/api/settings` | | Flags de geração, código de acesso e modelo remoto |
| GET `/api/examples` | | Nome, currículo e descrição fictícios |
| GET `/api/examples/{language}` | `PT`, `EN` ou `ES` | Carta fictícia formatada |
| POST `/api/resume` | Multipart com `file` PDF, TXT ou TEX | `{"text":"…","pdf":true}` |
| POST `/api/vacancy` | JSON com `url` | `{"description":"…","truncated":false}` |
| POST `/api/letters` | JSON abaixo, com `X-Orbe-Access` se configurado | `{"text":"…","wordCount":280,"evidence":["…"],"example":false}` |
| POST `/api/export/pdf` | JSON com `text` da carta revisada | Anexo `application/pdf` |
| GET `/actuator/health` | | Estado do processo HTTP |

```json
{
  "name": "Marina Costa",
  "resume": "Desenvolvi APIs em Java e Spring Boot para rastreamento de entregas.",
  "vacancy": "Engenharia de backend na Horizonte Logística. Requisitos: Java, Spring Boot e APIs REST.",
  "language": "EN",
  "tone": "PROFESSIONAL",
  "emphasis": "Rastreamento de entregas",
  "remoteConsent": false
}
```

A API retorna `400` para dados inválidos, `403` para código incorreto, `413` para arquivo acima do limite, `422` para link indisponível, `429` para geração concorrente, `502` para saída inválida do modelo, `503` para modelo indisponível ou desativado e `504` quando o modelo ultrapassa o prazo de resposta. A geração remota exige `remoteConsent: true`. As respostas com conteúdo pessoal usam `Cache-Control: no-store`. O indicador de saúde verifica o processo, independentemente de o modelo estar disponível.

A importação de vagas consulta as APIs públicas a partir dos identificadores presentes no link. As chamadas usam endereços HTTPS fixos, com limites de tamanho e duração e redirecionamentos desativados. Currículo e descrição são enviados ao modelo como dados JSON, com instruções para ignorar comandos encontrados no conteúdo.

A extração de PDF percorre até 12 páginas, em posição de leitura, e para quando ultrapassa o limite de 12.000 caracteres. O texto retorna ao formulário para revisão e pode ser baixado em TXT. A exportação gera um PDF de uma página A4 a partir do conteúdo atual do editor.
