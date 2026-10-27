#!/usr/bin/env bash
# 월 비용 예산(기본 $10)과 메일 알림 3개(실제 80%·100%, 월말 예상 100%)를 만든다.
# 같은 이름의 예산이 이미 있으면 바꾸지 않고 현재 설정만 보여 준다. Budgets 조회·알림은 무료다.
set -euo pipefail

export AWS_PAGER=""

BUDGET_NAME="${SC1HUB_BUDGET_NAME:-sc1hub-monthly}"
LIMIT_USD="${SC1HUB_BUDGET_LIMIT_USD:-10}"

usage() {
    echo "Usage: $0 <alert-email>" >&2
    echo "  SC1HUB_BUDGET_LIMIT_USD (default 10), SC1HUB_BUDGET_NAME (default sc1hub-monthly)" >&2
    exit 1
}

[[ $# -eq 1 && "$1" == *@*.* ]] || usage
EMAIL="$1"

for cmd in aws jq; do
    command -v "$cmd" >/dev/null 2>&1 || { echo "$cmd is required." >&2; exit 1; }
done

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"

show_budget() {
    aws budgets describe-budget --account-id "$ACCOUNT_ID" --budget-name "$BUDGET_NAME" --output json \
        | jq -r '.Budget | "budget \(.BudgetName): \(.BudgetLimit.Amount) \(.BudgetLimit.Unit) per \(.TimeUnit)"'
    aws budgets describe-notifications-for-budget --account-id "$ACCOUNT_ID" --budget-name "$BUDGET_NAME" --output json \
        | jq -r '.Notifications[] | "  alert: \(.NotificationType) > \(.Threshold)%"'
}

if aws budgets describe-budget --account-id "$ACCOUNT_ID" --budget-name "$BUDGET_NAME" >/dev/null 2>&1; then
    echo "Budget '$BUDGET_NAME' already exists in account $ACCOUNT_ID; leaving it unchanged."
    show_budget
    exit 0
fi

budget="$(jq -n --arg name "$BUDGET_NAME" --arg limit "$LIMIT_USD" '{
    BudgetName: $name,
    BudgetLimit: {Amount: $limit, Unit: "USD"},
    TimeUnit: "MONTHLY",
    BudgetType: "COST"
}')"

notifications="$(jq -n --arg email "$EMAIL" '
    [["ACTUAL", 80], ["ACTUAL", 100], ["FORECASTED", 100]]
    | map({
        Notification: {
            NotificationType: .[0],
            ComparisonOperator: "GREATER_THAN",
            Threshold: .[1],
            ThresholdType: "PERCENTAGE"
        },
        Subscribers: [{SubscriptionType: "EMAIL", Address: $email}]
    })')"

aws budgets create-budget \
    --account-id "$ACCOUNT_ID" \
    --budget "$budget" \
    --notifications-with-subscribers "$notifications"

echo "Created budget '$BUDGET_NAME' in account $ACCOUNT_ID."
show_budget
