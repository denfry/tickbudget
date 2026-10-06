# TickBudget

**TickBudget** — планировщик с бюджетированием времени тика (tick budget runner), защитой от лагов (fair-share pool) и встроенной телеметрией для серверов **Paper** и **Folia** (Minecraft 1.21+, Java 21).

---

## Возможности

* ⏱️ **Бюджетирование задач по тикам**: Выполнение объемной работы порциями (`step()`) с ограничением миллисекунд на тик (`Budget.millisPerTick(2.0)`).
* ⚖️ **Fair-Share диспетчер**: Защита от «трагедии общин» — серверный пул времени распределяется между плагинами справедливо, с гарантированным `floor`-бюджетом и приоритетами (`LOW`, `NORMAL`, `HIGH`, `CRITICAL`).
* 🌐 **Поддержка Folia и Paper**: Единый интерфейс `Target` (`global()`, `region(loc)`, `entity(entity)`, `async()`) автоматически выбирает правильный планировщик (`RegionScheduler`, `EntityScheduler`, `GlobalRegionScheduler`, `AsyncScheduler`).
* 🔍 **Диагностика потоков (`assertOn`)**: Режим отладки помогает выявить ошибки обращения к состоянию мира не из своего потока до того, как они вызовут падение сервера.
* 🚀 **Async-помощники**: Безопасная асинхронная загрузка чанков (`chunks().loadAsync(...)`) и телепортация (`teleportAsync(...)`).
* 📊 **Телеметрия для администраторов**: Команды `/<cmd> status` и `/<cmd> top` показывают потребление CPU в миллисекундах по каждому плагину за последнюю минуту, количество шагов и нарушения бюджета.

---

## Архитектура модулей

```
tickbudget/
├── tickbudget-api      # Публичный API (compileOnly для других плагинов)
├── tickbudget-core     # Раннер, пул времени, fair-share диспетчер, метрики
├── tickbudget-paper    # Мост для Paper (одиночный главный поток)
├── tickbudget-folia    # Мост для Folia (региональная многопоточность)
└── tickbudget-plugin   # Плагин-рантайм (команды, конфиг, регистрация Bukkit Service)
```

---

## Использование в плагинах

### 1. Добавление зависимости
В `plugin.yml` вашего плагина:
```yaml
depend: [TickBudget]
```

В `build.gradle.kts`:
```kotlin
repositories {
    mavenCentral()
}

dependencies {
    compileOnly("dev.denfry.tickbudget:tickbudget-api:0.1.0-SNAPSHOT")
}
```

### 2. Примеры кода

#### Пошаговая обработка списка (итерация блоков, инвентарей или сущностей):
```java
TickBudget tb = TickBudget.of(plugin);

List<Block> blocksToProcess = ...;

TaskHandle handle = tb.run(Target.region(location), BudgetedTask.iterate(blocksToProcess, block -> {
    block.setType(Material.AIR);
}))
.budget(Budget.millisPerTick(1.5)) // не более 1.5 мс в одном тике
.priority(Priority.NORMAL)
.name("clear-blocks-task")
.onComplete(() -> getLogger().info("Очистка блоков завершена!"))
.onError(throwable -> getLogger().severe("Ошибка при обработке: " + throwable.getMessage()))
.start();
```

#### Кастомная задача с шагами:
```java
tb.run(Target.entity(player), () -> {
    // Делаем одну небольшую порцию работы
    boolean hasMore = doSomeWork();
    return hasMore ? StepResult.MORE : StepResult.DONE;
})
.budgetMillis(2.0)
.priority(Priority.HIGH)
.start();
```

#### Проверка владения потоком (assertOn):
```java
// Бросит IllegalStateException с подсказкой, если вызвано не в потоке региона
tb.assertOn(Target.entity(player));
```

---

## Команды администратора

Право доступа: `tickbudget.admin` (по умолчанию у OP).

* `/tickbudget status` — состояние платформы (Paper/Folia), потребление каждого плагина за последнюю минуту (мс), активные задачи, отложенные тики и нарушения бюджета.
* `/tickbudget top` — топ активных задач по суммарному затраченному времени процессора.
* `/tickbudget debug <on|off>` — переключение режима подробной диагностики нарушений потоков.
* `/tickbudget reload` — перезагрузка конфигурации `config.yml`.

---

## Сборка проекта

Требуется **Java 21**.

```bash
./gradlew build
```

Готовый плагин будет собран в `tickbudget-plugin/build/libs/TickBudget-0.1.0-SNAPSHOT.jar`.
