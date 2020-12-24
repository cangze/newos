package nachos.vm;

import java.util.*;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
    /**
     * Allocate a new VM kernel.
     */
    public VMKernel() {
        super();

        //初始化反向页表
        for (int i = 0; i < invertedTable.length; i++)
            invertedTable[i] = new MemoryEntry(i);
    }

    /**
     * Initialize this kernel.
     */
    @Override
    public void initialize(String[] args) {
        super.initialize(args);
        memoryLock = new Lock();
        allfixed = new Condition(memoryLock);
        swap = new Swap();
    }

    /**
     * Start running user programs.
     */
    @Override
    public void run() {
        super.run();
    }

    /**
     * Terminate this kernel. Never returns.
     */
    @Override
    public void terminate() {
        //删除swap文件
        swap.cleanup();

        super.terminate();
    }

    /**
     * 选择一个帧
     */
    //选择一个空闲页
    private MemoryEntry clockAlgorithm() {
//        System.out.println("时钟置换starting");
        //内存锁，防止产生进程同步错误
        memoryLock.acquire();
        //若物理内存的帧全部固定，则让当前请求页进程睡眠
        while (fixedCount == invertedTable.length) allfixed.sleep();

        //需要将TLB中页的状态更新到反向页表内
        propagateAndFlushTLB(false);

        // 寻找不固定的页
        while (true) {
            clockHand = (clockHand+1) % invertedTable.length;
            MemoryEntry page = invertedTable[clockHand];

            //跳过不固定的页
            if (page.fixed)
                continue;

            // 无效页直接替换
            if (page.processID == -1 || page.translationEntry.valid == false)
                break;

            // 二次机会法
            if (page.translationEntry.used) {
                page.translationEntry.used = false;
            }
            //否则该页为牺牲品
            else {
                break;
            }
        }

        MemoryEntry me = invertedTable[clockHand];
        //固定该页
        fixedCount++;
        me.fixed = true;
        //置换该页后TLB中的项无效化
        invalidateTLBEntry(clockHand);
        System.out.println("clock选中的MemoEntry:"+me);
        //从快速查找表中删除该项
        MemoryEntry me1 = null;
        if (me.processID > -1)
            me1 = hashFindTable.remove(new TableKey(me.translationEntry.vpn, me.processID));
        
        memoryLock.release();

        //换出
        if (me1 != null) swap.swapOut(me);
//        System.out.println("时钟置换finished");
        return me;
    }

    /**
     * 使用二次机会算法，得到空闲的物理帧
     */
    TranslationEntry requestFreePage(int vpn, int pid) {
        System.out.println("二次机会算法");
        // 选择一个空闲页
        MemoryEntry page = clockAlgorithm();

        // 将页清零
        int pageBeginAddress = Processor.makeAddress(page.translationEntry.ppn, 0);
        Arrays.fill(Machine.processor().getMemory(), pageBeginAddress, pageBeginAddress + Processor.pageSize, (byte) 0);

        // 设置页
        page.translationEntry.vpn = vpn;
        page.translationEntry.valid = true;
        page.processID = pid;

        // 加入快速查找表
        insertIntoFindTable(vpn, pid, page);

        return page.translationEntry;
    }

    private void insertIntoFindTable(int vpn, int pid, MemoryEntry page) {
        System.out.println("this");
        memoryLock.acquire();
        hashFindTable.put(new TableKey(vpn, pid), page);
        memoryLock.release();
    }

    /**
     * 产生页错误的处理
     */
    TranslationEntry pageFault(int vpn, int pid) {
        System.out.println("处理页错误 vpn:"+vpn+" pid:"+pid);
        if (!swap.pageInSwap(vpn, pid))
            //不在swap file中
            return null;
        TranslationEntry te = requestFreePage(vpn, pid);
        //寻找空的物理帧
        swap.swapIn(vpn, pid, te.ppn);
        //换入
        return te;
    }

    /**
     * 清除该进程在内存中所有的页
     */
    void freePages(int pid, int maxVPN) {
        memoryLock.acquire();
        for (MemoryEntry page : invertedTable)
            if (page.processID == pid) {
                // 清除并无效
                hashFindTable.remove(new TableKey(page.translationEntry.vpn, page.processID));
                page.processID = -1;
                page.translationEntry.valid = false;
            }

        memoryLock.release();
        //清除swap file虚拟内存中的数据
        swap.freePages(maxVPN, pid);
    }

    /**
     * 将帧取消固定，并且唤醒一个等待使用物理内存的进程
     */
    void unfix(int ppn) {
        memoryLock.acquire();
        MemoryEntry me = invertedTable[ppn];

        if (me.fixed)
            fixedCount--;

        me.fixed = false;

        allfixed.wake();

        memoryLock.release();
    }

    /**
     * 将存在在反向页表中的帧固定
     */
    TranslationEntry fixIfExists(int vpn, int pid) {
        MemoryEntry me = null;
        memoryLock.acquire();

        if ((me = hashFindTable.get(new TableKey(vpn, pid))) != null) {
            //固定帧
            if (!me.fixed)
                fixedCount++;
            me.fixed = true;
        }

        memoryLock.release();

        if (me == null)
            return null;
        else
            return me.translationEntry;
    }


    void propagateAndFlushTLB(boolean flush) {
        for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
            TranslationEntry te = Machine.processor().readTLBEntry(i);
            //遍历每个TLB项，将所有TLB项置为invalid，方便给下一个进程使用
            if (te.valid) {
                TranslationEntry translationEntry = invertedTable[te.ppn].translationEntry;
                //将TLB项状态写回至至反向页表中
                if (translationEntry.valid && translationEntry.vpn == te.vpn) {
                    translationEntry.used |= te.used;
                    translationEntry.dirty |= te.dirty;
                }
            }

            if (flush) {
                te.valid = false;
                Machine.processor().writeTLBEntry(i, te);
            }
        }
    }

    /**
     * 无效TLB项
     */
    void invalidateTLBEntry(int ppn) {
        for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
            TranslationEntry te = Machine.processor().readTLBEntry(i);
            if (te.valid && te.ppn == ppn) {
                te.valid = false;
                Machine.processor().writeTLBEntry(i, te);
                break;
            }
        }
    }

    void propagateEntry(int ppn, boolean used, boolean dirty) {
        memoryLock.acquire();
        TranslationEntry te = invertedTable[ppn].translationEntry;
        te.used |= used;
        te.dirty |= dirty;
        memoryLock.release();
    }

    private static final char dbgVM = 'v';

    /** 反向页表 */
    private MemoryEntry[] invertedTable = new MemoryEntry[Machine.processor().getNumPhysPages()];

    /** clock算法也就是二次机会算法的指针 */
    private int clockHand = 0;

    /** 方便快速查找反向页表的哈希表 */
    /** TLB是一个指针，指向HASHFINDTABLE */
    private Hashtable<TableKey,MemoryEntry> hashFindTable = new Hashtable<TableKey,MemoryEntry>();

    /** 内存锁 */
    private Lock memoryLock;

    /** 内存固定帧的数量 */
    private int fixedCount;

    /** 当内存所有帧被占有时进程等待的条件变量*/
    private Condition allfixed;

    /** 反向页表中的虚拟页号和进程号的项 */
    private static class TableKey {
        TableKey(int vpn1, int pid1) {
            vpn = vpn1;
            pid = pid1;
        }

        @Override
        public int hashCode() {
            //将虚拟页号和pid作为hash code，来唯一标识反向页表中的项
            //进程数大于256，可能会产生冲突
            return Processor.makeAddress(vpn, pid );
        }

        @Override
        public boolean equals(Object x) {
            if (this == x)
                return true;
            else if (x instanceof TableKey) {
                TableKey xCasted = (TableKey)x;
                return vpn.equals(xCasted.vpn) && pid.equals(xCasted.pid);
            } else {
                return false;
            }
        }

        private Integer vpn, pid;
    }

    /** 在反向页表中的页表项*/
    private static class MemoryEntry {
        MemoryEntry (int ppn) {
            translationEntry = new TranslationEntry(-1, ppn, false, false, false, false);
        }
        //list  数组[]
        TranslationEntry translationEntry;

        int processID = -1;
        //表示该页还有没完成的操作，不允许被修改，以防内存内容不对应
        boolean fixed = false;


        @Override
        public String toString() {
            return "memoryentry processid:"+processID+" fixed:"+fixed+" translationEntry:"+translationEntry+" ";
        }
    }

    /**
     * 创建swap file
     */
    protected OpenFile openSwapFile() {
        return fileSystem.open("swapfile", true);
    }

    private class Swap {
        Swap() {
            swapFile = openSwapFile();
        }

        /**
         * 将物理帧写入swap file中
         */
        void swapOut(MemoryEntry me) {
            System.out.println("帧换出starting");
            if (me.translationEntry.valid) {
                //该页表项有效
                SwapEntry swapEntry = null;
                TableKey tk = new TableKey(me.translationEntry.vpn, me.processID);
                //获取swap file锁
                swapLock.acquire();
                if (me.translationEntry.dirty || !swapTable.containsKey(tk)) {
                    // 需要将帧的内容写回swap file
                    if (freeList.size() > 0) {
                        //有空闲位置
                        swapEntry = freeList.removeFirst();
                        swapEntry.readOnly = me.translationEntry.readOnly;
                    }
                    else {
                        //无空闲位置，需要在swap file中创建新的swap 项
                        swapEntry = new SwapEntry(maxTableEntry++, me.translationEntry.readOnly);
                    }

                    swapTable.put(tk, swapEntry);
                }
                swapLock.release();

                if (swapEntry != null) {
                    //需要写回swap file
                    Lib.debug(dbgVM,"swap out from physical page num:"+me.translationEntry.ppn+" into swap page num:"+swapEntry.swapPageNumber);
                    Lib.assertTrue(swapFile.write(swapEntry.swapPageNumber * Processor.pageSize,
                            Machine.processor().getMemory(),
                            me.translationEntry.ppn * Processor.pageSize,
                            Processor.pageSize) == Processor.pageSize);
                }
            }
            System.out.println("帧换出finised");
        }

        private int maxTableEntry = 0;

        /**
         * 从swap file 中读取虚拟页进入物理帧中
         */
        void swapIn(int vpn, int pid, int ppn) {
            System.out.println("帧换入starting");
            swapLock.acquire();
            SwapEntry swapEntry = swapTable.get(new TableKey(vpn, pid));
            //获得swap file中该项的信息
            swapLock.release();

            if (swapEntry != null) {
                // 该页在swap file 中
                Lib.debug(dbgVM,"swap in from swap page num:"+swapEntry.swapPageNumber+" into physical page num:"+ppn);
                Lib.assertTrue(swapFile.read(swapEntry.swapPageNumber * Processor.pageSize,
                        Machine.processor().getMemory(),
                        ppn * Processor.pageSize,
                        Processor.pageSize) == Processor.pageSize);
                //将swap file中的页读入物理内存
                invertedTable[ppn].translationEntry.readOnly = swapEntry.readOnly;
            }
            System.out.println("帧换入finished");
        }

        /**
         * 判断该进程的虚拟页号是否在swap file 中
         */
        boolean pageInSwap(int vpn, int pid) {
            swapLock.acquire();
            boolean retBool = swapTable.containsKey(new TableKey(vpn, pid));
            swapLock.release();
            return retBool;
        }

        /**
         * 将swap file中的页加入freelist，可以被其他swap帧置换
         */
        void freePages(int maxVPN, int pid) {
            swapLock.acquire();
            SwapEntry freeEntry;
            for (int i = 0; i < maxVPN; i++)
                if ((freeEntry = swapTable.get(new TableKey(i, pid))) != null)
                    freeList.add(freeEntry);
            swapLock.release();
        }

        /**
         * 关闭并删除swap file
         */
        void cleanup() {
            swapFile.close();
            fileSystem.remove(swapFile.getName());
        }

        /** swap记录 */
        private class SwapEntry {
            SwapEntry (int spn, boolean ro) {
                swapPageNumber = spn;
                readOnly = ro;
            }
            int swapPageNumber;
            boolean readOnly;
        }

        /**
         * swap file
         */
        private OpenFile swapFile;

        /** 在swap file中的页 */
        private LinkedList<SwapEntry> freeList = new LinkedList<SwapEntry>();

        /** 进程页号映射在 swap file中的记录 */
        private HashMap<TableKey, SwapEntry> swapTable = new HashMap<TableKey, SwapEntry>();

        /** 在操控swap方法的锁 */
        private Lock swapLock = new Lock();
    }

    private Swap swap;
}