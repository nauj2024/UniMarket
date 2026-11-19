## Some tips

* when a documented decision or constraint changes, rewrite or remove the affected text
* do not commit or push until i explicity say so
* most code changes do not need a documentation change. Agents can read the code.

## Running the app

* Default to `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` for local development and manual checks. This provides simulated login and sample data in the separate `unimarket-dev.db` database.
* Use another profile when the task specifically requires real Google login, deployment behavior, or the user requests it.

## Project context

* Before implementing or reviewing product behavior, domain models, or architecture, follow [docs/README.md](docs/README.md) to the relevant requirements, design, and unresolved decisions.

## Pull requests

* When writing or updating a PR body, follow the [PR skill](.agents/skills/pr/SKILL.md).
