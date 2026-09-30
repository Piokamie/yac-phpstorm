input=$(cat)
case "$2" in
  ok.php)
    case "$input" in
      BROKEN*) status=unparseable_source ;;
      *) status=resolved ;;
    esac
    printf '{"schema": 1, "files": [{"source": "ok.php", "annotations": [{"id": "yac_01JB8M3Z4XAAAAAAAAAAAAAAAA", "source": "ok.php", "line": 3, "status": "%s", "scope": "Foo::bar", "before": null, "anchor": "foo();", "after": null, "comment": "%s", "matches": [3], "problems": [], "future": true}]}], "warnings": ["Sidecar .yac/x.php.yac is invalid"]}' "$status" "$input"
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
  *)
    printf 'args:%s\n' "$*"
    printf 'Warning: careful\n' >&2
    exit 1
    ;;
esac
