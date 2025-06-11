#!/bin/sh

echo '⏳ Waiting for Nexus UI on :8081 ...' &&
until [ "$(curl -s -o /dev/null -w "%{http_code}" http://nexus:8081/)" -eq 200 ]; do sleep 5; done;
echo 'Nexus UI is up'
# Create ci_admin and jenkins user
curl -v -u admin:$(cat /nexus-data/admin.password) -X POST http://nexus:8081/service/rest/v1/script --header 'Content-Type: application/json' -d @/scripts/create-user.json
curl -v -u admin:$(cat /nexus-data/admin.password) -X POST http://nexus:8081/service/rest/v1/script/create_user/run --header 'Content-Type: text/plain' -d "{\"adminPassword\": \"$NEXUS_ADMIN_PASSWORD\", \"jenkinsPassword\": \"$NEXUS_JENKINS_PASSWORD\"}"
# Accept EULA for unblocking upload to repo
RESPONSE=$(curl -s -u ci_admin:$NEXUS_ADMIN_PASSWORD -X GET http://nexus:8081/service/rest/v1/system/eula)
echo "$RESPONSE" > /tmp/eula_assepted.json
sed -i 's/\("accepted"[[:space:]]*:[[:space:]]*\)false/\1true/' /tmp/eula_assepted.json
curl -v -u ci_admin:${NEXUS_ADMIN_PASSWORD} -X POST http://nexus:8081/service/rest/v1/system/eula --header 'Content-Type: application/json' -d @/tmp/eula_assepted.json