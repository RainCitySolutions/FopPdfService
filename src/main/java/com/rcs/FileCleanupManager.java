package com.rcs;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class FileCleanupManager implements Runnable
{
	private static TimeUnit	CRON_UNIT = TimeUnit.MINUTES;
	private static int CRON_PERIOD = 15;
	private static FileCleanupManager instance;

	private Logger logger;
	private SortedSet<PathEntry> entrySet = Collections.synchronizedSortedSet(new TreeSet<PathEntry>());

	private static class PathEntry
		implements Comparable<PathEntry>
	{
		private Path path;
		private Date delDate;
		
		public PathEntry (Path path, Date date)
		{
			this.path = path;
			this.delDate = date;
		}

		@Override
		public int compareTo(PathEntry entry) {
			return delDate.compareTo(entry.delDate);
		}
	}	
	
	
	private FileCleanupManager () {
		logger = LogManager.getLogger(FileCleanupManager.class);
	}
	
	public static FileCleanupManager getInstance()
	{
		if (null == instance) {
			instance = new FileCleanupManager();
		}
		
		return instance;
	}
	
	/**
	 * Adds an entry to the list of Paths to be cleaned up in the future.
	 * 
	 * @param path The Path to the folder or file to clean up.
	 * @param period The period of time to wait. Must be greater than 0 and less than or equal to 24 hours.
	 * @param unit The unit type specified by <code>period</code>.
	 */
	public void addEntry(final Path path, int period, TimeUnit unit)
	{
		if (null != path &&
			period > 0 &&
			unit.toSeconds(period) <= TimeUnit.HOURS.toSeconds(24) )
		{
			logger.debug("Queuing {} to be deleted", path.toString());

			Calendar cal = Calendar.getInstance();
			cal.setTime(new Date());
			cal.add(Calendar.SECOND, (int)unit.toSeconds(period));
			
			PathEntry entry = new PathEntry(path, cal.getTime());
			
			synchronized(entrySet) {
				entrySet.add(entry);
			}
		}
		else {
			throw new IllegalArgumentException("Invalid unit or period out of range");
		}
	}

	public void initializeSchedule (final ScheduledExecutorService scheduler)
	{
		scheduler.scheduleAtFixedRate(this, 0, CRON_PERIOD, CRON_UNIT);
	}

	@Override
	public void run() {
		logger.debug("Running FileCleanupManager");
		
		List<PathEntry> cleanupList = new LinkedList<PathEntry>();
		Date timeNow = new Date();

		synchronized (entrySet) {
			logger.debug("{} folders in the queue", entrySet.size());
			Iterator<PathEntry> itor = entrySet.iterator();
			
			while (itor.hasNext()) {
				PathEntry entry = itor.next();

				if (entry.delDate.before(timeNow)) {
					cleanupList.add(entry);
				}
				else {
					break;
				}
			}
		}
		
		logger.debug("Deleting {} folders", cleanupList.size());

		if (!cleanupList.isEmpty()) {
			Iterator<PathEntry> itor = cleanupList.iterator();
			while (itor.hasNext()) {
				PathEntry entry = itor.next();
				
			    try {
					logger.debug("Deleting {}", entry.path);
					Files.walkFileTree(
						entry.path, 
						new SimpleFileVisitor<Path> () {
							@Override
							public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
									throws IOException
							{
								Files.delete(file);
								return FileVisitResult.CONTINUE;
							}

							@Override
							public FileVisitResult postVisitDirectory(Path dir, IOException ioe)
									throws IOException
							{
								if (ioe == null) {
					                 Files.delete(dir);
					                 return FileVisitResult.CONTINUE;
					             } else {
					                 // directory iteration failed
					                 throw ioe;
					             }
							}
							
						});
				} catch (IOException e) {
					logger.catching(e);
				}
			}
			
			synchronized (entrySet) {
				entrySet.removeAll(cleanupList);
			}
		}
	}
}
