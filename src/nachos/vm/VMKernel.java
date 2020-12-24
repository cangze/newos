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

        //��ʼ������ҳ��
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
        //ɾ��swap�ļ�
        swap.cleanup();

        super.terminate();
    }

    /**
     * ѡ��һ��֡
     */
    //ѡ��һ������ҳ
    private MemoryEntry clockAlgorithm() {
//        System.out.println("ʱ���û�starting");
        //�ڴ�������ֹ��������ͬ������
        memoryLock.acquire();
        //�������ڴ��֡ȫ���̶������õ�ǰ����ҳ����˯��
        while (fixedCount == invertedTable.length) allfixed.sleep();

        //��Ҫ��TLB��ҳ��״̬���µ�����ҳ����
        propagateAndFlushTLB(false);

        // Ѱ�Ҳ��̶���ҳ
        while (true) {
            clockHand = (clockHand+1) % invertedTable.length;
            MemoryEntry page = invertedTable[clockHand];

            //�������̶���ҳ
            if (page.fixed)
                continue;

            // ��Чҳֱ���滻
            if (page.processID == -1 || page.translationEntry.valid == false)
                break;

            // ���λ��ᷨ
            if (page.translationEntry.used) {
                page.translationEntry.used = false;
            }
            //�����ҳΪ����Ʒ
            else {
                break;
            }
        }

        MemoryEntry me = invertedTable[clockHand];
        //�̶���ҳ
        fixedCount++;
        me.fixed = true;
        //�û���ҳ��TLB�е�����Ч��
        invalidateTLBEntry(clockHand);
        System.out.println("clockѡ�е�MemoEntry:"+me);
        //�ӿ��ٲ��ұ���ɾ������
        MemoryEntry me1 = null;
        if (me.processID > -1)
            me1 = hashFindTable.remove(new TableKey(me.translationEntry.vpn, me.processID));
        
        memoryLock.release();

        //����
        if (me1 != null) swap.swapOut(me);
//        System.out.println("ʱ���û�finished");
        return me;
    }

    /**
     * ʹ�ö��λ����㷨���õ����е�����֡
     */
    TranslationEntry requestFreePage(int vpn, int pid) {
        System.out.println("���λ����㷨");
        // ѡ��һ������ҳ
        MemoryEntry page = clockAlgorithm();

        // ��ҳ����
        int pageBeginAddress = Processor.makeAddress(page.translationEntry.ppn, 0);
        Arrays.fill(Machine.processor().getMemory(), pageBeginAddress, pageBeginAddress + Processor.pageSize, (byte) 0);

        // ����ҳ
        page.translationEntry.vpn = vpn;
        page.translationEntry.valid = true;
        page.processID = pid;

        // ������ٲ��ұ�
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
     * ����ҳ����Ĵ���
     */
    TranslationEntry pageFault(int vpn, int pid) {
        System.out.println("����ҳ���� vpn:"+vpn+" pid:"+pid);
        if (!swap.pageInSwap(vpn, pid))
            //����swap file��
            return null;
        TranslationEntry te = requestFreePage(vpn, pid);
        //Ѱ�ҿյ�����֡
        swap.swapIn(vpn, pid, te.ppn);
        //����
        return te;
    }

    /**
     * ����ý������ڴ������е�ҳ
     */
    void freePages(int pid, int maxVPN) {
        memoryLock.acquire();
        for (MemoryEntry page : invertedTable)
            if (page.processID == pid) {
                // �������Ч
                hashFindTable.remove(new TableKey(page.translationEntry.vpn, page.processID));
                page.processID = -1;
                page.translationEntry.valid = false;
            }

        memoryLock.release();
        //���swap file�����ڴ��е�����
        swap.freePages(maxVPN, pid);
    }

    /**
     * ��֡ȡ���̶������һ���һ���ȴ�ʹ�������ڴ�Ľ���
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
     * �������ڷ���ҳ���е�֡�̶�
     */
    TranslationEntry fixIfExists(int vpn, int pid) {
        MemoryEntry me = null;
        memoryLock.acquire();

        if ((me = hashFindTable.get(new TableKey(vpn, pid))) != null) {
            //�̶�֡
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
            //����ÿ��TLB�������TLB����Ϊinvalid���������һ������ʹ��
            if (te.valid) {
                TranslationEntry translationEntry = invertedTable[te.ppn].translationEntry;
                //��TLB��״̬д����������ҳ����
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
     * ��ЧTLB��
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

    /** ����ҳ�� */
    private MemoryEntry[] invertedTable = new MemoryEntry[Machine.processor().getNumPhysPages()];

    /** clock�㷨Ҳ���Ƕ��λ����㷨��ָ�� */
    private int clockHand = 0;

    /** ������ٲ��ҷ���ҳ��Ĺ�ϣ�� */
    /** TLB��һ��ָ�룬ָ��HASHFINDTABLE */
    private Hashtable<TableKey,MemoryEntry> hashFindTable = new Hashtable<TableKey,MemoryEntry>();

    /** �ڴ��� */
    private Lock memoryLock;

    /** �ڴ�̶�֡������ */
    private int fixedCount;

    /** ���ڴ�����֡��ռ��ʱ���̵ȴ�����������*/
    private Condition allfixed;

    /** ����ҳ���е�����ҳ�źͽ��̺ŵ��� */
    private static class TableKey {
        TableKey(int vpn1, int pid1) {
            vpn = vpn1;
            pid = pid1;
        }

        @Override
        public int hashCode() {
            //������ҳ�ź�pid��Ϊhash code����Ψһ��ʶ����ҳ���е���
            //����������256�����ܻ������ͻ
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

    /** �ڷ���ҳ���е�ҳ����*/
    private static class MemoryEntry {
        MemoryEntry (int ppn) {
            translationEntry = new TranslationEntry(-1, ppn, false, false, false, false);
        }
        //list  ����[]
        TranslationEntry translationEntry;

        int processID = -1;
        //��ʾ��ҳ����û��ɵĲ������������޸ģ��Է��ڴ����ݲ���Ӧ
        boolean fixed = false;


        @Override
        public String toString() {
            return "memoryentry processid:"+processID+" fixed:"+fixed+" translationEntry:"+translationEntry+" ";
        }
    }

    /**
     * ����swap file
     */
    protected OpenFile openSwapFile() {
        return fileSystem.open("swapfile", true);
    }

    private class Swap {
        Swap() {
            swapFile = openSwapFile();
        }

        /**
         * ������֡д��swap file��
         */
        void swapOut(MemoryEntry me) {
            System.out.println("֡����starting");
            if (me.translationEntry.valid) {
                //��ҳ������Ч
                SwapEntry swapEntry = null;
                TableKey tk = new TableKey(me.translationEntry.vpn, me.processID);
                //��ȡswap file��
                swapLock.acquire();
                if (me.translationEntry.dirty || !swapTable.containsKey(tk)) {
                    // ��Ҫ��֡������д��swap file
                    if (freeList.size() > 0) {
                        //�п���λ��
                        swapEntry = freeList.removeFirst();
                        swapEntry.readOnly = me.translationEntry.readOnly;
                    }
                    else {
                        //�޿���λ�ã���Ҫ��swap file�д����µ�swap ��
                        swapEntry = new SwapEntry(maxTableEntry++, me.translationEntry.readOnly);
                    }

                    swapTable.put(tk, swapEntry);
                }
                swapLock.release();

                if (swapEntry != null) {
                    //��Ҫд��swap file
                    Lib.debug(dbgVM,"swap out from physical page num:"+me.translationEntry.ppn+" into swap page num:"+swapEntry.swapPageNumber);
                    Lib.assertTrue(swapFile.write(swapEntry.swapPageNumber * Processor.pageSize,
                            Machine.processor().getMemory(),
                            me.translationEntry.ppn * Processor.pageSize,
                            Processor.pageSize) == Processor.pageSize);
                }
            }
            System.out.println("֡����finised");
        }

        private int maxTableEntry = 0;

        /**
         * ��swap file �ж�ȡ����ҳ��������֡��
         */
        void swapIn(int vpn, int pid, int ppn) {
            System.out.println("֡����starting");
            swapLock.acquire();
            SwapEntry swapEntry = swapTable.get(new TableKey(vpn, pid));
            //���swap file�и������Ϣ
            swapLock.release();

            if (swapEntry != null) {
                // ��ҳ��swap file ��
                Lib.debug(dbgVM,"swap in from swap page num:"+swapEntry.swapPageNumber+" into physical page num:"+ppn);
                Lib.assertTrue(swapFile.read(swapEntry.swapPageNumber * Processor.pageSize,
                        Machine.processor().getMemory(),
                        ppn * Processor.pageSize,
                        Processor.pageSize) == Processor.pageSize);
                //��swap file�е�ҳ���������ڴ�
                invertedTable[ppn].translationEntry.readOnly = swapEntry.readOnly;
            }
            System.out.println("֡����finished");
        }

        /**
         * �жϸý��̵�����ҳ���Ƿ���swap file ��
         */
        boolean pageInSwap(int vpn, int pid) {
            swapLock.acquire();
            boolean retBool = swapTable.containsKey(new TableKey(vpn, pid));
            swapLock.release();
            return retBool;
        }

        /**
         * ��swap file�е�ҳ����freelist�����Ա�����swap֡�û�
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
         * �رղ�ɾ��swap file
         */
        void cleanup() {
            swapFile.close();
            fileSystem.remove(swapFile.getName());
        }

        /** swap��¼ */
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

        /** ��swap file�е�ҳ */
        private LinkedList<SwapEntry> freeList = new LinkedList<SwapEntry>();

        /** ����ҳ��ӳ���� swap file�еļ�¼ */
        private HashMap<TableKey, SwapEntry> swapTable = new HashMap<TableKey, SwapEntry>();

        /** �ڲٿ�swap�������� */
        private Lock swapLock = new Lock();
    }

    private Swap swap;
}