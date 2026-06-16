# CI Lab — Continuous Integration with Spring Boot / Kotlin

A tiny web service used to teach the core moves of Continuous Integration on
GitHub Actions. The whole lab revolves around **one piece of business logic**
(`PricingService.applyDiscount`) so learners can stay focused on the *pipeline*,
not the domain.

## The service

`POST /quote` with a subtotal, get back a discounted total:

```bash
curl -s localhost:8080/quote \
  -H 'Content-Type: application/json' \
  -d '{"subtotal": 150.00}'
# {"subtotal":150.00,"total":135.00}
```

The discount rules (`src/main/kotlin/.../PricingService.kt`):

| Subtotal      | Discount |
|---------------|----------|
| `>= 200`      | 20% off  |
| `>= 100`      | 10% off  |
| below 100     | none     |

The tier boundaries (100, 200) are where the interesting tests — and bugs — live.

## Prerequisites

- JDK 17+ installed (`java -version`)
- A GitHub account and an empty GitHub repo to push this into
- No local Gradle install needed — the project ships with the Gradle wrapper (`./gradlew`)

## Run it locally

```bash
./gradlew test        # run the unit tests
./gradlew build       # compile + test + assemble the jar
./gradlew bootRun     # start the service on :8080
```

---

# The lab

Each step is small and self-contained. Push to GitHub after the setup step,
then do the rest through Pull Requests so the CI checks are visible.

## Step 0 — Get it on GitHub

```bash
git init
git add .
git commit -m "CI lab: Spring Boot/Kotlin pricing service"
git branch -M main
git remote add origin git@github.com:<you>/ci-lab.git
git push -u origin main
```

## Step 1 — `ci.yml`: checkout, build, test on every push

Already included at `.github/workflows/ci.yml`. The moment you push, open the
**Actions** tab and watch it run: it checks out the code, sets up JDK 17, and
runs `./gradlew build` (which compiles **and** tests).

Talking points for learners:
- `on: [push, pull_request]` — CI runs both on direct pushes and on PRs.
- `./gradlew build` fails the job (non-zero exit) if compilation or any test fails. That red/green signal *is* CI.

## Step 2 — One genuine Arrange–Act–Assert test (already green)

See `src/test/kotlin/.../PricingServiceTest.kt`:

```kotlin
@Test
fun `applies 10 percent discount exactly at the 100 boundary`() {
    // Arrange
    val subtotal = BigDecimal("100.00")

    // Act
    val total = pricingService.applyDiscount(subtotal)

    // Assert
    assertEquals(BigDecimal("90.00"), total)
}
```

Three clearly labelled phases, one behaviour under test, one assertion. Run
`./gradlew test` — it passes. This is your known-good baseline.

> Optional extension: have learners add tests for the 200 boundary, the
> below-100 case, and the negative-subtotal `require(...)` failure.

## Step 3 — Break it on purpose, watch CI go red, fix it

Create a branch and open a PR with a deliberate bug, so the class sees the
pipeline catch it.

```bash
git checkout -b break-the-build
```

Introduce an **off-by-one boundary bug** in `PricingService.kt` — change the
`>=` at the 100 tier to a strict `>`:

```diff
-            subtotal >= BigDecimal(100) -> BigDecimal("0.10")
+            subtotal >  BigDecimal(100) -> BigDecimal("0.10")
```

Now a subtotal of exactly `100.00` falls through to "no discount", so the test
expects `90.00` but gets `100.00`.

```bash
git commit -am "Oops: boundary bug at the 100 tier"
git push -u origin break-the-build
```

Open the PR. The **CI check turns red**; open the failing run and read the
assertion failure (`expected: 90.00 but was: 100.00`). That message is the lab's
payoff — CI told you exactly what broke. Then fix it:

```diff
-            subtotal >  BigDecimal(100) -> BigDecimal("0.10")
+            subtotal >= BigDecimal(100) -> BigDecimal("0.10")
```

```bash
git commit -am "Fix boundary: discount applies at exactly 100"
git push
```

The check goes green. Merge.

> Variant: instead of breaking the source, break the *test's* expected value
> (`90.00` -> `91.00`). Discuss which kind of red is "the test is wrong" vs
> "the code is wrong".

## Step 4 — Add Gradle caching, compare run times

First, record a baseline: note the duration of a CI run from Step 1–3 (no
cache). A cold Gradle run re-downloads Spring Boot + the Kotlin/JUnit toolchain
every time.

Now switch the workflow to use the official `gradle/actions/setup-gradle`
action, which transparently caches the Gradle dependency/build cache keyed on
your build files. Replace `.github/workflows/ci.yml` with:

```yaml
name: CI

on:
  push:
  pull_request:

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      # This action restores/saves ~/.gradle (downloaded deps + build cache).
      - name: Set up Gradle (with caching)
        uses: gradle/actions/setup-gradle@v4

      - name: Build and test
        run: ./gradlew build --no-daemon
```

Push this on a branch. The **first** run populates the cache (still slow-ish, it
has to save). **Subsequent** runs restore it — dependency download disappears
from the logs.

What to measure with the class:
- Total job duration: cold run vs warm run (expect a meaningful drop, often
  roughly a minute or more saved on the dependency phase).
- Look for the `Cache restored` / `Restored Gradle dependencies` lines in the
  "Set up Gradle" step logs.
- Discuss the trade-off: cache *keys*, cache *invalidation* (changing
  `build.gradle.kts` busts it), and cache size limits.

## Step 5 — Require the CI check before merge (branch protection)

Make green CI mandatory so a red pipeline physically blocks merging.

On GitHub: **Settings → Branches → Add branch ruleset** (or *Add rule* on the
classic UI) targeting `main`, then enable:

- **Require a pull request before merging**
- **Require status checks to pass before merging**, and in the search box add
  the check named **`build-and-test`** (the job name from `ci.yml`).
- Optionally **Require branches to be up to date before merging**.

Prove it works: open a new PR that breaks the test again (Step 3's diff). The
**Merge** button is now disabled until the check passes. Fix the test, the check
goes green, merge unlocks. That is the full CI loop: every change is built and
tested automatically, and broken changes cannot reach `main`.

---

## Project layout

```
ci-lab/
├─ .github/workflows/ci.yml          # the pipeline (Step 1; upgraded in Step 4)
├─ build.gradle.kts                  # Spring Boot + Kotlin + JUnit 5
├─ settings.gradle.kts
├─ gradlew / gradlew.bat             # Gradle wrapper (no local Gradle needed)
├─ gradle/wrapper/…
└─ src/
   ├─ main/kotlin/com/example/cilab/
   │  ├─ CiLabApplication.kt         # Spring Boot entry point
   │  ├─ PricingService.kt           # ← the business logic under test
   │  └─ QuoteController.kt          # POST /quote
   ├─ main/resources/application.properties
   └─ test/kotlin/com/example/cilab/
      └─ PricingServiceTest.kt       # ← the AAA unit test
```

## Notes for instructors

- Versions are pinned to a known-good combo (Spring Boot 3.3.x, Kotlin 1.9.24,
  Gradle 8.8, JDK 17). Bump them deliberately as a later exercise — a version
  bump is also a great way to demonstrate cache invalidation in Step 4.
- The whole point of one trivial business rule is to keep attention on the
  *pipeline mechanics*. Resist the urge to grow the domain.
