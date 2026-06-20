# Estapar — Garage Backend (Desafio Técnico Back Java)

Sistema backend para gerenciar um estacionamento: controla vagas, processa eventos de
entrada/parada/saída de veículos e calcula a receita por setor.

## Stack

- Java 21
- Spring Boot 3.5
- MySQL 8 (via Docker)
- Maven (wrapper incluso: `mvnw` / `mvnw.cmd`)

## Arquitetura / fluxo

As garagens têm um único grupo de cancelas na entrada; os **setores** são divisões
**lógicas**. O evento `ENTRY` traz apenas a placa e o horário — o **setor só é conhecido
no `PARKED`**, através das coordenadas (`lat`/`lng`) da vaga. Por isso:

- **ENTRY**: valida capacidade global da garagem e registra a sessão como ativa.
- **PARKED**: localiza a vaga pelas coordenadas → descobre o setor; aplica a regra de
  lotação (100% = setor fechado) e calcula o **preço dinâmico** com base na lotação do
  setor naquele momento; marca a vaga como ocupada.
- **EXIT**: libera a vaga e calcula o valor a cobrar.

## Como executar

### 1. Subir o banco MySQL

```bash
docker compose up -d
```

Sobe o MySQL em `localhost:3307` (banco `estapar_db`, usuário/senha `root`).

### 2. Subir o simulador da garagem

O simulador expõe a configuração em `GET /garage` e envia eventos para o webhook.

```bash
# Conforme o desafio (Linux/host network):
docker run -d --network="host" cfontes0estapar/garage-sim:1.0.0

# No Docker Desktop (Windows/Mac), publique a porta:
docker run -d -p 3000:3000 cfontes0estapar/garage-sim:1.0.0
```

> A URL do simulador é configurável em `application.properties` via `simulator.url`
> (padrão `http://localhost:3000`).

### 3. Subir a aplicação

```bash
./mvnw spring-boot:run
```

A aplicação sobe na porta **3003**. No startup, ela busca e armazena automaticamente os
setores e as vagas do simulador (`GET /garage`).

## API

### Webhook — `POST /webhook`

Recebe os eventos do simulador. Todos respondem **HTTP 200**.

```json
// ENTRY
{ "license_plate": "ZUL0001", "entry_time": "2025-01-01T12:00:00.000Z", "event_type": "ENTRY" }

// PARKED
{ "license_plate": "ZUL0001", "lat": -23.561684, "lng": -46.655981, "event_type": "PARKED" }

// EXIT
{ "license_plate": "ZUL0001", "exit_time": "2025-01-01T12:00:00.000Z", "event_type": "EXIT" }
```

### Faturamento — `GET /revenue`

Receita total por setor e data. Aceita os parâmetros via **query string** (recomendado
para GET) ou via corpo JSON:

```bash
curl "http://localhost:3003/revenue?date=2025-01-01&sector=A"
```

Resposta:

```json
{ "amount": 0.00, "currency": "BRL", "timestamp": "2025-01-01T12:00:00.000Z" }
```

## Regras de negócio

- **Cobrança**: os primeiros 30 minutos são grátis. Após 30 minutos, cobra-se tarifa fixa
  por hora (inclusive a primeira), arredondando as horas **para cima**, usando o
  `basePrice` do setor ajustado pelo preço dinâmico.
- **Preço dinâmico** (lotação do setor no momento da parada):
  | Lotação | Ajuste |
  |---------|--------|
  | < 25%   | −10%   |
  | até 50% |  0%    |
  | até 75% | +10%   |
  | até 100%| +25%   |
- **Lotação**: com 100% de lotação o setor é fechado e só aceita novos veículos após a
  saída de um já estacionado. Com a garagem inteira cheia, novas entradas são recusadas.

## Tratamento de erros

Centralizado em `GlobalExceptionHandler` (`@RestControllerAdvice`). Erros de negócio
retornam status apropriados (404 não encontrado, 409 conflito/lotado, 400 requisição
inválida) com corpo JSON `{ timestamp, status, error, message }`.

## Testes

```bash
./mvnw test
```

Cobrem o cálculo de cobrança (30 min exatos, 30min30s, arredondamento por hora), as
bordas do preço dinâmico (25/50/75%), a regra de lotação, entrada duplicada, eventos
inválidos e a formatação da resposta de faturamento.
```
