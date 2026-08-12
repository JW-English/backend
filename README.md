# 정운영어 — 백엔드

학원 학생의 **숙제 · 단어시험 · 리스닝 · Q&A · 인강**을 하나로 묶는 학습 관리 서비스의 서버.

기획·기술 설계는 [`project.md`](./project.md) 를 본다. 이 문서는 저장소 사용법만 다룬다.

## 구성

| 디렉터리 | 내용 |
|---|---|
| [`purut-api/`](./purut-api) | Spring Boot 4.1 / Java 21 / PostgreSQL 16 / Redis |
| `.github/workflows/` | 빌드·테스트 CI |

클라이언트(Expo 앱 + 관리자 웹)는 **별도 저장소**다 — `jungwoon-client`.
두 저장소를 잇는 계약은 서버가 만드는 **OpenAPI 스펙**이고, 클라이언트가 이를 타입으로 변환해
쓴다. API 를 바꾸면 클라이언트에서 컴파일 에러로 드러난다.

## 로컬 실행

```bash
cd jungwoon-api
docker compose up -d          # Postgres :5433, Redis :6380
./gradlew :api:bootRun

curl localhost:8080/actuator/health     # {"status":"UP"}
open http://localhost:8080/swagger-ui.html
```

자세한 내용(모듈 구조, 스키마 변경 규칙, 테스트)은 [`purut-api/README.md`](./purut-api/README.md).

## P0 종료 조건 진행 상황

- [x] Spring 멀티모듈 + Docker Compose 로컬 환경
- [x] Flyway 마이그레이션이 DB에 적용된다 (기획안 4장 스키마 전체)
- [x] `GET /actuator/health` 가 응답한다
- [x] 앱에서 헬스체크를 호출해 화면에 찍는다 (클라이언트 저장소)
- [x] 푸시 → 자동 빌드·테스트 (GitHub Actions)
- [ ] **실제 도메인(HTTPS)에 자동 배포** — 서버·도메인 확보 후
