# Bash script

## 팁

$USER_NAME으로 지정된 사용자로 install.sh 스크립트를 실행하며, 스크립트 실행 중에 나타나는 모든 프롬프트에 자동으로 y를 입력합니다.

```bash
sudo -u $USER_NAME yes | ./install.sh
```
