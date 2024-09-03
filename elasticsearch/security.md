이 내용은 Elasticsearch 8.x 이상, OpenSearch 2.12 이상을 기준으로 합니다.
실제 테스트는 OpenSearch 2.13 에서만 한 것으로, 버전에 따라 글의 내용이 실제 환경에 맞지 않을 수 있습니다.

# 보안 기능

Elasticsearch/OpenSearch의 보안 기능은 사용자 인증, 권한 부여, 통신 암호화, 감사 로깅, 인덱스 및 필드 레벨 보안을 포함하여 데이터를 안전하게 보호하는 다양한 방법을 제공합니다.

# 보안 설정 방법

## 보안 활성화

Elasticsearch에서 보안을 활성화하려면 `elasticsearch.yml`에 `xpack.security.enabled: true`로 설정합니다.

```
xpack.security.enabled: true
```

OpenSearch에서 보안을 활성화하려면 `opensearch.yml`에 `plugins.security.disabled: false`로 설정합니다.
```
plugins.security.disabled: false
```

## 사용자 관리

기본 사용자는 built-in user, 시스템 유저 라고도 합니다.

Elasticsearch에서는 `elasticsearch-setup-passwords` 명령어를 사용해서 기본 사용자 계정의 비밀번호를 설정하거나 새로운 사용자를 추가할 수 있습니다.

