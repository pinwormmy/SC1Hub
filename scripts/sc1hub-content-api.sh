#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
CONFIG_FILE="${SC1HUB_CONTENT_API_CONFIG:-$PROJECT_DIR/.content-api.env}"

if [[ -f "$CONFIG_FILE" ]]; then
    # shellcheck disable=SC1090
    source "$CONFIG_FILE"
fi

BASE_URL="${SC1HUB_CONTENT_API_BASE_URL:-https://sc1hub.com}"
TOKEN="${SC1HUB_CONTENT_API_TOKEN:-}"
KEYCHAIN_SERVICE="${SC1HUB_CONTENT_API_KEYCHAIN_SERVICE:-sc1hub-content-api}"

if [[ -z "$TOKEN" ]] && command -v security >/dev/null 2>&1; then
    TOKEN="$(security find-generic-password -a "$(id -un)" -s "$KEYCHAIN_SERVICE" -w 2>/dev/null || true)"
fi

if [[ -z "$TOKEN" ]]; then
    echo "SC1HUB_CONTENT_API_TOKEN or the '$KEYCHAIN_SERVICE' Keychain item is required." >&2
    exit 1
fi

AUTH_HEADER="Authorization: Bearer $TOKEN"
API_ROOT="$BASE_URL/api/admin/content"

request() {
    curl --fail --silent --show-error -H "$AUTH_HEADER" "$@"
    echo
}

usage() {
    cat <<'EOF'
Usage:
  scripts/sc1hub-content-api.sh boards
  scripts/sc1hub-content-api.sh list BOARD [LIMIT]
  scripts/sc1hub-content-api.sh read BOARD POST_NUM
  scripts/sc1hub-content-api.sh upload IMAGE_FILE
  scripts/sc1hub-content-api.sh publish BOARD TITLE CONTENT_HTML_FILE [IMAGE_FILE] [YOUTUBE_URL]

Optional publish environment variables:
  SC1HUB_POST_IMAGE_ALT
  SC1HUB_POST_IMAGE_CAPTION
  SC1HUB_POST_YOUTUBE_TITLE
  SC1HUB_POST_WRITER
  SC1HUB_POST_NOTICE=true|false
EOF
}

command="${1:-}"
case "$command" in
    boards)
        request "$API_ROOT/boards"
        ;;
    list)
        board="${2:?BOARD is required}"
        limit="${3:-20}"
        request "$API_ROOT/boards/$board/posts?limit=$limit"
        ;;
    read)
        board="${2:?BOARD is required}"
        post_num="${3:?POST_NUM is required}"
        request "$API_ROOT/boards/$board/posts/$post_num"
        ;;
    upload)
        image_file="${2:?IMAGE_FILE is required}"
        request -X POST -F "upload=@$image_file" "$API_ROOT/images"
        ;;
    publish)
        board="${2:?BOARD is required}"
        title="${3:?TITLE is required}"
        content_file="${4:?CONTENT_HTML_FILE is required}"
        image_file="${5:-}"
        youtube_url="${6:-}"
        curl_args=(
            -X POST
            -F "title=$title"
            -F "content=<$content_file"
            -F "notice=${SC1HUB_POST_NOTICE:-false}"
        )
        if [[ -n "${SC1HUB_POST_WRITER:-}" ]]; then
            curl_args+=( -F "writer=$SC1HUB_POST_WRITER" )
        fi
        if [[ -n "$image_file" ]]; then
            curl_args+=( -F "upload=@$image_file" )
            curl_args+=( -F "imageAlt=${SC1HUB_POST_IMAGE_ALT:-$title}" )
            if [[ -n "${SC1HUB_POST_IMAGE_CAPTION:-}" ]]; then
                curl_args+=( -F "imageCaption=$SC1HUB_POST_IMAGE_CAPTION" )
            fi
        fi
        if [[ -n "$youtube_url" ]]; then
            curl_args+=( -F "youtubeUrl=$youtube_url" )
            curl_args+=( -F "youtubeTitle=${SC1HUB_POST_YOUTUBE_TITLE:-유튜브 영상}" )
        fi
        request "${curl_args[@]}" "$API_ROOT/boards/$board/posts"
        ;;
    *)
        usage
        exit 1
        ;;
esac
