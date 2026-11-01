# RoverQuest MR — código do jogo

Projeto Android (Kotlin + Gradle) do RoverQuest MR. Visão geral do jogo no [README principal](../README.md).

## Requisitos

- JDK 17 ou superior
- Android SDK com a plataforma definida em `compileSdk` (`app/build.gradle.kts`), com `ANDROID_HOME` configurado ou `local.properties` apontando para o SDK
- Celular Android compatível com ARCore (testado no Galaxy S23 Ultra); o emulador não é suportado

## Estrutura

| Módulo | Conteúdo |
| --- | --- |
| `app/` | Aplicativo Android: sessão AR (ARCore + SceneView), interface em Jetpack Compose |
| `core/` | Lógica do jogo em Kotlin puro, sem dependências Android, com testes JUnit |

As versões das dependências ficam em [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Comandos

Execute dentro desta pasta:

```bash
./gradlew :core:test           # testes da lógica
./gradlew :app:assembleDebug   # gera o APK de debug
./gradlew :app:installDebug    # instala no celular conectado via adb
adb logcat -s RoverQuest       # logs do app
```

O APK gerado fica em `app/build/outputs/apk/debug/app-debug.apk`.

APKs prontos para instalar ficam em [Releases](https://github.com/lianeheidemann/rover-quest-mr/releases), publicados pela CI a cada merge na `main`.

Para testar outra branch (ex.: `develop`) sem merge: **Actions → APK de teste → Run workflow**, escolha a branch. O APK aparece em Releases como `<branch> (teste)`, substituído a cada build.
