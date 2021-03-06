package nachos.threads;

import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see	nachos.threads.Condition
 */
public class Condition2 {
    /**
     * Allocate a new condition variable.
     *
     * @param	conditionLock	the lock associated with this condition
     *				variable. The current thread must hold this
     *				lock whenever it uses <tt>sleep()</tt>,
     *				<tt>wake()</tt>, or <tt>wakeAll()</tt>.
     */
    public Condition2(Lock conditionLock) {
	this.conditionLock = conditionLock;
	//start
	waitqueue=ThreadedKernel.scheduler.newThreadQueue(false);
    //end
    }

    /**
     * Atomically release the associated lock and go to sleep on this condition
     * variable until another thread wakes it using <tt>wake()</tt>. The
     * current thread must hold the associated lock. The thread will
     * automatically reacquire the lock before <tt>sleep()</tt> returns.
     */
    public void sleep() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());//检测当前线程是否获得锁

	// 关中断--保持事务的原子性
	boolean status=Machine.interrupt().disable();

	//释放锁--让别的线程可以得到锁
	conditionLock.release();

	//将当前线程加入到waitQueue中
	waitqueue.waitForAccess(KThread.currentThread());

	//当前进程睡眠
	KThread.currentThread().sleep();

	//重新获得锁，唤醒线程的时候需要重新申请锁
	conditionLock.acquire();
    
	Machine.interrupt().restore(status);//开中断
    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());
	//s关中断
	boolean status = Machine.interrupt().disable();
	//得到当前线程的waitQueue中的下一个线程
	KThread thread = waitqueue.nextThread();

	if(!(thread==null))
		thread.ready();//将线程加入就绪队列但不释放锁

	Machine.interrupt().restore(status);

    }

    /**
     * Wake up all threads sleeping on this condition variable. The current
     * thread must hold the associated lock.
     */
    public void wakeAll() {

	Lib.assertTrue(conditionLock.isHeldByCurrentThread());
	//start
	boolean status = Machine.interrupt().disable();
	KThread thread = waitqueue.nextThread();
	while(thread!=null){
		//加入到就绪队列中
		thread.ready();
		thread=waitqueue.nextThread();
	}
	Machine.interrupt().restore(status);

    }

    private Lock conditionLock;
    //start
    private ThreadQueue waitqueue=null;
    //end
}
