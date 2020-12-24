package nachos.threads;

import java.lang.management.ThreadInfo;
import java.util.LinkedList;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption（抢先）, and to allow threads to sleep
 * until a certain（一定） time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler（时钟中断处理程序） to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function（运行） correctly with more than one
     * alarm.
     */

	private LinkedList<ThreadInfo> list = new LinkedList();
	/**
	 *存储等待线程的信息，包括线程号和等待时间
	 *内部类,存放线程信息
	 */
	private class ThreadInfo{
		private KThread thread;
		private long time;//可以被唤醒的时间
		public ThreadInfo(KThread thread,long time){
			this.thread=thread;
			this.time=time;
		}
		public KThread getThread() {
			return thread;
		}
		public void setThread(KThread thread) {
			this.thread = thread;
		}
		public long getTime() {
			return time;
		}
		public void setTime(long time) {
			this.time = time;
		}
	}


	public Alarm() {
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically（周期性的） (approximately（大约） every 500 clock ticks). Causes the current
     * thread to yield, forcing（强制） a context switch if there is another thread
     * that should be run.
     * 在每一次timer产生时间中断时 遍历队列，检查队列中的时间状态，当线程到了等待的时间
     * 就把线程从队列中取出放入就绪队列。
     */
    public void timerInterrupt() {//处理时钟中断
    	boolean status=Machine.interrupt().disable();
    	long currentTime=Machine.timer().getTime();
    	int size = list.size();
    	//当等待唤醒线程数量为零时，不做任何事情
		if(size!=0) {
//			System.out.println(list);
			int i;
			for(i=0;i<size;i++) {
//				System.out.println("i:"+i);
				//获得当前时间
				long currenttime=Machine.timer().getTime();
				long targetwaittime=list.get(i).getTime();

				KThread thread=list.get(i).getThread();
//				System.out.println("当前时间为:"+currenttime+";"+thread.getName()+"的唤醒时间为:"+targetwaittime);
				if(currenttime>=targetwaittime) {
//    				KThread thread=waitlist.get(i).thread;
					//将当前进程加入到就绪队列
					thread.ready();
//					System.out.println(thread.getName()+":进入就绪队列");
					//把该线程从waitlist中删除
					list.remove(i);

					//从头遍历
					size--;
//					System.out.println(list);

					if(size!=0) {
//						System.out.println("list(0)="+list.get(i).getThread().getName());
						i=-1;
					}

				}
				else {
					break;
				}
			}
		}
    	KThread.currentThread().yield();
    	Machine.interrupt().restore(status);
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */

    public void waitUntil(long x) {
    	//计算等待时间
    	boolean status=Machine.interrupt().disable();
	// for now, cheat just to get something working (busy waiting is bad)
    	//计算被唤醒的时间
    	long wakeTime = Machine.timer().getTime() + x;

    	//创建一个线程信息对象
    	ThreadInfo threadWake=new ThreadInfo(KThread.currentThread(),wakeTime);
    	int size = list.size();
    	//将它加入ThreadInfo按照可以被唤醒的顺序加入

		//thread1 150; thread2 70;
		//thread2,thread1
    	if(size==0)list.add(threadWake);

    	//寻找一个合适的位置将 KThreadInfo加入
    	else{
    		//在链表中寻找一个 waketime 合适的位置
    		for(int i=0;i<size;i++){
    			if(wakeTime<list.get(i).getTime()){
    				list.add(i, threadWake);
    				break;
    			}
    			if(i==size-1)
    				list.add(threadWake);
    		}
    	}
    	//System.out.println(KThread.currentThread().getName()+"线程睡眠，时间:"+Machine.timer().getTime()+",会在"+wakeTime+"醒来");

		//睡眠
		KThread.currentThread().sleep();
    	//开中断
    	Machine.interrupt().restore(status);
    }


    
    


}
