import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;


import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import sk.matus.wrappers.FileWatcher;

public class TestFileWatcher {
	
	public interface ThrowingConsumer {
		
		public void accept(FileWatcher fw, Utils.MockMethods m) throws Exception;
		
	}

	private Path[] existentFiles;
	
	private Path[] watchedDirs;
	private Path[] deepDirs;
	private Path nonwatchedDir;
	
	@Before
	public void setUp() throws Exception {
		watchedDirs = new Path[] {
			Utils.p("watched%d/"),
			Utils.p("recursive%d/")
		};
		deepDirs = new Path[] {
			Utils.p(watchedDirs[1], "deep"),
			null
		};
		deepDirs[1] = Utils.p(deepDirs[0], "verydeep");
		
		nonwatchedDir = Utils.p("nonwatched%d/");
		
		Stream.concat(
			Stream.concat(
				Stream.of(watchedDirs), Stream.of(deepDirs)),
			Stream.of(nonwatchedDir)).forEach(p -> {
				try {
					Files.createDirectories(p);
				} catch (IOException e) {}
			});
		
		existentFiles = new Path[] {
			Utils.p(watchedDirs[0], "file1.txt"),
			Utils.p(watchedDirs[1], ".hidden")
		};
		
		Stream.of(existentFiles).forEach(p -> {
			try {
				Files.createFile(p);
			} catch (IOException e) {}
		});
	}

	@After
	public void tearDown() throws Exception {
		for (Path path : watchedDirs) {
			FileUtils.deleteDirectory(path.toFile());
		}
		FileUtils.deleteDirectory(nonwatchedDir.toFile());
	}
	
	private void testFileWatcher(ThrowingConsumer body) throws Exception {
		Utils.MockMethods m = new Utils.MockMethods();
		FileWatcher fw = FileWatcher.create(m.onCreate, m.onModify, m.onDelete, watchedDirs);
		fw.start();
		
		body.accept(fw, m);
		
		fw.clean();
	}
	
	@Test
	public void onCreate_shouldBeCalled_whenFileIsCreatedInWatchedDirectory() throws Exception {
		testFileWatcher((fw, m) -> {
			Path path = Utils.p(watchedDirs[0], "newfile.jpg");
			Files.createFile(path);
			Thread.sleep(5);
			
			verify(m.onCreate, Mockito.times(1)).accept(Utils.pathEq(path));
			fw.stop();
		});
	}
	
	@Test
	public void onCreate_shouldNotBeCalled_whenFileIsCreatedInSubdirectoryOfWatchedDirectory() throws Exception {
		testFileWatcher((fw, m) -> {
			Path path = Utils.p(deepDirs[0], "newfile.jpg");
			Files.createFile(path);
			Thread.sleep(5);
			
			verify(m.onCreate, Mockito.never()).accept(Mockito.any(Path.class));
			fw.stop();
		});
	}
	
	@Test
	public void onCreate_shouldNotBeCalled_whenFileIsCreatedOutsideWatchedDirectory() throws Exception {
		testFileWatcher((fw, m) -> {
			Path path = Utils.p(nonwatchedDir, "newfile.jpg");
			Files.createFile(path);
			Thread.sleep(5);
			
			verify(m.onCreate, Mockito.never()).accept(Mockito.any(Path.class));
			fw.stop();
		});
	}
	
	@Test
	public void onCreate_shouldNotBeCalled_whenNothingIsCreated() throws Exception {
		testFileWatcher((fw, m) -> {
			Thread.sleep(5);
			
			verify(m.onCreate, Mockito.never()).accept(Mockito.any(Path.class));
			fw.stop();
		});
	}
	
	@Test
	public void onCreate_shouldNotBeCalled_whenFileIsCreatedInWatchedDirectoryAfterStopCall() throws Exception {
		testFileWatcher((fw, m) -> {
			fw.stop();
			Thread.sleep(5);
			Path path = Utils.p(watchedDirs[0], "newfile.jpg");
			Files.createFile(path);
			Thread.sleep(5);
			
			verify(m.onCreate, Mockito.never()).accept(Mockito.any(Path.class));
		});
	}
	
	@Test
	public void onModify_shouldBeCalled_whenFileIsModifiedInWatchedDirectory() throws Exception {
		testFileWatcher((fw, m) -> {
			Files.write(existentFiles[0], "aaakjfhdkjfhj".getBytes());
			Thread.sleep(5);
			
			verify(m.onModify, Mockito.atLeastOnce()).accept(Utils.pathEq(existentFiles[0]));
			fw.stop();
		});
	}
	
	@Test
	public void onModify_shouldNotBeCalled_whenNothingIsModified() throws Exception {
		testFileWatcher((fw, m) -> {
			Thread.sleep(5);
			
			verify(m.onModify, Mockito.never()).accept(Mockito.any(Path.class));
			fw.stop();
		});
	}
	
	@Test
	public void onModify_shouldNotBeCalled_whenFileIsModifiedInWatchedDirectoryAfterStopCall() throws Exception {
		testFileWatcher((fw, m) -> {
			fw.stop();
			Thread.sleep(5);
			Files.write(existentFiles[0], "aaakjfhdkjfhj".getBytes());
			Thread.sleep(5);
			
			verify(m.onModify, Mockito.never()).accept(Mockito.any(Path.class));
		});
	}
	
	@Test
	public void onDelete_shouldBeCalled_whenFileIsDeletedInWatchedDirectory() throws Exception {
		testFileWatcher((fw, m) -> {
			Files.deleteIfExists(existentFiles[1]);
			Thread.sleep(5);
			
			verify(m.onDelete, Mockito.times(1)).accept(Utils.pathEq(existentFiles[1]));
			fw.stop();
		});
	}
	
	@Test
	public void onDelete_shouldNotBeCalled_whenNothingIsDeleted() throws Exception {
		testFileWatcher((fw, m) -> {
			Thread.sleep(5);
			
			verify(m.onDelete, Mockito.never()).accept(Mockito.any(Path.class));
			fw.stop();
		});
	}
	
	@Test
	public void onDelete_shouldNotBeCalled_whenFileIsDeletedInWatchedDirectoryAfterStopCall() throws Exception {
		testFileWatcher((fw, m) -> {
			fw.stop();
			Thread.sleep(5);
			Files.deleteIfExists(existentFiles[1]);
			Thread.sleep(5);
			
			verify(m.onDelete, Mockito.never()).accept(Mockito.any(Path.class));
		});
	}
	
	@Test
	public void start_calledSecondTime_shouldBeIgnored() throws Exception {
		testFileWatcher((fw, m) -> {
			fw.start();
			Path path = Utils.p(watchedDirs[0], "newfile.jpg");
			Files.createFile(path);
			Thread.sleep(5);
			
			verify(m.onCreate, Mockito.times(1)).accept(Utils.pathEq(path));
			fw.stop();
		});
	}
	
	@Test(expected = Exception.class)
	public void start_calledAfterStop_shouldThrow() throws Exception {
		testFileWatcher((fw, m) -> {
			fw.stop();
			fw.start();
		});
	}
	
	@Test
	public void directoryDeletion_shouldNotCauseStoppingOfWatcher() throws Exception {
		testFileWatcher((fw, m) -> {
			FileUtils.deleteDirectory(watchedDirs[0].toFile());
			
			Path path = Utils.p(watchedDirs[1], "innondeleteddir.mov");
			Files.createFile(path);
			Thread.sleep(5);
			
			verify(m.onCreate, Mockito.times(1)).accept(Utils.pathEq(path));
			fw.stop();
		});
	}
	
}
