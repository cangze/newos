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
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());//��⵱ǰ�߳��Ƿ�����

	// ���ж�--���������ԭ����
	boolean status=Machine.interrupt().disable();

	//�ͷ���--�ñ���߳̿��Եõ���
	conditionLock.release();

	//����ǰ�̼߳��뵽waitQueue��
	waitqueue.waitForAccess(KThread.currentThread());

	//��ǰ����˯��
	KThread.currentThread().sleep();

	//���»�����������̵߳�ʱ����Ҫ����������
	conditionLock.acquire();
    
	Machine.interrupt().restore(status);//���ж�
    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());
	//s���ж�
	boolean status = Machine.interrupt().disable();
	//�õ���ǰ�̵߳�waitQueue�е���һ���߳�
	KThread thread = waitqueue.nextThread();

	if(!(thread==null))
		thread.ready();//���̼߳���������е����ͷ���

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
		//���뵽����������
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
