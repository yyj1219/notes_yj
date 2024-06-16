# isintance 함수

특정 값이나 인스턴스이 특정 데이터 타입이나 클래스와 일치하는지 확인하는 함수입니다.

결과는 True 혹은 False 입니다.

```text
isinstance(확인하려는 값 혹은 인스턴스, 확인하려는 데이터 타입)
```

### 한 가지 타입만 확인하는 경우

```python
if isinstance(33, int):
    print("this is int")
```

### 여러 가지 타입을 확인하는 경우

다음과 같이 사용하면 여러 가지 타입 중에 하나라도 일치하면 True를 반환합니다.

```python
if isinstance(33, (int, float, str)):
    print("this is int")
```

### 상속 관계인 클래스를 확인하는 경우

상속을 받으면 자식 클래스로 생성한 객체는 부모 클래스로 확인하면 True가 됩니다.

```python
class Parent:
    pass

class Child(Parent):
    pass

p = Parent()
c = Child()

if isinstance(c, Parent):
    print("this is in Parent.")
```
