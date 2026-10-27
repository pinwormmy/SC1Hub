#!/usr/bin/env bash
# AWS 과금 점검 (읽기 전용, 월 1회 이상).
# - 예산 대비 이번 달 실제 금액·월말 예상 금액 (Budgets API: 무료. Cost Explorer API는 요청당 $0.01이라 쓰지 않는다)
# - 루트 MFA
# - 모든 리전의 Lightsail·EC2·RDS에서 돈이 나갈 수 있는 리소스
# 정상 상태는 "서울의 sc1hub 인스턴스 1개(실행 중) + 거기에 붙은 고정 IP 1개"뿐이다. 그 밖의 것은 전부 경고로 센다.
# 종료 코드: 0 정상, 2 경고 있음.
set -euo pipefail

export AWS_PAGER=""

HOME_REGION="${SC1HUB_AWS_REGION:-ap-northeast-2}"
INSTANCE_NAME="${SC1HUB_LIGHTSAIL_INSTANCE:-sc1hub}"
BUDGET_NAME="${SC1HUB_BUDGET_NAME:-sc1hub-monthly}"

for cmd in aws jq; do
    command -v "$cmd" >/dev/null 2>&1 || { echo "$cmd is required." >&2; exit 1; }
done

warnings=0
warn() {
    echo "  !! $*"
    warnings=$((warnings + 1))
}

# items runs inside process substitutions, so it cannot bump the counter itself; denials go to a file counted at the end.
DENIED_LOG="$(mktemp "${TMPDIR:-/tmp}/aws-billing-check.XXXXXX")"
trap 'rm -f "$DENIED_LOG"' EXIT

# Prints one line per item from `aws <service> <command> --region <region>` run through a jq filter.
# Access errors count as warnings because they hide resources; other errors (an API a region lacks) are only noted.
items() {
    local region="$1" filter="$2" out
    shift 2
    if ! out="$(aws "$@" --region "$region" --output json 2>&1)"; then
        if [[ "$out" == *AccessDenied* || "$out" == *UnauthorizedOperation* || "$out" == *UnrecognizedClient* ]]; then
            echo "$region: $* — permission denied, resources may be hidden" >>"$DENIED_LOG"
        else
            echo "  ?  $region: $* — $(tail -n 1 <<<"$out")" >&2
        fi
        return 0
    fi
    jq -r "$filter" <<<"$out"
}

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
echo "== Account $ACCOUNT_ID ($(date '+%Y-%m-%d %H:%M %Z'))"

mfa="$(aws iam get-account-summary --query 'SummaryMap.AccountMFAEnabled' --output text 2>/dev/null || echo unknown)"
if [[ "$mfa" == "1" ]]; then
    echo "root MFA: on"
else
    warn "root MFA: $mfa"
fi

echo
echo "== Budget"
if budget="$(aws budgets describe-budget --account-id "$ACCOUNT_ID" --budget-name "$BUDGET_NAME" --output json 2>/dev/null)"; then
    jq -r '.Budget | "limit \(.BudgetLimit.Amount) \(.BudgetLimit.Unit) | actual this month \(.CalculatedSpend.ActualSpend.Amount // "0") | forecast \(.CalculatedSpend.ForecastedSpend.Amount // "n/a")"' <<<"$budget"
    over="$(jq -r '.Budget | ((.CalculatedSpend.ActualSpend.Amount // "0") | tonumber) >= ((.BudgetLimit.Amount | tonumber) * 0.8)' <<<"$budget")"
    [[ "$over" == "true" ]] && warn "actual spend is at 80% of the budget or more"
    alerts="$(aws budgets describe-notifications-for-budget --account-id "$ACCOUNT_ID" --budget-name "$BUDGET_NAME" \
        --query 'length(Notifications)' --output text 2>/dev/null || echo 0)"
    [[ "$alerts" -ge 3 ]] || warn "budget has $alerts alerts (expected 3) — rerun scripts/aws-budget-setup.sh"
else
    warn "budget '$BUDGET_NAME' not found — run scripts/aws-budget-setup.sh <email>"
fi

echo
echo "== Lightsail (all regions)"
found_instance=0
for region in $(aws lightsail get-regions --region "$HOME_REGION" --query 'regions[].name' --output text); do
    while IFS=$'\t' read -r name bundle state autosnap; do
        [[ -z "$name" ]] && continue
        echo "  $region instance $name ($bundle, $state, auto-snapshot $autosnap)"
        if [[ "$region" == "$HOME_REGION" && "$name" == "$INSTANCE_NAME" ]]; then
            found_instance=1
            [[ "$state" == "running" ]] || warn "$name is $state — a stopped instance is still billed"
            [[ "$autosnap" == "off" ]] || warn "$name has automatic snapshots on (billed per GB)"
        else
            warn "unexpected instance $region/$name"
        fi
    done < <(items "$region" '.instances[] | [.name, .bundleId, .state.name,
        (if any(.addOns[]?; .name == "AutoSnapshot" and .status == "Enabled") then "on" else "off" end)] | @tsv' \
        lightsail get-instances)

    while IFS=$'\t' read -r name ip attached to; do
        [[ -z "$name" ]] && continue
        echo "  $region static IP $name $ip -> ${to:-nothing}"
        [[ "$attached" == "true" ]] || warn "static IP $region/$name is unattached — billed until released"
        [[ "$region" == "$HOME_REGION" ]] || warn "static IP outside $HOME_REGION: $region/$name"
    done < <(items "$region" '.staticIps[] | [.name, .ipAddress, (.isAttached | tostring), (.attachedTo // "")] | @tsv' \
        lightsail get-static-ips)

    # Everything below is billable and not part of the planned setup.
    while IFS= read -r line; do
        [[ -n "$line" ]] && warn "$region $line"
    done < <(
        items "$region" '.instanceSnapshots[] | "instance snapshot \(.name) (\(.sizeInGb) GB)"' lightsail get-instance-snapshots
        items "$region" '.disks[] | "disk \(.name) (\(.sizeInGb) GB)"' lightsail get-disks
        items "$region" '.diskSnapshots[] | "disk snapshot \(.name) (\(.sizeInGb) GB)"' lightsail get-disk-snapshots
        items "$region" '.loadBalancers[] | "load balancer \(.name)"' lightsail get-load-balancers
        items "$region" '.relationalDatabases[] | "database \(.name) (\(.relationalDatabaseBundleId))"' lightsail get-relational-databases
        items "$region" '.buckets[] | "bucket \(.name) (\(.bundleId))"' lightsail get-buckets
        items "$region" '.containerServices[] | "container service \(.containerServiceName) (\(.power))"' lightsail get-container-services
        if [[ "$region" == "us-east-1" ]]; then
            # Distributions and DNS zones are global and only answer in us-east-1.
            items "$region" '.distributions[] | "distribution \(.name) (\(.bundleId))"' lightsail get-distributions
            items "$region" '.domains[] | "DNS zone \(.name)"' lightsail get-domains
        fi
    )
done
[[ "$found_instance" == "1" ]] || echo "  (no $INSTANCE_NAME instance in $HOME_REGION yet)"

echo
echo "== EC2 / RDS (all enabled regions, should be empty)"
for region in $(aws ec2 describe-regions --region "$HOME_REGION" --query 'Regions[].RegionName' --output text); do
    while IFS= read -r line; do
        [[ -n "$line" ]] && warn "$region $line"
    done < <(
        items "$region" '.Reservations[].Instances[] | select(.State.Name != "terminated") | "EC2 instance \(.InstanceId) (\(.InstanceType), \(.State.Name))"' \
            ec2 describe-instances
        items "$region" '.Volumes[] | "EBS volume \(.VolumeId) (\(.Size) GB, \(.State))"' ec2 describe-volumes
        items "$region" '.Addresses[] | "Elastic IP \(.PublicIp)"' ec2 describe-addresses
        items "$region" '.NatGateways[] | select(.State != "deleted") | "NAT gateway \(.NatGatewayId) (\(.State))"' ec2 describe-nat-gateways
        items "$region" '.Snapshots[] | "EBS snapshot \(.SnapshotId) (\(.VolumeSize) GB)"' ec2 describe-snapshots --owner-ids self
        items "$region" '.DBInstances[] | "RDS \(.DBInstanceIdentifier) (\(.DBInstanceClass))"' rds describe-db-instances
    )
done

while IFS= read -r line; do
    warn "$line"
done <"$DENIED_LOG"

echo
if [[ "$warnings" -eq 0 ]]; then
    echo "OK: nothing unexpected is billing."
    exit 0
fi
echo "$warnings warning(s). Delete what you do not need in the console (stopping a Lightsail instance does not stop billing)."
exit 2
