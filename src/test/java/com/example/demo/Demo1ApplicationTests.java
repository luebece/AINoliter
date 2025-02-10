package com.example.demo;

import com.example.demo.service.ChatGeminiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class GeminiServiceTest {

	@Autowired
	private ChatGeminiService chatGeminiService;

//	@Test
//	void testGenerateContent() {
//		String prompt = "Hello";
//		String response = geminiService.generateContent(prompt);
//		System.out.println(response);
//	}
}