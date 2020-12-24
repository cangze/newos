package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		this.processID = processesCreated++;
		//引用类型默认值为null
		this.openFiles = new OpenFile[MAX_OPEN_FILES_PER_PROCESS];
		// set stdin and stdout handlers properly
		// 文件打开表的前两项是基本输入输出
		this.openFiles[0] = UserKernel.console.openForReading();
		FileRef.referenceFile(openFiles[0].getName());
		this.openFiles[1] = UserKernel.console.openForWriting();
		FileRef.referenceFile(openFiles[1].getName());
		this.childrenCreated = new HashSet<Integer>();
//        int numPhysPages = Machine.processor().getNumPhysPages();
//        pageTable = new TranslationEntry[numPhysPages];
//        for (int i=0; i<numPhysPages; i++)
//            pageTable[i] = new TranslationEntry(i,i, true,false,false,false);
	}

	/**
	 * Allocate and return a new process of the correct class. The class name
	 * is specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 *
	 * @return	a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 *
	 * @param	name	the name of the file containing the executable.
	 * @param	args	the arguments to pass to the executable.
	 * @return	<tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args)) {
			return false;
		}
		UThread T=(UThread)new UThread(this).setName(name);
		//放一个map，方便找
		pidThreadMap.put(this.processID, T);
		T.fork();
		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read
	 * at most <tt>maxLength + 1</tt> bytes from the specified address, search
	 * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 *
	 * @param	vaddr	the starting virtual address of the null-terminated
	 *			string.
	 * @param	maxLength	the maximum number of characters in the string,
	 *				not including the null terminator.
	 * @return	the string read, or <tt>null</tt> if no null terminator was
	 *		found.
	 *	   //从该进程的虚拟内存中读取以空结尾的字符串。从指定地址最多读取<TT>最大长度+1 </TT>字节，
	 *     // 搜索null终止符，并将其转换为<TT> Java.Lang.Stult</TT>，不包括null终止符。如果未找到空终止符，则返回<tt>null</tt>。
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength+1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length=0; length<bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param	vaddr	the first byte of virtual memory to read.
	 * @param	data	the array where the data will be stored.
	 * @return	the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no
	 * data could be copied).
	 *
	 * @param	vaddr	the first byte of virtual memory to read.
	 * @param	data	the array where the data will be stored.
	 * @param	offset	the first byte to write in the array.
	 * @param	length	the number of bytes to transfer from virtual memory to
	 *			the array.
	 * @return	the number of bytes successfully transferred.
	 *   //将数据从该进程的虚拟内存传输到指定的数组。
	 *     // 此方法处理地址转换详细信息。如果发生错误，此方法必须 <i>not</i> 销毁当前进程，
	 *     // 而是应返回成功复制的字节数（如果无法复制任何数据，则返回零）。
	 *
	 *     //读内存时 利用页表 将逻辑地址转化为物理地址  然后将内存复制到数组中
	 */

	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length && memoryAccessLock != null);
		if (!validAddress(vaddr)) {
			return 0;
		} else {
			Collection<MemoryAccess> memoryAccesses = createMemoryAccesses(vaddr, data, offset, length, AccessType.READ);
			//创建内存访问链表
			int bytesRead = 0, temp;

			memoryAccessLock.acquire();
			for (MemoryAccess ma : memoryAccesses) {
				//执行内存读操作
				temp = ma.executeAccess();

				if (temp == 0)
					break;
				else
					bytesRead += temp;
			}
			memoryAccessLock.release();
			return bytesRead;
		}
	}
	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory.
	 * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param	vaddr	the first byte of virtual memory to write.
	 * @param	data	the array containing the data to transfer.
	 * @return	the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no
	 * data could be copied).
	 *
	 * @param	vaddr	the first byte of virtual memory to write.
	 * @param	data	the array containing the data to transfer.
	 * @param	offset	the first byte to transfer from the array.
	 * @param	length	the number of bytes to transfer from the array to
	 *			virtual memory.
	 * @return	the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length && memoryAccessLock != null);
		if (!validAddress(vaddr)) {
			return 0;
		} else {
			Collection<MemoryAccess> memoryAccesses = createMemoryAccesses(vaddr, data, offset, length, AccessType.WRITE);
			//创造内存访问列表
			int bytesWritten = 0, temp;
			memoryAccessLock.acquire();
			for (MemoryAccess ma : memoryAccesses) {
				//执行内存操作
				temp = ma.executeAccess();
				if (temp == 0)
					break;
				else
					bytesWritten += temp;
			}
			memoryAccessLock.release();

			return bytesWritten;
		}
	}

	/**
	 * 创造内存访问的序列
	 */
	private Collection<MemoryAccess> createMemoryAccesses(int vaddr, byte[] data, int offset, int length, AccessType accessType) {
		LinkedList<MemoryAccess> returnList = new LinkedList<MemoryAccess>();

		while (length > 0) {
			//访问地址的虚拟页号
			int vpn = Processor.pageFromAddress(vaddr);
			//可能还需要访问的数量
			int potentialPageAccess = Processor.pageSize - Processor.offsetFromAddress(vaddr);
			//确定访问长度
			int accessSize = length < potentialPageAccess ? length : potentialPageAccess;
			//将创建的访问内存访问项加入列表
			returnList.add(new MemoryAccess(accessType, data, vpn, offset, Processor.offsetFromAddress(vaddr), accessSize));
			//减少已经访问的长度
			length -= accessSize;
			//增加访问过的地址
			vaddr += accessSize;
			//增加访问过的偏移
			offset += accessSize;
		}

		return returnList;
	}


	protected boolean validAddress(int vaddr)
	{
		int vpn = Processor.pageFromAddress(vaddr);
		return vpn<numPages && vpn>=0;
	}


	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 *
	 * @param	name	the name of the file containing the executable.
	 * @param	args	the arguments to pass to the executable.
	 * @return	<tt>true</tt> if the executable was successfully loaded.
	 * //将具有指定名称的可执行文件加载到此进程中，并准备将指定参数传递给它。
	 * 	 *     // 打开可执行文件，读取其头信息，并将节和参数复制到此进程的虚拟内存中
	 * 	 *     //从磁盘装入进程  需要装入一个coff的对象  包含若干个段  每一段是一个coffsection的对象 包含若干个页，
	 * 	 *     如左图：
	 * COFF文件一共有8种数据，自上而下分别为：
	 * 1. 文件头（File Header）
	 * 2. 可选头（Optional Header）
	 * 3. 段落头（Section Header）
	 * 4. 段落数据（Section Data）
	 * 5. 重定位表（Relocation Directives）
	 * 6. 行号表（Line Numbers）
	 * 7. 符号表（Symbol Table）
	 * 8. 字符串表（String Table）
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			System.out.println("没找到");
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s=0; s<coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i=0; i<args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages*pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page

		loadArguments(args, argv);

		return true;
	}

	public void loadArguments(String[] args,byte[][] argv)
	{
		int entryOffset = (numPages-1)*pageSize;
		int stringOffset = entryOffset + args.length*4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i=0; i<argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
					argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
			stringOffset += 1;
		}
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be
	 * run (this is the last step in process initialization that can fail).
	 * 为该进程分配内存，并将COFF部分加载到Run（这是进程初始化的最后一步，可能会失败）。
	 * @return	<tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}
		//在导入 section之前应该先创建一个页表
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; ++i) {
			//分配物理页数
			int ppn = UserKernel.getFreePage();
			// //虚拟页号   物理页号  标记位   只读位    被使用位   脏位
			pageTable[i] = new TranslationEntry(i, ppn, true, false, false, false);

		}
		// load sections
		for (int s=0; s<coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i=0; i<section.getLength(); i++) {
				int vpn = section.getFirstVPN()+i;
				pageTable[vpn].readOnly = section.isReadOnly();
				// for now, just assume virtual addresses=physical addresses
				section.loadPage(i,pageTable[vpn].ppn);
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		releaseResource();
		coff.close();
	}
	protected void releaseResource() {
		for (int i = 0; i < pageTable.length; ++i)
			if (pageTable[i].valid) {
				UserKernel.addFreePage(pageTable[i].ppn);
				pageTable[i] = new TranslationEntry(pageTable[i].vpn, 0, false, false, false, false);
			}
		numPages = 0;
	}
	//读取页表的一项
	protected TranslationEntry AllocatePageTable(int vpn) {
		if (pageTable == null)
			return null;

		if (vpn >= 0 && vpn < pageTable.length)
			return pageTable[vpn];
		else
			return null;
	}
	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of
	 * the stack, set the A0 and A1 registers to argc and argv, respectively,
	 * and initialize all other registers to 0.
	 */
	//初始化处理器的寄存器，以准备运行加载到此进程中的程序。将pc寄存器设置为指向起始函数，
	// 将堆栈指针寄存器设置为指向堆栈顶部，将a0和a1寄存器分别设置为argc和argv，并将所有其他寄存器初始化为0。
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i=0; i<processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		//这一步判断是谁halt，只有root进程可以halt，别人不能随便halt
		if (this.processID != 0)
			return -1;
		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/** PROCESS MANAGEMENT SYSCALLS: exit(), exec(), join() */

	/**
	 * Terminate the current process immediately. Any open file descriptors
	 * belonging to the process are closed. Any children of the process no
	 * longer have a parent process.
	 * （1）首先关闭coff  将所有的打开文件关闭  将退出状态置入，
	 *      * （2）如果该进程有父进程   看是否执行了join方法 如果执行了就将其唤醒  同时将此进程从子进程链表中删除，这一步是在finish里面已经写了的！直接调用finish就可以了
	 *      * （3）调用unloadsections释放内存，调用kthread。finish结束线程
	 *      * （4）如果是最后一个线程 则停机
	 * @param status is returned to the parent process as this process's exit status
	 *               and can be collected using the join syscall. A process exiting normally
	 *               should (but is not required to) set status to 0.
	 *               <p/>
	 *               exit() never returns.
	 */
	private void handleExit(int status) {

		for (OpenFile file : openFiles) {
			if (file != null)
				file.close();
		}
		//释放资源
		this.unloadSections();
		//如果是最后一个进程用户进程调用了系统调用，就
		if (pidThreadMap.size() == 1) {
			Kernel.kernel.terminate();
		}
		pidThreadMap.remove(this.processID);
		processStatus = status;
		exitSuccess = true;
		UThread.finish();
	}

	/**
	 * Execute the program stored in the specified file, with the specified
	 * arguments, in a new child process. The child process has a new unique
	 * process ID, and starts with stdin opened as file descriptor 0, and stdout
	 * opened as file descriptor 1.
	 *
	 * @param pFile 程序地址 is a ptr to a null-terminated string that specifies the name
	 *              of the file containing the executable. Note that this string must include
	 *              the ".coff" extension.
	 * @param argc  参数个数 specifies the number of arguments to pass to the child
	 *              process. This number must be non-negative.
	 * @param pArgv  参数地址 is an array of pointers to null-terminated strings that
	 *              represent the arguments to pass to the child process. argv[0] points to
	 *              the first argument, and argv[argc-1] points to the last argument.
	 * @return the child process's process ID, which can be passed to join(). On
	 *         error, returns -1.
	 */
	private int handleExec(int pFile, int argc, int pArgv) {
		//Lib.debug(dbgProcess, "pFile is "+pFile);
		//Lib.debug(dbgProcess, "argc is "+argc);
		//Lib.debug(dbgProcess, "pArgv is "+pArgv);
		if (validAddress(pFile) && argc >= 0) {//判断地址是否合法，参数个数是否小于1
			String fileName = readVirtualMemoryString(pFile, MAX_FILENAME_LENGTH);
			byte[] argvBytes = new byte[argc * sizeOfInt];
			// 读取参数列表
			if (readVirtualMemory(pArgv, argvBytes, 0, argvBytes.length) == argvBytes.length) {
				int[] argvAddrs = new int[argc];//地址数组
				for (int i = 0; i < (argc * sizeOfInt); i += sizeOfInt) {
					argvAddrs[i / sizeOfInt] = Lib.bytesToInt(argvBytes, i);//从argvByte的第i位开始转换一个int，小端表示
				}
				String[] argvStrings = new String[argc];
				int remainingBytes = Processor.pageSize;
				for (int i = 0; i < argc; ++i) {
					argvStrings[i] = readVirtualMemoryString(argvAddrs[i], Math.min(remainingBytes, 256));
					if (argvStrings[i] == null || argvStrings[i].length() > remainingBytes) {
						return ERROR; // arguments do not fit on one page
					}
					remainingBytes -= argvStrings[i].length();
				}
				UserProcess childProcess = UserProcess.newUserProcess();
				if (childProcess.execute(fileName, argvStrings)) { //tries to load and run program
					childrenCreated.add(childProcess.processID);
					return childProcess.processID;
				}
			}
		}
		return ERROR;
	}
	/**
	 * Suspends execution of the current process until the child process
	 * specified by the processID argument has exited. handleJoin() returns
	 * immediately if the child has already exited by the time of the call. When
	 * the current process resumes, it disowns the child process, so that
	 * handleJoin() cannot be used on that process again.
	 *
	 * @param processID the process ID of the child process, returned by exec().
	 * @param pStatus    points to an integer where the exit status of the child
	 *                  process will be stored. This is the value the child passed to exit(). If
	 *                  the child exited because of an unhandled exception, the value stored is
	 *                  not defined.
	 * @return returns 1, if the child exited normally, 0 if the child exited as
	 *         a result of an unhandled exception and -1 if processID does not refer to
	 *         a child process of the current process.
	 */
	private int handleJoin(int processID, int pStatus) {
		if (!childrenCreated.contains(processID)) {
			return ERROR; // pID not a child of calling process
		}
		// calls join on thread running child process
		UThread uThread = pidThreadMap.get(processID);
		uThread.join();

		//set the status of the process retrieved
		int tmpStatus = uThread.process.processStatus;


		// removes this child's pID from set of children pIDs which will
		// prevent a future call to join on same child process
		childrenCreated.remove(processID);

		byte[] statusBytes = new byte[sizeOfInt];

		Lib.bytesFromInt(statusBytes, 0, tmpStatus);
		int statusBytesWritten = writeVirtualMemory(pStatus, statusBytes);

		if (uThread.process.exitSuccess && statusBytesWritten == sizeOfInt) {
			return 1; // child exited normally
		}
		return 0; // child exited as a result of an unhandled exception
	}
	/**
	 * Returns an available slot in the file descriptor table, if one exists,
	 * else, returns -1.
	 */
	protected int getUnusedFileDescriptor() throws NachosInternalException {
		for (int i = 0; i < this.openFiles.length; ++i) {
			if (this.openFiles[i] == null) {
				return i;
			}
		}
		throw new NachosOutOfFileDescriptorsException("没有空闲的文件打开表区域可以使用");
	}
	//根据文件名 寻找文件是否已经被打开
	public int findFileDescriptorByName(String filename) {
		for (int i = 0; i < openFiles.length; ++i) {
			if (openFiles[i]!=null&&openFiles[i].getName().equals(filename)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Handle the creat(char *name) system call.
	 */
	private int handleCreat(final int fileAddress) throws NachosInternalException {

		final String fileName = readVirtualMemoryString(fileAddress, MAX_FILENAME_LENGTH);

		// Is the fileName valid?
		if (fileName == null || fileName.length() == 0) {
			throw new NachosIllegalArgumentException("Invalid filename for creat()");
		}

		// Do we already have a file descriptor for a file with the same name?
		for (int i = 0; i < this.openFiles.length; ++i) {
			if (this.openFiles[i] != null && this.openFiles[i].getName().equals(fileName)) {
				return i;
			}
		}
		if (!FileRef.referenceFile(fileName))
			return -1;
		//
		final int fileDescriptor = this.getUnusedFileDescriptor();

		//使用文件系统 的open方法  第二个参数为true  表示 如果没有则创建一个新文件 ，这个就是create 和open 的差别
		final OpenFile file = ThreadedKernel.fileSystem.open(fileName, true);

		if (file == null) {
			FileRef.unreferenceFile(fileName);
			throw new NachosFileSystemException("Unable to create file: " + fileName);
		}

		this.openFiles[fileDescriptor] = file;

		return fileDescriptor;
	}
	public static enum AccessType {
		READ, WRITE
	};
	/**
	 * 将内存的操作抽象为一个类
	 * 便于管理
	 */
	public class MemoryAccess {
		protected MemoryAccess(AccessType at, byte[] d, int _vpn, int dStart, int pStart, int len) {
			accessType = at;
			data = d;
			vpn = _vpn;
			dataStart = dStart;
			pageStart = pStart;
			length = len;
		}

		/**
		 * 执行内存读写操作
		 */
		public int executeAccess() {
			if (translationEntry == null)
				//没有项需要去页表中寻找
				translationEntry = pageTable[vpn];
			if (translationEntry.valid) {
				if (accessType == AccessType.READ) {
					//读内存，将内存中的数据读取到指定的数组内
					System.arraycopy(Machine.processor().getMemory(), pageStart + (Processor.pageSize * translationEntry.ppn), data, dataStart, length);
					translationEntry.used = true;
					return length;
				} else if (!translationEntry.readOnly && accessType == AccessType.WRITE) {
					//将数组内容写进内存
					System.arraycopy(data, dataStart, Machine.processor().getMemory(), pageStart + (Processor.pageSize * translationEntry.ppn), length);
					translationEntry.used = true;
					//脏位置1
					translationEntry.dirty = true;
					return length;
				}
			}

			return 0;
		}

		/**
		 * 参与的数组
		 */
		protected byte[] data;

		/**
		 * 访问类型
		 */
		protected AccessType accessType;

		/**
		 * 操作的翻译项
		 */
		protected TranslationEntry translationEntry;

		/**
		 * 访问数组起始位置
		 */
		protected int dataStart;

		/**
		 * 物理帧的起始位置
		 */
		protected int pageStart;

		/**
		 * 访问长度
		 */
		protected int length;

		/**
		 * 虚拟页号
		 */
		protected int vpn;
	}

	/**
	 * Handle the open(char *name) syscall. Very similar to creat(), so most comments
	 * have been omitted.
	 */
	private int handleOpen(final int pName) throws NachosInternalException {
		final int fileDescriptor = this.getUnusedFileDescriptor();

		final String fileName = readVirtualMemoryString(pName, MAX_FILENAME_LENGTH);
		if (fileName == null || fileName.length() == 0) {
			throw new NachosIllegalArgumentException("Invalid filename for open()");
		}
		if (!FileRef.referenceFile(fileName))
			return -1;
		final OpenFile file = ThreadedKernel.fileSystem.open(fileName, false);
		if (file == null) {
			FileRef.unreferenceFile(fileName);
			throw new NachosFileSystemException("Unable to open file: " + fileName);
		}

		this.openFiles[fileDescriptor] = file;

		return fileDescriptor;
	}

	/**
	 * Handle the read(int fileDescriptor, void *buffer, int count) syscall.
	 * 文件index   ，地址，读取字节数
	 */
	private int handleRead(final int fileDescriptor, final int virtualAddress, final int bufferSize)
			throws NachosInternalException {
		// check count is a valid arg
		if (bufferSize < 0) {
			throw new NachosIllegalArgumentException("Count must be non-negative for read()");
		}
		// Make sure FD is valid
		if (fileDescriptor < 0 ||
				fileDescriptor >= this.openFiles.length ||
				openFiles[fileDescriptor] == null) {
			throw new NachosIllegalArgumentException("Invalid file descriptor passed to read()");
		}

		final OpenFile file = this.openFiles[fileDescriptor];
		//读数据的数组容器 ，返回读取字节数
		final byte[] tmp = new byte[bufferSize];
		Lib.debug('k', "read!");
		final int numBytesRead = file.read(tmp, 0, bufferSize);
		//读完以后要写入内存，写数据返回写入字节数
		if(numBytesRead<=0)
		{
			Lib.debug('k', "return 0!");
			return 0;
		}
		Lib.debug('k', "write!");
		final int numBytesWritten = writeVirtualMemory(virtualAddress, tmp, 0, numBytesRead);
		//这个是判断，读写字节不一致，就说明异常了

		if (numBytesRead != numBytesWritten) {
			return -1;
		}

		return numBytesRead;

	}

	/**
	 * Handle the write(int fileDescriptor, void *buffer, int count) syscall.，写和读没什么区别
	 */
	private int handleWrite(final int fileDescriptor, final int pBuffer, final int count)
			throws NachosInternalException {
		if (count < 0) {
			throw new NachosIllegalArgumentException("Count must be non-negative for write().");
		}
		if (fileDescriptor < 0 ||
				fileDescriptor >= this.openFiles.length ||
				openFiles[fileDescriptor] == null) {
			throw new NachosIllegalArgumentException("Invalid file descriptor passed to write()");
		}

		final OpenFile file = this.openFiles[fileDescriptor];
		//定义容器
		final byte[] tmp = new byte[count];
		final int numBytesToWrite = readVirtualMemory(pBuffer, tmp);

		if (numBytesToWrite != count) {
			return -1;
		}
		//to
		// TODO(amidvidy): need to handle the case that file is actually an instance of SynchConsole.file()...
		return file.write(tmp, 0, numBytesToWrite);


	}

	/**
	 * Handle closing a file descriptor
	 */
	private int handleClose(final int fileDescriptor) throws NachosInternalException {
		//check if file descriptor exists and then that
		if (fileDescriptor < 0 || fileDescriptor > MAX_OPEN_FILES_PER_PROCESS-1 ) {
			throw new NachosIllegalArgumentException("Invalid file descriptor passed to close()");
		}

		//set file to file referred to by file descriptor
		OpenFile file = openFiles[fileDescriptor];
		String fileName = file.getName();
		//check that the file is still open
		if (file == null) {
			throw new NachosFileSystemException("There is no open file with the given file descriptor passed to close()");
		}

		//从文件打开表里面删除
		openFiles[fileDescriptor] = null;
		//调用文件系统函数，进行删除
		file.close();

		return FileRef.unreferenceFile(fileName);
	}

	/**
	 * Handle unlinking a file     //删除某个文件  根据传入的文件名内存地址   从虚拟存储中读出  文件名
	 */
	private int handleUnlink(final int filenameAddress) throws NachosInternalException {
		//get the file from memory
		String fileName = readVirtualMemoryString(filenameAddress, MAX_FILENAME_LENGTH);

		//check that the file has a legitimate name and length
		if (fileName == null || fileName.length() <= 0) {
			throw new NachosIllegalArgumentException("Invalid file name for unlink()");
		}

		// Invalidate any file descriptors for this file for the current process
		for (int i = 0; i < openFiles.length; i++) {
			if ((openFiles[i] != null) && (openFiles[i].getName().equals(fileName))) {
				handleClose(filenameAddress);
				// If we change the behavior
				break;
			}
		}

		return FileRef.deleteFile(fileName);
	}
	protected static final int
			syscallHalt = 0,
			syscallExit = 1,
			syscallExec = 2,
			syscallJoin = 3,
			syscallCreate = 4,
			syscallOpen = 5,
			syscallRead = 6,
			syscallWrite = 7,
			syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 *
	 * <table>
	 * <tr><td>syscall#</td><td>syscall prototype</td></tr>
	 * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
	 * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
	 * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td></tr>
	 * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
	 * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
	 * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
	 * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
	 *								</tt></td></tr>
	 * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
	 *								</tt></td></tr>
	 * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
	 * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
	 * </table>
	 *
	 * @param	syscall	the syscall number.
	 * @param	a0	the first syscall argument.
	 * @param	a1	the second syscall argument.
	 * @param	a2	the third syscall argument.
	 * @param	a3	the fourth syscall argument.
	 * @return	the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {//系统调用 只实现了一个系统调用！进行扩展！
		try {
			switch (syscall) {
				case syscallHalt:
					return handleHalt();
				//第一个任务
				case syscallCreate:
					return handleCreat(a0);
				case syscallOpen:
					return handleOpen(a0);
				case syscallRead:
					return handleRead(a0, a1, a2);
				case syscallWrite:
					return handleWrite(a0, a1, a2);
				case syscallClose:
					return handleClose(a0);
				case syscallUnlink:
					return handleUnlink(a0);
				//第三个任务
				case syscallExec:
					return handleExec(a0, a1, a2);
				case syscallJoin:
					return handleJoin(a0, a1);
				case syscallExit:
					handleExit(a0);
					return 0;


				default:
					Lib.debug(dbgProcess, "Unknown syscall " + syscall);
					Lib.assertNotReached("Unknown system call!");
			}
		}
		catch (final NachosIllegalArgumentException e) {
			return ERROR;
		} catch (final NachosOutOfFileDescriptorsException e) {
			return ERROR;
		} catch (final NachosFileSystemException e) {
			return ERROR;
		} catch (final NachosVirtualMemoryException e) {
			return ERROR;
		} catch (final NachosFatalException e) {
			// kill process.
			handleExit(ERROR);
		} catch (final NachosInternalException e) {
			Lib.assertNotReached("This should never happen.");
		}

		return 0;
	}



	protected static class FileRef {
		int references;
		boolean delete;

		/**
		 * Increment the number of active references there are to a file
		 * @return
		 * 		False if the file has been marked for deletion
		 */
		public static boolean referenceFile(String fileName) {
			FileRef ref = updateFileReference(fileName);
			boolean canReference = !ref.delete;
			if (canReference)
				ref.references++;
			finishUpdateFileReference();
			return canReference;
		}

		/**
		 * Decrement the number of active references there are to a file
		 * Delete the file if necessary
		 * @return
		 * 		0 on success, -1 on failure
		 */
		public static int unreferenceFile(String fileName) {
			FileRef ref = updateFileReference(fileName);
			ref.references--;
			Lib.assertTrue(ref.references >= 0);
			int ret = removeIfNecessary(fileName, ref);
			finishUpdateFileReference();
			return ret;
		}

		/**
		 * Mark a file as pending deletion, and delete the file if no active references
		 * @return
		 * 		0 on success, -1 on failure
		 */
		public static int deleteFile(String fileName) {
			FileRef ref = updateFileReference(fileName);
			ref.delete = true;
			int ret = removeIfNecessary(fileName, ref);
			finishUpdateFileReference();
			return ret;
		}

		/**
		 * Remove a file if marked for deletion and has no active references
		 * Remove the file from the reference table if no active references
		 * THIS FUNCTION MUST BE CALLED WITHIN AN UPDATEFILEREFERENCE LOCK!
		 * @return
		 * 		0 on success, -1 on failure to remove file
		 */
		private static int removeIfNecessary(String fileName, FileRef ref) {
			if (ref.references <= 0) {
				globalFileReferences.remove(fileName);
				if (ref.delete == true) {
					if (!UserKernel.fileSystem.remove(fileName))
						return -1;
				}
			}
			return 0;
		}

		/**
		 * Lock the global file reference table and return a file reference for modification.
		 * If the reference doesn't already exist, create it.
		 * finishUpdateFileReference() must be called to unlock the table again!
		 *
		 * @param fileName
		 * 		File we with to reference
		 * @return
		 * 		FileRef object
		 */
		private static FileRef updateFileReference(String fileName) {
			globalFileReferencesLock.acquire();
			FileRef ref = globalFileReferences.get(fileName);
			if (ref == null) {
				ref = new FileRef();
				globalFileReferences.put(fileName, ref);
			}

			return ref;
		}

		/**
		 * Release the lock on the global file reference table
		 */
		private static void finishUpdateFileReference() {
			globalFileReferencesLock.release();
		}

		/** Global file reference tracker & lock */
		private static HashMap<String, FileRef> globalFileReferences = new HashMap<String, FileRef> ();
		private static Lock globalFileReferencesLock = new Lock();
	}
	/**
	 * Handle a user exception. Called by
	 * <tt>UserKernel.exceptionHandler()</tt>. The
	 * <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 *
	 * @param	cause	the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
			case Processor.exceptionSyscall:
				int result = handleSyscall(processor.readRegister(Processor.regV0),
						processor.readRegister(Processor.regA0),
						processor.readRegister(Processor.regA1),
						processor.readRegister(Processor.regA2),
						processor.readRegister(Processor.regA3)
				);
				processor.writeRegister(Processor.regV0, result);
				processor.advancePC();
				break;

			default:
				Lib.debug(dbgProcess, "Unexpected exception: " +
						Processor.exceptionNames[cause]);
				Lib.assertNotReached("Unexpected exception");
		}
	}
	public static abstract class NachosInternalException extends RuntimeException {
		public NachosInternalException(final String message) {
			super(message);
		}
	}

	/**
	 * Thrown when a syscall receives an illegal argument.
	 */
	private final class NachosIllegalArgumentException extends NachosInternalException {
		public NachosIllegalArgumentException(final String message) {
			super(message);
		}
	}

	/**
	 * Thrown when a syscall needs to create a new file descriptor, but has used all available FDs.
	 */
	private final class NachosOutOfFileDescriptorsException extends NachosInternalException {
		public NachosOutOfFileDescriptorsException(final String message) {
			super(message);
		}
	}

	/**
	 * Thrown when there is an error with the fileSystem.
	 */
	private final class NachosFileSystemException extends NachosInternalException {
		public NachosFileSystemException(final String message) {
			super(message);
		}
	}

	/**
	 * Thrown when there is an error with the virtual memory subsystem.
	 */
	private final class NachosVirtualMemoryException extends NachosInternalException {
		public NachosVirtualMemoryException(final String message) {
			super(message);
		}
	}

	/**
	 * Thrown when there is a user space exception that requires killing the running process.
	 */
	private final class NachosFatalException extends NachosInternalException {
		public NachosFatalException(final String message) {
			super(message);
		}
	}
	private boolean inVaddressSpace(int addr) {
		return (addr >= 0 && addr < pageTable.length * pageSize);
	}
	private boolean inPhysAddressSpace(int addr) {
		return (addr >= 0 || addr < Machine.processor().getMemory().length);
	}
	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;
	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;
	private int argc, argv;
	/**
	 * Constant. The return value for an error.
	 */
	private static final int ERROR = -1;

	/**
	 * Constant. The return value for a successful operation.
	 */
	private static final int SUCCESS = 0;

	/**
	 * Constant. The max amount of open files per process.
	 */
	private static final int MAX_OPEN_FILES_PER_PROCESS = 16;

	/**
	 * Constant. The max length in characters of a file name.
	 */
	private static final int MAX_FILENAME_LENGTH = 256;

	/**
	 * The processID of this process.
	 */
	public final int processID;

	/**
	 * The file handles that this process owns.
	 */
	protected final OpenFile[] openFiles;

	/**
	 * Pages allocated to the process.
	 */
	private List<Integer> allocatedPages;

	/**
	 * Global Hashmap of all <ProcessID,Thread>
	 */
	private static Map<Integer, UThread> pidThreadMap = new HashMap<Integer, UThread>();

	/**
	 * Status of a child process
	 */
	private int processStatus;

	/**
	 * Denotes whether a process exited successfully
	 */
	private boolean exitSuccess;
	/**
	 *
	 */
	private Lock memoryAccessLock=new Lock();

	/**
	 * Global number of processes created
	 */
	public static int processesCreated = 0;

	/**
	 * A list of children processes created
	 */
	private HashSet<Integer> childrenCreated;

	/**
	 * The number of pages in the program's stack.
	 */
	private static final int pageSize = Processor.pageSize;
	private static final int sizeOfInt = 4;
	private static final char dbgProcess = 'a';
}
