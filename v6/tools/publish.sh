#!/usr/bin/env bash
# v6 を公開版(gh-pages の v6/)に反映する。版(BUILD)を1つ上げ、公開URLで版と内容を確かめるまでが1回の公開。
#
# 使い方: v6/tools/publish.sh <ソースのref> <変更したファイル(リポジトリ直下からの相対パス)>...
#   例:   v6/tools/publish.sh HEAD v6/js/parts.js v6/js/s-ents.js v6/css/parts.css
# 環境変数 DRY=1 にすると、gh-pages への push と公開URLの確認を行わず、コミットの内容だけ作って消す。
#
# 注意: 公開版の v6/ はソースと別系統なので、指定したファイルだけを上書きする(v6/ 全体は上書きしない)。
set -euo pipefail

REF=${1:?ソースのref(例: HEAD)を指定}
shift
[ $# -gt 0 ] || { echo "変更したファイルを指定する" >&2; exit 1; }

REPO=$(git rev-parse --show-toplevel)
URL=https://amgm-087ff5.github.io/personal-record-app/v6
WT=$(mktemp -d)
BRANCH=gh-pages-publish

cleanup() {
  git -C "$REPO" worktree remove --force "$WT" >/dev/null 2>&1 || rm -rf "$WT"
  git -C "$REPO" branch -D "$BRANCH" >/dev/null 2>&1 || true
}
trap cleanup EXIT

git -C "$REPO" fetch -q origin gh-pages
git -C "$REPO" worktree add -q -B "$BRANCH" "$WT" origin/gh-pages
cd "$WT"

# 指定ファイルだけをソースの内容で置き換える
for f in "$@"; do
  git -C "$REPO" show "$REF:$f" > "$f"
done

# 版を1つ上げる(v6.1.3 → v6.1.4)。日時は公開した時刻
CUR=$(grep -o 'window.BUILD = "v6\.[0-9.]*' v6/index.html | grep -o 'v6\.[0-9.]*' | head -1)
[ -n "$CUR" ] || { echo "v6/index.html の window.BUILD が読めない" >&2; exit 1; }
PATCH=${CUR##*.}
NEXT="${CUR%.*}.$((PATCH + 1))"
NEW="$NEXT · $(date +%Y%m%d-%H%M)"
sed -i "s/window.BUILD = \"v6\.[0-9.]* · [0-9-]*\";/window.BUILD = \"$NEW\";/" v6/index.html

git add v6/index.html "$@"
git commit -q -F - <<EOF
publish UI v6 $NEXT

変更したファイル: $*
ソース: $REF
版を上げ、キャッシュで古い版が出ないようにする。

Co-Authored-By: Claude Haiku 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Pe3Z8PxZ1nKrRztmfv5GMs
EOF
echo "版: $NEW"

if [ "${DRY:-}" = "1" ]; then
  echo "DRY=1 のため push と公開URLの確認は行わない"
  git log --oneline -1
  git show --stat HEAD | tail -n +5
  exit 0
fi

git push -q origin HEAD:gh-pages

# 公開URLで版の表記を確かめる(配信に少し時間がかかるので待つ)
ok=""
for _ in $(seq 1 20); do
  if curl -s -H 'Cache-Control: no-cache' "$URL/index.html?c=$(date +%s)" | grep -q "window.BUILD = \"$NEW\""; then
    ok=1
    break
  fi
  sleep 20
done
[ -n "$ok" ] || { echo "NG: 公開URLの版が $NEW にならない" >&2; exit 1; }

# 変更したファイルの中身が、公開URLで同じか確かめる(クエリを付けてCDNの古い応答を避ける)
for f in "$@"; do
  served=$(curl -s "$URL/${f#v6/}?c=$(date +%s)" | sha256sum | cut -d' ' -f1)
  source=$(git -C "$REPO" show "$REF:$f" | sha256sum | cut -d' ' -f1)
  if [ "$served" = "$source" ]; then
    echo "OK  $f"
  else
    echo "NG  $f: 公開版の内容がソースと違う" >&2
    exit 1
  fi
done

echo "公開完了 版 $NEW"
echo "確認先: $URL/#/acts"
