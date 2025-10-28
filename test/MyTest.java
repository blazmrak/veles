import static org.junit.jupiter.api.condition.OS.LINUX;
import static org.junit.jupiter.api.condition.OS.WINDOWS;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;

import common.Os;

public class MyTest {

	@Test
	@EnabledOnOs(WINDOWS)
	public void test() {
		Assertions.assertTrue(Os.isWindows());
	}

	@Test
	@EnabledOnOs(LINUX)
	public void test2() {
		Assertions.assertTrue(Os.isLinux());
	}
}
