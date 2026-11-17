#!/bin/sh
# git フックを有効にする(クローンごとに1回)。
cd "$(dirname "$0")/.." && chmod +x .githooks/* && git config core.hooksPath .githooks && echo "git hooks を有効にしました(.githooks)"
