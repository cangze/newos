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
     * �����������л�ʱ������TLBˢ�²������Է�TLB�������
     */
    @Override
    public void saveState() {
        kernel.propagateAndFlushTLB(true);
    }


    @Override
    public void restoreState() {}

    /**
     * ����Coff Section����¼Ҫ���ص�����ҳ�ţ�ʵ��lazy load��
     */
    @Override
    protected boolean loadSections() {
        //��¼����Coff section�������ַ
        int topVPN = 0;
        for (int sectionNumber = 0; sectionNumber < coff.getNumSections(); sectionNumber++) {
            CoffSection section = coff.getSection(sectionNumber);

            CoffConstructor constructor;

            //��section������ҳ�Ŷ�Ӧ
            topVPN += section.getLength();
            for (int i = section.getFirstVPN(); i < topVPN; i++) {
                constructor = new CoffConstructor(section, i);
                Lib.debug(dbgProcess, "load coff section into vm "+i);
                lazyLoadSection.put(i, constructor);
            }
        }

        //Ϊ���̵�ջ��������ҳ��
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
     * ���TLBδ���е��쳣���
     * �̳�UserProcess���쳣����
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
     * ����TLBȱʧ�쳣
     */
    public void handleTLBMiss(int vaddr) {
        Lib.debug(dbgTLB, "TLB miss due to bad address :"+vaddr);
//        System.out.println("TLB");
        if (!validAddress(vaddr)) {
            System.out.println("TLB invalid");
            //��Ч�����ַ
        } else {

            //����ҳ
            TranslationEntry retrievedTE = retrievePage(Processor.pageFromAddress(vaddr));
            //�Ƿ��Ѿ�д��tlb�ı�ʶ
            boolean unwritten = true;
            Processor p = Machine.processor();
//            System.out.println("p.TLBSize"+p.getTLBSize());
            for (int i = 0; i < p.getTLBSize() && unwritten; i++) {
                //����TLB������TLB״̬
                //vpn-ppn<
                TranslationEntry tlbTranslationEntry = p.readTLBEntry(i);
                //
                //��������ͬ����֡��ʱ
//                System.out.println("tlbTranslation"+tlbTranslationEntry);
//                System.out.println("retrievedTE"+retrievedTE);

                if (tlbTranslationEntry.ppn == retrievedTE.ppn) {
                    if (unwritten) {
                        //����д����
                        p.writeTLBEntry(i, retrievedTE);
                        unwritten = false;
                    } else if (tlbTranslationEntry.valid) {
                        //����Ѿ�д�ˣ��򽫸�����Ч��
                        tlbTranslationEntry.valid = false;
                        p.writeTLBEntry(i, tlbTranslationEntry);
                    }
                } else if (unwritten && !tlbTranslationEntry.valid) {
                    //������Ч���ûд����д�����
                    p.writeTLBEntry(i, retrievedTE);
                    unwritten = false;
                }
            }

            //�������е�TLB�����ã�û��д���µ���ĵط�����Ҫ����滻һ��
            if (unwritten) {
//                System.out.println("TLB2");
                int randomIndex = generator.nextInt(p.getTLBSize());
                TranslationEntry oldEntry = p.readTLBEntry(randomIndex);

                //����TLB�������д��������ҳ��
                if (oldEntry.dirty || oldEntry.used)
                    kernel.propagateEntry(oldEntry.ppn, oldEntry.used, oldEntry.dirty);

                p.writeTLBEntry(randomIndex, retrievedTE);
            }

            //ȡ���̶�������֡
            kernel.unfix(retrievedTE.ppn);
        }
    }

    public Random generator = new Random();

    /**
     * �õ�����ҳ�Ŷ�Ӧ��ҳ����
     */
    public TranslationEntry retrievePage(int vpn) {
        TranslationEntry returnEntry = null;

        //��δ���ؽ����ڴ棬lazy load
        if (lazyLoadSection.containsKey(vpn))
        {
            Lib.debug(dbgProcess, "lazy load virtual page num:" +vpn + " into physical memory ");
            returnEntry = lazyLoadSection.get(vpn).execute();
        }
        else if ((returnEntry = kernel.fixIfExists(vpn, processID)) == null)//�����ڴ���û��
        {
            Lib.debug(dbgProcess, "page fault due to losing virtual page num:"+vpn);
            //����ҳ����
            returnEntry = kernel.pageFault(vpn, processID);
        }

        Lib.assertTrue(returnEntry != null);
        return returnEntry;
    }

    @Override
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        int bytesRead = 0;
        LinkedList<VMMemoryAccess> memoryAccesses = createMemoryAccesses(vaddr, data, offset, length, AccessType.READ, true);
        //�����ڴ�����б�
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
        //�����ڴ�����б�
        if (memoryAccesses != null) {
            int temp;
            for (VMMemoryAccess vma : memoryAccesses) {
                temp = vma.executeAccess();
                //ִ���ڴ���ʲ���
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
            //��Ч��ַ
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

    //��ʼcoff section
    public HashMap<Integer, Constructor> lazyLoadSection = new HashMap<Integer,Constructor>();

    public class VMMemoryAccess extends UserProcess.MemoryAccess {
        //�������ڴ���ʳ���Ϊһ���࣬����
        VMMemoryAccess(AccessType at, byte[] d, int _vpn, int dStart, int pStart, int len, boolean _unfix) {
            super(at,d,_vpn,dStart,pStart,len);
            unfix = _unfix;
        }

        @Override
        public int executeAccess() {
            //ͨ�������ڴ淽ʽ���Ҷ�Ӧ��
            translationEntry = retrievePage(vpn);
            //ִ�з��ʲ���
            int bytesAccessed = super.executeAccess();

            if (unfix)
                kernel.unfix(translationEntry.ppn);

            return bytesAccessed;
        }

        /** ��ʾִ�����ڴ��д�����󣬸������ڴ�֡�ܷ��޸� */
        public boolean unfix;
    }

    /**
     * ҳ���������
     * ����ʵ��lazy load
     * �����ֲ�ͬ��ҳ���ͣ�
     * 1.Coff section ����װ��.text��.data��.bss��section
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
            //lazy load ҳ
            int pageNumber = vpn - coffSection.getFirstVPN();
            //��lazyLoadSectionɾ����ҳ����ʾ��ҳ�Ѿ�����
            Lib.assertTrue(lazyLoadSection.remove(vpn) != null);
            //�õ�����ҳ
            TranslationEntry returnEntry = kernel.requestFreePage(vpn, processID);
            //load��ʼ
            coffSection.loadPage(pageNumber, returnEntry.ppn);
            returnEntry.readOnly = coffSection.isReadOnly() ? true : false;

            return returnEntry;
        }

        public CoffSection coffSection;
        public int vpn;
    }

    //stack lazy load ��
    public class StackConstructor extends Constructor {
        StackConstructor(int vpn1) {
            vpn = vpn1;
        }

        @Override
        TranslationEntry execute() {
            System.out.println("lazy load��ջ");
            Lib.assertTrue(lazyLoadSection.remove(vpn) != null);

            TranslationEntry te = kernel.requestFreePage(vpn, processID);
            te.readOnly = false;
            return te;
        }

        public int vpn;
    }
    //argument lazy load�� ������д������֡��
    public class ArgConstructor extends Constructor {
        ArgConstructor(int _entryOffset, int _stringOffset, byte[][] _argv) {
            entryOffset = _entryOffset; stringOffset = _stringOffset; argv = _argv;
        }

        @Override
        TranslationEntry execute() {
            System.out.println("lazy load����");
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