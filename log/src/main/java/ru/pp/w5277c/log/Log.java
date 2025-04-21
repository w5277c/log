/*--------------------------------------------------------------------------------------------------------------------------------------------------------------
Файл распространяется под лицензией GPL-3.0-or-later, https://www.gnu.org/licenses/gpl-3.0.txt
----------------------------------------------------------------------------------------------------------------------------------------------------------------
12.04.2016	w5277c@gmail.com		Начало
13.08.2019	w5277c@gmail.com		Расширен Level
--------------------------------------------------------------------------------------------------------------------------------------------------------------*/
package ru.pp.w5277c.log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Log {
	public static class Item {
		private	final	static	AtomicInteger		instanceQuantity	= new AtomicInteger(0);
		private			static	SimpleDateFormat	msgSDF				= new SimpleDateFormat(MSG_FORMAT);
		private	final			Level				level;
		private					LinkedList<Object>	messages			= null;
		private					long				timestamp			= 0;
		private					long				threadId			= 0;

		public Item(Level level, LinkedList<Object> message) {
			threadId = Thread.currentThread().getId();
			this.level = level;
			this.messages = message;
			timestamp = System.currentTimeMillis();
			instanceQuantity.incrementAndGet();
		}

		public Item(Level level, Object ...messages) {
			threadId = Thread.currentThread().getId();
			this.level = level;
			this.messages = new LinkedList<>();
			this.messages.addAll(Arrays.asList(messages));
			timestamp = System.currentTimeMillis();
			instanceQuantity.incrementAndGet();
		}

		public Item(Level level, long threadId, long timestamp, Object message) {
			this.threadId = threadId;
			this.level = level;
			this.messages = new LinkedList<>();
			this.messages.add(message);
			this.timestamp = timestamp;
			instanceQuantity.incrementAndGet();
		}

		public String getLine() throws UnsupportedEncodingException {
			StringBuilder sb =  new StringBuilder(	msgSDF.format(new Date(timestamp)) + "[" + Long.toHexString(threadId).toUpperCase()  + "] " +
													level.toString() + " ");
			for(Object message : messages) {
				sb.append(convert(message));
			}
			sb.append(CR);
			return sb.toString();
		}

		private String convert(Object message) {
			StringBuilder result = new StringBuilder();
			if(message instanceof byte[]) {
				result.append("0x");
				result.append(printHexBinary((byte[])message));
			}
			else if(message instanceof ParseException) {
				result.append(((ParseException)message).toString()).append(", offset:").append(((ParseException)message).getErrorOffset());
				for (StackTraceElement stackTrace : ((Exception)message).getStackTrace()) {
					result.append(CR);
					result.append(stackTrace.toString());
				}
			}
			else if(message instanceof Exception) {
				result.append(((Exception)message).toString());
				for (StackTraceElement stackTrace : ((Exception)message).getStackTrace()) {
					result.append(CR);
					result.append(stackTrace.toString());
				}
			}
			else {
				result.append(message);
			}
			return result.toString();
		}

		public Level getLevel() {
			return level;
		}

		public long getTimestamp() {
			return timestamp;
		}

		@Override
		protected void finalize() throws Throwable {
			instanceQuantity.decrementAndGet();
			super.finalize();
		}
		public static int getInstanceQuantity() {
			return instanceQuantity.get();
		}
	}

	public enum Level {
		PNC(0x04),
		DBG(0x03),
		INF(0x02),
		WRN(0x01),
		ERR(0x00);

		private static final Map<Integer, Level> ids = new HashMap<>();
		static {
			for (Level level : Level.values()) {
				ids.put(level.getId(), level);
			}
		}

		private int id = 0x00;
		
		private Level(int id) {
			this.id = id;
		}

		public int getId() {
			return id;
		}

		public static Level fromInt(int id) {
			Level resutl = ids.get(id);
			if(null == resutl) {
				resutl = Level.INF;
			}
			return resutl;
		}
	}

	private final TimerTask pusher = new TimerTask() {
		@Override
		public synchronized void run() {
			if(busy.compareAndSet(false, true)) {
				try {
					if(!queue.isEmpty()) {
						while(enabled && !queue.isEmpty()) {
							Item item = queue.poll();
							String str = item.getLine();
							if(stdout) System.out.print(str);
							
							int newDayNum = (int)((item.getTimestamp() + Calendar.getInstance().getTimeZone().getRawOffset()) / (24 * 60 * 60 * 1000L));
							if(newDayNum != dayNum) {
								dayNum = newDayNum;
								closeStream();
								openStream(item.getTimestamp());
							}
							os.write(str.getBytes(StandardCharsets.UTF_8));
						}
						os.flush();
					}
				}
				catch(Exception ex) {
					ex.printStackTrace();
				}
				busy.set(false);
			}
		}
	};
	 
	private	static	final	long					PERIOD				= 300;
	private	static	final	String					PATH				= ".";
	private	static	final	String					NAME				= "default";
	private	static	final	String					EXTENTION			= ".log";
	private	static	final	String					FILENAME_FORMAT		= "'_'yyyy_MM_dd_HH_mm_ss_SSS";
	private	static	final	String					MSG_FORMAT			= "HH:mm:ss.SSS";
	private	static	final	String					CR					= "\r\n";
	private			final	Queue<Item>				queue				= new ConcurrentLinkedQueue<>();
	private			final	Timer					timer				= new Timer("Log timer");
	private			final	AtomicBoolean			busy				= new AtomicBoolean(false);
	private					Level					level				= Level.INF;
	private					String					path;
	private					String					name;
	private					int						dayNum				= -1;
	private					OutputStream			os					= null;
	private					boolean					enabled				= true;
	private					boolean					stdout				= false;
			  
	public Log() {
		this(PATH, NAME, null, false);
	}
	public Log(String path) {
		this(path, NAME, null, false);
	}
	public Log(String path, boolean stdout) {
		this(path, NAME, null, stdout);
	}
	public Log(String path, String name, Level level) {
		this(path, name, level, false);
	}
	public Log(String path, String name, Level level, boolean stdout) {
		this.path = path;
		this.name = name;
		if(null != level) {
			this.level = level;
		}
		this.stdout = stdout;
		
		timer.scheduleAtFixedRate(pusher, PERIOD, PERIOD);
	}

	public void debug(Object... msgs) {
		push(new Item(Level.DBG, msgs));
	}

	public void info(Object... msgs) {
		push(new Item(Level.INF, msgs));
	}

	public void warning(Object... msgs) {
		push(new Item(Level.WRN, msgs));
	}

	public void error(Object... msgs) {
		push(new Item(Level.ERR, msgs));
	}

	public void panic(Object... msgs) {
		push(new Item(Level.PNC, msgs));
	}

	public void push(Item item) {
		if(item.getLevel().getId() <= level.getId()) {
			queue.add(item);
		}
	}

	private void openStream(long timestamp) {
		Date date = new Date(timestamp);
		SimpleDateFormat sdf = new SimpleDateFormat(FILENAME_FORMAT);

		try {
			File file = new File(path + File.separator + name + sdf.format(date) + EXTENTION);
			file.createNewFile();
			os = new FileOutputStream(file);
		}
		catch(Exception ex) {
			System.out.println("Exception (file:" + path + File.separator + name + sdf.format(date) + EXTENTION + "):");
			ex.printStackTrace();
		}
	}

	private void closeStream() {
		if(null != os) {
			try {
				os.close();
			}
			catch(Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	public void close() {
		timer.cancel();
		pusher.run();
		closeStream();
	}

	public void forceClose() {
		timer.cancel();
		enabled = false;
		closeStream();
	}

	public Level getLevel() {
		return level;
	}
	public void setLevel(Level level) {
		this.level = level;
	}

	public static String printHexBinary(byte[] bytes) {
		if(null == bytes || 0 == bytes.length) {
			return "";
		}

		StringBuilder result = new StringBuilder();
		for(int pos = 0; pos < bytes.length; pos++) {
			String num = Integer.toHexString(bytes[pos] & 0xff).toLowerCase();
			if(num.length() < 0x02) {
				result.append("0");
			}
			result.append(num);
		}
		return result.toString();
	}
}
