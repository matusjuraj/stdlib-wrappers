import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import sk.matus.wrappers.FileWatcher;


@SuppressWarnings("unchecked")
public class TestFileWatcher {

	private List<Path> files;
	
	private List<Path> dirs;
	
	@Before
	public void setUp() throws Exception {
		dirs = new ArrayList<>();
		for (Path path : Arrays.asList(
			Paths.get("watched1/"),
			Paths.get("watched2/"),
			Paths.get("nonwatched/"))) {
			
			Files.createDirectories(path);
			dirs.add(path);
		}
		
		files = new ArrayList<>();
		for (Path path : Arrays.asList(
			Paths.get("watched1/file1.txt"),
			Paths.get("watched1/file2.txt"),
			Paths.get("watched2/file3.png"))) {
			
			if (Files.notExists(path)) {
				Files.createFile(path);
			}
			files.add(path);
		}
	}
	
	private static class PathMatcher extends ArgumentMatcher<Path> {

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
	
	@After
	public void tearDown() throws Exception {
		for (Path path : dirs) {
			FileUtils.deleteDirectory(path.toFile());
		}
	}
	
	@Test
	public void onCreate_shouldBeCalled_whenFileIsCreatedInWatchedDirectory() throws Exception {
		Consumer<Path> onCreate = Mockito.mock(Consumer.class);
		
		FileWatcher fw = FileWatcher.create(onCreate, p -> {}, p -> {}, dirs.get(0), dirs.get(1));
		fw.start();
		
		Path path = Paths.get("watched1/newfile.jpg");
		Files.createFile(path);
		Thread.sleep(40);
		
		verify(onCreate, Mockito.times(1)).accept(Mockito.argThat(new PathMatcher(path)));
		fw.stop();
	}
	
	@Test
	public void onCreate_shouldNotBeCalled_whenFileIsCreatedOutsideWatchedDirectory() throws Exception {
		Consumer<Path> onCreate = Mockito.mock(Consumer.class);
		
		FileWatcher fw = FileWatcher.create(onCreate, p -> {}, p -> {}, dirs.get(0), dirs.get(1));
		fw.start();
		
		Path path = Paths.get("nonwatched/newfile.jpg");
		Files.createFile(path);
		Thread.sleep(40);
		
		verify(onCreate, Mockito.never()).accept(Mockito.argThat(new PathMatcher(path)));
		fw.stop();
	}
	
	@Test
	public void onCreate_shouldNotBeCalled_whenNothingIsCreated() throws Exception {
		Consumer<Path> onCreate = Mockito.mock(Consumer.class);
		
		FileWatcher fw = FileWatcher.create(onCreate, p -> {}, p -> {}, dirs.get(0), dirs.get(1));
		fw.start();
		Thread.sleep(40);
		
		verify(onCreate, Mockito.never()).accept(Mockito.any(Path.class));
		fw.stop();
	}
	
	@Test
	public void onCreate_shouldNotBeCalled_whenFileIsCreatedInWatchedDirectoryAfterStopCall() throws Exception {
		Consumer<Path> onCreate = Mockito.mock(Consumer.class);
		
		FileWatcher fw = FileWatcher.create(onCreate, p -> {}, p -> {}, dirs.get(0), dirs.get(1));
		fw.start();
		fw.stop();
		
		Path path = Paths.get("nonwatched/newfile.jpg");
		Files.createFile(path);
		Thread.sleep(40);
		
		verify(onCreate, Mockito.never()).accept(Mockito.argThat(new PathMatcher(path)));
	}
	
	@Test
	public void onModify_shouldBeCalled_whenFileIsModifiedInWatchedDirectory() throws Exception {
		Consumer<Path> onModify = Mockito.mock(Consumer.class);
		
		FileWatcher fw = FileWatcher.create(p -> {}, onModify, p -> {}, dirs.get(0), dirs.get(1));
		fw.start();
		
		Path path = files.get(0);
		Files.write(path, "akjsakjd".getBytes());
		Thread.sleep(40);
		
		verify(onModify, Mockito.atLeastOnce()).accept(Mockito.argThat(new PathMatcher(path)));
		fw.stop();
	}
	
	@Test
	public void onModify_shouldNotBeCalled_whenNothingIsModified() throws Exception {
		Consumer<Path> onModify = Mockito.mock(Consumer.class);
		
		FileWatcher fw = FileWatcher.create(p -> {}, onModify, p -> {}, dirs.get(0), dirs.get(1));
		fw.start();
		Thread.sleep(40);
		
		verify(onModify, Mockito.never()).accept(Mockito.any(Path.class));
		fw.stop();
	}
	
	@Test
	public void onModify_shouldNotBeCalled_whenFileIsModifiedInWatchedDirectoryAfterStopCall() throws Exception {
		Consumer<Path> onModify = Mockito.mock(Consumer.class);
		
		FileWatcher fw = FileWatcher.create(p -> {}, onModify, p -> {}, dirs.get(0), dirs.get(1));
		fw.start();
		fw.stop();
		
		Path path = files.get(0);
		Files.write(path, "akjsakjd".getBytes());
		Thread.sleep(40);
		
		verify(onModify, Mockito.never()).accept(Mockito.argThat(new PathMatcher(path)));
	}
	
	@Test
	public void onDelete_shouldBeCalled_whenFileIsDeletedInWatchedDirectory() throws Exception {
		Consumer<Path> onDelete = Mockito.mock(Consumer.class);
		
		FileWatcher fw = FileWatcher.create(p -> {}, p -> {}, onDelete, dirs.get(0), dirs.get(1));
		fw.start();
		
		Path path = files.get(1);
		Files.delete(path);
		Thread.sleep(40);
		
		verify(onDelete, Mockito.times(1)).accept(Mockito.argThat(new PathMatcher(path)));
		fw.stop();
	}
	
	@Test
	public void onDelete_shouldNotBeCalled_whenNothingIsDeleted() throws Exception {
		Consumer<Path> onDelete = Mockito.mock(Consumer.class);
		
		FileWatcher fw = FileWatcher.create(p -> {}, p -> {}, onDelete, dirs.get(0), dirs.get(1));
		fw.start();
		Thread.sleep(40);
		
		verify(onDelete, Mockito.never()).accept(Mockito.any(Path.class));
		fw.stop();
	}
	
	@Test
	public void onDelete_shouldNotBeCalled_whenFileIsDeletedInWatchedDirectoryAfterStopCall() throws Exception {
		Consumer<Path> onDelete = Mockito.mock(Consumer.class);
		
		FileWatcher fw = FileWatcher.create(p -> {}, p -> {}, onDelete, dirs.get(0), dirs.get(1));
		fw.start();
		fw.stop();
		
		Path path = files.get(1);
		Files.delete(path);
		Thread.sleep(40);
		verify(onDelete, Mockito.never()).accept(Mockito.argThat(new PathMatcher(path)));
	}
	
}
