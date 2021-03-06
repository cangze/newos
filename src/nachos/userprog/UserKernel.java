package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import java.util.LinkedList;
/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
    /**
     * Allocate a new user kernel.
     */
    public static LinkedList<Integer> freePages = new LinkedList<Integer>();
    public static Semaphore processIDSem;
    public static Semaphore freePagesSem;
    public UserKernel() {
        super();
    }
    /**
     * Initialize this kernel. Creates a synchronized console and sets the
     * processor's exception handler.
     *  初始化这个内核。创建同步控制台并设置处理器的异常处理程序。
     */
    public void initialize(String[] args) {
        System.out.println("initializing");
        //获取配置文件信息   创建线程 以及时钟
        super.initialize(args);
        super.initialize(args);
        //多个用户程序之间共享控制台
        console = new SynchConsole(Machine.console());
        processIDSem = new Semaphore(1);
        freePagesSem = new Semaphore(1);

        int numPhysPages = Machine.processor().getNumPhysPages();
        //初始化物理页  物理内存
        for (int i = 0; i < numPhysPages; ++i) {
            freePages.add(i);
        }
        Machine.processor().setExceptionHandler(new Runnable() {
            public void run() { exceptionHandler(); }
        });
    }
    //给进程获取物理页
    public static int getFreePage() {
        int pageNumber = -1;
        boolean interruptStatus = Machine.interrupt().disable();
        freePagesSem.P();
        if (!freePages.isEmpty()) {
            pageNumber = freePages.removeFirst();
        }
        freePagesSem.V();
        Machine.interrupt().restore(interruptStatus);
        return pageNumber;
    }

    //释放物理内存
    public static void addFreePage(int pageNumber) {
        boolean interruptStatus = Machine.interrupt().disable();
        freePagesSem.P();
        freePages.addFirst(pageNumber);
        freePagesSem.V();
        Machine.interrupt().restore(interruptStatus);
    }
    /**
     * Test the console device.
     */
    public void selfTest() {
        super.selfTest();

        System.out.println("Testing the console device. Typed characters");
        System.out.println("will be echoed until q is typed.");

        char c;

        do {
            c = (char) console.readByte(true);
            console.writeByte(c);
        }
        while (c != 'q');

        System.out.println("");
    }

    /**
     * Returns the current process.
     *
     * @return	the current process, or <tt>null</tt> if no process is current.
     */
    public static UserProcess currentProcess() {
        if (!(KThread.currentThread() instanceof UThread))
            return null;

        return ((UThread) KThread.currentThread()).process;
    }

    /**
     * The exception handler. This handler is called by the processor whenever
     * a user instruction causes a processor exception.
     *
     * <p>
     * When the exception handler is invoked, interrupts are enabled, and the
     * processor's cause register contains an integer identifying the cause of
     * the exception (see the <tt>exceptionZZZ</tt> constants in the
     * <tt>Processor</tt> class). If the exception involves a bad virtual
     * address (e.g. page fault, TLB miss, read-only, bus error, or address
     * error), the processor's BadVAddr register identifies the virtual address
     * that caused the exception.
     */
    public void exceptionHandler() {
        Lib.assertTrue(KThread.currentThread() instanceof UThread);

        UserProcess process = ((UThread) KThread.currentThread()).process;
        int cause = Machine.processor().readRegister(Processor.regCause);
        process.handleException(cause);
    }

    /**
     * Start running user programs, by creating a process and running a shell
     * program in it. The name of the shell program it must run is returned by
     * <tt>Machine.getShellProgramName()</tt>.
     *
     * @see	nachos.machine.Machine#getShellProgramName
     */
    public void run() {
        super.run();

        UserProcess process = UserProcess.newUserProcess();

        String shellProgram = Machine.getShellProgramName();
        Lib.assertTrue(process.execute(shellProgram, new String[] { }));

        KThread.currentThread().finish();
    }

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
        super.terminate();
    }

    /** Globally accessible reference to the synchronized console.
     *  //对同步控制台的全局可访问引用 */
    public static SynchConsole console;

    // dummy variables to make javac smarter
    private static Coff dummy1 = null;
}
