package com.atenea.unimarket;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:target/unimarket-test.db")
class UnimarketApplicationTests {

	@Test
	void contextLoads() {
	}

}
