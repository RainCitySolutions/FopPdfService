package com.rcs;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FileCleanupManagerTest
{
	@Mock
    private Logger loggerMock;
	private MockedStatic<LogManager> mockLogMgr;
	
	Path tempPath = null;
	
	private void validateItemCount(FileCleanupManager fcm, int count)
	{
		try {
			Field fld = FileCleanupManager.class.getDeclaredField("entrySet");
			fld.setAccessible(true);

			@SuppressWarnings("unchecked")
			Set<Object> set = (Set<Object>)fld.get(fcm);
			
			if (null != set) {
				assertEquals(count, set.size());
			}
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@BeforeEach
	public void prepTest()
	{
		/*
		 * Clear the instance variable so new instance is created for each test.
		 */
		try {
			Field fld = FileCleanupManager.class.getDeclaredField("instance");
			fld.setAccessible(true);
			
			fld.set(null, null);
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		}
		
		mockLogMgr = Mockito.mockStatic(LogManager.class);
		mockLogMgr.when(() -> LogManager.getLogger(any(Class.class))).thenReturn(loggerMock);
	}
	
	@AfterEach
	public void cleanupTest()
	{
		mockLogMgr.close();

		if (null != tempPath) {
			try
			{
				Files.delete(tempPath);
			}
			catch (Exception e) { }
			
			tempPath = null;
		}
	}

	@Test
	public void getInstanceTest()
	{
		FileCleanupManager fcm = FileCleanupManager.getInstance();
		assertEquals(fcm, FileCleanupManager.getInstance());
	}
	
	@Test
	public void initializeScheduleTest()
	{
		FileCleanupManager fcm = FileCleanupManager.getInstance();

		ScheduledExecutorService mockSvc = Mockito.mock(ScheduledExecutorService.class);
		
		Mockito.when(mockSvc.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(null);
		 		
		fcm.initializeSchedule(mockSvc);

		Mockito.verify(mockSvc).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
	}
	
	@Test
	public void addEntry_nullPath()
	{
		assertThrows(IllegalArgumentException.class, () -> {
			FileCleanupManager fcm = FileCleanupManager.getInstance();
			
			fcm.addEntry (null, 5, TimeUnit.MINUTES);

			validateItemCount(fcm, 0);
	    });
	}
	
	@Test
	public void addEntry_invalidUnit()
	{
		assertThrows(IllegalArgumentException.class, () -> {
			try {
				tempPath = Files.createTempFile("fcmTest", null);
	
				FileCleanupManager fcm = FileCleanupManager.getInstance();
				
				fcm.addEntry (tempPath, 5, TimeUnit.DAYS);
	
				validateItemCount(fcm, 0);
			} catch (IOException e) {
				fail("Unable to create test file");
			}
		});
	}

	@Test
	public void addEntry_periodOutOfRange()
	{
		assertThrows(IllegalArgumentException.class, () -> {
			try {
				tempPath = Files.createTempFile("fcmTest", null);
	
				FileCleanupManager fcm = FileCleanupManager.getInstance();
				
				fcm.addEntry (tempPath, 25, TimeUnit.HOURS);
	
				validateItemCount(fcm, 0);
			} catch (IOException e) {
				fail("Unable to create test file");
			}
		});
	}

	@Test
	public void addEntry_invalidPeriod()
	{
		assertThrows(IllegalArgumentException.class, () -> {
			try {
				tempPath = Files.createTempFile("fcmTest", null);
	
				FileCleanupManager fcm = FileCleanupManager.getInstance();
				
				fcm.addEntry (tempPath, 0, TimeUnit.SECONDS);
	
				validateItemCount(fcm, 0);
			} catch (IOException e) {
				fail("Unable to create test file");
			}
		});
	}

	@Test
	public void addEntry_validEntry()
	{
		try {
			tempPath = Files.createTempFile("fcmTest", null);

			FileCleanupManager fcm = FileCleanupManager.getInstance();
			
			fcm.addEntry (tempPath, 2, TimeUnit.MINUTES);

			validateItemCount(fcm, 1);
		} catch (IOException e) {
			fail("Unable to create test file");
		}
	}

	@Test
	public void run_emptyEntryList()
	{
		try {
			tempPath = Files.createTempFile("fcmTest", null);

			FileCleanupManager fcm = FileCleanupManager.getInstance();
			
			validateItemCount(fcm, 0);
			fcm.run();
		} catch (IOException e) {
			fail("Unable to create test file");
		}
	}
	
	@Test
	public void run_fileEntry()
	{
		try {
			tempPath = Files.createTempFile("fcmTest", null);

			FileCleanupManager fcm = FileCleanupManager.getInstance();
			
			fcm.addEntry (tempPath, 1, TimeUnit.MILLISECONDS);

			validateItemCount(fcm, 1);
			Thread.sleep(500);

			fcm.run();

			validateItemCount(fcm, 0);
			
			assertFalse(Files.exists(tempPath));
		} catch (IOException | InterruptedException e) {
			fail("Unable to create test file");
		}
	}

	@Test
	public void run_fileAlreadyDeleted()
	{
		try {
			tempPath = Files.createTempFile("fcmTest", null);

			FileCleanupManager fcm = FileCleanupManager.getInstance();
			
			fcm.addEntry (tempPath, 1, TimeUnit.MILLISECONDS);

			validateItemCount(fcm, 1);
			
			Files.delete(tempPath);

			Thread.sleep(500);
			
			fcm.run();
			
			validateItemCount(fcm, 0);
		} catch (IOException | InterruptedException e) {
			fail("Unable to create test file");
		}
	}


	@Test
	public void run_fileNotReadyYet()
	{
		try {
			tempPath = Files.createTempFile("fcmTest", null);

			FileCleanupManager fcm = FileCleanupManager.getInstance();
			
			fcm.addEntry (tempPath, 1, TimeUnit.HOURS);

			validateItemCount(fcm, 1);
			
			fcm.run();
			
			validateItemCount(fcm, 1);
		} catch (IOException e) {
			fail("Unable to create test file");
		}
	}
}
