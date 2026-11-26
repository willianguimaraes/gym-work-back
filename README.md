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
- Mailpit (e-mails enviados em dev): http://localhost:8025

O `docker-compose.yml` sobe o PostgreSQL em `localhost:5432` (banco, usuário e senha: `gymwork`, só para desenvolvimento) e o Mailpit, que captura os e-mails de recuperação de senha. As tabelas são criadas pelo Flyway ao iniciar a API.

## Perfis

| Perfil | Uso |
| --- | --- |
| `dev` (padrão) | Valores locais prontos: banco e Mailpit do Docker, segredo JWT de dev, cookie sem `Secure` (permite testar no celular via `http://<ip-do-pc>`). |
| `prod` | `SPRING_PROFILES_ACTIVE=prod`. Exige as variáveis obrigatórias, cookie `Secure`, SMTP com autenticação e Swagger desligado. |

## Testes

```bash
./mvnw test
```

- **Unitários** (JUnit + Mockito): regras de autenticação, rotação de tokens, recuperação de senha, conta, catálogo, planejamento (ativação, semanas, reordenação) execução (status do exercício, tipos de série) e análise de progressão (casos de borda: teto em uma série só, aquecimento, incompleto, dor, RIR, deload, regra configurável).
- **Controller** (`@WebMvcTest`): validação, códigos HTTP e cookies.
- **Integração** (Testcontainers + PostgreSQL 17): fluxos completos contra banco real, incluindo o isolamento entre usuários em todos os endpoints de planejamento e a FK composta que impede, no banco, um registro filho apontar para o pai de outro usuário. **Precisam do Docker rodando**; sem Docker eles são pulados (aparecem como *skipped*).

## Variáveis de ambiente

Lista completa em [`.env.example`](.env.example).

| Variável | Padrão (dev) | Obrigatória em prod |
| --- | --- | --- |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | banco do Docker | sim |
| `JWT_SECRET` | segredo de dev | sim (mínimo 32 caracteres aleatórios) |
| `JWT_EXPIRATION` | `PT15M` | não |
| `REFRESH_TOKEN_TTL` | `P30D` | não |
| `AUTH_COOKIE_SECURE` | `false` em dev, `true` em prod | — |
| `FRONTEND_URL` | `http://localhost:5173` | sim (usada nos links dos e-mails) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | sim |
| `MAIL_HOST` / `MAIL_PORT` | Mailpit (`localhost:1025`) | sim |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | — | sim |
| `MAIL_FROM` | `Gym Work <no-reply@gymwork.local>` | não |
| `STORAGE_DIR` | `./data/uploads` | sim (pasta privada das fotos) |
| `STORAGE_URL_SECRET` | segredo de dev | sim (mínimo 32 caracteres aleatórios) |

## Autenticação

- **Token de acesso** (JWT, 15 min): vai no corpo do login e deve ser enviado em `Authorization: Bearer <token>`.
- **Refresh token** (30 dias): vai **só** num cookie `gw_refresh` (`HttpOnly`, `SameSite=Strict`, `Path=/api/auth`). A cada `/api/auth/refresh` ele é trocado por um novo. Se um token já trocado for reapresentado, a sessão inteira é revogada (detecção de roubo), com tolerância de 10 s para duas abas renovando ao mesmo tempo.
- Trocar ou redefinir a senha encerra todas as sessões.

## Endpoints

| Método | Rota | Auth | Descrição |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | — | Cria conta e inicia a sessão |
| POST | `/api/auth/login` | — | Autentica e inicia a sessão |
| POST | `/api/auth/refresh` | cookie | Renova o token de acesso (rotaciona o cookie) |
| POST | `/api/auth/logout` | cookie | Encerra a sessão atual |
| POST | `/api/auth/forgot-password` | — | Envia link de redefinição (sempre 204) |
| POST | `/api/auth/reset-password` | — | Define nova senha com o token do e-mail |
| GET / PUT | `/api/me` | JWT | Perfil (nome, altura, data de nascimento) |
| GET / PUT | `/api/me/settings` | JWT | Unidades, tema, descanso padrão, som, fuso horário |
| PUT | `/api/me/password` | JWT | Troca a senha (pede a atual) |
| DELETE | `/api/me` | JWT | Exclui a conta e todos os dados (pede a senha) |
| GET | `/api/muscle-groups` | JWT | Grupos musculares |
| GET | `/api/exercises?search=&muscleGroup=&equipment=&scope=ALL\|GLOBAL\|MINE&includeInactive=&page=&size=` | JWT | Busca no catálogo (sem acento, por palavras), paginada |
| GET | `/api/exercises/{id}` | JWT | Detalhe do exercício |
| POST / PUT | `/api/exercises`, `/api/exercises/{id}` | JWT | Cria / edita exercício personalizado |
| PATCH | `/api/exercises/{id}/active` | JWT | Desativa ou reativa exercício personalizado |
| GET / POST | `/api/plans` | JWT | Lista / cria planejamentos |
| GET / PUT / DELETE | `/api/plans/{id}` | JWT | Detalhe com fases / edita (inclui status) / exclui |
| POST | `/api/plans/{id}/activate` | JWT | Torna ativo (o anterior vira concluído) |
| POST | `/api/plans/{id}/duplicate` | JWT | Duplica como rascunho |
| POST | `/api/plans/{id}/phases` | JWT | Cria fase (gera as semanas) |
| GET / PUT / DELETE | `/api/phases/{id}` | JWT | Detalhe com semanas, dias e exercícios / edita / exclui |
| POST | `/api/phases/{id}/duplicate` | JWT | Nova fase a partir desta |
| PUT | `/api/weeks/{id}` | JWT | Deload e observações da semana |
| POST | `/api/phases/{id}/days` | JWT | Cria dia de treino |
| PUT | `/api/phases/{id}/days/order` | JWT | Reordena os dias |
| GET / PUT / DELETE | `/api/days/{id}` | JWT | Dia com exercícios / edita / exclui |
| POST | `/api/days/{id}/exercises` | JWT | Adiciona exercício ao dia |
| PUT | `/api/days/{id}/exercises/order` | JWT | Reordena os exercícios |
| PUT / DELETE | `/api/planned-exercises/{id}` | JWT | Edita séries, reps, descanso, carga, RIR / remove |
| POST | `/api/planned-exercises/{id}/replace` | JWT | Substitui o exercício mantendo a configuração |
| GET | `/api/sessions/start-options?phaseId=` | JWT | Tela Treinar: sessão em andamento ou dias da fase atual, semana de hoje |
| POST | `/api/sessions` | JWT | Inicia sessão a partir de um dia (copia o plano) ou avulsa; 409 se já houver uma em andamento |
| GET | `/api/sessions/current` | JWT | Sessão em andamento (204 se não houver) |
| GET | `/api/sessions?from=&to=&page=&size=` | JWT | Histórico paginado |
| GET / PATCH / DELETE | `/api/sessions/{id}` | JWT | Detalhe (séries, última execução, observações) / observações gerais / descarta |
| POST | `/api/sessions/{id}/finish` | JWT | Finaliza, mesmo incompleta |
| POST | `/api/sessions/{id}/exercises` | JWT | Exercício extra |
| POST | `/api/session-exercises/{id}/skip`, `/unskip`, `/substitute` | JWT | Pular, desfazer, substituir |
| PATCH | `/api/session-exercises/{id}` | JWT | Nota do exercício |
| POST | `/api/session-exercises/{id}/sets` | JWT | Registra série (idempotente por `clientId`) |
| PUT / DELETE | `/api/sets/{id}` | JWT | Corrige / remove série |
| GET | `/api/exercises/{id}/history?page=&size=` | JWT | Execuções do exercício (resumo "45 kg — 8/7/6", séries, volume) |
| GET | `/api/exercises/{id}/summary` | JWT | Última execução, melhor série, 1RM estimado, análise, regra e observações |
| GET | `/api/exercises/{id}/progress?groupBy=SESSION\|WEEK\|PHASE` | JWT | Dados do gráfico de carga e reps |
| GET / PUT / DELETE | `/api/exercises/{id}/progression-rule` | JWT | Regra de progressão do exercício (DELETE volta ao padrão) |
| GET | `/api/progression?phaseId=&weeks=` | JWT | Análise de progressão de todos os exercícios |
| GET | `/api/progression/volume?weeks=` | JWT | Séries por grupo muscular e frequência semanal |
| GET / POST | `/api/assessments` | JWT | Avaliações físicas (só a data é obrigatória; só o peso = registro rápido) |
| GET / PUT / DELETE | `/api/assessments/{id}` | JWT | Detalhe com medidas e fotos / edita / exclui |
| GET | `/api/assessments/compare?a=&b=` | JWT | Comparativo entre duas avaliações |
| GET | `/api/assessments/trend` | JWT | Peso, gordura e medidas para gráficos |
| POST | `/api/assessments/{id}/photos?position=` | JWT | Envia foto (multipart `file`; JPEG/PNG/WebP, até 10 MB) |
| GET | `/api/photos/{id}/url` | JWT | Nova URL temporária da foto |
| PATCH / DELETE | `/api/photos/{id}` | JWT | Muda a posição / exclui a foto |
| GET | `/api/files/{token}` | URL assinada | Entrega a foto (link válido por 5 minutos) |
| GET / POST | `/api/observations` | JWT | Observações (dor, desconforto, sono, energia…) |
| PUT / DELETE | `/api/observations/{id}` | JWT | Edita / remove observação |

Recursos de outro usuário respondem **404** (nunca 403), para não revelar que o ID existe. Cargas são sempre em kg.

Erros seguem o padrão RFC 9457 (`application/problem+json`); erros ligados a campos trazem um mapa `errors` por campo.

## Arquitetura

A proposta completa (modelo de dados, isolamento multiusuário, autenticação, endpoints e plano por etapas) está em [docs/ARQUITETURA.md](docs/ARQUITETURA.md).
