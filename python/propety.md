# @property 데코레이터

@property 데코레이터는 파이썬 클래스의 속성을 메소드로서 접근할 수 있게 해주는 기능입니다. 

@property 데코레이터를 사용하면 클래스의 속성을 직접적으로 접근하는 것이 아니라, 메소드를 통해 간접적으로 접근할 수 있습니다.

@property 데코레이터를 사용하면 해당 메소드는 속성처럼 사용될 수 있습니다. <br> 이 메소드를 호출하면, 해당 메소드의 반환값이 속성으로 사용됩니다. <br> 이렇게 함으로써, 속성에 대한 접근을 제어하고 유효성 검사 등의 추가 로직을 수행할 수 있습니다.

간단히 생각하면 getter와 setter를 쉽게 사용할 수 있게 하는 겁니다.

## 사용 예

```python
class Circle:
    def __init__(self, radius=0):
        self.radius = radius
    
    @property
    def radius(self):
        """Getter"""
        return self.radius
    
    @radius.setter
    def radius(self, value):
        """Setter"""
        self._radius = value
```

위의 코드에서 `Circle` 클래스는 `radius` 속성을 가지고 있습니다. <br> 그리고 `radius` 메소드 위에 @property 데코레이터가 붙어 있습니다. <br> 이렇게 하면 `radius` 메소드는 `Circle` 객체의 속성처럼 사용될 수 있습니다.

```python
circle = Circle(10)
print(circle.radius)  # 출력: 10
```

@property 데코레이터를 사용하면, 속성에 대한 접근을 제어하고 추가 로직을 수행할 수 있으므로, 코드의 가독성과 유지보수성을 향상시킬 수 있습니다.
