package nachos.threads;

import nachos.machine.*;
import nachos.threads.PriorityScheduler.ThreadState;

import java.util.LinkedList;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking
 * the maximum).
 */
public class LotteryScheduler extends Scheduler {
    /**
     * Allocate a new lottery scheduler.
     */
    public LotteryScheduler() {
    }
    
    /**
     * Allocate a new lottery thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer tickets from waiting threads
     *					to the owning thread.
     * @return	a new lottery thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
	// implement me
	//return null;
    	return new LotteryQueue(true);
    }
    public int getPriority(KThread thread)
    {
    	Lib.assertTrue(Machine.interrupt().disabled());
    	return getThreadState(thread).getPriority();
    }
    public int getEffectivePriority(KThread thread)
    {
    	Lib.assertTrue(Machine.interrupt().disabled());
    	return getThreadState(thread).getEffectivePriority();
    }
    public void setPriority(KThread thread,int priority)
    {
    	Lib.assertTrue(Machine.interrupt().disabled());
    	Lib.assertTrue(priority>=priorityMinimum&&priority<=priorityMaximum);
    	getThreadState(thread).setPriority(priority);
    }
    public boolean increasePriority()
    {
    	boolean intStatus=Machine.interrupt().disable();
    	KThread thread=KThread.currentThread();
    	int priority=getPriority(thread);
    	if(priority==priorityMaximum)
    		return false;
    	setPriority(thread,priority+1);
    	Machine.interrupt().setStatus(intStatus);
    	return true;
    }
    public boolean decreasePriority()
    {
    	boolean intStatus=Machine.interrupt().disable();
    	KThread thread=KThread.currentThread();
    	int priority=getPriority(thread);
    	if(priority==priorityMinimum)
    		return false;
    	setPriority(thread,priority-1);
    	Machine.interrupt().setStatus(intStatus);
    	return true;
    }
    protected ThreadState getThreadState(KThread thread) {
    	if (thread.schedulingState == null)
    	    thread.schedulingState = new ThreadState(thread);

    	return (ThreadState) thread.schedulingState;
        }
    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;    

    protected class LotteryQueue extends ThreadQueue
    {
    	//����һ��ThreadState����
    	private LinkedList<ThreadState> link;
    	
    	private ThreadState linkthread;
    	private boolean transferPriority;
    	public LotteryQueue(boolean transferPriority)
    	{
    	this.transferPriority=transferPriority;
    	link=new LinkedList<ThreadState>();
    	}
    	@Override
    	public void waitForAccess(KThread thread) {
    		// TODO Auto-generated method stub
    		Lib.assertTrue(Machine.interrupt().disabled());
    		getThreadState(thread).waitForAccess(this);
    	}
    	@Override
    	public KThread nextThread() {
    		// TODO Auto-generated method stub
    		Lib.assertTrue(Machine.interrupt().disabled());
    		//��Ʊ����
    		int lottery=0;
    		KThread thread=null;
    		for(int i=0;i<link.size();i++)
    		{
    			//�õ����������������̵߳Ĳ�Ʊ
    			lottery+=link.get(i).getEffectivePriority();
    		}
    		//���ָ�������̵߳Ĳ�Ʊ��
    		int run=Lib.random(lottery+1);
    		
    		int rank=0;
    		
    		for(int i=0;i<link.size();i++)
    		{
    			//�������е��߳�����������һ���ж��Ƿ񳬳���Ʊ��
    			rank+=link.get(i).getEffectivePriority();
    			if(rank>=run)
    			{
    				thread=link.get(i).thread;//���߳̿���ִ��
    				break;
    			}
    		}
    		if(thread!=null)
    		{
    			//�����һ���̲߳�Ϊ�գ���Ӷ������Ƴ��߳�
    			link.remove(thread.schedulingState);//�Ƴ����߳�
    			return thread;
    		}
    		else
    		{
    			return null;
    		}
    			
    	}
    	@Override
    	public void acquire(KThread thread) {
    		// TODO Auto-generated method stub
    		Lib.assertTrue(Machine.interrupt().disabled());
    		getThreadState(thread).acquire(this);
    	}
    	@Override
    	public void print() {
    		// TODO Auto-generated method stub
    		
    	}
    	public ThreadState pickNextThread()
    	{
    		return null;
    	}
    }
    
    protected class ThreadState
    {
    	//����߳���Ϣ����󶨵��߳�
    	private KThread thread;
    	private int priority;
    	private int effectivePriority=0;
    	//ÿһ���߳�״̬�����а�����һ����Ʊ����
    	private LotteryQueue waitQueue;
    	
    	public ThreadState(KThread thread)
    	{
    		this.thread=thread;
    		//���ø��̵߳�Ĭ�����ȼ�
    		setPriority(priorityDefault);
    		waitQueue=new LotteryQueue(true);
    	}
    	public int getPriority()
    	{
    		return priority;
    	}
    	
    	public int getEffectivePriority()
    	{
    		effectivePriority=priority;//�õ��Լ��Ĳ�Ʊ��
    		for(int i=0;i<waitQueue.link.size();i++)
    		{
    			//�õ��Լ�waitQueue�ϵ��̵߳Ĳ�Ʊ�ŵ��ܺ���
    			effectivePriority+=waitQueue.link.get(i).getEffectivePriority();
    		}
    		return effectivePriority;
    	}
    	public void setPriority(int priority)
    	{
    		if(priority==this.priority)
    			return;
    		this.priority=priority;
    	}
    	public void waitForAccess(LotteryQueue waitQueue)
    	{
    		waitQueue.link.add(this);//��kthread��waitQueue�Ϲ���
    		if(waitQueue.linkthread!=null&&waitQueue.linkthread!=this)
    			waitQueue.linkthread.waitQueue.waitForAccess(thread);
//    		ͬʱ��ThreadState��schedulingState����waitQueue��link�Ϲ���Ϊ�õ�������Ч���ȼ���׼��
    	}
    	public void acquire(LotteryQueue waitQueue)
    	{
    		waitQueue.linkthread=this;
    	}
    }
	
}