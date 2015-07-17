package sk.matus.wrappers;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.javatuples.Pair;

import static java.nio.file.StandardWatchEventKinds.*;

public class FileWatcher {
	
	private final List<Pair<Path, WatchService>> services;
	
	private final Consumer<Path> onCreate;
	
	private final Consumer<Path> onModify;
	
	private final Consumer<Path> onDelete;
	
	private boolean running = false;
	
	private FileWatcher(Consumer<Path> onCreate, Consumer<Path> onModify,
		Consumer<Path> onDelete, Path... paths) throws IOException {
		
		this.services = new LinkedList<>();
		
		this.onCreate = onCreate;
		this.onModify = onModify;
		this.onDelete = onDelete;
		
		registerPaths(paths);
	}
	
	public static FileWatcher create(Consumer<Path> onCreate, Consumer<Path> onModify,
		Consumer<Path> onDelete, Path... paths) throws IOException {
		
		return new FileWatcher(onCreate, onModify, onDelete, paths);
		
	}
	
	private void registerPaths(Path... paths) throws IOException {
		for (Path path : paths) {
			WatchService ws = FileSystems.getDefault().newWatchService();
			path.register(ws, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
			services.add(new Pair<>(path, ws));
		}
	}
	
	public class WatchServiceLoop implements Runnable {
		
		Iterator<Pair<Path, WatchService>> iterator;
		
		private Pair<Path, WatchService> next() {
			
			if (iterator == null || !iterator.hasNext()) {
				iterator = FileWatcher.this.services.iterator();
				if (!iterator.hasNext()) {
					throw new IllegalStateException("No paths are registered to watch");
				}
			}
			
			return iterator.next();
			
		}
		
		@Override
		public void run() {
			while (true) {
				Pair<Path, WatchService> pathAndWs = next();
				
				WatchKey key;				
				try {
					key = pathAndWs.getValue1().poll();
				} catch (ClosedWatchServiceException e) {
					key = null;
					iterator.remove();
				}
				
				if (key != null) {
					for (WatchEvent<?> event : key.pollEvents()) {
						Kind<?> kind = event.kind();
						
						@SuppressWarnings("unchecked")
						Path path = pathAndWs.getValue0().toAbsolutePath().resolve(
							((WatchEvent<Path>) event).context());
						
						if (kind == ENTRY_CREATE) {
							FileWatcher.this.onCreate.accept(path);
						} else if (kind == ENTRY_MODIFY) {
							FileWatcher.this.onModify.accept(path);
						} else if (kind == ENTRY_DELETE) {
							FileWatcher.this.onDelete.accept(path);
						} else if (kind == OVERFLOW) {
							continue;
						}
						
					}
					
					if (!key.reset()) {
						FileWatcher.this.stop();
					}
				}
				
				synchronized (FileWatcher.this) {
					if (!FileWatcher.this.running) {
						break;
					}
				}
			}
		}
		
	}
	
	public void start(ExecutorService executorService) {
		synchronized (this) {
			this.running = true;
		}
		executorService.execute(new WatchServiceLoop());
	}
	
	public void start() {
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		start(executorService);
		executorService.shutdown();
	}
	
	public void stop() {
		synchronized (this) {
			this.running = false;
		}
		for (Pair<Path, WatchService> service : this.services) {
			try {
				service.getValue1().close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
