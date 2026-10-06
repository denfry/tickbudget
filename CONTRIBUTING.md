# Contributing to TickBudget

Thank you for your interest in contributing to **TickBudget**! We welcome bug reports, feature suggestions, documentation improvements, and code contributions.

---

## 🛠️ Development Setup

### Requirements
* **Java 21** or later (JDK 21)
* **Git**

### Building the Project
Clone the repository and build using Gradle:

```bash
git clone https://github.com/denfry/tickbudget.git
cd tickbudget
./gradlew build
```

This compiles all modules, runs unit tests, and produces:
* Shaded plugin JAR in `tickbudget-plugin/build/libs/TickBudget-<version>.jar`
* Public API JAR in `tickbudget-api/build/libs/tickbudget-api-<version>.jar`

---

## 🏛️ Architecture & Module Boundaries

TickBudget is designed with strict module boundaries:

| Module | Purpose | Allowed Dependencies |
|---|---|---|
| `tickbudget-api` | Public interfaces and records. Kept lightweight for client plugins. | No internal modules, only Paper API (`compileOnly`). |
| `tickbudget-core` | Fair-share dispatcher, budget runner, metrics, configuration. Zero server dependencies. | `tickbudget-api`, test-only Bukkit mocks. |
| `tickbudget-paper` | Single-threaded Paper platform bridge & `ServerTickStartEvent` listener. | `tickbudget-core`, Paper API. |
| `tickbudget-folia` | Multi-threaded regionized Folia platform bridge. | `tickbudget-core`, Folia API. |
| `tickbudget-plugin` | Runtime plugin bootstrap, commands, bStats, configuration. | All platform modules. |
| `tickbudget-testplugin` | Live integration test suite executed on test servers. | `tickbudget-api`, Paper API. |

**Important Rules:**
* `tickbudget-api` must maintain backward compatibility within major versions.
* `tickbudget-core` must remain agnostic of Paper or Folia classes. All server interactions pass through `PlatformBridge`.
* Bridges must not depend on each other.

---

## 🧪 Testing

Always ensure that existing tests pass before submitting a PR:

```bash
./gradlew test
```

### Writing Tests
* Unit tests for core scheduling, budgeting, and fair-share calculations belong in `tickbudget-core/src/test/java/` using `FakePlatformBridge` and `FakeClock`.
* Do not introduce real server dependencies in `tickbudget-core`.

---

## 📝 Pull Request Guidelines

1. **Create a topic branch**: `git checkout -b feature/my-feature` or `bugfix/issue-description`.
2. **Follow code style**: Keep code formatted cleanly, without trailing spaces or unnecessary dependencies.
3. **Write tests**: Provide unit tests covering new features or regression tests for bug fixes.
4. **Descriptive commits**: Write clear, descriptive commit messages.
5. **Open a PR**: Submit a pull request against the `main` branch with a summary of changes and test evidence.
