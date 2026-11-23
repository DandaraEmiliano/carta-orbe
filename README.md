# Carta Orbe

Transforme seu currículo e a descrição de uma vaga em uma **carta de apresentação personalizada**, em português do Brasil, inglês ou espanhol.

**Java 21 · Spring Boot · JavaScript · Ollama · Docker**

[**Testar a demo online**](https://carta-orbe.onrender.com/) · [Rodar com seu currículo](#use-seu-currículo) · [Ver a arquitetura](docs/arquitetura.md)

[![CI](https://github.com/DandaraEmiliano/carta-orbe/actions/workflows/ci.yml/badge.svg)](https://github.com/DandaraEmiliano/carta-orbe/actions/workflows/ci.yml)
[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/DandaraEmiliano/carta-orbe)

## O produto

[![Tela do Carta Orbe com o formulário da candidatura à esquerda e uma carta editável em português à direita](docs/painel.jpg)](https://carta-orbe.onrender.com/)

<sub>Captura da demo com uma candidatura fictícia e os trechos do currículo que sustentam a carta.</sub>

## Como a carta é gerada

```mermaid
flowchart LR
    CV["Currículo<br/>.txt, .tex ou texto"] --> FORM["Idioma, tom<br/>e experiência a destacar"]
    JOB["Vaga<br/>Link ou descrição"] --> FORM
    FORM --> JAVA["API Java<br/>Valida e prepara os dados"]
    JAVA --> MODEL["Ollama<br/>Redige a carta"]
    MODEL --> CHECK["API Java<br/>Confere formato e citações"]
    CHECK --> OUT["Editor<br/>Copiar, baixar ou salvar em PDF"]
```

O editor mostra a contagem de palavras e as citações literais do currículo. A geração com seu CV usa o Ollama. A demo online apresenta a mesma experiência com cartas fictícias prontas nos três idiomas.

## Experimente

Com **Java 21** e **Maven** disponíveis, execute na raiz do projeto:

```bash
ORBE_MODEL_ENABLED=false mvn spring-boot:run
```

Abra [http://localhost:8080](http://localhost:8080), clique em **Usar exemplo**, escolha um idioma e gere a carta. O cenário fictício inclui uma vaga de backend e um currículo com métricas de latência, testes de integração e reprocessamento de eventos.

## Use seu currículo

Com o [Ollama](https://ollama.com/) em execução na máquina, prepare o modelo e inicie a aplicação:

```bash
ollama pull llama3.2
mvn spring-boot:run
```

Se o serviço do Ollama estiver parado, execute `ollama serve` em outro terminal. A aplicação usa `http://127.0.0.1:11434` por padrão. Use `ORBE_MODEL_NAME` para selecionar outro modelo instalado que ofereça saída JSON estruturada.

1. Digite seu nome e cole o CV, ou importe um arquivo UTF-8 de até 64 KB.
2. Importe um link público do Greenhouse, Lever ou Ashby. Se a importação não funcionar, cole a descrição ou informe cargo, empresa e requisitos.
3. Confirme o idioma e, se desejar, ajuste o tom ou indique uma experiência do seu currículo.
4. Gere a carta, confira as experiências citadas e edite o texto antes de exportar. Para alterar o idioma ou o tom, mude a seleção e gere novamente.

A aplicação valida três ou quatro parágrafos de corpo, entre 250 e 380 palavras no texto final, e remove travessões e hífens duplos. A IA recebe instruções para usar somente fatos do currículo. A validação das citações verifica a presença dos trechos no CV. Revise as afirmações e números da carta antes de enviá-la.

## Arquitetura

O Spring Boot serve a interface HTML, CSS e JavaScript e a API na mesma origem. A API normaliza o LaTeX como texto, valida os dados, chama o Ollama e formata o resultado. Currículos e cartas são processados em memória. O app não tem banco de dados nem histórico.

- [Arquitetura, fluxograma e contrato da API](docs/arquitetura.md)
- [Política de escrita e validações](docs/geracao.md)

## Deploy

O `Dockerfile` gera um JAR executado por um usuário sem privilégios. O `render.yaml` provisiona um serviço web gratuito no Render, com verificação em `/actuator/health`. Esse ambiente serve cartas prontas para o exemplo nos três idiomas.

Para hospedar geração com currículos próprios, configure um endpoint Ollama acessível pelo servidor e mantenha o acesso restrito à instância. Em modo remoto, o modelo recebe o nome, o texto do CV e os dados da vaga após a autorização na interface.

| Variável | Padrão local | Uso |
| --- | --- | --- |
| `PORT` | `8080` | Porta HTTP |
| `BIND_ADDRESS` | `127.0.0.1` | Endereço HTTP. O contêiner usa `0.0.0.0`. |
| `ORBE_MODEL_ENABLED` | `true` | Permite geração personalizada. O Blueprint usa `false`. |
| `ORBE_MODEL_URL` | `http://127.0.0.1:11434` | Base do Ollama |
| `ORBE_MODEL_NAME` | `llama3.2` | Identificador de modelo disponível no Ollama |
| `ORBE_MODEL_KEY` | Vazio | Token do provedor para endpoint HTTPS autenticado |
| `ORBE_ACCESS_CODE` | Vazio | Código de acesso da instância para geração personalizada |

## Testes e build

```bash
mvn verify
java -jar target/carta-orbe-0.1.0.jar
```

O GitHub Actions executa a suíte Java e verifica a sintaxe do JavaScript em pushes e pull requests. Os testes automatizados da API usam o exemplo fictício e dispensam o Ollama.
