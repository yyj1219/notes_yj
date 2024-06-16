# 다중상속된 클래스에서 super()의 사용

super(클래스이름,클래스의 인스턴스).상위클래스의메소드(파라미터) vs super().상위클래스의메소드(파라미터)

super(클래스이름,클래스의 인스턴스)는 명시적으로 클래스의 바로 위 상위 클래스에서 메소드를 호출합니다.
super()는 상위 클래스 중에서 메소드를 찾아서 호출합니다.

다중 상속받은 경우에는 super(클래스이름,클래스의 인스턴스)와 같이 
명시적으로 어떤 클래스의 상위 클래스에서 메소드를 호출할지 정확하게 지정하는 게 좋습니다.

```python
class Parent:
    def print_name(self, name):
        print(name)

class Child(Parent):
    def print_name(self, name):
        super(Child, self).print_name(name)
```

```python
class Parent:
    def print_name(self, name):
        print(name)

class Child(Parent):
    def print_name(self, name):
        super().print_name(name)
```