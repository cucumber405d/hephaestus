# Start — 04-k8s-basics

Требования: Docker Desktop (WSL2), [kind](https://kind.sigs.k8s.io/) (`winget install Kubernetes.kind`),
`kubectl` (идёт с Docker Desktop). Для `validate.sh` — [kubeconform](https://github.com/yannh/kubeconform).
Скрипты — bash: на Windows из Git Bash или WSL. Образ 03 в GHCR должен быть публичным (см. 03/docs/start.md).

```bash
# Кластер + деплой (БД -> миграция -> API) + проверка
04-k8s-basics/scripts/up.sh
04-k8s-basics/scripts/smoke.sh

# Только манифесты, без кластера
04-k8s-basics/scripts/validate.sh
kubectl kustomize 04-k8s-basics/k8s/overlays/local     # посмотреть, что получится

# Повторный деплой после правок (миграция перезапускается, Flyway — no-op)
04-k8s-basics/scripts/deploy.sh

# Доступ к сервису
kubectl -n hephaestus-04 port-forward svc/catalog 8080:80
curl localhost:8080/api/categories
curl localhost:8080/actuator/runtime

# Посмотреть
kubectl -n hephaestus-04 get all,pvc,configmap,secret
kubectl -n hephaestus-04 logs deploy/catalog -f
kubectl -n hephaestus-04 logs job/catalog-migrate

# Удалить кластер целиком
04-k8s-basics/scripts/down.sh
```

Свой образ вместо GHCR (например, после правок в 03):
```bash
03-containers-ci/scripts/build-images.sh jib
kind load docker-image hephaestus/catalog:jib --name hephaestus-04
# в k8s/overlays/local/kustomization.yaml: newName: hephaestus/catalog, newTag: jib
04-k8s-basics/scripts/deploy.sh
```
