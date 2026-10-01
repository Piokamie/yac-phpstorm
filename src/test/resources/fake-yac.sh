if [ "deaf.php" = "$2" ]; then
  sleep 30
  exit 0
fi
preview() {
  printf -- '--- a/a.php\n+++ b/a.php\n@@ -1,2 +1,3 @@\n <?php\n+/** promoted */\n foo();\n'
}
wait_for_release() {
  i=0
  while [ ! -e release ] && [ "$i" -lt 300 ]; do
    sleep 0.1
    i=$((i + 1))
  done
}
write_promoted() {
  mkdir -p .yac
  printf 'after-sidecar\n' > .yac/a.php.yac
  printf '/.yac/\n' > .gitignore
}
case "$1 $2" in
  "promote yac_undo"|"promote yac_dryfail"|"promote yac_unlisted"|"promote yac_stray")
    if [ "--dry-run" = "$3" ]; then
      case "$2" in
        yac_dryfail) printf 'Error: preview failed\n' >&2; exit 2 ;;
        yac_unlisted) printf 'Nothing to do.\n'; exit 0 ;;
      esac
      preview
      exit 0
    fi
    printf '<?php\n/** promoted */\nfoo();\n' > a.php
    write_promoted
    if [ "yac_stray" = "$2" ]; then
      printf 'stray\n' > .yac/stray.php.yac
    fi
    printf 'Promoted %s.\n' "$2"
    exit 0
    ;;
  "promote yac_insert")
    if [ "--dry-run" = "$3" ]; then
      preview
      exit 0
    fi
    { head -n 1 a.php; printf '/** promoted */\n'; tail -n +2 a.php; } > a.php.new
    mv a.php.new a.php
    write_promoted
    printf 'Promoted yac_insert.\n'
    exit 0
    ;;
  "promote yac_many"|"promote yac_200")
    if [ "--dry-run" = "$3" ]; then
      limit=201
      if [ "yac_200" = "$2" ]; then
        limit=200
      fi
      i=1
      while [ "$i" -le "$limit" ]; do
        printf -- '--- a/f%d.php\n+++ b/f%d.php\n' "$i" "$i"
        i=$((i + 1))
      done
      exit 0
    fi
    printf '<?php\n/** promoted */\nfoo();\n' > a.php
    write_promoted
    printf 'Promoted %s.\n' "$2"
    exit 0
    ;;
  "promote yac_slowdry")
    touch dry-started
    sleep 30
    exit 0
    ;;
  "promote yac_slowrun")
    if [ "--dry-run" = "$3" ]; then
      preview
      exit 0
    fi
    touch run-started
    sleep 30
    exit 0
    ;;
  "promote yac_gated"|"promote yac_gateddry")
    if [ "--dry-run" = "$3" ]; then
      if [ "yac_gateddry" = "$2" ]; then
        printf 'Error: preview failed\n' >&2
        exit 2
      fi
      preview
      exit 0
    fi
    printf '<?php\n/** promoted */\nfoo();\n' > a.php
    write_promoted
    touch run-started
    wait_for_release
    printf 'Promoted %s.\n' "$2"
    exit 0
    ;;
  "remove yac_removed")
    printf 'after-sidecar\n' > .yac/a.php.yac
    printf 'Removed yac_removed.\n'
    exit 0
    ;;
  "extract a.php"|"extract yac_partial")
    if [ "--dry-run" = "$3" ]; then
      preview
      if [ "yac_partial" = "$2" ]; then
        exit 1
      fi
      exit 0
    fi
    mkdir -p .yac/.cache
    : > .yac/.lock
    if [ ! -e .yac/.gitignore ]; then
      printf '.lock\n/.cache/\n' > .yac/.gitignore
    fi
    printf '<?php\nfoo();\n' > a.php
    write_promoted
    if [ "yac_partial" = "$2" ]; then
      printf 'Warning: a.php:9: not extracted (skipped).\n' >&2
      printf 'Extracted 1 note.\n'
      exit 1
    fi
    printf 'Extracted 1 note.\n'
    exit 0
    ;;
  "inject a.php")
    if [ "--dry-run" = "$3" ]; then
      printf -- '--- a/a.php\n+++ b/a.php\n@@ -1,2 +1,3 @@\n <?php\n+/** @yac note */\n foo();\n'
      exit 0
    fi
    printf '<?php\n/** @yac note */\nfoo();\n' > a.php
    rm -f .yac/a.php.yac
    printf 'Injected 1 note into 1 file.\n'
    exit 0
    ;;
  "yeet a.php")
    rm -f .yac/a.php.yac
    printf 'Deleted 1 sidecar.\n'
    exit 0
    ;;
esac
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
  a.php)
    printf '{"schema": 1, "files": [], "warnings": []}'
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
    sleep 30
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
