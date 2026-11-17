#!/bin/sh
# 公開前の全検査(退行の再発防止)。v7/tools の t_*.py 全部 + model_test.mjs + audit + regress を順に実行し、最後に合否を1行で出す。
# どれか1つでも失敗したら非0で終わる。使い方: sh v7/tools/check_all.sh
cd "$(dirname "$0")/.." || exit 1
ng=0
out=$(node tools/model_test.mjs 2>&1); rc=$?
if [ $rc -ne 0 ] || echo "$out" | grep -q "FAIL\|NG"; then echo "NG  model_test"; echo "$out" | tail -5; ng=1; else echo "OK  model_test"; fi
out=$(node tools/model_sync.mjs 2>&1); rc=$?
if [ $rc -ne 0 ]; then echo "NG  model_sync"; echo "$out" | tail -8; ng=1; else echo "OK  model_sync"; fi
out=$(python3 -I tools/regress.py 2>&1); rc=$?
if [ $rc -ne 0 ] || echo "$out" | grep -q "FAIL\|NG"; then echo "NG  regress"; echo "$out" | tail -5; ng=1; else echo "OK  regress"; fi
for f in tools/t_*.py; do
  t=$(basename "$f" .py); [ "$t" = "t_w1_common" ] && continue     # 共通の土台(単体では実行しない)
  out=$(python3 -I "$f" 2>&1); rc=$?
  if [ $rc -ne 0 ] || echo "$out" | grep -q "FAIL\|NG"; then echo "NG  $t"; echo "$out" | tail -5; ng=1; else echo "OK  $t"; fi
done
out=$(python3 tools/audit.py 2>&1); echo "$out" | tail -1
if echo "$out" | tail -1 | grep -q "'err': 0, 'small': 0, 'taps': 0, 'over': 0" && ! echo "$out" | grep -q "直書きの色・小さい文字: \["; then echo "OK  audit"; else echo "NG  audit"; echo "$out" | tail -8; ng=1; fi
[ $ng -eq 0 ] && echo "ALL OK" || echo "NG あり"
exit $ng
