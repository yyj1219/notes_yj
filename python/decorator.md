# 데코레이터

파이썬 데코레이터는 함수나 메소드를 수정하거나 확장하기 위해 사용되는 강력한 기능입니다. 

데코레이터는 함수나 메소드의 정의 위에 `@` 기호와 데코레이터 함수를 붙여 사용합니다. 

이 때, 데코레이터 함수는 데코레이트하려는 함수를 매개변수로 받아 원하는 작업을 수행한 후, 수정된 함수를 반환합니다.



데코레이터의 활용은 주로 코드의 재사용성과 모듈성을 높이고, 코드의 가독성을 향상시키는 데 도움을 줍니다. 

예를 들어 로깅, 인증, 오류 처리 등과 같은 공통된 작업을 여러 함수에 적용하거나 코드의 특정 부분을 확장하는 데 데코레이터를 활용할 수 있습니다.

## 데코레이터 사용 예

간단한 데코레이터의 예를 살펴보겠습니다:

```python
def my_decorator(func):
    def wrapper():
        print("Something is happening before the function is called.")
        func()
        print("Something is happening after the function is called.")
    return wrapper

@my_decorator
def say_hello():
    print("Hello!")

say_hello()
```

위의 코드에서 `my_decorator` 함수는 데코레이터로 사용될 함수를 매개변수로 받습니다. 

이 함수 내부에서 새로운 함수인 `wrapper` 함수를 정의하고, 원래 함수를 호출하는 전후에 원하는 작업을 추가합니다. 

그리고 `wrapper` 함수를 반환합니다.

`@my_decorator` 데코레이터를 사용하여 `say_hello` 함수를 장식하면, 

`say_hello` 함수가 호출될 때 `wrapper` 함수가 먼저 실행되어 추가 작업이 수행된 후, 

원래 함수의 내용이 실행되고 다시 추가 작업이 수행됩니다.

실행 결과:
```
Something is happening before the function is called.
Hello!
Something is happening after the function is called.
```

이와 같이 데코레이터는 함수의 동작을 수정하거나 확장하여 중복 코드를 줄이고, 코드의 가독성을 높이는 데에 유용하게 활용됩니다. 

파이썬에서는 데코레이터를 조합하여 복잡한 기능을 구현하거나 웹 프레임워크에서 미들웨어를 구현하는 등 다양한 상황에서 사용됩니다.

## wraps

`wraps` 데코레이터는 파이썬에서 데코레이터를 작성할 때 사용되는 편리한 도우미 함수입니다. 

이를 사용하면 데코레이터를 만들 때 함수의 메타데이터(문서화 문자열, 이름, 매개변수 등)를 그대로 유지할 수 있습니다.

`wraps` 데코레이터는 `functools` 모듈에 포함되어 있습니다. 

### wraps 사용법

일반적으로 다음과 같이 사용됩니다:

```python
from functools import wraps

def my_decorator(func):
    @wraps(func)
    def wrapper(*args, **kwargs):
        # 데코레이터의 작업 수행
        result = func(*args, **kwargs)
        # 추가 작업 수행
        return result
    return wrapper

@my_decorator
def my_function():
    """이 함수는 어떤 작업을 수행합니다."""
    pass

print(my_function.__name__)       # 출력: my_function
print(my_function.__doc__)        # 출력: 이 함수는 어떤 작업을 수행합니다.
```

위의 예시에서 `@wraps(func)` 데코레이터를 사용하여 `wrapper` 함수가 `func` 함수의 메타데이터를 유지하도록 합니다. 

이렇게 하면 `my_function` 함수의 이름과 docstring이 제대로 유지되며, 이 정보는 디버깅이나 문서화에 사용될 수 있습니다.

일반적으로 데코레이터를 작성할 때 `wraps` 데코레이터를 함께 사용하는 것이 권장됩니다. 

이를 통해 데코레이터가 다루고 있는 함수의 메타데이터를 유지하면서도 데코레이터가 원하는 작업을 수행할 수 있습니다.

### wraps를 사용하지 않을 때 이슈

```python
import logging as logger

def trace_decorator(function):

    def wrapped(*args, **kwargs):
        """decorator docstring"""
        logger.info("%s 실행", function.__qualname__)
        return function(*args, **kwargs)
    
    return wrapped

# 로그 추적을 위한 데코레이터 trace_decorator를 달아줍니다.
@trace_decorator
def process_account(account_id):
    """id별 계정 처리 로그 보기"""
    logger.info("%s 계정 처리", account_id)

if __name__ == "__main__":
    print(process_account.__qualname__)
```

위 코드를 실행하면 process_account 함수가 데코레이터로 인해 wrapped 함수로 변경되었기 때문에 process_account 함수가 아니라 wrapped 함수가 출력됩니다.

```
>> trace_decorator.<locals>.wrapped
```

help로 docstring을 불러와도 데코레이터의 docstirng을 가져옵니다.
```
print(help(process_account))
>> decorator docstring
```

이렇게 되면 원본 함수를 확인하려고 해도 실제 실행된 함수를 알 수 없으므로 디버깅이 더 어려워집니다.

이 때에 @wraps 데코레이터를 사용하면 함수의 이름과 docstring이 유지됩니다.

```python
import logging as logger
from functools import wraps

def trace_decorator(function):

    @wraps(function)
    def wrapped(*args, **kwargs):
        """decorator docstring"""
        logger.info("%s 실행", function.__qualname__)
        return function(*args, **kwargs)
    
    return wrapped

# 로그 추적을 위한 데코레이터 trace_decorator를 달아줍니다.
@trace_decorator
def process_account(account_id):
    """id별 계정 처리 로그 보기"""
    logger.info("%s 계정 처리", account_id)

if __name__ == "__main__":
    print(process_account.__qualname__)
```

```
>> process_account

print(help(process_account))
>> id별 계정 처리 로그 보기
```
