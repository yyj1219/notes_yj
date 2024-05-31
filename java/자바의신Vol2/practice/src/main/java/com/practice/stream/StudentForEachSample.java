package com.practice.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class StudentForEachSample {

	public static void main(String[] args) {
		StudentForEachSample sample = new StudentForEachSample();
		List<StudentDTO> studentList = new ArrayList<StudentDTO>();
		
		studentList.add(new StudentDTO("요다", 43, 99, 10));
		studentList.add(new StudentDTO("만두", 30, 71, 85));
		studentList.add(new StudentDTO("건빵", 32, 81, 75));

		sample.printStudentNames(studentList);
		sample.printStudentNames2(studentList);
		
		System.out.println(getMergeNames(studentList));
		
		sample.filterWithScoreForLoop(studentList, 80);
	}
	
	// stream 최종 연산 forEach 테스트
	public void printStudentNames(List<StudentDTO> students) {
		students.stream().forEach(student -> System.out.println(student.getName()));		
	}
	
	// stream 중간 연산 map 테스트
	public void printStudentNames2(List<StudentDTO> students) {
		students.stream()
		.map(student -> student.getName())
		.forEach(name -> System.out.println(name));
	}
	
	// stream 최종 연산 collect 테스트
	public static List<String> getMergeNames(List<StudentDTO> students) {
		return students.stream()
				.map(student -> student.getName())
				.collect(Collectors.toList());
	}
	
	// stream 중간 연산 filter 테스트
	private void filterWithScoreForLoop(List<StudentDTO> studentList, int scoreCutLine) {
		/*
		for (StudentDTO student:studentList) {
			if (student.getScoreEnglish() > scoreCutLine) {
				System.out.println(student.getName());
			}
		}
		*/
		studentList.stream()
		.filter(student -> student.getScoreEnglish() > scoreCutLine)
		.forEach(filtredStudent -> System.out.println(filtredStudent.getName()));
	}
}
