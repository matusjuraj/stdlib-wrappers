package sk.matus.wrappers;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

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
	
	/**
	 * 
	 * Creates a FileWatcher for given paths reacting with given callbacks
	 * Watches all directories deep into the passed directories
	 * @param onCreate Callback to create event
	 * @param onModify Callback to modify event
	 * @param onDelete Callback to delete event
	 * @param paths Watched directories
	 * @return
	 * @throws IOException
	 */
	public static FileWatcher createRecursive(Consumer<Path> onCreate, Consumer<Path> onModify,
		Consumer<Path> onDelete, Path... paths) throws IOException {
		
		Path[] recursivePaths = Stream.of(paths).flatMap(p -> {
			try {
				return Files.walk(p);
			} catch (Exception e) {
				return Stream.empty();
			}
		}).toArray(l -> new Path[l]);
		
		return create(onCreate, onModify, onDelete, recursivePaths);
	}
	
	/**
	 * 
	 * Creates a FileWatcher for given paths reacting with given callbacks
	 * Watches all directories deep into the passed directories, registering new direcotries if created
	 * @param onCreate Callback to create event
	 * @param onModify Callback to modify event
	 * @param onDelete Callback to delete event
	 * @param paths Watched directories
	 * @return
	 * @throws IOException
	 */
	public static FileWatcher createRecursiveAdaptive(Consumer<Path> onCreate, Consumer<Path> onModify,
		Consumer<Path> onDelete, Path... paths) throws IOException {
		
		AtomicReference<FileWatcher> fw = new AtomicReference<>();
		
		Consumer<Path> onCreateRegisterNew = path -> {
			if (Files.isDirectory(path)) {
				fw.get().registerPaths(fw.get().additions, path);
			}
		};
		onCreate = onCreateRegisterNew.andThen(onCreate);
		
		fw.set(createRecursive(onCreate, onModify, onDelete, paths));
		return fw.get();
	}
	
	/**
	 * Running state of FileWatcher
	 *
	 */
	private enum State {
		PREPARED,
		RUNNING,
		STOPPED
	}
	
	private final List<Pair<Path, WatchService>> services;
	
	private final LinkedList<Pair<Path, WatchService>> additions;
	
	private final Consumer<Path> onCreate;
	
	private final Consumer<Path> onModify;
	
	private final Consumer<Path> onDelete;
	
	private State state = State.PREPARED;
	
	private FileWatcher(Consumer<Path> onCreate, Consumer<Path> onModify,
		Consumer<Path> onDelete, Path... paths) {
		
		this.services = new Vector<>();
		this.additions = new LinkedList<>();
		
		this.onCreate = onCreate;
		this.onModify = onModify;
		this.onDelete = onDelete;
		
		registerPaths(services, paths);
	}
	
	/**
	 * Creates watch service for each path and registers it to them
	 * @param paths
	 */
	private void registerPaths(List<Pair<Path, WatchService>> into, Path... paths) {
		for (Path path : paths) {
			
			// Do not register the same path repeatedly
			if (services.stream()
				.map(pw -> pw.getValue0())
				.filter(p -> {
					try {
						return Files.isSameFile(path, p);
					} catch (IOException e) {
						return false;
					}
				})
				.findFirst()
				.isPresent()) {
				continue;
			}
			
			WatchService ws = null;
			try {
				ws = FileSystems.getDefault().newWatchService();
				path.register(ws, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
				into.add(new Pair<>(path, ws));
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
		
		LinkedList<Pair<Path, WatchService>> toRemove = new LinkedList<>();
		
		private Pair<Path, WatchService> next() {
			
			if (iterator == null || !iterator.hasNext()) {
				
				// Apply remove queue
				while (toRemove.size() > 0) {
					services.remove(toRemove.pop());
				}
				
				// Apply add queue
				while (additions.size() > 0) {
					services.add(additions.pop());
				}
				
				iterator = services.iterator();
				if (!iterator.hasNext()) {
					stop();
					return null;
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
					key = pathAndWs == null ? null : pathAndWs.getValue1().poll();
				} catch (ClosedWatchServiceException e) {
					key = null;
					toRemove.add(pathAndWs);
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
						toRemove.add(pathAndWs);
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
