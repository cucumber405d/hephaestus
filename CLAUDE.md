# Hephaestus — правила для агента

- Спецификация: `docs/superpowers/specs/2026-11-05-hephaestus-root-design.md`; планы — `docs/superpowers/plans/`.
- Documentation as Code: документация в `docs/`, `docs/start.md` в корне и в каждом подпроекте.
- **Impact-файлы:** каждая задача, фикс или фича сопровождается файлом
  `impacts/impact_YYYY_MM_DD_HH.MM.SS.md` (время 24ч, локальное, реальные секунды: `Get-Date -Format yyyy_MM_dd_HH.mm.ss` / `TZ=Europe/Berlin date +%Y_%m_%d_%H.%M.%S`), в том же коммите.
  - Максимум один экран.
  - Сначала YAML front matter с полями `name:` и `description:`.
  - Далее свободный текст на русском и английском: смысл задачи/фикса/фичи — чтобы не читать diff.
- **Язык:** комментарии и javadoc в исходниках, POM, YAML, скриптах и batch-файлах — только на английском. Документация в `docs/` и impact-файлы — по своим правилам (RU / RU+EN).
- Сборка подпроекта: `./mvnw -f <subproject>/pom.xml verify` (Maven Central из среды агента недоступен — сборку запускает Dmitry или CI).
