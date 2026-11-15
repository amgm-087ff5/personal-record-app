#!/bin/sh
# 公開前の全検査(退行の再発防止)。どれか1つでも失敗したら非0で終わる。使い方: sh v6/tools/check_all.sh
cd "$(dirname "$0")/.." || exit 1
ng=0
for t in regress t_w1_1_convert t_w1_2_save t_w1_3_llm t_w1_4_off t_w1_5_end t_w2_pay t_w2_ledger t_w3_ents t_w3_todo t_w3_misc t_w4_data t_w4_undo t_w4_hol t_w4_ui; do
  out=$(python3 -I tools/$t.py 2>&1); rc=$?
  if [ $rc -ne 0 ] || echo "$out" | grep -q "FAIL\|NG"; then echo "NG  $t"; echo "$out" | tail -5; ng=1; else echo "OK  $t"; fi
done
out=$(python3 tools/audit.py 2>&1); echo "$out" | tail -1
echo "$out" | tail -1 | grep -q "'err': 0, 'small': 0, 'taps': 0, 'over': 0" && ! echo "$out" | grep -q "直書きの色・小さい文字: \[" || ng=1
[ $ng -eq 0 ] && echo "ALL OK" || echo "NG あり"
exit $ng
