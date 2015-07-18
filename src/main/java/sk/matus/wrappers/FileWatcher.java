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
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.javatuples.Pair;

import static java.nio.file.StandardWatchEventKinds.*;

public class FileWatcher {
	
	/**
	 * 
	 * Creates a FileWatcher for given paths reacting with given callbacks
	 * @param onCreate Callback to create event
	 * @param onModify Callback to modify event
	 * @param onDelete Callback to delete event
	 * @param paths Watched directories
	 * @return
	 * @throws IOException
	 */
	public static FileWatcher create(Consumer<Path> onCreate, Consumer<Path> onModify,
		Consumer<Path> onDelete, Path... paths) throws IOException {
		
		return new FileWatcher(onCreate, onModify, onDelete, paths);
		
	}
	
	private enum State {
		PREPARED,
		RUNNING,
		STOPPED
	}
	
	private final List<Pair<Path, WatchService>> services;
	
	private final Consumer<Path> onCreate;
	
	private final Consumer<Path> onModify;
	
	private final Consumer<Path> onDelete;
	
	private State state = State.PREPARED;
	
	private FileWatcher(Consumer<Path> onCreate, Consumer<Path> onModify,
		Consumer<Path> onDelete, Path... paths) {
		
		this.services = new Vector<>();
		
		this.onCreate = onCreate;
		this.onModify = onModify;
		this.onDelete = onDelete;
		
		registerPaths(paths);
	}
	
	/**
	 * Creates watch service for each path and registers it to them
	 * @param paths
	 */
	private void registerPaths(Path... paths) {
		for (Path path : paths) {
			WatchService ws = null;
			try {
				ws = FileSystems.getDefault().newWatchService();
				path.register(ws, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
				services.add(new Pair<>(path, ws));
			} catch (IOException e) {
				if (ws != null) {
					try {
						ws.close();
					} catch (IOException e1) {}
				}
			}
		}
	}
	
	/**
	 * Runnable that periodically polls all watch services of FileWatcher
	 * and invokes callbacks as necessary
	 *
	 */
	public class WatchServiceLoop implements Runnable {
		
		Iterator<Pair<Path, WatchService>> iterator;
		
		private Pair<Path, WatchService> next() {
			
			if (iterator == null || !iterator.hasNext()) {
				iterator = services.iterator();
				if (!iterator.hasNext()) {
					stop();
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
							onCreate.accept(path);
						} else if (kind == ENTRY_MODIFY) {
							onModify.accept(path);
						} else if (kind == ENTRY_DELETE) {
							onDelete.accept(path);
						} else if (kind == OVERFLOW) {
							continue;
						}
						
					}
					
					if (!key.reset()) {
						services.remove(pathAndWs);
					}
				}
				
				synchronized (state) {
					if (state != State.RUNNING) {
						clean();
						break;
					}
				}
			}
		}
		
	}
	
	/**
	 * Starts watch service in thread scheduled by executor service
	 * @param executorService
	 */
	public void start(ExecutorService executorService) {
		synchronized (this.state) {
			if (this.state == State.RUNNING) {
				return;
			}
			
			if (this.state == State.STOPPED) {
				throw new RuntimeException("FileWatcher has been already stopped");
			}
			
			this.state = State.RUNNING;
		}
		executorService.execute(new WatchServiceLoop());
	}
	
	/**
	 * Starts watch service in internally managed thread
	 */
	public void start() {
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		start(executorService);
		executorService.shutdown();
	}
	
	/**
	 * Stops watch service and cleans resources
	 */
	public void stop() {
		synchronized (this.state) {
			this.state = State.STOPPED;
		}
	}
	
	/**
	 * Cleans resources
	 */
	public void clean() {
		for (Pair<Path, WatchService> pathAndWs : services) {
			try {
				pathAndWs.getValue1().close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
