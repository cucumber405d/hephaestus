# Learning notes — 07-security

## OAuth2 и OIDC в одной картинке

- **OAuth2** — делегирование доступа: клиент получает **access token** для ресурсного сервера. **OIDC** поверх —
  аутентификация: **ID token** (кто вошёл) для самого клиента. Access token — для API, ID token — никуда не отправлять.
- Роли здесь: Keycloak — authorization server, Gateway — клиент (`shop-gateway`), Ordering/Inventory — resource
  servers, Ordering ещё и клиент (`ordering`) к Inventory.
- Потоки: **Authorization Code (+PKCE)** — пользователь в браузере; **Client Credentials** — сервис сам за себя;
  Password grant — устарел (OAuth 2.1 убирает), здесь только у `shop-cli` для скриптов; Implicit — забыть.

## PKCE

Клиент генерирует `code_verifier`, отправляет через браузер только `code_challenge = BASE64URL(SHA256(verifier))`, а
при обмене кода — сам verifier. Перехваченный код без verifier бесполезен. Нужен и конфиденциальному клиенту
(OAuth 2.1, RFC 9700): секрет защищает от чужого клиента, PKCE — от подмены/кражи кода. Spring включает PKCE сам
только для публичных клиентов; здесь — явно `OAuth2AuthorizationRequestCustomizers.withPkce()`, а в realm у
клиента `pkce.code.challenge.method = S256` — Keycloak отклонит запрос без challenge. `GatewayBffIT` проверяет,
что `SHA256(verifier)` из запроса токена совпал с challenge из редиректа.

## BFF против токенов в браузере

- SPA с токенами в `localStorage` — любой XSS уносит токен (и refresh token). BFF держит токены на сервере,
  браузеру — HttpOnly-cookie сессии: украсть нечего, XSS может только действовать *в* сессии, пока вкладка открыта.
- Цена: сессия на сервере (здесь — в памяти пода, поэтому **одна реплика** Gateway; общий стор — Spring Session/Redis),
  CSRF, Gateway становится stateful. Обновление токена — в Gateway (`refreshToken()` в `OAuth2AuthorizedClientManager`).
- `TokenRelay=` берёт токен из сессии и ставит `Authorization: Bearer`; `RemoveRequestHeader=Cookie` — cookie
  сессии бэкендам не нужен и не должен утекать.

## CSRF

Cookie браузер шлёт сам — значит, чужой сайт может заставить браузер сделать POST от имени пользователя. Защита:
токен, который чужой сайт прочитать не может. Схема «double submit» для SPA: сервер кладёт токен в читаемую cookie
`XSRF-TOKEN`, JavaScript своего origin копирует его в заголовок `X-XSRF-TOKEN` (`csrf.spa()` в Spring Security).
`SameSite=Lax` у cookie сессии — второй слой (не шлётся на кросс-сайтовый POST), но не замена. Resource servers
CSRF отключают: Bearer-токен браузер сам не подставляет.

## Валидация JWT в resource server

- Подпись (ключи из **JWKS** по `kid`, кэш и обновление при ротации), `exp`/`nbf` (с допуском часов), **`iss`** —
  ровно наш realm, **`aud`** — токен выдан *для нас* (`audiences` в Boot; в Keycloak — audience mapper). Без
  проверки `aud` любой токен realm'а, выданный другому клиенту, подошёл бы.
- `issuer-uri` + `jwk-set-uri` вместе: Boot не ходит в discovery, `iss` сравнивает со строкой, ключи берёт по
  внутреннему адресу. Это и снимает проблему «публичный адрес Keycloak недоступен из контейнера».
- В Gateway то же для ID token: явные endpoint'ы → Spring не знает issuer → `JwtIssuerValidator` добавлен
  вручную (`GatewayBffIT.idTokenFromAnotherIssuerIsRejected`).

## Роли и владение

- Keycloak кладёт роли realm в `realm_access.roles`, Spring ждёт `ROLE_*` → свой `Converter<Jwt, …>`
  (`RealmRolesConverter`). Роли клиента (`resource_access.<client>.roles`) — альтернатива, точнее по области.
- URL-правила (`hasRole("customer")` на POST) — грубая сетка; владение — в прикладном слое (`Requester.mayRead`).
- Чужой заказ — **404, не 403**: 403 подтвердил бы, что такой id существует (перебор id).
- Владелец — `sub` (неизменный id пользователя), не `preferred_username` (его можно сменить).

## Сервисная идентичность

Ordering резервирует **своим** токеном (client credentials, роль `inventory-reserver` у сервисного аккаунта), а не
токеном пользователя: пользователь не должен уметь резервировать напрямую (e2e: 403 с токеном alice прямо в Inventory).
Альтернатива — **token exchange** (RFC 8693): сервис меняет токен пользователя на токен «от имени пользователя для
Inventory» — нужно, когда ресурсу важно, *кто* пользователь. `AuthorizedClientServiceOAuth2AuthorizedClientManager`
кэширует токен и обновляет перед истечением; Keycloak недоступен → для вызывающего это та же 503.

## Сети

NetworkPolicy — L3/L4: «кто к кому может подключиться». Default deny + явные разрешения = zero trust внутри
namespace. Не заменяет аутентификацию (pod внутри разрешённого направления всё равно предъявляет токен) — это
два независимых слоя. kind (kindnet ≥ 0.24) политики применяет; кубелет-пробы идут с адресов узлов.
Следующий шаг — mTLS между сервисами (service mesh, 21).

## Что дальше

Секреты — в Git только для учебного стенда (в бою: External Secrets/Vault, плейсхолдеры realm import).
Logout сейчас локальный; RP-initiated logout (`end_session_endpoint`) завершил бы и SSO-сессию Keycloak.

## Миграция Stock API v1 → v2

Без изменений с 06: v1 отдаёт только `available` и помечен `Deprecation`/`Sunset` (30.06.2027); переходите на
`API-Version: 2` (`onHand`, `reserved`, `available`, `name`).

## Вопросы для собеседования

1. **Чем OIDC отличается от OAuth2?** Аутентификация (ID token, userinfo) поверх делегирования доступа.
2. **Зачем PKCE конфиденциальному клиенту?** Защита кода от перехвата/инъекции; обязателен в OAuth 2.1.
3. **Где хранить токены SPA?** Лучше нигде: BFF + HttpOnly cookie; иначе — в памяти, короткая жизнь, refresh rotation.
4. **Почему BFF требует CSRF-защиту, а Bearer API — нет?** Cookie подставляется браузером автоматически, Bearer — нет.
5. **Что проверяет resource server в JWT?** Подпись по JWKS, exp/nbf, iss, aud, затем роли/scope.
6. **Зачем audience?** Иначе токен для одного сервиса подходит к другому.
7. **Opaque token vs JWT?** Introspection на каждый запрос (мгновенный отзыв) vs локальная проверка (отзыв — через короткий срок жизни).
8. **Client credentials vs token exchange?** Сервис от себя vs от имени пользователя.
9. **403 или 404 на чужой ресурс?** 404 — не раскрывать существование.
10. **NetworkPolicy и токены — не дублирование?** Разные уровни: сеть ограничивает поверхность, токен — права.
