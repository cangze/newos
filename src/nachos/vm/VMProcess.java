package nachos.vm;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
    /**
     * Allocate a new process.
     */
    public VMProcess() {
        super();
        if (kernel == null) {
            try {
                kernel = (VMKernel) ThreadedKernel.kernel;
            } catch (ClassCastException cce) {

            }
        }
    }

    /**
     * 进程上下文切换时，进行TLB刷新操作，以防TLB翻译错误
     */
    @Override
    public void saveState() {
        kernel.propagateAndFlushTLB(true);
    }


    @Override
    public void restoreState() {}

    /**
     * 加载Coff Section，记录要加载的虚拟页号，实现lazy load。
     */
    @Override
    protected boolean loadSections() {
        //记录所有Coff section的虚拟地址
        int topVPN = 0;
        for (int sectionNumber = 0; sectionNumber < coff.getNumSections(); sectionNumber++) {
            CoffSection section = coff.getSection(sectionNumber);

            CoffConstructor constructor;

            //将section与虚拟页号对应
            topVPN += section.getLength();
            for (int i = section.getFirstVPN(); i < topVPN; i++) {
                constructor = new CoffConstructor(section, i);
                Lib.debug(dbgProcess, "load coff section into vm "+i);
                lazyLoadSection.put(i, constructor);
            }
        }

        //为进程的栈分配虚拟页号
        for (; topVPN < numPages - 1; topVPN++)
        {
            Lib.debug(dbgProcess, "load stack page into vm "+topVPN);
            lazyLoadSection.put(topVPN, new StackConstructor(topVPN));
        }


        return true;
    }

    @Override
    protected void unloadSections() {
        kernel.freePages(processID, numPages);
    }

    @Override
    public void loadArguments(String[] args,byte[][] argv) {
        int entryOffset = (numPages-1)*pageSize;
        int stringOffset = entryOffset + args.length*4;
        if(args.length!=0) {
            for (int i=0;i<args.length;i++){
                System.out.print("ARGS:"+args[i]);
            }
        }
        else {
            System.out.print("args is null");
        }
        Lib.debug(dbgProcess, "load argument page into vm "+(numPages-1));
        lazyLoadSection.put(numPages - 1, new ArgConstructor(entryOffset, stringOffset, argv));
    }

    /**
     * 解决TLB未命中的异常情况
     * 继承UserProcess的异常处理
     */
    @Override
    public void handleException(int cause) {
        Processor processor = Machine.processor();
        switch (cause) {
            case Processor.exceptionTLBMiss:
//                System.out.println("TLBmissing Handling");
                handleTLBMiss(processor.readRegister(processor.regBadVAddr));
                break;
            default:
                super.handleException(cause);
                break;
        }
    }

    /**
     * 处理TLB缺失异常
     */
    public void handleTLBMiss(int vaddr) {
        Lib.debug(dbgTLB, "TLB miss due to bad address :"+vaddr);
//        System.out.println("TLB");
        if (!validAddress(vaddr)) {
            System.out.println("TLB invalid");
            //无效虚拟地址
        } else {

            //返回页
            TranslationEntry retrievedTE = retrievePage(Processor.pageFromAddress(vaddr));
            //是否已经写到tlb的标识
            boolean unwritten = true;
            Processor p = Machine.processor();
//            System.out.println("p.TLBSize"+p.getTLBSize());
            for (int i = 0; i < p.getTLBSize() && unwritten; i++) {
                //遍历TLB，更新TLB状态
                //vpn-ppn<
                TranslationEntry tlbTranslationEntry = p.readTLBEntry(i);
                //
                //当遇到相同物理帧号时
//                System.out.println("tlbTranslation"+tlbTranslationEntry);
//                System.out.println("retrievedTE"+retrievedTE);

                if (tlbTranslationEntry.ppn == retrievedTE.ppn) {
                    if (unwritten) {
                        //进行写覆盖
                        p.writeTLBEntry(i, retrievedTE);
                        unwritten = false;
                    } else if (tlbTranslationEntry.valid) {
                        //如果已经写了，则将该项无效化
                        tlbTranslationEntry.valid = false;
                        p.writeTLBEntry(i, tlbTranslationEntry);
                    }
                } else if (unwritten && !tlbTranslationEntry.valid) {
                    //遇到无效项并且没写，则写入该项
                    p.writeTLBEntry(i, retrievedTE);
                    unwritten = false;
                }
            }

            //倘若所有的TLB均有用，没有写入新的项的地方，则要随机替换一项
            if (unwritten) {
//                System.out.println("TLB2");
                int randomIndex = generator.nextInt(p.getTLBSize());
                TranslationEntry oldEntry = p.readTLBEntry(randomIndex);

                //将该TLB项的内容写回至反向页表
                if (oldEntry.dirty || oldEntry.used)
                    kernel.propagateEntry(oldEntry.ppn, oldEntry.used, oldEntry.dirty);

                p.writeTLBEntry(randomIndex, retrievedTE);
            }

            //取消固定该物理帧
            kernel.unfix(retrievedTE.ppn);
        }
    }

    public Random generator = new Random();

    /**
     * 得到虚拟页号对应的页表项
     */
    public TranslationEntry retrievePage(int vpn) {
        TranslationEntry returnEntry = null;

        //还未加载进过内存，lazy load
        if (lazyLoadSection.containsKey(vpn))
        {
            Lib.debug(dbgProcess, "lazy load virtual page num:" +vpn + " into physical memory ");
            returnEntry = lazyLoadSection.get(vpn).execute();
        }
        else if ((returnEntry = kernel.fixIfExists(vpn, processID)) == null)//物理内存里没有
        {
            Lib.debug(dbgProcess, "page fault due to losing virtual page num:"+vpn);
            //引发页错误
            returnEntry = kernel.pageFault(vpn, processID);
        }

        Lib.assertTrue(returnEntry != null);
        return returnEntry;
    }

    @Override
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        int bytesRead = 0;
        LinkedList<VMMemoryAccess> memoryAccesses = createMemoryAccesses(vaddr, data, offset, length, AccessType.READ, true);
        //创建内存访问列表
        if (memoryAccesses != null) {
            int temp;
            for (VMMemoryAccess vma : memoryAccesses) {
                temp = vma.executeAccess();
                if (temp == 0)
                    break;
                else
                    bytesRead += temp;
            }
        }

        return bytesRead;
    }

    @Override
    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        return writeVirtualMemory(vaddr, data, offset, length, true);
    }

    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length, boolean unfix) {
        int bytesWritten = 0;
        LinkedList<VMMemoryAccess> memoryAccesses = createMemoryAccesses(vaddr, data, offset, length, AccessType.WRITE, unfix);
        //创建内存访问列表
        if (memoryAccesses != null) {
            int temp;
            for (VMMemoryAccess vma : memoryAccesses) {
                temp = vma.executeAccess();
                //执行内存访问操作
                if (temp == 0)
                    break;
                else
                    bytesWritten += temp;
            }
        }

        return bytesWritten;
    }

    public int writeVirtualMemory(int vaddr, byte[] data, boolean unfix) {
        return VMProcess.this.writeVirtualMemory(vaddr, data, 0, data.length, unfix);
    }

    public LinkedList<VMMemoryAccess> createMemoryAccesses(int vaddr, byte[] data, int offset, int length, AccessType accessType, boolean unfix) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
        LinkedList<VMMemoryAccess> returnList  = null;

        if (validAddress(vaddr)) {
            //有效地址
            returnList = new LinkedList<VMMemoryAccess>();

            while (length > 0) {
                int vpn = Processor.pageFromAddress(vaddr);

                int potentialPageAccess = Processor.pageSize - Processor.offsetFromAddress(vaddr);
                int accessSize = length < potentialPageAccess ? length : potentialPageAccess;

                returnList.add(new VMMemoryAccess(accessType, data, vpn, offset, Processor.offsetFromAddress(vaddr), accessSize, unfix));
                length -= accessSize;
                vaddr += accessSize;
                offset += accessSize;
            }
        }

        return returnList;
    }

    public static final int pageSize = Processor.pageSize;
    public static final char dbgProcess = 'a';
    public static final char dbgVM = 'v';


    public static VMKernel kernel = null;

    //初始coff section
    public HashMap<Integer, Constructor> lazyLoadSection = new HashMap<Integer,Constructor>();

    public class VMMemoryAccess extends UserProcess.MemoryAccess {
        //将虚拟内存访问抽象为一个类，方便
        VMMemoryAccess(AccessType at, byte[] d, int _vpn, int dStart, int pStart, int len, boolean _unfix) {
            super(at,d,_vpn,dStart,pStart,len);
            unfix = _unfix;
        }

        @Override
        public int executeAccess() {
            //通过虚拟内存方式查找对应项
            translationEntry = retrievePage(vpn);
            //执行访问操作
            int bytesAccessed = super.executeAccess();

            if (unfix)
                kernel.unfix(translationEntry.ppn);

            return bytesAccessed;
        }

        /** 表示执行完内存读写操作后，该物理内存帧能否被修改 */
        public boolean unfix;
    }

    /**
     * 页构造抽象类
     * 方便实现lazy load
     * 有三种不同的页类型：
     * 1.Coff section 用于装载.text、.data、.bss等section
     * 2.stack page
     * 3.argument page
     */
    public abstract class Constructor {
        abstract TranslationEntry execute();
    }
    //coff lazy load
    public class CoffConstructor extends Constructor {
        CoffConstructor(CoffSection ce, int vpn1) {
            coffSection = ce;
            vpn = vpn1;
        }

        @Override
        TranslationEntry execute() {
            System.out.println("lazy load section");
            //lazy load 页
            int pageNumber = vpn - coffSection.getFirstVPN();
            //从lazyLoadSection删除该页，表示该页已经加载
            Lib.assertTrue(lazyLoadSection.remove(vpn) != null);
            //得到空闲页
            TranslationEntry returnEntry = kernel.requestFreePage(vpn, processID);
            //load开始
            coffSection.loadPage(pageNumber, returnEntry.ppn);
            returnEntry.readOnly = coffSection.isReadOnly() ? true : false;

            return returnEntry;
        }

        public CoffSection coffSection;
        public int vpn;
    }

    //stack lazy load 类
    public class StackConstructor extends Constructor {
        StackConstructor(int vpn1) {
            vpn = vpn1;
        }

        @Override
        TranslationEntry execute() {
            System.out.println("lazy load堆栈");
            Lib.assertTrue(lazyLoadSection.remove(vpn) != null);

            TranslationEntry te = kernel.requestFreePage(vpn, processID);
            te.readOnly = false;
            return te;
        }

        public int vpn;
    }
    //argument lazy load类 将参数写入物理帧中
    public class ArgConstructor extends Constructor {
        ArgConstructor(int _entryOffset, int _stringOffset, byte[][] _argv) {
            entryOffset = _entryOffset; stringOffset = _stringOffset; argv = _argv;
        }

        @Override
        TranslationEntry execute() {
            System.out.println("lazy load参数");
            Lib.assertTrue(lazyLoadSection.remove(numPages - 1) != null);

            TranslationEntry te = kernel.requestFreePage(numPages - 1, processID);


            for (int i = 0; i < argv.length; i++) {
                byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
                Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes, false) == 4);
                entryOffset += 4;
                Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i], false) == argv[i].length);
                stringOffset += argv[i].length;
                Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }, false) == 1);
                stringOffset += 1;
            }

            te.readOnly = true;

            return te;
        }

        public int entryOffset, stringOffset;
        public byte[][] argv;
    }
    private static final char dbgTLB = 'T';
}