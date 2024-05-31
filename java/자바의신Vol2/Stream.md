자바의 스트림은 "뭔가 연속된 정보"를 처리하는 데 사용합니다. <br>
가장 기본적인 배열이나 컬렉션에 스트림을 사용할 수 있습니다. (배열은 안 됩니다.) <br>

# 스트림의 구조

```java
list
    .stream() // 스트림 생성, 컬랙센 목록을 java.util.stream.Stream 인터페이스로 변환
    .filter(x -> x>10) // 중간 연산 (intermediate operation), 필수는 아님
    .count() // 최종 연산 (terminal operation), 중간 연산에서 작업된 내용을 바탕으로 결과 반환
```

# 스트림 중간 연산 (intermidiate operation) 종류

스트림 중간 연산은 다른 Stream을 반환하며, 지연 연산됩니다. <br>
"지연 연산"은 최종 연산이 호출되기 전까지는 중간 연산이 실제로는 수행되지 않는 걸 의미합니다.

* `filter(Predicate<T> predicate)` : 조건에 맞는 요소를 필터링
* `map(Function<T, R> mapper)` : 요소를 반환
* `flatMap(Function<T, Stream<R>> mapper)` : 요소를 평면화
* `distinct()` : 중복 제거
* `sorted()` : 정렬
* `limit(long maxSize)` : 스트림 크기 제한
* `skip(long n)` : 처음 n개 요소를 건너뜀

# 스트림 최종 연산 (terminal operation) 종류

스트립 최종 연산은 Stream을 소비하고 결과를 반환합니다.

* `forEach(Consumer<T> action)` : 각 요소에 대해 작업 수행
* `collect(Conllector<T, A, R> collector)` : 결과를 컬렉션으로 수집
* `reduce(BinaryOperator<T> accumulator)` : 요소를 결합하여 결과 생성
* `count()` : 요소의 개수 반환
* `anyMatch(Predicate<T> predicate)` : 조건에 맞는 요소가 있는지 확인
* `allMatch(Predicate<T> predicate)` : 모든 요소가 조건을 만족하는지 확인
* `noneMatch(Predicate<T> predicate)` : 모든 요소가 조건을 만족하지 않는지 확인
* `findFirst()` : 첫 번쨰 요소를 반환
* `findAny()` : 아무 요소나 반환
