이 내용은 Elasticsearch 8.x 이상, OpenSearch 2.12 이상을 기준으로 합니다.
실제 테스트는 OpenSearch 2.13 에서만 한 것으로, 버전에 따라 글의 내용이 실제 환경에 맞지 않을 수 있습니다.

# 보안 기능

Elasticsearch/OpenSearch의 보안 기능은 사용자 인증, 권한 부여, 통신 암호화, 감사 로깅, 인덱스 및 필드 레벨 보안을 포함하여 데이터를 안전하게 보호하는 다양한 방법을 제공합니다.

# 최소 보안 설정

Elasticsearch 8.0 이상, OpenSearch 2.12 이상에서는 처음 시작할 때 보안 기능이 자동으로 활성화됩니다.

## 보안기능 활성화

기존 클러스터에서 보안 기능을 활성화하려면 Elasticsearch에서는 `elasticsearch.yml`에 `xpack.security.enabled: true`로 설정합니다.

```
xpack.security.enabled: true
```

기존 클러스터에서 보안 기능을 활성화하려면 OpenSearch에서는 `opensearch.yml`에 `plugins.security.disabled: false`로 설정합니다.
```
plugins.security.disabled: false
```

단일 노드 클러스터의 경우, Elasticsearch와 OpenSearch 모두 `discovery.type: single-node`를 설정합니다.
```
discovery.type: single-node
```

## 내장 사용자에 암호 설정

Elasticsearch에서는 `elasticsearch-setup-passwords` 명령어를 사용해서 기본 사용자 계정의 비밀번호를 설정하거나 새로운 사용자를 추가할 수 있습니다.

