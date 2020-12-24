package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A scheduler（调度程序） that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler（优先级调度程序） associates a priority with each thread. The next thread
 * to be dequeued（从队列中移除） is always a thread with priority no less than any other
 * waiting thread's priority（比其他的等待程序的优先级都大）. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially（本质上）, a priority scheduler gives access（使用权） in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential（可能性） to starve（饿死） a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially（部分的） solve the priority inversion（优先级反转） problem; in
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
        //得到线程的优先级
        Lib.assertTrue(Machine.interrupt().disabled());
        return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
        //得到线程的有效优先级
        Lib.assertTrue(Machine.interrupt().disabled());
        return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
        //设置线程的优先级
        Lib.assertTrue(Machine.interrupt().disabled());
        Lib.assertTrue(priority >= priorityMinimum && priority <= priorityMaximum);

        getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
        //增加运行线程的优先级
        boolean intStatus = Machine.interrupt().disable();
        KThread thread = KThread.currentThread();
        int priority = getPriority(thread);
        //达到最大优先级后不再增加优先级
        if (priority == priorityMaximum)
            return false;
        setPriority(thread, priority + 1);
        Machine.interrupt().restore(intStatus);
        return true;
    }

    public boolean decreasePriority() {
        //降低运行线程的优先级
        boolean intStatus = Machine.interrupt().disable();
        KThread thread = KThread.currentThread();
        int priority = getPriority(thread);
        //达到最小优先级后不再减小优先级
        if (priority == priorityMinimum)
            return false;
        setPriority(thread, priority - 1);
        Machine.interrupt().restore(intStatus);
        return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1; //新线程的默认优先级
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;//线程的最低优先级
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;//线程最高优先级为7

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param    thread    the thread whose scheduling state to return.
     * @return the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
        //得到线程优先级，未创建则创建未默认优先级
        if (thread.schedulingState == null)
            thread.schedulingState = new ThreadState(thread);

        return (ThreadState) thread.schedulingState;
    }

    //用优先级排列的线程队列

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    //!·····························································································！
    protected class PriorityQueue extends ThreadQueue {
        /**
         * <tt>true</tt> if this queue should transfer priority from waiting
         * threads to the owning thread.
         */
        public boolean transferPriority;
        public LinkedList<ThreadState> waitList = new LinkedList<ThreadState>();
        //将次队列绑定到一个KThread
        public ThreadState lockHolder = null;
        private int index;

        //优先级队列
        PriorityQueue(boolean transferPriority) {
            //自动调用父类无参数构造方法，创建一个线程队列
            this.transferPriority = transferPriority;
        }

        public void waitForAccess(KThread thread) {
            //传入等待队列的线程
            Lib.assertTrue(Machine.interrupt().disabled());
            getThreadState(thread).waitForAccess(this);
        }

        public void acquire(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            // getThreadState(thread).acquire(this);
            if (!thread.getName().equals("main")) {
                //将次线程绑定到这个线程上
                getThreadState(thread).acquire(this);

            }
        }

        //修改
        public KThread nextThread() {
            Lib.assertTrue(Machine.interrupt().disabled());
            // implement me
            //选择一个具有最高优先级的线程
            int max = -1;
            //如果在这里填上int 就会不打印
            index = 0;
            ThreadState state = null, temp = null;
            //遍历,找到最大的优先级的ThreadState，并将其赋给state
            while ((temp = pickNextThread()) != null) {
                //下一个线程不为空
                if (temp.getEffectivePriority() > max) {
                    //当前有效优先级 大于最大优先级
                    state = temp;
                    //把最大优先级置为当前的优先级
                    max = temp.getEffectivePriority();
                }
            }
            //如果下一个线程为空，则返回空；否则，将下一个线程拿出
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
//！····················································································
    //给一个线程绑定ThreadState

    /**
     * The scheduling state of a thread（一个线程的优先级状态）. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see    nachos.threads.KThread#schedulingState
     */


    protected class ThreadState {
        /**
         * The thread with which this object is associated.
         */
        protected KThread thread;    //ThreadState联系的线程
        /**
         * The priority of the associated thread.
         */
        protected int priority;        //此线程的优先级
        /**
         * 有效优先级
         */
        protected int effectivepriority;    //此线程的有效优先级
        /**
         * 优先级等待队列
         */
        protected PriorityQueue acquired ;    //优先等待队列，等待该线程的队列
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
            //等待队列？
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
            //得到有效的优先级
            // implement me
            //ThreadState 有一个PriorityQueue类型的 waitQueue, PriorityQueue 中有个LinkedList的

            for (int i = 0; i < acquired.waitList.size(); i++) {
                //如果队列中的有效优先级大于当前的，则把当前的有效优先级设为队列中最大的优先级
                if (acquired.waitList.get(i).getEffectivePriority() > effectivepriority)
                    effectivepriority = acquired.waitList.get(i).getEffectivePriority();
            }
            //把当前线程的优先级设为 有效优先级 即最大优先级
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
            //优先级传递
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
            //将此线程的状态存入传入调用该方法的KThread等待队列，优先级调度的等待队列
            acquired.waitList.add(this);
            //````````````````````````````````````````````````````````?????
            if (acquired.lockHolder != null && acquired.lockHolder != this) {
                //将次线程加入到,绑定的KThread的等待队列
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
            //相当于一个线程的队列锁
            // implement me
            Lib.assertTrue(acquired.waitList.isEmpty());
            acquired.lockHolder = this;
            //System.out.println("this:"+this.thread.getName());
        }


    }

}
