# Start — 05-platform-gitops

Требования: Docker Desktop (для `full` — 10+ ГБ памяти Docker), `kind`, `kubectl`, **`helm`**
(`winget install Helm.Helm` или `helm-*-windows-amd64.zip` с https://github.com/helm/helm/releases → `~/bin`).
Свободный порт 80 на хосте. Скрипты — bash (Git Bash / WSL).

```bash
# Кластер 04 больше не нужен (освобождает память)
04-k8s-basics/scripts/down.sh

# Платформа из main (или из своей ветки — она должна быть запушена: Argo CD читает GitHub)
platform/bootstrap.sh minimal
platform/bootstrap.sh full feat/05-platform-gitops

# Наблюдать, как Argo CD доводит всё до Synced/Healthy
kubectl -n argocd get applications -w

# Проверка
05-platform-gitops/scripts/smoke.sh minimal     # или full
```

| Адрес | Вход |
|---|---|
| http://argocd.localtest.me | `admin` / `kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' \| base64 -d` |
| http://grafana.localtest.me | `admin` / `admin` |
| http://catalog.localtest.me/api/categories | — |
| http://keycloak.localtest.me (full) | `kubectl -n keycloak get secret keycloak-initial-admin -o jsonpath='{.data.password}' \| base64 -d`, user из `.data.username` |

`*.localtest.me` — публичная DNS-зона, всегда отвечающая `127.0.0.1`; без интернета пропиши хосты в
`C:\Windows\System32\drivers\etc\hosts`.

**GitOps-цикл:** изменить манифест в `platform/` → commit → push в ветку, с которой стартовал bootstrap → Argo CD
применит за 1–2 минуты (опрос раз в 60 с + разброс; быстрее — кнопка Refresh в UI). Правка руками через `kubectl edit` будет откатана (self-heal).

Переключить профиль на работающем кластере:
```bash
kubectl -n argocd patch application platform --type merge \
  -p '{"spec":{"source":{"helm":{"valueFiles":["values-full.yaml"]}}}}'
```

Если корневое Application зависло в Syncing (дочернее приложение не становится Healthy и держит волну), новые
коммиты в `platform/apps` не применяются, пока операция идёт: исправить причину в Git, затем прервать операцию —
кнопка Terminate в UI или `kubectl -n argocd patch application platform --type json -p '[{"op":"remove","path":"/operation"}]'`.

Профиль понижать (full → minimal) лучше через `platform/down.sh` и новый bootstrap: операторы могут удалиться
раньше своих ресурсов с finalizer'ами (KafkaTopic), и удаление повиснет.

Удалить: `platform/down.sh`.
