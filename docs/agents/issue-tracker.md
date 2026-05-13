# Issue tracker: GitHub

Issues and PRDs for this repo live as GitHub issues in `fffcccdfgh-sys/test`.

Use the `gh` CLI for issue operations when it is available. Infer the repository from `git remote -v`; `gh` does this automatically when run inside this clone.

## Conventions

- **Create an issue**: `gh issue create --title "..." --body "..."`
- **Read an issue**: `gh issue view <number> --comments`
- **List issues**: `gh issue list --state open --json number,title,body,labels,comments`
- **Comment on an issue**: `gh issue comment <number> --body "..."`
- **Apply / remove labels**: `gh issue edit <number> --add-label "..."` / `--remove-label "..."`
- **Close**: `gh issue close <number> --comment "..."`

## When a skill says "publish to the issue tracker"

Create a GitHub issue in `fffcccdfgh-sys/test`.

## When a skill says "fetch the relevant ticket"

Run `gh issue view <number> --comments`.
