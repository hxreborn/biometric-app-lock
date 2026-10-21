#!/usr/bin/env python3
"""Convert `git cliff --context` JSON output into the in-app CHANGELOG.json shape.

Invoked by .github/release.sh as:
    git cliff --config .github/cliff.toml --context --tag "$TAG" \
        | python3 tools/cliff_to_changelog.py "$TAG" > CHANGELOG.json

Stdin: cliff context JSON (list of releases, each with `version`, `commits`).
Stdout: in-app CHANGELOG.json (latest MAX_VERSIONS releases, keyed by version).
"""

from __future__ import annotations

import datetime as dt
import json
import re
import sys
import tomllib
from pathlib import Path

CLIFF_TOML = Path(__file__).resolve().parent.parent / ".github" / "cliff.toml"

GROUP_TO_TYPE = {
    "Features": "feat",
    "Bug Fixes": "fix",
    "Performance Improvements": "perf",
    "Security": "security",
    "Refactor": "refactor",
    "Reverts": "revert",
    "Tests": "test",
    "Miscellaneous": "misc",
}

SKIPPED_GROUPS = {"CI/CD"}

GROUP_PREFIX_RE = re.compile(r"^<!--\s*\d+\s*-->")


def clean_group(group: str | None) -> str:
    if not group:
        return ""
    return GROUP_PREFIX_RE.sub("", group).strip()


def map_type(group: str | None, breaking: bool) -> str:
    if breaking:
        return "breaking"
    return GROUP_TO_TYPE.get(clean_group(group), "misc")


def load_remote() -> tuple[str, str]:
    with CLIFF_TOML.open("rb") as f:
        data = tomllib.load(f)
    remote = data.get("remote", {}).get("github", {})
    return remote.get("owner", ""), remote.get("repo", "")


def commit_url(owner: str, repo: str, commit: dict) -> str | None:
    remote = commit.get("remote") or {}
    pr_number = remote.get("pr_number")
    cid = commit.get("id")
    if not (owner and repo):
        return None
    if pr_number:
        return f"https://github.com/{owner}/{repo}/pull/{pr_number}"
    if cid:
        return f"https://github.com/{owner}/{repo}/commit/{cid}"
    return None


def iso_date(ts: int | None) -> str | None:
    if not ts:
        return None
    return dt.datetime.fromtimestamp(int(ts), tz=dt.timezone.utc).date().isoformat()


def transform_commit(commit: dict, version: str, owner: str, repo: str) -> dict:
    body = commit.get("body")
    if body is not None:
        body = body.strip() or None
    committer_ts = (commit.get("committer") or {}).get("timestamp")
    entry = {
        "type": map_type(commit.get("group"), bool(commit.get("breaking"))),
        "title": (commit.get("message") or "").strip(),
        "description": body,
        "version": version,
        "date": iso_date(committer_ts),
        "url": commit_url(owner, repo, commit),
        "scope": commit.get("scope"),
        "breaking": bool(commit.get("breaking")),
    }
    return entry


MAX_VERSIONS = 5


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"usage: {argv[0]} <tag>", file=sys.stderr)
        return 2
    tag = argv[1]
    version = tag[1:] if tag.startswith("v") else tag

    raw = sys.stdin.read()
    if not raw.strip():
        print("empty cliff context on stdin", file=sys.stderr)
        return 1

    context = json.loads(raw)
    if not isinstance(context, list) or not context:
        print("expected non-empty cliff context array", file=sys.stderr)
        return 1

    owner, repo = load_remote()
    entries: list[dict] = []
    for release in context[:MAX_VERSIONS]:
        rel_version = release.get("version") or version
        if rel_version.startswith("v"):
            rel_version = rel_version[1:]
        for c in release.get("commits") or []:
            if clean_group(c.get("group")) in SKIPPED_GROUPS:
                continue
            entries.append(transform_commit(c, rel_version, owner, repo))

    payload = {"version": version, "entries": entries}

    json.dump(payload, sys.stdout, indent=2, ensure_ascii=False)
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
