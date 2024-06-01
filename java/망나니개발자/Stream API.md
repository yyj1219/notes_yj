# Stream API 소개
Java 8부터 Stream API와 람다식, 함수형 인터페이스 등을 지원하며 Java를 이용해 함수형으로 프로그래밍할 수 있는 API들을 제공해주고 있습니다.

Stream API는 데이터를 추상화하고, 처리하는 데 자주 사용되는 함수들을 정의해 두었습니다.

`데이터를 추상화하였다` = 데이터의 종류에 상관 없이 같은 방식으로 데이터를 처리할 수 있다.

예) Stream API 사용 전 - 원본 데이터가 직접 변경됨
```java
List<String> nameList = Arrays.asList(nameArr);

Arrays.sort(nameArr);
Collections.sort(nameList);

for (String str: nameArr) { System.out.println(str); }
for (String str: nameList) { System.out.println(str); }
```

예) Stream API 사용 후 - 원본 데이터는 변형되지 않음
```java
String[] nameArr = {"IronMan", "Captain", "Hulk", "Thor"}
List<String> nameList = Arrays.asList(nameArr);

// 원본 데이터가 아닌 별도의 Stream을 생성함
Stream<String> nameStream = nameList.stream();
Stream<String> arrayStream = Arrays.asList(nameArr);

// 복사된 데이터를 정렬하여 출력
nameStream.sorted().forEach(System.out::println);
arrayStream.sorted().forEach(Stream.out::println);
```

# Stream API 특징

## 원본 데이터를 변경하지 않는다.

```java
List<String> sortedList = nameStream.sorted().collect(Collection.toList());
```

## 일회용이다.

Stream API는 일회용이기 때문에 한 번 사용이 끝나면 재사용이 불가능합니다.

```java
userStream().sorted.forEach(System.out::println);

// 스트림이 이미 사용되어 닫혔으므로 IllegalStateException 에러 발생
int count = userStream.count;
```

## 내부 반복으로 작업을 처리한다.

스트림은 for 나 while 등과 같은 문법을 메소드 내부에 숨기고 있기 때문에 보다 간결한 코드의 작성이 가능합니다.

```java
// 반복문이 forEach 메소드 내부에 숨겨져 있다.
nameStream.forEach(System.out::println);
```

# Stream 연산의 3 단계

* Stream 생성하기
* Stream 가공하기
* Stream 결과만들기

```java
List<String> myList = Arrays.asList("a1", "a2", "b1", "c2", "c1");

myList
    .stream()                       // 스트림 생성하기
    .filter(s -> s.startsWith("c")) // 가공하기 - 중간 연산은 Stream을 반환합니다.
    .map(String::toUpperCase)       // 가공하기 - 중간 연산은 Stream을 반환합니다.
    .sorted()                       // 가공하기 - 중간 연산은 Stream을 반환합니다.
    .count();                       // 결과 만들기
```

Stream 연산들은 매개변수로 **함수형 인터페이스 (FunctionalInterface)**를 받도록 되어 있습니다.

그리고 람다식은 반환값으로 **함수형 인터페이스**를 반환합니다

# 람다식

람다식은 함수를 하나의 식(expression)으로 표현한 것입니다. <br>
함수를 람다식으로 표현하면 **메소드의 이름이 필요없기** 때문에, 람다식은 **익명 함수(Anonymous Function)의 한 종류**라고 볼수 있습니다.

함수형 인터페이스의 인스턴스를 생성하여 함수를 변수처럼 선언하는 람다식에서는 메소드의 이름이 불필요하다가 여겨져서 이를 사용하지 않습니다. 대신 컴파일러가 문맥을 살펴 타입을 추론합니다.

## 람다식의 특징

* 람다식 내에서 사용되는 지역변수는 final이 붙지 않아도 상수로 간주됩니다.
* 람다식으로 선언된 변수명은 다른 변수명과 중복될 수 없습니다.

## 람다식의 장점

* 코드를 간결하게 만들 수 있습니다.
* 식에 개발자의 의도가 명확하게 드러나 가독성이 높아집니다.
* 함수를 만드는 과정없이 한번에 처리할 수 있어 생산성이 높아집니다.
* 병렬 프로그래밍이 용이합니다.

## 람다식의 단점

* 람다를 사용하면서 만든 무명함수는 재사용이 불가능합니다.
* 디버깅이 어렵습니다.
* 람다를 남발하면 비슷한 함수가 중복 생성되어 코드가 지저분해질 수 있습니다.
* 재귀로 만들 경우에 부적합합니다.

# 함수형 인터페이스(Functional Interface)

Java는 기본적으로 객체지향 언어이기 때문에 **순수 함수와 일반 함수를 다르게 취급**하고 있으며, Java에서는 이를 구분하기 위해 함수형 인터페이스가 등장하게 되었습니다.

함수형 인터페이스란 함수를 1급 객체처럼 다룰 수 있게 해주는 어노테이션으로, **인터페이스에 선언하여 단 하나의 추상 메소드만을 갖도록 제한**하는 역할을 합니다.

예 - 함수형 인터페이스 등장 전 = 익명 함수
```java
public class Lambda {
    public static void main(String[] args) {
        System.out.println(new MyLambdaFunction() {
            public int max(int a, int b) {
                return a > b ? a : b;
            }
        }.max(3,5));
    }
}
```

예 - 함수형 인터페이스 등장 후 = **함수를 변수처럼 선언**
```java
@FunctionalInterface
interface MyLambdaFunction {
    int max(int a, int b);
}

public class Lambda {
    public static void main(String[] args) {
        MyLambdaFunction lambdaFunction = (int a, int b) -> a > b ? a : b;
        System.out.println(lambdaFunction.max(3, 5));
    }
}
```

함수형 인터페이스를 구현하기 위해서는 인터페이스를 개발하여 그 내부에는 1개 뿐인 abstract 함수를 선언하고, 위에는 @FunctionalInterface 어노테이션을 붙여주면 됩니다.

## Java에서 제공하는 함수형 인터페이스

java에는 자주 사용될 것 같은 함수형 인터페이스가 이미 정의되어 있습니다.

### Supplier<T>

Supplier는 매개변수 없이 반환값만을 갖는 함수형 인터페이스입니다.<br>
`T get()` 을 추상 메소드로 갖고 있습니다.

```java
Supplier<String> supplier = () -> "Hello World!";
System.out.println(supplier.get());
```

### Consumer<T>

Consumer는 객체 T를 매개변수로 받아서 사용하며, 반환값은 없는 함수형 인터페이스입니다.
`void accept(T t)`를 추상 메소드로 갖고 있습니다.<br>
그리고 Consumer에는 andThen() 메소드도 있는데, 이를 통해 하나의 함수가 끝난 후에 다음 Consumer를 연쇄적으로 이용할 수 있습니다.

### Function<T, R>

Function은 객체 T를 매개변수로 받아서 처리한 후에 R로 반환하는 함수형 인터페이스입니다. <br>
`R apply(T t)`를 추상 메소드로 갖고 있습니다.<br>
Function은 Consumer와 마찬가지로 andThen을 제공하고 있으며, 추가로 compose 메소드를 제공하고 있습니다.

### Predicate<T>

Predicate는 객체 T를 매개변수로 받아서 처리한 후에 boolean을 반환합니다.
`boolean test(T t)`를 추상 메소드로 갖고 있습니다.

# 메소드 참조 (Method Reference)

메소드 참조란 함수형 인터페이스를 람다식이 아닌 일반 메소드를 참조시켜서 선언하는 방법입니다. <br>
참조 가능한 메소드는 일반 메소드, static 메소드, 생성자가 있으며 `클래스이름::메소드이름` 형식으로 참조할 수 있습니다. <br>
이렇게 참조를 하면 함수형 인터페이스로 반환이 됩니다.

예 - 일반 메소드 참조

```java
// 기존의 람다식
Function<String, Integer> function = (str) -> str.length();
function.apply("Hello World");

// 메소드 참조로 변경
Function<String, Integer> function = String::length;
function.apply("Hello World");
```

예 - 일반 메소드 참조

```java
// 일반 메소드를 참조하여 Consumer를 선언
Consumer<String> consumer = System.out::println;
consumer.accept("Hello World");

// 메소드 참조를 이용해서 Consumer를 매개변수로 받는 forEach를 쉽게 사용
List<String> list = Arrays.asList("red", "orange", "yellow", "green", "blue");
list.forEach(System.out::println);
```

예 - static 메소드 참조

```java
Predicate<Boolean> predicate = Objects::isNull;

public static boolean isNull(Object obj) {
    return obj == null;
}
```

예 - 생성자 참조

```java
Supplier<String> supplier = String::new;
```

# Stream 생성하기

타입에 따라 Stream을 생성하는 방법이 다릅니다.

## Collection의 Stream 생성

Collection 인터페이스에는 stream()이 정의되어 있기 때문에, Collection 인터페이스를 구현한 객체들(List, Set 등)은 모두 stream() 메소드를 이용해서 Stream을 생성할 수 있습니다.

```java
List<String> list = Arrays.asList("a", "b", "c");
Stream<String> listStream = list.stream();
```

## 배열의 Stream 생성

배열의 원소들을 소스로 하는 Stream을 생성하기 위해서는 Stream.of 메소드 혹은 Arrays.stream 메소드를 사용합니다.

```java
Stream<String> stream = Stream.of("a", "b", "c");
Stream<String> stream = Stream.of(new String[]{"a", "b", "c"});
Strema<String> stream = Arrays.stream(new String[]{"a", "b", "c"});
Stream<String> stream = Arrays.stream(new String[]{"a", "b", "c"}, 0, 3); // end 범위 포함
```

## 원시 Stream 생성

객체를 위한 Stream 외에도 int, long, double 같은 원시 자료형을 사용하기 위한 특수한 종류의 IntStream, LongStrean, DoubleStream 들도 사용할 수 있습니다. <br>
IntStream 같은 경우에는 range() 메소드를 사용해서 기존에 for 문을 대체할 수 있습니다.

```java
IntStream stream = IntStream.range(4, 10);
```

# Stream 가공하기(중간연산)

## filter

```java
Stream<String> stream = list.stream()
                            .filter(name -> name.contains("a"));
```

## map

Java에서는 map 메소드의 인자로 함수형 인터페이스 function을 받고 있습니다.

```java
Stream<String> stream = names.stream()
                            .map(s -> s.toUpperCase());
```

```java
Stream<File> fileStream = Stream.of(new File("test1.java"), new File("test2.java"), new File("test3.java"));
// Stream<File>을 Stream<String> 변환
Stream<String> fileNameStream = fileStream.map(File::getName);
```

## sorted

sorted 메소드에는 파라미터로 Comparator를 넘길 수도 있습니다. <br>
Comparator가 없으면 오름차순으로 정렬되며, Comparator.reverseOrder() 이용하면 내림차순으로 정렬할 수 있습니다.

```java
List<String> list = Arrays.asList("Java", "Scala", "Groovy", "Python", "Go", "Swift");

Stream<String> stream = list.stream().sorted();

Stream<String> reverseStream = list.stream()
                                    .sorted(Comparator.reverseOrder());
```

## 원시 Stream <-> Stream

일반적인 Stream 객체를 원시 Stream으로 바꾸거나 그 반대로 작업이 필요한 경우가 있습니다. <br>
일반적인 Stream 객체는 mapToInt(), mapToLong(), mapToDouble() 이라는 특수 매핑 연산을 지원하는 메소드가 있으며, 반대로 원시객체는 mapToObject()를 통해서 일반적인 Stream 객체로 바꿀 수 있습니다.

```java
// IntStream -> Stream<String>
IntStream.range(0, 4)
        .mapToObj(i -> "a"+i);

// Stream<Double> -> IntStream -> String<String>
Stream.of(1.0, 2.0, 3.0)
    .mapToInt(Double::intValue)
    .mapToObject(i -> "a"+i);
```

# Stream 결과 만들기 (최종 연산)

## max, min, sum, average, count

min, max, average는 Stream이 비어있으면 값을 특정할 수 없기 때문에 Optional로 값이 반환됩니다.

```java
OptionalInt min = IntStream.of(1, 3, 5, 7, 9).min();
int max = IntStream.of().max().ofElse(0);
IntStream.of(1, 3, 5, 7, 9).average().ifPresent(System.out::println);
```

sum, count는 Stream이 비어있어도 0으로 값을 특정할 수 있기 때문에 Optional이 아닌 원시값을 반환합니다.

## collect

Stream의 요소들을 list, set, map 등 다른 종류의 결과로 수집하고 싶은 경우에는 collect 함수를 이용할 수 있습니다. <br>
collect 메소드는 어떻게 Stream의 요소들을 수집할 것인가를 정의한 Collector 타입을 인자로 받아서 처리합니다. <br>
일반적으로 List를 Stream의 요소로 수집하는 경우가 많은데, 이렇듯 자주 사용하는 작업은 Collectors 객체에서 static 메소드로 제공합니다. 원하는 것이 없다면 Collector 인터페이스를 직접 구현해서 사용할 수도 있습니다.

```
collect(): 스트림의 최종 연산으로 매개변수로 Collector를 필요로 합니다.
Collector: 인터페이스, collect의 파라미터는 이 인터페이스를 구현해야 합니다.
Collectors: 클래스, static 메소드로 미리 작성된 콜렉터를 제공합니다.

// collect의 파라미터로는 Collector의 구현체가 와야 합니다.
Object collect(Collector collector)
```

### Collectors.toList()

```java
List<Product> productList = Arrays.asList(
    new Product(23, "potatoes"),
    new Product(14, "orange"),
    new Product(13, "lemon"),
    new Product(23, "bread"),
    new Product(13, "sugar")
);

List<String> nameList = productList.stream()
        .map(Product::getName)
        .collect(Collectors.toList());
```

### Collectors.joining()

Stream에서 작업한 결과를 1개의 String으로 이어붙이기를 원하는 경우에 Collectors.joining()을 이용할 수 있습니다.<br>
Collectors.joining()은 총 3개의 인자를 받을 수 있는데, 이를 활용하면 간단하게 String을 조합할 수 있습니다.

* delimiter: 각 요소 중간에 들어가 요소를 구분시켜주는 문자
* prefix: 결과 맨 앞에 붙는 문자
* suffix: 결과 맨 뒤에 붙는 문자

```java
List<Product> productList = Arrays.asList(
    new Product(23, "potatoes"),
    new Product(14, "orange"),
    new Product(13, "lemon"),
    new Product(23, "bread"),
    new Product(13, "sugar")
);

String listToString = productList.stream()
                            .map(Product::getName)
                            .collect(Collectors.joining());

String listToString = productList.stream()
                            .map(Product::getName)
                            .collect(Collectors.joining(" "));

// <potatoes, orange, lemon, bread, sugar>
String listToString = productList.stream()
                            .map(Product::getName)
                            .collect(Collectors.joining(", ", "<", ">"));
```

### Collectors.groupingBy()

Stream에서 작업한 결과를 특정 그룹으로 묶기를 원한다면 Collectors.groupingBy()를 이용할 수 있으며, 결과는 Map을 반환 받습니다. <br>
groupingBy는 매개변수로 함수형 인터페이스 Function을 필요로 합니다. <br>
예를 들어 수량을 기준으로 grouping을 원하는 경우에는 다음과 같이 작성할 수 있으며, 같은 수량일 경우에는 List로 묶어서 값을 반환받게 됩니다.

```java
List<Product> productList = Arrays.asList(
    new Product(23, "potatoes"),
    new Product(14, "orange"),
    new Product(13, "lemon"),
    new Product(23, "bread"),
    new Product(13, "sugar")
);

Map<Integer, List<Product>> collectorMapOfLists = 
                productList.stream()
                        .collect(Collectors.groupingBy(Product::getAmount));
```

### 조건검사 - match

Stream의 요소들이 특정한 조건을 충족하는지 검사하고 싶은 경우에는 match 함수를 이용합니다.<br>
match 함수는 함수형 인터페이스 Predicate를 받아서 해당 조건을 만족하는지 검사하고, 결과를 boolean으로 반환합니다.

```java
List<String> names = Arrays.asList("Eric", "Elena", "Java");

boolean anyMatch = names.stream()
                        .anyMatch(name -> name.contains("s"));
boolean allMatch = names.stream()
                        .allMatch(name -> name.length()> 3);
boolean noneMatch = names.stream()
                        .noneMatch(name -> name.endsWith("s"));
```

### 특정 연산 수행 - forEach

Stream의 요소들을 대상으로 어떤 특정한 연산을 수행하고 싶은 경우에는 forEach 함수를 이용할 수 있습니다. <br>
forEach는 최종 연산으로서 실제 요소들에 영향을 줄 수 있으며, 반환값이 존재하지 않습니다.

```java
names.stream()
    .forEach(System.out::println);
```
