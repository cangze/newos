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
    	//声明一个ThreadState链表
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
    		//彩票数量
    		int lottery=0;
    		KThread thread=null;
    		for(int i=0;i<link.size();i++)
    		{
    			//得到这个队列里的所有线程的彩票
    			lottery+=link.get(i).getEffectivePriority();
    		}
    		//随机指定运行线程的彩票号
    		int run=Lib.random(lottery+1);
    		
    		int rank=0;
    		
    		for(int i=0;i<link.size();i++)
    		{
    			//将队列中的线程垒起来，加一个判断是否超出彩票号
    			rank+=link.get(i).getEffectivePriority();
    			if(rank>=run)
    			{
    				thread=link.get(i).thread;//该线程可以执行
    				break;
    			}
    		}
    		if(thread!=null)
    		{
    			//如果下一个线程不为空，则从队列中移除线程
    			link.remove(thread.schedulingState);//移除该线程
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
    	//这个线程信息对象绑定的线程
    	private KThread thread;
    	private int priority;
    	private int effectivePriority=0;
    	//每一个线程状态对象中包含了一个彩票队列
    	private LotteryQueue waitQueue;
    	
    	public ThreadState(KThread thread)
    	{
    		this.thread=thread;
    		//设置该线程的默认优先级
    		setPriority(priorityDefault);
    		waitQueue=new LotteryQueue(true);
    	}
    	public int getPriority()
    	{
    		return priority;
    	}
    	
    	public int getEffectivePriority()
    	{
    		effectivePriority=priority;//得到自己的彩票数
    		for(int i=0;i<waitQueue.link.size();i++)
    		{
    			//得到自己waitQueue上的线程的彩票号的总号码
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
    		waitQueue.link.add(this);//在kthread的waitQueue上挂起
    		if(waitQueue.linkthread!=null&&waitQueue.linkthread!=this)
    			waitQueue.linkthread.waitQueue.waitForAccess(thread);
//    		同时在ThreadState（schedulingState）的waitQueue的link上挂起，为得到它的有效优先级做准备
    	}
    	public void acquire(LotteryQueue waitQueue)
    	{
    		waitQueue.linkthread=this;
    	}
    }
	
}