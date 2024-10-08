# --insecure 옵션

## 의미

주로 `curl` 같은 명령에서 사용되는 옵션으로, SSL/TLS 인증서를 검증하지 않고 통신을 허용하겠다는 의미입니다.

이 옵션을 사용하면 클라이언트는 서버의 인증서가 유효한지 확인하지 않습니다.

## 사용 이유

자체 서명된(self-signed) 인증서는 공인된 인증 기관(CA)에서 발급된 것이 아니기 때문에 브라우저나 클라이언트는 기본적으로 해당 인증서를 신뢰하지 않습니다.

따라서 인증서를 검증하는 과정에서 오류가 발생하고, 통신이 차단될 수 있습니다.

이럴 때 `--insecure` 옵션을 사용하면 인증서의 유효성을 확인하지 않고 통신을 계속할 수 있게 됩니다.

## 주로 사용하는 경우

- 개발 환경 테스트할 때
- DB처럼 외부 접근이 확실히 제안되고, 내부에서만 사용되는 시스템일 때

외부에서 접근할 수 있는 시스템이라면 공인된 인증서를 사용해야 합니다.
