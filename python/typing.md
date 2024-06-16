# 파이썬 Typing

파이썬 typing은 **파이썬 3.5**부터 도입된 모듈로, 타입 힌트를 지원하는 기능입니다. 

타입 힌트는 변수나 함수의 매개변수, 반환값 등에 **어떤 타입의 값이 사용되어야 하는지를 명시**하는 것을 말합니다.

typing 모듈은 다양한 타입 힌트를 제공하며, 이를 사용하여 코드의 가독성을 높이고, 타입 관련 오류를 사전에 방지할 수 있습니다. 

예를 들어, 함수의 매개변수에 타입 힌트를 추가하면 해당 매개변수에 잘못된 타입의 값이 전달될 경우 경고를 표시해줍니다.

## typing 모듈에서 제공하는 주요 타입

### typing.Union

하나의 파라미터에 여러 타입이 사용될 수 있을 때 사용합니다.

아래 코드는 msg 파라미터가 str, bytes, None 중에 한 가지를 받을 수 있습니다.

```python
from typing import Union

def process_message(msg: Union[str, bytes, None]) -> str:
    ...
```

```python
# 파이썬 3.10 이상은 아래와 같이 표기 가능
from typing import Union

def process_message(msg: str | bytes | None) -> str:
    ...
```

### typing.Optional

하나의 파라미터에 한 가지 타입과 None이 사용될 수 있을 때 사용합니다.

아래 코드는 food 파라미터가 str 타입, None 값 중에 한 가지를 받을 수 있습니다.

```python
from typing import Optional

def eat_food(food: Optional[str]) -> None:
    ...
```

```python
# 파이썬 3.10 이상은 아래와 같이 표기 가능
from typing import Optional

def eat_food(food: str | None) -> None:
    ...
```

### typing.List, typing.Tuple, typing.Dict

- List: 리스트 타입을 표현합니다. 예를 들어, List[int]는 정수형 값들로 이루어진 리스트를 의미합니다.
- Dict: 딕셔너리 타입을 표현합니다. 예를 들어, Dict[str, int]는 문자열 키와 정수형 값으로 이루어진 딕셔너리를 의미합니다.
- Tuple: 튜플 타입을 표현합니다. 예를 들어, Tuple[int, str]는 정수형과 문자열로 이루어진 튜플을 의미합니다.

```python
from typing import List, Tuple, Dict

names: List[str]
location: Tuple[int, int, int]
count_map: Dict[str, int]
```

```python
# 파이썬 3.9 이상은 typing 모듈을 임포트하지 않고도 가능합니다.

names: list[str]
location: tuple[int, int, int]
count_map: dict[str, int]
```

### typing.TypedDict

딕셔너리는 밸류의 타입이 한 가지로 고정되는 일만 있는 것은 아닙니다. 

그런 상황을 지원하기 위해 파이썬 3.8부터는 TypedDict를 사용할 수 있습니다.

TypedDict를 상속받은 클래스를 만든 다음에 아래와 같이 키와 밸류 타입을 매칭시켜주면 됩니다.

```python
from typing import TypedDict

class Person(TypedDict):
    name: str
    age: int
    gender: str

def calc_cost(person: Person) -> float:
    ...
```

또는 TypedDict를 상속하지 않고 아래와 같이 사용할 수 있습니다.

```python
from typing import TypedDict
Person = TypedDict("Person", name=str, age=str, gender=str)
Person = TypedDict("Person", "name": str, "age": str, "gender": str)
```

많은 경우에 TypedDict는 파이썬 3.7부터 지원되는 **dataclass**로 대체해서 사용할 수 있습니다.
```python
from dataclass import dataclass

@dataclass
class Person:
    name: str
    age: int
    gender: str

def calc_cost(person: Person) -> float:
    ...
```

### typing.Any

어떤 타입든 관계없다면 **Any**를 사용하면 됩니다.

## Typing 사용 주의점

타입 힌트는 코드의 가독성을 높이고, 타입 관련 오류를 사전에 방지하는데 도움을 줍니다. 

하지만 파이썬은 동적 타입 언어이기 때문에 타입 힌트를 엄격하게 검사하지는 않습니다.

따라서 타입 힌트는 주석이나 문서화의 역할을 하며, 실제 실행 시에는 무시될 수 있습니다.
