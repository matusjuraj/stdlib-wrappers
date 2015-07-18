import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.function.Consumer;

import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;


@SuppressWarnings("unchecked")
public class Utils {
	
	static class PathMatcher extends ArgumentMatcher<Path> {

		private final Path path;
		
		public PathMatcher(Path path) {
			this.path = path;
		}
		
		@Override
		public boolean matches(Object argument) {
			if (argument instanceof Path) {
				return path.toAbsolutePath().normalize().equals(
					((Path) argument).toAbsolutePath().normalize());
			}
			return false;
		}
	}
	
	public static Path pathEq(Path path) {
		return Mockito.argThat(new PathMatcher(path));
	}
	
	static class MockMethods {
		
		final Consumer<Path> onCreate;
		
		final Consumer<Path> onModify;
		
		final Consumer<Path> onDelete;
		
		public MockMethods() {
			onCreate = Mockito.mock(Consumer.class);
			onModify = Mockito.mock(Consumer.class);
			onDelete = Mockito.mock(Consumer.class);
		}
		
	}
	
	private final static Random rand = new Random();
	
	private static Path _p(String tpl) {
		return Paths.get(String.format(tpl, rand.nextInt()));
	}
	
	public static Path p(String tpl) {
		return Paths.get("src/test/resources").resolve(_p(tpl));
	}
	
	public static Path p(Path in, String tpl) {
		return in.resolve(_p(tpl));
	}

}
