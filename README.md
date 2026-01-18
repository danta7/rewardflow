# RewardFlow (Step-by-step build)

This repo bootstraps the **multi-stage reward issuance module** described in the design doc.

## Step 1 - Bootstrap (scaffold + local dependencies)

### 1) Start dependencies

```bash
cd rewardflow
docker compose up -d
```

Services (default ports):
- MySQL: `localhost:3306` (db=`rewardflow`, user=`rewardflow`, pwd=`rewardflow123`)
- Redis: `localhost:6379`
- MongoDB: `localhost:27017` (db=`rewardflow`)
- RabbitMQ: `localhost:5672` (UI: `http://localhost:15672`)
- Nacos: `http://localhost:8848/nacos` (embedded mode)
- Jaeger UI: `http://localhost:16686`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000` (admin/admin by default)

### 2) Run the app

Recommended (most stable):

```bash
mvn -DskipTests clean install
mvn -f rewardflow-app/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
```

Alternative (fast path after the first successful build):

```bash
mvn -q -pl rewardflow-app -am spring-boot:run -Dspring-boot.run.profiles=local
```

### 3) Quick smoke tests

```bash
curl -s http://localhost:8080/actuator/health | jq
curl -s http://localhost:8080/api/v1/ping | jq

# Step2: report play duration (unique key: userId+soundId+syncTime)
curl -s -X POST http://localhost:8080/api/v1/play/report \
  -H 'Content-Type: application/json' \
  -d '{"userId":"u1","soundId":"s1","duration":30,"syncTime":'"$(date +%s000)"',"scene":"audio_play"}' | jq
```

### 4) Stop

```bash
docker compose down -v
```

## What we build next
- Step 2: Flyway schema + MyBatis repositories + basic `POST /api/v1/play/report` writes `play_duration_report`
- Step 3: Nacos rule center + rule caching + JEXL gray routing
- Step 4: stage calculation + reward_flow + outbox
- Step 5: MQ publish + retry scanner
- Step 6: tracing/metrics dashboards + JMeter pressure test
