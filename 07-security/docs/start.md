# Start — 07-security

Требования: JDK 25, Docker, curl. Скрипты — bash (Git Bash / WSL).

```bash
# Сборка и все тесты (Testcontainers PostgreSQL, заглушки Keycloak/соседей на JDK HttpServer)
./mvnw -f 07-security/pom.xml spotless:apply verify

# Весь стек в Compose (Keycloak + сервисы) и e2e со скриптовым входом через форму Keycloak
07-security/scripts/e2e.sh
KEEP_STACK=true 07-security/scripts/e2e.sh     # оставить стек поднятым
docker compose -p hephaestus-07 -f 07-security/compose.yaml down -v
```

## Руками (стек поднят)

| Пользователь | Пароль | Роль | Может |
|---|---|---|---|
| alice | alice | customer | смотреть склад, создавать и читать **свои** заказы |
| bob | bob | smith | смотреть склад; заказы — нет (403), чужие — 404 |
| admin | admin | admin | смотреть склад и **любой** заказ; создавать — нет |

1. Браузер: <http://localhost:8080/me> → форма Keycloak (`localhost:8180`) → alice/alice → `{"subject":…,"name":"alice"}`.
2. <http://localhost:8080/api/stock/ANVIL-01> — склад через Gateway с токеном alice.
3. POST из консоли браузера (DevTools на `localhost:8080`):
   ```js
   const xsrf = document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1];
   fetch('/api/orders', {method: 'POST', headers: {'Content-Type': 'application/json', 'X-XSRF-TOKEN': xsrf},
     body: JSON.stringify({lines: [{sku: 'ANVIL-01', quantity: 1}]})}).then(r => r.status)   // 201
   ```
   Без заголовка `X-XSRF-TOKEN` — 403.
4. Выход: `fetch('/logout', {method: 'POST', headers: {'X-XSRF-TOKEN': xsrf}})`. Сессия Keycloak (SSO) остаётся:
   следующий вход пройдёт без формы. Админка Keycloak: <http://localhost:8180> (admin/admin, realm `master`).

Gateway должен быть на порту **8080**: этот redirect URI зарегистрирован у клиента `shop-gateway`.

Токен «как у скрипта» (клиент `shop-cli`, только для тестов, password grant) — посмотреть содержимое на jwt.io
или `cut -d. -f2 | base64 -d`:
```bash
curl -s -d grant_type=password -d client_id=shop-cli -d username=alice -d password=alice \
  http://localhost:8180/realms/hephaestus/protocol/openid-connect/token
```

## На платформе (05, профиль full — нужен Keycloak)

1. В hosts-файле (см. 05 `docs/start.md`) добавить `secure-shop.localtest.me` и `keycloak.localtest.me` → `127.0.0.1`.
2. После merge в `main` CI публикует `ghcr.io/cucumber405d/hephaestus/security-{inventory,ordering,gateway}:sha-<7>`.
   Сделать три пакета публичными.
3. В `07-security/k8s/kustomization.yaml` заменить `pending-first-publish` на `sha-<7>`;
   в `platform/apps/values-full.yaml` — `security: true`. Commit, push в `main`.
4. Argo CD развернёт приложение `security` (namespace `hephaestus-07`) и realm в Keycloak (`KeycloakRealmImport`):
   ```bash
   kubectl -n argocd get application security
   kubectl -n keycloak get keycloakrealmimports
   ```
   Браузер: <http://secure-shop.localtest.me/me> → вход на `keycloak.localtest.me` → alice/alice.

Секреты клиентов и пароли пользователей лежат в Git **намеренно** — одноразовые значения учебного стенда.
