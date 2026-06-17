# CI Lab: Continuous Integration with Spring Boot / Kotlin

A tiny web service for practising the core moves of Continuous Integration on
GitHub Actions. The whole lab revolves around **one piece of business logic**
(`PricingService.applyDiscount`), which keeps the focus on the *pipeline* rather
than the domain.

## What you'll do in this lab

A quick map before the details:

1. **Import** this codebase into a repo of your own and watch its CI workflow
   run on the first push.
2. **Break it on purpose** to prove the pipeline catches bugs: introduce a
   boundary bug, watch the CI check go red, then fix it.
3. **Speed it up** by adding Gradle caching to the workflow, then compare the
   run times.
4. **Lock the branch** with branch protection (if your account allows it), so a
   red pipeline blocks merges to `main`.
5. **Containerize** the service into a Docker image, ready for Lab 3.

> **This is the `workshop-application` repo.** It's the application you'll
> containerize and ship in the later labs: Lab 3 builds this into an image,
> pushes it to `registry.ff26.it`, and deploys it via Argo CD; Lab 4 wires its
> secrets through Vault. A `Dockerfile` is included for exactly that purpose.

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

The tier boundaries (100, 200) are where the interesting tests, and the bugs, live.

## Prerequisites

- JDK 17+ installed (`java -version`)
- A GitHub account and an empty GitHub repo named **`workshop-application`** to push this into
- No local Gradle install needed. The project ships with the Gradle wrapper (`./gradlew`)

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

## Step 0: Get it on GitHub

```bash
git init
git add .
git commit -m "CI lab: Spring Boot/Kotlin pricing service"
git branch -M main
git remote add origin git@github.com:<you>/workshop-application.git
git push -u origin main
```

## Step 1: checkout, build, and test on every push (`ci.yml`)

Already included at `.github/workflows/ci.yml`. The moment you push, open the
**Actions** tab and watch it run: it checks out the code, sets up JDK 17, makes
the Gradle wrapper executable, and runs `./gradlew build` (which compiles **and**
tests).

Two things worth noticing:
- `on: [push, pull_request]` tells CI to run both on direct pushes and on PRs.
- `./gradlew build` fails the job (non-zero exit) if compilation or any test fails. That red/green signal *is* CI.

## Step 2: One genuine Arrange-Act-Assert test (already green)

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
`./gradlew test` and it passes. This is your known-good baseline.

> Want more practice? Add tests for the 200 boundary, the below-100 case, and
> the negative-subtotal `require(...)` failure.

## Step 3: Break it on purpose, watch CI go red, fix it

Create a branch and open a PR with a deliberate bug, so you can watch the
pipeline catch it.

```bash
git checkout -b break-the-build
```

Introduce an **off-by-one boundary bug** in `PricingService.kt`: change the
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
payoff: CI told you exactly what broke. Then fix it:

```diff
-            subtotal >  BigDecimal(100) -> BigDecimal("0.10")
+            subtotal >= BigDecimal(100) -> BigDecimal("0.10")
```

```bash
git commit -am "Fix boundary: discount applies at exactly 100"
git push
```

The check goes green. Merge.

> Try the other kind of red too: instead of breaking the source, break the
> *test's* expected value (`90.00` -> `91.00`). It's worth knowing the
> difference between "the test is wrong" and "the code is wrong".

## Step 4: Add Gradle caching, compare run times

First, record a baseline: note the duration of a CI run from Steps 1 to 3 (no
cache). A cold Gradle run re-downloads Spring Boot plus the Kotlin/JUnit
toolchain every time.

Now switch the workflow to use the official `gradle/actions/setup-gradle`
action, which caches the Gradle dependencies and build outputs, keyed on your
build files. Replace `.github/workflows/ci.yml` with:

```yaml
name: CI

on:
  push:
    branches: [main]
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

      - name: Change wrapper permissions
        run: chmod +x ./gradlew

      # This action restores/saves ~/.gradle (downloaded deps + build cache).
      - name: Set up Gradle (with caching)
        uses: gradle/actions/setup-gradle@v4

      - name: Build and test
        run: ./gradlew build --no-daemon
```

Push this on a branch. The **first** run populates the cache (still slow-ish, it
has to save). **Subsequent** runs restore it, and the dependency download
disappears from the logs.

What to look for:
- Total job duration: cold run vs warm run (expect a meaningful drop, often
  roughly a minute or more saved on the dependency phase).
- The `Cache restored` / `Restored Gradle dependencies` lines in the
  "Set up Gradle" step logs.
- The trade-off behind caching: cache *keys*, cache *invalidation* (changing
  `build.gradle.kts` busts it), and cache size limits.

## Step 5: Require the CI check before merge (branch protection)

Make green CI mandatory so a red pipeline blocks merging.

> **Before you start, check that rulesets are available to you.** Depending on
> your account type and your permissions on the repo, this step may not be
> possible. If so, just skip it. See
> [About rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets)
> for the details. In short:
>
> - Anyone with read access to a repository can view its rulesets. Creating,
>   editing, and deleting them requires admin access, or a custom role with the
>   "edit repository rules" permission.
> - Rulesets are available in public repositories with GitHub Free and GitHub
>   Free for organizations, and in public and private repositories with GitHub
>   Pro, GitHub Team, and GitHub Enterprise Cloud.
> - Push rulesets are available for the GitHub Team plan in internal and private
>   repositories, and forks of repositories that have push rulesets enabled.

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

## Step 6 (bridge to Lab 3): Containerize it

A `Dockerfile` ships with this repo: a multi-stage build that produces the
Spring Boot fat jar, then copies it into a tiny JRE-only runtime image (the
Block 4 pattern). Build and smoke-test it locally:

```bash
docker build -t workshop-application:dev .
docker run --rm -p 8080:8080 workshop-application:dev
# then, in another shell:
curl -s -X POST localhost:8080/quote \
  -H 'Content-Type: application/json' -d '{"subtotal": 150.00}'
# {"subtotal":150.00,"total":135.00}
```

In **Lab 3** you'll tag this image for the shared registry under your own
per-user namespace
(`registry.ff26.it/ec-0X/workshop-application:<git-sha>`, where `ec-0X` is your
participant number), push it, and deploy it via Argo CD. Lab 3's appendix shows
how to make CI build, push, and bump the GitOps config repo automatically on
every commit here.

---

## Project layout

```
workshop-application/
├─ .github/workflows/ci.yml          # the pipeline (Step 1; upgraded in Step 4)
├─ Dockerfile                        # multi-stage build (used in Lab 3)
├─ .dockerignore
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
