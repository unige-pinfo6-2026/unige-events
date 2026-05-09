#!/usr/bin/env bash
# Étape 22 — agrégation coverage jacoco multi-module pour Sonar Option B.
# Lance après `cd backend && ./mvnw verify -DskipITs`.
#
# Output :
#   - Tableau per-module L %/B %
#   - Total agrégé L %/B %
#   - Liste des classes < 80 % L (debug)
set -euo pipefail

cd "$(dirname "$0")/.."

total_lm=0
total_lc=0
total_bm=0
total_bc=0

echo "Module                              L%      L missed/total    B%      B missed/total"
echo "---------------------------------- ------- ----------------- ------- -----------------"

for csv in services/*/target/jacoco-report/jacoco.csv contract-tests/target/jacoco-report/jacoco.csv e2e/target/jacoco-report/jacoco.csv; do
    [ -f "$csv" ] || continue
    module=$(echo "$csv" | sed -E 's|^(services/)?([^/]+)/target.*|\2|')
    awk -F, -v mod="$module" 'NR>1 {lm+=$8; lc+=$9; bm+=$6; bc+=$7}
        END { lt=lm+lc; bt=bm+bc;
              if(lt>0) printf "%-34s %6.1f%% %d/%d %20s %6.1f%% %d/%d\n",
                              mod, lc*100/lt, lm, lt, "", (bt>0?bc*100/bt:0), bm, bt
              else     printf "%-34s %6s        ---/--- %25s %6s        ---/---\n",
                              mod, "n/a", "", "n/a"
        }' "$csv"
    read -r total_lm total_lc total_bm total_bc < <(awk -F, -v lm0="$total_lm" -v lc0="$total_lc" -v bm0="$total_bm" -v bc0="$total_bc" \
        'NR>1 {lm+=$8; lc+=$9; bm+=$6; bc+=$7}
         END {printf "%d %d %d %d\n", lm0+lm, lc0+lc, bm0+bm, bc0+bc}' "$csv")
done

echo "---------------------------------- ------- ----------------- ------- -----------------"
total_lt=$((total_lm + total_lc))
total_bt=$((total_bm + total_bc))
total_lp=$(awk -v c=$total_lc -v t=$total_lt 'BEGIN{if(t>0)printf "%.1f",c*100/t; else print "0.0"}')
total_bp=$(awk -v c=$total_bc -v t=$total_bt 'BEGIN{if(t>0)printf "%.1f",c*100/t; else print "0.0"}')
printf "%-34s %6s%%  %d/%d %20s %6s%%  %d/%d\n" \
    "TOTAL" "$total_lp" "$total_lm" "$total_lt" "" "$total_bp" "$total_bm" "$total_bt"

# Exit code : 0 si L ≥ 80 % et B ≥ 70 %
if awk -v lp="$total_lp" -v bp="$total_bp" 'BEGIN{exit !(lp>=80 && bp>=70)}'; then
    echo
    echo "✅ PASS — coverage globale L $total_lp % ≥ 80 % et B $total_bp % ≥ 70 %"
    exit 0
else
    echo
    echo "❌ FAIL — coverage globale L $total_lp % < 80 % ou B $total_bp % < 70 %"
    echo
    echo "Classes < 80 % L (top 30) :"
    for csv in services/*/target/jacoco-report/jacoco.csv; do
        [ -f "$csv" ] || continue
        awk -F, 'NR>1 && $8+$9>0 {p=$9*100/($8+$9); if(p<80) printf "  %-32s %6.1f%% L (in %s)\n", $3, p, FILENAME}' "$csv"
    done | sort -k2 -n | head -30
    exit 1
fi
