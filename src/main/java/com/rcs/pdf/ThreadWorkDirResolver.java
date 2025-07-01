package com.rcs.pdf;

import java.nio.file.Path;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;

public class ThreadWorkDirResolver
	extends WorkDirResolver
{
	private static ThreadLocal<Path> workdirPath = new ThreadLocal<>();

	public ThreadWorkDirResolver ()
	{
		super();
	}

	public static void setWorkDir(Path workdir) {
		workdirPath.set(workdir);
	}

	public static void removeWorkDir() {
		workdirPath.remove();
	}
	
	public static Path getWorkDir() {
		return workdirPath.get();
	}

	@Override
	public Source resolve(String href, String base)
		throws TransformerException
	{
		Source src = null;
		
		tempDirPath = workdirPath.get();
		
		if (null != tempDirPath) {
			src = super.resolve(href, base);
		}
		
		return src;
	}
}
