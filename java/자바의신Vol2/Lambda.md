람다식은 익명 함수를 나타내는 방식으로, 함수형 프로그래밍을 지원하고 코드의 간결성을 높이기 위해 Java 8에서 도입되었습니다. <br>
람다식은 주로 함수형 인터페이스를 구현하는 데 사용됩니다. <br>
함수형 인터페이스는 **하나의 추상 메서드만 가지는 인터페이스**를 의미합니다. <br>
함수형 인터페이스는 **Functional 인터페이스** 라고도 합니다. <br>
Functional 인터페이스를 람다식에서 사용할 때 발생할 수 있는 혼란을 피하기 위해서 **@FunctionalInterface** 어노테이션을 사용합니다.

# 람다 식의 기본 문법

```java
(매개변수목록) -> 처리식
```
혹은
```java
(parameters) -> { statements; }
```

# FunctionalInterface 예

```java
@FunctionalInterface
interface Calculate {
  int operation(int a, int b);
}
```

# 람다 식의 예

## Runnable 인터페이스 구현

기존의 익명 클래스 사용 방식
```java
Runnable r = new Runnable() {
  @Override
  public void run() {
    System.out.println("Hello, World!");
  }
};
new Thread(r).start();
```

람다 식 사용 방식
```java
Runnable r = () -> System.out.println("Hello, World!");
new Thread(r).start();
```

## Comparator 인터페이스 구현

기존의 익명 클래스 사용 방식
```java
Comparator<String> comparator = new Comparator<String>() {
  @Override
  public int compare(String s1, String s2) {
    return s1.compareTo(s2);
  }
};
```

람다 식 사용 방식
```java
Comparator<String> comparator = (s1, s2) -> s1.compareTo(s2);
```

## List의 forEach 메서드 사용

기존의 익명 클래스 사용 방식
```java
List<String> list = Arrays.asList("a", "b", "c");
for (String s: list) {
  System.out.println(s);
}
```

람다 식 사용 방식
```java
List<String> list = Arrays.asList("a", "b", "c");
list.forEach(s -> System.out.println(s));
```

## Stream API와 함께 사용

Stream API는 람다 식과 함께 사용되며, 데이터를 처리하는 데 매우 유용합니다.

```java
List<String> list = Arrays.asList("a", "b", "c");
list.stream()
  .filter(s -> s.startsWith("a")) // "a"로 시작하는 요소만 필터링
  .forEach(s -> System.out.println(s)); // 결과 출력
```
