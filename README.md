# NShiftUI (Android)

Runtime de plugins e events NShiftUI para Android. Alvo: **minSdk API 23** (Android 6.0), versão `0.2.0-beta.1`. Pacote `com.nshiftui`.

Sintaxe Rainbow vive em [RainbowParser-Android](https://github.com/NShiftUI-OSS/RainbowParser-Android) `0.1.0-beta.1`. Este AAR faz a semântica (`NShiftRainbowCompiler`), DI, engine e `NShiftView`.

KSP é opcional (`:nshiftui-ksp`). Sem KSP, plugins e events herdam `NShiftPlugin` / `NShiftEvent`.

## Build

Requer Android SDK (`ANDROID_HOME` ou `local.properties` com `sdk.dir`). Toolchain: Gradle 9.7.1, AGP 9.4.1, Kotlin 2.4.20, Compose BOM 2026.09.00, `compileSdk` 37, bytecode Java 21. O daemon usa JDK 25 (`gradle/gradle-daemon-jvm.properties`).

O parser resolve-se pelo AAR da GitHub Release `v0.1.0-beta.1` (Ivy em `settings.gradle.kts`).

```bash
./gradlew test
./gradlew :nshiftui:assembleRelease :nshiftui-ksp:jar
```

## Release

Actions → **Release** (branch + SemVer com ou sem `v`). Apagar: **Delete release** (SemVer com ou sem `v`). Só `ArthurPorto-PucMinas`.

Se o SemVer do input já está em `NShiftUIVersion.current` e **ainda não** existe tag/Release, bump e commit são pulados e os artefactos publicam-se na versão atual. Se a tag ou o GitHub Release já existem, o validate falha.

O bump atualiza `NShiftUIVersion.current`, `version` em `:nshiftui` e `:nshiftui-ksp`, e o `shouldBe` em `NShiftUIVersionTests`. Não altera o pin do RainbowParser.

Os YAML em `.github/workflows/` só orquestram. A lógica de cada job está em `scripts/ci/release/` e `scripts/ci/delete-release/`. Cores e falhas com contexto: `scripts/helpers.sh`. Para reproduzir um job localmente:

```bash
ALLOWED_ACTOR=ArthurPorto-PucMinas GITHUB_ACTOR=ArthurPorto-PucMinas \
  VERSION_INPUT=0.2.0-beta.1 BRANCH_INPUT=main \
  bash scripts/ci/release/setup.sh
```
