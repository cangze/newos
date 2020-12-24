package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A scheduler�����ȳ��� that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler�����ȼ����ȳ��� associates a priority with each thread. The next thread
 * to be dequeued���Ӷ������Ƴ��� is always a thread with priority no less than any other
 * waiting thread's priority���������ĵȴ���������ȼ�����. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially�������ϣ�, a priority scheduler gives access��ʹ��Ȩ�� in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential�������ԣ� to starve�������� a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially�����ֵģ� solve the priority inversion�����ȼ���ת�� problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }

    /**
     * Allocate a new priority thread queue.
     *
     * @param    transferPriority    <tt>true</tt> if this queue should
     * transfer priority from waiting threads
     * to the owning thread.
     * @return a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
        return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
        //�õ��̵߳����ȼ�
        Lib.assertTrue(Machine.interrupt().disabled());
        return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
        //�õ��̵߳���Ч���ȼ�
        Lib.assertTrue(Machine.interrupt().disabled());
        return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
        //�����̵߳����ȼ�
        Lib.assertTrue(Machine.interrupt().disabled());
        Lib.assertTrue(priority >= priorityMinimum && priority <= priorityMaximum);

        getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
        //���������̵߳����ȼ�
        boolean intStatus = Machine.interrupt().disable();
        KThread thread = KThread.currentThread();
        int priority = getPriority(thread);
        //�ﵽ������ȼ������������ȼ�
        if (priority == priorityMaximum)
            return false;
        setPriority(thread, priority + 1);
        Machine.interrupt().restore(intStatus);
        return true;
    }

    public boolean decreasePriority() {
        //���������̵߳����ȼ�
        boolean intStatus = Machine.interrupt().disable();
        KThread thread = KThread.currentThread();
        int priority = getPriority(thread);
        //�ﵽ��С���ȼ����ټ�С���ȼ�
        if (priority == priorityMinimum)
            return false;
        setPriority(thread, priority - 1);
        Machine.interrupt().restore(intStatus);
        return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1; //���̵߳�Ĭ�����ȼ�
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;//�̵߳�������ȼ�
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;//�߳�������ȼ�Ϊ7

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param    thread    the thread whose scheduling state to return.
     * @return the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
        //�õ��߳����ȼ���δ�����򴴽�δĬ�����ȼ�
        if (thread.schedulingState == null)
            thread.schedulingState = new ThreadState(thread);

        return (ThreadState) thread.schedulingState;
    }

    //�����ȼ����е��̶߳���

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    //!��������������������������������������������������������������������������������������������������������������������������������������������������������������������������������������������
    protected class PriorityQueue extends ThreadQueue {
        /**
         * <tt>true</tt> if this queue should transfer priority from waiting
         * threads to the owning thread.
         */
        public boolean transferPriority;
        public LinkedList<ThreadState> waitList = new LinkedList<ThreadState>();
        //���ζ��а󶨵�һ��KThread
        public ThreadState lockHolder = null;
        private int index;

        //���ȼ�����
        PriorityQueue(boolean transferPriority) {
            //�Զ����ø����޲������췽��������һ���̶߳���
            this.transferPriority = transferPriority;
        }

        public void waitForAccess(KThread thread) {
            //����ȴ����е��߳�
            Lib.assertTrue(Machine.interrupt().disabled());
            getThreadState(thread).waitForAccess(this);
        }

        public void acquire(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            // getThreadState(thread).acquire(this);
            if (!thread.getName().equals("main")) {
                //�����̰߳󶨵�����߳���
                getThreadState(thread).acquire(this);

            }
        }

        //�޸�
        public KThread nextThread() {
            Lib.assertTrue(Machine.interrupt().disabled());
            // implement me
            //ѡ��һ������������ȼ����߳�
            int max = -1;
            //�������������int �ͻ᲻��ӡ
            index = 0;
            ThreadState state = null, temp = null;
            //����,�ҵ��������ȼ���ThreadState�������丳��state
            while ((temp = pickNextThread()) != null) {
                //��һ���̲߳�Ϊ��
                if (temp.getEffectivePriority() > max) {
                    //��ǰ��Ч���ȼ� ����������ȼ�
                    state = temp;
                    //��������ȼ���Ϊ��ǰ�����ȼ�
                    max = temp.getEffectivePriority();
                }
            }
            //�����һ���߳�Ϊ�գ��򷵻ؿգ����򣬽���һ���߳��ó�
            if (state == null)
                return null;
            else
                return waitList.remove(waitList.indexOf(state)).thread;
        }

        /**
         * Return the next thread that <tt>nextThread()</tt> would return,
         * without modifying the state of this queue.
         *
         * @return the next thread that <tt>nextThread()</tt> would
         * return.
         */
        protected ThreadState pickNextThread() {
            // implement me
            if (index < waitList.size()) {
                index++;
                return waitList.get(index - 1);
            }
            return null;

        }

        public void print() {
            Lib.assertTrue(Machine.interrupt().disabled());
            // implement me (if you want)
        }


    }
//��������������������������������������������������������������������������������������������������������������������������������������������������������������������������
    //��һ���̰߳�ThreadState

    /**
     * The scheduling state of a thread��һ���̵߳����ȼ�״̬��. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see    nachos.threads.KThread#schedulingState
     */


    protected class ThreadState {
        /**
         * The thread with which this object is associated.
         */
        protected KThread thread;    //ThreadState��ϵ���߳�
        /**
         * The priority of the associated thread.
         */
        protected int priority;        //���̵߳����ȼ�
        /**
         * ��Ч���ȼ�
         */
        protected int effectivepriority;    //���̵߳���Ч���ȼ�
        /**
         * ���ȼ��ȴ�����
         */
        protected PriorityQueue acquired ;    //���ȵȴ����У��ȴ����̵߳Ķ���
        /**
         * Allocate a new <tt>ThreadState</tt> object and associate it with the
         * specified thread.
         *
         * @param    thread    the thread this state belongs to.
         */
        public ThreadState(KThread thread) {
            this.thread = thread;
            setPriority(priorityDefault);
            //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            //�ȴ����У�
            acquired = new PriorityQueue(true);
        }

        /**
         * Return the priority of the associated thread.
         *
         * @return the priority of the associated thread.
         */
        public int getPriority() {
            return priority;
        }

//	public int geteffectivepriority(){
//		return effectivepriority;
//	}



        /**
         * Return the effective priority of the associated thread.
         *
         * @return the effective priority of the associated thread.
         */
        public int getEffectivePriority() {
            effectivepriority = -1;
            //�õ���Ч�����ȼ�
            // implement me
            //ThreadState ��һ��PriorityQueue���͵� waitQueue, PriorityQueue ���и�LinkedList��

            for (int i = 0; i < acquired.waitList.size(); i++) {
                //��������е���Ч���ȼ����ڵ�ǰ�ģ���ѵ�ǰ����Ч���ȼ���Ϊ�������������ȼ�
                if (acquired.waitList.get(i).getEffectivePriority() > effectivepriority)
                    effectivepriority = acquired.waitList.get(i).getEffectivePriority();
            }
            //�ѵ�ǰ�̵߳����ȼ���Ϊ ��Ч���ȼ� ��������ȼ�
            if (effectivepriority > priority)
                setPriority(effectivepriority);
            return priority;
        }

        /**
         * Set the priority of the associated thread to the specified value.
         *
         * @param    priority    the new priority.
         */
        public void setPriority(int priority) {
            //���ȼ�����
            if (this.priority == priority)
                return;

            this.priority = priority;
            if(thread.getName().equals("(unnamed thread)")){//System.out.println("this thread doesn't have a name");
            }
            else System.out.println("set thread:"+thread.getName()+" priority is"+priority);
            // implement me
        }

        /**
         * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
         * the associated thread) is invoked on the specified priority queue.
         * The associated thread is therefore waiting for access to the
         * resource guarded by <tt>waitQueue</tt>. This method is only called
         * if the associated thread cannot immediately obtain access.
         *
         * @param    acquired    the queue that the associated thread is
         * now waiting on.
         * @see    nachos.threads.ThreadQueue#waitForAccess
         */
        public void waitForAccess(PriorityQueue acquired) {
            // implement me
            //�����̵߳�״̬���봫����ø÷�����KThread�ȴ����У����ȼ����ȵĵȴ�����
            acquired.waitList.add(this);
            //````````````````````````````````````````````````````````?????
            if (acquired.lockHolder != null && acquired.lockHolder != this) {
                //�����̼߳��뵽,�󶨵�KThread�ĵȴ�����
                acquired.lockHolder.acquired.waitForAccess(this.thread);
            }

        }

        /**
         * Called when the associated thread has acquired access to whatever is
         * guarded by <tt>waitQueue</tt>. This can occur either as a result of
         * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
         * <tt>thread</tt> is the associated thread), or as a result of
         * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
         *
         * @see    nachos.threads.ThreadQueue#acquire
         * @see    nachos.threads.ThreadQueue#nextThread
         */
        public void acquire(PriorityQueue acquired) {
            //�൱��һ���̵߳Ķ�����
            // implement me
            Lib.assertTrue(acquired.waitList.isEmpty());
            acquired.lockHolder = this;
            //System.out.println("this:"+this.thread.getName());
        }


    }

}
