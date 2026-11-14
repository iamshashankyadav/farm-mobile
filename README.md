# farm-mobile

Mobile apps for the Farm Platform, built by **Team 1**: the **customer app** (Produce tab for V1 buying, Land tab for V2 plot reservation) and the **delivery app** (pooled routes, pickup and proof of delivery).

All data comes from the shared backend ([farm-backend](https://github.com/lakshayg2005/farm-backend)) through the API gateway at `/api/v1`.
The typed API client is generated from the backend's OpenAPI specs (`pnpm gen:api`), and a mock server built from the same specs (`pnpm mock`) lets screens be built before a service is ready.

## Stack

React Native with Expo, TypeScript, Expo Router, TanStack Query, react-native-maps, Expo Notifications, EAS Build, pnpm workspace.

## Layout

```text
apps/customer/      customer app (Produce + Land tabs)
apps/delivery/      delivery partner app
packages/ui/        shared components
packages/api/       generated API client + auth
```

## Workflow (all repos)

- `main` is protected: changes go in only through a pull request with 1 approval.
- One issue per branch: `feat/<area>-<short-name>` or `fix/<area>-<short-name>`. Merge within 2 to 3 days.
- Commit messages follow Conventional Commits: `feat:`, `fix:`, `chore:`, `docs:`, `test:`.
- Squash-merge PRs and write `Closes #<issue>` in the PR description.
- Never commit `.env`; copy `.env.example` instead.
- Issues are labelled by team (`team-app` = Team 1, `team-web` = Team 2, `team-joint`) and by member slot (`M1`–`M4` Team 1, `M5`–`M8` Team 2). Pick your slot and assign yourself.

## Getting started

Setup instructions are added by the Sprint 1 scaffold issue.
