# farm-mobile

Mobile apps for the Farm Platform, built by **Team 1**: the **customer app** (Produce for V1
buying, Land for V2 plot reservation) and the **delivery app** (pooled routes, pickup and proof of
delivery).

Both are native Android apps (Kotlin + Jetpack Compose) in [`android/`](android/). All data comes
from the shared backend ([farm-backend](https://github.com/lakshayg2005/farm-backend)) through the
API gateway at `/api/v1`.

```text
android/core/       API client, auth, theme and shared UI
android/customer/   customer app (Produce, Land, Orders)
android/delivery/   delivery partner app (route, pickup, proof of delivery)
```

Build, server address and install steps: [`android/README.md`](android/README.md).

The earlier React Native / Expo apps (`apps/`, `packages/`) were retired (#51). They are kept, as
they were, on the `archive/expo` branch.

## Workflow

- `main` is protected: changes go in only through a pull request with 1 approval.
- One issue per branch: `feat/android-<short-name>` or `fix/android-<short-name>`.
- Commit messages follow Conventional Commits: `feat:`, `fix:`, `chore:`, `docs:`, `test:`.
- Squash-merge PRs and write `Closes #<issue>` in the PR description.
- Never commit `.env`, keystores, `google-services.json` or API keys.
- Issues are labelled `team-app` (Team 1) and `team-web` (Team 2).
