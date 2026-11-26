# gym-work-back

API REST do **Gym Work**: acompanhamento de treinos de musculação, evolução de cargas, avaliações físicas e relatórios.

O frontend fica em [gym-work-front](https://github.com/willianguimaraes/gym-work-front).

## Stack

| Item | Versão |
| --- | --- |
| Java | 17 |
| Spring Boot | 4.2.0 (Web MVC, Data JPA, Security, Validation, Actuator) |
| JWT | Spring Security OAuth2 Resource Server (Nimbus, HS256) |
| Banco | PostgreSQL 17 + Flyway |
| Documentação | springdoc-openapi 3.2.1 (Swagger UI) |
| Testes | JUnit Jupiter + Mockito |
| Build | Maven 3.9 (wrapper incluso) |

> O Spring Boot 4 usa o JUnit Jupiter 6, que mantém a mesma API do JUnit 5.

## Pré-requisitos

- Java 17
- Docker (para o PostgreSQL)

## Como rodar

```bash
docker compose up -d
./mvnw spring-boot:run
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health check: http://localhost:8080/actuator/health

O `docker-compose.yml` sobe o PostgreSQL em `localhost:5432` (banco, usuário e senha: `gymwork`, só para desenvolvimento). As tabelas são criadas pelo Flyway ao iniciar a API.

Testes:

```bash
./mvnw test
```

## Variáveis de ambiente

| Variável | Padrão |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/gymwork` |
| `DB_USERNAME` | `gymwork` |
| `DB_PASSWORD` | `gymwork` |
| `JWT_SECRET` | segredo de desenvolvimento — **troque em produção** (mínimo 32 caracteres) |
| `JWT_EXPIRATION` | `PT8H` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` |
| `SERVER_PORT` | `8080` |

## Endpoints atuais

| Método | Rota | Auth | Descrição |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | — | Cria conta e retorna o token |
| POST | `/api/auth/login` | — | Autentica e retorna o token |
| GET | `/api/users/me` | JWT | Dados do usuário autenticado |

Envie o token no header `Authorization: Bearer <token>`. Erros seguem o padrão RFC 9457 (`application/problem+json`); erros de validação trazem um mapa `errors` por campo.

## Arquitetura

A proposta completa (modelo de dados, isolamento multiusuário, autenticação, endpoints e plano por etapas) está em [docs/ARQUITETURA.md](docs/ARQUITETURA.md).
