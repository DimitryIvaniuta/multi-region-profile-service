#!/usr/bin/env bash
set -euo pipefail
find src/main/java -name '*.java' -print | sort >/tmp/profile-java-files.txt
test -s /tmp/profile-java-files.txt
test -f src/main/resources/db/migration/V1__create_profile_write_model.sql
test -f src/main/resources/db/migration/V2__create_profile_read_model.sql
test -f src/main/resources/db/migration/V3__harden_outbox_and_projection_ops.sql
test -f postman/multi-region-profile-service.postman_collection.json
python -m json.tool postman/multi-region-profile-service.postman_collection.json >/dev/null
grep -R "READ_MODEL_BEHIND" -n src/main/java >/dev/null
grep -R "profile_projection_watermark" -n src/main/resources/db/migration src/main/java >/dev/null
grep -R "FOR UPDATE SKIP LOCKED" -n src/main/java >/dev/null
printf 'Static project checks passed.\n'
