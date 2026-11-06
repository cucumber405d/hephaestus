# Start — 01-modern-java

Подпроект собирается на **JDK 27** (модуль `next` — `--release 27 --enable-preview`).

```powershell
# JDK 27 только для текущего окна PowerShell
$env:JAVA_HOME = (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory | Where-Object Name -like "jdk-27*" | Select-Object -First 1).FullName

# Сборка и тесты
.\mvnw.cmd -f 01-modern-java/pom.xml verify

# Форматирование — только на JDK 25 (palantir-java-format не работает на JDK 27)
$jdk27 = $env:JAVA_HOME
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot"; .\mvnw.cmd -f 01-modern-java/pom.xml -q spotless:apply
$env:JAVA_HOME = $jdk27

# Тесты с выходом в интернет (HTTP/3)
.\mvnw.cmd -f 01-modern-java/next/pom.xml test -Dgroups=network
```
