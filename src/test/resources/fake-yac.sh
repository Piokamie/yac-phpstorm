if [ "deaf.php" = "$2" ]; then
  sleep 5
  exit 0
fi
input=$(cat)
case "$2" in
  ok.php)
    comment=$(printf '%s' "$input" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' | awk 'NR>1 {printf "\\n"} {printf "%s", $0}')
    case "$input" in
      BROKEN*) status=unparseable_source ;;
      *) status=resolved ;;
    esac
    printf '{"schema": 1, "files": [{"source": "ok.php", "annotations": [{"id": "yac_01JB8M3Z4XAAAAAAAAAAAAAAAA", "source": "ok.php", "line": 3, "status": "%s", "scope": "Foo::bar", "before": null, "anchor": "foo();", "after": null, "comment": "%s", "matches": [3], "problems": [], "future": true}]}], "warnings": ["Sidecar .yac/x.php.yac is invalid"]}' "$status" "$comment"
    ;;
  old.php)
    printf '\n  The "--stdin" option does not exist.  \n\n' >&2
    exit 2
    ;;
  schema.php)
    printf '{"schema": 2, "files": []}'
    ;;
  noschema.php)
    printf '{"files": []}'
    ;;
  broken.php)
    printf 'Error: boom\n' >&2
    exit 2
    ;;
  slow.php)
    sleep 5
    ;;
  xdebug.php)
    printf '%s' "$XDEBUG_MODE"
    ;;
  ini.php)
    printf '%s' "$YAC_FAKE_PHP_OPTION"
    ;;
  long.php)
    printf 'Fatal: %0250d\nsecond\n' 0 >&2
    exit 255
    ;;
  multiline.php)
    printf 'Error: first\nsecond\n' >&2
    exit 2
    ;;
  *)
    printf 'args:%s\n' "$*"
    printf 'Warning: careful\n' >&2
    exit 1
    ;;
esac
